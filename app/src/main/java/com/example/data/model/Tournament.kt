package com.example.data.model

data class TournamentTeamStanding(
    val teamId: String = "",
    val teamName: String = "",
    val shortName: String = "",
    val played: Int = 0,
    val won: Int = 0,
    val lost: Int = 0,
    val tied: Int = 0,
    val noResult: Int = 0,
    val points: Int = 0,
    val runsScored: Int = 0,
    val oversFacedBalls: Int = 0, // total legal balls faced
    val runsConceded: Int = 0,
    val oversBowledBalls: Int = 0 // total legal balls bowled
) {
    // Official Net Run Rate (NRR) = (Runs Scored / Overs Faced) - (Runs Conceded / Overs Bowled)
    val netRunRate: Double
        get() {
            val oversFaced = oversFacedBalls / 6.0
            val oversBowled = oversBowledBalls / 6.0
            val forRate = if (oversFaced > 0) (runsScored / oversFaced) else 0.0
            val againstRate = if (oversBowled > 0) (runsConceded / oversBowled) else 0.0
            return forRate - againstRate
        }

    val nrrFormatted: String
        get() = String.format(java.util.Locale.US, "%+.3f", netRunRate)
}

data class TournamentFixture(
    val id: String = "",
    val tournamentId: String = "",
    val roundName: String = "Group Stage",
    val matchNumber: Int = 1,
    val teamAId: String = "",
    val teamAName: String = "",
    val teamBId: String = "",
    val teamBName: String = "",
    val date: Long = System.currentTimeMillis(),
    val venue: String = "",
    val matchId: String? = null,
    val isCompleted: Boolean = false
)

data class Tournament(
    val id: String = "",
    val name: String = "",
    val logoUrl: String? = null,
    val organizer: String = "",
    val venue: String = "",
    val startDate: Long = System.currentTimeMillis(),
    val endDate: Long = System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000),
    val format: String = "League", // League, Knockout, League + Knockout, Group Stage, Custom
    val matchOvers: Int = 20,
    val description: String = "",
    val rules: String = "Standard T20 Playing Conditions",
    val teamIds: List<String> = emptyList(),
    val creatorId: String = "",
    val isPublished: Boolean = true,
    val standings: List<TournamentTeamStanding> = emptyList(),
    val fixtures: List<TournamentFixture> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)
