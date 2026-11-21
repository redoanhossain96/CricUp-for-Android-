package com.example.data.model

object PlayerRoles {
    const val BATSMAN = "Batsman"
    const val BOWLER = "Bowler"
    const val ALL_ROUNDER = "All-Rounder"
    const val WICKETKEEPER = "Wicketkeeper"

    fun getRoleIcon(role: String): String = when {
        role.contains("Bat", ignoreCase = true) -> "🏏"
        role.contains("Bowl", ignoreCase = true) -> "🎯"
        role.contains("Keeper", ignoreCase = true) -> "🧤"
        else -> "⚡"
    }

    val rolesWithIcons = listOf(
        "🏏 Batsman",
        "🎯 Bowler",
        "⚡ All-Rounder",
        "🧤 Wicketkeeper"
    )
}

data class PlayerStats(
    val matches: Int = 0,
    val innings: Int = 0,
    val runs: Int = 0,
    val highestScore: Int = 0,
    val fours: Int = 0,
    val sixes: Int = 0,
    val fifties: Int = 0,
    val hundreds: Int = 0,
    val ballsFaced: Int = 0,
    val notOuts: Int = 0,
    // Bowling
    val wickets: Int = 0,
    val oversBowled: Double = 0.0,
    val runsConceded: Int = 0,
    val maidens: Int = 0,
    val bestBowlingWickets: Int = 0,
    val bestBowlingRuns: Int = 0,
    // Fielding
    val catches: Int = 0,
    val runOuts: Int = 0
) {
    val battingAverage: Double
        get() {
            val dismissals = innings - notOuts
            return if (dismissals > 0) (runs.toDouble() / dismissals) else runs.toDouble()
        }

    val strikeRate: Double
        get() = if (ballsFaced > 0) (runs.toDouble() * 100.0 / ballsFaced) else 0.0

    val bowlingEconomy: Double
        get() {
            val totalBalls = (oversBowled.toInt() * 6) + Math.round((oversBowled - oversBowled.toInt()) * 10).toInt()
            return if (totalBalls > 0) (runsConceded.toDouble() * 6.0 / totalBalls) else 0.0
        }

    val bowlingAverage: Double
        get() = if (wickets > 0) (runsConceded.toDouble() / wickets) else 0.0
}

data class Player(
    val id: String = java.util.UUID.randomUUID().toString(),
    val fullName: String = "",
    val nickname: String = "",
    val photoUrl: String? = null,
    val jerseyNumber: Int = 0,
    val primaryRole: String = PlayerRoles.ALL_ROUNDER,
    val battingStyle: String = "Right Hand",
    val bowlingStyle: String = "Right-arm Medium",
    val teamIds: List<String> = emptyList(),
    val contactInfo: String = "",
    val isArchived: Boolean = false,
    val creatorId: String = "",
    val stats: PlayerStats = PlayerStats(),
    val createdAt: Long = System.currentTimeMillis()
)
