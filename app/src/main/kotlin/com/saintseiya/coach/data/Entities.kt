package com.saintseiya.coach.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "account")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val server: String? = null, // desconhecido = null
    val createdAt: Long,
)

@Entity(tableName = "capture_session", indices = [Index("accountId")])
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val source: String, // LIVE | IMPORT
    val startedAt: Long,
    val endedAt: Long? = null,
    val endReason: String? = null, // USER | PROJECTION_REVOKED | ERROR | DONE
    val framesSeen: Int = 0,
    val framesProcessed: Int = 0,
    val framesReadable: Int = 0,
    val knightsNew: Int = 0,
    val knightsUpdated: Int = 0,
    val resourcesUpdated: Int = 0,
    val issuesOpened: Int = 0,
)

/** Observação append-only. NÃO guarda imagem nem texto bruto (privacidade): só tipo, hash e contagens. */
@Entity(tableName = "screen_observation", indices = [Index("accountId"), Index("sessionId")])
data class ObservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val sessionId: Long,
    val observedAt: Long,
    val kind: String,
    val kindScore: Double,
    val hash: Long,
    val lineCount: Int,
    val knightsRead: Int,
    val resourcesRead: Int,
    val summary: String,
)

@Entity(tableName = "knight", primaryKeys = ["accountId", "key"])
data class KnightEntity(
    val accountId: Long,
    val key: String,
    val displayName: String,
    val level: Int?,
    val role: String?,
    val status: String,
    val confidence: Double,
    val sightings: Int,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val sourceObservationId: Long?,
    val levelLocked: Boolean,
    val version: Int,
    val updatedAt: Long,
    // v2 (lidos do painel Aprimorar; null = desconhecido, nunca 0)
    val stars: Int? = null,
    val rarity: String? = null,
    val pv: Int? = null,
    val atqC: Int? = null,
    val defC: Int? = null,
    val atqF: Int? = null,
    val defF: Int? = null,
    val vel: Int? = null,
)

@Entity(tableName = "knight_history", indices = [Index("accountId", "key")])
data class KnightHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val key: String,
    val field: String,
    val oldValue: String?,
    val newValue: String?,
    val origin: String, // OCR | MANUAL
    val observationId: Long?,
    val at: Long,
)

@Entity(tableName = "resource", primaryKeys = ["accountId", "key"])
data class ResourceEntity(
    val accountId: Long,
    val key: String,
    val label: String,
    val value: Long,
    val confidence: Double,
    val observedAt: Long,
    val sourceObservationId: Long?,
)

@Entity(tableName = "issue", indices = [Index("accountId")])
data class IssueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val kind: String,
    val detail: String,
    val observationId: Long?,
    val createdAt: Long,
    val resolved: Boolean = false,
)

@Entity(tableName = "battle", indices = [Index("accountId")])
data class BattleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val observedAt: Long,
    val outcome: String?, // VICTORY | DEFEAT | null (desconhecido)
    val observationId: Long,
    val sessionId: Long,
)

@Entity(tableName = "team", indices = [Index("accountId")])
data class TeamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val name: String,
    val version: Int,
    val memberKeys: String, // chaves separadas por vírgula, em ordem
    val note: String?,
    val createdAt: Long,
)

@Entity(tableName = "recommendation_log", indices = [Index("accountId")])
data class RecommendationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val adviceId: String,
    val title: String,
    val dataUsed: String,
    val confidence: Double,
    val shownAt: Long,
)

/** Catálogo aprendido (nomes confirmados). Definições globais; atualizável por pacote de conhecimento no futuro. */
@Entity(tableName = "catalog")
data class CatalogEntity(
    @PrimaryKey val key: String,
    val name: String,
    val source: String, // LEARNED | PACK
    val addedAt: Long,
)

/** Habilidade de um cavaleiro (aba "Habilidade"). Nome pode vir truncado pelo jogo ("Espada Sagrada.S..."). */
@Entity(tableName = "knight_skill", primaryKeys = ["accountId", "knightKey", "skillKey"])
data class SkillEntity(
    val accountId: Long,
    val knightKey: String,
    val skillKey: String,
    val name: String,
    val level: Int,
    val maxLevel: Int,
    val observedAt: Long,
)
