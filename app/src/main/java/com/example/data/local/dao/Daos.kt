package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.BallEventEntity
import com.example.data.local.entity.MatchEntity
import com.example.data.local.entity.PlayerEntity
import com.example.data.local.entity.TeamEntity
import com.example.data.local.entity.TournamentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TeamDao {
    @Query("SELECT * FROM teams WHERE isArchived = 0 ORDER BY name ASC")
    fun getAllTeamsFlow(): Flow<List<TeamEntity>>

    @Query("SELECT * FROM teams")
    suspend fun getAllTeamsList(): List<TeamEntity>

    @Query("SELECT * FROM teams WHERE id = :teamId LIMIT 1")
    suspend fun getTeamById(teamId: String): TeamEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTeams(teams: List<TeamEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTeam(team: TeamEntity)

    @Query("DELETE FROM teams WHERE id = :teamId")
    suspend fun deleteTeamById(teamId: String)

    @Query("DELETE FROM teams")
    suspend fun deleteAllTeams()
}

@Dao
interface PlayerDao {
    @Query("SELECT * FROM players WHERE isArchived = 0 ORDER BY fullName ASC")
    fun getAllPlayersFlow(): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM players WHERE isArchived = 0 ORDER BY fullName ASC")
    suspend fun getAllPlayers(): List<PlayerEntity>

    @Query("SELECT * FROM players WHERE id = :playerId LIMIT 1")
    suspend fun getPlayerById(playerId: String): PlayerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayers(players: List<PlayerEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayer(player: PlayerEntity)

    @Query("DELETE FROM players WHERE id = :playerId")
    suspend fun deletePlayerById(playerId: String)

    @Query("DELETE FROM players")
    suspend fun deleteAllPlayers()
}

@Dao
interface MatchDao {
    @Query("SELECT * FROM matches ORDER BY updatedAt DESC")
    fun getAllMatchesFlow(): Flow<List<MatchEntity>>

    @Query("SELECT * FROM matches ORDER BY updatedAt DESC")
    suspend fun getAllMatches(): List<MatchEntity>

    @Query("SELECT * FROM matches WHERE id = :matchId LIMIT 1")
    suspend fun getMatchById(matchId: String): MatchEntity?

    @Query("SELECT * FROM matches WHERE id = :matchId LIMIT 1")
    fun getMatchFlowById(matchId: String): Flow<MatchEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatch(match: MatchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatches(matches: List<MatchEntity>)

    @Query("SELECT id FROM matches")
    suspend fun getAllMatchIds(): List<String>

    @Query("DELETE FROM matches WHERE id = :matchId")
    suspend fun deleteMatchById(matchId: String)

    @Query("DELETE FROM matches WHERE LENGTH(jsonPayload) > 1800000")
    suspend fun deleteUnreadableOversizedMatches(): Int
}

@Dao
interface BallEventDao {
    @Query("SELECT * FROM ball_events WHERE matchId = :matchId AND inningsNumber = :innings AND syncStatus != 'DELETED' ORDER BY ballSeq ASC")
    fun getBallEventsFlow(matchId: String, innings: Int): Flow<List<BallEventEntity>>

    @Query("SELECT * FROM ball_events WHERE matchId = :matchId AND inningsNumber = :innings AND syncStatus != 'DELETED' ORDER BY ballSeq ASC")
    suspend fun getBallEventsList(matchId: String, innings: Int): List<BallEventEntity>

    @Query("SELECT * FROM ball_events WHERE matchId = :matchId ORDER BY ballSeq ASC")
    suspend fun getAllRawBallEventsForMatch(matchId: String): List<BallEventEntity>

    @Query("SELECT * FROM ball_events WHERE syncStatus = 'PENDING' ORDER BY timestamp ASC")
    suspend fun getPendingSyncEvents(): List<BallEventEntity>

    @Query("SELECT * FROM ball_events WHERE matchId = :matchId AND syncStatus IN ('PENDING', 'DELETED') ORDER BY timestamp ASC")
    suspend fun getPendingOrDeletedSyncEventsForMatch(matchId: String): List<BallEventEntity>

    @Query("SELECT DISTINCT matchId FROM ball_events WHERE syncStatus IN ('PENDING', 'FAILED', 'DELETED')")
    suspend fun getPendingSyncMatchIds(): List<String>

    @Query("SELECT COUNT(*) FROM ball_events WHERE matchId = :matchId AND syncStatus IN ('PENDING', 'DELETED')")
    suspend fun hasPendingSyncEvents(matchId: String): Int

    @Query("SELECT COUNT(*) FROM ball_events WHERE matchId = :matchId AND syncStatus IN ('PENDING', 'DELETED')")
    fun getPendingCountFlow(matchId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBallEvent(event: BallEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBallEvents(events: List<BallEventEntity>)

    @Query("DELETE FROM ball_events WHERE id = :eventId")
    suspend fun deleteBallEvent(eventId: String)

    @Query("UPDATE ball_events SET syncStatus = 'DELETED' WHERE id = :eventId")
    suspend fun markEventDeleted(eventId: String)

    @Query("UPDATE ball_events SET syncStatus = 'SYNCED' WHERE id = :eventId")
    suspend fun markEventSynced(eventId: String)
}

@Dao
interface TournamentDao {
    @Query("SELECT * FROM tournaments ORDER BY updatedAt DESC")
    fun getAllTournamentsFlow(): Flow<List<TournamentEntity>>

    @Query("SELECT * FROM tournaments ORDER BY updatedAt DESC")
    suspend fun getAllTournamentsList(): List<TournamentEntity>

    @Query("SELECT * FROM tournaments WHERE id = :id LIMIT 1")
    suspend fun getTournamentById(id: String): TournamentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTournament(tournament: TournamentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTournaments(tournaments: List<TournamentEntity>)

    @Query("DELETE FROM tournaments WHERE id = :id")
    suspend fun deleteTournamentById(id: String)

    @Query("DELETE FROM tournaments")
    suspend fun deleteAllTournaments()
}

@Dao
interface UmpireDao {
    @Query("SELECT * FROM umpires WHERE isArchived = 0 ORDER BY fullName ASC")
    fun getAllUmpiresFlow(): Flow<List<com.example.data.local.entity.UmpireEntity>>

    @Query("SELECT * FROM umpires WHERE id = :umpireId LIMIT 1")
    suspend fun getUmpireById(umpireId: String): com.example.data.local.entity.UmpireEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUmpires(umpires: List<com.example.data.local.entity.UmpireEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUmpire(umpire: com.example.data.local.entity.UmpireEntity)

    @Query("SELECT * FROM umpires WHERE isArchived = 0 ORDER BY fullName ASC")
    suspend fun getAllUmpiresList(): List<com.example.data.local.entity.UmpireEntity>

    @Query("DELETE FROM umpires WHERE id = :umpireId")
    suspend fun deleteUmpireById(umpireId: String)

    @Query("DELETE FROM umpires")
    suspend fun deleteAllUmpires()

    @Query("DELETE FROM umpires WHERE LENGTH(jsonPayload) > 1800000")
    suspend fun deleteUnreadableOversizedUmpires(): Int
}

