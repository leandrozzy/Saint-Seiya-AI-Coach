package com.saintseiya.coach.core

import kotlin.math.hypot

/** Converte linhas de OCR em leituras estruturadas. Não decide posse: isso é do [Reconciler]. */
object ScreenAnalyzer {
    private val stop = setOf(
        "cavaleiros", "caballeros", "knights", "saints", "todos", "all", "filtrar", "filter", "ordenar", "sort",
        "nivel", "level", "poder", "power", "habilidades", "skills", "equipar", "equip", "voltar", "back", "auto",
        "evoluir", "evolve", "confirmar", "confirm", "cancelar", "cancel", "formacao", "formation", "equipe", "team",
        "parar", "coach", "abrir", "iniciar", "encerrar", "inventario", "inventory", "vitoria", "victory", "derrota", "defeat", "loja", "shop", "evento", "event",
    )
    private val nameRegex = Regex("^[\\p{L}][\\p{L}'’\\- ]{2,27}$")
    private val qtyRegex = Regex("^[x×]\\s?(\\d[\\d.,]*[kKmMbB]?)$")
    private val inlineRegex = Regex("^([\\p{L}][\\p{L} '’\\-]{2,30}?)\\s*[:：]?\\s+(\\d[\\d.,]*[kKmMbB]?)$")

    fun isNameLike(t: String): Boolean {
        val s = t.trim()
        if (!nameRegex.matches(s)) return false
        if (Norm.fold(s) in stop) return false
        if (ScreenClassifier.levelOf(s) != null) return false
        return true
    }

    private val totalOwned = Regex("total\\s*possu[^\\d]{0,12}(\\d{1,4})", RegexOption.IGNORE_CASE)
    private val qtde = Regex("qtde\\W*(\\d[\\d.,]*)", RegexOption.IGNORE_CASE)

    /**
     * `stage` = faixa vertical do nome lida pelo app (ver [Stage]). Nas telas de cavaleiro (Gerenciar/Aprimorar...) o jogo NÃO
     * mostra nomes na grade: o nome vem da faixa, o nível ("Nível N") e os atributos do painel Aprimorar.
     */
    fun analyze(lines: List<TextLine>, imgW: Int, imgH: Int, stage: StageStrip? = null): ScreenAnalysis {
        var (kind, score) = ScreenClassifier.classify(lines)
        val resources = ArrayList<ResourceReading>()
        val panelLevel = Stage.panelLevel(lines, imgW, imgH)
        val stats = Stage.basicStats(lines, imgW, imgH)
        // A faixa vertical do nome + estrelas é o sinal visual mais confiável das telas de cavaleiro: não depende de palavras-chave.
        val stageName = stage?.name?.let { Stage.cleanName(it) }
        if (kind == ScreenKind.UNKNOWN && stageName != null && stageName.length >= 6 && stage.stars != null) {
            val f = lines.map { Norm.fold(it.text) }
            val grid = ScreenClassifier.levelCount(lines) >= 3 || f.any { it.contains("possu") || it.contains("gerenciar") || it.contains("desbloquear") }
            kind = if (grid) ScreenKind.KNIGHT_ROSTER else ScreenKind.KNIGHT_DETAIL; score = 2.0
        }

        val knights = ArrayList<KnightReading>()
        if (kind == ScreenKind.KNIGHT_ROSTER || kind == ScreenKind.KNIGHT_DETAIL) {
            val name = stage?.name?.let { Stage.cleanName(it) }
            if (name != null) {
                // posse só com NÍVEL lido no painel (a grade "Gerenciar" também lista cavaleiros ainda não obtidos)
                val strong = panelLevel != null && stats.size >= 3
                val conf = (stage.confidence.coerceIn(0.0, 1.0) * 0.5 + (if (panelLevel != null) 0.4 else 0.1) + (if (strong) 0.1 else 0.0)).coerceIn(0.0, 1.0)
                knights += KnightReading(name, panelLevel, conf, stage.stars, Stage.cleanRarity(stage.rarity), stats, strong, Stage.skills(lines, imgW, imgH))
            }
        }
        // "Total possuído: 113" (rodapé de Gerenciar): métrica oficial do jogo para medir a cobertura
        lines.firstNotNullOfOrNull { totalOwned.find(it.text) }?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..9999 }
            ?.let { resources += ResourceReading(TOTAL_OWNED_LABEL, it.toLong(), 0.85) }
        // Inventário: "QTDE: 5" ao lado do nome do item selecionado
        if (kind == ScreenKind.INVENTORY || lines.any { qtde.containsMatchIn(it.text) }) resources += readQtde(lines, imgH)
        if (kind == ScreenKind.HOME || kind == ScreenKind.SUMMON_BANNER) resources += readResources(lines)
        val outcome = if (kind == ScreenKind.BATTLE_RESULT) {
            val f = lines.map { Norm.fold(it.text) }
            fun has(vararg w: String) = f.any { l -> w.any { Regex("(^|[^a-z])$it([^a-z]|$)").containsMatchIn(l) } }
            val win = has("win", "venceu", "vitoria", "victory", "victoria"); val lose = has("lose", "derrota", "defeat", "fracasso")
            when { win && !lose -> "VICTORY"; lose && !win -> "DEFEAT"; else -> null }
        } else null
        return ScreenAnalysis(kind, score, knights, resources, outcome, lines.size)
    }

    const val TOTAL_OWNED_LABEL = "Cavaleiros possuídos (total informado pelo jogo)"

    private fun readQtde(lines: List<TextLine>, imgH: Int): List<ResourceReading> {
        val out = ArrayList<ResourceReading>()
        for (l in lines) {
            val m = qtde.find(l.text) ?: continue
            val v = Norm.parseNumber(m.groupValues[1]) ?: continue
            // o nome do item fica acima (ou na mesma linha, antes de QTDE)
            val before = l.text.substring(0, m.range.first).trim()
            val name = if (before.length >= 3 && isNameLike(before)) before else
                lines.filter { it !== l && isNameLike(it.text) && it.cy < l.cy && l.cy - it.cy < 0.18 * imgH && kotlin.math.abs(it.left - l.left) < 0.12 * imgH * 3 }
                    .minByOrNull { l.cy - it.cy }?.text?.trim()
            if (name != null) out += ResourceReading(name, v, 0.65)
        }
        return out
    }

    /** Também usado para detectar nomes em telas "não-próprias" contra o catálogo (ver Reconciler). */
    fun allNameCandidates(lines: List<TextLine>): List<String> =
        lines.map { it.text.trim() }.filter { isNameLike(it) }

    fun readResources(lines: List<TextLine>): List<ResourceReading> {
        val out = LinkedHashMap<String, ResourceReading>()
        for (l in lines) {
            val t = l.text.trim()
            inlineRegex.matchEntire(t)?.let { m ->
                val label = m.groupValues[1].trim()
                if (Norm.fold(label) !in stop) Norm.parseNumber(m.groupValues[2])?.let { v ->
                    out[Norm.key(label)] = ResourceReading(label, v, 0.7)
                }
            }
            qtyRegex.matchEntire(t)?.let { m ->
                val v = Norm.parseNumber(m.groupValues[1]) ?: return@let
                val label = lines.filter { isNameLike(it.text) && it.cy <= l.cy + l.height }
                    .minByOrNull { hypot(it.cx - l.cx, it.cy - l.cy) }
                    ?.takeIf { hypot(it.cx - l.cx, it.cy - l.cy) < l.height * 6 }
                if (label != null) out[Norm.key(label.text)] = ResourceReading(label.text.trim(), v, 0.6)
            }
        }
        return out.values.toList()
    }
}
