package com.example.data.model

import com.google.firebase.firestore.IgnoreExtraProperties

enum class PostMediaType {
    NONE,
    IMAGE,
    VIDEO
}

@IgnoreExtraProperties
data class PostComment(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val text: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class PostFeeling(
    val key: String,
    val emoji: String
)

object CommunityPostFeelings {
    val all: List<PostFeeling> = listOf(
        PostFeeling("Happy", "😊"),
        PostFeeling("Blessed", "🙏"),
        PostFeeling("Loved", "❤️"),
        PostFeeling("Sad", "😔"),
        PostFeeling("Lovely", "🥰"),
        PostFeeling("Thankful", "🙌"),
        PostFeeling("Excited", "🤩"),
        PostFeeling("Crazy", "🤪")
    )

    fun findByKey(key: String): PostFeeling? {
        if (key.isBlank()) return null
        return all.firstOrNull { it.key.equals(key.trim(), ignoreCase = true) }
    }

    fun formatLabel(key: String): String {
        val found = findByKey(key) ?: return if (key.isBlank()) "" else "✨ ${key.trim()}"
        return "${found.emoji} ${found.key}"
    }
}

@IgnoreExtraProperties
data class CommunityPost(
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorAvatarUrl: String? = null,
    val categoryTag: String = "মুহূর্ত",
    val feeling: String = "",
    val contentText: String = "",
    val mediaType: String = PostMediaType.NONE.name,
    val imageUrl: String? = null,
    val videoThumbnailUrl: String? = null,
    val videoBase64: String? = null,
    val videoChunkCount: Int = 0,
    val localVideoPath: String? = null,
    val videoDurationMs: Long = 0L,
    val likedByIds: List<String> = emptyList(),
    val comments: List<PostComment> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: String = SYNC_STATUS_SYNCED
) {
    companion object {
        const val SYNC_STATUS_PENDING = "PENDING"
        const val SYNC_STATUS_SYNCED = "SYNCED"
    }

    fun resolvedMediaType(): PostMediaType = when (mediaType.uppercase()) {
        PostMediaType.IMAGE.name -> PostMediaType.IMAGE
        PostMediaType.VIDEO.name -> PostMediaType.VIDEO
        else -> PostMediaType.NONE
    }
}
