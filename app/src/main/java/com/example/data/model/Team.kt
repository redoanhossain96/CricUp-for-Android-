package com.example.data.model

data class Team(
    val id: String = "",
    val name: String = "",
    val shortName: String = "",
    val logoUrl: String? = null,
    val description: String = "",
    val ownerId: String = "",
    val ownerName: String = "",
    val homeGround: String = "",
    val contactInfo: String = "",
    val isArchived: Boolean = false,
    val playerIds: List<String> = emptyList(),
    val matchesPlayed: Int = 0,
    val matchesWon: Int = 0,
    val matchesLost: Int = 0,
    val matchesTied: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
