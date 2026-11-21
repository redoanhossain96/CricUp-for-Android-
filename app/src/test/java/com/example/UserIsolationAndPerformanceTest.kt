package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.Match
import com.example.data.model.MatchStatus
import com.example.data.model.MatchTeamSnapshot
import com.example.data.model.Player
import com.example.data.model.Team
import com.example.data.model.Tournament
import com.example.data.model.Umpire
import com.example.data.repository.AuthRepository
import com.example.data.repository.MatchRepository
import com.example.data.repository.PlayerRepository
import com.example.data.repository.TeamRepository
import com.example.data.repository.TournamentRepository
import com.example.data.repository.UmpireRepository
import com.example.util.Base64ImageModel
import com.example.util.ImageModelResolver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
class UserIsolationAndPerformanceTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var teamRepository: TeamRepository
    private lateinit var playerRepository: PlayerRepository
    private lateinit var tournamentRepository: TournamentRepository
    private lateinit var umpireRepository: UmpireRepository
    private lateinit var matchRepository: MatchRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("cricup_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("cricup_member_profile", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        teamRepository = TeamRepository(teamDao = db.teamDao(), firestore = null, enableCloudSync = false)
        playerRepository = PlayerRepository(playerDao = db.playerDao(), firestore = null, enableCloudSync = false)
        tournamentRepository = TournamentRepository(tournamentDao = db.tournamentDao(), firestore = null, enableCloudSync = false)
        umpireRepository = UmpireRepository(umpireDao = db.umpireDao(), firestore = null, enableCloudSync = false)
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
    fun `new user or guest never sees another users teams players tournaments umpires or matches`(): Unit = runBlocking {
        val oldDeviceUid = "dev_old_device_1"
        val userA = "gmail_uid_user_a"
        val userB = "gmail_uid_user_b"
        val newGuest = "dev_fresh_device_2"

        teamRepository.saveTeam(Team(id = "team_dev", name = "Old Device XI", shortName = "OLD", ownerId = oldDeviceUid))
        teamRepository.saveTeam(Team(id = "team_a", name = "Alpha Titans", shortName = "ALP", ownerId = userA))

        playerRepository.savePlayer(Player(id = "player_dev", fullName = "Device Batter", creatorId = oldDeviceUid))
        playerRepository.savePlayer(Player(id = "player_a", fullName = "User A Batter", creatorId = userA))

        tournamentRepository.saveTournament(Tournament(id = "tour_dev", name = "Old Local Cup", creatorId = oldDeviceUid))
        tournamentRepository.saveTournament(Tournament(id = "tour_a", name = "User A Premier League", creatorId = userA))

        umpireRepository.saveUmpire(Umpire(id = "ump_dev", fullName = "Old Device Umpire", creatorId = oldDeviceUid))
        umpireRepository.saveUmpire(Umpire(id = "ump_a", fullName = "User A Umpire", creatorId = userA))

        matchRepository.createMatch(
            Match(
                id = "MATCH_A",
                matchCode = "MATCH_A",
                code = "MATCH_A",
                title = "Alpha vs Beta",
                teamA = MatchTeamSnapshot(id = "team_a", name = "Alpha", shortName = "ALP"),
                teamB = MatchTeamSnapshot(id = "team_b", name = "Beta", shortName = "BET"),
                status = MatchStatus.LIVE,
                ownerId = userA,
                ownerUid = userA
            )
        )

        // User A sees only User A's records
        assertEquals(listOf("team_a"), teamRepository.getMyTeamsFlow(userA).first().map { it.id })
        assertEquals(listOf("player_a"), playerRepository.getMyPlayersFlow(userA).first().map { it.id })
        assertEquals(listOf("tour_a"), tournamentRepository.getMyTournamentsFlow(userA).first().map { it.id })
        assertEquals(listOf("ump_a"), umpireRepository.getMyUmpiresFlow(userA).first().map { it.id })
        assertEquals(listOf("MATCH_A"), matchRepository.getMyMatchesFlow(userA).first().map { it.id })

        // Brand-new Gmail user B sees ZERO records from User A or old device
        assertTrue(teamRepository.getMyTeamsFlow(userB).first().isEmpty())
        assertTrue(playerRepository.getMyPlayersFlow(userB).first().isEmpty())
        assertTrue(tournamentRepository.getMyTournamentsFlow(userB).first().isEmpty())
        assertTrue(umpireRepository.getMyUmpiresFlow(userB).first().isEmpty())
        assertTrue(matchRepository.getMyMatchesFlow(userB).first().isEmpty())

        // Brand-new device guest also sees ZERO records (no dev_/device_ cross-user leak)
        assertTrue(teamRepository.getMyTeamsFlow(newGuest).first().isEmpty())
        assertTrue(playerRepository.getMyPlayersFlow(newGuest).first().isEmpty())
        assertTrue(tournamentRepository.getMyTournamentsFlow(newGuest).first().isEmpty())
        assertTrue(umpireRepository.getMyUmpiresFlow(newGuest).first().isEmpty())
        assertTrue(matchRepository.getMyMatchesFlow(newGuest).first().isEmpty())
    }

    @Test
    fun `same-name players and umpires are isolated by creatorId without colliding`(): Unit = runBlocking {
        val userA = "gmail_uid_a"
        val userB = "gmail_uid_b"

        playerRepository.savePlayer(Player(id = "p_a", fullName = "Shakib Al Hasan", creatorId = userA, jerseyNumber = 75))
        playerRepository.savePlayer(Player(id = "p_b", fullName = "Shakib Al Hasan", creatorId = userB, jerseyNumber = 10))

        umpireRepository.saveUmpire(Umpire(id = "u_a", fullName = "Sharfuddoula", creatorId = userA))
        umpireRepository.saveUmpire(Umpire(id = "u_b", fullName = "Sharfuddoula", creatorId = userB))

        val playersA = playerRepository.getMyPlayersFlow(userA).first()
        val playersB = playerRepository.getMyPlayersFlow(userB).first()
        assertEquals(1, playersA.size)
        assertEquals(1, playersB.size)
        assertEquals(75, playersA.first().jerseyNumber)
        assertEquals(10, playersB.first().jerseyNumber)

        val umpiresA = umpireRepository.getMyUmpiresFlow(userA).first()
        val umpiresB = umpireRepository.getMyUmpiresFlow(userB).first()
        assertEquals(listOf("u_a"), umpiresA.map { it.id })
        assertEquals(listOf("u_b"), umpiresB.map { it.id })
    }

    @Test
    fun `gmail account link migrates guest data and switching gmail accounts wipes prior user local caches`(): Unit = runBlocking {
        val authRepo = AuthRepository(
            context = context,
            auth = null,
            firestore = null,
            emailOtpService = null,
            enableCloudSync = false
        )
        val guestUid = authRepo.getCurrentUserId()

        var switchedCalls = 0
        var linkedPair: Pair<String, String>? = null
        var signedOutUid: String? = null

        authRepo.onAccountSwitching = { oldUid, newUid ->
            switchedCalls++
            if (oldUid.isNotBlank() && oldUid != newUid) {
                teamRepository.clearAllTeams()
                playerRepository.clearAllPlayers()
                tournamentRepository.clearAllTournaments()
                umpireRepository.clearAllUmpires()
                matchRepository.clearUserOwnedLocalMatches(oldUid)
            }
        }
        authRepo.onAccountLinked = { oldUid, linkedUser ->
            val newUid = linkedUser.uid
            linkedPair = oldUid to newUid
            teamRepository.migrateOwnerUid(oldUid, newUid)
            playerRepository.migrateCreatorUid(oldUid, newUid)
            tournamentRepository.migrateCreatorUid(oldUid, newUid)
            umpireRepository.migrateCreatorUid(oldUid, newUid)
            matchRepository.migrateOwnerUid(oldUid, newUid)
        }
        authRepo.onSignedOutCleanup = { prevUid ->
            signedOutUid = prevUid
            runBlocking {
                teamRepository.clearAllTeams()
                playerRepository.clearAllPlayers()
                tournamentRepository.clearAllTournaments()
                umpireRepository.clearAllUmpires()
                if (prevUid.isNotBlank()) {
                    matchRepository.clearUserOwnedLocalMatches(prevUid)
                }
            }
        }

        // Guest creates local data before Gmail login
        teamRepository.saveTeam(Team(id = "t1", name = "Dhaka Stars", shortName = "DHS", ownerId = guestUid))
        playerRepository.savePlayer(Player(id = "p1", fullName = "Tamim", creatorId = guestUid))
        tournamentRepository.saveTournament(Tournament(id = "tr1", name = "Club Cup", creatorId = guestUid))
        umpireRepository.saveUmpire(Umpire(id = "u1", fullName = "Nadir Shah", creatorId = guestUid))
        matchRepository.createMatch(
            Match(
                id = "M_GUEST",
                matchCode = "M_GUEST",
                code = "M_GUEST",
                title = "Dhaka vs Chittagong",
                ownerId = guestUid,
                ownerUid = guestUid
            )
        )

        // First Gmail login adopts guest data under gmail_A
        authRepo.linkAndSyncGoogleUser(
            googleUid = "gmail_A",
            email = "user.a@gmail.com",
            googleDisplayName = "User A"
        )

        assertEquals(guestUid to "gmail_A", linkedPair)
        assertEquals(0, switchedCalls)
        assertEquals(listOf("t1"), teamRepository.getMyTeamsFlow("gmail_A").first().map { it.id })
        assertEquals(listOf("p1"), playerRepository.getMyPlayersFlow("gmail_A").first().map { it.id })
        assertEquals(listOf("tr1"), tournamentRepository.getMyTournamentsFlow("gmail_A").first().map { it.id })
        assertEquals(listOf("u1"), umpireRepository.getMyUmpiresFlow("gmail_A").first().map { it.id })
        assertEquals(listOf("M_GUEST"), matchRepository.getMyMatchesFlow("gmail_A").first().map { it.id })

        // Switching directly to a DIFFERENT Gmail account wipes User A's local data before loading User B
        authRepo.linkAndSyncGoogleUser(
            googleUid = "gmail_B",
            email = "user.b@gmail.com",
            googleDisplayName = "User B"
        )
        assertEquals(1, switchedCalls)
        assertTrue(teamRepository.getMyTeamsFlow("gmail_A").first().isEmpty())
        assertTrue(playerRepository.getMyPlayersFlow("gmail_A").first().isEmpty())
        assertTrue(tournamentRepository.getMyTournamentsFlow("gmail_A").first().isEmpty())
        assertTrue(umpireRepository.getMyUmpiresFlow("gmail_A").first().isEmpty())
        assertTrue(matchRepository.getMyMatchesFlow("gmail_A").first().isEmpty())
        assertTrue(teamRepository.getMyTeamsFlow("gmail_B").first().isEmpty())

        // Add item for User B, then sign out -> all local data cleaned
        teamRepository.saveTeam(Team(id = "t_b", name = "Sylhet Royals", shortName = "SYL", ownerId = "gmail_B"))
        val teamsB = withTimeout(2_000L) {
            teamRepository.getMyTeamsFlow("gmail_B").first { it.isNotEmpty() }
        }
        assertEquals(listOf("t_b"), teamsB.map { it.id })

        authRepo.signOut()
        assertEquals("gmail_B", signedOutUid)
        assertFalse(authRepo.isGoogleSignedIn())
        val afterSignOut = withTimeout(2_000L) {
            teamRepository.getMyTeamsFlow("gmail_B").first { it.isEmpty() }
        }
        assertTrue(afterSignOut.isEmpty())
    }

    @Test
    fun `resolveForCoil returns lazy Base64ImageModel and non-blocking File without main-thread decode`() {
        assertNull(ImageModelResolver.resolveForCoil(null))
        assertNull(ImageModelResolver.resolveForCoil("   "))

        val fakeDataUri = "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/"
        val coilModel = ImageModelResolver.resolveForCoil(fakeDataUri)
        assertTrue("Expected Base64ImageModel for async Coil decoding", coilModel is Base64ImageModel)
        assertEquals(fakeDataUri, (coilModel as Base64ImageModel).raw)

        val nonExistentPath = "/tmp/non_existent_cricup_avatar_999.jpg"
        val fileModel = ImageModelResolver.resolveForCoil(nonExistentPath)
        assertTrue("Expected File wrapper without blocking File.exists() on caller thread", fileModel is File)
        assertNotNull(fileModel)
        assertEquals("https://example.com/photo.jpg", ImageModelResolver.resolveForCoil("https://example.com/photo.jpg"))
    }
}
