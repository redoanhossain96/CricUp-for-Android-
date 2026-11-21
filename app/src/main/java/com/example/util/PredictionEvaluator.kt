package com.example.util

import com.example.data.model.BallEvent
import com.example.data.model.CrowdVoteStats
import com.example.data.model.DismissalType
import com.example.data.model.ExtraType
import com.example.data.model.Match
import com.example.data.model.MatchPrediction
import com.example.data.model.MatchStatus
import com.example.data.model.PredictionCategory
import com.example.data.model.PredictionCelebrationStyle
import com.example.data.model.PredictionOptionSpec
import com.example.data.model.PredictionStatus
import com.example.data.model.PredictionWinCelebration
import kotlin.math.abs

data class NextBallTarget(
    val inningsNumber: Int,
    val baselineBallCount: Int,
    val targetKey: String,
    val targetLabel: String
)

data class NextOverTarget(
    val inningsNumber: Int,
    val targetOverZeroBased: Int,
    val targetKey: String,
    val targetLabel: String
)

data class PredictionEvaluationResult(
    val updatedPredictions: List<MatchPrediction>,
    val newlyWonCelebrations: List<PredictionWinCelebration>,
    val newlySettledWins: Int,
    val newlySettledLosses: Int,
    val newlyEarnedPoints: Int
)

object PredictionEvaluator {

    const val OPT_DOT = "DOT"
    const val OPT_RUNS_1_3 = "RUNS_1_3"
    const val OPT_FOUR = "FOUR"
    const val OPT_SIX = "SIX"
    const val OPT_WICKET = "WICKET"
    const val OPT_EXTRA = "EXTRA"

    const val OPT_OVER_LOW = "OVER_LOW_0_5"
    const val OPT_OVER_MID = "OVER_MID_6_10"
    const val OPT_OVER_BIG = "OVER_BIG_11_PLUS"
    const val OPT_OVER_WICKET = "OVER_WICKET"

    const val OPT_ACTION_BOUNDARY_YES = "ACTION_BOUNDARY_YES"
    const val OPT_ACTION_BOUNDARY_NO = "ACTION_BOUNDARY_NO"
    const val OPT_ACTION_WKT_CAUGHT = "ACTION_WKT_CAUGHT"
    const val OPT_ACTION_WKT_BOWLED_LBW = "ACTION_WKT_BOWLED_LBW"
    const val OPT_ACTION_WKT_RUN_OUT = "ACTION_WKT_RUN_OUT"
    const val OPT_ACTION_DOT_PAIR = "ACTION_DOT_PAIR"

    val nextBallOptions: List<PredictionOptionSpec> = listOf(
        PredictionOptionSpec(OPT_DOT, "ডট বল (0)", "কোনো রান নয়", 15),
        PredictionOptionSpec(OPT_RUNS_1_3, "১–৩ রান", "সিঙ্গেল/ডাবল", 15),
        PredictionOptionSpec(OPT_FOUR, "চার (4)", "বাউন্ডারি", 30),
        PredictionOptionSpec(OPT_SIX, "ছক্কা (6)", "ওভার বাউন্ডারি", 40),
        PredictionOptionSpec(OPT_WICKET, "উইকেট (W)", "আউট হবে!", 50),
        PredictionOptionSpec(OPT_EXTRA, "এক্সট্রা", "ওয়াইড / নো / বাই", 25)
    )

    val nextOverOptions: List<PredictionOptionSpec> = listOf(
        PredictionOptionSpec(OPT_OVER_LOW, "০–৫ রান", "টাইট ওভার", 25),
        PredictionOptionSpec(OPT_OVER_MID, "৬–১০ রান", "ব্যালেন্সড ওভার", 25),
        PredictionOptionSpec(OPT_OVER_BIG, "১১+ রান 🔥", "বিগ হিট ওভার", 40),
        PredictionOptionSpec(OPT_OVER_WICKET, "উইকেট পড়বে 🎯", "কমপক্ষে ১ উইকেট", 35)
    )

