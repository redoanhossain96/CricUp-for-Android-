package com.example.data.repository

import android.content.Context
import com.example.data.model.BallEvent
import com.example.data.model.CrowdVoteStats
import com.example.data.model.Match
import com.example.data.model.MatchPrediction
import com.example.data.model.PredictionCategory
import com.example.data.model.PredictionStatus
import com.example.data.model.PredictionWinCelebration
import com.example.util.JsonHelper
import com.example.util.PredictionEvaluator
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.squareup.moshi.Types
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

class PredictionRepository(
    private val context: Context? = null,
    private val firestore: FirebaseFirestore? = try { FirebaseFirestore.getInstance() } catch (_: Exception) { null },
    private val authRepository: AuthRepository,
    private val enableCloudSync: Boolean = firestore != null
) {
    companion object {
        private const val PREFS_NAME = "cricup_predictions_v1"
        private const val KEY_MATCH_PREFIX = "preds_match_"
        private const val KEY_AWARDED_IDS = "awarded_prediction_ids"
    }

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, t ->
        android.util.Log.w("PredictionRepository", "Cloud sync skipped: ${t.message}")
    })

    private val listAdapter by lazy {
        val type = Types.newParameterizedType(List::class.java, MatchPrediction::class.java)
        JsonHelper.moshi.adapter<List<MatchPrediction>>(type)
    }

    private val matchFlows = ConcurrentHashMap<String, MutableStateFlow<List<MatchPrediction>>>()
    private val matchListeners = ConcurrentHashMap<String, ListenerRegistration>()
    private val evalMutexes = ConcurrentHashMap<String, Mutex>()

    private fun prefs() = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun evalMutexFor(matchId: String): Mutex =
        evalMutexes.computeIfAbsent(matchId) { Mutex() }

    private fun readLocalPredictions(matchId: String): List<MatchPrediction> {
        val raw = prefs()?.getString("$KEY_MATCH_PREFIX$matchId", null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return try {
            listAdapter.fromJson(raw).orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeLocalPredictions(matchId: String, predictions: List<MatchPrediction>) {
        val json = try {
            listAdapter.toJson(predictions.sortedByDescending { it.createdAt }.take(150))
        } catch (_: Exception) {
            ""
        }
        if (json.isNotBlank()) {
            prefs()?.edit()?.putString("$KEY_MATCH_PREFIX$matchId", json)?.apply()
        }
    }

    private fun readAwardedPredictionIds(): MutableSet<String> {
        return prefs()?.getStringSet(KEY_AWARDED_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
    }

    private fun markPredictionIdsAwarded(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val existing = readAwardedPredictionIds()
        existing.addAll(ids)
        prefs()?.edit()?.putStringSet(KEY_AWARDED_IDS, existing.take(1000).toSet())?.apply()
    }

    fun observeMatchPredictions(matchId: String): StateFlow<List<MatchPrediction>> {
        val flow = matchFlows.computeIfAbsent(matchId) {
            MutableStateFlow(readLocalPredictions(matchId))
        }
        if (enableCloudSync && firestore != null && !matchListeners.containsKey(matchId)) {
            startMatchPredictionsListener(matchId)
        }
        return flow.asStateFlow()
    }

    private fun startMatchPredictionsListener(matchId: String) {
        val fs = firestore ?: return
        if (matchId.isBlank()) return
        matchListeners[matchId]?.remove()
        val reg = fs.collection("matches")
            .document(matchId)
            .collection("predictions")
            .limit(300)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val cloudList = snapshot.documents.mapNotNull { doc ->
                    try {
                        doc.toObject(MatchPrediction::class.java)?.let { p ->
                            if (p.id.isBlank()) p.copy(id = doc.id) else p
                        }
                    } catch (_: Exception) {
                        null
                    }
                }
                repoScope.launch {
                    evalMutexFor(matchId).withLock {
                        val localList = matchFlows[matchId]?.value ?: readLocalPredictions(matchId)
                        val merged = mergePredictions(localList, cloudList)
                        matchFlows.computeIfAbsent(matchId) { MutableStateFlow(merged) }.value = merged
                        writeLocalPredictions(matchId, merged)
                    }
                }
            }
        matchListeners[matchId] = reg
    }

    fun stopObservingMatch(matchId: String) {
        matchListeners.remove(matchId)?.remove()
    }

    private fun mergePredictions(
        local: List<MatchPrediction>,
        cloud: List<MatchPrediction>
    ): List<MatchPrediction> {
        val map = LinkedHashMap<String, MatchPrediction>()
        cloud.forEach { if (it.id.isNotBlank()) map[it.id] = it }
        local.forEach { loc ->
            if (loc.id.isBlank()) return@forEach
            val existing = map[loc.id]
            if (existing == null) {
                map[loc.id] = loc
            } else {
                // Keep whichever has settled status or newer resolution/timestamp
                val locSettled = loc.resolvedStatus() != PredictionStatus.PENDING
                val cloudSettled = existing.resolvedStatus() != PredictionStatus.PENDING
                val chosen = when {
                    locSettled && !cloudSettled -> loc
                    !locSettled && cloudSettled -> existing
                    (loc.resolvedAt ?: loc.createdAt) >= (existing.resolvedAt ?: existing.createdAt) -> loc
                    else -> existing
                }
                map[loc.id] = chosen
            }
        }
        return map.values.sortedByDescending { it.createdAt }
    }

    suspend fun submitPrediction(
        match: Match,
        userId: String,
        userName: String,
        category: PredictionCategory,
        targetKey: String,
        targetLabel: String,
        selectedOption: String,
        selectedLabel: String,
        rewardPoints: Int,
        targetInnings: Int,
        baselineBallCount: Int,
        targetOverNumber: Int = 0,
        selectedPercentTeamA: Int = 50
    ): Result<MatchPrediction> {
        if (userId.isBlank()) {
            return Result.failure(IllegalStateException("প্রেডিকশন করতে লগইন প্রয়োজন"))
        }
        if (PredictionEvaluator.isScorerOrSubScorer(match, userId)) {
            return Result.failure(IllegalStateException("Head Scorer বা Sub-Scorer এই ম্যাচের প্রেডিকশনে অংশ নিতে পারবেন না"))
        }
        if (!PredictionEvaluator.isMatchAcceptingPredictions(match)) {
            return Result.failure(IllegalStateException("ম্যাচ শেষ হয়ে যাওয়ায় নতুন প্রেডিকশন নেওয়া যাচ্ছে না"))
        }

        val docId = "${userId}_${category.name}_${targetKey}"
        val prediction = MatchPrediction(
            id = docId,
            matchId = match.id,
            userId = userId,
            userName = userName.ifBlank { "Spectator" },
            category = category.name,
            targetKey = targetKey,
            targetLabel = targetLabel,
            selectedOption = selectedOption,
            selectedLabel = selectedLabel,
            selectedPercentTeamA = selectedPercentTeamA.coerceIn(0, 100),
            rewardPoints = rewardPoints.coerceAtLeast(10),
            targetInnings = targetInnings,
            baselineBallCount = baselineBallCount,
            targetOverNumber = targetOverNumber,
            status = PredictionStatus.PENDING.name,
            createdAt = System.currentTimeMillis()
        )

        return try {
            evalMutexFor(match.id).withLock {
                val flow = matchFlows.computeIfAbsent(match.id) {
                    MutableStateFlow(readLocalPredictions(match.id))
                }
                val current = flow.value.toMutableList()
                val existingIdx = current.indexOfFirst { it.id == docId }
                if (existingIdx >= 0) {
                    if (current[existingIdx].resolvedStatus() != PredictionStatus.PENDING) {
                        return@withLock Result.failure(IllegalStateException("এই ধাপের প্রেডিকশন ইতোমধ্যে সেটেল হয়ে গেছে"))
                    }
                    // Allow updating vote for WINNING_TEAM / WINNING_PERCENT while still pending
                    current[existingIdx] = prediction
                } else {
                    current.add(0, prediction)
                }
                flow.value = current
                writeLocalPredictions(match.id, current)

                val fs = firestore
                if (fs != null && enableCloudSync && !match.isOfflineScored) {
                    try {
                        fs.collection("matches")
                            .document(match.id)
                            .collection("predictions")
                            .document(docId)
                            .set(prediction, SetOptions.merge())
                            .await()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.w("PredictionRepository", "Cloud write skipped: ${e.message}")
                    }
                }
                Result.success(prediction)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Evaluates all pending predictions for [userId] on [match] using latest ball events,
     * awards won points once to [AuthRepository], persists updates, and returns celebration events.
     */
    suspend fun evaluateAndSettleForUser(
        match: Match,
        userId: String,
        ballEvents1: List<BallEvent>,
        ballEvents2: List<BallEvent>
    ): List<PredictionWinCelebration> {
        if (userId.isBlank() || PredictionEvaluator.isScorerOrSubScorer(match, userId)) {
            return emptyList()
        }

        return evalMutexFor(match.id).withLock {
            val flow = matchFlows.computeIfAbsent(match.id) {
                MutableStateFlow(readLocalPredictions(match.id))
            }
            val allPreds = flow.value
            val userPreds = allPreds.filter { it.userId == userId }
            if (userPreds.none { it.resolvedStatus() == PredictionStatus.PENDING }) {
                return@withLock emptyList()
            }

            val result = PredictionEvaluator.evaluatePredictions(
                match = match,
                ballEvents1 = ballEvents1,
                ballEvents2 = ballEvents2,
                predictions = userPreds
            )

            val awardedIds = readAwardedPredictionIds()
            var pointsToCredit = 0
            var winsToCredit = 0
            var lossesToCredit = 0
            val newlyAwardedIds = mutableListOf<String>()
            val syncedMap = LinkedHashMap<String, MatchPrediction>()

            result.updatedPredictions.forEach { pred ->
                val wasPendingBefore = userPreds.firstOrNull { it.id == pred.id }
                    ?.resolvedStatus() == PredictionStatus.PENDING
                val statusNow = pred.resolvedStatus()

                if (wasPendingBefore && statusNow != PredictionStatus.PENDING) {
                    if (statusNow == PredictionStatus.WON && pred.id !in awardedIds && !pred.pointsSyncedToProfile) {
                        pointsToCredit += pred.awardedPoints
                        winsToCredit++
                        newlyAwardedIds.add(pred.id)
                        syncedMap[pred.id] = pred.copy(pointsSyncedToProfile = true)
                    } else {
                        if (statusNow == PredictionStatus.LOST) {
                            lossesToCredit++
                        }
                        syncedMap[pred.id] = pred
                    }
                } else {
                    syncedMap[pred.id] = pred
                }
            }

            if (winsToCredit > 0 || lossesToCredit > 0 || pointsToCredit > 0) {
                authRepository.recordPredictionOutcome(
                    winsAdded = winsToCredit,
                    lossesAdded = lossesToCredit,
                    pointsEarned = pointsToCredit
                )
                markPredictionIdsAwarded(newlyAwardedIds)
            }

            val updatedAll = allPreds.map { existing ->
                syncedMap[existing.id] ?: existing
            }
            flow.value = updatedAll
            writeLocalPredictions(match.id, updatedAll)

            // Sync newly settled predictions back to Firestore
            val fs = firestore
            if (fs != null && enableCloudSync && !match.isOfflineScored) {
                val changedSettled = syncedMap.values.filter { updated ->
                    val old = userPreds.firstOrNull { it.id == updated.id }
                    old != null && old.status != updated.status
                }
                changedSettled.forEach { settled ->
                    try {
                        fs.collection("matches")
                            .document(match.id)
                            .collection("predictions")
                            .document(settled.id)
                            .set(settled, SetOptions.merge())
                            .await()
                    } catch (_: Exception) {
                        // Safe offline fallback; already stored locally
                    }
                }
            }

            result.newlyWonCelebrations.filter { cel ->
                cel.predictionId in newlyAwardedIds
            }
        }
    }

    fun computeCrowdStats(match: Match, predictions: List<MatchPrediction>): CrowdVoteStats =
        PredictionEvaluator.computeCrowdVoteStats(match, predictions)
}
