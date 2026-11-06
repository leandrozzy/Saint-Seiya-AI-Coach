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

    fun analyze(lines: List<TextLine>, imgW: Int, imgH: Int): ScreenAnalysis {
        var (kind, score) = ScreenClassifier.classify(lines)
        // Rede de segurança: tela sem palavra-chave reconhecida mas com 2+ pares nome+nível => provável tela de coleção.
        // (Só quando NENHUM tipo foi reconhecido; telas de adversário/banner/PvP seguem a classificação.)
        if (kind == ScreenKind.UNKNOWN && pairNamesWithLevels(lines, imgW, imgH).count { it.level != null } >= 2) {
            kind = ScreenKind.KNIGHT_ROSTER; score = 1.0
        }
        val knights = if (kind.ownData) pairNamesWithLevels(lines, imgW, imgH) else emptyList()
        val resources = if (kind == ScreenKind.INVENTORY || kind == ScreenKind.HOME || kind == ScreenKind.SUMMON_BANNER) readResources(lines) else emptyList()
        val outcome = if (kind == ScreenKind.BATTLE_RESULT) {
            val f = lines.map { Norm.fold(it.text) }
            val win = f.any { it.contains("vitoria") || it.contains("victory") || it.contains("victoria") }
            val lose = f.any { it.contains("derrota") || it.contains("defeat") }
            when { win && !lose -> "VICTORY"; lose && !win -> "DEFEAT"; else -> null }
        } else null
        return ScreenAnalysis(kind, score, knights, resources, outcome, lines.size)
    }

    /** Também usado para detectar nomes em telas "não-próprias" contra o catálogo (ver Reconciler). */
    fun allNameCandidates(lines: List<TextLine>): List<String> =
        lines.map { it.text.trim() }.filter { isNameLike(it) }

    fun pairNamesWithLevels(lines: List<TextLine>, imgW: Int, imgH: Int): List<KnightReading> {
        val diag = hypot(imgW.toDouble(), imgH.toDouble()).coerceAtLeast(1.0)
        val names = lines.filter { isNameLike(it.text) }
        val used = HashSet<TextLine>()
        val out = ArrayList<KnightReading>()
        val levels = lines.filter { ScreenClassifier.levelOf(it.text) != null }
        // "Seiya Lv.80" na mesma linha
        for (lv in levels) {
            val before = ScreenClassifier.textBeforeLevel(lv.text)
            if (before.isNotBlank() && isNameLike(before)) {
                out += KnightReading(before, ScreenClassifier.levelOf(lv.text)!!, (0.85 * minOf(1f, lv.confidence).toDouble()).coerceAtLeast(0.55))
                used += lv
            }
        }
        for (lv in levels.filter { it !in used }) {
            val level = ScreenClassifier.levelOf(lv.text)!!
            val best = names.filter { it !in used }
                .map { it to hypot(it.cx - lv.cx, it.cy - lv.cy) / diag }
                .filter { (n, d) -> d < 0.18 && n.cy <= lv.cy + lv.height * 2 }
                .minByOrNull { it.second } ?: continue
            used += best.first
            val conf = (0.9 - best.second * 2).coerceIn(0.4, 0.9) * minOf(1f, (best.first.confidence + lv.confidence) / 2f).toDouble().coerceAtLeast(0.5)
            out += KnightReading(best.first.text.trim(), level, conf)
        }
        // 2ª passada (fichas): o nome costuma ser o texto MAIOR da tela, longe do nível. Confiança menor.
        for (lv in levels.filter { it !in used }) {
            val level = ScreenClassifier.levelOf(lv.text)!!
            val best = names.filter { it !in used && hypot(it.cx - lv.cx, it.cy - lv.cy) / diag < 0.35 }
                .maxByOrNull { it.height } ?: continue
            used += best
            out += KnightReading(best.text.trim(), level, 0.55)
        }
        // nomes sem nível: leitura sem nível (NÃO conta como posse)
        for (n in names) if (n !in used) out += KnightReading(n.text.trim(), null, 0.4)
        return out
    }

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
