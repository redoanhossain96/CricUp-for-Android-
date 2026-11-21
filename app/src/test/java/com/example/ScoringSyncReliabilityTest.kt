package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.BallEventEntity
import com.example.data.local.entity.MatchEntity
import com.example.data.model.BallEvent
import com.example.data.model.DismissalType
import com.example.data.model.ExtraType
import com.example.data.model.InningsScorecard
import com.example.data.model.Match
import com.example.data.model.MatchPlayerSnapshot
import com.example.data.model.MatchStatus
import com.example.data.model.MatchTeamSnapshot
import com.example.data.repository.MatchRepository
import com.example.data.repository.ScoringRepository
import com.example.util.JsonHelper
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScoringSyncReliabilityTest {

    private lateinit var db: AppDatabase
    private lateinit var matchRepository: MatchRepository
    private lateinit var scoringRepository: ScoringRepository

    private val teamA = MatchTeamSnapshot(id = "team_a", name = "Tigers", shortName = "TIG")
    private val teamB = MatchTeamSnapshot(id = "team_b", name = "Lions", shortName = "LIO")
    private val squadA = listOf(
        MatchPlayerSnapshot(id = "a1", name = "Tamim"),
        MatchPlayerSnapshot(id = "a2", name = "Liton"),
        MatchPlayerSnapshot(id = "a3", name = "Shakib"),
        MatchPlayerSnapshot(id = "a4", name = "Mushfiq")
    )
    private val squadB = listOf(
        MatchPlayerSnapshot(id = "b1", name = "Taskin"),
        MatchPlayerSnapshot(id = "b2", name = "Mustafiz"),
        MatchPlayerSnapshot(id = "b3", name = "Shoriful"),
        MatchPlayerSnapshot(id = "b4", name = "Miraz")
    )

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
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

    private suspend fun createLiveMatch(id: String = "TEST01", overs: Int = 10): Match {
        val initial = Match(
            id = id,
            matchCode = id,
            code = id,
            title = "Tigers vs Lions",
            totalOvers = overs,
            teamA = teamA,
            teamB = teamB,
            playingXI_A = squadA,
            playingXI_B = squadB,
            battingFirstTeamId = teamA.id,
            bowlingFirstTeamId = teamB.id,
            status = MatchStatus.LIVE,
            isPublic = true,
            isOfflineScored = false,
            ownerId = "scorer_1",
            ownerUid = "scorer_1",
            currentInningsNumber = 1,
            currentStrikerId = "a1",
            currentStrikerName = "Tamim",
            currentNonStrikerId = "a2",
            currentNonStrikerName = "Liton",
            currentBowlerId = "b1",
            currentBowlerName = "Taskin"
        )
        matchRepository.commitLocalMatch(initial, allowProgressRollback = true)
        return initial
    }

    @Test
    fun `concurrent rapid ball taps serialize cleanly and never exceed 6 legal balls in an over`() = runBlocking {
        val initial = createLiveMatch(id = "CONC01", overs = 5)

        // Fire 10 simultaneous run=1 taps without waiting in between
        val results = (1..10).map {
            async {
                scoringRepository.recordBallEvent(
                    match = initial,
                    runsOffBat = 1,
                    extraType = ExtraType.NONE,
                    extraRuns = 0,
                    isWicket = false,
                    dismissalType = null,
                    dismissedPlayerId = null,
                    dismissedPlayerName = null,
                    incomingBatterId = null,
                    incomingBatterName = null,
                    assistingPlayerName = null,
                    scorerId = "scorer_1"
                )
            }
        }.awaitAll()

        // Exactly 6 deliveries succeed for Over 1; remaining 4 are rejected cleanly because bowler is cleared at over end
        assertEquals(6, results.count { it.isSuccess })
        assertEquals(4, results.count { it.isFailure })

        val persisted = matchRepository.getMatchById("CONC01")!!
        assertEquals(6, persisted.firstInnings?.legalBalls)
        assertEquals(6, persisted.firstInnings?.totalRuns)
        assertNull("Bowler must be cleared at end of 6 legal balls", persisted.currentBowlerId)

        val events = db.ballEventDao().getBallEventsList("CONC01", 1)
        assertEquals(6, events.size)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), events.map { it.ballSeq })
    }

    @Test
    fun `stale cloud snapshot and out-of-order updateMatch never rewind overs or score`() = runBlocking {
        val m0 = createLiveMatch(id = "SYNC01", overs = 10)

        // Score Over 1 (6 balls of 2 runs = 12/0 in 1.0)
        repeat(6) {
            assertTrue(
                scoringRepository.recordBallEvent(
                    match = m0,
                    runsOffBat = 2,
                    extraType = ExtraType.NONE,
                    extraRuns = 0,
                    isWicket = false,
                    dismissalType = null,
                    dismissedPlayerId = null,
                    dismissedPlayerName = null,
                    incomingBatterId = null,
                    incomingBatterName = null,
                    assistingPlayerName = null,
                    scorerId = "scorer_1"
                ).isSuccess
            )
        }
        val atOver1 = matchRepository.getMatchById("SYNC01")!!
        assertEquals(6, atOver1.firstInnings?.legalBalls)
        assertEquals(12, atOver1.firstInnings?.totalRuns)

        // Pick new bowler for Over 2 & score 3 more balls (27/0 in 1.3)
        assertTrue(scoringRepository.selectBowler("SYNC01", "b2", "Mustafiz").isSuccess)
        val afterBowler = matchRepository.getMatchById("SYNC01")!!
        val staleOver0Copy = m0.copy(
            firstInnings = InningsScorecard(inningsNumber = 1, totalRuns = 2, legalBalls = 1),
            lastSyncTimestamp = System.currentTimeMillis() + 99_999L // Even if cloud timestamp looks newer!
        )

        repeat(3) {
            assertTrue(
                scoringRepository.recordBallEvent(
                    match = afterBowler,
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
                    scorerId = "scorer_1"
                ).isSuccess
            )
        }

        val currentBeforeAttack = matchRepository.getMatchById("SYNC01")!!
        assertEquals(9, currentBeforeAttack.firstInnings?.legalBalls)
        assertEquals(24, currentBeforeAttack.firstInnings?.totalRuns)

        // 1. Stale cloud snapshot attempt MUST be rejected
        val cloudApplied = matchRepository.upsertCloudSnapshotIfNewer(staleOver0Copy)
        assertFalse("Stale cloud snapshot MUST NOT overwrite newer local match", cloudApplied)

        // 2. Stale delayed updateMatch coroutine MUST be rejected
        matchRepository.updateMatch(atOver1, allowProgressRollback = false)

        val currentAfterAttack = matchRepository.getMatchById("SYNC01")!!
        assertEquals(9, currentAfterAttack.firstInnings?.legalBalls)
        assertEquals(24, currentAfterAttack.firstInnings?.totalRuns)

        // 3. Even if local events were still PENDING, even a higher cloud timestamp cannot overwrite local
        db.ballEventDao().insertBallEvent(
            BallEventEntity(
                id = "pending_dummy",
                matchId = "SYNC01",
                inningsNumber = 1,
                ballSeq = 99,
                syncStatus = BallEvent.SYNC_STATUS_PENDING,
                jsonPayload = JsonHelper.toJson(BallEvent(id = "pending_dummy", matchId = "SYNC01", ballSeq = 99)),
                timestamp = System.currentTimeMillis()
            )
        )
        val higherFakeCloud = currentAfterAttack.copy(
            firstInnings = currentAfterAttack.firstInnings?.copy(totalRuns = 99),
            lastSyncTimestamp = System.currentTimeMillis() + 500_000L
        )
        assertFalse(matchRepository.upsertCloudSnapshotIfNewer(higherFakeCloud))
    }

    @Test
    fun `undoLastBall restores exact pre-ball crease and clears dismissed player`() = runBlocking {
        val m0 = createLiveMatch(id = "UNDO01", overs = 5)

        // Ball 1: Striker Tamim (a1) scores 1 -> rotates strike to Liton (a2)
        scoringRepository.recordBallEvent(
            match = m0,
            runsOffBat = 1,
            extraType = ExtraType.NONE,
            extraRuns = 0,
            isWicket = false,
            dismissalType = null,
            dismissedPlayerId = null,
            dismissedPlayerName = null,
            incomingBatterId = null,
            incomingBatterName = null,
            assistingPlayerName = null,
            scorerId = "scorer_1"
        )
        val afterBall1 = matchRepository.getMatchById("UNDO01")!!
        assertEquals("a2", afterBall1.currentStrikerId)

        // Ball 2: Liton (a2) gets Bowled out, Shakib (a3) comes in
        scoringRepository.recordBallEvent(
            match = afterBall1,
            runsOffBat = 0,
            extraType = ExtraType.NONE,
            extraRuns = 0,
            isWicket = true,
            dismissalType = DismissalType.BOWLED,
            dismissedPlayerId = "a2",
            dismissedPlayerName = "Liton",
            incomingBatterId = "a3",
            incomingBatterName = "Shakib",
            assistingPlayerName = null,
            scorerId = "scorer_1"
        )
        val afterWicket = matchRepository.getMatchById("UNDO01")!!
        assertEquals(1, afterWicket.firstInnings?.wickets)
        assertTrue(afterWicket.dismissedPlayerIds.contains("a2"))
        assertEquals("a3", afterWicket.currentStrikerId)

        // Undo wicket -> must restore Liton (a2) on strike, Tamim (a1) non-striker, remove a2 from dismissedPlayerIds
        val undoRes = scoringRepository.undoLastBall(afterWicket, scorerId = "scorer_1")
        assertTrue(undoRes.isSuccess)
        val afterUndo = matchRepository.getMatchById("UNDO01")!!
        assertEquals(0, afterUndo.firstInnings?.wickets)
        assertEquals(1, afterUndo.firstInnings?.legalBalls)
        assertEquals(1, afterUndo.firstInnings?.totalRuns)
        assertEquals("a2", afterUndo.currentStrikerId)
        assertEquals("a1", afterUndo.currentNonStrikerId)
        assertFalse(afterUndo.dismissedPlayerIds.contains("a2"))
    }

    @Test
    fun `reconcileMatchFromLocalBalls recovers rolled-back scorecard from ball_events`() = runBlocking {
        val m0 = createLiveMatch(id = "HEAL01", overs = 10)
        // Record 6 balls of 4 runs -> 24 runs, 1.0 over
        repeat(6) {
            scoringRepository.recordBallEvent(
                match = m0,
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
                scorerId = "scorer_1"
            )
        }
        assertEquals(24, matchRepository.getMatchById("HEAL01")!!.firstInnings?.totalRuns)

        // Simulate corrupted/rewound MatchEntity row (e.g., from legacy bug) back to 1 ball / 4 runs
        val corrupted = m0.copy(
            firstInnings = InningsScorecard(inningsNumber = 1, totalRuns = 4, legalBalls = 1),
            lastSyncTimestamp = System.currentTimeMillis()
        )
        db.matchDao().insertMatch(
            MatchEntity(
                id = "HEAL01",
                title = corrupted.title,
                status = corrupted.status.name,
                isOfflineScored = false,
                jsonPayload = JsonHelper.toJson(corrupted),
                updatedAt = corrupted.lastSyncTimestamp
            )
        )
        assertEquals(4, matchRepository.getMatchById("HEAL01")!!.firstInnings?.totalRuns)

        // Reconcile recovers exact 24 runs and 6 legalBalls from ball_events!
        val healed = scoringRepository.reconcileMatchFromLocalBalls("HEAL01")
        assertNotNull(healed)
        assertEquals(24, healed?.firstInnings?.totalRuns)
        assertEquals(6, healed?.firstInnings?.legalBalls)
        assertNull("End of 6-ball over clears bowler", healed?.currentBowlerId)
    }

    @Test
    fun `manualCompleteOver preserves online public status and rotates strike`() = runBlocking {
        val m0 = createLiveMatch(id = "OVER01", overs = 5)
        val res = scoringRepository.manualCompleteOver(m0, reason = "Umpire call", scorerId = "scorer_1")
        assertTrue(res.isSuccess)
        val updated = matchRepository.getMatchById("OVER01")!!
        assertFalse("Online match must NOT flip to isOfflineScored = true", updated.isOfflineScored)
        assertTrue(updated.isPublic)
        assertEquals("a2", updated.currentStrikerId)
        assertEquals("a1", updated.currentNonStrikerId)
        assertNull(updated.currentBowlerId)
    }

    @Test
    fun `syncAllPendingMatches recovers multiple interrupted matches after simulated restart`() = runBlocking {
        val m1 = createLiveMatch(id = "REC01", overs = 5)
        val m2 = createLiveMatch(id = "REC02", overs = 5)

        // Score 4 runs in m1, and 6+1 runs in m2
        scoringRepository.recordBallEvent(
            match = m1,
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
            scorerId = "scorer_1"
        )
        scoringRepository.recordBallEvent(
            match = m2,
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
            scorerId = "scorer_1"
        )
        scoringRepository.recordBallEvent(
            match = m2,
            runsOffBat = 1,
            extraType = ExtraType.NONE,
            extraRuns = 0,
            isWicket = false,
            dismissalType = null,
            dismissedPlayerId = null,
            dismissedPlayerName = null,
            incomingBatterId = null,
            incomingBatterName = null,
            assistingPlayerName = null,
            scorerId = "scorer_1"
        )

        // Simulate stale/rewound MatchEntity rows in Room while ball_events still hold PENDING rows
        db.matchDao().insertMatch(
            MatchEntity(
                id = "REC01",
                title = m1.title,
                status = MatchStatus.LIVE.name,
                isOfflineScored = false,
                jsonPayload = JsonHelper.toJson(m1),
                updatedAt = System.currentTimeMillis()
            )
        )
        db.matchDao().insertMatch(
            MatchEntity(
                id = "REC02",
                title = m2.title,
                status = MatchStatus.LIVE.name,
                isOfflineScored = false,
                jsonPayload = JsonHelper.toJson(m2),
                updatedAt = System.currentTimeMillis()
            )
        )
        assertEquals(0, matchRepository.getMatchById("REC01")?.firstInnings?.totalRuns ?: 0)
        assertEquals(0, matchRepository.getMatchById("REC02")?.firstInnings?.totalRuns ?: 0)

        // Run global recovery sweep (invoked on startup, ON_RESUME, and network reconnect)
        scoringRepository.syncAllPendingMatches()

        val recovered1 = matchRepository.getMatchById("REC01")!!
        val recovered2 = matchRepository.getMatchById("REC02")!!
        assertEquals(4, recovered1.firstInnings?.totalRuns)
        assertEquals(1, recovered1.firstInnings?.legalBalls)
        assertEquals(7, recovered2.firstInnings?.totalRuns)
        assertEquals(2, recovered2.firstInnings?.legalBalls)
    }

    @Test
    fun `cached JsonHelper adapters roundtrip Match and BallEvent accurately`() {
        val sample = Match(id = "J1", matchCode = "J1", code = "J1", title = "Cache Test")
        val json = JsonHelper.toJson(sample)
        val parsed = JsonHelper.fromJson<Match>(json)
        assertNotNull(parsed)
        assertEquals(sample.id, parsed?.id)
        assertEquals(sample.title, parsed?.title)

        val ball = BallEvent(id = "b1", matchId = "J1", runsOffBat = 6, ballSeq = 1)
        val ballJson = JsonHelper.toJson(ball)
        val parsedBall = JsonHelper.fromJson<BallEvent>(ballJson)
        assertNotNull(parsedBall)
        assertEquals(6, parsedBall?.runsOffBat)
    }
}
