package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.Match
import com.example.data.model.Player
import com.example.data.model.PlayerRoles
import com.example.data.model.PostMediaType
import com.example.data.model.Team
import com.example.data.model.Tournament
import com.example.data.repository.AuthRepository
import com.example.data.repository.MatchRepository
import com.example.data.repository.PlayerRepository
import com.example.data.repository.PostRepository
import com.example.data.repository.TeamRepository
import com.example.data.repository.TournamentRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoogleProfileLinkingTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "community_posts_cache.json").delete()
        context.getSharedPreferences("cricup_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("cricup_member_profile", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
        File(context.filesDir, "community_posts_cache.json").delete()
    }

    @Test
    fun `linkAndSyncGoogleUser adopts device profile cricket details teams and followers`(): Unit = runBlocking {
        val authRepo = AuthRepository(
            context = context,
            auth = null,
            firestore = null,
            emailOtpService = null,
            enableCloudSync = false
        )
        val deviceUid = authRepo.getCurrentUserId()
        assertTrue(deviceUid.startsWith("dev_"))
        assertFalse(authRepo.isGoogleSignedIn())

        authRepo.updateUserCricketProfile(
            uid = deviceUid,
            displayName = "Redoan Captain",
            photoUrl = "data:image/jpeg;base64,DEVPHOTO",
            primaryRole = PlayerRoles.ALL_ROUNDER,
            battingStyle = "Left Hand",
            bowlingStyle = "Left-arm Orthodox",
            bio = "Club Captain & Scorer"
        ).getOrThrow()
        authRepo.updateUserJoinedTeams("team_dhaka_01", join = true)
        authRepo.seedDirectoryUsers(
            listOf(
                com.example.data.model.User(
                    uid = "friend_sakib",
                    displayName = "Sakib"
                )
            )
        )
        authRepo.toggleFollowUser("friend_sakib")

        val linked = authRepo.linkAndSyncGoogleUser(
            googleUid = "google_uid_abc",
            email = "RedoAn.Club@gmail.com",
            googleDisplayName = "Redoan Google",
            googlePhotoUrl = "https://lh3.googleusercontent.com/photo"
        )

        assertEquals("google_uid_abc", linked.uid)
        assertEquals("redoan.club@gmail.com", linked.email)
        assertEquals("Redoan Captain", linked.displayName)
        assertEquals("data:image/jpeg;base64,DEVPHOTO", linked.photoUrl)
        assertEquals("Left Hand", linked.battingStyle)
        assertEquals("Left-arm Orthodox", linked.bowlingStyle)
        assertEquals("Club Captain & Scorer", linked.bio)
        assertTrue(linked.joinedTeamIds.contains("team_dhaka_01"))
        assertTrue(linked.followingIds.contains("friend_sakib"))
        assertTrue(linked.linkedDeviceIds.contains(deviceUid))
        assertTrue(linked.isGoogleSignedIn)
        assertTrue(authRepo.isGoogleSignedIn())
        assertEquals("google_uid_abc", authRepo.getCurrentUserId())

        // Persisted across repository re-initialization
        val reloaded = AuthRepository(
            context = context,
            auth = null,
            firestore = null,
            emailOtpService = null,
            enableCloudSync = false
        )
        assertTrue(reloaded.isGoogleSignedIn())
        assertEquals("google_uid_abc", reloaded.getCurrentUserId())
        assertEquals("redoan.club@gmail.com", reloaded.getCurrentUserFlow().first()?.email)
    }

    @Test
    fun `switching directly from first Gmail to second Gmail isolates private profile data`(): Unit = runBlocking {
        val authRepo = AuthRepository(
            context = context,
            auth = null,
            firestore = null,
            emailOtpService = null,
            enableCloudSync = false
        )
        val deviceUid = authRepo.getCurrentUserId()

        authRepo.updateUserCricketProfile(
            uid = deviceUid,
            displayName = "Player One",
            photoUrl = null,
            primaryRole = PlayerRoles.BATSMAN,
            battingStyle = "Right Hand",
            bowlingStyle = "None",
            bio = "First device bio"
        )
        authRepo.updateUserJoinedTeams("team_alpha", join = true)

        val gmailOne = authRepo.linkAndSyncGoogleUser(
            googleUid = "gmail_uid_1",
            email = "first@gmail.com",
            googleDisplayName = "First Gmail",
            googlePhotoUrl = null
        )
        assertTrue(gmailOne.joinedTeamIds.contains("team_alpha"))
        assertEquals("Player One", gmailOne.displayName)

        // Direct switch to a second Gmail without signing out first
        val gmailTwo = authRepo.linkAndSyncGoogleUser(
            googleUid = "gmail_uid_2",
            email = "second@gmail.com",
            googleDisplayName = "Second Gmail",
            googlePhotoUrl = "https://example.com/two.jpg"
        )
        assertEquals("gmail_uid_2", gmailTwo.uid)
        assertEquals("second@gmail.com", gmailTwo.email)
        assertEquals("Second Gmail", gmailTwo.displayName)
        assertFalse(gmailTwo.joinedTeamIds.contains("team_alpha"))
        assertTrue(gmailTwo.followingIds.isEmpty())
    }

    @Test
    fun `signOut clears Google session reverts uid to device id and restores device name`(): Unit = runBlocking {
        val authRepo = AuthRepository(
            context = context,
            auth = null,
            firestore = null,
            emailOtpService = null,
            enableCloudSync = false
        )
        val deviceUid = authRepo.getCurrentUserId()
        authRepo.updateUserCricketProfile(
            uid = deviceUid,
            displayName = "Local Scorer Name",
            photoUrl = "data:image/jpeg;base64,LOCAL",
            primaryRole = PlayerRoles.BOWLER,
            battingStyle = "Right Hand",
            bowlingStyle = "Right-arm Fast",
            bio = ""
        )

        authRepo.linkAndSyncGoogleUser(
            googleUid = "gmail_uid_signout",
            email = "tester@gmail.com",
            googleDisplayName = "Tester Gmail",
            googlePhotoUrl = "https://example.com/g.jpg"
        )
        assertEquals("gmail_uid_signout", authRepo.getCurrentUserId())
        assertTrue(authRepo.isGoogleSignedIn())

        authRepo.signOut()

        assertFalse(authRepo.isGoogleSignedIn())
        assertEquals(deviceUid, authRepo.getCurrentUserId())
        val restored = authRepo.getCurrentUserFlow().value
        assertEquals(deviceUid, restored?.uid)
        assertEquals("", restored?.email)
        assertEquals("Local Scorer Name", restored?.displayName)
        assertEquals("data:image/jpeg;base64,LOCAL", restored?.photoUrl)
    }

    @Test
    fun `ownership migration transfers matches teams players tournaments and posts to Gmail UID`(): Unit = runBlocking {
        val matchRepo = MatchRepository(
            matchDao = db.matchDao(),
            ballEventDao = db.ballEventDao(),
            firestore = null,
            enableCloudListeners = false
        )
        val teamRepo = TeamRepository(teamDao = db.teamDao(), firestore = null, enableCloudSync = false)
        val playerRepo = PlayerRepository(playerDao = db.playerDao(), firestore = null, enableCloudSync = false)
        val tournamentRepo = TournamentRepository(tournamentDao = db.tournamentDao(), firestore = null, enableCloudSync = false)
        val postRepo = PostRepository(context = context, firestore = null, enableCloudListeners = false)

        val devUid = "dev_old_999"
        val gmailUid = "google_uid_new_123"

        val match = Match(
            id = "match_mig_1",
            matchCode = "CR9M2K",
            ownerId = devUid,
            ownerUid = devUid,
            ownerName = "User 0999",
            teamA = com.example.data.model.MatchTeamSnapshot(id = "team_mig_1", name = "Dhaka Warriors", shortName = "DWR"),
            teamB = com.example.data.model.MatchTeamSnapshot(id = "team_mig_2", name = "Chattogram Challengers", shortName = "CHC"),
            isPublic = false,
            isOfflineScored = true
        )
        matchRepo.updateMatch(match).getOrThrow()

        val team = Team(
            id = "team_mig_1",
            name = "Dhaka Warriors",
            shortName = "DWR",
            ownerId = devUid,
            playerIds = listOf(devUid, "p_other")
        )
        teamRepo.saveTeam(team).getOrThrow()

        val player = Player(
            id = "player_mig_1",
            fullName = "Shakib Test",
            creatorId = devUid
        )
        playerRepo.savePlayer(player).getOrThrow()

        val tournament = Tournament(
            id = "tour_mig_1",
            name = "Super Cup",
            creatorId = devUid
        )
        tournamentRepo.saveTournament(tournament).getOrThrow()

        postRepo.createPost(
            postId = "post_mig_1",
            authorId = devUid,
            authorName = "Local User",
            authorAvatarUrl = null,
            feeling = "",
            contentText = "দারুণ ম্যাচ জয়!",
            mediaType = PostMediaType.NONE
        ).getOrThrow()

        val migratedMatches = matchRepo.migrateOwnerUid(devUid, gmailUid, "Redoan Hossain")
        val migratedTeams = teamRepo.migrateOwnerUid(devUid, gmailUid)
        val migratedPlayers = playerRepo.migrateCreatorUid(devUid, gmailUid)
        val migratedTournaments = tournamentRepo.migrateCreatorUid(devUid, gmailUid)
        val migratedPosts = postRepo.migrateAuthorId(devUid, gmailUid, "Redoan Hossain", "https://example.com/p.jpg")

        assertEquals(1, migratedMatches)
        assertEquals(1, migratedTeams)
        assertEquals(1, migratedPlayers)
        assertEquals(1, migratedTournaments)
        assertEquals(1, migratedPosts)

        val updatedMatch = matchRepo.getMatchById("match_mig_1")!!
        assertEquals(gmailUid, updatedMatch.effectiveOwnerUid)
        assertTrue(updatedMatch.canUserScore(gmailUid))
        assertEquals("Redoan Hossain", updatedMatch.ownerName)

        val updatedTeam = teamRepo.getTeamById("team_mig_1")!!
        assertEquals(gmailUid, updatedTeam.ownerId)
        assertTrue(updatedTeam.playerIds.contains(gmailUid))

        val updatedPlayer = playerRepo.getPlayerById("player_mig_1")!!
        assertEquals(gmailUid, updatedPlayer.creatorId)

        val updatedTour = tournamentRepo.getTournamentById("tour_mig_1")!!
        assertEquals(gmailUid, updatedTour.creatorId)

        val updatedPost = postRepo.postsFlow.first().first { it.id == "post_mig_1" }
        assertEquals(gmailUid, updatedPost.authorId)
        assertEquals("Redoan Hossain", updatedPost.authorName)
        assertEquals("https://example.com/p.jpg", updatedPost.authorAvatarUrl)
    }

    @Test
    fun `unregistered user stays in viewer mode until Gmail login and reverts on signOut`(): Unit = runBlocking {
        val authRepo = AuthRepository(
            context = context,
            auth = null,
            firestore = null,
            emailOtpService = null,
            enableCloudSync = false
        )

        // Fresh user without Gmail is strictly in viewer (guest) mode
        assertFalse(authRepo.isGoogleSignedIn())
        assertFalse(authRepo.isRegisteredUser())
        assertTrue(authRepo.isGuestMode.value)

        // Pressing START or loginAsGuest without Gmail does NOT unlock interactive write features
        authRepo.initializeFullAccessSession()
        assertTrue(authRepo.isGuestMode.value)
        authRepo.loginAsGuest()
        assertTrue(authRepo.isGuestMode.value)

        // Linking Gmail unlocks registered user features
        authRepo.linkAndSyncGoogleUser(
            googleUid = "gmail_verified_uid",
            email = "player@gmail.com",
            googleDisplayName = "Verified Player",
            googlePhotoUrl = null
        )
        assertTrue(authRepo.isGoogleSignedIn())
        assertTrue(authRepo.isRegisteredUser())
        assertFalse(authRepo.isGuestMode.value)

        // Signing out reverts immediately back to viewer mode
        authRepo.signOut()
        assertFalse(authRepo.isGoogleSignedIn())
        assertFalse(authRepo.isRegisteredUser())
        assertTrue(authRepo.isGuestMode.value)
    }

    @Test
    fun `google sign in helper generates unique nonces and non-silent cancellation diagnostics`(): Unit = runBlocking {
        val n1 = com.example.util.GoogleSignInHelper.generateSecureNonce()
        val n2 = com.example.util.GoogleSignInHelper.generateSecureNonce()
        assertTrue(n1.length >= 20)
        assertTrue(n1 != n2)
        assertFalse(n1.contains("="))

        val slowCancelMsg = com.example.util.GoogleSignInHelper.buildCancellationGuidance(
            errorMessage = "activity is cancelled by the user.",
            elapsedMs = 3100L
        )
        assertTrue(slowCancelMsg.contains("SHA-1"))
        assertTrue(slowCancelMsg.contains("ব্রাউজার"))

        val quickCancelMsg = com.example.util.GoogleSignInHelper.buildCancellationGuidance(
            errorMessage = null,
            elapsedMs = 200L
        )
        assertTrue(quickCancelMsg.contains("ব্রাউজার"))
    }

    @Test
    fun `auth repository maps developer and network auth errors clearly and safely checks pending result`(): Unit = runBlocking {
        val authRepo = AuthRepository(
            context = context,
            auth = null,
            firestore = null,
            emailOtpService = null,
            enableCloudSync = false
        )
        val pending = authRepo.consumePendingFirebaseAuthResult()
        assertTrue(pending.isSuccess)
        assertNull(pending.getOrNull())

        val devErrMsg = authRepo.mapAuthErrorMessage(RuntimeException("10: DEVELOPER_ERROR"))
        assertTrue(devErrMsg.contains("SHA-1"))
        assertTrue(devErrMsg.contains("ব্রাউজার"))

        val netErrMsg = authRepo.mapAuthErrorMessage(RuntimeException("network timeout occurred"))
        assertTrue(netErrMsg.contains("ইন্টারনেট"))
    }
}