    val actionChallengeOptions: List<PredictionOptionSpec> = listOf(
        PredictionOptionSpec(OPT_ACTION_BOUNDARY_YES, "৩ বলে ৪/৬ হবে 🔥", "পরবর্তী ৩ ডেলিভারি", 30),
        PredictionOptionSpec(OPT_ACTION_BOUNDARY_NO, "৩ বলে ৪/৬ নয় 🛡️", "পরবর্তী ৩ ডেলিভারি", 25),
        PredictionOptionSpec(OPT_ACTION_DOT_PAIR, "টানা ২ ডট বল 🧱", "পরবর্তী ২ ডেলিভারি", 35),
        PredictionOptionSpec(OPT_ACTION_WKT_CAUGHT, "পরের উইকেট: ক্যাচ 🤲", "ক্যাচ আউট", 35),
        PredictionOptionSpec(OPT_ACTION_WKT_BOWLED_LBW, "পরের উইকেট: বোল্ড/এলবিডব্লিউ 🎯", "বোল্ড/এলবিডব্লিউ/স্টাম্প", 45),
        PredictionOptionSpec(OPT_ACTION_WKT_RUN_OUT, "পরের উইকেট: রান আউট 🏃", "ফিল্ডিং ম্যাজিক", 55)
    )

    /**
     * Strictly enforces: Head Scorer and Sub-Scorers CANNOT participate in predictions or votes.
     */
    fun isScorerOrSubScorer(match: Match, userId: String): Boolean {
        if (userId.isBlank()) return false
        return match.isHeadScorer(userId) || match.canUserScore(userId)
    }

    fun isMatchAcceptingPredictions(match: Match): Boolean =
        match.status == MatchStatus.UPCOMING ||
            match.status == MatchStatus.LIVE ||
            match.status == MatchStatus.INNINGS_BREAK

    fun activeInningsForPrediction(match: Match): Int = when (match.status) {
        MatchStatus.INNINGS_BREAK -> 2
        else -> match.currentInningsNumber.coerceIn(1, 2)
    }

    fun sortedInningsBalls(balls: List<BallEvent>): List<BallEvent> =
        balls.filter { it.syncStatus != BallEvent.SYNC_STATUS_DELETED }
            .sortedWith(compareBy<BallEvent>({ it.ballSeq }, { it.timestamp }, { it.id }))

    fun computeNextBallTarget(
        match: Match,
        ballEvents1: List<BallEvent>,
        ballEvents2: List<BallEvent>
    ): NextBallTarget {
        val innings = activeInningsForPrediction(match)
        val sortedBalls = sortedInningsBalls(if (innings == 1) ballEvents1 else ballEvents2)
        val baseline = sortedBalls.size
        val activeScorecard = if (innings == 1) match.firstInnings else match.secondInnings
        val legalBalls = activeScorecard?.legalBalls ?: sortedBalls.count { it.isLegalDelivery }
        val overDisp = legalBalls / 6
        val ballDisp = (legalBalls % 6) + 1
        return NextBallTarget(
            inningsNumber = innings,
            baselineBallCount = baseline,
            targetKey = "inn${innings}_seq${baseline + 1}",
            targetLabel = "ইনিংস $innings • বল #${baseline + 1} (${overDisp}.${ballDisp})"
        )
    }

    fun computeNextOverTarget(
        match: Match,
        ballEvents1: List<BallEvent>,
        ballEvents2: List<BallEvent>
    ): NextOverTarget {
        val innings = activeInningsForPrediction(match)
        val sortedBalls = sortedInningsBalls(if (innings == 1) ballEvents1 else ballEvents2)
        val activeScorecard = if (innings == 1) match.firstInnings else match.secondInnings
        val legalBalls = activeScorecard?.legalBalls ?: sortedBalls.count { it.isLegalDelivery }
        val maxOvers = match.totalOvers.coerceAtLeast(1)
        val targetOverZero = if (legalBalls % 6 == 0) {
            (legalBalls / 6).coerceAtMost(maxOvers - 1)
        } else {
            ((legalBalls / 6) + 1).coerceAtMost(maxOvers - 1)
        }
        return NextOverTarget(
            inningsNumber = innings,
            targetOverZeroBased = targetOverZero,
            targetKey = "inn${innings}_over${targetOverZero + 1}",
            targetLabel = "ইনিংস $innings • ${targetOverZero + 1}ম/তম ওভার"
        )
    }

