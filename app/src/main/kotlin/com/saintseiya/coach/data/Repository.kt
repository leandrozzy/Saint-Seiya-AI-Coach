package com.saintseiya.coach.data

import androidx.room.withTransaction
import com.saintseiya.coach.core.*

data class IngestResult(
    val kind: ScreenKind,
    val knightsNew: Int,
    val knightsUpdated: Int,
    val resourcesUpdated: Int,
    val issues: Int,
    val readable: Boolean,
    val lines: Int = 0,
    val score: Double = 0.0,
)

class Repository(val db: CoachDb) {
    private val dao = db.dao()
    fun dao() = dao

    private fun KnightEntity.toState() = KnightState(
        key, displayName, level, role?.let { runCatching { Role.valueOf(it) }.getOrNull() },
        runCatching { KnightStatus.valueOf(status) }.getOrDefault(KnightStatus.SEEN_ELSEWHERE),
        confidence, sightings, firstSeenAt, lastSeenAt, sourceObservationId, levelLocked,
    )

    private fun KnightState.toEntity(acc: Long, version: Int, now: Long) = KnightEntity(
        acc, key, displayName, level, role?.name, status.name, confidence, sightings, firstSeenAt, lastSeenAt,
        sourceObservationId, levelLocked, version, now,
    )

    suspend fun ensureAccount(): Long {
        val list = dao.accountsOnce()
        return list.firstOrNull()?.id ?: dao.insertAccount(AccountEntity(name = "Minha conta", createdAt = System.currentTimeMillis()))
    }

    /** Tudo numa transação: ou aplica a observação inteira ou nada. Idempotente por (observação, campo). */
    suspend fun ingest(accountId: Long, sessionId: Long, analysis: ScreenAnalysis, nameCandidates: List<String>, hash: Long, now: Long): IngestResult =
        db.withTransaction {
            val readable = analysis.kind != ScreenKind.UNKNOWN || analysis.lineCount >= 3
            val obsId = dao.insertObservation(ObservationEntity(
                accountId = accountId, sessionId = sessionId, observedAt = now, kind = analysis.kind.name,
                kindScore = analysis.kindScore, hash = hash, lineCount = analysis.lineCount,
                knightsRead = analysis.knights.size, resourcesRead = analysis.resources.size,
                summary = analysis.knights.take(8).joinToString { "${it.rawName}${it.level?.let { l -> " Lv.$l" } ?: ""}" },
            ))
            val catalogAll = dao.catalogAll()
            val catalog = catalogAll.map { it.key }.toMutableSet()
            val existingAll = dao.knightsOnce(accountId).toMutableList()
            var newK = 0; var updK = 0; var resUpd = 0; var iss = 0

            suspend fun applyReading(reading: KnightReading, kind: ScreenKind) {
                // corrige pequenos erros de OCR casando com cavaleiro/catálogo já conhecido
                val own = existingAll.maxByOrNull { Norm.similarity(it.displayName, reading.rawName) }
                    ?.takeIf { Norm.similarity(it.displayName, reading.rawName) >= 0.86 }
                val canon = own?.displayName ?: catalogAll.maxByOrNull { Norm.similarity(it.name, reading.rawName) }
                    ?.takeIf { Norm.similarity(it.name, reading.rawName) >= 0.86 }?.name ?: reading.rawName
                val key = Norm.key(canon)
                val existing = existingAll.firstOrNull { it.key == key }
                val res = Reconciler.mergeKnight(existing?.toState(), reading.copy(rawName = canon), kind, key in catalog, obsId, now)
                val st = res.state ?: return
                val before = existing?.toState()
                if (before != st) {
                    val ent = st.toEntity(accountId, (existing?.version ?: 0) + 1, now)
                    dao.upsertKnight(ent)
                    existingAll.removeAll { it.key == key }; existingAll += ent
                    res.changes.forEach { dao.insertHistory(KnightHistoryEntity(accountId = accountId, key = key, field = it.field, oldValue = it.old, newValue = it.new, origin = "OCR", observationId = obsId, at = now)) }
                    if (existing == null) newK++ else if (res.changes.isNotEmpty()) updK++
                }
                if (st.status == KnightStatus.CONFIRMED && key !in catalog) {
                    dao.upsertCatalog(CatalogEntity(key, st.displayName, "LEARNED", now)); catalog += key
                }
                for (i in res.issues) if (dao.countOpenIssue(accountId, i.kind, i.detail) == 0) {
                    dao.insertIssue(IssueEntity(accountId = accountId, kind = i.kind, detail = i.detail, observationId = obsId, createdAt = now)); iss++
                }
            }

            // Leituras sem nível só interessam se o nome já é conhecido (evita lixo como rótulos de UI virarem "cavaleiros").
            analysis.knights.filter { it.level != null || catalogAll.any { c -> Norm.similarity(c.name, it.rawName) >= 0.9 } }
                .forEach { applyReading(it, analysis.kind) }

            // Telas não-próprias: só reconhece nomes JÁ conhecidos (catálogo) e marca "visto em outro lugar".
            // Isso NUNCA cria posse.
            if (analysis.kind in setOf(ScreenKind.SUMMON_BANNER, ScreenKind.ENEMY_PREVIEW, ScreenKind.PICK_BAN, ScreenKind.PVP_LOBBY)) {
                nameCandidates.forEach { n ->
                    if (catalogAll.any { Norm.similarity(it.name, n) >= 0.9 }) applyReading(KnightReading(n, null, 0.6), analysis.kind)
                }
            }

            for (r in analysis.resources) {
                val k = Norm.key(r.label)
                dao.upsertResource(ResourceEntity(accountId, k, r.label, r.value, r.confidence, now, obsId)); resUpd++
            }
            if (analysis.kind == ScreenKind.BATTLE_RESULT && analysis.battleOutcome != null) {
                dao.insertBattle(BattleEntity(accountId = accountId, observedAt = now, outcome = analysis.battleOutcome, observationId = obsId, sessionId = sessionId))
            }
            val s = dao.session(sessionId)
            if (s != null) dao.updateSession(s.copy(
                framesProcessed = s.framesProcessed + 1,
                framesReadable = s.framesReadable + if (readable) 1 else 0,
                knightsNew = s.knightsNew + newK, knightsUpdated = s.knightsUpdated + updK,
                resourcesUpdated = s.resourcesUpdated + resUpd, issuesOpened = s.issuesOpened + iss,
            ))
            IngestResult(analysis.kind, newK, updK, resUpd, iss, readable, analysis.lineCount, analysis.kindScore)
        }

