package com.saintseiya.coach.core

data class TeamSuggestion(
    val members: List<KnightState>,
    val bench: List<KnightState>,
    val explanation: List<String>,
    val warnings: List<String>,
    val confidence: Double,
    val complete: Boolean,
)

object TeamBuilder {
    private val template = listOf(Role.TANK, Role.HEALER, Role.CONTROL, Role.SUPPORT, Role.DAMAGE, Role.DAMAGE)

    /** Usa SOMENTE cavaleiros CONFIRMED. Nível é o único proxy de força disponível hoje (declarado). */
    fun build(owned: List<KnightState>): TeamSuggestion {
        val pool = owned.filter { it.status == KnightStatus.CONFIRMED }
            .sortedWith(compareByDescending<KnightState> { it.level ?: -1 }.thenBy { it.key })
        val warnings = ArrayList<String>()
        val why = ArrayList<String>()
        if (pool.isEmpty()) {
            return TeamSuggestion(emptyList(), emptyList(), listOf("Nenhum cavaleiro confirmado como possuído."),
                listOf("Faça uma leitura passando pela lista de cavaleiros."), 0.0, false)
        }
        val chosen = LinkedHashSet<KnightState>()
        val roleKnown = pool.count { it.role != null }
        if (roleKnown > 0) {
            for (r in template) {
                val pick = pool.firstOrNull { it.role == r && it !in chosen } ?: continue
                chosen += pick
                why += "${pick.displayName}: ${r.label} (função informada), Lv.${pick.level ?: "?"}."
            }
        }
        for (k in pool) {
            if (chosen.size >= 6) break
            if (k !in chosen) {
                chosen += k
                why += "${k.displayName}: escolhido por nível (Lv.${k.level ?: "?"}) — função ${k.role?.label ?: "desconhecida"}."
            }
        }
        val members = chosen.toList().take(6)
        val bench = pool.filter { it !in members }.take(4)
        if (roleKnown == 0) warnings += "Funções desconhecidas: defina a função dos cavaleiros para uma equipe equilibrada."
        else if (roleKnown < members.size) warnings += "${members.size - members.count { it.role != null }} membro(s) sem função definida."
        if (members.size < 6) warnings += "Apenas ${members.size} cavaleiro(s) confirmado(s): equipe incompleta."
        if (members.any { it.level == null }) warnings += "Há membros sem nível lido."
        warnings += "Sem dados de habilidades/atributos/Cosmos: recomendação baseada em nível e função informada."
        val conf = (0.25 + 0.35 * roleKnown.coerceAtMost(6) / 6.0).let { if (members.size < 6) it * 0.7 else it }
        return TeamSuggestion(members, bench, why, warnings, conf, members.size == 6)
    }
}

data class Advice(
    val id: String,
    val title: String,
    val why: String,
    val dataUsed: String,
    val cost: String,
    val benefit: String,
    val confidence: Double,
    val priority: Int,
)

object Recommender {
    fun advise(
        knights: List<KnightState>,
        pendingIssues: Int,
        screensVisited: Set<ScreenKind>,
        now: Long,
        staleMs: Long = 7L * 24 * 3600 * 1000,
    ): List<Advice> {
        val out = ArrayList<Advice>()
        val confirmed = knights.filter { it.status == KnightStatus.CONFIRMED }
        if (confirmed.isEmpty()) {
            out += Advice("first-read", "Faça sua primeira leitura",
                "Ainda não há cavaleiros confirmados; sem eles não dá para montar equipes nem planos.",
                "Nenhum dado de conta", "Gratuito, ~3 min", "Habilita equipes e evolução", 0.9, 100)
        }
        val missing = coverageChecklist.filter { it.first !in screensVisited }
        if (missing.isNotEmpty() && confirmed.isNotEmpty()) {
            out += Advice("visit", "Visite: ${missing.joinToString { it.second }}",
                "Essas telas ainda não foram lidas; faltam dados para recomendações melhores.",
                "Cobertura ${coveragePercent(screensVisited)}%", "Gratuito", "Mais dados confiáveis", 0.8, 80)
        }
        if (pendingIssues > 0) {
            out += Advice("review", "Revisar $pendingIssues pendência(s) de leitura",
                "Leituras ambíguas ou conflitantes não foram aplicadas.", "Pendências abertas", "~1 min", "Dados mais corretos", 0.8, 90)
        }
        val team = TeamBuilder.build(knights).members
        val maxLv = team.mapNotNull { it.level }.maxOrNull()
        if (maxLv != null) {
            team.filter { it.level != null && maxLv - it.level >= 8 }.sortedBy { it.level }.take(3).forEach { k ->
                out += Advice("lvl-${k.key}", "Subir ${k.displayName} (Lv.${k.level} → ~Lv.$maxLv)",
                    "Está ${maxLv - k.level!!} níveis abaixo do mais forte da equipe sugerida; é o elo mais fraco por nível.",
                    "Níveis lidos (equipe sugerida)", "Desconhecido — leia a tela de materiais/recursos",
                    "Equipe mais uniforme (proxy: nível)", 0.35, 50 + (maxLv - k.level!!).coerceAtMost(30))
            }
        }
        val newest = confirmed.maxOfOrNull { it.lastSeenAt }
        if (newest != null && now - newest > staleMs) {
            out += Advice("stale", "Dados desatualizados", "Última leitura há mais de 7 dias.",
                "Data da última observação", "Gratuito", "Evita conselhos obsoletos", 0.9, 70)
        }
        return out.sortedByDescending { it.priority }
    }

    val coverageChecklist: List<Pair<ScreenKind, String>> = listOf(
        ScreenKind.KNIGHT_ROSTER to "Lista de cavaleiros",
        ScreenKind.KNIGHT_DETAIL to "Ficha de um cavaleiro",
        ScreenKind.TEAM_FORMATION to "Formação de equipe",
        ScreenKind.COSMOS to "Cosmos",
        ScreenKind.INVENTORY to "Inventário",
    )

    /** Cobertura = telas do checklist já lidas / total do checklist (métrica explícita). */
    fun coveragePercent(visited: Set<ScreenKind>): Int =
        coverageChecklist.count { it.first in visited } * 100 / coverageChecklist.size
}
