package com.example

import com.example.data.model.BatterInningsScore
import com.example.data.model.BowlerInningsScore
import com.example.data.model.InningsScorecard
import com.example.data.model.Match
import com.example.data.model.MatchPlayerSnapshot
import com.example.data.model.MatchStatus
import com.example.data.model.MatchTeamSnapshot
import com.example.data.model.Player
import com.example.data.model.PlayerRoles
import com.example.data.model.PlayerStats
import com.example.data.model.User
import com.example.util.FeaturedPlayersAggregator
import com.example.util.MatchCodeGenerator
import com.example.util.MatchSearchHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAndHistoryFeatureTest {

    @Test
    fun featuredPlayersAggregator_aggregatesMultiUserMatchesWithSrArpmAndSpells() {
        val playerShakib = Player(
            id = "p_shakib",
            fullName = "Shakib Al Hasan",
            primaryRole = PlayerRoles.ALL_ROUNDER,
            creatorId = "user_1"
        )
        val playerTamim = Player(
            id = "p_tamim",
            fullName = "Tamim Iqbal",
            primaryRole = PlayerRoles.BATSMAN,
            creatorId = "user_2"
        )
        val playerZero = Player(
            id = "p_zero",
            fullName = "Bench Player",
            primaryRole = PlayerRoles.BATSMAN,
            creatorId = "user_3"
        )

        val match1 = Match(
            id = "m1",
            matchCode = "CR7K9M",
            ownerId = "user_1",
            ownerUid = "user_1",
            status = MatchStatus.COMPLETED,
            playingXI_A = listOf(MatchPlayerSnapshot(id = "p_shakib", name = "Shakib Al Hasan")),
            playingXI_B = listOf(MatchPlayerSnapshot(id = "p_tamim", name = "Tamim Iqbal")),
            firstInnings = InningsScorecard(
                inningsNumber = 1,
                batters = listOf(
                    BatterInningsScore(
                        playerId = "p_shakib",
                        playerName = "Shakib Al Hasan",
                        runs = 60,
                        balls = 30,
                        fours = 6,
                        sixes = 3,
                        isOut = false
                    )
                ),
                bowlers = listOf(
                    BowlerInningsScore(
                        playerId = "p_tamim",
                        playerName = "Tamim Iqbal",
                        legalBalls = 6,
                        runsConceded = 12,
                        wickets = 0
                    )
                )
            ),
            secondInnings = InningsScorecard(
                inningsNumber = 2,
                batters = listOf(
                    BatterInningsScore(
                        playerId = "p_tamim",
                        playerName = "Tamim Iqbal",
                        runs = 45,
                        balls = 30,
                        fours = 5,
                        sixes = 1,
                        isOut = true
                    )
                ),
                bowlers = listOf(
                    BowlerInningsScore(
                        playerId = "p_shakib",
                        playerName = "Shakib Al Hasan",
                        legalBalls = 24,
                        runsConceded = 18,
                        wickets = 4
                    )
                )
            )
        )

        val match2 = Match(
            id = "m2",
            matchCode = "AB3X8P",
            ownerId = "user_2",
            ownerUid = "user_2",
            status = MatchStatus.COMPLETED,
            playingXI_A = listOf(MatchPlayerSnapshot(id = "p_shakib", name = "Shakib Al Hasan")),
            firstInnings = InningsScorecard(
                inningsNumber = 1,
                batters = listOf(
                    BatterInningsScore(
                        playerId = "p_shakib",
                        playerName = "Shakib Al Hasan",
                        runs = 40,
                        balls = 20,
                        fours = 4,
                        sixes = 2,
                        isOut = true
                    )
                ),
                bowlers = listOf(
                    BowlerInningsScore(
                        playerId = "p_shakib",
                        playerName = "Shakib Al Hasan",
                        legalBalls = 18,
                        runsConceded = 21,
                        wickets = 1
                    )
                )
            )
        )

        val results = FeaturedPlayersAggregator.computeTopPlayers(
            matches = listOf(match1, match2),
            dbPlayers = listOf(playerShakib, playerTamim, playerZero),
            limit = 10
        )

        // Bench Player with zero runs/balls/wickets is excluded
        assertEquals(2, results.size)
        val top = results[0]
        assertEquals("Shakib Al Hasan", top.name)
        assertEquals(2, top.matchesPlayed)
        assertEquals(100, top.totalRuns)
        assertEquals(50, top.ballsFaced)
        assertEquals(200.0, top.strikeRate, 0.01)
        assertEquals(50.0, top.averageRunPerMatch, 0.01)
        assertEquals("60*(30)", top.bestKnockText)
        assertEquals(5, top.totalWickets)
        assertEquals("4/18 • 4.0 Ov", top.bestBowlingSpellText)
        // Batting RR = 100*6/50 = 12.0, Bowling Economy = (18+21)*6/(24+18) = 5.57 -> NRR = +6.43
        assertEquals(6.43, top.netRunRate, 0.02)
        assertTrue(top.formattedNrr.startsWith("+"))

        val second = results[1]
        assertEquals("Tamim Iqbal", second.name)
        assertEquals(1, second.matchesPlayed)
        assertEquals(45, second.totalRuns)
        assertEquals(150.0, second.strikeRate, 0.01)
        assertEquals(45.0, second.averageRunPerMatch, 0.01)
    }

    @Test
    fun featuredPlayersAggregator_fallsBackToDbStatsWhenNoMatchScorecardsExist() {
        val dbPlayer = Player(
            id = "p_legacy",
            fullName = "Mashrafe Mortaza",
            primaryRole = PlayerRoles.BOWLER,
            stats = PlayerStats(
                matches = 4,
                innings = 3,
                runs = 48,
                ballsFaced = 32,
                highestScore = 24,
                wickets = 7,
                oversBowled = 14.0,
                runsConceded = 78,
                bestBowlingWickets = 3,
                bestBowlingRuns = 15
            )
        )

        val results = FeaturedPlayersAggregator.computeTopPlayers(
            matches = emptyList(),
            dbPlayers = listOf(dbPlayer),
            limit = 5
        )

        assertEquals(1, results.size)
        val summary = results[0]
        assertEquals(4, summary.matchesPlayed)
        assertEquals(48, summary.totalRuns)
        assertEquals(150.0, summary.strikeRate, 0.01)
        assertEquals(12.0, summary.averageRunPerMatch, 0.01)
        assertEquals(7, summary.totalWickets)
        assertTrue(summary.bestBowlingSpellText.contains("3/15"))
    }

    @Test
    fun historyUniqueMatchCodeNormalization_matchesFormattedOrLowercaseCodes() {
        val normalizedInput = MatchCodeGenerator.normalizeCode(" cr7-k9m ")
        val normalizedMatchCode = MatchCodeGenerator.normalizeCode("CR7K9M")
        assertEquals("CR7K9M", normalizedInput)
        assertTrue(normalizedMatchCode.contains(normalizedInput))
    }

    @Test
    fun liveSearchHelper_matchesUniqueCodeTournamentNameAndTeamNames() {
        val match = Match(
            id = "m_live_1",
            matchCode = "CR7K9M",
            code = "CR7K9M",
            title = "Quarter Final",
            tournamentName = "Dhaka Premier T20 Cup",
            teamA = MatchTeamSnapshot(id = "ta", name = "Sunrise Cricketers", shortName = "SRC"),
            teamB = MatchTeamSnapshot(id = "tb", name = "Green Bengal Tigers", shortName = "GBT")
        )

        // Match code (formatted lowercase / with hyphen)
        assertTrue(MatchSearchHelper.matchesLiveSearchQuery(match, "cr7-k9m"))
        assertTrue(MatchSearchHelper.matchesLiveSearchQuery(match, "CR7K"))
        // Tournament name
        assertTrue(MatchSearchHelper.matchesLiveSearchQuery(match, "dhaka premier"))
        // Team A full & short name
        assertTrue(MatchSearchHelper.matchesLiveSearchQuery(match, "Sunrise"))
        assertTrue(MatchSearchHelper.matchesLiveSearchQuery(match, "src"))
        // Team B full & short name
        assertTrue(MatchSearchHelper.matchesLiveSearchQuery(match, "Bengal Tigers"))
        assertTrue(MatchSearchHelper.matchesLiveSearchQuery(match, "gbt"))
        // Blank shows all
        assertTrue(MatchSearchHelper.matchesLiveSearchQuery(match, "   "))
        // Unrelated query rejected
        assertFalse(MatchSearchHelper.matchesLiveSearchQuery(match, "Rajshahi Kings"))
    }

    @Test
    fun computeUserCareerStats_aggregatesByUidLinkedPlayerAndDisplayNameWithZeroFallback() {
        val activeUser = User(
            uid = "dev_ashraful",
            displayName = "Md Ashraful",
            primaryRole = PlayerRoles.ALL_ROUNDER,
            battingStyle = "Right Hand",
            bowlingStyle = "Right-arm Leg Spin",
            linkedPlayerId = "p_ashraful"
        )
        val zeroUser = User(
            uid = "dev_newbie",
            displayName = "New Member",
            primaryRole = PlayerRoles.BATSMAN
        )

        val match1 = Match(
            id = "m_u1",
            status = MatchStatus.COMPLETED,
            playingXI_A = listOf(MatchPlayerSnapshot(id = "p_ashraful", name = "Md Ashraful")),
            firstInnings = InningsScorecard(
                inningsNumber = 1,
                batters = listOf(
                    BatterInningsScore(
                        playerId = "p_ashraful",
                        playerName = "Md Ashraful",
                        runs = 72,
                        balls = 40,
                        isOut = true
                    )
                ),
                bowlers = listOf(
                    BowlerInningsScore(
                        playerId = "dev_ashraful",
                        playerName = "Md Ashraful",
                        legalBalls = 18,
                        runsConceded = 14,
                        wickets = 3
                    )
                )
            )
        )

        val stats = FeaturedPlayersAggregator.computeUserCareerStats(
            user = activeUser,
            matches = listOf(match1),
            dbPlayers = emptyList()
        )
        assertEquals(1, stats.matchesPlayed)
        assertEquals(72, stats.totalRuns)
        assertEquals(3, stats.totalWickets)
        assertEquals(180.0, stats.strikeRate, 0.01)
        assertEquals("72(40)", stats.bestKnockText)
        assertEquals("3/14 • 3.0 Ov", stats.bestBowlingSpellText)

        val zeroStats = FeaturedPlayersAggregator.computeUserCareerStats(
            user = zeroUser,
            matches = listOf(match1),
            dbPlayers = emptyList()
        )
        assertEquals(0, zeroStats.matchesPlayed)
        assertEquals(0, zeroStats.totalRuns)
        assertEquals(0, zeroStats.totalWickets)
        assertEquals("—", zeroStats.bestKnockText)
    }

    @Test
    fun featuredPlayersAggregator_excludesRemovedPlayersAndBackfillsNextTopPerformer() {
        val p1 = Player(
            id = "p_star1",
            fullName = "Shakib Al Hasan",
            stats = PlayerStats(matches = 3, innings = 3, runs = 150, ballsFaced = 90, wickets = 6)
        )
        val p2 = Player(
            id = "p_star2",
            fullName = "Tamim Iqbal",
            stats = PlayerStats(matches = 3, innings = 3, runs = 110, ballsFaced = 80, wickets = 0)
        )

        val token = FeaturedPlayersAggregator.exclusionToken("p_star1", "Shakib Al Hasan")
        assertEquals("id:p_star1", token)

        val afterRemove = FeaturedPlayersAggregator.computeTopPlayers(
            matches = emptyList(),
            dbPlayers = listOf(p1, p2),
            limit = 10,
            excludedKeys = setOf(token)
        )
        assertEquals(1, afterRemove.size)
        assertEquals("Tamim Iqbal", afterRemove[0].name)

        val restored = FeaturedPlayersAggregator.computeTopPlayers(
            matches = emptyList(),
            dbPlayers = listOf(p1, p2),
            limit = 10,
            excludedKeys = emptySet()
        )
        assertEquals(2, restored.size)
        assertEquals("Shakib Al Hasan", restored[0].name)
    }
}
