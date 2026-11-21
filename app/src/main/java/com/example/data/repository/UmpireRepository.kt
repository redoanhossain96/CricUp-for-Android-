package com.example.data.repository

import com.example.data.local.dao.UmpireDao
import com.example.data.local.entity.UmpireEntity
import com.example.data.model.Umpire
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class UmpireRepository(
    private val umpireDao: UmpireDao,
    private val firestore: FirebaseFirestore? = try { FirebaseFirestore.getInstance() } catch (_: Exception) { null },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, t ->
        android.util.Log.e("UmpireRepository", "Coroutine exception handled", t)
    }),
    private val enableCloudSync: Boolean = firestore != null
) {
    private val hotUmpiresCache = ConcurrentHashMap<String, Pair<String, Umpire>>()
    private var creatorSyncListener: ListenerRegistration? = null
    @Volatile
    private var currentSyncedUserId: String? = null

    private fun parseUmpireCached(entity: UmpireEntity): Umpire? {
        val cached = hotUmpiresCache[entity.id]
        if (cached != null && cached.first == entity.jsonPayload) {
            return cached.second
        }
        val parsed = JsonHelper.fromJson<Umpire>(entity.jsonPayload) ?: return null
        hotUmpiresCache[entity.id] = entity.jsonPayload to parsed
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

    private val sharedAllUmpiresFlow: Flow<List<Umpire>> = umpireDao.getAllUmpiresFlow()
        .map { entities ->
            entities.mapNotNull { parseUmpireCached(it) }
        }
        .catch { err ->
            android.util.Log.e("UmpireRepository", "Umpires flow error: ${err.message}", err)
            if (err is android.database.sqlite.SQLiteBlobTooBigException) {
                runCatching { umpireDao.deleteUnreadableOversizedUmpires() }
            }
            emit(emptyList())
        }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L, replayExpirationMillis = 0L), replay = 1)

    init {
        if (enableCloudSync && firestore != null) {
            val initialUid = try { FirebaseAuth.getInstance().currentUser?.uid } catch (_: Exception) { null }
            if (!initialUid.isNullOrBlank()) {
                syncUmpiresForUser(initialUid)
            }
        }
    }

    fun syncUmpiresForUser(userId: String) {
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

        try {
            creatorSyncListener = fs.collection("umpires")
                .whereEqualTo("creatorId", cleanUid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    val changes = snapshot.documentChanges
                    if (changes.isEmpty()) return@addSnapshotListener
                    scope.launch {
                        try {
                            if (currentSyncedUserId != cleanUid) return@launch
                            val existingById = umpireDao.getAllUmpiresList().associateBy { it.id }
                            val toUpsert = mutableListOf<UmpireEntity>()
                            for (change in changes) {
                                val doc = change.document
                                if (change.type == DocumentChange.Type.REMOVED) {
                                    hotUmpiresCache.remove(doc.id)
                                    umpireDao.deleteUmpireById(doc.id)
                                    continue
                                }
                                val umpire = doc.toObject(Umpire::class.java)?.let {
                                    if (it.id.isBlank()) it.copy(id = doc.id) else it
                                } ?: continue
                                val newJson = JsonHelper.toJson(umpire)
                                val existing = existingById[umpire.id]
                                if (existing != null && existing.jsonPayload == newJson) continue
                                hotUmpiresCache[umpire.id] = newJson to umpire
                                toUpsert.add(
                                    UmpireEntity(
                                        id = umpire.id,
                                        fullName = umpire.fullName.trim(),
                                        isArchived = umpire.isArchived,
                                        jsonPayload = newJson,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                )
                            }
                            if (toUpsert.isNotEmpty() && currentSyncedUserId == cleanUid) {
                                umpireDao.insertUmpires(toUpsert)
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("UmpireRepository", "Delta sync error: ${e.message}")
                        }
                    }
                }

            scope.launch {
                syncPendingToCloud(cleanUid)
            }
        } catch (e: Exception) {
            android.util.Log.w("UmpireRepository", "Sync initialization failed", e)
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
            val localUmpires = umpireDao.getAllUmpiresList().mapNotNull { parseUmpireCached(it) }
                .filter { it.creatorId == userId }
            for (umpire in localUmpires) {
                try {
                    fs.collection("umpires").document(umpire.id).set(umpire).await()
                    synced++
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        synced
    }

    fun getAllUmpiresFlow(): Flow<List<Umpire>> = sharedAllUmpiresFlow

    fun getMyUmpiresFlow(userId: String): Flow<List<Umpire>> {
        val cleanUid = userId.trim()
        return getAllUmpiresFlow().map { umpires ->
            val filtered = when {
                cleanUid.isBlank() -> umpires.filter { it.creatorId.startsWith("guest") || it.creatorId.isEmpty() }
                cleanUid.startsWith("guest") -> umpires.filter { it.creatorId == cleanUid || it.creatorId.startsWith("guest") }
                else -> umpires.filter { it.creatorId == cleanUid }
            }
            filtered.distinctBy { it.fullName.trim().lowercase() }
        }
            .flowOn(Dispatchers.Default)
            .distinctUntilChanged()
    }

    suspend fun getUmpireById(id: String): Umpire? = withContext(Dispatchers.IO) {
        val cached = umpireDao.getUmpireById(id)
        if (cached != null) {
            val umpire = parseUmpireCached(cached)
            if (umpire != null) return@withContext umpire
        }
        val fs = firestore ?: return@withContext null
        return@withContext try {
            val doc = fs.collection("umpires").document(id).get().await()
            doc.toObject(Umpire::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveUmpire(umpire: Umpire): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val trimmedName = umpire.fullName.trim()
            if (trimmedName.isBlank()) return@withContext Result.success(Unit)

            val activeAuthUid = try { FirebaseAuth.getInstance().currentUser?.uid } catch (_: Exception) { null }
            val resolvedCreator = when {
                umpire.creatorId.isNotBlank() -> umpire.creatorId
                !activeAuthUid.isNullOrBlank() -> activeAuthUid
                else -> ""
            }

            val allLocal = try {
                umpireDao.getAllUmpiresList()
            } catch (e: android.database.sqlite.SQLiteBlobTooBigException) {
                android.util.Log.w("UmpireRepository", "Pruning unreadable oversized umpire row", e)
                runCatching { umpireDao.deleteUnreadableOversizedUmpires() }
                runCatching { umpireDao.getAllUmpiresList() }.getOrDefault(emptyList())
            }
            val parsedLocal = allLocal.mapNotNull { parseUmpireCached(it) }
            val existingMatch = parsedLocal.firstOrNull { u ->
                (umpire.id.isNotBlank() && u.id == umpire.id) ||
                    (u.fullName.trim().equals(trimmedName, ignoreCase = true) &&
                        (resolvedCreator.isBlank() || u.creatorId == resolvedCreator || u.creatorId.isBlank()))
            }

            val fs = firestore
            val targetId = when {
                umpire.id.isNotBlank() -> umpire.id
                existingMatch != null && existingMatch.id.isNotBlank() -> existingMatch.id
                fs != null -> fs.collection("umpires").document().id
                else -> java.util.UUID.randomUUID().toString()
            }

            // Preserve existing photo/profile metadata when saving by name (e.g., from CreateMatchScreen)
            val mergedPhoto = (umpire.photoUrl?.takeIf { it.isNotBlank() } ?: existingMatch?.photoUrl)
                ?.takeIf { it.length <= 500_000 }
            val toSave = umpire.copy(
                id = targetId,
                fullName = trimmedName,
                association = umpire.association.ifBlank { existingMatch?.association ?: "" },
                phone = umpire.phone.ifBlank { existingMatch?.phone ?: "" },
                photoUrl = mergedPhoto,
                certificationLevel = if (umpire.certificationLevel == "Club / Local" && !existingMatch?.certificationLevel.isNullOrBlank()) {
                    existingMatch!!.certificationLevel
                } else {
                    umpire.certificationLevel
                },
                matchesOfficiated = maxOf(umpire.matchesOfficiated, existingMatch?.matchesOfficiated ?: 0),
                creatorId = resolvedCreator.ifBlank { existingMatch?.creatorId ?: "" }
            )
            val serialized = JsonHelper.toJson(toSave)
            if (serialized.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Failed to serialize umpire"))
            }
            hotUmpiresCache[toSave.id] = serialized to toSave

            val entity = UmpireEntity(
                id = toSave.id,
                fullName = toSave.fullName,
                isArchived = toSave.isArchived,
                jsonPayload = serialized,
                updatedAt = System.currentTimeMillis()
            )
            umpireDao.insertUmpire(entity)

            if (fs != null) {
                scope.launch {
                    try {
                        fs.collection("umpires").document(toSave.id).set(toSave).await()
                    } catch (_: Exception) {
                    }
                }
            }
            Result.success(Unit)
        } catch (t: Throwable) {
            android.util.Log.e("UmpireRepository", "saveUmpire failed: ${t.message}", t)
            Result.failure(t)
        }
    }

    suspend fun migrateCreatorUid(oldUid: String, newUid: String): Int = withContext(Dispatchers.IO) {
        if (oldUid.isBlank() || newUid.isBlank() || oldUid == newUid) return@withContext 0
        val cached = umpireDao.getAllUmpiresList()
        var migratedCount = 0
        val fs = firestore
        for (entity in cached) {
            val u = parseUmpireCached(entity) ?: continue
            if (u.creatorId != oldUid) continue
            val updated = u.copy(creatorId = newUid)
            val serialized = JsonHelper.toJson(updated)
            hotUmpiresCache[updated.id] = serialized to updated
            umpireDao.insertUmpire(
                UmpireEntity(
                    id = updated.id,
                    fullName = updated.fullName,
                    isArchived = updated.isArchived,
                    jsonPayload = serialized,
                    updatedAt = System.currentTimeMillis()
                )
            )
            if (fs != null) {
                scope.launch {
                    try {
                        fs.collection("umpires").document(updated.id).set(updated).await()
                    } catch (_: Exception) {}
                }
            }
            migratedCount++
        }
        migratedCount
    }

    suspend fun deleteUmpire(umpireId: String): Result<Unit> = withContext(Dispatchers.IO) {
        hotUmpiresCache.remove(umpireId)
        umpireDao.deleteUmpireById(umpireId)
        val fs = firestore
        if (fs != null) {
            scope.launch {
                try {
                    fs.collection("umpires").document(umpireId).delete().await()
                } catch (_: Exception) {
                }
            }
        }
        return@withContext Result.success(Unit)
    }

    suspend fun clearAllUmpires() {
        hotUmpiresCache.clear()
        umpireDao.deleteAllUmpires()
    }
}
