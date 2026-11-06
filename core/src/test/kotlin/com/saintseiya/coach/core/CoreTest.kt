package com.saintseiya.coach.core

import kotlin.test.*

private fun line(t: String, x: Int, y: Int, w: Int = 200, h: Int = 30) = TextLine(t, x, y, x + w, y + h)

class TextTest {
    @Test fun numbers() {
        assertEquals(1234567L, Norm.parseNumber("1,234,567"))
        assertEquals(1234L, Norm.parseNumber("1.234"))
        assertEquals(12500L, Norm.parseNumber("12.5K"))
        assertEquals(1200000L, Norm.parseNumber("1,2M"))
        assertNull(Norm.parseNumber("abc"))
    }
    @Test fun keysIgnoreAccents() {
        assertEquals(Norm.key("Shiryū"), Norm.key("shiryu"))
        assertTrue(Norm.similarity("Seiya", "Seiyo") >= 0.8)
    }
    @Test fun hashDistance() {
        val a = IntArray(72) { it % 9 }
        val b = IntArray(72) { 8 - it % 9 }
        assertEquals(0, Hash.distance(Hash.dHash(a), Hash.dHash(a)))
        assertTrue(Hash.distance(Hash.dHash(a), Hash.dHash(b)) > 30)
    }
}

class ClassifierTest {
    private fun roster() = buildList {
        add(line("Cavaleiros", 10, 10))
        for (i in 0 until 6) { add(line("Nome$i".replace(Regex("\\d"), "x") + "yy", 100 + i * 220, 300)); add(line("Lv.${10 + i}", 100 + i * 220, 340)) }
    }
    @Test fun rosterDetectedByStructure() {
        assertEquals(ScreenKind.KNIGHT_ROSTER, ScreenClassifier.classify(roster()).first)
    }
    @Test fun emptyIsUnknown() { assertEquals(ScreenKind.UNKNOWN, ScreenClassifier.classify(emptyList()).first) }
    @Test fun banIsNotBanner() {
        val kind = ScreenClassifier.classify(listOf(line("Banner", 0, 0), line("Rate up", 0, 40))).first
        assertEquals(ScreenKind.SUMMON_BANNER, kind)
    }
    @Test fun battleResult() {
        val a = ScreenAnalyzer.analyze(listOf(line("Vitória", 0, 0), line("Recompensas", 0, 50)), 2000, 1000)
        assertEquals(ScreenKind.BATTLE_RESULT, a.kind); assertEquals("VICTORY", a.battleOutcome)
    }
}

class ReconcilerTest {
    private val now = 1000L
    private fun r(n: String, lv: Int?) = KnightReading(n, lv, 0.8)

