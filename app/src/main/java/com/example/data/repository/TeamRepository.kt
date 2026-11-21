package com.example.data.repository

import com.example.data.local.dao.TeamDao
import com.example.data.local.entity.TeamEntity
import com.example.data.model.Team
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

class TeamRepository(
    private val teamDao: TeamDao,
    private val firestore: FirebaseFirestore? = try { FirebaseFirestore.getInstance() } catch (_: Exception) { null },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, t ->
        android.util.Log.e("TeamRepository", "Handled background error", t)
    }),
    private val enableCloudSync: Boolean = firestore != null
) {
    private val hotTeamsCache = ConcurrentHashMap<String, Pair<String, Team>>()
    private var ownerSyncListener: ListenerRegistration? = null
    private var memberSyncListener: ListenerRegistration? = null
    @Volatile
    private var currentSyncedUserId: String? = null

    private fun parseTeamCached(entity: TeamEntity): Team? {
        val cached = hotTeamsCache[entity.id]
        if (cached != null && cached.first == entity.jsonPayload) {
            return cached.second
        }
        val parsed = JsonHelper.fromJson<Team>(entity.jsonPayload) ?: return null
        hotTeamsCache[entity.id] = entity.jsonPayload to parsed
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

    private val sharedAllTeamsFlow: Flow<List<Team>> = teamDao.getAllTeamsFlow()
        .map { entities -> entities.mapNotNull { parseTeamCached(it) } }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L, replayExpirationMillis = 0L), replay = 1)

    init {
        if (enableCloudSync && firestore != null) {
            val initialUid = try { FirebaseAuth.getInstance().currentUser?.uid } catch (_: Exception) { null }
            if (!initialUid.isNullOrBlank()) {
                syncTeamsForUser(initialUid)
            }
        }
    }

    fun syncTeamsForUser(userId: String) {
        val fs = firestore ?: return
        if (!enableCloudSync) return
        val cleanUid = userId.trim()
        if (!isCloudBackedUid(cleanUid)) {
            stopSync()
            return
        }
        if (currentSyncedUserId == cleanUid && ownerSyncListener != null) return
        stopSync()
        currentSyncedUserId = cleanUid

        ownerSyncListener = fs.collection("teams")
            .whereEqualTo("ownerId", cleanUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                applyCloudChanges(snapshot.documentChanges, allowLocalDelete = true, expectedUid = cleanUid)
            }

        memberSyncListener = fs.collection("teams")
            .whereArrayContains("playerIds", cleanUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                applyCloudChanges(snapshot.documentChanges, allowLocalDelete = false, expectedUid = cleanUid)
            }

        scope.launch {
            syncPendingToCloud(cleanUid)
        }
    }

    private fun applyCloudChanges(
        changes: List<DocumentChange>,
        allowLocalDelete: Boolean,
        expectedUid: String
    ) {
        if (changes.isEmpty()) return
        scope.launch {
            try {
                if (currentSyncedUserId != expectedUid) return@launch
                val existingById = teamDao.getAllTeamsList().associateBy { it.id }
                val toUpsert = mutableListOf<TeamEntity>()
                for (change in changes) {
                    val doc = change.document
                    if (change.type == DocumentChange.Type.REMOVED) {
                        if (allowLocalDelete) {
                            hotTeamsCache.remove(doc.id)
                            teamDao.deleteTeamById(doc.id)
                        }
                        continue
                    }
                    val team = doc.toObject(Team::class.java)?.let {
                        if (it.id.isBlank()) it.copy(id = doc.id) else it
                    } ?: continue
                    val newJson = JsonHelper.toJson(team)
                    val existing = existingById[team.id]
                    if (existing != null && existing.jsonPayload == newJson) continue
                    hotTeamsCache[team.id] = newJson to team
                    toUpsert.add(
                        TeamEntity(
                            id = team.id,
                            name = team.name,
                            shortName = team.shortName,
                            isArchived = team.isArchived,
                            jsonPayload = newJson,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
                if (toUpsert.isNotEmpty() && currentSyncedUserId == expectedUid) {
                    teamDao.insertTeams(toUpsert)
                }
            } catch (e: Exception) {
                android.util.Log.w("TeamRepository", "Delta sync error: ${e.message}")
            }
        }
    }

    fun stopSync() {
        ownerSyncListener?.remove()
        ownerSyncListener = null
        memberSyncListener?.remove()
        memberSyncListener = null
        currentSyncedUserId = null
    }

    suspend fun syncPendingToCloud(userId: String): Int = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext 0
        if (!isCloudBackedUid(userId)) return@withContext 0
        var synced = 0
        try {
            val localTeams = teamDao.getAllTeamsList().mapNotNull { parseTeamCached(it) }
                .filter { it.ownerId == userId }
            for (team in localTeams) {
                try {
                    fs.collection("teams").document(team.id).set(team).await()
                    synced++
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        synced
    }

    fun getAllTeamsFlow(): Flow<List<Team>> = sharedAllTeamsFlow

    fun getMyTeamsFlow(userId: String): Flow<List<Team>> {
        val cleanUid = userId.trim()
        return getAllTeamsFlow().map { teams ->
            when {
                cleanUid.isBlank() -> teams.filter { it.ownerId.startsWith("guest") || it.ownerId.isEmpty() }
                cleanUid.startsWith("guest") -> teams.filter { it.ownerId == cleanUid || it.ownerId.startsWith("guest") || it.playerIds.contains(cleanUid) }
                else -> teams.filter { it.ownerId == cleanUid || it.playerIds.contains(cleanUid) }
            }
        }
            .flowOn(Dispatchers.Default)
            .distinctUntilChanged()
    }

    suspend fun getTeamById(id: String): Team? = withContext(Dispatchers.IO) {
        val cached = teamDao.getTeamById(id)
        if (cached != null) {
            val team = parseTeamCached(cached)
            if (team != null) return@withContext team
        }
        val fs = firestore ?: return@withContext null
        return@withContext try {
            val doc = fs.collection("teams").document(id).get().await()
            doc.toObject(Team::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveTeam(team: Team): Result<Unit> = withContext(Dispatchers.IO) {
        val activeAuthUid = try { FirebaseAuth.getInstance().currentUser?.uid } catch (_: Exception) { null }
        val resolvedOwnerId = when {
            team.ownerId.isNotBlank() -> team.ownerId
            !activeAuthUid.isNullOrBlank() -> activeAuthUid
            else -> ""
        }
        val baseTeam = if (resolvedOwnerId != team.ownerId) team.copy(ownerId = resolvedOwnerId) else team
        val teamToSave = if (baseTeam.id.isEmpty()) {
            val generatedId = firestore?.collection("teams")?.document()?.id ?: java.util.UUID.randomUUID().toString()
            baseTeam.copy(id = generatedId)
        } else {
            baseTeam
        }

        // Save locally first for instant offline readiness
        val serialized = JsonHelper.toJson(teamToSave)
        hotTeamsCache[teamToSave.id] = serialized to teamToSave
        val entity = TeamEntity(
            id = teamToSave.id,
            name = teamToSave.name,
            shortName = teamToSave.shortName,
            isArchived = teamToSave.isArchived,
            jsonPayload = serialized,
            updatedAt = System.currentTimeMillis()
        )
        teamDao.insertTeam(entity)

        // Non-blocking background cloud sync so UI is instant even on poor connectivity
        val fs = firestore
        if (fs != null) {
            scope.launch {
                try {
                    fs.collection("teams").document(teamToSave.id).set(teamToSave).await()
                } catch (_: Exception) {
                }
            }
        }
        Result.success(Unit)
    }

    suspend fun joinTeamAsPlayer(teamId: String, playerId: String): Result<Team> = withContext(Dispatchers.IO) {
        val existing = getTeamById(teamId) ?: return@withContext Result.failure(IllegalArgumentException("দল পাওয়া যায়নি"))
        val updatedIds = (existing.playerIds + playerId).distinct()
        val updated = existing.copy(playerIds = updatedIds)
        teamDao.insertTeam(
            TeamEntity(
                id = updated.id,
                name = updated.name,
                shortName = updated.shortName,
                isArchived = updated.isArchived,
                jsonPayload = JsonHelper.toJson(updated),
                updatedAt = System.currentTimeMillis()
            )
        )
        val fs = firestore
        if (fs != null) {
            scope.launch {
                try {
                    fs.collection("teams").document(teamId)
                        .update("playerIds", com.google.firebase.firestore.FieldValue.arrayUnion(playerId))
                        .await()
                } catch (_: Exception) {
                    try {
                        fs.collection("teams").document(teamId).set(updated).await()
                    } catch (_: Exception) {}
                }
            }
        }
        Result.success(updated)
    }

    suspend fun leaveTeamAsPlayer(teamId: String, playerId: String): Result<Team> = withContext(Dispatchers.IO) {
        val existing = getTeamById(teamId) ?: return@withContext Result.failure(IllegalArgumentException("দল পাওয়া যায়নি"))
        val updatedIds = existing.playerIds.filter { it != playerId }
        val updated = existing.copy(playerIds = updatedIds)
        teamDao.insertTeam(
            TeamEntity(
                id = updated.id,
                name = updated.name,
                shortName = updated.shortName,
                isArchived = updated.isArchived,
                jsonPayload = JsonHelper.toJson(updated),
                updatedAt = System.currentTimeMillis()
            )
        )
        val fs = firestore
        if (fs != null) {
            scope.launch {
                try {
                    fs.collection("teams").document(teamId)
                        .update("playerIds", com.google.firebase.firestore.FieldValue.arrayRemove(playerId))
                        .await()
                } catch (_: Exception) {
                    try {
                        fs.collection("teams").document(teamId).set(updated).await()
                    } catch (_: Exception) {}
                }
            }
        }
        Result.success(updated)
    }

    suspend fun setTeamArchived(teamId: String, isArchived: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val existing = getTeamById(teamId)
            if (existing != null) {
                val updated = existing.copy(isArchived = isArchived)
                teamDao.insertTeam(
                    TeamEntity(
                        id = updated.id,
                        name = updated.name,
                        shortName = updated.shortName,
                        isArchived = updated.isArchived,
                        jsonPayload = JsonHelper.toJson(updated),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            firestore?.collection("teams")?.document(teamId)?.update("isArchived", isArchived)?.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteTeam(teamId: String): Result<Unit> = withContext(Dispatchers.IO) {
        hotTeamsCache.remove(teamId)
        teamDao.deleteTeamById(teamId)
        val fs = firestore
        if (fs != null) {
            scope.launch {
                try {
                    fs.collection("teams").document(teamId).delete().await()
                } catch (_: Exception) {
                }
            }
        }
        Result.success(Unit)
    }

    suspend fun migrateOwnerUid(oldUid: String, newUid: String): Int = withContext(Dispatchers.IO) {
        if (oldUid.isBlank() || newUid.isBlank() || oldUid == newUid) return@withContext 0
        val cached = teamDao.getAllTeamsList()
        var migratedCount = 0
        for (entity in cached) {
            val team = parseTeamCached(entity) ?: continue
            val ownedByOld = team.ownerId == oldUid
            val hasOldMember = team.playerIds.contains(oldUid)
            if (!ownedByOld && !hasOldMember) continue
            val updated = team.copy(
                ownerId = if (ownedByOld) newUid else team.ownerId,
                playerIds = team.playerIds.map { if (it == oldUid) newUid else it }.distinct()
            )
            val serialized = JsonHelper.toJson(updated)
            hotTeamsCache[updated.id] = serialized to updated
            teamDao.insertTeam(
                TeamEntity(
                    id = updated.id,
                    name = updated.name,
                    shortName = updated.shortName,
                    isArchived = updated.isArchived,
                    jsonPayload = serialized,
                    updatedAt = System.currentTimeMillis()
                )
            )
            val fs = firestore
            if (fs != null) {
                scope.launch {
                    try {
                        fs.collection("teams").document(updated.id).set(updated).await()
                    } catch (_: Exception) {}
                }
            }
            migratedCount++
        }
        migratedCount
    }

    suspend fun clearAllTeams() {
        hotTeamsCache.clear()
        teamDao.deleteAllTeams()
    }
}
