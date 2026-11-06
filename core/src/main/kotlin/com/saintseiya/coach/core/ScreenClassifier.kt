package com.saintseiya.coach.core

/**
 * Classificação HEURÍSTICA por palavras-chave (PT/EN/ES) + sinais estruturais.
 * As tabelas abaixo são pontos de partida e PRECISAM ser validadas no aparelho com o jogo real;
 * por isso toda observação guarda o tipo inferido e o score (visível em Diagnóstico).
 */
object ScreenClassifier {
    private val levelRegex = Regex("(?:^|[^\\p{L}])(?:lv|lvl|nv|nivel|nível|level)\\.?\\s*(\\d{1,3})(?!\\d)", RegexOption.IGNORE_CASE)

    val keywords: Map<ScreenKind, List<String>> = mapOf(
        // Gerenciar (grade de cartas + "Total possuído") — medido no vídeo do jogo (UI em PT-BR)
        ScreenKind.KNIGHT_ROSTER to listOf("gerenciar", "total possuido", "casa dos cavaleiros", "desbloquear", "raridade", "configuracoes", "informacoes", "cavaleiros", "knights", "saints", "caballeros", "collection"),
        // Aprimorar e abas (Renascimento, Sétimo Sentido, Habilidade, Vínculo)
        ScreenKind.KNIGHT_DETAIL to listOf("aprimorar", "atributos basicos", "atributos de luta", "subir de nivel", "renascimento", "setimo sentido", "oitavo sentido", "vinculo", "estatisticas", "atributo", "skills", "attributes"),
        ScreenKind.TEAM_FORMATION to listOf("formacao", "formacoes", "salvar", "colocar", "formation", "lineup", "equipe", "team"),
        ScreenKind.COSMOS to listOf("cosmos", "solar", "lunar", "estelar", "stellar", "lendario", "codice do cosmo"),
        ScreenKind.INVENTORY to listOf("inventario", "qtde", "vender", "materiais", "fragmentos", "inventory", "backpack"),
        ScreenKind.SUMMON_BANNER to listOf("invocacao", "invocar", "invocacao tematica", "tentativa", "summon", "banner", "rate up", "recrutar"),
        ScreenKind.PVP_LOBBY to listOf("pvp", "arena de honra", "duelo galactico", "galactic duel", "ranking", "classificatoria", "jxj"),
        ScreenKind.PICK_BAN to listOf("pick", "ban", "banir", "banido", "proibir"),
        ScreenKind.ENEMY_PREVIEW to listOf("adversario", "oponente", "opponent", "enemy", "inimigo", "rival", "poder do oponente"),
        ScreenKind.BATTLE to listOf("ataque basico", "passiva", "bloqueado", "rodada", "pular", "pausar", "turno", "ultimate", "skip"),
        ScreenKind.BATTLE_RESULT to listOf("win", "lose", "vitoria", "derrota", "victory", "defeat", "dados", "recompensas obtidas", "recompensas", "resultado"),
        ScreenKind.EVENTS to listOf("evento", "event", "missao", "quest", "mission", "diaria", "daily", "semanal", "weekly", "atividade", "gato da fortuna"),
        ScreenKind.LOADING to listOf("iniciar jogo", "start game", "repair client", "verificando arquivos", "checking resources", "carregando", "loading", "atualizando recursos", "baixando"),
        ScreenKind.HOME to listOf("desenvolvimento", "fortaleza", "codices", "mercado", "legiao", "campanha", "diario", "cidadela", "forum", "festa de castelo"),
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
        // 3+ rótulos "lv.NN" = grade de cartas (Gerenciar ou Formação); as palavras-chave desempatam
        if (lv >= 3) { scores.merge(ScreenKind.KNIGHT_ROSTER, 1.0, Double::plus); scores.merge(ScreenKind.TEAM_FORMATION, 1.0, Double::plus) }
        // resultado de batalha tem prioridade sobre o resto quando bem evidente
        val best = scores.maxByOrNull { it.value } ?: return ScreenKind.UNKNOWN to 0.0
        if (best.value < 2.0) return ScreenKind.UNKNOWN to best.value
        return best.key to best.value
    }
}