    @Test fun enemyOrBannerNeverOwned() {
        for (k in listOf(ScreenKind.SUMMON_BANNER, ScreenKind.ENEMY_PREVIEW, ScreenKind.PICK_BAN)) {
            val res = Reconciler.mergeKnight(null, r("Seiya", 50), k, true, 1, now)
            assertEquals(KnightStatus.SEEN_ELSEWHERE, res.state!!.status)
            assertNull(res.state!!.level)
        }
    }
    @Test fun rosterWithoutLevelNotOwned() {
        val res = Reconciler.mergeKnight(null, r("Seiya", null), ScreenKind.KNIGHT_ROSTER, true, 1, now)
        assertEquals(KnightStatus.SEEN_ELSEWHERE, res.state!!.status)
    }
    @Test fun candidateThenConfirmedOnSecondSighting() {
        val a = Reconciler.mergeKnight(null, r("Seiya", 30), ScreenKind.KNIGHT_ROSTER, false, 1, now).state!!
        assertEquals(KnightStatus.CANDIDATE, a.status)
        val b = Reconciler.mergeKnight(a, r("Seiya", 30), ScreenKind.KNIGHT_DETAIL, false, 2, now + 1).state!!
        assertEquals(KnightStatus.CONFIRMED, b.status)
    }
    @Test fun catalogKnownConfirmsImmediately() {
        val a = Reconciler.mergeKnight(null, r("Seiya", 30), ScreenKind.KNIGHT_ROSTER, true, 1, now).state!!
        assertEquals(KnightStatus.CONFIRMED, a.status)
    }
    @Test fun neverEraseAndNoRegression() {
        val a = Reconciler.mergeKnight(null, r("Seiya", 30), ScreenKind.KNIGHT_ROSTER, true, 1, now).state!!
        val seenAgain = Reconciler.mergeKnight(a, r("Seiya", null), ScreenKind.ENEMY_PREVIEW, true, 2, now + 1)
        assertEquals(30, seenAgain.state!!.level)
        val lower = Reconciler.mergeKnight(a, r("Seiya", 20), ScreenKind.KNIGHT_ROSTER, true, 3, now + 2)
        assertEquals(30, lower.state!!.level); assertEquals("LEVEL_CONFLICT", lower.issues.single().kind)
        val higher = Reconciler.mergeKnight(a, r("Seiya", 35), ScreenKind.KNIGHT_ROSTER, true, 4, now + 3)
        assertEquals(35, higher.state!!.level)
    }
    @Test fun lockedLevelSurvivesOcr() {
        val a = Reconciler.mergeKnight(null, r("Seiya", 30), ScreenKind.KNIGHT_ROSTER, true, 1, now).state!!.copy(levelLocked = true, level = 31)
        assertEquals(31, Reconciler.mergeKnight(a, r("Seiya", 80), ScreenKind.KNIGHT_ROSTER, true, 2, now).state!!.level)
    }
    @Test fun idempotentSameObservationData() {
        val a = Reconciler.mergeKnight(null, r("Seiya", 30), ScreenKind.KNIGHT_ROSTER, true, 1, now).state!!
        val b = Reconciler.mergeKnight(a, r("Seiya", 30), ScreenKind.KNIGHT_ROSTER, true, 1, now).state!!
        assertEquals(a.level, b.level)
        assertTrue(Reconciler.mergeKnight(a, r("Seiya", 30), ScreenKind.KNIGHT_ROSTER, true, 1, now).changes.none { it.field == "level" })
    }
    @Test fun rejectedStaysRejected() {
        val rej = KnightState("seiya", "Seiya", status = KnightStatus.REJECTED, firstSeenAt = 0, lastSeenAt = 0)
        assertEquals(KnightStatus.REJECTED, Reconciler.mergeKnight(rej, r("Seiya", 30), ScreenKind.KNIGHT_ROSTER, true, 1, now).state!!.status)
    }
}

class TeamTest {
    private fun k(n: String, lv: Int?, role: Role? = null, st: KnightStatus = KnightStatus.CONFIRMED) =
        KnightState(Norm.key(n), n, lv, role, st, 0.8, 2, 0, 0)

    @Test fun usesOnlyConfirmed() {
        val all = listOf(k("A", 50), k("B", 40), k("C", 30, st = KnightStatus.CANDIDATE), k("D", null, st = KnightStatus.SEEN_ELSEWHERE))
        val t = TeamBuilder.build(all)
        assertEquals(setOf("A", "B"), t.members.map { it.displayName }.toSet())
        assertFalse(t.complete)
    }
    @Test fun sixWithRolesAndExplanations() {
        val all = listOf(k("T", 10, Role.TANK), k("H", 10, Role.HEALER), k("C", 10, Role.CONTROL), k("S", 10, Role.SUPPORT),
            k("D1", 50, Role.DAMAGE), k("D2", 40, Role.DAMAGE), k("D3", 90, Role.DAMAGE))
        val t = TeamBuilder.build(all)
        assertTrue(t.complete); assertEquals(6, t.members.size)
        assertEquals(6, t.explanation.size)
        assertTrue("D3" in t.members.map { it.displayName } && "D2" !in t.members.map { it.displayName })
    }
    @Test fun emptyHasZeroConfidence() { assertEquals(0.0, TeamBuilder.build(emptyList()).confidence) }
    @Test fun adviceFirstRead() {
        val adv = Recommender.advise(emptyList(), 0, emptySet(), 0)
        assertEquals("first-read", adv.first().id)
        assertEquals(0, Recommender.coveragePercent(emptySet()))
        assertEquals(50, Recommender.coveragePercent(setOf(ScreenKind.TEAM_FORMATION, ScreenKind.INVENTORY)))
    }
    @Test fun levelAdviceHasUnknownCost() {
        val t = (1..6).map { k("K$it", if (it == 1) 10 else 60) }
        val adv = Recommender.advise(t, 0, setOf(ScreenKind.KNIGHT_ROSTER), 0)
        val a = adv.first { it.id.startsWith("lvl-") }
        assertTrue(a.cost.contains("Desconhecido")); assertTrue(a.confidence < 0.5)
    }
}

