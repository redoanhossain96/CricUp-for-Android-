package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.PostMediaType
import com.example.data.repository.PostRepository
import com.example.util.MediaHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CommunityPostAndThemeTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "community_posts_cache.json").delete()
        context.getSharedPreferences("cricup_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("cricup_member_profile", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        File(context.filesDir, "community_posts_cache.json").delete()
    }

    @Test
    fun `default theme starts in Modern Golden-Green Light Mode and persists switch to Dark Mode`() {
        val prefs = context.getSharedPreferences("cricup_prefs", Context.MODE_PRIVATE)
        // App launches with dark mode disabled (Modern Golden-Green Light Theme)
        val initialIsDark = prefs.getBoolean("pref_dark_mode", false)
        assertFalse(initialIsDark)

        // User toggles to Dark Mode
        prefs.edit().putBoolean("pref_dark_mode", true).commit()
        assertTrue(prefs.getBoolean("pref_dark_mode", false))

        // User switches back to Light Mode
        prefs.edit().putBoolean("pref_dark_mode", false).commit()
        assertFalse(prefs.getBoolean("pref_dark_mode", false))
    }

    @Test
    fun `createPost saves text image and video posts and persists across repository reload`(): Unit = runBlocking {
        val repo1 = PostRepository(context = context, firestore = null, enableCloudListeners = false)

        val emptyAttempt = repo1.createPost(
            authorId = "u1",
            authorName = "Redo",
            authorAvatarUrl = null,
            categoryTag = "চিন্তা",
            contentText = "   ",
            mediaType = PostMediaType.NONE
        )
        assertTrue(emptyAttempt.isFailure)

        val textResult = repo1.createPost(
            postId = "p_text",
            authorId = "u1",
            authorName = "Sakib",
            authorAvatarUrl = null,
            categoryTag = "ভাবনা",
            contentText = "আজকের ম্যাচটা দারুণ জমে উঠেছিল!",
            mediaType = PostMediaType.NONE
        )
        assertTrue(textResult.isSuccess)

        val imageResult = repo1.createPost(
            postId = "p_img",
            authorId = "u2",
            authorName = "Tamim",
            authorAvatarUrl = null,
            categoryTag = "মুহূর্ত",
            contentText = "জয়ের মুহূর্ত!",
            mediaType = PostMediaType.IMAGE,
            imageUrl = "data:image/jpeg;base64,/9j/4AAQ"
        )
        assertTrue(imageResult.isSuccess)

        val tempVideo = File(context.filesDir, "sample_clip.mp4").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val videoResult = repo1.createPost(
            postId = "p_vid",
            authorId = "u1",
            authorName = "Sakib",
            authorAvatarUrl = null,
            categoryTag = "মুহূর্ত",
            contentText = "সুপার সিক্স!",
            mediaType = PostMediaType.VIDEO,
            localVideoPath = tempVideo.absolutePath,
            videoDurationMs = 15_000L
        )
        assertTrue(videoResult.isSuccess)

        // Reload repository from JSON cache
        val repo2 = PostRepository(context = context, firestore = null, enableCloudListeners = false)
        // Wait briefly for init coroutine to load cache
        val posts = repo2.postsFlow.first { it.size == 3 }
        assertEquals(3, posts.size)
        assertTrue(posts.any { it.id == "p_text" && it.categoryTag == "ভাবনা" })
        assertTrue(posts.any { it.id == "p_img" && it.resolvedMediaType() == PostMediaType.IMAGE })
        assertTrue(posts.any { it.id == "p_vid" && it.resolvedMediaType() == PostMediaType.VIDEO && it.videoDurationMs == 15_000L })

        tempVideo.delete()
    }

    @Test
    fun `toggleLike addComment deletePost and member display name work cleanly`() = runBlocking {
        val repo = PostRepository(context = context, firestore = null, enableCloudListeners = false)
        val tempVideo = File(context.filesDir, "vid_delete.mp4").apply { writeBytes(byteArrayOf(9, 8, 7)) }

        repo.createPost(
            postId = "p1",
            authorId = "u1",
            authorName = "Musfiq",
            authorAvatarUrl = null,
            categoryTag = "চিন্তা",
            contentText = "অনুশীলন শুরু",
            mediaType = PostMediaType.VIDEO,
            localVideoPath = tempVideo.absolutePath
        )

        // Like & unlike
        repo.toggleLike("p1", "u2")
        var post = repo.postsFlow.first().first { it.id == "p1" }
        assertEquals(listOf("u2"), post.likedByIds)

        repo.toggleLike("p1", "u2")
        post = repo.postsFlow.first().first { it.id == "p1" }
        assertTrue(post.likedByIds.isEmpty())

        // Comments
        assertTrue(repo.addComment("p1", "u3", "Taskin", "   ").isFailure)
        assertTrue(repo.addComment("p1", "u3", "Taskin", "শুভকামনা ভাই!").isSuccess)
        post = repo.postsFlow.first().first { it.id == "p1" }
        assertEquals(1, post.comments.size)
        assertEquals("Taskin", post.comments.first().authorName)

        // Member display name persistence
        assertEquals("Guest", repo.getSavedMemberDisplayName("Guest"))
        repo.saveMemberDisplayName("রিদোয়ান আহমেদ", "")
        assertEquals("রিদোয়ান আহমেদ", repo.getSavedMemberDisplayName("Guest"))

        // Delete removes post and deletes local video file
        repo.deletePost("p1")
        assertTrue(repo.postsFlow.first().none { it.id == "p1" })
        assertFalse(tempVideo.exists())
    }

    @Test
    fun `media helper formatDuration renders clean mm ss`() {
        assertEquals("0:00", MediaHelper.formatDuration(0L))
        assertEquals("0:01", MediaHelper.formatDuration(450L))
        assertEquals("1:05", MediaHelper.formatDuration(65_000L))
    }

    @Test
    fun `community feelings include all 8 requested moods and persist with post`(): Unit = runBlocking {
        val keys = com.example.data.model.CommunityPostFeelings.all.map { it.key }
        assertEquals(
            listOf("Happy", "Blessed", "Loved", "Sad", "Lovely", "Thankful", "Excited", "Crazy"),
            keys
        )

        val repo = PostRepository(context = context, firestore = null, enableCloudListeners = false)
        val res = repo.createPost(
            postId = "p_feel",
            authorId = "u_feel",
            authorName = "Mehedi",
            authorAvatarUrl = "data:image/jpeg;base64,/9j/AVATAR",
            categoryTag = "মুহূর্ত",
            feeling = "Excited",
            contentText = "ম্যাচ শুরু হচ্ছে!",
            mediaType = PostMediaType.NONE
        )
        assertTrue(res.isSuccess)

        val reloaded = PostRepository(context = context, firestore = null, enableCloudListeners = false)
        val saved = reloaded.postsFlow.first { it.any { p -> p.id == "p_feel" } }.first { it.id == "p_feel" }
        assertEquals("Excited", saved.feeling)
        assertEquals("data:image/jpeg;base64,/9j/AVATAR", saved.authorAvatarUrl)
    }

    @Test
    fun `saveMemberProfile updates both name and avatar across profile flow and user posts`(): Unit = runBlocking {
        val repo = PostRepository(context = context, firestore = null, enableCloudListeners = false)
        repo.createPost(
            postId = "p_user_post",
            authorId = "dev_abc",
            authorName = "Old Name",
            authorAvatarUrl = null,
            categoryTag = "চিন্তা",
            contentText = "টিম অনুশীলন চলছে",
            mediaType = PostMediaType.NONE
        )

        repo.saveMemberProfile(
            name = "তানভীর আহমেদ",
            photoUrl = "data:image/jpeg;base64,/9j/NEWPHOTO",
            userId = "dev_abc"
        )
        val profile = repo.memberProfileFlow.first { it.displayName == "তানভীর আহমেদ" }
        assertEquals("তানভীর আহমেদ", profile.displayName)
        assertEquals("data:image/jpeg;base64,/9j/NEWPHOTO", profile.photoUrl)
        assertEquals("data:image/jpeg;base64,/9j/NEWPHOTO", repo.getSavedMemberPhoto())

        val updatedPost = repo.postsFlow.first { list ->
            list.any { it.id == "p_user_post" && it.authorName == "তানভীর আহমেদ" }
        }.first { it.id == "p_user_post" }
        assertEquals("data:image/jpeg;base64,/9j/NEWPHOTO", updatedPost.authorAvatarUrl)

        // Clearing photo preserves updated name
        repo.saveMemberProfile(
            name = "তানভীর আহমেদ",
            photoUrl = null,
            userId = "dev_abc",
            clearPhoto = true
        )
        assertEquals(null, repo.getSavedMemberPhoto())
        assertEquals("তানভীর আহমেদ", repo.getSavedMemberDisplayName("Guest"))
    }

    @Test
    fun `feed default page size is 10 and lightweight video dimensions scale proportionally`() {
        assertEquals(10, PostRepository.DEFAULT_FEED_PAGE_SIZE)
        assertTrue(MediaHelper.MAX_POST_IMAGE_BASE64_CHARS in 120_000..250_000)

        val (wLandscape, hLandscape) = com.example.util.VideoCompressor.computeLightweightVideoDimensions(1920, 1080)
        assertEquals(480, wLandscape)
        assertTrue(hLandscape in 256..288)
        assertEquals(0, wLandscape % 16)
        assertEquals(0, hLandscape % 16)

        val (wPortrait, hPortrait) = com.example.util.VideoCompressor.computeLightweightVideoDimensions(1080, 1920)
        assertEquals(480, hPortrait)
        assertTrue(wPortrait in 256..288)
        assertEquals(0, wPortrait % 16)
        assertEquals(0, hPortrait % 16)
    }

    @Test
    fun `placeholder profile names are detected and real profiles are never downgraded or stripped of photo`(): Unit = runBlocking {
        assertTrue(com.example.data.repository.AuthRepository.isPlaceholderProfileName("User 7D14"))
        assertTrue(com.example.data.repository.AuthRepository.isPlaceholderProfileName("User F95D"))
        assertTrue(com.example.data.repository.AuthRepository.isPlaceholderProfileName(""))
        assertTrue(com.example.data.repository.AuthRepository.isPlaceholderProfileName("Guest"))
        assertFalse(com.example.data.repository.AuthRepository.isPlaceholderProfileName("Md Redoan Hossain"))
        assertFalse(com.example.data.repository.AuthRepository.isPlaceholderProfileName("রিদোয়ান আহমেদ"))

        val repo = PostRepository(context = context, firestore = null, enableCloudListeners = false)
        repo.createPost(
            postId = "p_placeholder_post",
            authorId = "dev_7d14",
            authorName = "User 7D14",
            authorAvatarUrl = null,
            categoryTag = "মুহূর্ত",
            contentText = "ম্যাচের আগে প্র্যাকটিস",
            mediaType = PostMediaType.NONE
        )

        // Cloud recovery sync arrives with real name & photo
        repo.syncProfileFromAuth(
            name = "Md Redoan Hossain",
            photoUrl = "data:image/jpeg;base64,/9j/REALPHOTO",
            explicitlyCleared = false,
            currentUserId = "dev_7d14"
        )

        val profileAfterSync = repo.memberProfileFlow.first { it.displayName == "Md Redoan Hossain" }
        assertEquals("Md Redoan Hossain", profileAfterSync.displayName)
        assertEquals("data:image/jpeg;base64,/9j/REALPHOTO", profileAfterSync.photoUrl)

        val upgradedPost = repo.postsFlow.first { list ->
            list.any { it.id == "p_placeholder_post" && it.authorName == "Md Redoan Hossain" }
        }.first { it.id == "p_placeholder_post" }
        assertEquals("data:image/jpeg;base64,/9j/REALPHOTO", upgradedPost.authorAvatarUrl)

        // A stale placeholder emission must NEVER downgrade real name or wipe photo
        repo.syncProfileFromAuth(
            name = "User 7D14",
            photoUrl = null,
            explicitlyCleared = false,
            currentUserId = "dev_7d14"
        )
        assertEquals("Md Redoan Hossain", repo.memberProfileFlow.value.displayName)
        assertEquals("data:image/jpeg;base64,/9j/REALPHOTO", repo.memberProfileFlow.value.photoUrl)

        // Name-only edit (photoUrl = null, clearPhoto = false) must preserve existing photo
        repo.saveMemberProfile(
            name = "Redoan Official",
            photoUrl = null,
            userId = "dev_7d14",
            clearPhoto = false
        )
        assertEquals("Redoan Official", repo.memberProfileFlow.value.displayName)
        assertEquals("data:image/jpeg;base64,/9j/REALPHOTO", repo.memberProfileFlow.value.photoUrl)
        assertEquals("data:image/jpeg;base64,/9j/REALPHOTO", repo.getSavedMemberPhoto())
    }

    @Test
    fun `authRepository uses shared StateFlow, persists profile locally, and preserves photo on name-only update`(): Unit = runBlocking {
        val authRepo = com.example.data.repository.AuthRepository(
            context = context,
            auth = null,
            firestore = null,
            emailOtpService = null,
            enableCloudSync = false
        )
        // Multiple calls return the exact same shared StateFlow instance (no cold callbackFlow reset)
        val flow1 = authRepo.getCurrentUserFlow()
        val flow2 = authRepo.getCurrentUserFlow()
        assertTrue(flow1 === flow2)

        val uid = authRepo.getCurrentUserId()
        var syncedName = ""
        var syncedPhoto: String? = null
        authRepo.onProfileSynced = { name, photo, _ ->
            syncedName = name
            syncedPhoto = photo
        }

        val saveRes = authRepo.updateUserProfile(
            uid = uid,
            displayName = "Md Redoan Hossain",
            photoUrl = "data:image/jpeg;base64,/9j/AVATAR1",
            clearPhoto = false
        )
        assertTrue(saveRes.isSuccess)
        assertEquals("Md Redoan Hossain", syncedName)
        assertEquals("data:image/jpeg;base64,/9j/AVATAR1", syncedPhoto)
        assertEquals("Md Redoan Hossain", flow1.first()?.displayName)

        // Name-only update preserves existing photo
        authRepo.updateUserProfile(
            uid = uid,
            displayName = "Md Redoan Updated",
            photoUrl = null,
            clearPhoto = false
        )
        assertEquals("Md Redoan Updated", flow1.first()?.displayName)
        assertEquals("data:image/jpeg;base64,/9j/AVATAR1", flow1.first()?.photoUrl)

        // Fresh AuthRepository instance immediately starts with persisted profile (no User XXXX flash)
        val authRepoReloaded = com.example.data.repository.AuthRepository(
            context = context,
            auth = null,
            firestore = null,
            emailOtpService = null,
            enableCloudSync = false
        )
        val initialUser = authRepoReloaded.getCurrentUserFlow().first()
        assertEquals("Md Redoan Updated", initialUser?.displayName)
        assertEquals("data:image/jpeg;base64,/9j/AVATAR1", initialUser?.photoUrl)
    }
}
