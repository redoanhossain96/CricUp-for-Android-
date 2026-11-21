package com.example.data.repository

import com.example.data.local.dao.PlayerDao
import com.example.data.local.entity.PlayerEntity
import com.example.data.model.Player
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

class PlayerRepository(
    private val playerDao: PlayerDao,
    private val firestore: FirebaseFirestore? = try { FirebaseFirestore.getInstance() } catch (_: Exception) { null },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, t ->
        android.util.Log.e("PlayerRepository", "Handled background error", t)
    }),
    private val enableCloudSync: Boolean = firestore != null
) {
    private val hotPlayersCache = ConcurrentHashMap<String, Pair<String, Player>>()
    private var creatorSyncListener: ListenerRegistration? = null
    private var selfDocListener: ListenerRegistration? = null
    @Volatile
    private var currentSyncedUserId: String? = null

    private fun parsePlayerCached(entity: PlayerEntity): Player? {
        val cached = hotPlayersCache[entity.id]
        if (cached != null && cached.first == entity.jsonPayload) {
            return cached.second
        }
        val parsed = JsonHelper.fromJson<Player>(entity.jsonPayload) ?: return null
        hotPlayersCache[entity.id] = entity.jsonPayload to parsed
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

    private val sharedAllPlayersFlow: Flow<List<Player>> = playerDao.getAllPlayersFlow()
        .map { entities ->
            entities.mapNotNull { parsePlayerCached(it) }
        }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L, replayExpirationMillis = 0L), replay = 1)

    init {
        if (enableCloudSync && firestore != null) {
            val initialUid = try { FirebaseAuth.getInstance().currentUser?.uid } catch (_: Exception) { null }
            if (!initialUid.isNullOrBlank()) {
                syncPlayersForUser(initialUid)
            }
        }
    }

    fun syncPlayersForUser(userId: String) {
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

        creatorSyncListener = fs.collection("players")
            .whereEqualTo("creatorId", cleanUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val changes = snapshot.documentChanges
                if (changes.isEmpty()) return@addSnapshotListener
                scope.launch {
                    try {
                        if (currentSyncedUserId != cleanUid) return@launch
                        val existingById = playerDao.getAllPlayers().associateBy { it.id }
                        val toUpsert = mutableListOf<PlayerEntity>()
                        for (change in changes) {
                            val doc = change.document
                            if (change.type == DocumentChange.Type.REMOVED) {
                                hotPlayersCache.remove(doc.id)
                                playerDao.deletePlayerById(doc.id)
                                continue
                            }
                            val player = doc.toObject(Player::class.java)?.let {
                                if (it.id.isBlank()) it.copy(id = doc.id) else it
                            } ?: continue
                            val newJson = JsonHelper.toJson(player)
                            val existing = existingById[player.id]
                            if (existing != null && existing.jsonPayload == newJson) continue
                            hotPlayersCache[player.id] = newJson to player
                            toUpsert.add(
                                PlayerEntity(
                                    id = player.id,
                                    fullName = player.fullName,
                                    primaryRole = player.primaryRole,
                                    isArchived = player.isArchived,
                                    jsonPayload = newJson,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        }
                        if (toUpsert.isNotEmpty() && currentSyncedUserId == cleanUid) {
                            playerDao.insertPlayers(toUpsert)
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("PlayerRepository", "Delta sync error: ${e.message}")
                    }
                }
            }

        selfDocListener = fs.collection("players").document(cleanUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val player = snapshot.toObject(Player::class.java)?.let {
                    if (it.id.isBlank()) it.copy(id = snapshot.id) else it
                } ?: return@addSnapshotListener
                scope.launch {
                    if (currentSyncedUserId != cleanUid) return@launch
                    val newJson = JsonHelper.toJson(player)
                    val existing = playerDao.getPlayerById(player.id)
                    if (existing != null && existing.jsonPayload == newJson) return@launch
                    hotPlayersCache[player.id] = newJson to player
                    playerDao.insertPlayer(
                        PlayerEntity(
                            id = player.id,
                            fullName = player.fullName,
                            primaryRole = player.primaryRole,
                            isArchived = player.isArchived,
                            jsonPayload = newJson,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }

        scope.launch {
            syncPendingToCloud(cleanUid)
        }
    }

    fun stopSync() {
        creatorSyncListener?.remove()
        creatorSyncListener = null
        selfDocListener?.remove()
        selfDocListener = null
        currentSyncedUserId = null
    }

    suspend fun syncPendingToCloud(userId: String): Int = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext 0
        if (!isCloudBackedUid(userId)) return@withContext 0
        var synced = 0
        try {
            val localPlayers = playerDao.getAllPlayers().mapNotNull { parsePlayerCached(it) }
                .filter { it.creatorId == userId || it.id == userId }
            for (player in localPlayers) {
                try {
                    fs.collection("players").document(player.id).set(player).await()
                    synced++
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        synced
    }

    fun getAllPlayersFlow(): Flow<List<Player>> = sharedAllPlayersFlow

    fun getMyPlayersFlow(userId: String): Flow<List<Player>> {
        val cleanUid = userId.trim()
        return getAllPlayersFlow().map { players ->
            val filtered = when {
                cleanUid.isBlank() -> players.filter { it.creatorId.startsWith("guest") || it.creatorId.isEmpty() }
                cleanUid.startsWith("guest") -> players.filter {
                    it.creatorId == cleanUid || it.id == cleanUid || it.creatorId.startsWith("guest")
                }
                else -> players.filter {
                    it.creatorId == cleanUid || it.id == cleanUid
                }
            }
            filtered.distinctBy { it.fullName.trim().lowercase() }
        }
            .flowOn(Dispatchers.Default)
            .distinctUntilChanged()
    }

    suspend fun findPlayerByName(name: String, creatorId: String? = null): Player? = withContext(Dispatchers.IO) {
        val normalized = name.trim().lowercase()
        if (normalized.isBlank()) return@withContext null
        val cached = playerDao.getAllPlayers()
        cached.mapNotNull { parsePlayerCached(it) }
            .firstOrNull { p ->
                p.fullName.trim().lowercase() == normalized &&
                    (creatorId.isNullOrBlank() || p.creatorId == creatorId || p.creatorId.isBlank())
            }
    }

    suspend fun getPlayerById(id: String): Player? = withContext(Dispatchers.IO) {
        val cached = playerDao.getPlayerById(id)
        if (cached != null) {
            val player = parsePlayerCached(cached)
            if (player != null) return@withContext player
        }
        val fs = firestore ?: return@withContext null
        return@withContext try {
            val doc = fs.collection("players").document(id).get().await()
            doc.toObject(Player::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun syncUserAsPlayerProfile(user: com.example.data.model.User): Player = withContext(Dispatchers.IO) {
        val playerId = user.effectivePlayerId.ifBlank { user.uid }
        val existing = getPlayerById(playerId)
        val mergedTeams = (existing?.teamIds.orEmpty() + user.joinedTeamIds).distinct()
        val resolvedName = user.displayName.trim().ifBlank { existing?.fullName ?: "Member" }
        val toSave = (existing ?: Player(id = playerId, creatorId = user.uid)).copy(
            id = playerId,
            fullName = resolvedName,
            photoUrl = user.photoUrl ?: existing?.photoUrl,
            primaryRole = user.primaryRole.ifBlank { existing?.primaryRole ?: com.example.data.model.PlayerRoles.ALL_ROUNDER },
            battingStyle = user.battingStyle.ifBlank { existing?.battingStyle ?: "Right Hand" },
            bowlingStyle = user.bowlingStyle.ifBlank { existing?.bowlingStyle ?: "Right-arm Medium" },
            teamIds = mergedTeams,
            creatorId = user.uid,
            isArchived = false
        )
        val serializedProfile = JsonHelper.toJson(toSave)
        hotPlayersCache[toSave.id] = serializedProfile to toSave
        playerDao.insertPlayer(
            PlayerEntity(
                id = toSave.id,
                fullName = toSave.fullName,
                primaryRole = toSave.primaryRole,
                isArchived = false,
                jsonPayload = serializedProfile,
                updatedAt = System.currentTimeMillis()
            )
        )
        val fs = firestore
        if (fs != null) {
            scope.launch {
                try {
                    fs.collection("players").document(toSave.id).set(toSave).await()
                } catch (_: Exception) {}
            }
        }
        toSave
    }

    suspend fun setPlayerTeamMembership(playerId: String, teamId: String, isMember: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val existing = getPlayerById(playerId) ?: return@withContext Result.failure(IllegalArgumentException("Player not found"))
        val updatedTeams = if (isMember) {
            (existing.teamIds + teamId).distinct()
        } else {
            existing.teamIds.filter { it != teamId }
        }
        val updated = existing.copy(teamIds = updatedTeams)
        val serialized = JsonHelper.toJson(updated)
        hotPlayersCache[updated.id] = serialized to updated
        playerDao.insertPlayer(
            PlayerEntity(
                id = updated.id,
                fullName = updated.fullName,
                primaryRole = updated.primaryRole,
                isArchived = updated.isArchived,
                jsonPayload = serialized,
                updatedAt = System.currentTimeMillis()
            )
        )
        val fs = firestore
        if (fs != null) {
            scope.launch {
                try {
                    fs.collection("players").document(updated.id).set(updated).await()
                } catch (_: Exception) {}
            }
        }
        Result.success(Unit)
    }

    suspend fun savePlayer(player: Player): Result<Unit> = withContext(Dispatchers.IO) {
        val trimmedName = player.fullName.trim()
        if (trimmedName.isBlank()) return@withContext Result.failure(IllegalArgumentException("Player name cannot be empty"))

        val activeAuthUid = try { FirebaseAuth.getInstance().currentUser?.uid } catch (_: Exception) { null }
        val resolvedCreator = when {
            player.creatorId.isNotBlank() -> player.creatorId
            !activeAuthUid.isNullOrBlank() -> activeAuthUid
            else -> ""
        }
        val basePlayer = if (resolvedCreator != player.creatorId) player.copy(creatorId = resolvedCreator) else player
        val existing = findPlayerByName(trimmedName, resolvedCreator.ifBlank { null })

        val toSave = when {
            existing != null && existing.id == basePlayer.id -> {
                basePlayer.copy(fullName = trimmedName)
            }
            basePlayer.id.isNotBlank() && existing == null -> {
                basePlayer.copy(fullName = trimmedName)
            }
            existing != null -> {
                basePlayer.copy(
                    id = existing.id,
                    fullName = trimmedName,
                    teamIds = (existing.teamIds + basePlayer.teamIds).distinct()
                )
            }
            else -> {
                basePlayer.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    fullName = trimmedName
                )
            }
        }

        // Local save first into Room
        val serialized = JsonHelper.toJson(toSave)
        hotPlayersCache[toSave.id] = serialized to toSave
        val entity = PlayerEntity(
            id = toSave.id,
            fullName = toSave.fullName,
            primaryRole = toSave.primaryRole,
            isArchived = toSave.isArchived,
            jsonPayload = serialized,
            updatedAt = System.currentTimeMillis()
        )
        playerDao.insertPlayer(entity)

        val fs = firestore
        if (fs != null) {
            scope.launch {
                try {
                    fs.collection("players").document(toSave.id).set(toSave).await()
                } catch (_: Exception) {
                }
            }
        }
        Result.success(Unit)
    }

    suspend fun setPlayerArchived(playerId: String, isArchived: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val existing = getPlayerById(playerId)
            if (existing != null) {
                val updated = existing.copy(isArchived = isArchived)
                val serialized = JsonHelper.toJson(updated)
                hotPlayersCache[updated.id] = serialized to updated
                playerDao.insertPlayer(
                    PlayerEntity(
                        id = updated.id,
                        fullName = updated.fullName,
                        primaryRole = updated.primaryRole,
                        isArchived = updated.isArchived,
                        jsonPayload = serialized,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            firestore?.collection("players")?.document(playerId)?.update("isArchived", isArchived)?.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePlayer(playerId: String): Result<Unit> = withContext(Dispatchers.IO) {
        hotPlayersCache.remove(playerId)
        playerDao.deletePlayerById(playerId)
        val fs = firestore
        if (fs != null) {
            scope.launch {
                try {
                    fs.collection("players").document(playerId).delete().await()
                } catch (_: Exception) {
                }
            }
        }
        Result.success(Unit)
    }

    suspend fun migrateCreatorUid(oldUid: String, newUid: String): Int = withContext(Dispatchers.IO) {
        if (oldUid.isBlank() || newUid.isBlank() || oldUid == newUid) return@withContext 0
        val cached = playerDao.getAllPlayers()
        var migratedCount = 0
        for (entity in cached) {
            val player = parsePlayerCached(entity) ?: continue
            if (player.creatorId != oldUid) continue
            val updated = player.copy(creatorId = newUid)
            val serialized = JsonHelper.toJson(updated)
            hotPlayersCache[updated.id] = serialized to updated
            playerDao.insertPlayer(
                PlayerEntity(
                    id = updated.id,
                    fullName = updated.fullName,
                    primaryRole = updated.primaryRole,
                    isArchived = updated.isArchived,
                    jsonPayload = serialized,
                    updatedAt = System.currentTimeMillis()
                )
            )
            val fs = firestore
            if (fs != null) {
                scope.launch {
                    try {
                        fs.collection("players").document(updated.id).set(updated).await()
                    } catch (_: Exception) {}
                }
            }
            migratedCount++
        }
        migratedCount
    }

    suspend fun clearAllPlayers() {
        hotPlayersCache.clear()
        playerDao.deleteAllPlayers()
    }
}
