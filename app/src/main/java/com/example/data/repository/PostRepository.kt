package com.example.data.repository

import android.content.Context
import com.example.data.model.CommunityPost
import com.example.data.model.PostComment
import com.example.data.model.PostMediaType
import com.example.util.JsonHelper
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.squareup.moshi.Types
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class MemberProfile(
    val displayName: String = "",
    val photoUrl: String? = null
)

class PostRepository(
    private val context: Context? = null,
    private val firestore: FirebaseFirestore? = try { FirebaseFirestore.getInstance() } catch (_: Exception) { null },
    private val enableCloudListeners: Boolean = firestore != null
) {
    companion object {
        const val DEFAULT_FEED_PAGE_SIZE = 10
        private const val PREFS_NAME = "cricup_member_profile"
        private const val KEY_DISPLAY_NAME = "member_display_name"
        private const val KEY_PHOTO_URL = "member_photo_url"
        private const val KEY_PHOTO_CLEARED = "member_photo_explicitly_cleared"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()

    private val _postsFlow = MutableStateFlow<List<CommunityPost>>(emptyList())
    val postsFlow: Flow<List<CommunityPost>> = _postsFlow.asStateFlow()

    private val _isRefreshingFeed = MutableStateFlow(false)
    val isRefreshingFeed: StateFlow<Boolean> = _isRefreshingFeed.asStateFlow()

    private val _hasMoreCloudPosts = MutableStateFlow(true)
    val hasMoreCloudPosts: StateFlow<Boolean> = _hasMoreCloudPosts.asStateFlow()

    private val _memberProfileFlow = MutableStateFlow(loadSavedProfile())
    val memberProfileFlow: StateFlow<MemberProfile> = _memberProfileFlow.asStateFlow()

    private var listenerRegistration: ListenerRegistration? = null
    private var currentCloudSyncLimit: Int = DEFAULT_FEED_PAGE_SIZE

    private val listType = Types.newParameterizedType(List::class.java, CommunityPost::class.java)
    private val listAdapter by lazy { JsonHelper.moshi.adapter<List<CommunityPost>>(listType) }

    init {
        scope.launch {
            loadLocalCache()
            if (enableCloudListeners) {
                startPostsSync(currentCloudSyncLimit)
            }
        }
    }

    private fun loadSavedProfile(): MemberProfile {
        val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?: return MemberProfile()
        val photoCleared = prefs.getBoolean(KEY_PHOTO_CLEARED, false)
        val savedName = prefs.getString(KEY_DISPLAY_NAME, null)
            ?.trim()
            ?.takeUnless { AuthRepository.isPlaceholderProfileName(it) }
            .orEmpty()
        return MemberProfile(
            displayName = savedName,
            photoUrl = if (photoCleared) null else prefs.getString(KEY_PHOTO_URL, null)?.takeIf { it.isNotBlank() }
        )
    }

    private fun cacheFile(): File? {
        val dir = context?.filesDir ?: return null
        return File(dir, "community_posts_cache.json")
    }

    private suspend fun loadLocalCache() {
        mutex.withLock {
            val file = cacheFile() ?: return@withLock
            if (!file.exists()) return@withLock
            try {
                val json = file.readText()
                if (json.isNotBlank()) {
                    val cached = listAdapter.fromJson(json).orEmpty()
                    _postsFlow.value = cached.sortedByDescending { it.createdAt }
                }
            } catch (e: Exception) {
                android.util.Log.w("PostRepository", "Failed reading posts cache: ${e.message}")
            }
        }
    }

    private fun persistLocalCacheLocked(posts: List<CommunityPost>) {
        val file = cacheFile() ?: return
        try {
            // Keep up to 35 latest posts locally to avoid bloated JSON serialization overhead
            file.writeText(listAdapter.toJson(posts.take(35)))
        } catch (e: Exception) {
            android.util.Log.w("PostRepository", "Failed writing posts cache: ${e.message}")
        }
    }

    private fun startPostsSync(limit: Int) {
        val fs = firestore ?: return
        currentCloudSyncLimit = limit.coerceAtLeast(DEFAULT_FEED_PAGE_SIZE)
        listenerRegistration?.remove()
        listenerRegistration = fs.collection("posts")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(currentCloudSyncLimit.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                scope.launch {
                    val cloudPosts = snapshot.documents.mapNotNull { doc ->
                        try {
                            doc.toObject(CommunityPost::class.java)?.copy(
                                id = doc.id,
                                syncStatus = CommunityPost.SYNC_STATUS_SYNCED
                            )
                        } catch (_: Exception) {
                            null
                        }
                    }
                    _hasMoreCloudPosts.value = cloudPosts.size >= currentCloudSyncLimit
                    mergeCloudPosts(cloudPosts)
                }
            }
    }

    private suspend fun mergeCloudPosts(cloudPosts: List<CommunityPost>) {
        mutex.withLock {
            val currentPosts = _postsFlow.value
            val existingById = currentPosts.associateBy { it.id }
            val mergedCloud = cloudPosts.map { cloud ->
                val local = existingById[cloud.id]
                if (local != null && !local.localVideoPath.isNullOrBlank()) {
                    cloud.copy(localVideoPath = local.localVideoPath)
                } else {
                    cloud
                }
            }
            val cloudIds = mergedCloud.map { it.id }.toSet()
            val preservedOthers = currentPosts.filter { it.id !in cloudIds }
            val combined = (preservedOthers + mergedCloud)
                .distinctBy { it.id }
                .sortedByDescending { it.createdAt }
            if (combined != currentPosts) {
                _postsFlow.value = combined
                persistLocalCacheLocked(combined)
            }
        }
    }

    /**
     * Refreshes feed from Firestore and loads up to [targetLimit] latest posts
     * when the user scrolls down past the current page.
     */
    suspend fun refreshAndLoadMorePosts(targetLimit: Int): Result<Int> = withContext(Dispatchers.IO) {
        val safeLimit = targetLimit.coerceAtLeast(DEFAULT_FEED_PAGE_SIZE)
        val fs = firestore
        if (fs == null) {
            return@withContext Result.success(_postsFlow.value.size)
        }
        _isRefreshingFeed.value = true
        return@withContext try {
            val snap = fs.collection("posts")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(safeLimit.toLong())
                .get()
                .await()
            val cloudPosts = snap.documents.mapNotNull { doc ->
                try {
                    doc.toObject(CommunityPost::class.java)?.copy(
                        id = doc.id,
                        syncStatus = CommunityPost.SYNC_STATUS_SYNCED
                    )
                } catch (_: Exception) {
                    null
                }
            }
            mergeCloudPosts(cloudPosts)
            _hasMoreCloudPosts.value = cloudPosts.size >= safeLimit
            if (safeLimit > currentCloudSyncLimit && enableCloudListeners) {
                startPostsSync(safeLimit)
            }
            Result.success(cloudPosts.size)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            _isRefreshingFeed.value = false
        }
    }

    suspend fun createPost(
        postId: String = "post_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
        authorId: String,
        authorName: String,
        authorAvatarUrl: String?,
        categoryTag: String = "",
        feeling: String = "",
        contentText: String,
        mediaType: PostMediaType,
        imageUrl: String? = null,
        videoThumbnailUrl: String? = null,
        videoBase64: String? = null,
        videoChunks: List<String> = emptyList(),
        localVideoPath: String? = null,
        videoDurationMs: Long = 0L
    ): Result<CommunityPost> = withContext(Dispatchers.IO) {
        val trimmed = contentText.trim()
        if (trimmed.isBlank() && imageUrl.isNullOrBlank() && videoThumbnailUrl.isNullOrBlank() && localVideoPath.isNullOrBlank() && videoBase64.isNullOrBlank()) {
            return@withContext Result.failure(IllegalArgumentException("পোস্ট করার জন্য কিছু লিখুন অথবা ছবি/ভিডিও নির্বাচন করুন"))
        }

        val now = System.currentTimeMillis()
        val localPost = CommunityPost(
            id = postId,
            authorId = authorId,
            authorName = authorName.ifBlank { "CricUp Member" },
            authorAvatarUrl = authorAvatarUrl,
            categoryTag = categoryTag.trim(),
            feeling = feeling.trim(),
            contentText = trimmed,
            mediaType = mediaType.name,
            imageUrl = imageUrl,
            videoThumbnailUrl = videoThumbnailUrl,
            videoBase64 = videoBase64,
            videoChunkCount = videoChunks.size,
            localVideoPath = localVideoPath,
            videoDurationMs = videoDurationMs,
            likedByIds = emptyList(),
            comments = emptyList(),
            createdAt = now,
            updatedAt = now,
            syncStatus = CommunityPost.SYNC_STATUS_PENDING
        )

        mutex.withLock {
            val updated = (listOf(localPost) + _postsFlow.value.filterNot { it.id == postId })
                .sortedByDescending { it.createdAt }
            _postsFlow.value = updated
            persistLocalCacheLocked(updated)
        }

        val fs = firestore
        if (fs != null) {
            scope.launch {
                try {
                    // Upload video chunks first if present
                    videoChunks.forEachIndexed { idx, chunk ->
                        fs.collection("posts").document(postId)
                            .collection("chunks").document(idx.toString())
                            .set(mapOf("index" to idx, "data" to chunk))
                            .await()
                    }
                    val cloudPayload = localPost.copy(
                        localVideoPath = null, // Do not upload device-local path to other users
                        syncStatus = CommunityPost.SYNC_STATUS_SYNCED
                    )
                    fs.collection("posts").document(postId).set(cloudPayload).await()

                    val syncedLocal = localPost.copy(syncStatus = CommunityPost.SYNC_STATUS_SYNCED)
                    mutex.withLock {
                        val updated = _postsFlow.value.map { if (it.id == postId) syncedLocal else it }
                        _postsFlow.value = updated
                        persistLocalCacheLocked(updated)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("PostRepository", "Post saved locally; cloud sync queued: ${e.message}")
                }
            }
        }

        Result.success(localPost)
    }

    suspend fun toggleLike(postId: String, userId: String) = withContext(Dispatchers.IO) {
        var updatedPost: CommunityPost? = null
        mutex.withLock {
            val nextList = _postsFlow.value.map { post ->
                if (post.id == postId) {
                    val currentLikes = post.likedByIds.toMutableList()
                    if (currentLikes.contains(userId)) {
                        currentLikes.remove(userId)
                    } else {
                        currentLikes.add(userId)
                    }
                    post.copy(
                        likedByIds = currentLikes,
                        updatedAt = System.currentTimeMillis()
                    ).also { updatedPost = it }
                } else post
            }
            _postsFlow.value = nextList
            persistLocalCacheLocked(nextList)
        }

        val post = updatedPost ?: return@withContext
        val fs = firestore ?: return@withContext
        try {
            fs.collection("posts").document(postId)
                .update(
                    mapOf(
                        "likedByIds" to post.likedByIds,
                        "updatedAt" to post.updatedAt
                    )
                ).await()
        } catch (_: Exception) {
        }
    }

    suspend fun addComment(
        postId: String,
        authorId: String,
        authorName: String,
        commentText: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val clean = commentText.trim()
        if (clean.isBlank()) return@withContext Result.failure(IllegalArgumentException("মন্তব্য লিখুন"))

        val comment = PostComment(
            id = "cmt_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}",
            authorId = authorId,
            authorName = authorName.ifBlank { "Member" },
            text = clean,
            createdAt = System.currentTimeMillis()
        )

        var updatedPost: CommunityPost? = null
        mutex.withLock {
            val nextList = _postsFlow.value.map { post ->
                if (post.id == postId) {
                    post.copy(
                        comments = post.comments + comment,
                        updatedAt = System.currentTimeMillis()
                    ).also { updatedPost = it }
                } else post
            }
            _postsFlow.value = nextList
            persistLocalCacheLocked(nextList)
        }

        val post = updatedPost
        val fs = firestore
        if (post != null && fs != null) {
            try {
                fs.collection("posts").document(postId)
                    .update(
                        mapOf(
                            "comments" to post.comments,
                            "updatedAt" to post.updatedAt
                        )
                    ).await()
            } catch (_: Exception) {
            }
        }
        Result.success(Unit)
    }

    suspend fun deletePost(postId: String) = withContext(Dispatchers.IO) {
        var removedLocalVideo: String? = null
        mutex.withLock {
            removedLocalVideo = _postsFlow.value.firstOrNull { it.id == postId }?.localVideoPath
            val nextList = _postsFlow.value.filterNot { it.id == postId }
            _postsFlow.value = nextList
            persistLocalCacheLocked(nextList)
        }
        removedLocalVideo?.takeIf { it.isNotBlank() }?.let { localPath ->
            try {
                File(localPath).delete()
            } catch (_: Exception) {
            }
        }
        val fs = firestore ?: return@withContext
        try {
            fs.collection("posts").document(postId).delete().await()
        } catch (_: Exception) {
        }
    }

    suspend fun fetchVideoChunks(postId: String): List<String> = withContext(Dispatchers.IO) {
        val fs = firestore ?: return@withContext emptyList()
        return@withContext try {
            val snap = fs.collection("posts").document(postId)
                .collection("chunks")
                .orderBy("index")
                .get()
                .await()
            snap.documents.mapNotNull { it.getString("data") }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getSavedMemberDisplayName(defaultFallback: String): String {
        val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?: return defaultFallback
        val saved = prefs.getString(KEY_DISPLAY_NAME, null)
            ?.trim()
            ?.takeUnless { AuthRepository.isPlaceholderProfileName(it) }
        return if (!saved.isNullOrBlank()) saved else defaultFallback
    }

    fun getSavedMemberPhoto(defaultFallback: String? = null): String? {
        val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?: return defaultFallback
        if (prefs.getBoolean(KEY_PHOTO_CLEARED, false)) return defaultFallback
        val saved = prefs.getString(KEY_PHOTO_URL, null)
        return if (!saved.isNullOrBlank()) saved else defaultFallback
    }

    /**
     * Synchronizes a recovered or Firestore-updated user profile into PostRepository
     * without ever downgrading a real name to a placeholder or wiping an existing photo.
     */
    fun syncProfileFromAuth(
        name: String,
        photoUrl: String?,
        explicitlyCleared: Boolean = false,
        currentUserId: String = ""
    ) {
        val clean = name.trim()
        val existing = _memberProfileFlow.value
        val incomingIsReal = !AuthRepository.isPlaceholderProfileName(clean)
        val existingIsReal = !AuthRepository.isPlaceholderProfileName(existing.displayName)

        val resolvedName = when {
            incomingIsReal -> clean
            existingIsReal -> existing.displayName
            else -> ""
        }
        val existingPhoto = existing.photoUrl?.takeIf { it.isNotBlank() } ?: getSavedMemberPhoto()
        val resolvedPhoto = when {
            explicitlyCleared -> null
            !photoUrl.isNullOrBlank() -> photoUrl
            else -> existingPhoto
        }

        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.apply {
                if (resolvedName.isNotBlank() && !AuthRepository.isPlaceholderProfileName(resolvedName)) {
                    putString(KEY_DISPLAY_NAME, resolvedName)
                }
                if (explicitlyCleared) {
                    remove(KEY_PHOTO_URL)
                    putBoolean(KEY_PHOTO_CLEARED, true)
                } else if (!resolvedPhoto.isNullOrBlank()) {
                    putString(KEY_PHOTO_URL, resolvedPhoto)
                    putBoolean(KEY_PHOTO_CLEARED, false)
                }
            }
            ?.apply()

        _memberProfileFlow.value = MemberProfile(
            displayName = resolvedName,
            photoUrl = resolvedPhoto
        )

        if (currentUserId.isNotBlank() && (resolvedName.isNotBlank() || resolvedPhoto != null || explicitlyCleared)) {
            scope.launch {
                mutex.withLock {
                    var changed = false
                    val updated = _postsFlow.value.map { post ->
                        if (post.authorId == currentUserId) {
                            val nextName = if (resolvedName.isNotBlank() && AuthRepository.isPlaceholderProfileName(post.authorName)) {
                                resolvedName
                            } else {
                                post.authorName
                            }
                            val nextAvatar = when {
                                explicitlyCleared -> null
                                !resolvedPhoto.isNullOrBlank() && post.authorAvatarUrl.isNullOrBlank() -> resolvedPhoto
                                else -> post.authorAvatarUrl
                            }
                            if (nextName != post.authorName || nextAvatar != post.authorAvatarUrl) {
                                changed = true
                                post.copy(authorName = nextName, authorAvatarUrl = nextAvatar)
                            } else {
                                post
                            }
                        } else {
                            post
                        }
                    }
                    if (changed) {
                        _postsFlow.value = updated
                        persistLocalCacheLocked(updated)
                    }
                }
            }
        }
    }

    fun saveMemberDisplayName(name: String, userId: String) {
        val currentPhoto = _memberProfileFlow.value.photoUrl ?: getSavedMemberPhoto()
        saveMemberProfile(name = name, photoUrl = currentPhoto, userId = userId, clearPhoto = false)
    }

    fun saveMemberProfile(
        name: String,
        photoUrl: String?,
        userId: String,
        clearPhoto: Boolean = false
    ) {
        val clean = name.trim()
        if (clean.isBlank()) return

        val existingPhoto = _memberProfileFlow.value.photoUrl?.takeIf { it.isNotBlank() } ?: getSavedMemberPhoto()
        val resolvedPhoto = when {
            clearPhoto -> null
            !photoUrl.isNullOrBlank() -> photoUrl
            else -> existingPhoto
        }

        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.apply {
                putString(KEY_DISPLAY_NAME, clean)
                if (clearPhoto) {
                    remove(KEY_PHOTO_URL)
                    putBoolean(KEY_PHOTO_CLEARED, true)
                } else if (resolvedPhoto != null) {
                    putString(KEY_PHOTO_URL, resolvedPhoto)
                    putBoolean(KEY_PHOTO_CLEARED, false)
                }
            }
            ?.apply()

        _memberProfileFlow.value = MemberProfile(displayName = clean, photoUrl = resolvedPhoto)

        scope.launch {
            var authoredIds = emptyList<String>()
            if (userId.isNotBlank()) {
                mutex.withLock {
                    val updated = _postsFlow.value.map { post ->
                        if (post.authorId == userId) {
                            authoredIds = authoredIds + post.id
                            post.copy(
                                authorName = clean,
                                authorAvatarUrl = resolvedPhoto
                            )
                        } else post
                    }
                    _postsFlow.value = updated
                    persistLocalCacheLocked(updated)
                }
            }

            val fs = firestore
            if (fs != null && userId.isNotBlank()) {
                try {
                    val userPatch = mutableMapOf<String, Any?>(
                        "uid" to userId,
                        "displayName" to clean,
                        "lastLoginAt" to System.currentTimeMillis()
                    )
                    if (clearPhoto) {
                        userPatch["photoUrl"] = null
                    } else if (!resolvedPhoto.isNullOrBlank()) {
                        userPatch["photoUrl"] = resolvedPhoto
                    }
                    fs.collection("users").document(userId).set(userPatch, SetOptions.merge()).await()
                    authoredIds.forEach { pid ->
                        try {
                            val postPatch = mutableMapOf<String, Any?>(
                                "authorName" to clean
                            )
                            if (clearPhoto) {
                                postPatch["authorAvatarUrl"] = null
                            } else if (!resolvedPhoto.isNullOrBlank()) {
                                postPatch["authorAvatarUrl"] = resolvedPhoto
                            }
                            fs.collection("posts").document(pid).update(postPatch).await()
                        } catch (_: Exception) {
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    suspend fun migrateAuthorId(
        oldUid: String,
        newUid: String,
        authorName: String,
        authorPhoto: String?
    ): Int = withContext(Dispatchers.IO) {
        if (oldUid.isBlank() || newUid.isBlank() || oldUid == newUid) return@withContext 0
        val migratedPostIds = mutableListOf<String>()
        mutex.withLock {
            val updated = _postsFlow.value.map { post ->
                if (post.authorId == oldUid) {
                    migratedPostIds.add(post.id)
                    post.copy(
                        authorId = newUid,
                        authorName = if (authorName.isNotBlank() && !AuthRepository.isPlaceholderProfileName(authorName)) authorName else post.authorName,
                        authorAvatarUrl = authorPhoto ?: post.authorAvatarUrl
                    )
                } else {
                    post
                }
            }
            if (migratedPostIds.isNotEmpty()) {
                _postsFlow.value = updated
                persistLocalCacheLocked(updated)
            }
        }
        val fs = firestore
        if (fs != null && migratedPostIds.isNotEmpty()) {
            migratedPostIds.forEach { pid ->
                try {
                    val patch = mutableMapOf<String, Any?>("authorId" to newUid)
                    if (authorName.isNotBlank() && !AuthRepository.isPlaceholderProfileName(authorName)) {
                        patch["authorName"] = authorName
                    }
                    if (!authorPhoto.isNullOrBlank()) {
                        patch["authorAvatarUrl"] = authorPhoto
                    }
                    fs.collection("posts").document(pid).update(patch).await()
                } catch (_: Exception) {}
            }
        }
        migratedPostIds.size
    }
}
