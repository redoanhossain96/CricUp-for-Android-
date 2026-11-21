package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.Match
import com.example.data.model.MatchPlayerSnapshot
import com.example.data.model.MatchStatus
import com.example.data.model.MatchTeamSnapshot
import com.example.data.repository.MatchRepository
import com.example.util.ImageModelResolver
import com.example.util.ImagePickerHelper
import com.example.util.JsonHelper
import com.example.util.MediaHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImageQualityAndPerformanceTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var matchRepository: MatchRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        matchRepository = MatchRepository(
            matchDao = db.matchDao(),
            ballEventDao = db.ballEventDao(),
            firestore = null,
            enableCloudListeners = false
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `image helpers preserve crisp normal resolution settings and compression steps`() {
        assertTrue(ImagePickerHelper.DEFAULT_AVATAR_OR_LOGO_MAX_DIM >= 440)
        assertTrue(ImagePickerHelper.DEFAULT_AVATAR_OR_LOGO_QUALITY >= 80)

        assertTrue(MediaHelper.MAX_AVATAR_BASE64_CHARS >= 95_000)
        assertEquals(480 to 84, MediaHelper.AVATAR_COMPRESSION_STEPS.first())

        assertTrue(MediaHelper.MAX_POST_IMAGE_BASE64_CHARS >= 210_000)
        assertEquals(1080 to 82, MediaHelper.POST_IMAGE_COMPRESSION_STEPS.first())
    }

    @Test
    fun `calculateInSampleSize never downsamples below target dimension`() {
        assertEquals(1, ImagePickerHelper.calculateInSampleSize(440, 440, 440))
        assertEquals(2, ImagePickerHelper.calculateInSampleSize(1200, 1200, 440))
        assertEquals(4, ImagePickerHelper.calculateInSampleSize(2400, 1800, 440))
    }

    @Test
    fun `encodeBitmapToDataUri scales large bitmaps and outputs valid jpeg data uri`() {
        val largeTransparentLogo = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.argb(128, 16, 185, 129))
        }
        val encoded = ImagePickerHelper.encodeBitmapToDataUri(largeTransparentLogo, maxDimension = 440, quality = 82)
        largeTransparentLogo.recycle()

        assertTrue(encoded.startsWith("data:image/jpeg;base64,"))
        val resolved = ImageModelResolver.resolve(encoded)
        assertTrue(resolved is ByteArray && resolved.isNotEmpty())
    }

    @Test
    fun `image model resolver safely handles valid urls and invalid base64 without throwing`() {
        assertNull(ImageModelResolver.resolve(null))
        assertNull(ImageModelResolver.resolve("   "))
        assertEquals("https://example.com/logo.png", ImageModelResolver.resolve("https://example.com/logo.png"))
        assertNull(ImageModelResolver.resolve("data:image/jpeg;base64,!!!"))
        assertNull(ImageModelResolver.resolve("data:image/jpeg;base64,A=B=C"))
    }

    @Test
    fun `firestore match serializer preserves team logos and prevents exceeding 1MB limit`() {
        val fakeLargePhoto = "data:image/jpeg;base64," + "A".repeat(45_000)
        val fakeTeamLogo = "data:image/jpeg;base64," + "B".repeat(25_000)

        val squadA = (1..15).map { idx ->
            MatchPlayerSnapshot(id = "a$idx", name = "Player A$idx", photoUrl = fakeLargePhoto)
        }
        val squadB = (1..15).map { idx ->
            MatchPlayerSnapshot(id = "b$idx", name = "Player B$idx", photoUrl = fakeLargePhoto)
        }

        val heavyMatch = Match(
            id = "HEAVY01",
            matchCode = "HEAVY01",
            code = "HEAVY01",
            title = "Mega Cup Final",
            teamA = MatchTeamSnapshot(id = "tA", name = "Alpha", shortName = "ALP", logoUrl = fakeTeamLogo),
            teamB = MatchTeamSnapshot(id = "tB", name = "Beta", shortName = "BET", logoUrl = fakeTeamLogo),
            playingXI_A = squadA.take(11),
            playingXI_B = squadB.take(11),
            fullSquad_A = squadA,
            fullSquad_B = squadB,
            status = MatchStatus.LIVE,
            isPublic = true,
            ownerId = "u1",
            ownerUid = "u1"
        )

        val doc = with(matchRepository) { heavyMatch.toFirestoreDocument() }
        val matchJson = doc["matchJson"] as? String
        assertNotNull(matchJson)
        assertTrue("Serialized matchJson should stay safely under Firestore 1 MiB limit", matchJson!!.length < 950_000)

        val parsed = JsonHelper.fromJson<Match>(matchJson)
        assertNotNull(parsed)
        assertEquals(fakeTeamLogo, parsed!!.teamA.logoUrl)
        assertEquals(fakeTeamLogo, parsed.teamB.logoUrl)
    }

    @Test
    fun `compressUriToBase64 and MediaHelper helpers successfully decode and compress real JPEG and PNG URIs`(): Unit = kotlinx.coroutines.runBlocking {
        val jpegFile = java.io.File(context.cacheDir, "test_photo_sample.jpg")
        val pngFile = java.io.File(context.cacheDir, "test_logo_sample.png")
        try {
            val photoBmp = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.rgb(22, 163, 74))
            }
            java.io.FileOutputStream(jpegFile).use { out ->
                photoBmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            photoBmp.recycle()

            val logoBmp = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.argb(180, 245, 158, 11))
            }
            java.io.FileOutputStream(pngFile).use { out ->
                logoBmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            logoBmp.recycle()

            val jpegUri = android.net.Uri.fromFile(jpegFile)
            val pngUri = android.net.Uri.fromFile(pngFile)

            // Regression check: inJustDecodeBounds=true must NEVER cause compressUriToBase64 to return null
            val directEncoded = ImagePickerHelper.compressUriToBase64(context, jpegUri)
            assertNotNull("compressUriToBase64 must succeed for valid JPEG URI", directEncoded)
            assertTrue(directEncoded!!.startsWith("data:image/jpeg;base64,"))
            assertNotNull(ImageModelResolver.resolve(directEncoded))

            val pngLogoEncoded = ImagePickerHelper.compressUriToBase64Async(context, pngUri)
            assertNotNull("compressUriToBase64Async must succeed for valid PNG URI", pngLogoEncoded)
            assertTrue(pngLogoEncoded!!.startsWith("data:image/jpeg;base64,"))

            val postImgEncoded = MediaHelper.compressPostImageToBase64(context, jpegUri)
            assertNotNull("compressPostImageToBase64 must succeed for post photos", postImgEncoded)
            assertTrue(postImgEncoded!!.length <= MediaHelper.MAX_POST_IMAGE_BASE64_CHARS)

            val avatarEncoded = MediaHelper.compressAvatarToBase64(context, pngUri)
            assertNotNull("compressAvatarToBase64 must succeed for avatars/team logos", avatarEncoded)
            assertTrue(avatarEncoded!!.length <= MediaHelper.MAX_AVATAR_BASE64_CHARS)
        } finally {
            jpegFile.delete()
            pngFile.delete()
        }
    }

    @Test
    fun `preparePostVideo copies URI to app storage cleanly and produces inline or chunked payload`(): Unit = kotlinx.coroutines.runBlocking {
        val sampleVideoFile = java.io.File(context.cacheDir, "sample_tiny_clip.mp4")
        val postId = "vid_test_${System.currentTimeMillis()}"
        try {
            // 120 KB payload under LIGHTWEIGHT_VIDEO_SKIP_COMPRESS_BYTES -> fast path without hardware MediaCodec
            sampleVideoFile.writeBytes(ByteArray(120 * 1024) { (it % 127).toByte() })
            val result = MediaHelper.preparePostVideo(context, android.net.Uri.fromFile(sampleVideoFile), postId)
            assertTrue("preparePostVideo should succeed for readable video URI", result.isSuccess)
            val prepared = result.getOrThrow()
            assertTrue(java.io.File(prepared.localFilePath).exists())
            assertNotNull(prepared.inlineVideoBase64)
            java.io.File(prepared.localFilePath).delete()
        } finally {
            sampleVideoFile.delete()
        }
    }

    @Test
    fun `isOfflineOnlyMatch accurately distinguishes offline matches from public online matches`() {
        val onlinePublic = Match(
            id = "ABC123",
            matchCode = "ABC123",
            isPublic = true,
            isOfflineScored = false,
            status = MatchStatus.LIVE
        )
        assertTrue(!onlinePublic.isOfflineOnlyMatch())

        val offlineByFlag = onlinePublic.copy(isOfflineScored = true)
        assertTrue(offlineByFlag.isOfflineOnlyMatch())

        val offlineByCode = onlinePublic.copy(matchCode = "OFFLINE")
        assertTrue(offlineByCode.isOfflineOnlyMatch())

        val offlineByIdPrefix = onlinePublic.copy(id = "OFFLINE_99A1B2C3")
        assertTrue(offlineByIdPrefix.isOfflineOnlyMatch())

        val privateMatch = onlinePublic.copy(isPublic = false)
        assertTrue(privateMatch.isOfflineOnlyMatch())
    }

    @Test
    fun `resolveAspectRatio preserves exact portrait landscape and square proportions for feed images`(): Unit = kotlinx.coroutines.runBlocking {
        val portraitBmp = Bitmap.createBitmap(450, 800, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(16, 185, 129))
        }
        val landscapeBmp = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(245, 158, 11))
        }
        val squareBmp = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(34, 197, 94))
        }

        val portraitUri = ImagePickerHelper.encodeBitmapToDataUri(portraitBmp, maxDimension = 1080, quality = 80)
        val landscapeUri = ImagePickerHelper.encodeBitmapToDataUri(landscapeBmp, maxDimension = 1080, quality = 80)
        val squareUri = ImagePickerHelper.encodeBitmapToDataUri(squareBmp, maxDimension = 1080, quality = 80)
        portraitBmp.recycle()
        landscapeBmp.recycle()
        squareBmp.recycle()

        val portraitRatio = ImageModelResolver.resolveAspectRatio(portraitUri)
        val landscapeRatio = ImageModelResolver.resolveAspectRatio(landscapeUri)
        val squareRatio = ImageModelResolver.resolveAspectRatio(squareUri)

        assertNotNull(portraitRatio)
        assertNotNull(landscapeRatio)
        assertNotNull(squareRatio)
        assertEquals(450f / 800f, portraitRatio!!, 0.02f)
        assertEquals(960f / 540f, landscapeRatio!!, 0.02f)
        assertEquals(1.0f, squareRatio!!, 0.01f)
    }

    @Test
    fun `selectCenteredAutoplayVideoPostId picks most centered visible video post and returns null when none visible`() {
        val videoIds = setOf("v1", "v2")
        val visibleItems = listOf(
            Triple("community_post_img1", -100, 500),
            Triple("community_post_v1", 50, 600),   // center = 350, dist from 500 = 150
            Triple("community_post_v2", 660, 600)   // center = 960, visiblePx = 340, dist = 460
        )

        val picked = com.example.ui.screens.home.selectCenteredAutoplayVideoPostId(
            visibleItems = visibleItems,
            videoPostIds = videoIds,
            viewportStart = 0,
            viewportEnd = 1000
        )
        assertEquals("v1", picked)

        // When v1 scrolls mostly out of view, v2 wins
        val scrolledItems = listOf(
            Triple("community_post_v1", -520, 600), // visiblePx = 80 (< 35% and < 220px)
            Triple("community_post_v2", 120, 600)   // center = 420
        )
        val pickedAfterScroll = com.example.ui.screens.home.selectCenteredAutoplayVideoPostId(
            visibleItems = scrolledItems,
            videoPostIds = videoIds,
            viewportStart = 0,
            viewportEnd = 1000
        )
        assertEquals("v2", pickedAfterScroll)

        // When no video posts exist in visible items
        assertNull(
            com.example.ui.screens.home.selectCenteredAutoplayVideoPostId(
                visibleItems = listOf(Triple("community_post_img1", 100, 400)),
                videoPostIds = videoIds,
                viewportStart = 0,
                viewportEnd = 1000
            )
        )
    }
}