class LoadingTest {
    @Test fun loginScreenRecognized() {
        assertEquals(ScreenKind.LOADING, ScreenClassifier.classify(listOf(line("Iniciar Jogo", 0, 0))).first)
        assertEquals(ScreenKind.LOADING, ScreenClassifier.classify(listOf(line("Repair Client", 0, 0), line("Verificando arquivos de recurso", 0, 40))).first)
    }
}

/** Coordenadas medidas no vídeo real (2448x1080): painel "Aprimorar" de um Guerreiro Marina nível 11. */
private const val W = 2448
private const val H = 1080
private fun at(t: String, x: Double, y: Double, w: Int = 120, h: Int = 34) = TextLine(t, (x * W).toInt() - w / 2, (y * H).toInt() - h / 2, (x * W).toInt() + w / 2, (y * H).toInt() + h / 2)

private fun aprimorar(level: String = "Nível 11") = listOf(
    at("Aprimorar", 0.14, 0.035), at("Atributo", 0.696, 0.118), at(level, 0.72, 0.223, 200),
    at("Atributos Básicos", 0.595, 0.363, 260), at("1458", 0.636, 0.43), at("231+14", 0.808, 0.43),
    at("ATQ C.", 0.58, 0.488), at("0+14", 0.652, 0.488), at("65", 0.808, 0.488),
    at("65", 0.636, 0.548), at("22", 0.808, 0.548),
    at("Atributos de Luta", 0.595, 0.632, 280), at("225", 0.81, 0.69), at("Subir de Nível", 0.025, 0.26, 150),
    at("Renascimento", 0.025, 0.4, 150), at("Sétimo Sentido", 0.025, 0.55, 150), at("Vínculo", 0.025, 0.86, 100),
)