    fun classifyBallOutcome(ball: BallEvent): String {
        if (ball.isWicket && ball.dismissalType != DismissalType.RETIRED_HURT) return OPT_WICKET
        if (ball.runsOffBat == 6) return OPT_SIX
        if (ball.runsOffBat == 4) return OPT_FOUR
        if (ball.extraType != ExtraType.NONE) return OPT_EXTRA
        if (ball.totalRunsOnBall == 0) return OPT_DOT
        return OPT_RUNS_1_3
    }

    fun resolveMatchWinnerTeamId(match: Match): String? {
        if (!match.winnerTeamId.isNullOrBlank()) return match.winnerTeamId
        if (match.status != MatchStatus.COMPLETED) return null
        val inn1 = match.firstInnings ?: return null
        val inn2 = match.secondInnings ?: return null
        return when {
            inn1.totalRuns > inn2.totalRuns -> inn1.battingTeamId.ifBlank { match.battingFirstTeamId }
            inn2.totalRuns > inn1.totalRuns -> inn2.battingTeamId.ifBlank { match.bowlingFirstTeamId }
            else -> null
        }
    }

    fun isCloseThrillerMatch(match: Match): Boolean {
        val inn1 = match.firstInnings ?: return false
        val inn2 = match.secondInnings ?: return false
        val runDiff = abs(inn1.totalRuns - inn2.totalRuns)
        if (runDiff <= 10) return true
        val winnerId = resolveMatchWinnerTeamId(match)
        if (winnerId == inn2.battingTeamId) {
            val wicketsLeft = (match.getMaxWicketsForInnings(2) - inn2.wickets).coerceAtLeast(0)
            if (wicketsLeft <= 2) return true
        }
        return false
    }

