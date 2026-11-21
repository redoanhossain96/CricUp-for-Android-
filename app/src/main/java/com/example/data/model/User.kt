package com.example.data.model

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class User(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: String = ROLE_MEMBER,
    val photoUrl: String? = null,
    val primaryRole: String = PlayerRoles.ALL_ROUNDER,
    val battingStyle: String = "Right Hand",
    val bowlingStyle: String = "Right-arm Medium",
    val bio: String = "",
    val linkedPlayerId: String = "",
    val joinedTeamIds: List<String> = emptyList(),
    val followerIds: List<String> = emptyList(),
    val followingIds: List<String> = emptyList(),
    val predictionPoints: Int = 0,
    val predictionsWon: Int = 0,
    val predictionsTotal: Int = 0,
    val isSuspended: Boolean = false,
    val isBlocked: Boolean = false,
    val deviceId: String = "",
    val linkedDeviceIds: List<String> = emptyList(),
    val linkedUserUid: String = "",
    val provider: String = "device_identity",
    val createdAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val ROLE_MEMBER = "member"
    }

    @get:com.google.firebase.firestore.Exclude
    val effectivePlayerId: String
        get() = linkedPlayerId.ifBlank { uid }

    @get:com.google.firebase.firestore.Exclude
    val isGoogleSignedIn: Boolean
        get() = provider.equals("google", ignoreCase = true) ||
            (email.isNotBlank() && !uid.startsWith("dev_") && !uid.startsWith("guest"))

    @get:com.google.firebase.firestore.Exclude
    val predictionRankLabel: String
        get() = when {
            predictionPoints >= 1000 -> "👑 কিংবদন্তি বিশ্লেষক"
            predictionPoints >= 500 -> "🏆 মাস্টার প্রেডিক্টর"
            predictionPoints >= 200 -> "🔥 প্রো স্পেক্টেটর"
            predictionPoints >= 50 -> "⭐ রাইজিং ফ্যান"
            else -> "🏏 ক্রিকেট দর্শক"
        }
}