class StageTest {
    @Test fun cleanName() {
        assertEquals("Guerreiro Marina", Stage.cleanName("Guerreiro Marina /"))
        assertEquals("Thane da Terra Flamejante", Stage.cleanName("| Thane da Terra Flamejante //"))
        assertNull(Stage.cleanName("3 A : |")); assertNull(Stage.cleanName("ab"))
        assertEquals("SS", Stage.cleanRarity("ss")); assertNull(Stage.cleanRarity("Luta"))
    }
    @Test fun stars() {
        assertTrue(Stage.isGold(240, 190, 60)); assertFalse(Stage.isGold(120, 130, 150))
        assertEquals(2, Stage.starsFrom(booleanArrayOf(true, true, false, false, false, false))); assertNull(Stage.starsFrom(BooleanArray(6)))
    }
    @Test fun aprimorarIsClassifiedAndRead() {
        val a = ScreenAnalyzer.analyze(aprimorar(), W, H, StageStrip("Guerreiro Marina", "C", 2, 0.8))
        assertEquals(ScreenKind.KNIGHT_DETAIL, a.kind)
        val k = a.knights.single()
        assertEquals("Guerreiro Marina", k.rawName); assertEquals(11, k.level); assertEquals(2, k.stars); assertEquals("C", k.rarity); assertTrue(k.strong)
        assertEquals(mapOf("pv" to 1458, "atqC" to 0, "defC" to 65, "atqF" to 231, "defF" to 65, "vel" to 22), k.stats)
    }
    @Test fun statsWhenLabelAndValueAreMerged() {
        val merged = listOf(at("Aprimorar", 0.14, 0.035), at("Atributos Básicos", 0.595, 0.363, 260),
            at("PV 1458", 0.60, 0.43, 200), at("ATQ F. 231+14", 0.80, 0.43, 260), at("ATQ C. 0+14", 0.60, 0.488, 220), at("DEF F. 65", 0.78, 0.488, 200),
            at("DEF C. 65", 0.60, 0.548, 200), at("Veloci... 22", 0.80, 0.548, 220), at("Atributos de Luta", 0.595, 0.632, 280), at("Nível 11", 0.72, 0.223, 200))
        val k = ScreenAnalyzer.analyze(merged, W, H, StageStrip("Guerreiro Marina", "C", 2, 0.8)).knights.single()
        assertEquals(mapOf("pv" to 1458, "atqC" to 0, "defC" to 65, "atqF" to 231, "defF" to 65, "vel" to 22), k.stats)
    }
    @Test fun statsWhenWholeRowIsOneLine() {
        val rows = listOf(at("Atributos Básicos", 0.595, 0.363, 260), at("PV 1458 ATQ F. 231+14", 0.70, 0.43, 600), at("ATQ C. 0+14 DEF F. 65", 0.70, 0.488, 600),
            at("DEF C. 65 Veloci... 22", 0.70, 0.548, 600), at("Atributos de Luta", 0.595, 0.632, 280))
        assertEquals(6, Stage.basicStats(rows, W, H).size)
    }
    @Test fun levelSplitAcrossLines() {
        val lines = aprimorar("Nível").filter { it.text != "Nível" } + listOf(at("Nível", 0.70, 0.223, 90), at("12", 0.76, 0.225, 50)) 
        assertEquals(12, Stage.panelLevel(lines, W, H))
    }
    @Test fun criticalLabelIsNotLevel() { assertNull(Stage.panelLevel(listOf(at("Nível Crít. F.", 0.72, 0.1, 200), at("225", 0.82, 0.1, 50)), W, H)) }
    @Test fun gerenciarHasNoLevelSoNoOwnership() {
        val lines = listOf(at("Gerenciar", 0.14, 0.035), at("Total possuído: 113", 0.8, 0.96, 300), at("Casa dos Cavaleiros", 0.05, 0.6, 200), at("Desbloquear", 0.46, 0.96, 120),
            at("Configurações", 0.46, 0.67, 150), at("Luta", 0.7, 0.4), at("lv.40 (Máx.)", 0.72, 0.4, 150), at("lv.40 (Máx.)", 0.84, 0.4, 150), at("lv.20 (Máx.)", 0.95, 0.4, 150))
        val a = ScreenAnalyzer.analyze(lines, W, H, StageStrip("Thane da Terra Flamejante", "C", 2, 0.8))
        assertEquals(ScreenKind.KNIGHT_ROSTER, a.kind)
        assertEquals(113L, a.resources.single { it.label == ScreenAnalyzer.TOTAL_OWNED_LABEL }.value)
        val k = a.knights.single(); assertNull(k.level); assertFalse(k.strong)   // sem Nível => não prova posse
        // "Luta"/"lv.NN" das cartas nunca viram cavaleiros
        assertTrue(a.knights.none { it.rawName == "Luta" })
    }
    @Test fun inventoryQtde() {
        val lines = listOf(at("Inventário", 0.5, 0.05), at("Cristal Valiosa", 0.1, 0.2, 200), at("QTDE: 7", 0.12, 0.26, 120), at("Vender", 0.1, 0.9, 80))
        val a = ScreenAnalyzer.analyze(lines, W, H)
        assertEquals(ScreenKind.INVENTORY, a.kind); assertEquals(7L, a.resources.single { it.label == "Cristal Valiosa" }.value)
    }
    @Test fun winScreen() {
        val a = ScreenAnalyzer.analyze(listOf(at("WIN", 0.5, 0.2), at("Dados", 0.6, 0.4), at("+2520", 0.5, 0.4)), W, H)
        assertEquals(ScreenKind.BATTLE_RESULT, a.kind); assertEquals("VICTORY", a.battleOutcome)
    }
    @Test fun battleHud() {
        assertEquals(ScreenKind.BATTLE, ScreenClassifier.classify(listOf(at("Ataque Básico", 0.9, 0.9), at("Passiva", 0.9, 0.7), at("Bloqueado", 0.9, 0.75))).first)
    }
    @Test fun strongEvidenceConfirmsImmediately() {
        val r = KnightReading("Guerreiro Marina", 11, 0.8, stars = 2, rarity = "C", stats = mapOf("pv" to 1458), strong = true)
        val st = Reconciler.mergeKnight(null, r, ScreenKind.KNIGHT_DETAIL, false, 1, 10L).state!!
        assertEquals(KnightStatus.CONFIRMED, st.status); assertEquals(1458, st.pv); assertEquals(2, st.stars)
        // nova leitura sem stats/estrelas não apaga o que já havia
        val again = Reconciler.mergeKnight(st, KnightReading("Guerreiro Marina", 12, 0.8, strong = true), ScreenKind.KNIGHT_DETAIL, true, 2, 11L).state!!
        assertEquals(12, again.level); assertEquals(1458, again.pv); assertEquals(2, again.stars)
    }
    @Test fun coverageAdviceUsesGameTotal() {
        val ks = (1..3).map { KnightState("k$it", "K$it", 10, null, KnightStatus.CONFIRMED, 0.8, 1, 0, 0) }
        val adv = Recommender.advise(ks, 0, setOf(ScreenKind.KNIGHT_ROSTER), 0, totalOwned = 113)
        assertTrue(adv.any { it.id == "coverage-knights" && it.title.contains("110") })
    }
}

