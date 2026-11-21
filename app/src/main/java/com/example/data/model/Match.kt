package com.example.data.model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties

enum class MatchStatus {
    UPCOMING,
    LIVE,
    INNINGS_BREAK,
    COMPLETED,
    ABANDONED,
    CANCELLED,
    NO_RESULT
}

enum class DismissalType(val displayName: String, val icon: String, val isBowlerWicket: Boolean) {
    BOWLED("Bowled", "🎯", true),
    CAUGHT("Caught", "🤲", true),
    CAUGHT_BEHIND("Caught Behind", "🧤", true),
    RUN_OUT("Run Out", "🏃", false),
    LBW("LBW", "🦵", true),
    STUMPED("Stumped", "⚡", true),
    RETIRED_HURT("Retired Hurt", "🏥", false),
    MANKAD("Mankad / Non-Striker Run Out", "⚠️", false),
    HIT_WICKET("Hit Wicket", "💥", true),
    TIMED_OUT("Timed Out", "⏱️", false),
    OBSTRUCTING_FIELD("Obstructing the Field", "🛑", false),
    HIT_BALL_TWICE("Hit the Ball Twice", "🏏", false),
    HANDLED_BALL("Handled the Ball", "🖐️", false),
    RETIRED_OUT("Retired Out", "🚶", false)
}

enum class ExtraType(val shortName: String) {
    NONE(""),
    WIDE("wd"),
    NO_BALL("nb"),
    BYE("b"),
    LEG_BYE("lb"),
    PENALTY("pen")
}

@IgnoreExtraProperties
data class MatchTeamSnapshot(
    val id: String = "",
    val name: String = "",
    val shortName: String = "",
    val logoUrl: String? = null
)

@IgnoreExtraProperties
data class SubScorerInfo(
    val uid: String = "",
    val name: String = "",
    val addedAt: Long = System.currentTimeMillis()
)

@IgnoreExtraProperties
data class MatchPlayerSnapshot(
    val id: String = "",
    val name: String = "",
    val role: String = "All-Rounder",
    val isCaptain: Boolean = false,
    val isWicketkeeper: Boolean = false,
    val photoUrl: String? = null
)

@IgnoreExtraProperties
data class BatterInningsScore(
    val playerId: String = "",
    val playerName: String = "",
    val battingPosition: Int = 0,
    val isCaptain: Boolean = false,
    val isWicketkeeper: Boolean = false,
    val runs: Int = 0,
    val balls: Int = 0,
    val fours: Int = 0,
    val sixes: Int = 0,
    val isOut: Boolean = false,
    val dismissalText: String = "not out",
    val bowlerName: String = "",
    val fielderName: String = ""
) {
    @get:Exclude
    val strikeRate: Double
        get() = if (balls > 0) (runs.toDouble() * 100.0 / balls) else 0.0

    @get:Exclude
    val completedStatusLabel: String
        get() = if (isOut) "OUT" else "NOT OUT"
}

@IgnoreExtraProperties
data class BowlerInningsScore(
    val playerId: String = "",
    val playerName: String = "",
    val legalBalls: Int = 0, // 6 balls = 1.0 over
    val maidens: Int = 0,
    val wicketMaidens: Int = 0,
    val dotBalls: Int = 0,
    val runsConceded: Int = 0,
    val wickets: Int = 0,
    val wides: Int = 0,
    val noBalls: Int = 0
) {
    @get:Exclude
    val oversString: String
        get() = "${legalBalls / 6}.${legalBalls % 6}"

    @get:Exclude
    val economy: Double
        get() = if (legalBalls > 0) (runsConceded.toDouble() * 6.0 / legalBalls) else 0.0

    @get:Exclude
    val average: Double
        get() = if (wickets > 0) (runsConceded.toDouble() / wickets) else 0.0
}

@IgnoreExtraProperties
data class FallOfWicket(
    val wicketNumber: Int = 0,
    val teamScore: Int = 0,
    val legalBalls: Int = 0,
    val batterName: String = ""
) {
    @get:Exclude
    val overString: String
        get() = "${legalBalls / 6}.${legalBalls % 6}"
}

