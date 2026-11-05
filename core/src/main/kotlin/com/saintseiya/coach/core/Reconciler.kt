package com.saintseiya.coach.core

/**
 * Regras de reconciliação (puras e testáveis):
 *  - só telas "próprias" (lista/ficha/formação) com NÍVEL lido podem indicar posse;
 *  - nome visto em banner/adversário/Pick&Ban NUNCA vira possuído;
 *  - campos não vistos nunca são apagados; nível não regride sem segunda observação (vira pendência);
 *  - campos corrigidos manualmente (levelLocked) não são sobrescritos pelo OCR.
 */
object Reconciler {
    const val MIN_CONFIDENCE = 0.5
    const val MAX_LEVEL = 300

    fun mergeKnight(
        existing: KnightState?,
        reading: KnightReading,
        kind: ScreenKind,
        catalogKnown: Boolean,
        observationId: Long?,
        now: Long,
    ): MergeResult {
        val key = Norm.key(reading.rawName)
        if (key.length < 3) return MergeResult(existing, emptyList(), emptyList())
        if (existing?.status == KnightStatus.REJECTED) return MergeResult(existing, emptyList(), emptyList())

        val evidenceOfOwnership = kind.ownData && reading.level != null &&
            reading.level in 1..MAX_LEVEL && reading.confidence >= MIN_CONFIDENCE

        if (!evidenceOfOwnership) {
            if (existing != null) return MergeResult(existing, emptyList(), emptyList())
            val seen = KnightState(
                key = key, displayName = reading.rawName.trim(), status = KnightStatus.SEEN_ELSEWHERE,
                confidence = reading.confidence, sightings = 0, firstSeenAt = now, lastSeenAt = now,
                sourceObservationId = observationId,
            )
            return MergeResult(seen, listOf(Change("status", null, seen.status.name)), emptyList())
        }

        val level = reading.level!!
        val changes = ArrayList<Change>()
        val issues = ArrayList<IssueDraft>()
        val base = existing ?: KnightState(
            key = key, displayName = reading.rawName.trim(), status = KnightStatus.SEEN_ELSEWHERE,
            firstSeenAt = now, lastSeenAt = now,
        )
        val sightings = if (existing?.status == KnightStatus.CONFIRMED || existing?.status == KnightStatus.CANDIDATE)
            existing.sightings + 1 else 1
        val status = when {
            base.status == KnightStatus.CONFIRMED -> KnightStatus.CONFIRMED
            catalogKnown || sightings >= 2 -> KnightStatus.CONFIRMED
            else -> KnightStatus.CANDIDATE
        }
        if (status != base.status) changes += Change("status", base.status.name, status.name)

        var newLevel = base.level
        if (base.levelLocked) {
            // mantém valor corrigido manualmente
        } else if (base.level == null) {
            newLevel = level; changes += Change("level", null, level.toString())
        } else if (level > base.level) {
            newLevel = level; changes += Change("level", base.level.toString(), level.toString())
        } else if (level < base.level) {
            issues += IssueDraft("LEVEL_CONFLICT", "${base.displayName}: lido Lv.$level mas já havia Lv.${base.level}. Mantido o anterior; confirme.")
        }
        val state = base.copy(
            level = newLevel, status = status, sightings = sightings, lastSeenAt = now,
            confidence = maxOf(base.confidence, reading.confidence),
            sourceObservationId = observationId ?: base.sourceObservationId,
        )
        return MergeResult(state, changes, issues)
    }
}
