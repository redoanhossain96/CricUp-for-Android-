package com.example.data.model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class BallEvent(
    val id: String = "",
    val matchId: String = "",
    val inningsNumber: Int = 1,
    val ballSeq: Int = 0,
    val overNumber: Int = 0,
    val ballInOver: Int = 0,
    val bowlerId: String = "",
    val bowlerName: String = "",
    val strikerId: String = "",
    val strikerName: String = "",
    val nonStrikerId: String = "",
    val nonStrikerName: String = "",
    val runsOffBat: Int = 0,
    val extraType: ExtraType = ExtraType.NONE,
    val extraRuns: Int = 0,
    val isLegalDelivery: Boolean = true,
    val isWicket: Boolean = false,
    val dismissalType: DismissalType? = null,
    val dismissedPlayerId: String? = null,
    val dismissedPlayerName: String? = null,
    val assistingPlayerId: String? = null,
    val assistingPlayerName: String? = null,
    val commentary: String = "",
    val scorerId: String = "",
    val createdByUid: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val syncStatus: String = SYNC_STATUS_SYNCED
) {
    companion object {
        const val SYNC_STATUS_PENDING = "PENDING"
        const val SYNC_STATUS_SYNCED = "SYNCED"
        const val SYNC_STATUS_FAILED = "FAILED"
        const val SYNC_STATUS_DELETED = "DELETED"
    }

    @get:Exclude
    val totalRunsOnBall: Int
        get() = runsOffBat + extraRuns

    @get:Exclude
    val displayBadgeText: String
        get() = when {
            dismissalType == DismissalType.RETIRED_HURT -> "RH"
            dismissalType == DismissalType.RETIRED_OUT -> "RO"
            isWicket -> if (totalRunsOnBall > 0) "W+$totalRunsOnBall" else "W"
            extraType == ExtraType.WIDE -> if (extraRuns > 1) "${extraRuns}Wd" else "Wd"
            extraType == ExtraType.NO_BALL -> if (runsOffBat > 0) "${runsOffBat + extraRuns}Nb" else "Nb"
            extraType == ExtraType.BYE -> "${extraRuns}B"
            extraType == ExtraType.LEG_BYE -> "${extraRuns}Lb"
            extraType == ExtraType.PENALTY -> "+${extraRuns}P"
            runsOffBat == 0 -> "•"
            runsOffBat == 4 -> "4"
            runsOffBat == 6 -> "6"
            else -> "$runsOffBat"
        }
}

@IgnoreExtraProperties
data class CorrectionRecord(
    val id: String = "",
    val matchId: String = "",
    val ballId: String = "",
    val userId: String = "",
    val userName: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val reason: String = "",
    val originalDescription: String = "",
    val updatedDescription: String = ""
)
