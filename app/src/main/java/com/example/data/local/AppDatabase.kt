package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.BallEventDao
import com.example.data.local.dao.MatchDao
import com.example.data.local.dao.PlayerDao
import com.example.data.local.dao.TeamDao
import com.example.data.local.dao.TournamentDao
import com.example.data.local.entity.BallEventEntity
import com.example.data.local.entity.MatchEntity
import com.example.data.local.entity.PlayerEntity
import com.example.data.local.entity.TeamEntity
import com.example.data.local.entity.TournamentEntity

@Database(
    entities = [
        TeamEntity::class,
        PlayerEntity::class,
        MatchEntity::class,
        BallEventEntity::class,
        TournamentEntity::class,
        com.example.data.local.entity.UmpireEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun teamDao(): TeamDao
    abstract fun playerDao(): PlayerDao
    abstract fun matchDao(): MatchDao
    abstract fun ballEventDao(): BallEventDao
    abstract fun tournamentDao(): TournamentDao
    abstract fun umpireDao(): com.example.data.local.dao.UmpireDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "csm_bimpha_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
