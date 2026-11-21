package com.example.data.repository

import com.example.data.local.dao.BallEventDao
import com.example.data.local.dao.MatchDao
import com.example.data.local.entity.BallEventEntity
import com.example.data.local.entity.MatchEntity
import com.example.data.model.BallEvent
import com.example.data.model.BatterInningsScore
import com.example.data.model.BowlerInningsScore
import com.example.data.model.CorrectionRecord
import com.example.data.model.DismissalType
import com.example.data.model.ExtraType
import com.example.data.model.FallOfWicket
import com.example.data.model.InningsScorecard
import com.example.data.model.Match
import com.example.data.model.MatchStatus
import com.example.util.JsonHelper
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ScoringRepository(
    private val ballEventDao: BallEventDao,
    private val matchDao: MatchDao,
    private val matchRepository: MatchRepository,
    private val firestore: FirebaseFirestore? = FirebaseFirestore.getInstance(),
    private val enableCloudListeners: Boolean = firestore != null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, t ->
        android.util.Log.e("ScoringRepository", "Handled background error", t)
    })
) {
    private data class CachedBallEntry(
        val rawJson: String,
        val ball: BallEvent
    )

    private val activeBallListeners = ConcurrentHashMap<String, ListenerRegistration>()
    private val matchMutexes = ConcurrentHashMap<String, Mutex>()
    private val cloudSyncMutexes = ConcurrentHashMap<String, Mutex>()
    private val pendingResyncFlags = ConcurrentHashMap.newKeySet<String>()
    private val inFlightSyncMatches = ConcurrentHashMap.newKeySet<String>()
    private val ballEventCache = ConcurrentHashMap<String, CachedBallEntry>()
    private val _latestBallEventFlow = MutableStateFlow<BallEvent?>(null)
    val latestBallEventFlow: StateFlow<BallEvent?> = _latestBallEventFlow

    private fun getMatchMutex(matchId: String): Mutex =
        matchMutexes.computeIfAbsent(matchId) { Mutex() }

    private fun getCloudSyncMutex(matchId: String): Mutex =
        cloudSyncMutexes.computeIfAbsent(matchId) { Mutex() }

    private fun parseBallCached(entity: BallEventEntity): BallEvent? {
        val cached = ballEventCache[entity.id]
        if (cached != null && cached.rawJson == entity.jsonPayload) {
            return cached.ball
        }
        val parsed = JsonHelper.fromJson<BallEvent>(entity.jsonPayload) ?: return null
        ballEventCache[entity.id] = CachedBallEntry(entity.jsonPayload, parsed)
        return parsed
    }

    private suspend fun loadAuthoritativeMatch(matchId: String, fallback: Match? = null): Match? {
        val entity = matchDao.getMatchById(matchId)
        return entity?.let { matchRepository.parseMatchCached(it) } ?: fallback
    }

    private fun scheduleCloudSync(matchId: String) {
        if (firestore == null || matchId.isBlank() || matchId.startsWith("OFFLINE_") || matchId == "OFFLINE") return
        pendingResyncFlags.add(matchId)
        if (!inFlightSyncMatches.add(matchId)) {
            return
        }
        scope.launch {
            try {
                while (pendingResyncFlags.remove(matchId)) {
                    syncPendingEvents(matchId)
                }
            } catch (e: Exception) {
                android.util.Log.w("ScoringRepository", "Background cloud sync deferred for $matchId: ${e.message}")
            } finally {
                inFlightSyncMatches.remove(matchId)
                if (pendingResyncFlags.contains(matchId)) {
                    scheduleCloudSync(matchId)
                }
            }
        }
    }

    /**
     * Attaches a real-time Firestore listener for live ball-by-ball events for viewers,
     * while strictly protecting local PENDING / DELETED events from being overwritten or resurrected.
     */
    fun syncMatchBallEventsFromCloud(matchId: String) {
        val fs = firestore ?: return
        if (!enableCloudListeners) return
        if (matchId.isBlank() || matchId.startsWith("OFFLINE_") || matchId == "OFFLINE") return
        if (activeBallListeners.containsKey(matchId)) return
        val registration = fs.collection("matches").document(matchId)
            .collection("ballEvents")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val removedIds = snapshot.documentChanges
                    .filter { it.type == DocumentChange.Type.REMOVED }
                    .map { it.document.id }
                val changedDocs = snapshot.documentChanges
                    .filter { it.type != DocumentChange.Type.REMOVED }
                    .map { it.document }
                if (removedIds.isEmpty() && changedDocs.isEmpty()) return@addSnapshotListener

                scope.launch {
                    getMatchMutex(matchId).withLock {
                        val localMap = ballEventDao.getAllRawBallEventsForMatch(matchId).associateBy { it.id }

                        for (remId in removedIds) {
                            val existingLocal = localMap[remId]
                            if (existingLocal != null && existingLocal.syncStatus == BallEvent.SYNC_STATUS_SYNCED) {
                                ballEventDao.deleteBallEvent(remId)
                                ballEventCache.remove(remId)
                            }
                        }

                        val toInsert = mutableListOf<BallEventEntity>()
                        for (doc in changedDocs) {
                            val ball = doc.getString("ballJson")?.let { JsonHelper.fromJson<BallEvent>(it) }
                                ?: try {
                                    doc.toObject(BallEvent::class.java)
                                } catch (e: Exception) {
                                    null
                                } ?: doc.data?.let { JsonHelper.fromJson<BallEvent>(JsonHelper.toJson(it)) }
                                ?: continue

                            val existingLocal = localMap[ball.id]
                            if (existingLocal != null &&
                                (existingLocal.syncStatus == BallEvent.SYNC_STATUS_PENDING ||
                                    existingLocal.syncStatus == BallEvent.SYNC_STATUS_FAILED ||
                                    existingLocal.syncStatus == BallEvent.SYNC_STATUS_DELETED)
                            ) {
                                // Never clobber un-synced local edits or resurrect undone balls!
                                continue
                            }

                            val normalizedJson = JsonHelper.toJson(ball.copy(syncStatus = BallEvent.SYNC_STATUS_SYNCED))
                            if (existingLocal != null &&
                                existingLocal.syncStatus == BallEvent.SYNC_STATUS_SYNCED &&
                                existingLocal.jsonPayload == normalizedJson
                            ) {
                                continue
                            }

                            ballEventCache[ball.id] = CachedBallEntry(
                                normalizedJson,
                                ball.copy(syncStatus = BallEvent.SYNC_STATUS_SYNCED)
                            )
                            toInsert.add(
                                BallEventEntity(
                                    id = ball.id,
                                    matchId = ball.matchId,
                                    inningsNumber = ball.inningsNumber,
                                    ballSeq = ball.ballSeq,
                                    syncStatus = BallEvent.SYNC_STATUS_SYNCED,
                                    jsonPayload = normalizedJson,
                                    timestamp = ball.timestamp
                                )
                            )
                        }
                        if (toInsert.isNotEmpty()) {
                            ballEventDao.insertBallEvents(toInsert)
                        }
                    }
                }
            }
        activeBallListeners[matchId] = registration
    }

    fun getBallEventsFlow(matchId: String, innings: Int): Flow<List<BallEvent>> {
        syncMatchBallEventsFromCloud(matchId)
        return ballEventDao.getBallEventsFlow(matchId, innings)
            .map { list -> list.mapNotNull { parseBallCached(it) } }
            .flowOn(Dispatchers.Default)
            .distinctUntilChanged()
    }

    fun getPendingCountFlow(matchId: String): Flow<Int> {
        return ballEventDao.getPendingCountFlow(matchId)
            .distinctUntilChanged()
    }

    suspend fun recordBallEvent(
        match: Match,
        runsOffBat: Int,
        extraType: ExtraType,
        extraRuns: Int,
        isWicket: Boolean,
        dismissalType: DismissalType?,
        dismissedPlayerId: String?,
        dismissedPlayerName: String?,
        incomingBatterId: String?,
        incomingBatterName: String?,
        assistingPlayerName: String?,
        scorerId: String,
        commentaryLanguage: String = "bn"
    ): Result<Match> = withContext(Dispatchers.IO) {
        getMatchMutex(match.id).withLock {
            val activeMatch = loadAuthoritativeMatch(match.id, match) ?: match

            // Head Scorer & Authorized Sub-Scorer Enforcement (Only for online matches)
            val isOfflineMatch = activeMatch.isOfflineScored || !activeMatch.isPublic || activeMatch.id.startsWith("OFFLINE_") || activeMatch.id == "OFFLINE"
            if (!isOfflineMatch && scorerId.isNotBlank() && !activeMatch.canUserScore(scorerId)) {
                return@withLock Result.failure(SecurityException("Unauthorized: Only the Head Scorer (${activeMatch.ownerName}) or authorized Sub Scorers can record scores for this match."))
            }

            if (activeMatch.status == MatchStatus.INNINGS_BREAK) {
                return@withLock Result.failure(IllegalStateException("১ম ইনিংস সমাপ্ত হয়েছে! ২য় ইনিংস শুরু করতে কনফার্ম করুন।"))
            }
            if (activeMatch.status == MatchStatus.COMPLETED) {
                return@withLock Result.failure(IllegalStateException("ম্যাচ ইতোমধ্যে সম্পন্ন হয়েছে!"))
            }
            if (activeMatch.currentBowlerId.isNullOrBlank()) {
                return@withLock Result.failure(IllegalStateException("ওভার সমাপ্ত! পরবর্তী বোলার নির্বাচন করুন।"))
            }

            val inningsNumber = activeMatch.currentInningsNumber
            val currentEvents = ballEventDao.getBallEventsList(activeMatch.id, inningsNumber)
                .mapNotNull { parseBallCached(it) }
                .sortedBy { it.ballSeq }

            val isRetiredHurt = dismissalType == DismissalType.RETIRED_HURT
            val isRetiredOut = dismissalType == DismissalType.RETIRED_OUT
            val isRetiredEvent = isRetiredHurt || isRetiredOut
            val isLegal = extraType != ExtraType.WIDE && extraType != ExtraType.NO_BALL && !isRetiredEvent
            val currentLegalBalls = currentEvents.count {
                it.isLegalDelivery && it.dismissalType != DismissalType.RETIRED_HURT && it.dismissalType != DismissalType.RETIRED_OUT
            }
            if (currentLegalBalls / 6 >= activeMatch.totalOvers) {
                return@withLock Result.failure(IllegalStateException("নির্ধারিত ওভার সমাপ্ত হয়েছে!"))
            }

            val nextLegalBalls = if (isLegal) currentLegalBalls + 1 else currentLegalBalls
            val overNum = currentLegalBalls / 6
            val ballInOver = if (isLegal) (currentLegalBalls % 6) + 1 else (currentLegalBalls % 6)
            val nextBallSeq = (currentEvents.maxOfOrNull { it.ballSeq } ?: 0) + 1

            val ballId = "${activeMatch.id}_inn${inningsNumber}_b${nextBallSeq}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}"

            val strikerId = activeMatch.currentStrikerId ?: ""
            val strikerName = activeMatch.currentStrikerName ?: ""
            val nonStrikerId = activeMatch.currentNonStrikerId ?: ""
            val nonStrikerName = activeMatch.currentNonStrikerName ?: ""
            val bowlerId = activeMatch.currentBowlerId ?: ""
            val bowlerName = activeMatch.currentBowlerName ?: ""

            val initialSyncStatus = if (isOfflineMatch) BallEvent.SYNC_STATUS_SYNCED else BallEvent.SYNC_STATUS_PENDING
            val draftBall = BallEvent(
                id = ballId,
                matchId = activeMatch.id,
                inningsNumber = inningsNumber,
                ballSeq = nextBallSeq,
                overNumber = overNum,
                ballInOver = ballInOver,
                bowlerId = bowlerId,
                bowlerName = bowlerName,
                strikerId = strikerId,
                strikerName = strikerName,
                nonStrikerId = nonStrikerId,
                nonStrikerName = nonStrikerName,
                runsOffBat = runsOffBat,
                extraType = extraType,
                extraRuns = extraRuns,
                isLegalDelivery = isLegal,
                isWicket = isWicket,
                dismissalType = dismissalType,
                dismissedPlayerId = dismissedPlayerId,
                dismissedPlayerName = dismissedPlayerName,
                assistingPlayerId = null,
                assistingPlayerName = assistingPlayerName,
                commentary = "",
                scorerId = scorerId,
                createdByUid = scorerId,
                timestamp = System.currentTimeMillis(),
                syncStatus = initialSyncStatus
            )
            val newBall = draftBall.copy(
                commentary = com.example.util.CommentaryHelper.formatCommentary(draftBall, commentaryLanguage)
            )

            // 1. Save to Room database synchronously BEFORE returning
            val ballJson = JsonHelper.toJson(newBall)
            ballEventCache[newBall.id] = CachedBallEntry(ballJson, newBall)
            ballEventDao.insertBallEvent(
                BallEventEntity(
                    id = newBall.id,
                    matchId = newBall.matchId,
                    inningsNumber = newBall.inningsNumber,
                    ballSeq = newBall.ballSeq,
                    syncStatus = initialSyncStatus,
                    jsonPayload = ballJson,
                    timestamp = newBall.timestamp
                )
            )
            _latestBallEventFlow.value = newBall

            val updatedBallList = currentEvents + newBall

            // 2. Recalculate scorecard
            val recalculatedScorecard = recalculateScorecard(
                inningsNumber = inningsNumber,
                match = activeMatch,
                ballEvents = updatedBallList
            )

        // 3. Determine strike rotation
        var nextStrikerId: String? = strikerId
        var nextStrikerName: String? = strikerName
        var nextNonStrikerId: String? = nonStrikerId
        var nextNonStrikerName: String? = nonStrikerName

        if ((isWicket || isRetiredHurt || dismissalType == DismissalType.RETIRED_OUT) && incomingBatterId != null && incomingBatterName != null) {
            if (dismissedPlayerId == strikerId) {
                nextStrikerId = incomingBatterId
                nextStrikerName = incomingBatterName
            } else {
                nextNonStrikerId = incomingBatterId
                nextNonStrikerName = incomingBatterName
            }
        }

        // Strike rotates on odd running runs (including completed runs before run-out)
        val completedExtraRuns = if (extraType == ExtraType.WIDE && extraRuns > 1) (extraRuns - 1)
        else if (extraType == ExtraType.NO_BALL && extraRuns > 1) (extraRuns - 1)
        else if (extraType == ExtraType.BYE || extraType == ExtraType.LEG_BYE) extraRuns
        else 0
        val totalCompletedRunningRuns = runsOffBat + completedExtraRuns
        val shouldRotateForRuns = (totalCompletedRunningRuns % 2 != 0) && !isRetiredEvent

        if (shouldRotateForRuns) {
            val tempId = nextStrikerId
            val tempName = nextStrikerName
            nextStrikerId = nextNonStrikerId
            nextStrikerName = nextNonStrikerName
            nextNonStrikerId = tempId
            nextNonStrikerName = tempName
        }

        // Over completed? (6 legal deliveries)
        val isOverComplete = isLegal && (nextLegalBalls % 6 == 0)
        if (isOverComplete) {
            // Swap batsmen at end of over
            val tempId = nextStrikerId
            val tempName = nextStrikerName
            nextStrikerId = nextNonStrikerId
            nextStrikerName = nextNonStrikerName
            nextNonStrikerId = tempId
            nextNonStrikerName = tempName
        }

        val totalLegalOversCompleted = nextLegalBalls / 6
        val maxOversReached = totalLegalOversCompleted >= activeMatch.totalOvers
        val maxWickets = activeMatch.getMaxWicketsForInnings(inningsNumber)
        val allOut = recalculatedScorecard.wickets >= maxWickets
        val inningsEnded = maxOversReached || allOut

        var newStatus = activeMatch.status
        val newInningsNumber = activeMatch.currentInningsNumber
        var resultText = activeMatch.resultDescription
        var winnerTeamId = activeMatch.winnerTeamId
        var targetRuns = activeMatch.targetRuns

        if (inningsNumber == 1) {
            if (inningsEnded) {
                newStatus = MatchStatus.INNINGS_BREAK
                targetRuns = recalculatedScorecard.totalRuns + 1
            } else {
                newStatus = MatchStatus.LIVE
            }
        } else {
            // Second Innings - Winner decided by maximum runs
            val firstRuns = activeMatch.firstInnings?.totalRuns ?: 0
            val secondRuns = recalculatedScorecard.totalRuns
            val firstTeam = if (activeMatch.battingFirstTeamId == activeMatch.teamA.id) activeMatch.teamA else activeMatch.teamB
            val chasingTeam = if (activeMatch.battingFirstTeamId == activeMatch.teamA.id) activeMatch.teamB else activeMatch.teamA

            val target = activeMatch.targetRuns ?: (firstRuns + 1)
            if (secondRuns >= target) {
                newStatus = MatchStatus.COMPLETED
                winnerTeamId = chasingTeam.id
                val wicketsRemaining = maxOf(1, maxWickets - recalculatedScorecard.wickets)
                resultText = if (allOut || maxOversReached) {
                    val margin = secondRuns - firstRuns
                    "${chasingTeam.name} won by $margin runs"
                } else {
                    "${chasingTeam.name} won by $wicketsRemaining wickets"
                }
            } else if (inningsEnded) {
                newStatus = MatchStatus.COMPLETED
                if (firstRuns > secondRuns) {
                    val diff = firstRuns - secondRuns
                    winnerTeamId = firstTeam.id
                    resultText = "${firstTeam.name} won by $diff runs"
                } else if (secondRuns > firstRuns) {
                    val diff = secondRuns - firstRuns
                    winnerTeamId = chasingTeam.id
                    resultText = "${chasingTeam.name} won by $diff runs"
                } else {
                    winnerTeamId = null
                    resultText = "Match Tied! (${firstRuns} runs each)"
                }
            } else {
                newStatus = MatchStatus.LIVE
            }
        }

        // When innings has ended (either all out N-1 wickets or overs completed), clear active crease
        if (inningsEnded || newStatus == MatchStatus.COMPLETED || newStatus == MatchStatus.INNINGS_BREAK) {
            nextStrikerId = null
            nextStrikerName = null
            nextNonStrikerId = null
            nextNonStrikerName = null
        }

        val recentBadges = updatedBallList.takeLast(12).map { it.displayBadgeText }

        val updatedMatch = activeMatch.copy(
            status = newStatus,
            currentInningsNumber = newInningsNumber,
            currentStrikerId = nextStrikerId,
            currentStrikerName = nextStrikerName,
            currentNonStrikerId = nextNonStrikerId,
            currentNonStrikerName = nextNonStrikerName,
            // After every complete over or innings end, bowler must be changed - cleared
            currentBowlerId = if (isOverComplete || inningsEnded || newStatus != MatchStatus.LIVE) null else bowlerId,
            currentBowlerName = if (isOverComplete || inningsEnded || newStatus != MatchStatus.LIVE) null else bowlerName,
            recentBallsSummary = recentBadges,
            dismissedPlayerIds = if (isWicket && dismissedPlayerId != null) (activeMatch.dismissedPlayerIds + dismissedPlayerId).distinct() else activeMatch.dismissedPlayerIds,
            targetRuns = targetRuns,
            resultDescription = resultText,
            winnerTeamId = winnerTeamId,
            firstInnings = if (inningsNumber == 1) recalculatedScorecard else activeMatch.firstInnings,
            secondInnings = if (inningsNumber == 2) recalculatedScorecard else activeMatch.secondInnings,
            lastSyncTimestamp = System.currentTimeMillis()
        )

        // Commit atomically to Room inside MatchRepository write lock
        val committed = matchRepository.commitLocalMatch(updatedMatch, allowProgressRollback = false)

        // Schedule serialized, coalesced cloud sync ONLY if online public match
        if (committed.isPublic && !committed.isOfflineScored) {
            scheduleCloudSync(committed.id)
        }

        return@withLock Result.success(committed)
        }
    }

    suspend fun undoLastBall(match: Match, scorerId: String = ""): Result<Match> = withContext(Dispatchers.IO) {
        getMatchMutex(match.id).withLock {
            val activeMatch = loadAuthoritativeMatch(match.id, match) ?: match
            val isOfflineMatch = activeMatch.isOfflineScored || !activeMatch.isPublic || activeMatch.id.startsWith("OFFLINE_") || activeMatch.id == "OFFLINE"
            if (!isOfflineMatch && scorerId.isNotBlank() && !activeMatch.canUserScore(scorerId)) {
                return@withLock Result.failure(SecurityException("Unauthorized: Only the Head Scorer (${activeMatch.ownerName}) or authorized Sub Scorers can undo deliveries."))
            }

            val innings = activeMatch.currentInningsNumber
            val currentEvents = ballEventDao.getBallEventsList(activeMatch.id, innings)
                .mapNotNull { parseBallCached(it) }
                .sortedBy { it.ballSeq }

            if (currentEvents.isEmpty()) {
                return@withLock Result.failure(Exception("No balls to undo in this innings"))
            }

            val lastBall = currentEvents.last()
            val fs = firestore
            if (isOfflineMatch || fs == null) {
                ballEventDao.deleteBallEvent(lastBall.id)
                ballEventCache.remove(lastBall.id)
            } else {
                // Mark as DELETED tombstone so Firestore cannot resurrect this ball before delete confirms
                ballEventDao.markEventDeleted(lastBall.id)
            }

            val remainingEvents = currentEvents.dropLast(1)
            val recalculatedScorecard = recalculateScorecard(innings, activeMatch, remainingEvents)

            // Restore exact crease & bowler state immediately prior to lastBall
            val restoredStrikerId = lastBall.strikerId.ifBlank { activeMatch.currentStrikerId }
            val restoredStrikerName = lastBall.strikerName.ifBlank { activeMatch.currentStrikerName }
            val restoredNonStrikerId = lastBall.nonStrikerId.ifBlank { activeMatch.currentNonStrikerId }
            val restoredNonStrikerName = lastBall.nonStrikerName.ifBlank { activeMatch.currentNonStrikerName }
            val restoredBowlerId = lastBall.bowlerId.ifBlank { activeMatch.currentBowlerId }
            val restoredBowlerName = lastBall.bowlerName.ifBlank { activeMatch.currentBowlerName }

            val rebuiltDismissedIds = remainingEvents
                .filter { it.isWicket && it.dismissalType != DismissalType.RETIRED_HURT && !it.dismissedPlayerId.isNullOrBlank() }
                .mapNotNull { it.dismissedPlayerId }
                .distinct()

            val updatedMatch = activeMatch.copy(
                status = if (activeMatch.status == MatchStatus.COMPLETED || activeMatch.status == MatchStatus.INNINGS_BREAK) MatchStatus.LIVE else activeMatch.status,
                resultDescription = if (activeMatch.status == MatchStatus.COMPLETED) "" else activeMatch.resultDescription,
                winnerTeamId = if (activeMatch.status == MatchStatus.COMPLETED) null else activeMatch.winnerTeamId,
                targetRuns = if (innings == 1 && activeMatch.status == MatchStatus.INNINGS_BREAK) null else activeMatch.targetRuns,
                firstInnings = if (innings == 1) recalculatedScorecard else activeMatch.firstInnings,
                secondInnings = if (innings == 2) recalculatedScorecard else activeMatch.secondInnings,
                currentStrikerId = restoredStrikerId,
                currentStrikerName = restoredStrikerName,
                currentNonStrikerId = restoredNonStrikerId,
                currentNonStrikerName = restoredNonStrikerName,
                currentBowlerId = restoredBowlerId,
                currentBowlerName = restoredBowlerName,
                dismissedPlayerIds = rebuiltDismissedIds,
                recentBallsSummary = remainingEvents.takeLast(12).map { it.displayBadgeText },
                lastSyncTimestamp = System.currentTimeMillis()
            )

            val committed = matchRepository.commitLocalMatch(updatedMatch, allowProgressRollback = true)

            if (!isOfflineMatch && fs != null) {
                scheduleCloudSync(committed.id)
            }

            return@withLock Result.success(committed)
        }
    }

    suspend fun editBallEvent(
        match: Match,
        ballId: String,
        newRunsOffBat: Int,
        newExtraType: ExtraType,
        newExtraRuns: Int,
        newIsWicket: Boolean,
        newDismissalType: DismissalType?,
        reason: String,
        userId: String,
        userName: String
    ): Result<Match> = withContext(Dispatchers.IO) {
        getMatchMutex(match.id).withLock {
            val activeMatch = loadAuthoritativeMatch(match.id, match) ?: match
            val isOfflineMatch = activeMatch.isOfflineScored || !activeMatch.isPublic || activeMatch.id.startsWith("OFFLINE_") || activeMatch.id == "OFFLINE"
            if (!isOfflineMatch && userId.isNotBlank() && !activeMatch.canUserScore(userId)) {
                return@withLock Result.failure(SecurityException("Unauthorized: Only the Head Scorer (${activeMatch.ownerName}) or authorized Sub Scorers can edit deliveries."))
            }

            val innings = activeMatch.currentInningsNumber
            val currentEvents = ballEventDao.getBallEventsList(activeMatch.id, innings)
                .mapNotNull { parseBallCached(it) }
                .sortedBy { it.ballSeq }

            val targetBall = currentEvents.find { it.id == ballId }
                ?: return@withLock Result.failure(Exception("Ball event not found"))

            val isLegal = newExtraType != ExtraType.WIDE && newExtraType != ExtraType.NO_BALL &&
                newDismissalType != DismissalType.RETIRED_HURT && newDismissalType != DismissalType.RETIRED_OUT
            val syncStat = if (isOfflineMatch) BallEvent.SYNC_STATUS_SYNCED else BallEvent.SYNC_STATUS_PENDING
            val updatedBall = targetBall.copy(
                runsOffBat = newRunsOffBat,
                extraType = newExtraType,
                extraRuns = newExtraRuns,
                isLegalDelivery = isLegal,
                isWicket = newIsWicket,
                dismissalType = newDismissalType,
                syncStatus = syncStat
            )

            // Save updated ball locally
            val updatedBallJson = JsonHelper.toJson(updatedBall)
            ballEventCache[updatedBall.id] = CachedBallEntry(updatedBallJson, updatedBall)
            ballEventDao.insertBallEvent(
                BallEventEntity(
                    id = updatedBall.id,
                    matchId = updatedBall.matchId,
                    inningsNumber = updatedBall.inningsNumber,
                    ballSeq = updatedBall.ballSeq,
                    syncStatus = syncStat,
                    jsonPayload = updatedBallJson,
                    timestamp = System.currentTimeMillis()
                )
            )

            val updatedList = currentEvents.map { if (it.id == ballId) updatedBall else it }
            val recalculated = recalculateScorecard(innings, activeMatch, updatedList)

            val correction = CorrectionRecord(
                id = "${ballId}_corr_${System.currentTimeMillis()}",
                matchId = activeMatch.id,
                ballId = ballId,
                userId = userId,
                userName = userName,
                timestamp = System.currentTimeMillis(),
                reason = reason,
                originalDescription = "Runs: ${targetBall.runsOffBat}, Extra: ${targetBall.extraType}, Wicket: ${targetBall.isWicket}",
                updatedDescription = "Runs: $newRunsOffBat, Extra: $newExtraType, Wicket: $newIsWicket"
            )

            val updatedMatch = activeMatch.copy(
                firstInnings = if (innings == 1) recalculated else activeMatch.firstInnings,
                secondInnings = if (innings == 2) recalculated else activeMatch.secondInnings,
                recentBallsSummary = updatedList.takeLast(12).map { it.displayBadgeText },
                lastSyncTimestamp = System.currentTimeMillis()
            )

            val committed = matchRepository.commitLocalMatch(updatedMatch, allowProgressRollback = true)

            val fs = firestore
            if (!isOfflineMatch && fs != null) {
                scheduleCloudSync(committed.id)
                scope.launch {
                    try {
                        fs.collection("matches").document(committed.id)
                            .collection("corrections").document(correction.id).set(correction).await()
                    } catch (_: Exception) {
                        // Best-effort audit log
                    }
                }
            }

            return@withLock Result.success(committed)
        }
    }

    private fun BallEvent.toFirestoreMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        map["id"] = id
        map["matchId"] = matchId
        map["inningsNumber"] = inningsNumber
        map["ballSeq"] = ballSeq
        map["runsOffBat"] = runsOffBat
        map["extraType"] = extraType.name
        map["extraRuns"] = extraRuns
        map["isLegalDelivery"] = isLegalDelivery
        map["isWicket"] = isWicket
        map["timestamp"] = timestamp
        map["ballJson"] = JsonHelper.toJson(this)
        return map
    }

    /**
     * Flushes pending/failed/deleted ball events AND the latest Match document in strict durability order:
     * 1) Upload all pending ball events & remove deleted tombstones in Firestore
     * 2) Push latest authoritative Match doc to Firestore
     * 3) ONLY AFTER Match push succeeds, mark uploaded balls SYNCED & purge deleted tombstones
     *    (if Match push fails, pending status stays intact so Room can NEVER be rolled back!)
     */
    suspend fun syncPendingEvents(matchId: String): Int = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext 0
        getCloudSyncMutex(matchId).withLock {
            val activeMatch = loadAuthoritativeMatch(matchId) ?: return@withLock 0
            if (activeMatch.isOfflineScored || !activeMatch.isPublic || matchId.startsWith("OFFLINE_") || matchId == "OFFLINE") {
                return@withLock 0
            }

            val pending = ballEventDao.getPendingOrDeletedSyncEventsForMatch(matchId)
            val uploadedItems = mutableListOf<BallEventEntity>()
            val deletedIds = mutableListOf<String>()

            for (item in pending) {
                try {
                    if (item.syncStatus == BallEvent.SYNC_STATUS_DELETED) {
                        fs.collection("matches").document(matchId)
                            .collection("ballEvents").document(item.id).delete().await()
                        deletedIds.add(item.id)
                    } else {
                        val event = parseBallCached(item) ?: continue
                        val cleanEvent = event.copy(syncStatus = BallEvent.SYNC_STATUS_SYNCED)
                        fs.collection("matches").document(matchId)
                            .collection("ballEvents").document(cleanEvent.id).set(cleanEvent.toFirestoreMap()).await()
                        uploadedItems.add(item)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("ScoringRepository", "Pending ball sync paused for $matchId: ${e.message}")
                    break
                }
            }

            val matchPushed = matchRepository.pushLatestMatchToCloud(matchId)
            var syncedCount = 0
            if (matchPushed && (uploadedItems.isNotEmpty() || deletedIds.isNotEmpty())) {
                getMatchMutex(matchId).withLock {
                    val currentMap = ballEventDao.getAllRawBallEventsForMatch(matchId).associateBy { it.id }
                    for (delId in deletedIds) {
                        val cur = currentMap[delId]
                        if (cur != null && cur.syncStatus == BallEvent.SYNC_STATUS_DELETED) {
                            ballEventDao.deleteBallEvent(delId)
                            ballEventCache.remove(delId)
                            syncedCount++
                        }
                    }
                    for (upItem in uploadedItems) {
                        val cur = currentMap[upItem.id]
                        if (cur != null &&
                            cur.syncStatus != BallEvent.SYNC_STATUS_DELETED &&
                            cur.jsonPayload == upItem.jsonPayload
                        ) {
                            ballEventDao.markEventSynced(upItem.id)
                            syncedCount++
                        }
                    }
                }
            }
            syncedCount
        }
    }

    /**
     * Global recovery & sync sweep (runs on startup, foreground resume, and network reconnection).
     * Ensures every un-synced ball event across all matches is reconciled locally and flushed to cloud.
     */
    suspend fun syncAllPendingMatches(): Int = withContext(Dispatchers.IO) {
        var totalSynced = 0
        try {
            val pendingMatchIds = ballEventDao.getPendingSyncMatchIds()
            for (id in pendingMatchIds) {
                reconcileMatchFromLocalBalls(id)
                totalSynced += syncPendingEvents(id)
            }
            totalSynced += matchRepository.syncPendingLocalMatchesToCloudSuspend()
        } catch (e: Exception) {
            android.util.Log.e("ScoringRepository", "syncAllPendingMatches error: ${e.message}")
        }
        totalSynced
    }

    /**
     * Self-healing consistency recovery:
     * Ensures MatchEntity scorecards in Room match the durable ball_events log (source of truth).
     * Recovers immediately if any crash, forced close, or legacy stale sync caused a scorecard lag.
     */
    suspend fun reconcileMatchFromLocalBalls(matchId: String): Match? = withContext(Dispatchers.IO) {
        getMatchMutex(matchId).withLock {
            val activeMatch = loadAuthoritativeMatch(matchId) ?: return@withLock null
            val inn1Balls = ballEventDao.getBallEventsList(matchId, 1)
                .mapNotNull { parseBallCached(it) }
                .sortedBy { it.ballSeq }
            val inn2Balls = ballEventDao.getBallEventsList(matchId, 2)
                .mapNotNull { parseBallCached(it) }
                .sortedBy { it.ballSeq }

            if (inn1Balls.isEmpty() && inn2Balls.isEmpty()) {
                return@withLock activeMatch
            }

            val recalc1 = if (inn1Balls.isNotEmpty()) recalculateScorecard(1, activeMatch, inn1Balls) else activeMatch.firstInnings
            val recalc2 = if (inn2Balls.isNotEmpty()) recalculateScorecard(2, activeMatch, inn2Balls) else activeMatch.secondInnings

            fun differs(existing: InningsScorecard?, recalc: InningsScorecard?): Boolean {
                if (recalc == null) return false
                if (existing == null) return true
                return existing.totalRuns != recalc.totalRuns ||
                    existing.wickets != recalc.wickets ||
                    existing.legalBalls != recalc.legalBalls ||
                    existing.wides != recalc.wides ||
                    existing.noBalls != recalc.noBalls ||
                    existing.byes != recalc.byes ||
                    existing.legByes != recalc.legByes ||
                    existing.penalties != recalc.penalties ||
                    existing.batters.size != recalc.batters.size ||
                    existing.bowlers.size != recalc.bowlers.size
            }

            val needsInnings2Fix = inn2Balls.isNotEmpty() && activeMatch.currentInningsNumber < 2
            val needsInn1Fix = inn1Balls.isNotEmpty() && differs(activeMatch.firstInnings, recalc1)
            val needsInn2Fix = inn2Balls.isNotEmpty() && differs(activeMatch.secondInnings, recalc2)

            if (!needsInnings2Fix && !needsInn1Fix && !needsInn2Fix) {
                return@withLock activeMatch
            }

            val targetInnings = if (inn2Balls.isNotEmpty()) 2 else activeMatch.currentInningsNumber
            val activeBalls = if (targetInnings == 2) inn2Balls else inn1Balls
            val activeCard = if (targetInnings == 2) recalc2 else recalc1
            val maxWickets = activeMatch.getMaxWicketsForInnings(targetInnings)
            val totalOversDone = (activeCard?.legalBalls ?: 0) / 6 >= activeMatch.totalOvers
            val allOut = (activeCard?.wickets ?: 0) >= maxWickets

            var repairedStatus = activeMatch.status
            var repairedTarget = activeMatch.targetRuns
            var repairedWinner = activeMatch.winnerTeamId
            var repairedResult = activeMatch.resultDescription

            if (targetInnings == 1) {
                if (totalOversDone || allOut) {
                    repairedStatus = MatchStatus.INNINGS_BREAK
                    repairedTarget = (recalc1?.totalRuns ?: 0) + 1
                } else {
                    repairedStatus = MatchStatus.LIVE
                }
            } else {
                val firstRuns = recalc1?.totalRuns ?: 0
                val secondRuns = recalc2?.totalRuns ?: 0
                val target = repairedTarget ?: (firstRuns + 1)
                repairedTarget = target
                val firstTeam = if (activeMatch.battingFirstTeamId == activeMatch.teamA.id) activeMatch.teamA else activeMatch.teamB
                val chasingTeam = if (activeMatch.battingFirstTeamId == activeMatch.teamA.id) activeMatch.teamB else activeMatch.teamA
                if (secondRuns >= target) {
                    repairedStatus = MatchStatus.COMPLETED
                    repairedWinner = chasingTeam.id
                    val wktsRem = maxOf(1, maxWickets - (recalc2?.wickets ?: 0))
                    repairedResult = if (allOut || totalOversDone) {
                        "${chasingTeam.name} won by ${secondRuns - firstRuns} runs"
                    } else {
                        "${chasingTeam.name} won by $wktsRem wickets"
                    }
                } else if (totalOversDone || allOut) {
                    repairedStatus = MatchStatus.COMPLETED
                    if (firstRuns > secondRuns) {
                        repairedWinner = firstTeam.id
                        repairedResult = "${firstTeam.name} won by ${firstRuns - secondRuns} runs"
                    } else if (secondRuns > firstRuns) {
                        repairedWinner = chasingTeam.id
                        repairedResult = "${chasingTeam.name} won by ${secondRuns - firstRuns} runs"
                    } else {
                        repairedWinner = null
                        repairedResult = "Match Tied! ($firstRuns runs each)"
                    }
                } else {
                    repairedStatus = MatchStatus.LIVE
                }
            }

            val rebuiltDismissed = activeBalls
                .filter { it.isWicket && it.dismissalType != DismissalType.RETIRED_HURT && !it.dismissedPlayerId.isNullOrBlank() }
                .mapNotNull { it.dismissedPlayerId }
                .distinct()

            val lastBall = activeBalls.lastOrNull()
            val shouldClearCrease = repairedStatus != MatchStatus.LIVE
            val isEndOfOver = lastBall != null &&
                lastBall.isLegalDelivery &&
                lastBall.dismissalType != DismissalType.RETIRED_HURT &&
                lastBall.dismissalType != DismissalType.RETIRED_OUT &&
                (activeCard?.legalBalls ?: 0) % 6 == 0

            val repaired = activeMatch.copy(
                currentInningsNumber = targetInnings,
                status = repairedStatus,
                targetRuns = repairedTarget,
                winnerTeamId = repairedWinner,
                resultDescription = repairedResult,
                firstInnings = recalc1,
                secondInnings = recalc2,
                dismissedPlayerIds = rebuiltDismissed,
                recentBallsSummary = activeBalls.takeLast(12).map { it.displayBadgeText },
                currentStrikerId = if (shouldClearCrease) null else activeMatch.currentStrikerId ?: lastBall?.strikerId,
                currentStrikerName = if (shouldClearCrease) null else activeMatch.currentStrikerName ?: lastBall?.strikerName,
                currentNonStrikerId = if (shouldClearCrease) null else activeMatch.currentNonStrikerId ?: lastBall?.nonStrikerId,
                currentNonStrikerName = if (shouldClearCrease) null else activeMatch.currentNonStrikerName ?: lastBall?.nonStrikerName,
                currentBowlerId = if (shouldClearCrease || isEndOfOver) null else activeMatch.currentBowlerId ?: lastBall?.bowlerId,
                currentBowlerName = if (shouldClearCrease || isEndOfOver) null else activeMatch.currentBowlerName ?: lastBall?.bowlerName
            )

            matchRepository.commitLocalMatch(repaired, allowProgressRollback = true)
        }
    }

    suspend fun swapStrike(matchId: String): Result<Match> = withContext(Dispatchers.IO) {
        getMatchMutex(matchId).withLock {
            val active = loadAuthoritativeMatch(matchId) ?: return@withLock Result.failure(Exception("Match not found"))
            val updated = active.copy(
                currentStrikerId = active.currentNonStrikerId,
                currentStrikerName = active.currentNonStrikerName,
                currentNonStrikerId = active.currentStrikerId,
                currentNonStrikerName = active.currentStrikerName
            )
            val committed = matchRepository.commitLocalMatch(updated, allowProgressRollback = false)
            if (committed.isPublic && !committed.isOfflineScored) {
                scheduleCloudSync(committed.id)
            }
            Result.success(committed)
        }
    }

    suspend fun selectBowler(matchId: String, bowlerId: String, bowlerName: String): Result<Match> = withContext(Dispatchers.IO) {
        getMatchMutex(matchId).withLock {
            val active = loadAuthoritativeMatch(matchId) ?: return@withLock Result.failure(Exception("Match not found"))
            val updated = active.copy(
                currentBowlerId = bowlerId,
                currentBowlerName = bowlerName
            )
            val committed = matchRepository.commitLocalMatch(updated, allowProgressRollback = false)
            if (committed.isPublic && !committed.isOfflineScored) {
                scheduleCloudSync(committed.id)
            }
            Result.success(committed)
        }
    }

    suspend fun selectOpeningPlayers(
        matchId: String,
        strikerId: String,
        strikerName: String,
        nonStrikerId: String,
        nonStrikerName: String,
        bowlerId: String,
        bowlerName: String
    ): Result<Match> = withContext(Dispatchers.IO) {
        getMatchMutex(matchId).withLock {
            val active = loadAuthoritativeMatch(matchId) ?: return@withLock Result.failure(Exception("Match not found"))
            val updated = active.copy(
                status = MatchStatus.LIVE,
                currentStrikerId = strikerId,
                currentStrikerName = strikerName,
                currentNonStrikerId = nonStrikerId,
                currentNonStrikerName = nonStrikerName,
                currentBowlerId = bowlerId,
                currentBowlerName = bowlerName
            )
            val committed = matchRepository.commitLocalMatch(updated, allowProgressRollback = false)
            if (committed.isPublic && !committed.isOfflineScored) {
                scheduleCloudSync(committed.id)
            }
            Result.success(committed)
        }
    }

    suspend fun startSecondInnings(matchId: String): Result<Match> = withContext(Dispatchers.IO) {
        getMatchMutex(matchId).withLock {
            val active = loadAuthoritativeMatch(matchId) ?: return@withLock Result.failure(Exception("Match not found"))
            val target = active.targetRuns ?: ((active.firstInnings?.totalRuns ?: 0) + 1)
            val chasingTeam = if (active.battingFirstTeamId == active.teamA.id) active.teamB else active.teamA
            val bowlingTeam = if (active.battingFirstTeamId == active.teamA.id) active.teamA else active.teamB
            val updated = active.copy(
                currentInningsNumber = 2,
                targetRuns = target,
                status = MatchStatus.LIVE,
                currentStrikerId = null,
                currentStrikerName = null,
                currentNonStrikerId = null,
                currentNonStrikerName = null,
                currentBowlerId = null,
                currentBowlerName = null,
                recentBallsSummary = emptyList(),
                dismissedPlayerIds = emptyList(),
                secondInnings = active.secondInnings ?: InningsScorecard(
                    inningsNumber = 2,
                    battingTeamId = chasingTeam.id,
                    battingTeamName = chasingTeam.name,
                    bowlingTeamId = bowlingTeam.id,
                    bowlingTeamName = bowlingTeam.name
                )
            )
            val committed = matchRepository.commitLocalMatch(updated, allowProgressRollback = false)
            if (committed.isPublic && !committed.isOfflineScored) {
                scheduleCloudSync(committed.id)
            }
            Result.success(committed)
        }
    }

    suspend fun endInningsOrMatch(matchId: String): Result<Match> = withContext(Dispatchers.IO) {
        getMatchMutex(matchId).withLock {
            val active = loadAuthoritativeMatch(matchId) ?: return@withLock Result.failure(Exception("Match not found"))
            val updated = if (active.currentInningsNumber == 1) {
                val inn1 = active.firstInnings
                val target = (inn1?.totalRuns ?: 0) + 1
                active.copy(
                    currentInningsNumber = 2,
                    targetRuns = target,
                    status = MatchStatus.LIVE,
                    currentStrikerId = null,
                    currentStrikerName = null,
                    currentNonStrikerId = null,
                    currentNonStrikerName = null,
                    currentBowlerId = null,
                    currentBowlerName = null,
                    recentBallsSummary = emptyList(),
                    dismissedPlayerIds = emptyList()
                )
            } else {
                val firstRuns = active.firstInnings?.totalRuns ?: 0
                val secondRuns = active.secondInnings?.totalRuns ?: 0
                val firstTeam = if (active.battingFirstTeamId == active.teamA.id) active.teamA else active.teamB
                val chasingTeam = if (active.battingFirstTeamId == active.teamA.id) active.teamB else active.teamA
                val winnerId: String?
                val desc: String
                if (secondRuns > firstRuns) {
                    winnerId = chasingTeam.id
                    desc = "${chasingTeam.name} won by ${secondRuns - firstRuns} runs"
                } else if (firstRuns > secondRuns) {
                    winnerId = firstTeam.id
                    desc = "${firstTeam.name} won by ${firstRuns - secondRuns} runs"
                } else {
                    winnerId = null
                    desc = "Match Tied! ($firstRuns runs each)"
                }
                active.copy(
                    status = MatchStatus.COMPLETED,
                    winnerTeamId = winnerId,
                    resultDescription = desc
                )
            }
            val committed = matchRepository.commitLocalMatch(updated, allowProgressRollback = false)
            if (committed.isPublic && !committed.isOfflineScored) {
                scheduleCloudSync(committed.id)
            }
            Result.success(committed)
        }
    }

    private fun recalculateScorecard(
        inningsNumber: Int,
        match: Match,
        ballEvents: List<BallEvent>
    ): InningsScorecard {
        val battingTeam = if (inningsNumber == 1) {
            if (match.battingFirstTeamId == match.teamA.id) match.teamA else match.teamB
        } else {
            if (match.battingFirstTeamId == match.teamA.id) match.teamB else match.teamA
        }

        val bowlingTeam = if (battingTeam.id == match.teamA.id) match.teamB else match.teamA

        var totalRuns = 0
        var totalWickets = 0
        var legalBalls = 0
        var wides = 0
        var noBalls = 0
        var byes = 0
        var legByes = 0
        var penalties = 0

        val batterMap = mutableMapOf<String, BatterInningsScore>()
        val bowlerMap = mutableMapOf<String, BowlerInningsScore>()
        val fallOfWickets = mutableListOf<FallOfWicket>()

        for (ball in ballEvents) {
            val runsBat = ball.runsOffBat
            val extraRuns = ball.extraRuns
            val isBallRetiredHurt = ball.dismissalType == DismissalType.RETIRED_HURT
            val isBallRetiredOut = ball.dismissalType == DismissalType.RETIRED_OUT
            val isBallRetiredNoCount = isBallRetiredHurt || isBallRetiredOut

            totalRuns += (runsBat + extraRuns)
            val isCountedLegalDelivery = ball.isLegalDelivery && !isBallRetiredNoCount
            if (isCountedLegalDelivery) {
                legalBalls++
            }

            when (ball.extraType) {
                ExtraType.WIDE -> wides += extraRuns
                ExtraType.NO_BALL -> noBalls += extraRuns
                ExtraType.BYE -> byes += extraRuns
                ExtraType.LEG_BYE -> legByes += extraRuns
                ExtraType.PENALTY -> penalties += extraRuns
                ExtraType.NONE -> {}
            }

            // Update Batter
            if (ball.strikerId.isNotEmpty()) {
                val current = batterMap[ball.strikerId] ?: BatterInningsScore(
                    playerId = ball.strikerId,
                    playerName = ball.strikerName
                )
                val facedBall = (ball.isLegalDelivery || ball.extraType == ExtraType.NO_BALL) && !isBallRetiredNoCount
                val isFour = runsBat == 4
                val isSix = runsBat == 6

                val resumedFromHurt = current.dismissalText.startsWith("retired hurt") && !isBallRetiredHurt
                batterMap[ball.strikerId] = current.copy(
                    runs = current.runs + runsBat,
                    balls = current.balls + (if (facedBall) 1 else 0),
                    fours = current.fours + (if (isFour) 1 else 0),
                    sixes = current.sixes + (if (isSix) 1 else 0),
                    isOut = if (resumedFromHurt) false else current.isOut,
                    dismissalText = if (resumedFromHurt) "not out" else current.dismissalText
                )
            }
            if (ball.nonStrikerId.isNotEmpty()) {
                val nonStrikerCurr = batterMap[ball.nonStrikerId]
                if (nonStrikerCurr != null && nonStrikerCurr.dismissalText.startsWith("retired hurt") && !isBallRetiredHurt) {
                    batterMap[ball.nonStrikerId] = nonStrikerCurr.copy(isOut = false, dismissalText = "not out")
                }
            }

            // Update Bowler
            if (ball.bowlerId.isNotEmpty()) {
                val current = bowlerMap[ball.bowlerId] ?: BowlerInningsScore(
                    playerId = ball.bowlerId,
                    playerName = ball.bowlerName
                )
                // Bowler concedes runs off bat + wide + no ball (byes & leg-byes are not conceded by bowler)
                val bowlerRunsConceded = runsBat + (if (ball.extraType == ExtraType.WIDE || ball.extraType == ExtraType.NO_BALL) extraRuns else 0)
                val isBowlerWicket = ball.isWicket && (ball.dismissalType?.isBowlerWicket == true)

                bowlerMap[ball.bowlerId] = current.copy(
                    legalBalls = current.legalBalls + (if (isCountedLegalDelivery) 1 else 0),
                    runsConceded = current.runsConceded + bowlerRunsConceded,
                    wickets = current.wickets + (if (isBowlerWicket) 1 else 0),
                    wides = current.wides + (if (ball.extraType == ExtraType.WIDE) extraRuns else 0),
                    noBalls = current.noBalls + (if (ball.extraType == ExtraType.NO_BALL) extraRuns else 0)
                )
            }

            // Dismissal
            if (ball.isWicket && !isBallRetiredHurt) {
                totalWickets++
                val dismissedId = ball.dismissedPlayerId ?: ball.strikerId
                val dismissedName = ball.dismissedPlayerName ?: ball.strikerName
                val bTag = if (ball.bowlerName.isNotBlank()) " b ${ball.bowlerName}" else ""
                val dismissalDesc = when (ball.dismissalType) {
                    DismissalType.BOWLED -> "b ${ball.bowlerName}"
                    DismissalType.CAUGHT -> "c ${ball.assistingPlayerName ?: "fielder"}$bTag"
                    DismissalType.CAUGHT_BEHIND -> "c †${ball.assistingPlayerName ?: "keeper"}$bTag"
                    DismissalType.LBW -> "lbw$bTag"
                    DismissalType.STUMPED -> "st †${ball.assistingPlayerName ?: "keeper"}$bTag"
                    DismissalType.RUN_OUT -> "run out (${ball.assistingPlayerName ?: "direct"})"
                    DismissalType.MANKAD -> "run out non-striker$bTag"
                    DismissalType.HIT_WICKET -> "hit wicket$bTag"
                    DismissalType.TIMED_OUT -> "timed out$bTag"
                    DismissalType.HANDLED_BALL -> "handled the ball$bTag"
                    DismissalType.HIT_BALL_TWICE -> "hit the ball twice$bTag"
                    DismissalType.OBSTRUCTING_FIELD -> "obstructing the field$bTag"
                    DismissalType.RETIRED_OUT -> "retired out$bTag"
                    else -> "${ball.dismissalType?.displayName ?: "out"}$bTag"
                }

                val currBatter = batterMap[dismissedId] ?: BatterInningsScore(playerId = dismissedId, playerName = dismissedName)
                batterMap[dismissedId] = currBatter.copy(
                    isOut = true,
                    dismissalText = dismissalDesc,
                    bowlerName = if (ball.dismissalType == DismissalType.RUN_OUT) "" else ball.bowlerName,
                    fielderName = ball.assistingPlayerName ?: ""
                )

                fallOfWickets.add(
                    FallOfWicket(
                        wicketNumber = totalWickets,
                        teamScore = totalRuns,
                        legalBalls = legalBalls,
                        batterName = dismissedName
                    )
                )
            } else if (isBallRetiredHurt) {
                // Retired Hurt: Batter is NOT out and no team wicket or legal ball charged
                val dismissedId = ball.dismissedPlayerId ?: ball.strikerId
                val dismissedName = ball.dismissedPlayerName ?: ball.strikerName
                val currBatter = batterMap[dismissedId] ?: BatterInningsScore(playerId = dismissedId, playerName = dismissedName)
                batterMap[dismissedId] = currBatter.copy(
                    isOut = false,
                    dismissalText = "retired hurt (not out)"
                )
            }
        }

        // 1. Calculate Batting Order and Roles
        val battingOrderMap = mutableMapOf<String, Int>()
        var nextBattingPos = 1
        for (ball in ballEvents) {
            if (ball.strikerId.isNotEmpty() && !battingOrderMap.containsKey(ball.strikerId)) {
                battingOrderMap[ball.strikerId] = nextBattingPos++
            }
            if (ball.nonStrikerId.isNotEmpty() && !battingOrderMap.containsKey(ball.nonStrikerId)) {
                battingOrderMap[ball.nonStrikerId] = nextBattingPos++
            }
        }

        val battingSquadPlayers = if (inningsNumber == 1) {
            if (match.battingFirstTeamId == match.teamA.id) match.playingXI_A else match.playingXI_B
        } else {
            if (match.battingFirstTeamId == match.teamA.id) match.playingXI_B else match.playingXI_A
        }

        val finalBatters = batterMap.values.map { b ->
            val pSnapshot = battingSquadPlayers.find { it.id == b.playerId }
            b.copy(
                battingPosition = battingOrderMap[b.playerId] ?: nextBattingPos++,
                isCaptain = pSnapshot?.isCaptain == true || b.playerId == match.team1CaptainId || b.playerId == match.team2CaptainId,
                isWicketkeeper = pSnapshot?.isWicketkeeper == true
            )
        }.sortedBy { it.battingPosition }

        // 2. Calculate Maidens, Wicket Maidens, and Dot Balls (MCC Law 21/22/Scorebook Analysis)
        val bowlerMaidens = mutableMapOf<String, Int>()
        val bowlerWicketMaidens = mutableMapOf<String, Int>()
        val bowlerDotBalls = mutableMapOf<String, Int>()

        var curOverBowlerId = ""
        var curOverLegalBalls = 0
        var curOverBowlerRuns = 0
        var curOverBowlerWickets = 0

        for (ball in ballEvents) {
            val runsChargedToBowler = ball.runsOffBat + (if (ball.extraType == ExtraType.WIDE || ball.extraType == ExtraType.NO_BALL) ball.extraRuns else 0)
            val isBowlerWkt = ball.isWicket && (ball.dismissalType?.isBowlerWicket == true)
            val isCountedLegal = ball.isLegalDelivery &&
                ball.dismissalType != DismissalType.RETIRED_HURT &&
                ball.dismissalType != DismissalType.RETIRED_OUT

            if (isCountedLegal && ball.runsOffBat == 0 && ball.extraRuns == 0 && ball.bowlerId.isNotEmpty()) {
                bowlerDotBalls[ball.bowlerId] = (bowlerDotBalls[ball.bowlerId] ?: 0) + 1
            }

            if (curOverBowlerId.isEmpty()) {
                curOverBowlerId = ball.bowlerId
            }

            if (curOverBowlerId != ball.bowlerId && curOverLegalBalls > 0) {
                if (curOverLegalBalls >= 6) {
                    if (curOverBowlerRuns == 0) {
                        bowlerMaidens[curOverBowlerId] = (bowlerMaidens[curOverBowlerId] ?: 0) + 1
                        if (curOverBowlerWickets > 0) {
                            bowlerWicketMaidens[curOverBowlerId] = (bowlerWicketMaidens[curOverBowlerId] ?: 0) + 1
                        }
                    }
                }
                curOverBowlerId = ball.bowlerId
                curOverLegalBalls = 0
                curOverBowlerRuns = 0
                curOverBowlerWickets = 0
            }

            curOverBowlerRuns += runsChargedToBowler
            if (isBowlerWkt) curOverBowlerWickets++
            if (isCountedLegal) {
                curOverLegalBalls++
                if (curOverLegalBalls == 6) {
                    if (curOverBowlerRuns == 0) {
                        bowlerMaidens[curOverBowlerId] = (bowlerMaidens[curOverBowlerId] ?: 0) + 1
                        if (curOverBowlerWickets > 0) {
                            bowlerWicketMaidens[curOverBowlerId] = (bowlerWicketMaidens[curOverBowlerId] ?: 0) + 1
                        }
                    }
                    curOverLegalBalls = 0
                    curOverBowlerRuns = 0
                    curOverBowlerWickets = 0
                    curOverBowlerId = ""
                }
            }
        }

        val finalBowlers = bowlerMap.values.map { b ->
            b.copy(
                maidens = bowlerMaidens[b.playerId] ?: 0,
                wicketMaidens = bowlerWicketMaidens[b.playerId] ?: 0,
                dotBalls = bowlerDotBalls[b.playerId] ?: 0
            )
        }

        return InningsScorecard(
            inningsNumber = inningsNumber,
            battingTeamId = battingTeam.id,
            battingTeamName = battingTeam.name,
            bowlingTeamId = bowlingTeam.id,
            bowlingTeamName = bowlingTeam.name,
            totalRuns = totalRuns,
            wickets = totalWickets,
            legalBalls = legalBalls,
            wides = wides,
            noBalls = noBalls,
            byes = byes,
            legByes = legByes,
            penalties = penalties,
            batters = finalBatters,
            bowlers = finalBowlers,
            fallOfWickets = fallOfWickets,
            isDeclaredOrAllOut = totalWickets >= match.getMaxWicketsForInnings(inningsNumber)
        )
    }

    // Commentary generation lives in com.example.util.CommentaryHelper for single-source consistency.

    suspend fun manualCompleteOver(match: Match, reason: String, scorerId: String): Result<Match> = withContext(Dispatchers.IO) {
        getMatchMutex(match.id).withLock {
            val active = loadAuthoritativeMatch(match.id, match) ?: match
            val isOfflineMatch = active.isOfflineScored || !active.isPublic || active.id.startsWith("OFFLINE_") || active.id == "OFFLINE"
            if (!isOfflineMatch && scorerId.isNotBlank() && !active.canUserScore(scorerId)) {
                return@withLock Result.failure(SecurityException("Unauthorized: Only Head Scorer or Sub Scorers can complete over."))
            }
            val correction = CorrectionRecord(
                id = "over_override_${System.currentTimeMillis()}",
                matchId = active.id,
                ballId = "",
                userId = scorerId,
                userName = "Scorer ($scorerId)",
                timestamp = System.currentTimeMillis(),
                reason = "Manual Over Completion: $reason",
                originalDescription = "Over ended by umpire confirmation",
                updatedDescription = "Over ended manually. Strike rotated."
            )

            // Swap batsmen at end of over
            val updated = active.copy(
                currentStrikerId = active.currentNonStrikerId,
                currentStrikerName = active.currentNonStrikerName,
                currentNonStrikerId = active.currentStrikerId,
                currentNonStrikerName = active.currentStrikerName,
                currentBowlerId = null,
                currentBowlerName = null,
                lastSyncTimestamp = System.currentTimeMillis()
            )

            val committed = matchRepository.commitLocalMatch(updated, allowProgressRollback = false)

            val fs = firestore
            if (committed.isPublic && !committed.isOfflineScored && fs != null) {
                scheduleCloudSync(committed.id)
                scope.launch {
                    try {
                        fs.collection("matches").document(committed.id)
                            .collection("corrections").document(correction.id).set(correction).await()
                    } catch (_: Exception) {}
                }
            }

            Result.success(committed)
        }
    }
}