@IgnoreExtraProperties
data class InningsScorecard(
    val inningsNumber: Int = 1,
    val battingTeamId: String = "",
    val battingTeamName: String = "",
    val bowlingTeamId: String = "",
    val bowlingTeamName: String = "",
    val totalRuns: Int = 0,
    val wickets: Int = 0,
    val legalBalls: Int = 0,
    val oversLimit: Int = 20,
    val wides: Int = 0,
    val noBalls: Int = 0,
    val byes: Int = 0,
    val legByes: Int = 0,
    val penalties: Int = 0,
    val batters: List<BatterInningsScore> = emptyList(),
    val bowlers: List<BowlerInningsScore> = emptyList(),
    val fallOfWickets: List<FallOfWicket> = emptyList(),
    val isDeclaredOrAllOut: Boolean = false
) {
    @get:Exclude
    val totalExtras: Int
        get() = wides + noBalls + byes + legByes + penalties

    @get:Exclude
    val bowlingExtras: Int
        get() = wides + noBalls

    @get:Exclude
    val fieldingExtras: Int
        get() = byes + legByes

    @get:Exclude
    val totalBatsmanRuns: Int
        get() = batters.sumOf { it.runs }

    @get:Exclude
    val totalBowlerRunsConceded: Int
        get() = bowlers.sumOf { it.runsConceded }

    // Automatic Score Reconciliation Check (MCC Law / Scorebook Reconciliation)
    @get:Exclude
    val isScoreReconciled: Boolean
        get() {
            val runsMatch = totalRuns == (totalBatsmanRuns + totalExtras)
            val bowlerRunsMatch = totalBowlerRunsConceded == (totalRuns - fieldingExtras - penalties)
            return runsMatch && bowlerRunsMatch
        }

    @get:Exclude
    val oversFormatted: String
        get() = "${legalBalls / 6}.${legalBalls % 6}"

    @get:Exclude
    val runRate: Double
        get() = if (legalBalls > 0) (totalRuns.toDouble() * 6.0 / legalBalls) else 0.0

    @Exclude
    fun calculateProjectedScore(totalOvers: Int): Int {
        if (legalBalls == 0) return 0
        val rr = totalRuns.toDouble() / (legalBalls / 6.0)
        return (rr * totalOvers).toInt()
    }
}

