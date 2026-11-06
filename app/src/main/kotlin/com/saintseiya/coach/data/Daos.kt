package com.saintseiya.coach.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CoachDao {
    // contas
    @Query("SELECT * FROM account ORDER BY id") fun accounts(): Flow<List<AccountEntity>>
    @Query("SELECT * FROM account ORDER BY id") suspend fun accountsOnce(): List<AccountEntity>
    @Insert suspend fun insertAccount(a: AccountEntity): Long
    @Query("DELETE FROM account WHERE id=:id") suspend fun deleteAccount(id: Long)
    @Query("UPDATE account SET name=:name, server=:server WHERE id=:id") suspend fun updateAccount(id: Long, name: String, server: String?)

    // sessões
    @Insert suspend fun insertSession(s: SessionEntity): Long
    @Update suspend fun updateSession(s: SessionEntity)
    @Query("SELECT * FROM capture_session WHERE id=:id") suspend fun session(id: Long): SessionEntity?
    @Query("SELECT * FROM capture_session WHERE accountId=:acc ORDER BY startedAt DESC") fun sessions(acc: Long): Flow<List<SessionEntity>>
    @Query("SELECT * FROM capture_session WHERE accountId=:acc ORDER BY startedAt DESC LIMIT 1") fun lastSession(acc: Long): Flow<SessionEntity?>
    @Query("UPDATE capture_session SET endedAt=:at, endReason='INTERRUPTED' WHERE endedAt IS NULL") suspend fun closeDanglingSessions(at: Long)

    // observações
    @Insert suspend fun insertObservation(o: ObservationEntity): Long
    @Query("SELECT * FROM screen_observation WHERE accountId=:acc ORDER BY observedAt DESC LIMIT :limit") fun observations(acc: Long, limit: Int): Flow<List<ObservationEntity>>
    @Query("SELECT DISTINCT kind FROM screen_observation WHERE accountId=:acc") fun visitedKinds(acc: Long): Flow<List<String>>

    // cavaleiros
    @Query("SELECT * FROM knight WHERE accountId=:acc AND `key`=:key") suspend fun knight(acc: Long, key: String): KnightEntity?
    @Query("SELECT * FROM knight WHERE accountId=:acc ORDER BY CASE status WHEN 'CONFIRMED' THEN 0 WHEN 'CANDIDATE' THEN 1 ELSE 2 END, level DESC, displayName") fun knights(acc: Long): Flow<List<KnightEntity>>
    @Query("SELECT * FROM knight WHERE accountId=:acc") suspend fun knightsOnce(acc: Long): List<KnightEntity>
    @Upsert suspend fun upsertKnight(k: KnightEntity)
    @Insert suspend fun insertHistory(h: KnightHistoryEntity)
    @Query("SELECT * FROM knight_history WHERE accountId=:acc AND `key`=:key ORDER BY at DESC") fun history(acc: Long, key: String): Flow<List<KnightHistoryEntity>>

    // habilidades
    @Upsert suspend fun upsertSkill(s: SkillEntity)
    @Query("SELECT * FROM knight_skill WHERE accountId=:acc AND knightKey=:key") suspend fun skillsOnce(acc: Long, key: String): List<SkillEntity>
    @Query("SELECT * FROM knight_skill WHERE accountId=:acc AND knightKey=:key ORDER BY name") fun skills(acc: Long, key: String): Flow<List<SkillEntity>>
    @Query("DELETE FROM knight_skill WHERE accountId=:acc") suspend fun clearSkills(acc: Long)

    // recursos
    @Upsert suspend fun upsertResource(r: ResourceEntity)
    @Query("SELECT * FROM resource WHERE accountId=:acc ORDER BY label") fun resources(acc: Long): Flow<List<ResourceEntity>>

    // pendências
    @Insert suspend fun insertIssue(i: IssueEntity): Long
    @Query("SELECT * FROM issue WHERE accountId=:acc AND resolved=0 ORDER BY createdAt DESC") fun openIssues(acc: Long): Flow<List<IssueEntity>>
    @Query("UPDATE issue SET resolved=1 WHERE id=:id") suspend fun resolveIssue(id: Long)
    @Query("SELECT COUNT(*) FROM issue WHERE accountId=:acc AND kind=:kind AND detail=:detail AND resolved=0") suspend fun countOpenIssue(acc: Long, kind: String, detail: String): Int

    // batalhas
    @Insert suspend fun insertBattle(b: BattleEntity)
    @Query("SELECT * FROM battle WHERE accountId=:acc ORDER BY observedAt DESC") fun battles(acc: Long): Flow<List<BattleEntity>>

    // equipes
    @Insert suspend fun insertTeam(t: TeamEntity): Long
    @Query("SELECT * FROM team WHERE accountId=:acc ORDER BY createdAt DESC") fun teams(acc: Long): Flow<List<TeamEntity>>
    @Query("SELECT COALESCE(MAX(version),0) FROM team WHERE accountId=:acc AND name=:name") suspend fun maxTeamVersion(acc: Long, name: String): Int

    // recomendações
    @Insert suspend fun insertRecommendation(r: RecommendationLogEntity)
    @Query("SELECT * FROM recommendation_log WHERE accountId=:acc ORDER BY shownAt DESC LIMIT 100") fun recommendations(acc: Long): Flow<List<RecommendationLogEntity>>
    @Query("SELECT COUNT(*) FROM recommendation_log WHERE accountId=:acc AND adviceId=:id AND shownAt>:since") suspend fun recentAdvice(acc: Long, id: String, since: Long): Int

    // catálogo
    @Upsert suspend fun upsertCatalog(c: CatalogEntity)
    @Query("SELECT `key` FROM catalog") suspend fun catalogKeys(): List<String>
    @Query("SELECT * FROM catalog") suspend fun catalogAll(): List<CatalogEntity>

    // limpeza
    @Query("DELETE FROM screen_observation WHERE accountId=:acc") suspend fun clearObservations(acc: Long)
    @Query("DELETE FROM capture_session WHERE accountId=:acc") suspend fun clearSessions(acc: Long)
    @Query("DELETE FROM knight WHERE accountId=:acc") suspend fun clearKnights(acc: Long)
    @Query("DELETE FROM knight_history WHERE accountId=:acc") suspend fun clearHistory(acc: Long)
    @Query("DELETE FROM resource WHERE accountId=:acc") suspend fun clearResources(acc: Long)
    @Query("DELETE FROM issue WHERE accountId=:acc") suspend fun clearIssues(acc: Long)
    @Query("DELETE FROM battle WHERE accountId=:acc") suspend fun clearBattles(acc: Long)
    @Query("DELETE FROM team WHERE accountId=:acc") suspend fun clearTeams(acc: Long)
    @Query("DELETE FROM recommendation_log WHERE accountId=:acc") suspend fun clearRecs(acc: Long)
}
