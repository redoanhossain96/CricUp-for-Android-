package com.example

import com.example.data.model.BallEvent
import com.example.data.model.DismissalType
import com.example.data.model.Player
import com.example.data.model.PlayerStats
import com.example.data.model.Team
import com.example.data.model.TournamentTeamStanding
import com.example.data.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ExampleRobolectricTest {

    @Test
    fun `user default role is member`() {
        val regularMember = User(
            uid = "regular_member_123"
        )
        assertEquals(User.ROLE_MEMBER, regularMember.role)
    }

    @Test
    fun `wicket with runs badge and retired hurt badge formatting`() {
        val runOutWithTwo = BallEvent(
            isWicket = true,
            dismissalType = DismissalType.RUN_OUT,
            runsOffBat = 2
        )
        assertEquals("W+2", runOutWithTwo.displayBadgeText)

        val retiredHurt = BallEvent(
            isWicket = false,
            dismissalType = DismissalType.RETIRED_HURT
        )
        assertEquals("RH", retiredHurt.displayBadgeText)
    }

    @Test
    fun `tournament net run rate calculation`() {
        val standing = TournamentTeamStanding(
            teamId = "t1",
            teamName = "Team Alpha",
            runsScored = 180,
            oversFacedBalls = 120,
            runsConceded = 150,
            oversBowledBalls = 120
        )
        assertEquals(1.500, standing.netRunRate, 0.001)
        assertEquals("+1.500", standing.nrrFormatted)
    }

    @Test
    fun `reusable permanent team and player integrity`() {
        val team = Team(
            id = "team_permanence_01",
            name = "Team Alpha",
            shortName = "TAL"
        )
        assertEquals("team_permanence_01", team.id)
        assertFalse(team.isArchived)

        val player = Player(
            id = "p_test_01",
            fullName = "Player One",
            stats = PlayerStats(
                matches = 10,
                wickets = 20,
                runsConceded = 140,
                oversBowled = 40.0
            )
        )
        assertEquals(3.50, player.stats.bowlingEconomy, 0.01)
    }

    @Test
    fun `initial destination is INTRO`() {
        val initialDestination = NavDestination.INTRO
        assertEquals(NavDestination.INTRO, initialDestination)
    }
}