    // ---- correções manuais (trilha de auditoria) ----
    suspend fun setLevel(acc: Long, key: String, level: Int?) = db.withTransaction {
        val k = dao.knight(acc, key) ?: return@withTransaction
        val now = System.currentTimeMillis()
        dao.upsertKnight(k.copy(level = level, levelLocked = level != null, version = k.version + 1, updatedAt = now))
        dao.insertHistory(KnightHistoryEntity(accountId = acc, key = key, field = "level", oldValue = k.level?.toString(), newValue = level?.toString(), origin = "MANUAL", observationId = null, at = now))
    }
    suspend fun setRole(acc: Long, key: String, role: Role?) = db.withTransaction {
        val k = dao.knight(acc, key) ?: return@withTransaction
        val now = System.currentTimeMillis()
        dao.upsertKnight(k.copy(role = role?.name, version = k.version + 1, updatedAt = now))
        dao.insertHistory(KnightHistoryEntity(accountId = acc, key = key, field = "role", oldValue = k.role, newValue = role?.name, origin = "MANUAL", observationId = null, at = now))
    }
    suspend fun setStatus(acc: Long, key: String, status: KnightStatus) = db.withTransaction {
        val k = dao.knight(acc, key) ?: return@withTransaction
        val now = System.currentTimeMillis()
        dao.upsertKnight(k.copy(status = status.name, version = k.version + 1, updatedAt = now))
        dao.insertHistory(KnightHistoryEntity(accountId = acc, key = key, field = "status", oldValue = k.status, newValue = status.name, origin = "MANUAL", observationId = null, at = now))
        if (status == KnightStatus.CONFIRMED) dao.upsertCatalog(CatalogEntity(key, k.displayName, "LEARNED", now))
    }
    suspend fun addManualKnight(acc: Long, name: String, level: Int?) = db.withTransaction {
        val key = Norm.key(name); if (key.length < 3) return@withTransaction
        val now = System.currentTimeMillis()
        dao.upsertKnight(KnightEntity(acc, key, name.trim(), level, null, KnightStatus.CONFIRMED.name, 1.0, 1, now, now, null, level != null, 1, now))
        dao.insertHistory(KnightHistoryEntity(accountId = acc, key = key, field = "status", oldValue = null, newValue = "CONFIRMED", origin = "MANUAL", observationId = null, at = now))
        dao.upsertCatalog(CatalogEntity(key, name.trim(), "LEARNED", now))
    }

    suspend fun saveTeam(acc: Long, name: String, keys: List<String>, note: String?) {
        val v = dao.maxTeamVersion(acc, name) + 1
        dao.insertTeam(TeamEntity(accountId = acc, name = name, version = v, memberKeys = keys.joinToString(","), note = note, createdAt = System.currentTimeMillis()))
    }

    suspend fun deleteAccountData(acc: Long) = db.withTransaction {
        dao.clearObservations(acc); dao.clearSessions(acc); dao.clearKnights(acc); dao.clearHistory(acc)
        dao.clearResources(acc); dao.clearIssues(acc); dao.clearBattles(acc); dao.clearTeams(acc); dao.clearRecs(acc)
    }
}
