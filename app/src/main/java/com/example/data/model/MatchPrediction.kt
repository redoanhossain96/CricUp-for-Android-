package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.firestore.IgnoreExtraProperties

enum class PredictionCategory(val displayTitle: String, val badgeEmoji: String) {
    NEXT_BALL("পরবর্তী বল", "⚡"),
    NEXT_OVER("পরবর্তী ওভার", "🎯"),
    ACTION_CHALLENGE("অ্যাকশন প্রেডিকশন", "🔥"),
    WINNING_TEAM("জয়ী দল ভোট", "🏆"),
    WINNING_PERCENT("উইনিং % ভোট", "📊")
}

enum class PredictionStatus {
    PENDING,
    WON,
    LOST,
    EXPIRED
}

enum class PredictionCelebrationStyle {
    CONFETTI_TROPHY,
    FIREWORKS_STARBURST,
    ROCKET_STREAK,
    CROWN_SPARKLE,
    STADIUM_FLASH
}

@Keep
@IgnoreExtraProperties
data class MatchPrediction(
    val id: String = "",
    val matchId: String = "",
    val userId: String = "",
    val userName: String = "",
    val category: String = PredictionCategory.NEXT_BALL.name,
    val targetKey: String = "",
    val targetLabel: String = "",
    val selectedOption: String = "",
    val selectedLabel: String = "",
    val selectedPercentTeamA: Int = 50,
    val rewardPoints: Int = 20,
    val targetInnings: Int = 1,
    val baselineBallCount: Int = 0,
    val targetOverNumber: Int = 0,
    val status: String = PredictionStatus.PENDING.name,
    val awardedPoints: Int = 0,
    val pointsSyncedToProfile: Boolean = false,
    val resolvedDetail: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val resolvedAt: Long? = null
) {
    fun resolvedCategory(): PredictionCategory =
        PredictionCategory.entries.firstOrNull { it.name == category } ?: PredictionCategory.NEXT_BALL

    fun resolvedStatus(): PredictionStatus =
        PredictionStatus.entries.firstOrNull { it.name == status } ?: PredictionStatus.PENDING
}

data class CrowdVoteStats(
    val teamAVotes: Int = 0,
    val teamBVotes: Int = 0,
    val totalPercentPollVotes: Int = 0,
    val avgTeamAWinPercent: Int = 50,
    val totalPredictionsCount: Int = 0,
    val categoryOptionCounts: Map<String, Int> = emptyMap()
) {
    val totalTeamVotes: Int
        get() = teamAVotes + teamBVotes

    val teamAVotePercent: Int
        get() = if (totalTeamVotes > 0) ((teamAVotes * 100f) / totalTeamVotes).toInt() else 50

    val teamBVotePercent: Int
        get() = if (totalTeamVotes > 0) (100 - teamAVotePercent) else 50

    val avgTeamBWinPercent: Int
        get() = (100 - avgTeamAWinPercent).coerceIn(0, 100)
}

data class PredictionWinCelebration(
    val id: String,
    val predictionId: String,
    val category: PredictionCategory,
    val title: String,
    val subtitle: String,
    val pointsEarned: Int,
    val style: PredictionCelebrationStyle
)

data class PredictionOptionSpec(
    val key: String,
    val label: String,
    val subtitle: String,
    val rewardPoints: Int
)
