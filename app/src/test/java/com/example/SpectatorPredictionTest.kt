package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.BallEvent
import com.example.data.model.DismissalType
import com.example.data.model.ExtraType
import com.example.data.model.InningsScorecard
import com.example.data.model.Match
import com.example.data.model.MatchStatus
import com.example.data.model.MatchTeamSnapshot
import com.example.data.model.PredictionCategory
import com.example.data.model.PredictionCelebrationStyle
import com.example.data.model.PredictionStatus
import com.example.data.model.SubScorerInfo
import com.example.data.repository.AuthRepository
import com.example.data.repository.PredictionRepository
import com.example.util.PredictionEvaluator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpectatorPredictionTest {

    private lateinit var context: Context
    private lateinit var authRepo: AuthRepository
    private lateinit var predictionRepo: PredictionRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("cricup_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("cricup_member_profile", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("cricup_predictions_v1", Context.MODE_PRIVATE).edit().clear().commit()

        authRepo = AuthRepository(
            context = context,
            auth = null,
            firestore = null,
            emailOtpService = null,
            enableCloudSync = false
        )
        predictionRepo = PredictionRepository(
            context = context,
            firestore = null,
            authRepository = authRepo,
            enableCloudSync = false
        )
    }

    private fun sampleLiveMatch(
        matchId: String = "match_pred_01",
        ownerUid: String = "head_scorer_1",
        subScorerUid: String = "sub_scorer_2",
        status: MatchStatus = MatchStatus.LIVE,
        winnerTeamId: String? = null,
        inn1Runs: Int = 120,
        inn2Runs: Int = 115
    ): Match = Match(
        id = matchId,
        title = "Sylhet Sixers vs Dhaka Royals",
        teamA = MatchTeamSnapshot(id = "team_a", name = "Sylhet Sixers", shortName = "SYL"),
        teamB = MatchTeamSnapshot(id = "team_b", name = "Dhaka Royals", shortName = "DHK"),
        battingFirstTeamId = "team_a",
        bowlingFirstTeamId = "team_b",
        totalOvers = 10,
        currentInningsNumber = 1,
        status = status,
        ownerId = ownerUid,
        ownerUid = ownerUid,
        isPublic = true,
        isOfflineScored = false,
        authorizedScorers = listOf(subScorerUid),
        subScorerProfiles = listOf(SubScorerInfo(uid = subScorerUid, name = "Sub Scorer")),
        winnerTeamId = winnerTeamId,
        firstInnings = InningsScorecard(battingTeamId = "team_a", battingTeamName = "Sylhet Sixers", totalRuns = inn1Runs, wickets = 4, legalBalls = 60),
        secondInnings = if (status == MatchStatus.COMPLETED) {
            InningsScorecard(battingTeamId = "team_b", battingTeamName = "Dhaka Royals", totalRuns = inn2Runs, wickets = 6, legalBalls = 60)
        } else null
    )

    private fun ball(
        seq: Int,
        over: Int = 0,
        ballInOver: Int = seq,
        runsOffBat: Int = 0,
        extraType: ExtraType = ExtraType.NONE,
        extraRuns: Int = 0,
        isLegal: Boolean = true,
        isWicket: Boolean = false,
        dismissalType: DismissalType? = null
    ) = BallEvent(
        id = "b_$seq",
        matchId = "match_pred_01",
        inningsNumber = 1,
        ballSeq = seq,
        overNumber = over,
        ballInOver = ballInOver,
        runsOffBat = runsOffBat,
        extraType = extraType,
        extraRuns = extraRuns,
        isLegalDelivery = isLegal,
        isWicket = isWicket,
        dismissalType = dismissalType
    )

    @Test
    fun `head scorer and sub-scorer are strictly blocked from predicting and voting`() = runBlocking {
        val match = sampleLiveMatch()
        assertTrue(PredictionEvaluator.isScorerOrSubScorer(match, "head_scorer_1"))
        assertTrue(PredictionEvaluator.isScorerOrSubScorer(match, "sub_scorer_2"))
        assertFalse(PredictionEvaluator.isScorerOrSubScorer(match, "spectator_99"))

        val headResult = predictionRepo.submitPrediction(
            match = match,
            userId = "head_scorer_1",
            userName = "Head Scorer",
            category = PredictionCategory.NEXT_BALL,
            targetKey = "inn1_seq1",
            targetLabel = "ইনিংস 1 • বল #1",
            selectedOption = PredictionEvaluator.OPT_SIX,
            selectedLabel = "ছক্কা (6)",
            rewardPoints = 40,
            targetInnings = 1,
            baselineBallCount = 0
        )
        assertTrue(headResult.isFailure)

        val subResult = predictionRepo.submitPrediction(
            match = match,
            userId = "sub_scorer_2",
            userName = "Sub Scorer",
            category = PredictionCategory.WINNING_TEAM,
            targetKey = "match_winner",
            targetLabel = "ম্যাচ জয়ী দল ভোট",
            selectedOption = "team_a",
            selectedLabel = "Sylhet Sixers জিতবে",
            rewardPoints = 50,
            targetInnings = 1,
            baselineBallCount = 0
        )
        assertTrue(subResult.isFailure)

        val offlineMatch = match.copy(isOfflineScored = true)
        assertTrue(PredictionEvaluator.isScorerOrSubScorer(offlineMatch, "spectator_99"))
    }

    @Test
    fun `spectator earns profile points once per win across all 5 prediction types and triggers varied animations`() = runBlocking {
        val spectatorUid = "spectator_fan_1"
        val match = sampleLiveMatch()

        // 1. Next ball SIX (+40)
        predictionRepo.submitPrediction(
            match, spectatorUid, "Fan One",
            PredictionCategory.NEXT_BALL, "inn1_seq1", "ইনিংস 1 • বল #1",
            PredictionEvaluator.OPT_SIX, "ছক্কা (6)", 40,
            targetInnings = 1, baselineBallCount = 0
        ).getOrThrow()

        // 2. Action Challenge: Boundary in next 3 balls (+30)
        predictionRepo.submitPrediction(
            match, spectatorUid, "Fan One",
            PredictionCategory.ACTION_CHALLENGE, "inn1_act_from_0", "অ্যাকশন",
            PredictionEvaluator.OPT_ACTION_BOUNDARY_YES, "৩ বলে ৪/৬ হবে", 30,
            targetInnings = 1, baselineBallCount = 0
        ).getOrThrow()

        // 3. Next Over 11+ runs (+40) on over 0
        predictionRepo.submitPrediction(
            match, spectatorUid, "Fan One",
            PredictionCategory.NEXT_OVER, "inn1_over1", "১ম ওভার",
            PredictionEvaluator.OPT_OVER_BIG, "১১+ রান", 40,
            targetInnings = 1, baselineBallCount = 0, targetOverNumber = 0
        ).getOrThrow()

        // 4. Winning Team vote (+50)
        predictionRepo.submitPrediction(
            match, spectatorUid, "Fan One",
            PredictionCategory.WINNING_TEAM, "match_winner", "জয়ী দল",
            "team_a", "Sylhet Sixers জিতবে", 50,
            targetInnings = 1, baselineBallCount = 0
        ).getOrThrow()

        // 5. Winning Percent vote 75% Team A (+45)
        predictionRepo.submitPrediction(
            match, spectatorUid, "Fan One",
            PredictionCategory.WINNING_PERCENT, "win_percent_poll", "উইনিং %",
            "PCT_A_75", "Sylhet Sixers (75%)", 45,
            targetInnings = 1, baselineBallCount = 0, selectedPercentTeamA = 75
        ).getOrThrow()

        // Simulate 1st over: 6, 4, 1, 0, 2, W(Caught) -> total = 13 runs (11+ wins NEXT_OVER, 1st ball 6 wins NEXT_BALL & ACTION)
        val over0Balls = listOf(
            ball(1, over = 0, ballInOver = 1, runsOffBat = 6),
            ball(2, over = 0, ballInOver = 2, runsOffBat = 4),
            ball(3, over = 0, ballInOver = 3, runsOffBat = 1),
            ball(4, over = 0, ballInOver = 4, runsOffBat = 0),
            ball(5, over = 0, ballInOver = 5, runsOffBat = 2),
            ball(6, over = 0, ballInOver = 6, isWicket = true, dismissalType = DismissalType.CAUGHT)
        )

        val liveCelebrations = predictionRepo.evaluateAndSettleForUser(match, spectatorUid, over0Balls, emptyList())
        assertEquals(3, liveCelebrations.size)
        // Varied animation styles across consecutive wins!
        assertEquals(
            setOf(
                PredictionCelebrationStyle.CONFETTI_TROPHY,
                PredictionCelebrationStyle.FIREWORKS_STARBURST,
                PredictionCelebrationStyle.ROCKET_STREAK
            ),
            liveCelebrations.map { it.style }.toSet()
        )
        assertEquals(110, authRepo.getCurrentUserFlow().value?.predictionPoints)
        assertEquals(3, authRepo.getCurrentUserFlow().value?.predictionsWon)

        // Complete match -> team_a wins -> remaining 2 predictions settle with remaining animation styles
        val completedMatch = sampleLiveMatch(status = MatchStatus.COMPLETED, winnerTeamId = "team_a")
        val finalCelebrations = predictionRepo.evaluateAndSettleForUser(completedMatch, spectatorUid, over0Balls, emptyList())
        assertEquals(2, finalCelebrations.size)
        assertEquals(
            setOf(
                PredictionCelebrationStyle.CROWN_SPARKLE,
                PredictionCelebrationStyle.STADIUM_FLASH
            ),
            finalCelebrations.map { it.style }.toSet()
        )

        val finalUser = authRepo.getCurrentUserFlow().value!!
        assertEquals(205, finalUser.predictionPoints) // 40 + 30 + 40 + 50 + 45 = 205
        assertEquals(5, finalUser.predictionsWon)
        assertEquals(5, finalUser.predictionsTotal)
        assertEquals("🔥 প্রো স্পেক্টেটর", finalUser.predictionRankLabel)

        // Re-running settlement must NOT double-award points or re-emit celebrations
        val replayCelebrations = predictionRepo.evaluateAndSettleForUser(completedMatch, spectatorUid, over0Balls, emptyList())
        assertTrue(replayCelebrations.isEmpty())
        assertEquals(205, authRepo.getCurrentUserFlow().value?.predictionPoints)
    }

    @Test
    fun `action challenge consecutive dots, wicket type, and crowd stats work accurately`() = runBlocking {
        val match = sampleLiveMatch()
        predictionRepo.submitPrediction(
            match, "fan_dots", "Dot Fan",
            PredictionCategory.ACTION_CHALLENGE, "inn1_act_from_0", "অ্যাকশন",
            PredictionEvaluator.OPT_ACTION_DOT_PAIR, "টানা ২ ডট বল", 35,
            targetInnings = 1, baselineBallCount = 0
        ).getOrThrow()

        predictionRepo.submitPrediction(
            match, "fan_wkt", "Bowler Fan",
            PredictionCategory.ACTION_CHALLENGE, "inn1_act_from_0", "অ্যাকশন",
            PredictionEvaluator.OPT_ACTION_WKT_BOWLED_LBW, "বোল্ড/এলবিডব্লিউ", 45,
            targetInnings = 1, baselineBallCount = 0
        ).getOrThrow()

        val balls = listOf(
            ball(1, runsOffBat = 0),
            ball(2, runsOffBat = 0),
            ball(3, isWicket = true, dismissalType = DismissalType.BOWLED)
        )

        val res = PredictionEvaluator.evaluatePredictions(
            match = match,
            ballEvents1 = balls,
            ballEvents2 = emptyList(),
            predictions = predictionRepo.observeMatchPredictions(match.id).value
        )
        assertEquals(2, res.newlySettledWins)
        assertEquals(80, res.newlyEarnedPoints)
        assertTrue(res.updatedPredictions.all { it.resolvedStatus() == PredictionStatus.WON })
    }
}
