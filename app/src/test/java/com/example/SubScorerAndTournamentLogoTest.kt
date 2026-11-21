package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.TournamentEntity
import com.example.data.model.ExtraType
import com.example.data.model.InningsScorecard
import com.example.data.model.Match
import com.example.data.model.MatchPlayerSnapshot
import com.example.data.model.MatchStatus
import com.example.data.model.MatchTeamSnapshot
import com.example.data.model.SubScorerInfo
import com.example.data.model.Tournament
import com.example.data.model.User
import com.example.data.repository.AuthRepository
import com.example.data.repository.MatchRepository
import com.example.data.repository.ScoringRepository
import com.example.util.JsonHelper
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubScorerAndTournamentLogoTest {

    private lateinit var db: AppDatabase
    private lateinit var matchRepository: MatchRepository
    private lateinit var scoringRepository: ScoringRepository

    private val teamA = MatchTeamSnapshot(id = "t_a", name = "Comilla Victorians", shortName = "CV")
    private val teamB = MatchTeamSnapshot(id = "t_b", name = "Dhaka Capitals", shortName = "DC")

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("cricup_searched_scorer_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("cricup_member_profile", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        matchRepository = MatchRepository(
            matchDao = db.matchDao(),
            ballEventDao = db.ballEventDao(),
            firestore = null,
            enableCloudListeners = false
        )
        scoringRepository = ScoringRepository(
            ballEventDao = db.ballEventDao(),
            matchDao = db.matchDao(),
            matchRepository = matchRepository,
            firestore = null,
            enableCloudListeners = false
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun loadMatch(id: String): Match {
        return JsonHelper.fromJson<Match>(db.matchDao().getMatchById(id)!!.jsonPayload)!!
    }

    @Test
    fun headScorer_canAddMultipleSubScorers_andAllAuthorizedScorersCanRecordAndUndoBalls() = runBlocking {
        val headUid = "head_scorer_1"
        val sub1Uid = "sub_scorer_1"
        val sub2Uid = "sub_scorer_2"
        val strangerUid = "stranger_99"

        val match = Match(
            id = "SUBTEST1",
            matchCode = "SUBTEST1",
            code = "SUBTEST1",
            title = "Comilla vs Dhaka",
            ownerId = headUid,
            ownerUid = headUid,
            ownerName = "Head Scorer",
            status = MatchStatus.LIVE,
            isPublic = true,
            isOfflineScored = false,
            totalOvers = 6,
            teamA = teamA,
            teamB = teamB,
            battingFirstTeamId = teamA.id,
            bowlingFirstTeamId = teamB.id,
            playingXI_A = listOf(
                MatchPlayerSnapshot(id = "a1", name = "Liton"),
                MatchPlayerSnapshot(id = "a2", name = "Imrul")
            ),
            playingXI_B = listOf(
                MatchPlayerSnapshot(id = "b1", name = "Mustafiz"),
                MatchPlayerSnapshot(id = "b2", name = "Taskin")
            ),
            currentInningsNumber = 1,
            currentStrikerId = "a1",
            currentStrikerName = "Liton",
            currentNonStrikerId = "a2",
            currentNonStrikerName = "Imrul",
            currentBowlerId = "b1",
            currentBowlerName = "Mustafiz",
            firstInnings = InningsScorecard(
                inningsNumber = 1,
                battingTeamId = "t_a",
                battingTeamName = "Comilla Victorians",
                bowlingTeamId = "t_b",
                bowlingTeamName = "Dhaka Capitals"
            )
        )
        matchRepository.commitLocalMatch(match, allowProgressRollback = true)

        // Initially only Head Scorer can score
        val initial = loadMatch("SUBTEST1")
        assertTrue(initial.isHeadScorer(headUid))
        assertFalse(initial.canUserScore(sub1Uid))
        assertFalse(initial.canUserScore(sub2Uid))

        // Head Scorer adds multiple sub scorers (duplicate & headUid must be deduplicated/excluded)
        val updateResult = matchRepository.updateSubScorers(
            matchId = "SUBTEST1",
            subScorers = listOf(
                SubScorerInfo(uid = headUid, name = "Head Scorer"),
                SubScorerInfo(uid = sub1Uid, name = "Sub Scorer 1"),
                SubScorerInfo(uid = sub2Uid, name = "Sub Scorer 2"),
                SubScorerInfo(uid = sub1Uid, name = "Sub Scorer 1 Dup")
            )
        )
        assertTrue(updateResult.isSuccess)

        val afterAdd = loadMatch("SUBTEST1")
        assertEquals(listOf(sub1Uid, sub2Uid), afterAdd.authorizedScorers)
        assertEquals(2, afterAdd.subScorerProfiles.size)
        assertTrue(afterAdd.isHeadScorer(headUid))
        assertFalse(afterAdd.isHeadScorer(sub1Uid))
        assertTrue(afterAdd.canUserScore(headUid))
        assertTrue(afterAdd.canUserScore(sub1Uid))
        assertTrue(afterAdd.canUserScore(sub2Uid))
        assertFalse(afterAdd.canUserScore(strangerUid))

        // Stranger is still blocked from recording balls
        val strangerAttempt = scoringRepository.recordBallEvent(
            match = afterAdd,
            runsOffBat = 4,
            extraType = ExtraType.NONE,
            extraRuns = 0,
            isWicket = false,
            dismissalType = null,
            dismissedPlayerId = null,
            dismissedPlayerName = null,
            incomingBatterId = null,
            incomingBatterName = null,
            assistingPlayerName = null,
            scorerId = strangerUid
        )
        assertTrue("Unauthorized spectator must fail to score", strangerAttempt.isFailure)

        // Sub Scorer 1 records first ball (4 runs)
        val sub1Attempt = scoringRepository.recordBallEvent(
            match = afterAdd,
            runsOffBat = 4,
            extraType = ExtraType.NONE,
            extraRuns = 0,
            isWicket = false,
            dismissalType = null,
            dismissedPlayerId = null,
            dismissedPlayerName = null,
            incomingBatterId = null,
            incomingBatterName = null,
            assistingPlayerName = null,
            scorerId = sub1Uid
        )
        assertTrue("Sub Scorer 1 must be able to score: ${sub1Attempt.exceptionOrNull()}", sub1Attempt.isSuccess)
        assertEquals(4, sub1Attempt.getOrThrow().firstInnings?.totalRuns)

        // Sub Scorer 2 records second ball (6 runs) on the updated match — must retain Sub Scorers list!
        val afterSub1Match = loadMatch("SUBTEST1")
        assertEquals(listOf(sub1Uid, sub2Uid), afterSub1Match.authorizedScorers)

        val sub2Attempt = scoringRepository.recordBallEvent(
            match = afterSub1Match,
            runsOffBat = 6,
            extraType = ExtraType.NONE,
            extraRuns = 0,
            isWicket = false,
            dismissalType = null,
            dismissedPlayerId = null,
            dismissedPlayerName = null,
            incomingBatterId = null,
            incomingBatterName = null,
            assistingPlayerName = null,
            scorerId = sub2Uid
        )
        assertTrue("Sub Scorer 2 must be able to score: ${sub2Attempt.exceptionOrNull()}", sub2Attempt.isSuccess)
        assertEquals(10, sub2Attempt.getOrThrow().firstInnings?.totalRuns)

        // Sub Scorer 1 can undo the last ball
        val undoResult = scoringRepository.undoLastBall(loadMatch("SUBTEST1"), scorerId = sub1Uid)
        assertTrue("Authorized sub scorer can undo: ${undoResult.exceptionOrNull()}", undoResult.isSuccess)
        assertEquals(4, undoResult.getOrThrow().firstInnings?.totalRuns)

        // Removing Sub Scorer 2 revokes Sub Scorer 2's permission while keeping Sub Scorer 1
        matchRepository.updateSubScorers("SUBTEST1", listOf(SubScorerInfo(uid = sub1Uid, name = "Sub Scorer 1"))).getOrThrow()
        val afterRevoke = loadMatch("SUBTEST1")
        assertTrue(afterRevoke.canUserScore(sub1Uid))
        assertFalse(afterRevoke.canUserScore(sub2Uid))
    }

    @Test
    fun tournamentLogo_persistsAcrossSaveUpdateAndRemoval() = runBlocking {
        val sampleLogo = "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/"
        val created = Tournament(
            id = "tour_logo_1",
            name = "CricUp Gold Cup",
            venue = "Mirpur",
            matchOvers = 15,
            logoUrl = sampleLogo,
            creatorId = "head_scorer_1"
        )

        db.tournamentDao().insertTournament(
            TournamentEntity(
                id = created.id,
                name = created.name,
                jsonPayload = JsonHelper.toJson(created),
                updatedAt = created.createdAt
            )
        )

        val storedList = db.tournamentDao().getAllTournamentsFlow().first()
        assertEquals(1, storedList.size)
        val restored = JsonHelper.fromJson<Tournament>(storedList.first().jsonPayload)
        assertEquals(sampleLogo, restored?.logoUrl)
        assertEquals("CricUp Gold Cup", restored?.name)

        // Update tournament logo to null (removed)
        val updated = created.copy(logoUrl = null)
        db.tournamentDao().insertTournament(
            TournamentEntity(
                id = updated.id,
                name = updated.name,
                jsonPayload = JsonHelper.toJson(updated),
                updatedAt = System.currentTimeMillis()
            )
        )
        val afterRemove = JsonHelper.fromJson<Tournament>(
            db.tournamentDao().getAllTournamentsFlow().first().first().jsonPayload
        )
        assertNull(afterRemove?.logoUrl)
    }

    @Test
    fun subScorerSuggestions_doNotAutoShowOtherUsers_andSearchSupportsIdNameGmailAndPerUserHistory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val authRepo = AuthRepository(
            context = context,
            auth = null,
            firestore = null,
            emailOtpService = null,
            enableCloudSync = false
        )

        val ownerUid = "gmail_owner_1"
        val otherUid = "gmail_other_2"

        val u1 = User(uid = "ijElqmg3okWxj0znHQ3", displayName = "Facebook Player", email = "fb.player@gmail.com")
        val u2 = User(uid = "vFImEb4SHaenqVPVCS", displayName = "Google Cricket Member", email = "cricket.fan@gmail.com")
        val u3 = User(uid = "WvcV8BWZ3tcrWLRF1K", displayName = "Redoan Hossain", email = "redoanhossain96@gmail.com")
        val staleDevStub = User(uid = "dev_old_1234", displayName = "Redoan Hossain", provider = "google_linked")
        authRepo.seedDirectoryUsers(listOf(u1, u2, u3, staleDevStub))

        // 1. Even with directory users present, previously searched suggestion list is EMPTY initially
        assertTrue(authRepo.getPreviouslySearchedScorerUsers(ownerUid).isEmpty())
        assertTrue(authRepo.searchUsers("   ", excludeUid = ownerUid).getOrThrow().isEmpty())

        // 2. Search by Name, ID, and Gmail works accurately and ignores stale google_linked stub unless exact UID queried
        val byName = authRepo.searchUsers("Redoan", excludeUid = ownerUid).getOrThrow()
        assertEquals(listOf(u3.uid), byName.map { it.uid })

        val byId = authRepo.searchUsers("ijElqmg", excludeUid = ownerUid).getOrThrow()
        assertEquals(listOf(u1.uid), byId.map { it.uid })

        val byGmail = authRepo.searchUsers("cricket.fan@gmail.com", excludeUid = ownerUid).getOrThrow()
        assertEquals(listOf(u2.uid), byGmail.map { it.uid })

        // 3. Recording searched users puts ONLY those searched items in ownerUid's suggestion list
        authRepo.recordSearchedScorerUser(u3, ownerUid = ownerUid)
        authRepo.recordSearchedScorerUser(u1, ownerUid = ownerUid)

        val suggestionsOwner = authRepo.getPreviouslySearchedScorerUsers(ownerUid)
        assertEquals(listOf(u1.uid, u3.uid), suggestionsOwner.map { it.uid })

        // Other account still sees ZERO searched suggestions
        assertTrue(authRepo.getPreviouslySearchedScorerUsers(otherUid).isEmpty())

        // 4. Removing a suggestion removes only that user
        val afterRemove = authRepo.removeSearchedScorerUser(u1.uid, ownerUid = ownerUid)
        assertEquals(listOf(u3.uid), afterRemove.map { it.uid })
    }
}
