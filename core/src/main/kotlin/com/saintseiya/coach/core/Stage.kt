package com.saintseiya.coach.core

/**
 * Geometria da tela de cavaleiro (Gerenciar / Aprimorar / Sétimo Sentido / ...), medida em vídeo real 2448x1080
 * e expressa em FRAÇÕES da largura/altura (vale para qualquer resolução 21:9 / 16:9 do mesmo layout).
 * O nome do cavaleiro está em texto VERTICAL (lido de cima para baixo): o app gira o recorte 90° anti-horário.
 */
object Stage {
    // (left, top, right, bottom)
    val NAME = floatArrayOf(0.452f, 0.170f, 0.482f, 0.715f)
    val RARITY = floatArrayOf(0.438f, 0.050f, 0.497f, 0.178f)
    const val STAR_X = 0.4872f
    val STAR_Y = floatArrayOf(0.190f, 0.230f, 0.269f, 0.307f, 0.347f, 0.387f)

    /** Estrela acesa = dourada. */
    fun isGold(r: Int, g: Int, b: Int) = r > 190 && g > 140 && b < 135 && r - b > 85

    /** Estrelas acendem de cima para baixo; conta as acesas. null se nenhuma (ex.: flash de brilho). */
    fun starsFrom(gold: BooleanArray): Int? {
        val n = gold.count { it }
        return if (n in 1..6) n else null
    }

    private val nameOk = Regex("^[\\p{L}][\\p{L}'’.\\- ]{3,44}$")
    private val rarities = setOf("SS", "S", "A", "B", "C")

    /** Limpa ruído típico do OCR (| / [ ] dígitos soltos) e valida. */
    fun cleanName(raw: String): String? {
        val s = raw.replace(Regex("[|/\\\\\\[\\]{}()_=*<>~`^]"), " ").replace(Regex("\\d+"), " ")
            .replace(Regex("\\s+"), " ").trim()
        if (!nameOk.matches(s)) return null
        if (s.split(' ').count { it.length >= 2 } < 1) return null
        return s
    }

    fun cleanRarity(raw: String?): String? {
        val t = raw?.uppercase()?.filter { it.isLetter() } ?: return null
        return t.takeIf { it in rarities }
    }

    // ---- painel de atributos ("Atributos Básicos": 3 linhas x 2 colunas) ----
    private val num = Regex("^(\\d[\\d.,]*)(?:\\s*[+\\-~]\\s*\\d[\\d.,]*)?$")
    private fun firstInt(t: String): Int? = num.matchEntire(t.trim())?.groupValues?.get(1)?.let { Norm.parseNumber(it)?.toInt() }

    /** Linha 1: PV | ATQ F.  ·  Linha 2: ATQ C. | DEF F.  ·  Linha 3: DEF C. | Veloc. (ordem fixa do jogo). */
    fun basicStats(lines: List<TextLine>, w: Int, h: Int): Map<String, Int> {
        fun hasAll(l: TextLine, vararg k: String) = Norm.fold(l.text).let { f -> k.all { f.contains(it) } }
        val top = lines.firstOrNull { it.cx > 0.5 * w && it.cy < 0.55 * h && hasAll(it, "atributos") && (Norm.fold(it.text).contains("sicos") || Norm.fold(it.text).contains("bas")) } ?: return emptyMap()
        val bottom = lines.filter { it.cx > 0.5 * w && it.cy > top.cy && hasAll(it, "atributos", "luta") }.minByOrNull { it.cy }
        val yMax = bottom?.cy ?: (top.cy + 0.30 * h)
        // cada linha pode ter só o valor ("1458"), rótulo+valor ("ATQ C. 0+14") ou a linha inteira com 2 valores
        val cand = lines.filter { it.cx > 0.5 * w && it.cy > top.cy + 0.02 * h && it.cy < yMax }
            .map { it to numbersIn(it.text) }.filter { it.second.isNotEmpty() }
        if (cand.isEmpty()) return emptyMap()
        val rows = ArrayList<MutableList<Pair<TextLine, List<Int>>>>()
        for (v in cand.sortedBy { it.first.cy }) {
            val r = rows.firstOrNull { abs(it.first().first.cy - v.first.cy) < 0.03 * h }
            if (r != null) r += v else rows += mutableListOf(v)
        }
        if (rows.size != 3) return emptyMap() // layout inesperado: não chuta
        val keysL = listOf("pv", "atqC", "defC"); val keysR = listOf("atqF", "defF", "vel")
        val out = LinkedHashMap<String, Int>()
        val split = 0.72 * w
        rows.sortedBy { it.first().first.cy }.forEachIndexed { i, r ->
            val tokens = r.sortedBy { it.first.cx }.flatMap { (l, ns) -> ns.map { l to it } }
            if (tokens.size >= 2) { out[keysL[i]] = tokens.first().second; out[keysR[i]] = tokens.last().second }
            else tokens.firstOrNull()?.let { (l, n) -> if (l.cx < split) out[keysL[i]] = n else out[keysR[i]] = n }
        }
        return out
    }

