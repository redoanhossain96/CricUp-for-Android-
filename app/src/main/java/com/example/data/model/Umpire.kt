package com.example.data.model

data class MatchUmpireSnapshot(
    val id: String = "",
    val name: String = "",
    val photoUrl: String? = null,
    val phone: String = ""
)

data class Umpire(
    val id: String = "",
    val fullName: String = "",
    val photoUrl: String? = null,
    val phone: String = "",
    val association: String = "",
    val certificationLevel: String = "Club / Local",
    val additionalNotes: String = "",
    val creatorId: String = "",
    val matchesOfficiated: Int = 0,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    val name: String get() = fullName
}

