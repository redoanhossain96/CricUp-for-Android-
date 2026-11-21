package com.example

import com.example.data.model.BallEvent
import com.example.data.model.DismissalType
import com.example.data.model.ExtraType
import com.example.util.CommentaryHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentaryHelperTest {

    private fun baseBall(
        seq: Int = 1,
        over: Int = 0,
        ballInOver: Int = 1,
        runsOffBat: Int = 0,
        extraType: ExtraType = ExtraType.NONE,
        extraRuns: Int = 0,
        isWicket: Boolean = false,
        dismissalType: DismissalType? = null
    ) = BallEvent(
        id = "m1_inn1_b$seq",
        matchId = "match_test_1",
        inningsNumber = 1,
        ballSeq = seq,
        overNumber = over,
        ballInOver = ballInOver,
        bowlerName = "Taskin",
        strikerName = "Liton",
        runsOffBat = runsOffBat,
        extraType = extraType,
        extraRuns = extraRuns,
        isLegalDelivery = extraType != ExtraType.WIDE && extraType != ExtraType.NO_BALL,
        isWicket = isWicket,
        dismissalType = dismissalType,
        dismissedPlayerName = if (isWicket) "Liton" else null,
        assistingPlayerName = if (isWicket) "Miraz" else null
    )

    @Test
    fun sameActionProducesDistinctDialogues_acrossConsecutiveBalls_bnAndEn() {
        val actions = listOf(
            0 to ExtraType.NONE,
            1 to ExtraType.NONE,
            2 to ExtraType.NONE,
            3 to ExtraType.NONE,
            4 to ExtraType.NONE,
            6 to ExtraType.NONE
        )

        for (lang in listOf("bn", "en")) {
            for ((runs, extra) in actions) {
                val outputs = (1..6).map { seq ->
                    CommentaryHelper.formatCommentary(
                        baseBall(seq = seq, over = 0, ballInOver = seq, runsOffBat = runs, extraType = extra),
                        lang = lang
                    )
                }
                assertTrue("All commentary lines must be non-blank for runs=$runs lang=$lang", outputs.all { it.isNotBlank() })
                val distinctCount = outputs.distinct().size
                assertTrue(
                    "Expected at least 5 distinct dialogues for runs=$runs lang=$lang, got $distinctCount: $outputs",
                    distinctCount >= 5
                )
            }
        }
    }

    @Test
    fun sameWicketTypeAndExtrasProduceDistinctDialogues() {
        val dismissalCases = listOf(
            DismissalType.BOWLED,
            DismissalType.CAUGHT,
            DismissalType.CAUGHT_BEHIND,
            DismissalType.LBW,
            DismissalType.RUN_OUT,
            DismissalType.STUMPED
        )

        for (lang in listOf("bn", "en")) {
            for (dismissal in dismissalCases) {
                val outputs = (1..5).map { seq ->
                    CommentaryHelper.formatCommentary(
                        baseBall(seq = seq, isWicket = true, dismissalType = dismissal),
                        lang = lang
                    )
                }
                assertTrue(outputs.all { it.isNotBlank() })
                assertEquals(
                    "Consecutive $dismissal wickets in $lang should produce 5 distinct dialogues",
                    5,
                    outputs.distinct().size
                )
            }

            // Consecutive Wides
            val wides = (1..5).map { seq ->
                CommentaryHelper.formatCommentary(
                    baseBall(seq = seq, extraType = ExtraType.WIDE, extraRuns = 1),
                    lang = lang
                )
            }
            assertEquals(5, wides.distinct().size)

            // Consecutive No Balls
            val noBalls = (1..5).map { seq ->
                CommentaryHelper.formatCommentary(
                    baseBall(seq = seq, extraType = ExtraType.NO_BALL, extraRuns = 1),
                    lang = lang
                )
            }
            assertEquals(5, noBalls.distinct().size)
        }
    }

    @Test
    fun noBallWithBoundaryAndFiveWidesHaveRichDedicatedCommentary() {
        val nbSixBn = CommentaryHelper.formatCommentary(
            baseBall(seq = 1, runsOffBat = 6, extraType = ExtraType.NO_BALL, extraRuns = 1),
            lang = "bn"
        )
        val nbFourBn = CommentaryHelper.formatCommentary(
            baseBall(seq = 2, runsOffBat = 4, extraType = ExtraType.NO_BALL, extraRuns = 1),
            lang = "bn"
        )
        val fiveWidesEn = CommentaryHelper.formatCommentary(
            baseBall(seq = 3, runsOffBat = 0, extraType = ExtraType.WIDE, extraRuns = 5),
            lang = "en"
        )
        assertTrue(nbSixBn.contains("ছক্কা") || nbSixBn.contains("ছয়"))
        assertTrue(nbFourBn.contains("চার") || nbFourBn.contains("বাউন্ডারি"))
        assertTrue(fiveWidesEn.contains("5") || fiveWidesEn.contains("FIVE", ignoreCase = true))
    }

    @Test
    fun commentaryIsDeterministicForSameBallEvent() {
        val ball = baseBall(seq = 14, over = 2, ballInOver = 2, runsOffBat = 4)
        assertEquals(
            CommentaryHelper.formatCommentary(ball, "bn"),
            CommentaryHelper.formatCommentary(ball, "bn")
        )
        assertEquals(
            CommentaryHelper.formatCommentary(ball, "en"),
            CommentaryHelper.formatCommentary(ball, "en")
        )
        assertNotEquals(
            CommentaryHelper.formatCommentary(ball, "bn"),
            CommentaryHelper.formatCommentary(ball.copy(ballSeq = 15, ballInOver = 3), "bn")
        )
    }

    @Test
    fun allDismissalAndExtraTypesReturnNonBlankCommentary() {
        for (lang in listOf("bn", "en")) {
            DismissalType.entries.forEach { dismissal ->
                val text = CommentaryHelper.formatCommentary(
                    baseBall(seq = 1, isWicket = dismissal != DismissalType.RETIRED_HURT, dismissalType = dismissal),
                    lang = lang
                )
                assertTrue("Empty commentary for $dismissal ($lang)", text.isNotBlank())
            }
            ExtraType.entries.forEach { extra ->
                val text = CommentaryHelper.formatCommentary(
                    baseBall(seq = 1, extraType = extra, extraRuns = if (extra == ExtraType.NONE) 0 else 1),
                    lang = lang
                )
                assertTrue("Empty commentary for $extra ($lang)", text.isNotBlank())
            }
        }
    }
}
