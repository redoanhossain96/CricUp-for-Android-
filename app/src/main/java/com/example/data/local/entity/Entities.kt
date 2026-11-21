package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "teams")
data class TeamEntity(
    @PrimaryKey val id: String,
    val name: String,
    val shortName: String,
    val isArchived: Boolean,
    val jsonPayload: String,
    val updatedAt: Long
)

@Entity(tableName = "players")
data class PlayerEntity(
    @PrimaryKey val id: String,
    val fullName: String,
    val primaryRole: String,
    val isArchived: Boolean,
    val jsonPayload: String,
    val updatedAt: Long
)

@Entity(tableName = "matches")
data class MatchEntity(
    @PrimaryKey val id: String,
    val title: String,
    val status: String,
    val isOfflineScored: Boolean,
    val jsonPayload: String,
    val updatedAt: Long
)

@Entity(tableName = "ball_events")
data class BallEventEntity(
    @PrimaryKey val id: String,
    val matchId: String,
    val inningsNumber: Int,
    val ballSeq: Int,
    val syncStatus: String, // PENDING, SYNCED, FAILED
    val jsonPayload: String,
    val timestamp: Long
)

@Entity(tableName = "tournaments")
data class TournamentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val jsonPayload: String,
    val updatedAt: Long
)

@Entity(tableName = "umpires")
data class UmpireEntity(
    @PrimaryKey val id: String,
    val fullName: String,
    val isArchived: Boolean,
    val jsonPayload: String,
    val updatedAt: Long
)