    /** Valores numéricos de uma linha: "0+14" -> 0 ; "231+14" -> 231 ; "PV 1458" -> 1458. */
    private fun numbersIn(t: String): List<Int> =
        Regex("(?<![\\p{L}\\d])(\\d[\\d.,]*)(?:\\s*[+\\-~]\\s*\\d[\\d.,]*)?(?![\\p{L}\\d])").findAll(t).mapNotNull { Norm.parseNumber(it.groupValues[1])?.toInt() }.toList()

    private val skillLevel = Regex("n[ií]vel\\W*(\\d{1,2})\\s*/\\s*(\\d{1,2})", RegexOption.IGNORE_CASE)
    private val skillName = Regex("^[\\p{L}][\\p{L}\\d'’.\\-() ]{2,40}$")

    /** Aba "Habilidade" (Detalhes da Habilidade): cada linha = nome acima de "Nível N /M" + botão Subir de Nível. */
    fun skills(lines: List<TextLine>, w: Int, h: Int): List<SkillReading> {
        val right = lines.filter { it.cx > 0.5 * w }
        val out = LinkedHashMap<String, SkillReading>()
        for (l in right) {
            val m = skillLevel.find(l.text) ?: continue
            val lv = m.groupValues[1].toInt(); val mx = m.groupValues[2].toInt()
            if (mx !in 1..99 || lv !in 0..mx) continue
            val name = right.filter { it !== l && it.cy < l.cy && l.cy - it.cy < 0.09 * h && abs((it.left - l.left).toDouble()) < 0.07 * w }
                .filter { val f = Norm.fold(it.text); !f.contains("subir") && !skillLevel.containsMatchIn(it.text) && skillName.matches(it.text.trim()) }
                .maxByOrNull { it.cy }?.text?.trim()?.trimEnd('.', '…', ' ') ?: continue
            if (name.length >= 3) out[Norm.key(name)] = SkillReading(name, lv, mx)
        }
        return out.values.toList()
    }

    private fun abs(d: Double) = if (d < 0) -d else d

    /** "Nível 11": no painel da direita; aceita "Nível" e o número em linhas separadas. */
    fun panelLevel(lines: List<TextLine>, w: Int, h: Int): Int? {
        val right = lines.filter { it.cx > 0.5 * w && it.cy < 0.45 * h }
        right.forEach { l -> ScreenClassifier.levelOf(l.text)?.takeIf { it in 1..Reconciler.MAX_LEVEL && Norm.fold(l.text).contains("nivel") }?.let { return it } }
        val lab = right.firstOrNull { Norm.fold(it.text).trim().trimEnd('.', ':') == "nivel" } ?: return null
        val n = right.filter { it !== lab && firstInt(it.text) != null && abs(it.cy - lab.cy) < lab.height * 1.5 && it.cx > lab.cx && it.cx - lab.cx < 0.15 * w }
            .minByOrNull { it.cx - lab.cx } ?: return null
        return firstInt(n.text)?.takeIf { it in 1..Reconciler.MAX_LEVEL }
    }
}