@IgnoreExtraProperties
data class Match(
    val id: String = "",
    val matchCode: String = "",
    val code: String = "", // Canonical match code field for Firebase Firestore
    val title: String = "",
    val category: String = "Friendly", // Friendly, Tournament, Practice, League, Custom
    val format: String = "T20", // T10, T20, T25, T30, T40, T50, Test, Custom Overs
    val totalOvers: Int = 20,
    val venue: String = "",
    val matchDate: Long = System.currentTimeMillis(),
    val description: String = "",
    val tournamentId: String? = null,
    val tournamentName: String? = null,
    val teamA: MatchTeamSnapshot = MatchTeamSnapshot(),
    val teamB: MatchTeamSnapshot = MatchTeamSnapshot(),
    val team1CaptainId: String = "",
    val team2CaptainId: String = "",
    val fullSquad_A: List<MatchPlayerSnapshot> = emptyList(), // Up to 16 players
    val fullSquad_B: List<MatchPlayerSnapshot> = emptyList(),
    val playingXI_A: List<MatchPlayerSnapshot> = emptyList(), // Selected playing squad (e.g. 11)
    val playingXI_B: List<MatchPlayerSnapshot> = emptyList(),
    val tossWinnerTeamId: String = "",
    val tossDecision: String = "BAT", // BAT or FIELD
    val battingFirstTeamId: String = "",
    val bowlingFirstTeamId: String = "",
    val currentInningsNumber: Int = 1,
    val status: MatchStatus = MatchStatus.UPCOMING,
    val ownerId: String = "",
    val ownerUid: String = "", // Canonical owner UID for Firebase security and cross-device ownership
    val ownerName: String = "",
    val isPublic: Boolean = true, // Publicly discoverable by unique match code
    val authorizedScorers: List<String> = emptyList(),
    val subScorerProfiles: List<SubScorerInfo> = emptyList(),
    val currentScorerId: String? = null,
    val resultDescription: String = "",
    val winnerTeamId: String? = null,
    val playerOfTheMatch: String? = null,
    val targetRuns: Int? = null,
    val maxWicketsCustom: Int? = null, // Custom max wickets, defaults to (playing squad size - 1)
    val playersPerTeam: Int = 11, // Exact number of players selected for each team
    val umpire1: String = "", // Mandatory: 1st Umpire / ম্যাচ পরিচালক ১ (বাধ্যতামূলক)
    val umpire2: String = "", // Optional: 2nd Umpire / ২য় আম্পায়ার (ঐচ্ছিক)
    val umpire3: String = "", // Optional: 3rd Umpire or Match Referee / ৩য় আম্পায়ার / রেফারি (ঐচ্ছিক)
    // Active Live Scoring Snapshot
    val currentStrikerId: String? = null,
    val currentStrikerName: String? = null,
    val currentNonStrikerId: String? = null,
    val currentNonStrikerName: String? = null,
    val currentBowlerId: String? = null,
    val currentBowlerName: String? = null,
    val currentPartnershipRuns: Int = 0,
    val currentPartnershipBalls: Int = 0,
    val recentBallsSummary: List<String> = emptyList(),
    val dismissedPlayerIds: List<String> = emptyList(),
    // Completed/Generated Scorecards
    val firstInnings: InningsScorecard? = null,
    val secondInnings: InningsScorecard? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val lastSyncTimestamp: Long = System.currentTimeMillis(),
    val isOfflineScored: Boolean = false,
    val pendingSyncCount: Int = 0
) {
    @get:Exclude
    val effectiveOwnerUid: String
        get() = when {
            ownerUid.isNotBlank() -> ownerUid
            ownerId.isNotBlank() -> ownerId
            else -> ""
        }

    @Exclude
    fun isHeadScorer(userId: String): Boolean {
        if (isOfflineScored || !isPublic) return true
        if (userId.isBlank() || effectiveOwnerUid.isBlank()) return false
        return effectiveOwnerUid == userId || effectiveOwnerUid == "device_local"
    }

    fun canUserScore(userId: String): Boolean {
        if (isHeadScorer(userId)) return true
        if (userId.isBlank()) return false
        return authorizedScorers.contains(userId) || subScorerProfiles.any { it.uid == userId }
    }

    @get:Exclude
    val activeInnings: InningsScorecard?
        get() = if (currentInningsNumber == 1) firstInnings else secondInnings

    @Exclude
    fun getEffectivePlayersPerTeam(): Int {
        if (playersPerTeam > 0) return playersPerTeam
        val countA = if (playingXI_A.isNotEmpty()) playingXI_A.size else fullSquad_A.size
        val countB = if (playingXI_B.isNotEmpty()) playingXI_B.size else fullSquad_B.size
        val determined = maxOf(countA, countB)
        return if (determined > 0) determined else 11
    }

    // Dynamic Maximum Wickets Calculation:
    // যত গুলো খেলোয়াড় সিলেক্ট করা হবে প্রতি দলে খেলার জন্য (N), ব্যাটিং এর সময় ঐ দলের উইকেট সংখ্যা তার চেয়ে সর্বদা ১ টা কম থাকবে (N - 1)
    @Exclude
    fun getMaxWicketsForInnings(inningsNumber: Int = currentInningsNumber): Int {
        val battingSquad = if (inningsNumber == 1) {
            if (battingFirstTeamId == teamA.id) playingXI_A else playingXI_B
        } else {
            if (battingFirstTeamId == teamA.id) playingXI_B else playingXI_A
        }
        val squadSize = battingSquad.distinctBy { it.id }.size
        val totalPlayers = when {
            squadSize > 0 -> squadSize
            playersPerTeam > 0 -> playersPerTeam
            else -> getEffectivePlayersPerTeam()
        }
        return if (totalPlayers > 1) totalPlayers - 1 else 1
    }

    // 2nd Innings Target Chase Helpers
    @Exclude
    fun getRunsRemaining(): Int {
        val target = targetRuns ?: return 0
        val currentRuns = secondInnings?.totalRuns ?: 0
        return maxOf(0, target - currentRuns)
    }

    @Exclude
    fun getBallsRemaining(): Int {
        val bowledBalls = secondInnings?.legalBalls ?: 0
        val totalMatchBalls = totalOvers * 6
        return maxOf(0, totalMatchBalls - bowledBalls)
    }

    @Exclude
    fun getRequiredRunRate(): Double {
        val runsNeeded = getRunsRemaining()
        val ballsLeft = getBallsRemaining()
        if (ballsLeft <= 0) return 0.0
        return (runsNeeded.toDouble() * 6.0) / ballsLeft
    }

    @Exclude
    fun compareScoringProgressTo(other: Match): Int {
        fun terminalRank(s: MatchStatus): Int = when (s) {
            MatchStatus.COMPLETED, MatchStatus.ABANDONED, MatchStatus.CANCELLED, MatchStatus.NO_RESULT -> 1
            else -> 0
        }
        fun subStatusRank(s: MatchStatus): Int = when (s) {
            MatchStatus.UPCOMING -> 0
            MatchStatus.LIVE -> 1
            MatchStatus.INNINGS_BREAK -> 2
            else -> 3
        }
        fun legalBallsSum(m: Match): Int =
            (m.firstInnings?.legalBalls ?: 0) + (m.secondInnings?.legalBalls ?: 0)
        fun ballVolumeSum(m: Match): Int =
            legalBallsSum(m) +
                (m.firstInnings?.wides ?: 0) + (m.firstInnings?.noBalls ?: 0) +
                (m.secondInnings?.wides ?: 0) + (m.secondInnings?.noBalls ?: 0)
        fun runsSum(m: Match): Int =
            (m.firstInnings?.totalRuns ?: 0) + (m.secondInnings?.totalRuns ?: 0)
        fun wicketsSum(m: Match): Int =
            (m.firstInnings?.wickets ?: 0) + (m.secondInnings?.wickets ?: 0)
        fun batterActivitySum(m: Match): Int {
            val b1 = m.firstInnings?.batters.orEmpty()
            val b2 = m.secondInnings?.batters.orEmpty()
            val hurtCount = b1.count { it.dismissalText.startsWith("retired hurt") } +
                b2.count { it.dismissalText.startsWith("retired hurt") }
            return b1.size + b2.size + hurtCount
        }
        fun creaseReadiness(m: Match): Int {
            var r = 0
            if (!m.currentStrikerId.isNullOrBlank()) r++
            if (!m.currentNonStrikerId.isNullOrBlank()) r++
            return r
        }

        val checks = intArrayOf(
            terminalRank(this.status).compareTo(terminalRank(other.status)),
            this.currentInningsNumber.compareTo(other.currentInningsNumber),
            subStatusRank(this.status).compareTo(subStatusRank(other.status)),
            legalBallsSum(this).compareTo(legalBallsSum(other)),
            ballVolumeSum(this).compareTo(ballVolumeSum(other)),
            runsSum(this).compareTo(runsSum(other)),
            wicketsSum(this).compareTo(wicketsSum(other)),
            batterActivitySum(this).compareTo(batterActivitySum(other)),
            creaseReadiness(this).compareTo(creaseReadiness(other)),
            this.recentBallsSummary.size.compareTo(other.recentBallsSummary.size)
        )
        for (cmp in checks) {
            if (cmp != 0) return cmp
        }
        return 0
    }

    @Exclude
    fun hasStrictlyHigherProgressThan(other: Match): Boolean =
        compareScoringProgressTo(other) > 0

    @Exclude
    fun isOfflineOnlyMatch(): Boolean =
        isOfflineScored || !isPublic || matchCode.equals("OFFLINE", ignoreCase = true) || id.startsWith("OFFLINE_")
}
