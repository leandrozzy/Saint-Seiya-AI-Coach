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

class AnalyzerTest {
    @Test fun pairsNameWithLevel() {
        val lines = listOf(
            line("Cavaleiros", 10, 10), line("Filtrar", 900, 10),
            line("Seiya", 100, 300), line("Lv.45", 100, 340),
            line("Shiryu", 400, 300), line("Lv.40", 400, 340),
            line("Hyoga", 700, 300), line("Lv.38", 700, 340),
            line("Shun", 1000, 300), line("Lv.12", 1000, 340),
        )
        val a = ScreenAnalyzer.analyze(lines, 2400, 1080)
        assertEquals(ScreenKind.KNIGHT_ROSTER, a.kind)
        val m = a.knights.associate { it.rawName to it.level }
        assertEquals(45, m["Seiya"]); assertEquals(12, m["Shun"]); assertEquals(4, m.size)
    }
    @Test fun inventoryResources() {
        val lines = listOf(line("Inventário", 0, 0), line("Itens", 0, 40), line("Poeira estelar", 100, 200), line("x1,250", 100, 240, 100))
        val a = ScreenAnalyzer.analyze(lines, 2400, 1080)
        assertEquals(1250L, a.resources.single().value)
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
        assertEquals(40, Recommender.coveragePercent(setOf(ScreenKind.COSMOS, ScreenKind.INVENTORY)))
    }
    @Test fun levelAdviceHasUnknownCost() {
        val t = (1..6).map { k("K$it", if (it == 1) 10 else 60) }
        val adv = Recommender.advise(t, 0, setOf(ScreenKind.KNIGHT_ROSTER), 0)
        val a = adv.first { it.id.startsWith("lvl-") }
        assertTrue(a.cost.contains("Desconhecido")); assertTrue(a.confidence < 0.5)
    }
}

class InlineLevelTest {
    @Test fun nameAndLevelOnSameLine() {
        val lines = listOf(line("Seiya Lv.45", 100, 300), line("Shiryu Nv 40", 400, 300), line("Hyoga LV.38", 700, 300), line("Cavaleiros", 0, 0))
        val a = ScreenAnalyzer.analyze(lines, 2400, 1080)
        assertEquals(ScreenKind.KNIGHT_ROSTER, a.kind)
        assertEquals(mapOf("Seiya" to 45, "Shiryu" to 40, "Hyoga" to 38), a.knights.associate { it.rawName to it.level })
    }
    @Test fun levelTokenAnywhere() { assertEquals(80, ScreenClassifier.levelOf("Lv.80/100")); assertNull(ScreenClassifier.levelOf("Level up")) }
}

class FallbackTest {
    @Test fun unknownScreenWithPairsBecomesRoster() {
        val lines = listOf(line("Seiya", 100, 300), line("Lv.45", 100, 340), line("Shiryu", 600, 300), line("Lv.40", 600, 340))
        val a = ScreenAnalyzer.analyze(lines, 2400, 1080)
        assertEquals(ScreenKind.KNIGHT_ROSTER, a.kind); assertEquals(2, a.knights.count { it.level != null })
    }
    @Test fun detailPicksLargestNameFarFromLevel() {
        val lines = listOf(TextLine("Seiya", 100, 40, 500, 120), TextLine("Equipar", 100, 600, 300, 630), TextLine("Lv.60", 900, 400, 1000, 430), TextLine("Habilidades", 100, 700, 400, 730), TextLine("Atributos", 100, 760, 400, 790))
        val a = ScreenAnalyzer.analyze(lines, 2400, 1080)
        assertEquals(60, a.knights.single { it.level != null }.level); assertEquals("Seiya", a.knights.single { it.level != null }.rawName)
    }
}

class LoadingTest {
    @Test fun loginScreenRecognized() {
        assertEquals(ScreenKind.LOADING, ScreenClassifier.classify(listOf(line("Iniciar Jogo", 0, 0))).first)
        assertEquals(ScreenKind.LOADING, ScreenClassifier.classify(listOf(line("Repair Client", 0, 0), line("Verificando arquivos de recurso", 0, 40))).first)
    }
}
