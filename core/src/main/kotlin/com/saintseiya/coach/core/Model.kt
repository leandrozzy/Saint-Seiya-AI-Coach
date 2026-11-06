package com.saintseiya.coach.core

/** Tipo de tela do jogo reconhecido por heurística de texto (ver [ScreenClassifier]). */
enum class ScreenKind(val ownData: Boolean, val label: String) {
    HOME(false, "Início"),
    KNIGHT_ROSTER(true, "Lista de cavaleiros"),
    KNIGHT_DETAIL(true, "Ficha de cavaleiro"),
    TEAM_FORMATION(true, "Formação de equipe"),
    COSMOS(false, "Cosmos"),
    INVENTORY(false, "Inventário"),
    SUMMON_BANNER(false, "Invocação/Banner"),
    PVP_LOBBY(false, "PvP"),
    PICK_BAN(false, "Pick & Ban"),
    ENEMY_PREVIEW(false, "Adversário"),
    BATTLE(false, "Batalha"),
    BATTLE_RESULT(false, "Resultado de batalha"),
    EVENTS(false, "Eventos"),
    LOADING(false, "Login / atualização"),
    UNKNOWN(false, "Não reconhecida"),
}

enum class KnightStatus { CANDIDATE, CONFIRMED, SEEN_ELSEWHERE, REJECTED }

enum class Role(val label: String) {
    TANK("Tanque"), SUPPORT("Suporte"), HEALER("Curandeiro"), CONTROL("Controle"), DAMAGE("Dano")
}

data class TextLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val confidence: Float = 1f,
) {
    val cx get() = (left + right) / 2.0
    val cy get() = (top + bottom) / 2.0
    val height get() = (bottom - top).coerceAtLeast(1)
}

/** Leitura de um cavaleiro. `strong` = nível E atributos lidos no painel "Aprimorar" (prova forte de posse). */
data class KnightReading(
    val rawName: String,
    val level: Int?,
    val confidence: Double,
    val stars: Int? = null,
    val rarity: String? = null,
    val stats: Map<String, Int> = emptyMap(),
    val strong: Boolean = false,
    val skills: List<SkillReading> = emptyList(),
)

/** Habilidade lida na aba "Habilidade": nome (pode vir truncado pelo jogo) e nível atual/máximo. */
data class SkillReading(val name: String, val level: Int, val max: Int)

/** Faixa vertical do nome (lida pelo app girando o recorte), raridade (letra) e estrelas (cor). */
data class StageStrip(val name: String?, val rarity: String?, val stars: Int?, val confidence: Double)
data class ResourceReading(val label: String, val value: Long, val confidence: Double)

data class ScreenAnalysis(
    val kind: ScreenKind,
    val kindScore: Double,
    val knights: List<KnightReading>,
    val resources: List<ResourceReading>,
    val battleOutcome: String?, // "VICTORY" | "DEFEAT" | null
    val lineCount: Int,
)

/** Estado materializado de um cavaleiro. Campos nulos = DESCONHECIDO (nunca zero). */
data class KnightState(
    val key: String,
    val displayName: String,
    val level: Int? = null,
    val role: Role? = null,
    val status: KnightStatus,
    val confidence: Double = 0.0,
    val sightings: Int = 0,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val sourceObservationId: Long? = null,
    val levelLocked: Boolean = false, // corrigido manualmente: OCR não sobrescreve
    val stars: Int? = null,
    val rarity: String? = null,
    val pv: Int? = null,
    val atqC: Int? = null,
    val defC: Int? = null,
    val atqF: Int? = null,
    val defF: Int? = null,
    val vel: Int? = null,
)

data class Change(val field: String, val old: String?, val new: String?)
data class IssueDraft(val kind: String, val detail: String)
data class MergeResult(val state: KnightState?, val changes: List<Change>, val issues: List<IssueDraft>)
