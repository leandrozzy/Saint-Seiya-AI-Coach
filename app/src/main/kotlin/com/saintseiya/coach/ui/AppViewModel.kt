package com.saintseiya.coach.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.saintseiya.coach.CoachApp
import com.saintseiya.coach.capture.CaptureState
import com.saintseiya.coach.capture.Pipeline
import com.saintseiya.coach.core.*
import com.saintseiya.coach.data.*
import com.saintseiya.coach.update.ReleaseInfo
import com.saintseiya.coach.update.UpdateCheck
import com.saintseiya.coach.update.Updater
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ImportState(val running: Boolean = false, val total: Int = 0, val done: Int = 0, val readable: Int = 0, val newK: Int = 0, val updK: Int = 0, val error: String? = null, val finished: Boolean = false)
data class UpdateUi(val checking: Boolean = false, val result: UpdateCheck? = null, val progress: Float? = null, val file: File? = null, val error: String? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val coach = CoachApp.instance
    private val dao = coach.repo.dao()
    private val repo = coach.repo

    val activeAccount = MutableStateFlow(coach.prefs.activeAccountId)
    val accounts = dao.accounts().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val knights = activeAccount.flatMapLatest { dao.knights(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val resources = activeAccount.flatMapLatest { dao.resources(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val issues = activeAccount.flatMapLatest { dao.openIssues(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val sessions = activeAccount.flatMapLatest { dao.sessions(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val observations = activeAccount.flatMapLatest { dao.observations(it, 200) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val battles = activeAccount.flatMapLatest { dao.battles(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val teams = activeAccount.flatMapLatest { dao.teams(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recLog = activeAccount.flatMapLatest { dao.recommendations(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val visited = activeAccount.flatMapLatest { dao.visitedKinds(it) }
        .map { l -> l.mapNotNull { runCatching { ScreenKind.valueOf(it) }.getOrNull() }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val live = CaptureState.live
    val report = CaptureState.report

    private fun KnightEntity.state() = KnightState(key, displayName, level, role?.let { runCatching { Role.valueOf(it) }.getOrNull() },
        runCatching { KnightStatus.valueOf(status) }.getOrDefault(KnightStatus.SEEN_ELSEWHERE), confidence, sightings, firstSeenAt, lastSeenAt, sourceObservationId, levelLocked)

    val knightStates = knights.map { l -> l.map { it.state() } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val teamSuggestion = knightStates.map { TeamBuilder.build(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, TeamBuilder.build(emptyList()))
    val advice = combine(knightStates, issues, visited, knights) { ks, iss, vis, kEnt ->
        val candidates = kEnt.count { it.status == "CANDIDATE" }
        Recommender.advise(ks, iss.size + candidates, vis, System.currentTimeMillis())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // registra recomendações exibidas (máx. 1x/24h por id) para a aba Aprendizado
        viewModelScope.launch {
            advice.collect { list ->
                val acc = activeAccount.value
                val since = System.currentTimeMillis() - 24L * 3600_000
                list.take(5).forEach { a ->
                    if (dao.recentAdvice(acc, a.id, since) == 0)
                        dao.insertRecommendation(RecommendationLogEntity(accountId = acc, adviceId = a.id, title = a.title, dataUsed = a.dataUsed, confidence = a.confidence, shownAt = System.currentTimeMillis()))
                }
            }
        }
    }

    fun selectAccount(id: Long) { coach.prefs.activeAccountId = id; activeAccount.value = id }
    fun addAccount(name: String, server: String?) = viewModelScope.launch {
        val id = dao.insertAccount(AccountEntity(name = name.ifBlank { "Conta" }, server = server?.ifBlank { null }, createdAt = System.currentTimeMillis()))
        selectAccount(id)
    }
    fun renameAccount(id: Long, name: String, server: String?) = viewModelScope.launch { dao.updateAccount(id, name, server?.ifBlank { null }) }
    fun deleteAccount(id: Long) = viewModelScope.launch {
        dao.deleteAccount(id); repo.deleteAccountData(id)
        val rest = dao.accountsOnce()
        selectAccount(rest.firstOrNull()?.id ?: repo.ensureAccount())
    }
    fun wipeData() = viewModelScope.launch { repo.deleteAccountData(activeAccount.value) }

    // correções manuais
    fun setLevel(key: String, level: Int?) = viewModelScope.launch { repo.setLevel(activeAccount.value, key, level) }
    fun setRole(key: String, role: Role?) = viewModelScope.launch { repo.setRole(activeAccount.value, key, role) }
    fun setStatus(key: String, st: KnightStatus) = viewModelScope.launch { repo.setStatus(activeAccount.value, key, st) }
    fun addKnight(name: String, level: Int?) = viewModelScope.launch { repo.addManualKnight(activeAccount.value, name, level) }
    fun resolveIssue(id: Long) = viewModelScope.launch { dao.resolveIssue(id) }
    fun confirmAllCandidates() = viewModelScope.launch {
        knights.value.filter { it.status == "CANDIDATE" && it.level != null }.forEach { repo.setStatus(activeAccount.value, it.key, KnightStatus.CONFIRMED) }
    }
    fun saveTeam(name: String, keys: List<String>, note: String?) = viewModelScope.launch { repo.saveTeam(activeAccount.value, name.ifBlank { "Equipe" }, keys, note) }
    fun historyOf(key: String) = activeAccount.flatMapLatest { dao.history(it, key) }

    // importação de screenshots (mesma pipeline da captura ao vivo)
    private val _import = MutableStateFlow(ImportState())
    val importState = _import.asStateFlow()
    private var importJob: Job? = null
    fun importImages(uris: List<Uri>) {
        if (uris.isEmpty() || _import.value.running) return
        val acc = activeAccount.value
        _import.value = ImportState(running = true, total = uris.size)
        importJob = viewModelScope.launch(Dispatchers.Default) {
            val sid = dao.insertSession(SessionEntity(accountId = acc, source = "IMPORT", startedAt = System.currentTimeMillis(), framesSeen = uris.size))
            val pipe = Pipeline(repo)
            var st = _import.value
            try {
                for (u in uris) {
                    ensureActive()
                    var bmp: Bitmap? = null
                    try {
                        bmp = decode(u)
                        if (bmp == null) { st = st.copy(done = st.done + 1, error = "Não foi possível abrir uma imagem."); _import.value = st; continue }
                        val r = pipe.process(acc, sid, bmp, pipe.hash(bmp))
                        if (r == null) { st = st.copy(done = st.done + 1); _import.value = st; continue }
                        st = st.copy(done = st.done + 1, readable = st.readable + if (r.readable) 1 else 0, newK = st.newK + r.knightsNew, updK = st.updK + r.knightsUpdated)
                    } catch (e: CancellationException) { throw e
                    } catch (e: Exception) { st = st.copy(done = st.done + 1, error = "Erro: ${e.message}"); coach.prefs.lastError = "Importação: ${e.message}" }
                    finally { bmp?.recycle() }
                    _import.value = st
                }
                _import.value = st.copy(running = false, finished = true)
                dao.session(sid)?.let { dao.updateSession(it.copy(endedAt = System.currentTimeMillis(), endReason = "DONE")) }
            } catch (e: CancellationException) {
                _import.value = st.copy(running = false, finished = true, error = "Importação cancelada.")
                dao.session(sid)?.let { dao.updateSession(it.copy(endedAt = System.currentTimeMillis(), endReason = "USER")) }
            }
        }
    }
    fun cancelImport() { importJob?.cancel() }
    fun clearImport() { _import.value = ImportState() }

    private fun decode(u: Uri): Bitmap? {
        val cr = getApplication<Application>().contentResolver
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, opts) }
        var s = 1; val big = maxOf(opts.outWidth, opts.outHeight)
        while (big / s > 2400) s *= 2
        return cr.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) }
    }

    // atualização in-app
    private val _upd = MutableStateFlow(UpdateUi())
    val update = _upd.asStateFlow()
    fun checkUpdate() = viewModelScope.launch {
        _upd.value = UpdateUi(checking = true)
        _upd.value = UpdateUi(result = Updater.check())
    }
    fun downloadUpdate(info: ReleaseInfo) = viewModelScope.launch {
        _upd.value = _upd.value.copy(progress = 0f, error = null)
        val r = Updater.download(getApplication(), info) { p -> _upd.value = _upd.value.copy(progress = p) }
        _upd.value = r.fold({ _upd.value.copy(progress = null, file = it) }, { _upd.value.copy(progress = null, error = "Falha no download: ${it.message}") })
    }
    fun installUpdate() { _upd.value.file?.let { Updater.install(getApplication(), it) } }

    // exportação JSON (backup legível)
    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val acc = activeAccount.value
        val j = JSONObject()
        j.put("app", "SaintSeiyaAICoach"); j.put("exportedAt", System.currentTimeMillis()); j.put("accountId", acc)
        j.put("knights", JSONArray().also { a -> dao.knightsOnce(acc).forEach { k ->
            a.put(JSONObject().put("key", k.key).put("name", k.displayName).put("level", k.level ?: JSONObject.NULL).put("role", k.role ?: JSONObject.NULL)
                .put("status", k.status).put("confidence", k.confidence).put("sightings", k.sightings).put("lastSeenAt", k.lastSeenAt)) } })
        j.put("resources", JSONArray().also { a -> resources.value.forEach { r -> a.put(JSONObject().put("label", r.label).put("value", r.value).put("observedAt", r.observedAt)) } })
        j.put("teams", JSONArray().also { a -> teams.value.forEach { t -> a.put(JSONObject().put("name", t.name).put("version", t.version).put("members", t.memberKeys)) } })
        j.toString(2)
    }

    init { if (System.currentTimeMillis() - coach.prefs.lastUpdateCheck > 6L * 3600_000) checkUpdate() }
}