class VisualAnchorTest {
    @Test fun stageStripAloneMakesKnightScreen() {
        val a = ScreenAnalyzer.analyze(listOf(at("Sétimo", 0.3, 0.9), at("Espada Sagrada", 0.8, 0.9)), W, H, StageStrip("Shura de Capricórnio", "S", 3, 0.8))
        assertEquals(ScreenKind.KNIGHT_DETAIL, a.kind); assertNull(a.knights.single().level)
    }
    @Test fun battleHudLeftButtons() {
        assertEquals(ScreenKind.BATTLE, ScreenClassifier.classify(listOf(at("Recuar", 0.01, 0.1), at("Pausar", 0.01, 0.2), at("Auto", 0.01, 0.3))).first)
    }
    @Test fun ocrTypoStillMatches() {
        assertEquals(ScreenKind.TEAM_FORMATION, ScreenClassifier.classify(listOf(at("Frmação", 0.9, 0.3), at("Salvar", 0.9, 0.8))).first)
    }
}

class SkillsTest {
    @Test fun skillTabReadsNamesAndLevels() {
        val lines = listOf(at("Aprimorar", 0.14, 0.035), at("Detalhes da Habilidade", 0.7, 0.07, 300),
            at("Espada Sagrada.S...", 0.66, 0.22, 260), at("Nível 1 /5", 0.64, 0.27, 120), at("Subir de Nível", 0.82, 0.25, 140),
            at("Espada Sagrada.T...", 0.66, 0.43, 260), at("Nível 2 /5", 0.64, 0.48, 120),
            at("Espada Sagrada (Passiva)", 0.68, 0.63, 300), at("Nível 1 /5", 0.64, 0.68, 120))
        val s = Stage.skills(lines, W, H)
        assertEquals(listOf("Espada Sagrada.S" to 1, "Espada Sagrada.T" to 2, "Espada Sagrada (Passiva)" to 1), s.map { it.name to it.level })
        assertTrue(s.all { it.max == 5 })
        val a = ScreenAnalyzer.analyze(lines, W, H, StageStrip("Shura de Capricórnio", "S", 3, 0.8))
        assertEquals(ScreenKind.KNIGHT_DETAIL, a.kind); assertEquals(3, a.knights.single().skills.size)
    }
    @Test fun cosmoScreen() {
        assertEquals(ScreenKind.COSMOS, ScreenClassifier.classify(listOf(at("Cosmo Divino", 0.1, 0.03), at("Desequipar Tudo", 0.8, 0.9), at("Inventário de Cosmos", 0.9, 0.9))).first)
    }
    @Test fun storyVictoryMessage() {
        val a = ScreenAnalyzer.analyze(listOf(at("Você venceu a luta. Saída automática em 4 segundos", 0.5, 0.02, 600), at("Recompensas", 0.5, 0.4)), W, H)
        assertEquals("VICTORY", a.battleOutcome)
    }
}
