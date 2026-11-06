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
        KnightHistoryEntity::class, SkillEntity::class, ResourceEntity::class, IssueEntity::class, BattleEntity::class,
        TeamEntity::class, RecommendationLogEntity::class, CatalogEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class CoachDb : RoomDatabase() {
    abstract fun dao(): CoachDao

    companion object {
        @Volatile private var inst: CoachDb? = null
        fun get(ctx: Context): CoachDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, CoachDb::class.java, "coach.db")
                .addMigrations(*MIGRATIONS)
                .build().also { inst = it }
        }
        /** 1→2: estrelas, raridade e atributos básicos do cavaleiro (colunas anuláveis; dados existentes preservados). */
        val MIGRATIONS: Array<androidx.room.migration.Migration> = arrayOf(
            object : androidx.room.migration.Migration(1, 2) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    for (c in listOf("stars INTEGER", "rarity TEXT", "pv INTEGER", "atqC INTEGER", "defC INTEGER", "atqF INTEGER", "defF INTEGER", "vel INTEGER"))
                        db.execSQL("ALTER TABLE knight ADD COLUMN $c")
                }
            },
            // 2→3: habilidades por cavaleiro (tabela nova; nada é apagado)
            object : androidx.room.migration.Migration(2, 3) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS knight_skill (accountId INTEGER NOT NULL, knightKey TEXT NOT NULL, skillKey TEXT NOT NULL, name TEXT NOT NULL, level INTEGER NOT NULL, maxLevel INTEGER NOT NULL, observedAt INTEGER NOT NULL, PRIMARY KEY(accountId, knightKey, skillKey))")
                }
            },
        )
    }
}
