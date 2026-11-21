package com.example.data.repository

import com.example.data.local.dao.TournamentDao
import com.example.data.local.entity.TournamentEntity
import com.example.data.model.Tournament
import com.example.util.JsonHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class TournamentRepository(
    private val tournamentDao: TournamentDao,
    private val firestore: FirebaseFirestore? = try { FirebaseFirestore.getInstance() } catch (_: Exception) { null },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, t ->
        android.util.Log.e("TournamentRepository", "Handled background error", t)
    }),
    private val enableCloudSync: Boolean = firestore != null
) {
    private val hotTournamentsCache = ConcurrentHashMap<String, Pair<String, Tournament>>()
    private var creatorSyncListener: ListenerRegistration? = null
    @Volatile
    private var currentSyncedUserId: String? = null

    private fun parseTournamentCached(entity: TournamentEntity): Tournament? {
        val cached = hotTournamentsCache[entity.id]
        if (cached != null && cached.first == entity.jsonPayload) {
            return cached.second
        }
        val parsed = JsonHelper.fromJson<Tournament>(entity.jsonPayload) ?: return null
        hotTournamentsCache[entity.id] = entity.jsonPayload to parsed
        return parsed
    }

    private fun isCloudBackedUid(userId: String): Boolean {
        val clean = userId.trim()
        return clean.isNotBlank() &&
            !clean.startsWith("dev_") &&
            !clean.startsWith("device_") &&
            !clean.startsWith("guest") &&
            clean != "device_local"
    }

    private val sharedAllTournamentsFlow: Flow<List<Tournament>> = tournamentDao.getAllTournamentsFlow()
        .map { entities -> entities.mapNotNull { parseTournamentCached(it) } }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L, replayExpirationMillis = 0L), replay = 1)

    init {
        if (enableCloudSync && firestore != null) {
            val initialUid = try { FirebaseAuth.getInstance().currentUser?.uid } catch (_: Exception) { null }
            if (!initialUid.isNullOrBlank()) {
                syncTournamentsForUser(initialUid)
            }
        }
    }

    fun syncTournamentsForUser(userId: String) {
        val fs = firestore ?: return
        if (!enableCloudSync) return
        val cleanUid = userId.trim()
        if (!isCloudBackedUid(cleanUid)) {
            stopSync()
            return
        }
        if (currentSyncedUserId == cleanUid && creatorSyncListener != null) return
        stopSync()
        currentSyncedUserId = cleanUid

        creatorSyncListener = fs.collection("tournaments")
            .whereEqualTo("creatorId", cleanUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val changes = snapshot.documentChanges
                if (changes.isEmpty()) return@addSnapshotListener
                scope.launch {
                    try {
                        if (currentSyncedUserId != cleanUid) return@launch
                        val existingById = tournamentDao.getAllTournamentsList().associateBy { it.id }
                        val toUpsert = mutableListOf<TournamentEntity>()
                        for (change in changes) {
                            val doc = change.document
                            if (change.type == DocumentChange.Type.REMOVED) {
                                hotTournamentsCache.remove(doc.id)
                                tournamentDao.deleteTournamentById(doc.id)
                                continue
                            }
                            val t = doc.toObject(Tournament::class.java)?.let {
                                if (it.id.isBlank()) it.copy(id = doc.id) else it
                            } ?: continue
                            val newJson = JsonHelper.toJson(t)
                            val existing = existingById[t.id]
                            if (existing != null && existing.jsonPayload == newJson) continue
                            hotTournamentsCache[t.id] = newJson to t
                            toUpsert.add(
                                TournamentEntity(
                                    id = t.id,
                                    name = t.name,
                                    jsonPayload = newJson,
                                    updatedAt = t.createdAt
                                )
                            )
                        }
                        if (toUpsert.isNotEmpty() && currentSyncedUserId == cleanUid) {
                            tournamentDao.insertTournaments(toUpsert)
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("TournamentRepository", "Delta sync error: ${e.message}")
                    }
                }
            }

        scope.launch {
            syncPendingToCloud(cleanUid)
        }
    }

    fun stopSync() {
        creatorSyncListener?.remove()
        creatorSyncListener = null
        currentSyncedUserId = null
    }

    suspend fun syncPendingToCloud(userId: String): Int = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext 0
        if (!isCloudBackedUid(userId)) return@withContext 0
        var synced = 0
        try {
            val localTournaments = tournamentDao.getAllTournamentsList().mapNotNull { parseTournamentCached(it) }
                .filter { it.creatorId == userId }
            for (t in localTournaments) {
                try {
                    fs.collection("tournaments").document(t.id).set(t).await()
                    synced++
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        synced
    }

    fun getAllTournamentsFlow(): Flow<List<Tournament>> = sharedAllTournamentsFlow

    fun getMyTournamentsFlow(userId: String): Flow<List<Tournament>> {
        val cleanUid = userId.trim()
        return getAllTournamentsFlow().map { tournaments ->
            when {
                cleanUid.isBlank() -> tournaments.filter { it.creatorId.startsWith("guest") || it.creatorId.isEmpty() }
                cleanUid.startsWith("guest") -> tournaments.filter { it.creatorId == cleanUid || it.creatorId.startsWith("guest") }
                else -> tournaments.filter { it.creatorId == cleanUid }
            }
        }
            .flowOn(Dispatchers.Default)
            .distinctUntilChanged()
    }

    suspend fun getTournamentById(id: String): Tournament? = withContext(Dispatchers.IO) {
        val cached = tournamentDao.getTournamentById(id)
        if (cached != null) {
            val t = parseTournamentCached(cached)
            if (t != null) return@withContext t
        }
        val fs = firestore ?: return@withContext null
        return@withContext try {
            val doc = fs.collection("tournaments").document(id).get().await()
            doc.toObject(Tournament::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveTournament(tournament: Tournament): Result<String> = withContext(Dispatchers.IO) {
        val fs = firestore
        val activeAuthUid = try { FirebaseAuth.getInstance().currentUser?.uid } catch (_: Exception) { null }
        val resolvedCreator = when {
            tournament.creatorId.isNotBlank() -> tournament.creatorId
            !activeAuthUid.isNullOrBlank() -> activeAuthUid
            else -> ""
        }
        val toSaveId = when {
            tournament.id.isNotEmpty() -> tournament.id
            fs != null -> fs.collection("tournaments").document().id
            else -> java.util.UUID.randomUUID().toString()
        }
        val finalTournament = tournament.copy(id = toSaveId, creatorId = resolvedCreator)
        val serialized = JsonHelper.toJson(finalTournament)
        hotTournamentsCache[finalTournament.id] = serialized to finalTournament

        tournamentDao.insertTournament(
            TournamentEntity(
                id = finalTournament.id,
                name = finalTournament.name,
                jsonPayload = serialized,
                updatedAt = System.currentTimeMillis()
            )
        )

        if (fs != null) {
            scope.launch {
                try {
                    fs.collection("tournaments").document(toSaveId).set(finalTournament).await()
                } catch (_: Exception) {
                }
            }
        }
        Result.success(toSaveId)
    }

    suspend fun migrateCreatorUid(oldUid: String, newUid: String): Int = withContext(Dispatchers.IO) {
        if (oldUid.isBlank() || newUid.isBlank() || oldUid == newUid) return@withContext 0
        val cached = tournamentDao.getAllTournamentsList()
        var migratedCount = 0
        val fs = firestore
        for (entity in cached) {
            val t = parseTournamentCached(entity) ?: continue
            if (t.creatorId != oldUid) continue
            val updated = t.copy(creatorId = newUid)
            val serialized = JsonHelper.toJson(updated)
            hotTournamentsCache[updated.id] = serialized to updated
            tournamentDao.insertTournament(
                TournamentEntity(
                    id = updated.id,
                    name = updated.name,
                    jsonPayload = serialized,
                    updatedAt = System.currentTimeMillis()
                )
            )
            if (fs != null) {
                scope.launch {
                    try {
                        fs.collection("tournaments").document(updated.id).set(updated).await()
                    } catch (_: Exception) {}
                }
            }
            migratedCount++
        }
        migratedCount
    }

    suspend fun deleteTournament(tournamentId: String): Result<Unit> = withContext(Dispatchers.IO) {
        hotTournamentsCache.remove(tournamentId)
        tournamentDao.deleteTournamentById(tournamentId)
        val fs = firestore
        if (fs != null) {
            scope.launch {
                try {
                    fs.collection("tournaments").document(tournamentId).delete().await()
                } catch (_: Exception) {
                }
            }
        }
        Result.success(Unit)
    }

    suspend fun clearAllTournaments() {
        hotTournamentsCache.clear()
        tournamentDao.deleteAllTournaments()
    }
}
