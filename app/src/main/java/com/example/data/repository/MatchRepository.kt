package com.example.data.repository

import com.example.data.local.dao.BallEventDao
import com.example.data.local.dao.MatchDao
import com.example.data.local.entity.MatchEntity
import com.example.data.model.Match
import com.example.data.model.MatchStatus
import com.example.data.model.SubScorerInfo
import com.example.data.model.User
import com.example.util.JsonHelper
import com.example.util.MatchCodeGenerator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

sealed class MatchSearchResult {
    data class Success(val match: Match) : MatchSearchResult()
    object NotFound : MatchSearchResult()
    data class PrivateMatch(val message: String = "এই ম্যাচটি প্রাইভেট (Private Match - শুধুমাত্র ম্যাচ নির্মাতা দেখতে পারবেন)") : MatchSearchResult()
    data class PermissionDenied(val message: String = "অনুমতি নেই (Firestore rules ঠিক করুন)") : MatchSearchResult()
    data class NetworkError(val message: String = "ইন্টারনেট সংযোগ চেক করুন") : MatchSearchResult()
    data class Error(val message: String) : MatchSearchResult()
}

class MatchRepository(
    private val matchDao: MatchDao,
    private val ballEventDao: BallEventDao? = null,
    private val firestore: FirebaseFirestore? = FirebaseFirestore.getInstance(),
    private val enableCloudListeners: Boolean = firestore != null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, t ->
        android.util.Log.e("MatchRepository", "Handled background error", t)
    })
) {
    private data class CachedMatchEntry(
        val rawJson: String,
        val match: Match
    )

    private val activeMatchListeners = ConcurrentHashMap<String, ListenerRegistration>()
    private val matchWriteMutexes = ConcurrentHashMap<String, Mutex>()
    private val cloudPushMutexes = ConcurrentHashMap<String, Mutex>()
    private val hotMatchesCache = ConcurrentHashMap<String, CachedMatchEntry>()
    private val dirtyCloudMatchIds = ConcurrentHashMap.newKeySet<String>()
    private var publicMatchesListener: ListenerRegistration? = null
    private var userMatchesListener: ListenerRegistration? = null
    private var userMatchesLegacyListener: ListenerRegistration? = null

    private fun getWriteMutex(matchId: String): Mutex =
        matchWriteMutexes.computeIfAbsent(matchId) { Mutex() }

    private fun getCloudPushMutex(matchId: String): Mutex =
        cloudPushMutexes.computeIfAbsent(matchId) { Mutex() }

    companion object {
        // Strictly bounded well below both Firestore's 1 MiB document cap and Android SQLite's 2 MiB CursorWindow cap
        internal const val MAX_SAFE_STORAGE_JSON_CHARS = 700_000
        private const val INLINE_PHOTO_SOFT_LIMIT = 520_000
        private const val LOGO_FALLBACK_LIMIT = 840_000

        private fun isInlinePhotoBlob(url: String?): Boolean {
            if (url.isNullOrBlank()) return false
            val trimmed = url.trim()
            return trimmed.startsWith("data:image", ignoreCase = true) ||
                (trimmed.length > 200 && !trimmed.startsWith("http", ignoreCase = true) && !trimmed.startsWith("/"))
        }

        /**
         * Ensures any Match serialized for Room SQLite or Cloud Firestore stays safely compact
         * so it can NEVER trigger SQLiteBlobTooBigException (>2 MB CursorWindow) or Firestore 1 MiB rejection.
         * Deduplicates fullSquad vs playingXI photos first; strips inline Base64 player blobs next
         * (screens automatically resolve player photos from PlayerRepository by ID/name).
         */
        internal fun compactMatchForStorage(
            match: Match,
            maxChars: Int = MAX_SAFE_STORAGE_JSON_CHARS
        ): Pair<Match, String> {
            // 1. Always avoid duplicating identical player Base64 photos in BOTH fullSquad and playingXI
            val xiIdsA = match.playingXI_A.mapTo(HashSet()) { it.id }
            val xiIdsB = match.playingXI_B.mapTo(HashSet()) { it.id }
            var safeMatch = match.copy(
                fullSquad_A = match.fullSquad_A.map { p ->
                    if (p.id in xiIdsA && isInlinePhotoBlob(p.photoUrl)) p.copy(photoUrl = null) else p
                },
                fullSquad_B = match.fullSquad_B.map { p ->
                    if (p.id in xiIdsB && isInlinePhotoBlob(p.photoUrl)) p.copy(photoUrl = null) else p
                }
            )
            var serialized = JsonHelper.toJson(safeMatch)

            // 2. If still large, strip remaining inline Base64 photos from fullSquad benches
            if (serialized.length > INLINE_PHOTO_SOFT_LIMIT) {
                safeMatch = safeMatch.copy(
                    fullSquad_A = safeMatch.fullSquad_A.map { p ->
                        if (isInlinePhotoBlob(p.photoUrl)) p.copy(photoUrl = null) else p
                    },
                    fullSquad_B = safeMatch.fullSquad_B.map { p ->
                        if (isInlinePhotoBlob(p.photoUrl)) p.copy(photoUrl = null) else p
                    }
                )
                serialized = JsonHelper.toJson(safeMatch)
            }

            // 3. If still above maxChars, strip inline Base64 photos from playingXI snapshots
            //    (LiveScoringScreen & MatchDetailScreen seamlessly look up player photos from PlayerRepository)
            if (serialized.length > maxChars) {
                safeMatch = safeMatch.copy(
                    playingXI_A = safeMatch.playingXI_A.map { p ->
                        if (isInlinePhotoBlob(p.photoUrl)) p.copy(photoUrl = null) else p
                    },
                    playingXI_B = safeMatch.playingXI_B.map { p ->
                        if (isInlinePhotoBlob(p.photoUrl)) p.copy(photoUrl = null) else p
                    }
                )
                serialized = JsonHelper.toJson(safeMatch)
            }

            // 4. Last-resort safety floor if team logos alone exceed safe storage limits
            if (serialized.length > LOGO_FALLBACK_LIMIT) {
                safeMatch = safeMatch.copy(
                    teamA = if (isInlinePhotoBlob(safeMatch.teamA.logoUrl) && (safeMatch.teamA.logoUrl?.length ?: 0) > 220_000) {
                        safeMatch.teamA.copy(logoUrl = null)
                    } else safeMatch.teamA,
                    teamB = if (isInlinePhotoBlob(safeMatch.teamB.logoUrl) && (safeMatch.teamB.logoUrl?.length ?: 0) > 220_000) {
                        safeMatch.teamB.copy(logoUrl = null)
                    } else safeMatch.teamB
                )
                serialized = JsonHelper.toJson(safeMatch)
            }

            return safeMatch to serialized
        }
    }

    internal fun parseMatchCached(entity: MatchEntity): Match? {
        val cached = hotMatchesCache[entity.id]
        if (cached != null && cached.rawJson == entity.jsonPayload) {
            return cached.match
        }
        val parsed = JsonHelper.fromJson<Match>(entity.jsonPayload) ?: return null
        hotMatchesCache[entity.id] = CachedMatchEntry(entity.jsonPayload, parsed)
        return parsed
    }

    fun markMatchNeedsCloudSync(matchId: String) {
        if (firestore != null && matchId.isNotBlank() && !matchId.startsWith("OFFLINE_") && matchId != "OFFLINE") {
            dirtyCloudMatchIds.add(matchId)
        }
    }

    fun clearMatchCloudSyncDirty(matchId: String) {
        dirtyCloudMatchIds.remove(matchId)
    }

    init {
        if (enableCloudListeners && firestore != null) {
            startPublicMatchesSync()
            // Listen to auth changes so when user logs in or switches, we sync their private & owned matches
            try {
                FirebaseAuth.getInstance().addAuthStateListener { auth ->
                    val user = auth.currentUser
                    if (user != null && user.uid.isNotBlank()) {
                        syncUserMatches(user.uid)
                    } else {
                        stopUserMatchesSync()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("MatchRepository", "Auth listener init skipped: ${e.message}")
            }
        }
    }

    /**
     * Filtered snapshot listener for all public matches (satisfies Firestore Security Rules read constraint)
     */
    fun startPublicMatchesSync() {
        val fs = firestore ?: return
        publicMatchesListener?.remove()
        publicMatchesListener = fs.collection("matches")
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("MatchRepository", "public matches sync listener error: ${error.code} - ${error.message}", error)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                val changedDocs = snapshot.documentChanges
                    .filter { it.type != DocumentChange.Type.REMOVED }
                    .map { it.document }
                if (changedDocs.isNotEmpty()) {
                    saveSnapshotToRoom(changedDocs)
                }
            }
        // Also push any local matches that were not yet uploaded to Firestore
        syncPendingLocalMatchesToCloud()
    }

    /**
     * Snapshot listeners for user-owned matches (both public and private)
     */
    fun syncUserMatches(userId: String) {
        val fs = firestore ?: return
        if (userId.isBlank()) return
        userMatchesListener?.remove()
        userMatchesLegacyListener?.remove()

        userMatchesListener = fs.collection("matches")
            .whereEqualTo("ownerUid", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("MatchRepository", "userMatchesListener error: ${error.code} - ${error.message}", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val changedDocs = snapshot.documentChanges
                        .filter { it.type != DocumentChange.Type.REMOVED }
                        .map { it.document }
                    if (changedDocs.isNotEmpty()) {
                        saveSnapshotToRoom(changedDocs)
                    }
                }
            }

        userMatchesLegacyListener = fs.collection("matches")
            .whereEqualTo("ownerId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("MatchRepository", "userMatchesLegacyListener error: ${error.code} - ${error.message}", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val changedDocs = snapshot.documentChanges
                        .filter { it.type != DocumentChange.Type.REMOVED }
                        .map { it.document }
                    if (changedDocs.isNotEmpty()) {
                        saveSnapshotToRoom(changedDocs)
                    }
                }
            }
    }

    fun stopUserMatchesSync() {
        userMatchesListener?.remove()
        userMatchesListener = null
        userMatchesLegacyListener?.remove()
        userMatchesLegacyListener = null
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toMatchObject(): Match? {
        val matchJson = getString("matchJson")
        if (!matchJson.isNullOrBlank()) {
            val parsed = JsonHelper.fromJson<Match>(matchJson)
            if (parsed != null) return parsed
        }
        try {
            val obj = toObject(Match::class.java)
            if (obj != null) return obj
        } catch (e: Exception) {
            android.util.Log.w("MatchRepo", "toObject(Match) failed: ${e.message}")
        }
        val rawData = data ?: return null
        return try {
            val fixedData = rawData.toMutableMap()
            if (fixedData.containsKey("public") && !fixedData.containsKey("isPublic")) {
                fixedData["isPublic"] = fixedData["public"]
            }
            if (fixedData.containsKey("offlineScored") && !fixedData.containsKey("isOfflineScored")) {
                fixedData["isOfflineScored"] = fixedData["offlineScored"]
            }
            val mapType = com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
            val adapter = JsonHelper.moshi.adapter<Map<String, Any?>>(mapType)
            val jsonString = adapter.toJson(fixedData)
            JsonHelper.fromJson<Match>(jsonString)
        } catch (e: Exception) {
            android.util.Log.e("MatchRepo", "Fallback JsonHelper failed: ${e.message}")
            null
        }
    }

    /**
     * Guarded Room upsert for cloud snapshots:
     * NEVER overwrites a local match if:
     * 1) Local match has pending/deleted unsynced ball events,
     * 2) Local match is offline-scored,
     * 3) Local match has strictly higher monotonic scoring progress, or
     * 4) Progress is tied and local copy is at least as recent (or payload is identical).
     */
    suspend fun upsertCloudSnapshotIfNewer(incomingMatch: Match): Boolean = withContext(Dispatchers.IO) {
        getWriteMutex(incomingMatch.id).withLock {
            val pendingLocalEvents = ballEventDao?.hasPendingSyncEvents(incomingMatch.id) ?: 0
            if (pendingLocalEvents > 0) {
                android.util.Log.d("MatchRepository", "Skipped cloud overwrite for ${incomingMatch.id}: $pendingLocalEvents pending local balls")
                return@withLock false
            }

            val existingEntity = matchDao.getMatchById(incomingMatch.id)
            val existingLocal = existingEntity?.let { parseMatchCached(it) }
            if (existingLocal != null) {
                if (existingLocal.isOfflineScored || existingLocal.id.startsWith("OFFLINE_")) {
                    return@withLock false
                }
                val cmp = existingLocal.compareScoringProgressTo(incomingMatch)
                if (cmp > 0) {
                    android.util.Log.w(
                        "MatchRepository",
                        "Blocked stale cloud snapshot for ${incomingMatch.id}: local progress ahead of cloud"
                    )
                    return@withLock false
                }
                if (cmp == 0 && dirtyCloudMatchIds.contains(incomingMatch.id)) {
                    // Local device has un-uploaded metadata/status changes at same progress
                    return@withLock false
                }
                if (cmp == 0 && existingLocal.lastSyncTimestamp >= incomingMatch.lastSyncTimestamp) {
                    return@withLock false
                }
            }

            val (compactIncoming, newJson) = compactMatchForStorage(incomingMatch)
            if (newJson.isBlank()) return@withLock false
            if (existingEntity != null && existingEntity.jsonPayload == newJson) {
                return@withLock false
            }

            hotMatchesCache[compactIncoming.id] = CachedMatchEntry(newJson, compactIncoming)
            matchDao.insertMatch(
                MatchEntity(
                    id = compactIncoming.id,
                    title = compactIncoming.title,
                    status = compactIncoming.status.name,
                    isOfflineScored = compactIncoming.isOfflineScored,
                    jsonPayload = newJson,
                    updatedAt = maxOf(compactIncoming.lastSyncTimestamp, System.currentTimeMillis())
                )
            )
            true
        }
    }

    private fun saveSnapshotToRoom(documents: List<com.google.firebase.firestore.DocumentSnapshot>) {
        scope.launch {
            for (doc in documents) {
                val rawMatch = doc.toMatchObject() ?: continue
                val docId = doc.id
                val codeVal = when {
                    rawMatch.code.isNotBlank() -> rawMatch.code
                    rawMatch.matchCode.isNotBlank() -> rawMatch.matchCode
                    else -> docId
                }
                val normalizedCode = MatchCodeGenerator.normalizeCode(codeVal)

                val match = rawMatch.copy(
                    id = if (rawMatch.id.isNotBlank()) rawMatch.id else docId,
                    matchCode = normalizedCode,
                    code = normalizedCode,
                    ownerUid = if (rawMatch.ownerUid.isNotBlank()) rawMatch.ownerUid else rawMatch.ownerId,
                    ownerId = if (rawMatch.ownerId.isNotBlank()) rawMatch.ownerId else rawMatch.ownerUid,
                    isPublic = rawMatch.isPublic
                )
                upsertCloudSnapshotIfNewer(match)
            }
        }
    }

    /**
     * Attaches a real-time Cloud Firestore snapshot listener to a specific match document.
     * Ensures viewers on separate devices receive every score update automatically in real-time,
     * while protecting the scorer from stale/cached snapshot rollbacks.
     */
    fun syncMatchDocumentFromCloud(matchId: String) {
        val fs = firestore ?: return
        if (!enableCloudListeners) return
        if (matchId.isBlank() || matchId.startsWith("OFFLINE_") || matchId == "OFFLINE") return
        if (activeMatchListeners.containsKey(matchId)) return
        val registration = fs.collection("matches").document(matchId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("MatchRepository", "syncMatchDocumentFromCloud error for $matchId: ${error.code}", error)
                    return@addSnapshotListener
                }
                if (snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val rawMatch = snapshot.toMatchObject() ?: return@addSnapshotListener
                val docId = snapshot.id
                val codeVal = when {
                    rawMatch.code.isNotBlank() -> rawMatch.code
                    rawMatch.matchCode.isNotBlank() -> rawMatch.matchCode
                    else -> docId
                }
                val finalMatch = rawMatch.copy(
                    id = if (rawMatch.id.isNotBlank()) rawMatch.id else docId,
                    matchCode = MatchCodeGenerator.normalizeCode(codeVal),
                    code = MatchCodeGenerator.normalizeCode(codeVal),
                    ownerUid = if (rawMatch.ownerUid.isNotBlank()) rawMatch.ownerUid else rawMatch.ownerId,
                    ownerId = if (rawMatch.ownerId.isNotBlank()) rawMatch.ownerId else rawMatch.ownerUid,
                    isPublic = rawMatch.isPublic
                )
                scope.launch {
                    upsertCloudSnapshotIfNewer(finalMatch)
                }
            }
        activeMatchListeners[matchId] = registration
    }

    fun unsubscribeMatchDocument(matchId: String) {
        activeMatchListeners.remove(matchId)?.remove()
    }

    private val sharedAllMatchesFlow: Flow<List<Match>> = matchDao.getAllMatchesFlow()
        .map { entities ->
            entities.mapNotNull { parseMatchCached(it) }
        }
        .catch { err ->
            android.util.Log.e("MatchRepository", "getAllMatchesFlow error: ${err.message}", err)
            if (err is android.database.sqlite.SQLiteBlobTooBigException) {
                runCatching { matchDao.deleteUnreadableOversizedMatches() }
            }
            emit(emptyList())
        }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .shareIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L, replayExpirationMillis = 0L),
            replay = 1
        )

    fun getAllMatchesFlow(): Flow<List<Match>> = sharedAllMatchesFlow

    fun getLiveMatchesFlow(): Flow<List<Match>> {
        return getAllMatchesFlow().map { list ->
            list.filter { it.status == MatchStatus.LIVE || it.status == MatchStatus.INNINGS_BREAK }
        }.distinctUntilChanged()
    }

    fun getUpcomingMatchesFlow(): Flow<List<Match>> {
        return getAllMatchesFlow().map { list ->
            list.filter { it.status == MatchStatus.UPCOMING }
        }.distinctUntilChanged()
    }

    fun getCompletedMatchesFlow(): Flow<List<Match>> {
        return getAllMatchesFlow().map { list ->
            list.filter { it.status == MatchStatus.COMPLETED }
        }.distinctUntilChanged()
    }

    fun getMyMatchesFlow(userId: String): Flow<List<Match>> {
        return getAllMatchesFlow().map { list ->
            list.filter { it.canUserScore(userId) }
        }
            .flowOn(Dispatchers.Default)
            .distinctUntilChanged()
    }

    fun getMatchFlowById(matchId: String): Flow<Match?> {
        // Automatically attach cloud document listener for live score updates
        syncMatchDocumentFromCloud(matchId)
        return matchDao.getMatchFlowById(matchId)
            .map { entity -> entity?.let { parseMatchCached(it) } ?: hotMatchesCache[matchId]?.match }
            .catch { err ->
                android.util.Log.e("MatchRepository", "getMatchFlowById($matchId) error: ${err.message}", err)
                emit(hotMatchesCache[matchId]?.match)
            }
            .flowOn(Dispatchers.Default)
            .distinctUntilChanged()
    }

    suspend fun getMatchById(matchId: String): Match? = withContext(Dispatchers.IO) {
        val cached = matchDao.getMatchById(matchId)
        if (cached != null) {
            val match = parseMatchCached(cached)
            if (match != null) return@withContext match
        }
        val fs = firestore ?: return@withContext null
        return@withContext try {
            val doc = fs.collection("matches").document(matchId).get().await()
            val match = doc.toMatchObject()
            if (match != null) {
                val finalMatch = match.copy(
                    ownerUid = if (match.ownerUid.isNotBlank()) match.ownerUid else match.ownerId,
                    ownerId = if (match.ownerId.isNotBlank()) match.ownerId else match.ownerUid,
                    isPublic = match.isPublic
                )
                upsertCloudSnapshotIfNewer(finalMatch)
                syncMatchDocumentFromCloud(finalMatch.id)
                finalMatch
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Retrieves a match by its unique Match Code (case-insensitive, whitespace-trimmed, normalized).
     * First checks direct Document ID in Cloud Firestore (/matches/{code}), then legacy queries, then local cache.
     */
    suspend fun getMatchByCode(code: String): Match? = withContext(Dispatchers.IO) {
        val cleanCode = MatchCodeGenerator.normalizeCode(code)
        if (cleanCode.isBlank()) return@withContext null

        val fs = firestore
        if (fs != null) {
            // 1. Direct Document ID lookup in Cloud Firestore: /matches/{cleanCode}
            try {
                val docSnapshot = fs.collection("matches").document(cleanCode).get().await()
                if (docSnapshot.exists()) {
                    val match = docSnapshot.toMatchObject()
                    if (match != null) {
                        val finalMatch = match.copy(
                            id = if (match.id.isNotBlank()) match.id else docSnapshot.id,
                            matchCode = cleanCode,
                            code = cleanCode,
                            ownerUid = if (match.ownerUid.isNotBlank()) match.ownerUid else match.ownerId,
                            ownerId = if (match.ownerId.isNotBlank()) match.ownerId else match.ownerUid,
                            isPublic = match.isPublic
                        )
                        upsertCloudSnapshotIfNewer(finalMatch)
                        syncMatchDocumentFromCloud(finalMatch.id)
                        return@withContext matchDao.getMatchById(finalMatch.id)?.let { JsonHelper.fromJson<Match>(it.jsonPayload) } ?: finalMatch
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MatchRepository", "Direct doc fetch failed for code $cleanCode", e)
            }

            // 2. Legacy fallback: query by "matchCode" field (without composite index constraint)
            try {
                val querySnapshot = fs.collection("matches")
                    .whereEqualTo("matchCode", cleanCode)
                    .limit(1)
                    .get()
                    .await()

                val doc = querySnapshot.documents.firstOrNull()
                if (doc != null) {
                    val match = doc.toMatchObject()
                    if (match != null) {
                        val finalMatch = match.copy(
                            id = if (match.id.isNotBlank()) match.id else doc.id,
                            matchCode = cleanCode,
                            code = cleanCode,
                            ownerUid = if (match.ownerUid.isNotBlank()) match.ownerUid else match.ownerId,
                            ownerId = if (match.ownerId.isNotBlank()) match.ownerId else match.ownerUid,
                            isPublic = match.isPublic
                        )
                        upsertCloudSnapshotIfNewer(finalMatch)
                        syncMatchDocumentFromCloud(finalMatch.id)
                        return@withContext matchDao.getMatchById(finalMatch.id)?.let { JsonHelper.fromJson<Match>(it.jsonPayload) } ?: finalMatch
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MatchRepository", "Query failed for matchCode $cleanCode", e)
            }

            // 3. Legacy fallback: query by "code" field
            try {
                val querySnapshot = fs.collection("matches")
                    .whereEqualTo("code", cleanCode)
                    .limit(1)
                    .get()
                    .await()

                val doc = querySnapshot.documents.firstOrNull()
                if (doc != null) {
                    val match = doc.toMatchObject()
                    if (match != null) {
                        val finalMatch = match.copy(
                            id = if (match.id.isNotBlank()) match.id else doc.id,
                            matchCode = cleanCode,
                            code = cleanCode,
                            ownerUid = if (match.ownerUid.isNotBlank()) match.ownerUid else match.ownerId,
                            ownerId = if (match.ownerId.isNotBlank()) match.ownerId else match.ownerUid,
                            isPublic = match.isPublic
                        )
                        upsertCloudSnapshotIfNewer(finalMatch)
                        syncMatchDocumentFromCloud(finalMatch.id)
                        return@withContext matchDao.getMatchById(finalMatch.id)?.let { JsonHelper.fromJson<Match>(it.jsonPayload) } ?: finalMatch
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MatchRepository", "Query failed for code $cleanCode", e)
            }
        }

        // 4. Fallback to local Room database cache
        val allEntities = matchDao.getAllMatches()
        for (entity in allEntities) {
            val m = parseMatchCached(entity)
            if (m != null && (
                MatchCodeGenerator.normalizeCode(m.matchCode) == cleanCode ||
                MatchCodeGenerator.normalizeCode(m.code) == cleanCode ||
                MatchCodeGenerator.normalizeCode(m.id) == cleanCode
            )) {
                return@withContext m
            }
        }

        return@withContext null
    }

    /**
     * Authoritative public search returning MatchSearchResult to strictly differentiate:
     * - Success with match found (via Document ID, legacy query, or cache)
     * - NotFound (search succeeded, but match does not exist)
     * - PrivateMatch (match exists but isPrivate and searcher is not creator)
     * - PermissionDenied (Firestore security rules reject read)
     * - NetworkError (offline, connection timeout)
     * - Error (general exception)
     */
    suspend fun searchMatchDetailed(code: String): MatchSearchResult = withContext(Dispatchers.IO) {
        val cleanCode = MatchCodeGenerator.normalizeCode(code)
        if (cleanCode.isBlank()) return@withContext MatchSearchResult.NotFound

        val currentUserId = try { FirebaseAuth.getInstance().currentUser?.uid ?: "" } catch (_: Exception) { "" }

        fun verifyMatchAccess(match: Match): MatchSearchResult {
            val isOwner = currentUserId.isNotBlank() && (match.ownerUid == currentUserId || match.ownerId == currentUserId)
            return if (!match.isPublic && !isOwner) {
                MatchSearchResult.PrivateMatch("এই ম্যাচটি প্রাইভেট (Private Match - শুধুমাত্র ম্যাচ নির্মাতা দেখতে পারবেন)")
            } else {
                MatchSearchResult.Success(match)
            }
        }

        // 1. First check local Room database cache
        val allEntities = matchDao.getAllMatches()
        val localMatch = allEntities.mapNotNull { parseMatchCached(it) }
            .firstOrNull {
                MatchCodeGenerator.normalizeCode(it.matchCode) == cleanCode ||
                MatchCodeGenerator.normalizeCode(it.code) == cleanCode ||
                MatchCodeGenerator.normalizeCode(it.id) == cleanCode
            }
        if (localMatch != null) {
            syncMatchDocumentFromCloud(localMatch.id)
            return@withContext verifyMatchAccess(localMatch)
        }

        val fs = firestore ?: return@withContext MatchSearchResult.NotFound

        // 2. Direct Document ID lookup: /matches/{cleanCode} (Fastest, index-free)
        try {
            val docSnapshot = fs.collection("matches").document(cleanCode).get().await()
            if (docSnapshot.exists()) {
                val match = docSnapshot.toMatchObject()
                if (match != null) {
                    val finalMatch = match.copy(
                        id = if (match.id.isNotBlank()) match.id else docSnapshot.id,
                        matchCode = cleanCode,
                        code = cleanCode,
                        ownerUid = if (match.ownerUid.isNotBlank()) match.ownerUid else match.ownerId,
                        ownerId = if (match.ownerId.isNotBlank()) match.ownerId else match.ownerUid,
                        isPublic = match.isPublic
                    )
                    upsertCloudSnapshotIfNewer(finalMatch)
                    syncMatchDocumentFromCloud(finalMatch.id)
                    return@withContext verifyMatchAccess(finalMatch)
                }
            }
        } catch (e: FirebaseFirestoreException) {
            android.util.Log.w("MatchSearch", "Direct doc fetch for $cleanCode returned ${e.code}")
        } catch (e: Exception) {
            android.util.Log.w("MatchSearch", "Direct doc fetch for $cleanCode failed: ${e.message}")
        }

        // 3. Fallback query by "matchCode" field (without composite index constraint)
        var queryPermissionDenied = false
        try {
            val querySnapshot = fs.collection("matches")
                .whereEqualTo("matchCode", cleanCode)
                .limit(1)
                .get()
                .await()

            val doc = querySnapshot.documents.firstOrNull()
            if (doc != null) {
                val match = doc.toMatchObject()
                if (match != null) {
                    val finalMatch = match.copy(
                        id = if (match.id.isNotBlank()) match.id else doc.id,
                        matchCode = cleanCode,
                        code = cleanCode,
                        ownerUid = if (match.ownerUid.isNotBlank()) match.ownerUid else match.ownerId,
                        ownerId = if (match.ownerId.isNotBlank()) match.ownerId else match.ownerUid,
                        isPublic = match.isPublic
                    )
                    upsertCloudSnapshotIfNewer(finalMatch)
                    syncMatchDocumentFromCloud(finalMatch.id)
                    return@withContext verifyMatchAccess(finalMatch)
                }
            }
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                queryPermissionDenied = true
            }
        } catch (ignored: Exception) {}

        // 4. Fallback query by "code" field
        try {
            val querySnapshotCode = fs.collection("matches")
                .whereEqualTo("code", cleanCode)
                .limit(1)
                .get()
                .await()

            val doc = querySnapshotCode.documents.firstOrNull()
            if (doc != null) {
                val match = doc.toMatchObject()
                if (match != null) {
                    val finalMatch = match.copy(
                        id = if (match.id.isNotBlank()) match.id else doc.id,
                        matchCode = cleanCode,
                        code = cleanCode,
                        ownerUid = if (match.ownerUid.isNotBlank()) match.ownerUid else match.ownerId,
                        ownerId = if (match.ownerId.isNotBlank()) match.ownerId else match.ownerUid,
                        isPublic = match.isPublic
                    )
                    upsertCloudSnapshotIfNewer(finalMatch)
                    syncMatchDocumentFromCloud(finalMatch.id)
                    return@withContext verifyMatchAccess(finalMatch)
                }
            }
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                queryPermissionDenied = true
            }
        } catch (ignored: Exception) {}

        // 5. If cleanCode != raw code, try raw trimmed code query
        val rawTrim = code.trim()
        if (rawTrim.isNotBlank() && rawTrim != cleanCode) {
            try {
                val rawSnapshot = fs.collection("matches")
                    .whereEqualTo("matchCode", rawTrim)
                    .limit(1)
                    .get()
                    .await()
                val doc = rawSnapshot.documents.firstOrNull()
                if (doc != null) {
                    val match = doc.toMatchObject()
                    if (match != null) {
                        val finalMatch = match.copy(
                            id = if (match.id.isNotBlank()) match.id else doc.id,
                            matchCode = cleanCode,
                            code = cleanCode,
                            ownerUid = if (match.ownerUid.isNotBlank()) match.ownerUid else match.ownerId,
                            ownerId = if (match.ownerId.isNotBlank()) match.ownerId else match.ownerUid,
                            isPublic = match.isPublic
                        )
                        upsertCloudSnapshotIfNewer(finalMatch)
                        syncMatchDocumentFromCloud(finalMatch.id)
                        return@withContext verifyMatchAccess(finalMatch)
                    }
                }
            } catch (e: FirebaseFirestoreException) {
                if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    queryPermissionDenied = true
                }
            } catch (ignored: Exception) {}
        }

        if (queryPermissionDenied) {
            return@withContext MatchSearchResult.PermissionDenied("অনুমতি নেই - Firebase Console-এ Firestore Rules আপডেট করুন")
        }

        return@withContext MatchSearchResult.NotFound
    }

    suspend fun searchMatchByCode(code: String): Result<Match?> {
        val result = searchMatchDetailed(code)
        return when (result) {
            is MatchSearchResult.Success -> Result.success(result.match)
            is MatchSearchResult.NotFound -> Result.success(null)
            is MatchSearchResult.PrivateMatch -> Result.failure(Exception(result.message))
            is MatchSearchResult.PermissionDenied -> Result.failure(Exception(result.message))
            is MatchSearchResult.NetworkError -> Result.failure(Exception(result.message))
            is MatchSearchResult.Error -> Result.failure(Exception(result.message))
        }
    }

    /**
     * Searches both local Room cache and Firestore matches by unique match code,
     * tournament name, team names, or match title.
     */
    suspend fun searchMatchesByQueryFromCloud(query: String): List<Match> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        val matched = LinkedHashMap<String, Match>()
        matchDao.getAllMatches()
            .mapNotNull { parseMatchCached(it) }
            .filter { com.example.util.MatchSearchHelper.matchesLiveSearchQuery(it, trimmed) }
            .forEach { matched[it.id] = it }

        val cleanCode = MatchCodeGenerator.normalizeCode(trimmed)
        if (cleanCode.length in 4..10) {
            val codeResult = searchMatchDetailed(cleanCode)
            if (codeResult is MatchSearchResult.Success) {
                matched[codeResult.match.id] = codeResult.match
            }
        }

        val fs = firestore
        if (fs != null) {
            val currentUid = try { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() } catch (_: Exception) { "" }
            val docs = try {
                val ordered = fs.collection("matches")
                    .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(200)
                    .get()
                    .await()
                if (ordered.isEmpty) {
                    fs.collection("matches").limit(200).get().await().documents
                } else {
                    ordered.documents
                }
            } catch (_: Exception) {
                try {
                    fs.collection("matches").limit(200).get().await().documents
                } catch (e: Exception) {
                    android.util.Log.w("MatchSearch", "Cloud query fallback failed: ${e.message}")
                    emptyList()
                }
            }
            for (doc in docs) {
                val m = doc.toMatchObject()?.let {
                    if (it.id.isBlank()) it.copy(id = doc.id) else it
                } ?: continue
                val accessible = m.isPublic || (currentUid.isNotBlank() && (
                    m.ownerUid == currentUid ||
                        m.ownerId == currentUid ||
                        m.canUserScore(currentUid)
                    ))
                if (accessible && com.example.util.MatchSearchHelper.matchesLiveSearchQuery(m, trimmed)) {
                    matched[m.id] = m
                    upsertCloudSnapshotIfNewer(m)
                }
            }
        }

        matched.values.sortedByDescending { it.createdAt }
    }

    fun Match.toFirestoreDocument(): Map<String, Any?> {
        val cleanCode = MatchCodeGenerator.normalizeCode(if (matchCode.isNotBlank()) matchCode else if (code.isNotBlank()) code else id)
        val map = mutableMapOf<String, Any?>()
        map["id"] = if (id.isNotBlank()) id else cleanCode
        map["matchCode"] = cleanCode
        map["code"] = cleanCode
        map["title"] = title
        map["category"] = category
        map["format"] = format
        map["totalOvers"] = totalOvers
        map["venue"] = venue
        map["matchDate"] = matchDate
        map["status"] = status.name
        map["ownerId"] = effectiveOwnerUid
        map["ownerUid"] = effectiveOwnerUid
        map["ownerName"] = ownerName
        map["isPublic"] = isPublic
        map["public"] = isPublic
        map["currentInningsNumber"] = currentInningsNumber
        map["updatedAt"] = System.currentTimeMillis()
        map["lastSyncTimestamp"] = System.currentTimeMillis()

        // Guard against Firestore's 1 MiB document size limit when multiple squad players carry high-res photos
        val (_, serialized) = compactMatchForStorage(this, MAX_SAFE_STORAGE_JSON_CHARS)
        map["matchJson"] = serialized
        return map
    }

    /**
     * Uploads ONLY matches modified locally on this device (tracked in dirtyCloudMatchIds)
     * whose ball events are already synced. NEVER blindly overwrites another user's cached match!
     */
    fun syncPendingLocalMatchesToCloud() {
        if (firestore == null) return
        scope.launch {
            syncPendingLocalMatchesToCloudSuspend()
        }
    }

    suspend fun syncPendingLocalMatchesToCloudSuspend(): Int = withContext(Dispatchers.IO) {
        if (firestore == null) return@withContext 0
        var syncedCount = 0
        try {
            val candidateIds = dirtyCloudMatchIds.toSet()
            for (matchId in candidateIds) {
                if (matchId.isBlank() || matchId.startsWith("OFFLINE_") || matchId == "OFFLINE") {
                    dirtyCloudMatchIds.remove(matchId)
                    continue
                }
                val entity = matchDao.getMatchById(matchId)
                val match = entity?.let { parseMatchCached(it) }
                if (match == null || match.isOfflineScored || !match.isPublic || match.matchCode == "OFFLINE") {
                    dirtyCloudMatchIds.remove(matchId)
                    continue
                }
                val pendingBalls = ballEventDao?.hasPendingSyncEvents(matchId) ?: 0
                if (pendingBalls == 0 && pushLatestMatchToCloud(matchId)) {
                    syncedCount++
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MatchRepository", "syncPendingLocalMatchesToCloud: ${e.message}")
        }
        syncedCount
    }

    /**
     * Creates a new match with guaranteed unique 6-character Match Code and permanent creator UID.
     * Document ID in Cloud Firestore is strictly set to the Match Code (matches/{code})
     * Writes to Room and immediately publishes to Cloud Firestore.
     */
    suspend fun createMatch(match: Match): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Fast-path for Solo / Offline Matches: 100% local in Room DB, zero network calls, zero latency
            if (match.isOfflineScored || !match.isPublic) {
                val offlineId = if (match.id.isNotBlank()) match.id else "OFFLINE_${java.util.UUID.randomUUID().toString().take(8).uppercase()}"
                val activeUserId = try { FirebaseAuth.getInstance().currentUser?.uid ?: "" } catch (_: Exception) { "" }
                val finalOwner = when {
                    match.ownerUid.isNotBlank() && match.ownerUid != "device_local" -> match.ownerUid
                    match.ownerId.isNotBlank() && match.ownerId != "device_local" -> match.ownerId
                    activeUserId.isNotBlank() -> activeUserId
                    else -> "device_local"
                }
                val finalOfflineMatch = match.copy(
                    id = offlineId,
                    matchCode = "OFFLINE",
                    code = "OFFLINE",
                    ownerId = finalOwner,
                    ownerUid = finalOwner,
                    isPublic = false,
                    isOfflineScored = true,
                    status = MatchStatus.LIVE,
                    createdAt = if (match.createdAt > 0) match.createdAt else System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    lastSyncTimestamp = System.currentTimeMillis()
                )
                val (compactOffline, offlineJson) = compactMatchForStorage(finalOfflineMatch)
                if (offlineJson.isBlank()) {
                    return@withContext Result.failure(IllegalStateException("ম্যাচ ডেটা সেভ করা যায়নি"))
                }
                hotMatchesCache[compactOffline.id] = CachedMatchEntry(offlineJson, compactOffline)
                getWriteMutex(compactOffline.id).withLock {
                    matchDao.insertMatch(
                        MatchEntity(
                            id = compactOffline.id,
                            title = compactOffline.title,
                            status = compactOffline.status.name,
                            isOfflineScored = true,
                            jsonPayload = offlineJson,
                            updatedAt = compactOffline.lastSyncTimestamp
                        )
                    )
                }
                return@withContext Result.success(compactOffline.id)
            }

            val fs = firestore
            // Lightweight ID-only lookup avoids loading multi-MB JSON rows into CursorWindow
            val existingCodes = runCatching {
                matchDao.getAllMatchIds().map { MatchCodeGenerator.normalizeCode(it) }.toSet()
            }.getOrDefault(emptySet())

            val cleanCode = MatchCodeGenerator.normalizeCode(
                when {
                    match.matchCode.isNotBlank() -> match.matchCode
                    match.code.isNotBlank() -> match.code
                    fs != null -> MatchCodeGenerator.generateUniqueMatchCode(fs, existingCodes)
                    else -> MatchCodeGenerator.generateCandidateCode()
                }
            ).ifBlank { MatchCodeGenerator.generateCandidateCode() }

            // Document ID IS THE MATCH CODE
            val matchId = cleanCode

            val activeUid = try { FirebaseAuth.getInstance().currentUser?.uid } catch (_: Exception) { null }
            val finalOwnerUid = when {
                match.ownerUid.isNotBlank() -> match.ownerUid
                match.ownerId.isNotBlank() -> match.ownerId
                !activeUid.isNullOrBlank() -> activeUid
                else -> "device_local"
            }

            val finalMatch = match.copy(
                id = matchId,
                matchCode = cleanCode,
                code = cleanCode,
                ownerId = finalOwnerUid,
                ownerUid = finalOwnerUid,
                authorizedScorers = (match.authorizedScorers + match.subScorerProfiles.map { it.uid })
                    .map { it.trim() }
                    .filter { it.isNotBlank() && it != finalOwnerUid }
                    .distinct(),
                subScorerProfiles = match.subScorerProfiles
                    .filter { it.uid.isNotBlank() && it.uid != finalOwnerUid }
                    .distinctBy { it.uid },
                isPublic = true,
                createdAt = if (match.createdAt > 0) match.createdAt else System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                lastSyncTimestamp = System.currentTimeMillis()
            )

            // 1. Compact & save to Room immediately so match start is instant and crash-free
            val (compactMatch, createdJson) = compactMatchForStorage(finalMatch)
            if (createdJson.isBlank()) {
                return@withContext Result.failure(IllegalStateException("ম্যাচ ডেটা সেভ করা যায়নি"))
            }
            hotMatchesCache[compactMatch.id] = CachedMatchEntry(createdJson, compactMatch)
            markMatchNeedsCloudSync(matchId)
            getWriteMutex(matchId).withLock {
                matchDao.insertMatch(
                    MatchEntity(
                        id = compactMatch.id,
                        title = compactMatch.title,
                        status = compactMatch.status.name,
                        isOfflineScored = false,
                        jsonPayload = createdJson,
                        updatedAt = compactMatch.lastSyncTimestamp
                    )
                )
            }

            // 2. Publish to Cloud Firestore asynchronously so slow/offline networks NEVER freeze match start
            if (fs != null) {
                scope.launch {
                    try {
                        val pushed = pushLatestMatchToCloud(matchId)
                        if (pushed) {
                            syncMatchDocumentFromCloud(matchId)
                        }
                    } catch (e: Exception) {
                        markMatchNeedsCloudSync(matchId)
                        android.util.Log.w("MatchRepository", "Deferred cloud publish for match $matchId: ${e.message}")
                    }
                }
            }

            Result.success(matchId)
        } catch (t: Throwable) {
            android.util.Log.e("MatchRepository", "createMatch failed: ${t.message}", t)
            Result.failure(t)
        }
    }

    suspend fun updateAuthorizedScorers(matchId: String, scorers: List<String>): Result<Unit> {
        val profiles = scorers.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .map { SubScorerInfo(uid = it, name = it) }
        return updateSubScorers(matchId, profiles)
    }

    suspend fun updateSubScorers(matchId: String, subScorers: List<SubScorerInfo>): Result<Unit> = withContext(Dispatchers.IO) {
        val existing = getMatchById(matchId)
            ?: return@withContext Result.failure(IllegalStateException("Match not found"))
        val ownerUid = existing.effectiveOwnerUid
        val cleanedProfiles = subScorers
            .map { it.copy(uid = it.uid.trim(), name = it.name.trim()) }
            .filter { it.uid.isNotBlank() && it.uid != ownerUid }
            .distinctBy { it.uid }
        val cleanedIds = cleanedProfiles.map { it.uid }

        val updated = existing.copy(
            authorizedScorers = cleanedIds,
            subScorerProfiles = cleanedProfiles
        )
        val committed = commitLocalMatch(updated, allowProgressRollback = false)
        if (!committed.isOfflineScored && committed.isPublic && firestore != null) {
            pushLatestMatchToCloud(committed.id)
        }
        Result.success(Unit)
    }

    /**
     * Atomically saves a locally computed Match state into Room inside the write mutex.
     * When [allowProgressRollback] is false (normal scoring), stale states with lower progress
     * than Room's current row are strictly rejected.
     */
    suspend fun commitLocalMatch(
        match: Match,
        allowProgressRollback: Boolean = false,
        allowOwnerTransfer: Boolean = false
    ): Match = withContext(Dispatchers.IO) {
        getWriteMutex(match.id).withLock {
            val existingEntity = matchDao.getMatchById(match.id)
            val existing = existingEntity?.let { parseMatchCached(it) }

            if (!allowProgressRollback && existing != null && existing.hasStrictlyHigherProgressThan(match)) {
                android.util.Log.w(
                    "MatchRepository",
                    "Prevented local rollback on match ${match.id}: keeping newer Room state"
                )
                return@withLock existing
            }

            val finalOwnerUid = when {
                allowOwnerTransfer && match.ownerUid.isNotBlank() -> match.ownerUid
                allowOwnerTransfer && match.ownerId.isNotBlank() -> match.ownerId
                existing != null && existing.ownerUid.isNotBlank() -> existing.ownerUid
                existing != null && existing.ownerId.isNotBlank() -> existing.ownerId
                match.ownerUid.isNotBlank() -> match.ownerUid
                match.ownerId.isNotBlank() -> match.ownerId
                else -> "device_local"
            }
            val finalMatchCode = when {
                existing != null && existing.matchCode.isNotBlank() -> MatchCodeGenerator.normalizeCode(existing.matchCode)
                match.matchCode.isNotBlank() -> MatchCodeGenerator.normalizeCode(match.matchCode)
                else -> ""
            }
            val now = maxOf(System.currentTimeMillis(), (existing?.lastSyncTimestamp ?: 0L) + 1L)
            val resolvedScorerProfiles = match.subScorerProfiles
                .filter { it.uid.isNotBlank() && it.uid != finalOwnerUid }
                .distinctBy { it.uid }
            val resolvedScorers = (match.authorizedScorers + resolvedScorerProfiles.map { it.uid })
                .map { it.trim() }
                .filter { it.isNotBlank() && it != finalOwnerUid }
                .distinct()
            val sanitized = match.copy(
                ownerId = finalOwnerUid,
                ownerUid = finalOwnerUid,
                matchCode = finalMatchCode.ifBlank { match.matchCode },
                code = finalMatchCode.ifBlank { match.code },
                authorizedScorers = resolvedScorers,
                subScorerProfiles = resolvedScorerProfiles,
                updatedAt = now,
                lastSyncTimestamp = now
            )
            val (compactSanitized, serializedJson) = compactMatchForStorage(sanitized)
            hotMatchesCache[compactSanitized.id] = CachedMatchEntry(serializedJson, compactSanitized)
            if (!compactSanitized.isOfflineScored && compactSanitized.isPublic) {
                markMatchNeedsCloudSync(compactSanitized.id)
            }
            matchDao.insertMatch(
                MatchEntity(
                    id = compactSanitized.id,
                    title = compactSanitized.title,
                    status = compactSanitized.status.name,
                    isOfflineScored = compactSanitized.isOfflineScored,
                    jsonPayload = serializedJson,
                    updatedAt = compactSanitized.lastSyncTimestamp
                )
            )
            compactSanitized
        }
    }

    /**
     * Serializes cloud upload per match and ALWAYS reads the latest authoritative Match from Room
     * at upload time, so out-of-order coroutines can NEVER push an older over/score to Firestore.
     */
    suspend fun pushLatestMatchToCloud(matchId: String): Boolean = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext false
        if (matchId.isBlank() || matchId.startsWith("OFFLINE_") || matchId == "OFFLINE") return@withContext false

        getCloudPushMutex(matchId).withLock {
            val entity = matchDao.getMatchById(matchId) ?: return@withLock false
            val latest = parseMatchCached(entity) ?: return@withLock false
            if (latest.isOfflineScored || !latest.isPublic || latest.matchCode == "OFFLINE") {
                dirtyCloudMatchIds.remove(matchId)
                return@withLock false
            }

            return@withLock try {
                val docData = latest.toFirestoreDocument()
                fs.collection("matches").document(latest.id).set(docData).await()
                val cleanCode = MatchCodeGenerator.normalizeCode(latest.matchCode)
                if (cleanCode.isNotBlank() && cleanCode != latest.id) {
                    fs.collection("matches").document(cleanCode).set(docData).await()
                }
                dirtyCloudMatchIds.remove(matchId)
                true
            } catch (e: Exception) {
                markMatchNeedsCloudSync(matchId)
                android.util.Log.w("MatchRepository", "pushLatestMatchToCloud failed for $matchId: ${e.message}")
                false
            }
        }
    }

    suspend fun updateMatch(
        match: Match,
        allowProgressRollback: Boolean = false
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val existing = getMatchById(match.id)
        val activeUid = try { FirebaseAuth.getInstance().currentUser?.uid } catch (_: Exception) { null }
        val finalOwnerUid = when {
            existing != null && existing.ownerUid.isNotBlank() -> existing.ownerUid
            existing != null && existing.ownerId.isNotBlank() -> existing.ownerId
            match.ownerUid.isNotBlank() -> match.ownerUid
            match.ownerId.isNotBlank() -> match.ownerId
            !activeUid.isNullOrBlank() -> activeUid
            else -> "device_local"
        }
        val finalMatchCode = when {
            existing != null && existing.matchCode.isNotBlank() -> MatchCodeGenerator.normalizeCode(existing.matchCode)
            match.matchCode.isNotBlank() -> MatchCodeGenerator.normalizeCode(match.matchCode)
            else -> MatchCodeGenerator.generateCandidateCode()
        }

        val resolvedScorerProfiles = match.subScorerProfiles
            .filter { it.uid.isNotBlank() && it.uid != finalOwnerUid }
            .distinctBy { it.uid }
        val resolvedScorers = (match.authorizedScorers + resolvedScorerProfiles.map { it.uid })
            .map { it.trim() }
            .filter { it.isNotBlank() && it != finalOwnerUid }
            .distinct()
        val candidate = match.copy(
            ownerId = finalOwnerUid,
            ownerUid = finalOwnerUid,
            matchCode = finalMatchCode,
            code = finalMatchCode,
            authorizedScorers = resolvedScorers,
            subScorerProfiles = resolvedScorerProfiles,
            isPublic = match.isPublic
        )

        val committed = commitLocalMatch(candidate, allowProgressRollback = allowProgressRollback)

        // If offline solo match, do not perform network writes to Firestore
        if (committed.isOfflineScored || !committed.isPublic || firestore == null) {
            return@withContext Result.success(Unit)
        }

        pushLatestMatchToCloud(committed.id)
        Result.success(Unit)
    }

    suspend fun deleteMatch(matchId: String): Result<Unit> = withContext(Dispatchers.IO) {
        getWriteMutex(matchId).withLock {
            hotMatchesCache.remove(matchId)
            dirtyCloudMatchIds.remove(matchId)
            matchDao.deleteMatchById(matchId)
        }
        val fs = firestore
        if (fs != null) {
            scope.launch {
                try {
                    fs.collection("matches").document(matchId).delete().await()
                } catch (_: Exception) {
                    // Offline fallback
                }
            }
        }
        Result.success(Unit)
    }

    suspend fun migrateOwnerUid(oldUid: String, newUid: String, newOwnerName: String = ""): Int = withContext(Dispatchers.IO) {
        if (oldUid.isBlank() || newUid.isBlank() || oldUid == newUid) return@withContext 0
        val cached = matchDao.getAllMatches()
        var updatedCount = 0
        for (entity in cached) {
            val match = JsonHelper.fromJson<Match>(entity.jsonPayload) ?: continue
            val isOwnedByOld = match.ownerUid == oldUid || match.ownerId == oldUid
            val isSubScorer = match.authorizedScorers.contains(oldUid) || match.subScorerProfiles.any { it.uid == oldUid }
            if (!isOwnedByOld && !isSubScorer) continue

            val nextOwner = if (isOwnedByOld) newUid else match.effectiveOwnerUid
            val nextOwnerName = if (isOwnedByOld && newOwnerName.isNotBlank() &&
                AuthRepository.isPlaceholderProfileName(match.ownerName)
            ) {
                newOwnerName
            } else {
                match.ownerName
            }
            val nextScorers = (match.authorizedScorers.map { if (it == oldUid) newUid else it } +
                if (isOwnedByOld) listOf(oldUid) else emptyList())
                .filter { it.isNotBlank() }
                .distinct()
            val nextProfiles = match.subScorerProfiles.map {
                if (it.uid == oldUid) it.copy(uid = newUid) else it
            }.distinctBy { it.uid }

            val migrated = match.copy(
                ownerId = nextOwner,
                ownerUid = nextOwner,
                ownerName = nextOwnerName,
                authorizedScorers = nextScorers,
                subScorerProfiles = nextProfiles
            )
            commitLocalMatch(migrated, allowProgressRollback = false, allowOwnerTransfer = true)
            if (migrated.isPublic && !migrated.isOfflineScored && firestore != null) {
                pushLatestMatchToCloud(migrated.id)
            }
            updatedCount++
        }
        updatedCount
    }

    suspend fun clearUserOwnedLocalMatches(userId: String): Int = withContext(Dispatchers.IO) {
        val cleanUid = userId.trim()
        val cached = matchDao.getAllMatches()
        var removedCount = 0
        for (entity in cached) {
            val match = parseMatchCached(entity) ?: continue
            val isOwned = (cleanUid.isNotBlank() && (match.ownerUid == cleanUid || match.ownerId == cleanUid)) ||
                match.isOfflineScored ||
                !match.isPublic
            if (!isOwned) continue
            getWriteMutex(match.id).withLock {
                hotMatchesCache.remove(match.id)
                dirtyCloudMatchIds.remove(match.id)
                unsubscribeMatchDocument(match.id)
                matchDao.deleteMatchById(match.id)
            }
            removedCount++
        }
        removedCount
    }
}
