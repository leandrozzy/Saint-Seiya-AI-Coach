package com.saintseiya.coach.core

/**
 * Classificação HEURÍSTICA por palavras-chave (PT/EN/ES) + sinais estruturais.
 * As tabelas abaixo são pontos de partida e PRECISAM ser validadas no aparelho com o jogo real;
 * por isso toda observação guarda o tipo inferido e o score (visível em Diagnóstico).
 */
object ScreenClassifier {
    private val levelRegex = Regex("(?:^|[^\\p{L}])(?:lv|lvl|nv|nivel|nível|level)\\.?\\s*(\\d{1,3})(?!\\d)", RegexOption.IGNORE_CASE)

    val keywords: Map<ScreenKind, List<String>> = mapOf(
        ScreenKind.KNIGHT_ROSTER to listOf("cavaleiros", "caballeros", "knights", "saints", "colecao", "collection", "coleccion", "ordenar", "sort", "filtrar", "filter"),
        ScreenKind.KNIGHT_DETAIL to listOf("habilidades", "skills", "habilidad", "atributos", "attributes", "sentido", "sense", "armadura", "armor", "evoluir", "evolve", "ascender", "awaken"),
        ScreenKind.TEAM_FORMATION to listOf("formacao", "formation", "formacion", "equipe", "team", "equipo", "lineup", "escalar", "deploy"),
        ScreenKind.COSMOS to listOf("cosmo", "cosmos", "solar", "lunar", "estelar", "stellar", "lendario", "legendary"),
        ScreenKind.INVENTORY to listOf("inventario", "inventory", "mochila", "backpack", "bag", "materiais", "materials", "itens", "items"),
        ScreenKind.SUMMON_BANNER to listOf("invocacao", "summon", "invocacion", "banner", "recrutar", "recruit", "probabilidade", "rate up", "rate-up"),
        ScreenKind.PVP_LOBBY to listOf("pvp", "arena", "duelo galactico", "galactic duel", "ranking", "ranked", "classificatoria"),
        ScreenKind.PICK_BAN to listOf("pick", "ban", "banir", "banido", "escolher", "proibir"),
        ScreenKind.ENEMY_PREVIEW to listOf("adversario", "oponente", "opponent", "enemy", "inimigo", "rival", "poder do oponente"),
        ScreenKind.BATTLE to listOf("auto", "pausar", "pause", "rodada", "round", "turno", "turn", "ultimate", "skip"),
        ScreenKind.BATTLE_RESULT to listOf("vitoria", "victory", "victoria", "derrota", "defeat", "recompensas", "rewards", "resultado", "result"),
        ScreenKind.EVENTS to listOf("evento", "event", "evento", "missao", "quest", "mission", "diaria", "daily", "semanal", "weekly"),
        ScreenKind.LOADING to listOf("iniciar jogo", "start game", "repair client", "verificando arquivos", "checking resources", "carregando", "loading", "atualizando recursos", "baixando"),
        ScreenKind.HOME to listOf("legiao", "legion", "santuario", "sanctuary", "loja", "shop", "aventura", "adventure", "sorteio"),
    )

    fun levelCount(lines: List<TextLine>) = lines.count { levelRegex.containsMatchIn(it.text) }
    fun levelOf(text: String): Int? = levelRegex.find(text)?.groupValues?.get(1)?.toIntOrNull()
    /** Texto da linha antes do token de nível ("Seiya Lv.80" -> "Seiya"). */
    fun textBeforeLevel(text: String): String = levelRegex.find(text)?.let { text.substring(0, it.range.first + (if (it.value.firstOrNull()?.isLetter() == false) 1 else 0)).trim() } ?: text

    fun classify(lines: List<TextLine>): Pair<ScreenKind, Double> {
        if (lines.isEmpty()) return ScreenKind.UNKNOWN to 0.0
        val folded = lines.map { Norm.fold(it.text) }
        val scores = HashMap<ScreenKind, Double>()
        for ((kind, words) in keywords) {
            var s = 0.0
            for (w in words) {
                // palavra isolada (evita "ban" em "banner")
                val re = Regex("(^|[^a-z])" + Regex.escape(w) + "([^a-z]|$)")
                if (folded.any { re.containsMatchIn(it) }) s += 1.0
            }
            scores[kind] = s
        }
        // palavras muito específicas de login/atualização valem por duas
        if (folded.any { it.contains("iniciar jogo") || it.contains("repair client") || it.contains("start game") }) scores.merge(ScreenKind.LOADING, 1.0, Double::plus)
        val lv = levelCount(lines)
        // sinais estruturais
        if (lv >= 3) scores.merge(ScreenKind.KNIGHT_ROSTER, 2.0, Double::plus)
        if (lv in 1..3) scores.merge(ScreenKind.KNIGHT_DETAIL, 0.5, Double::plus)
        if (lv in 4..8) scores.merge(ScreenKind.TEAM_FORMATION, 0.5, Double::plus)
        // resultado de batalha tem prioridade sobre o resto quando bem evidente
        val best = scores.maxByOrNull { it.value } ?: return ScreenKind.UNKNOWN to 0.0
        if (best.value < 2.0) return ScreenKind.UNKNOWN to best.value
        return best.key to best.value
    }
}