    fun evaluatePredictions(
        match: Match,
        ballEvents1: List<BallEvent>,
        ballEvents2: List<BallEvent>,
        predictions: List<MatchPrediction>,
        nowMs: Long = System.currentTimeMillis()
    ): PredictionEvaluationResult {
        val inn1Balls = sortedInningsBalls(ballEvents1)
        val inn2Balls = sortedInningsBalls(ballEvents2)
        val isTerminal = match.status == MatchStatus.COMPLETED ||
            match.status == MatchStatus.ABANDONED ||
            match.status == MatchStatus.CANCELLED ||
            match.status == MatchStatus.NO_RESULT
        val isCancelledOrAbandoned = match.status == MatchStatus.ABANDONED ||
            match.status == MatchStatus.CANCELLED ||
            match.status == MatchStatus.NO_RESULT

        var winsCount = 0
        var lossesCount = 0
        var earnedPts = 0
        val celebrations = mutableListOf<PredictionWinCelebration>()
        var styleIndex = predictions.count { it.resolvedStatus() == PredictionStatus.WON }

        val updated = predictions.map { pred ->
            if (pred.resolvedStatus() != PredictionStatus.PENDING) {
                return@map pred
            }

            val inningsBalls = if (pred.targetInnings == 2) inn2Balls else inn1Balls
            val inningsEnded = isTerminal || (pred.targetInnings == 1 && (
                match.currentInningsNumber >= 2 ||
                    match.status == MatchStatus.INNINGS_BREAK ||
                    match.firstInnings?.isDeclaredOrAllOut == true
                ))

            var newStatus: PredictionStatus? = null
            var awarded = 0
            var detail = ""

            when (pred.resolvedCategory()) {
                PredictionCategory.NEXT_BALL -> {
                    val targetIdx = pred.baselineBallCount
                    if (targetIdx < inningsBalls.size) {
                        val ball = inningsBalls[targetIdx]
                        val actual = classifyBallOutcome(ball)
                        if (actual == pred.selectedOption) {
                            newStatus = PredictionStatus.WON
                            awarded = pred.rewardPoints.coerceAtLeast(15)
                            detail = "সঠিক অনুমান! বলের ফল: ${ball.displayBadgeText}"
                        } else {
                            newStatus = PredictionStatus.LOST
                            detail = "বলের ফল ছিল: ${ball.displayBadgeText}"
                        }
                    } else if (inningsEnded) {
                        newStatus = PredictionStatus.EXPIRED
                        detail = "পরবর্তী বল খেলার আগেই ইনিংস/ম্যাচ সমাপ্ত হয়েছে"
                    }
                }

                PredictionCategory.NEXT_OVER -> {
                    val overBalls = inningsBalls.filter { it.overNumber == pred.targetOverNumber }
                    val legalInOver = overBalls.count { it.isLegalDelivery }
                    val overCompleted = legalInOver >= 6 || (inningsEnded && overBalls.isNotEmpty())
                    if (overCompleted) {
                        val overRuns = overBalls.sumOf { it.totalRunsOnBall }
                        val overWickets = overBalls.count {
                            it.isWicket && it.dismissalType != DismissalType.RETIRED_HURT
                        }
                        val won = when (pred.selectedOption) {
                            OPT_OVER_LOW -> overRuns in 0..5
                            OPT_OVER_MID -> overRuns in 6..10
                            OPT_OVER_BIG -> overRuns >= 11
                            OPT_OVER_WICKET -> overWickets >= 1
                            else -> false
                        }
                        newStatus = if (won) PredictionStatus.WON else PredictionStatus.LOST
                        awarded = if (won) pred.rewardPoints.coerceAtLeast(25) else 0
                        detail = "ওভারে ${overRuns} রান ও ${overWickets} উইকেট"
                    } else if (inningsEnded) {
                        newStatus = PredictionStatus.EXPIRED
                        detail = "এই ওভার শুরুর আগেই ইনিংস সমাপ্ত"
                    }
                }

                PredictionCategory.ACTION_CHALLENGE -> {
                    val futureBalls = inningsBalls.drop(pred.baselineBallCount.coerceAtLeast(0))
                    when (pred.selectedOption) {
                        OPT_ACTION_BOUNDARY_YES, OPT_ACTION_BOUNDARY_NO -> {
                            val window = futureBalls.take(3)
                            val hitBoundary = window.any { it.runsOffBat == 4 || it.runsOffBat == 6 }
                            val wantYes = pred.selectedOption == OPT_ACTION_BOUNDARY_YES
                            if (hitBoundary) {
                                newStatus = if (wantYes) PredictionStatus.WON else PredictionStatus.LOST
                                awarded = if (wantYes) pred.rewardPoints.coerceAtLeast(30) else 0
                                detail = "৩ বলের মধ্যেই বাউন্ডারি হয়েছে!"
                            } else if (window.size >= 3 || (inningsEnded && window.isNotEmpty())) {
                                newStatus = if (!wantYes) PredictionStatus.WON else PredictionStatus.LOST
                                awarded = if (!wantYes) pred.rewardPoints.coerceAtLeast(25) else 0
                                detail = "পরবর্তী ৩ বলে বাউন্ডারি হয়নি"
                            } else if (inningsEnded) {
                                newStatus = PredictionStatus.EXPIRED
                                detail = "ইনিংস সমাপ্ত হওয়ায় বাতিল"
                            }
                        }
                        OPT_ACTION_DOT_PAIR -> {
                            val window = futureBalls.take(2)
                            val firstIsNonDot = window.firstOrNull()?.let {
                                it.totalRunsOnBall > 0 || (it.isWicket && it.dismissalType != DismissalType.RETIRED_HURT)
                            } == true
                            val secondIsNonDot = window.getOrNull(1)?.let {
                                it.totalRunsOnBall > 0 || (it.isWicket && it.dismissalType != DismissalType.RETIRED_HURT)
                            } == true
                            if (firstIsNonDot || secondIsNonDot) {
                                newStatus = PredictionStatus.LOST
                                detail = "টানা ২ ডট বল হয়নি"
                            } else if (window.size >= 2) {
                                newStatus = PredictionStatus.WON
                                awarded = pred.rewardPoints.coerceAtLeast(35)
                                detail = "পরপর ২টি নিখুঁত ডট বল!"
                            } else if (inningsEnded) {
                                newStatus = PredictionStatus.EXPIRED
                                detail = "ইনিংস সমাপ্ত হওয়ায় বাতিল"
                            }
                        }
                        OPT_ACTION_WKT_CAUGHT, OPT_ACTION_WKT_BOWLED_LBW, OPT_ACTION_WKT_RUN_OUT -> {
                            val nextWicket = futureBalls.firstOrNull {
                                it.isWicket && it.dismissalType != DismissalType.RETIRED_HURT
                            }
                            if (nextWicket != null) {
                                val actualCategory = when (nextWicket.dismissalType) {
                                    DismissalType.CAUGHT, DismissalType.CAUGHT_BEHIND -> OPT_ACTION_WKT_CAUGHT
                                    DismissalType.BOWLED, DismissalType.LBW, DismissalType.STUMPED, DismissalType.HIT_WICKET -> OPT_ACTION_WKT_BOWLED_LBW
                                    DismissalType.RUN_OUT, DismissalType.MANKAD -> OPT_ACTION_WKT_RUN_OUT
                                    else -> "OTHER"
                                }
                                val won = actualCategory == pred.selectedOption
                                newStatus = if (won) PredictionStatus.WON else PredictionStatus.LOST
                                awarded = if (won) pred.rewardPoints.coerceAtLeast(35) else 0
                                detail = "উইকেট ধরন: ${nextWicket.dismissalType?.displayName ?: "Out"}"
                            } else if (inningsEnded) {
                                newStatus = PredictionStatus.EXPIRED
                                detail = "এই ইনিংসে আর উইকেট পড়েনি"
                            }
                        }
                    }
                }

                PredictionCategory.WINNING_TEAM -> {
                    if (isCancelledOrAbandoned) {
                        newStatus = PredictionStatus.EXPIRED
                        detail = "ম্যাচ বাতিল/পরিত্যক্ত"
                    } else if (match.status == MatchStatus.COMPLETED) {
                        val winnerId = resolveMatchWinnerTeamId(match)
                        if (winnerId.isNullOrBlank()) {
                            newStatus = PredictionStatus.EXPIRED
                            detail = "ম্যাচ ড্র/টাই হয়েছে"
                        } else if (pred.selectedOption == winnerId) {
                            newStatus = PredictionStatus.WON
                            awarded = pred.rewardPoints.coerceAtLeast(50)
                            val winName = if (winnerId == match.teamA.id) match.teamA.name else match.teamB.name
                            detail = "$winName জিতেছে!"
                        } else {
                            newStatus = PredictionStatus.LOST
                            val winName = if (winnerId == match.teamA.id) match.teamA.name else match.teamB.name
                            detail = "জয়ী দল: $winName"
                        }
                    }
                }

                PredictionCategory.WINNING_PERCENT -> {
                    if (isCancelledOrAbandoned) {
                        newStatus = PredictionStatus.EXPIRED
                        detail = "ম্যাচ বাতিল/পরিত্যক্ত"
                    } else if (match.status == MatchStatus.COMPLETED) {
                        val winnerId = resolveMatchWinnerTeamId(match)
                        if (winnerId.isNullOrBlank()) {
                            newStatus = PredictionStatus.EXPIRED
                            detail = "ম্যাচ টাই/ড্র হয়েছে"
                        } else {
                            val teamAPct = pred.selectedPercentTeamA.coerceIn(0, 100)
                            val pctForWinner = if (winnerId == match.teamA.id) teamAPct else (100 - teamAPct)
                            val won = when {
                                pctForWinner > 50 -> true
                                pctForWinner == 50 && isCloseThrillerMatch(match) -> true
                                else -> false
                            }
                            if (won) {
                                newStatus = PredictionStatus.WON
                                awarded = when {
                                    pctForWinner >= 70 -> 45
                                    pctForWinner == 50 -> 40
                                    else -> 30
                                }
                                detail = "জয়ী দলের পক্ষে আপনার ভোট ছিল $pctForWinner%"
                            } else {
                                newStatus = PredictionStatus.LOST
                                detail = "জয়ী দলের পক্ষে আপনার ভোট ছিল $pctForWinner%"
                            }
                        }
                    }
                }
            }

            if (newStatus != null) {
                if (newStatus == PredictionStatus.WON) {
                    winsCount++
                    earnedPts += awarded
                    val styles = PredictionCelebrationStyle.entries
                    val chosenStyle = styles[styleIndex % styles.size]
                    styleIndex++
                    val cat = pred.resolvedCategory()
                    celebrations.add(
                        PredictionWinCelebration(
                            id = "${pred.id}_$nowMs",
                            predictionId = pred.id,
                            category = cat,
                            title = celebrationHeadlineFor(chosenStyle, cat),
                            subtitle = "${pred.selectedLabel} • $detail",
                            pointsEarned = awarded,
                            style = chosenStyle
                        )
                    )
                } else if (newStatus == PredictionStatus.LOST) {
                    lossesCount++
                }
                pred.copy(
                    status = newStatus.name,
                    awardedPoints = awarded,
                    resolvedDetail = detail,
                    resolvedAt = nowMs
                )
            } else {
                pred
            }
        }

        return PredictionEvaluationResult(
            updatedPredictions = updated,
            newlyWonCelebrations = celebrations,
            newlySettledWins = winsCount,
            newlySettledLosses = lossesCount,
            newlyEarnedPoints = earnedPts
        )
    }

