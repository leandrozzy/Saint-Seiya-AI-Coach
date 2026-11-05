package com.saintseiya.coach.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * IMPORTANTE (atualizações do app): NUNCA usar fallbackToDestructiveMigration.
 * Ao subir `version`, escrever Migration explícita — os dados do usuário sobrevivem às atualizações.
 */
@Database(
    entities = [
        AccountEntity::class, SessionEntity::class, ObservationEntity::class, KnightEntity::class,
        KnightHistoryEntity::class, ResourceEntity::class, IssueEntity::class, BattleEntity::class,
        TeamEntity::class, RecommendationLogEntity::class, CatalogEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class CoachDb : RoomDatabase() {
    abstract fun dao(): Dao

    companion object {
        @Volatile private var inst: CoachDb? = null
        fun get(ctx: Context): CoachDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, CoachDb::class.java, "coach.db")
                .addMigrations(*MIGRATIONS)
                .build().also { inst = it }
        }
        val MIGRATIONS: Array<androidx.room.migration.Migration> = arrayOf()
    }
}