    fun celebrationHeadlineFor(
        style: PredictionCelebrationStyle,
        category: PredictionCategory
    ): String = when (style) {
        PredictionCelebrationStyle.CONFETTI_TROPHY -> "🎉 দারুণ প্রেডিকশন! (${category.displayTitle})"
        PredictionCelebrationStyle.FIREWORKS_STARBURST -> "🎆 অবিশ্বাস্য! একদম নিখুঁত অনুমান!"
        PredictionCelebrationStyle.ROCKET_STREAK -> "🚀 সিক্সার প্রেডিকশন! দারুণ জয়!"
        PredictionCelebrationStyle.CROWN_SPARKLE -> "👑 ক্রিকেট গুরু! ১০০% সঠিক অনুমান!"
        PredictionCelebrationStyle.STADIUM_FLASH -> "🏆 বাজিমাত! সেরা দর্শক বিশ্লেষক!"
    }

    fun computeCrowdVoteStats(
        match: Match,
        allMatchPredictions: List<MatchPrediction>
    ): CrowdVoteStats {
        if (allMatchPredictions.isEmpty()) return CrowdVoteStats()
        val teamVotes = allMatchPredictions.filter { it.resolvedCategory() == PredictionCategory.WINNING_TEAM }
        val teamAVotes = teamVotes.count { it.selectedOption == match.teamA.id }
        val teamBVotes = teamVotes.count { it.selectedOption == match.teamB.id }

        val pctVotes = allMatchPredictions.filter { it.resolvedCategory() == PredictionCategory.WINNING_PERCENT }
        val avgA = if (pctVotes.isNotEmpty()) {
            (pctVotes.sumOf { it.selectedPercentTeamA.coerceIn(0, 100) }.toFloat() / pctVotes.size).toInt()
        } else 50

        val optionCounts = mutableMapOf<String, Int>()
        allMatchPredictions.forEach { p ->
            val key = "${p.category}:${p.targetKey}:${p.selectedOption}"
            optionCounts[key] = (optionCounts[key] ?: 0) + 1
        }

        return CrowdVoteStats(
            teamAVotes = teamAVotes,
            teamBVotes = teamBVotes,
            totalPercentPollVotes = pctVotes.size,
            avgTeamAWinPercent = avgA,
            totalPredictionsCount = allMatchPredictions.size,
            categoryOptionCounts = optionCounts
        )
    }
}
