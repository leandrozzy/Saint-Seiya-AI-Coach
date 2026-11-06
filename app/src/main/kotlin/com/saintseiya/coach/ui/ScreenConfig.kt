package com.saintseiya.coach.ui

import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.saintseiya.coach.BuildConfig
import com.saintseiya.coach.CoachApp
import com.saintseiya.coach.update.UpdateCheck
import com.saintseiya.coach.update.Updater
import kotlinx.coroutines.launch

@Composable
fun ConfigScreen(vm: AppViewModel, nav: Nav) {
    val titles = listOf("Contas", "Captura", "Atualizações", "Privacidade e dados", "Diagnóstico", "Sobre")
    val sel = nav.sub[7] ?: 0
    Column {
        SectionTabs(titles, sel) { nav.sub[7] = it }
        Spacer(Modifier.height(8.dp))
        when (sel) {
            0 -> Contas(vm); 1 -> CapturaCfg(); 2 -> Atualizacoes(vm); 3 -> Privacidade(vm); 4 -> Diagnostico(vm); else -> Sobre()
        }
    }
}

@Composable
private fun Contas(vm: AppViewModel) {
    val accounts by vm.accounts.collectAsState(); val active by vm.activeAccount.collectAsState()
    var name by remember { mutableStateOf("") }; var server by remember { mutableStateOf("") }
    var delete by remember { mutableStateOf<Long?>(null) }
    delete?.let { id -> AlertDialog({ delete = null }, title = { Text("Excluir conta e dados?") }, text = { Text("Apaga TODOS os dados desta conta neste aparelho. Não afeta o jogo.") },
        confirmButton = { TextButton({ vm.deleteAccount(id); delete = null }) { Text("Excluir", color = C.bad) } }, dismissButton = { TextButton({ delete = null }) { Text("Cancelar") } }) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { Small("Cada conta tem dados isolados por ID interno; uma sessão só altera a conta ativa quando foi iniciada.") }
        items(accounts, key = { it.id }) { a ->
            SCard(Modifier.clickable { vm.selectAccount(a.id) }, accent = if (a.id == active) C.gold else null) {
                Row { Column(Modifier.weight(1f)) { Text(a.name, color = C.text, fontWeight = FontWeight.SemiBold); Small(a.server ?: "servidor desconhecido") }
                    if (a.id == active) Badge("ativa", C.ok) else TextButton({ vm.selectAccount(a.id) }) { Text("Usar") } }
                TextButton({ delete = a.id }) { Text("Excluir", color = C.bad) }
            }
        }
        item {
            SCard { H("Nova conta")
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(server, { server = it }, label = { Text("Servidor/região (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button({ vm.addAccount(name, server); name = ""; server = "" }, enabled = name.isNotBlank()) { Text("Criar e usar") } }
        }
    }
}

@Composable
private fun CapturaCfg() {
    val prefs = CoachApp.instance.prefs
    var interval by remember { mutableFloatStateOf(prefs.intervalMs / 1000f) }
    var thr by remember { mutableFloatStateOf(prefs.changeThreshold.toFloat()) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SCard { H("Taxa de amostragem"); Body("A cada %.1f s".format(interval))
            Slider(interval, { interval = it; prefs.intervalMs = (it * 1000).toLong() }, valueRange = 0.5f..10f)
            Small("Menor = mais responsivo e mais bateria/calor. 1,5 s é o padrão.") } }
        item { SCard { H("Sensibilidade a mudança de tela"); Body("Distância mínima: ${thr.toInt()} (0–64)")
            Slider(thr, { thr = it; prefs.changeThreshold = it.toInt() }, valueRange = 1f..24f)
            Small("Só telas que mudaram são analisadas (hash perceptual), economizando bateria. Ajuste se telas diferentes forem ignoradas.") } }
        item { SCard { H("Como funciona"); Body("• Você autoriza a captura pelo Android (pode parar a qualquer momento).\n• O OCR roda no aparelho (ML Kit); nenhuma imagem sai dele nem é salva.\n• Na janela do Android escolha 'Tela inteira' para seguir o jogo (ou apenas o app do jogo).") } }
    }
}

@Composable
private fun Atualizacoes(vm: AppViewModel) {
    val ctx = LocalContext.current; val u by vm.update.collectAsState()
    val prefs = CoachApp.instance.prefs
    var token by remember { mutableStateOf("") }
    var tokenSaved by remember { mutableStateOf(!prefs.githubToken.isNullOrBlank()) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { SCard { H("Versão instalada"); Body("${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
            Small("Última verificação: ${fmtTime(prefs.lastUpdateCheck)}")
            Button({ vm.checkUpdate() }, enabled = !u.checking) { Text(if (u.checking) "Verificando…" else "Verificar atualização") } } }
        item {
            when (val r = u.result) {
                is UpdateCheck.Available -> SCard(accent = C.gold) {
                    H("Nova versão: ${r.info.name.ifBlank { r.info.tag }}")
                    Body(r.info.notes.take(400).ifBlank { "Sem notas." }, muted = true)
                    if (u.progress != null) LinearProgressIndicator(progress = { u.progress ?: 0f }, modifier = Modifier.fillMaxWidth())
                    u.error?.let { Small(it) }
                    if (u.file == null) Button({ vm.downloadUpdate(r.info) }, enabled = u.progress == null) { Text("Baixar (${r.info.size / 1_000_000} MB)") }
                    else {
                        if (!Updater.canInstall(ctx)) { Body("Permita instalar apps deste app e volte."); Button({ Updater.openInstallPermission(ctx) }) { Text("Permitir instalação") } }
                        Button({ vm.installUpdate() }) { Text("Instalar por cima (mantém seus dados)") }
                    }
                }
                UpdateCheck.UpToDate -> SCard(accent = C.ok) { Body("Você está na versão mais recente.") }
                is UpdateCheck.Failed -> SCard(accent = C.bad) { Body(r.reason) }
                null -> {}
            }
        }
        item {
            SCard { H("Repositório privado?"); Small("Se o repositório do GitHub é privado, crie um token fine-grained SOMENTE LEITURA (Contents: Read) e cole abaixo. Fica criptografado no aparelho (Android Keystore) e nunca vai para o Git.")
                if (!prefs.secureAvailable) Small("Armazenamento seguro indisponível neste aparelho: token não será salvo.")
                OutlinedTextField(token, { token = it }, label = { Text(if (tokenSaved) "Token salvo (digite para trocar)" else "Token (opcional)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ prefs.githubToken = token; tokenSaved = token.isNotBlank(); token = "" }, enabled = token.isNotBlank()) { Text("Salvar token") }
                    if (tokenSaved) OutlinedButton({ prefs.githubToken = null; tokenSaved = false }) { Text("Remover") }
                } }
        }
        item { Small("Atualizações são grátis via GitHub Releases. O APK é sempre assinado com a mesma chave, então o Android instala por cima sem apagar seus dados.") }
    }
}

@Composable
private fun Privacidade(vm: AppViewModel) {
    val scope = rememberCoroutineScope(); val ctx = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch { val txt = vm.exportJson(); ctx.contentResolver.openOutputStream(uri)?.use { it.write(txt.toByteArray()) } }
    }
    if (confirm) AlertDialog({ confirm = false }, title = { Text("Apagar dados da conta ativa?") }, text = { Text("Remove cavaleiros, recursos, equipes, sessões e histórico da conta ativa neste aparelho.") },
        confirmButton = { TextButton({ vm.wipeData(); confirm = false }) { Text("Apagar", color = C.bad) } }, dismissButton = { TextButton({ confirm = false }) { Text("Cancelar") } })
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SCard { H("Privacidade"); Body("• Imagens da tela NÃO são salvas.\n• Texto bruto do OCR NÃO é guardado; só nome, nível, tipo de tela e contagens.\n• Nada é enviado à internet pela leitura (a internet só é usada para verificar atualizações).\n• Sem senha do jogo, sem interceptar tráfego, sem acessibilidade, sem automação de cliques.") } }
        item { SCard { H("Exportar"); Button({ save.launch("saint-seiya-coach-backup.json") }) { Text("Exportar JSON") }
            Small("Backup automático do Android (nuvem do Google) pode restaurar o banco se você reinstalar o app no mesmo Google.") } }
        item { SCard(accent = C.bad) { H("Apagar"); OutlinedButton({ confirm = true }) { Text("Apagar dados da conta ativa") } } }
    }
}

private data class Feature(val name: String, val status: String, val note: String)

@Composable
private fun Diagnostico(vm: AppViewModel) {
    val live by vm.live.collectAsState(); val ctx = LocalContext.current; val prefs = CoachApp.instance.prefs
    val features = listOf(
        Feature("Captura de tela com consentimento (MediaProjection)", "REAL", "Implementada; precisa ser validada no seu aparelho (ver docs/TEST_PLAN.md)."),
        Feature("OCR local (ML Kit)", "REAL", "On-device. Precisão varia com a UI do jogo."),
        Feature("Classificação de tela", "PARCIAL", "Heurística por palavras-chave PT/EN/ES; precisa calibrar com o jogo real."),
        Feature("Leitura de cavaleiros (nome + nível)", "PARCIAL", "Telas de lista/ficha/formação; depende do layout do jogo."),
        Feature("Persistência incremental (Room) e sem apagar dados", "REAL", "Testado em unidade (lógica) — regras de reconciliação."),
        Feature("Importar prints", "REAL", "Mesma pipeline da captura ao vivo."),
        Feature("Correção manual com histórico", "REAL", "Nível, função, posse."),
        Feature("Montador de equipe (só possuídos)", "PARCIAL", "Usa nível + função informada; sem habilidades/Cosmos."),
        Feature("Plano de evolução", "PARCIAL", "Só níveis; custos desconhecidos."),
        Feature("Atualização in-app via GitHub Releases", "REAL", "Requer release publicado pelo CI."),
        Feature("Cosmos, habilidades, sentidos, armaduras", "INDISPONÍVEL", "Não extraídos."),
        Feature("Copiloto de batalha / Pick&Ban / counters", "INDISPONÍVEL", "Não implementado."),
        Feature("Catálogo verificado, banners, eventos, RAG, IA externa", "INDISPONÍVEL", "Sem fonte verificada; sem IA externa (tudo local/grátis)."),
        Feature("Sincronização Supabase", "INDISPONÍVEL", "Opcional; não implementada."),
    )
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { SCard { H("Estado"); Body("Serviço de captura: ${if (live.running) "ativo" else "parado"}")
            Body("Último erro: ${prefs.lastError ?: "nenhum"}")
            Body("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MODEL}")
            Body("Build ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            Body("Pode instalar atualizações: ${if (Updater.canInstall(ctx)) "sim" else "não (permissão pendente)"}")
            TextButton({ prefs.lastError = null }) { Text("Limpar último erro") } } }
        item {
            SCard(accent = C.celeste) {
                H("Última tela lida (texto do OCR)")
                Body("Tipo: ${live.lastKind ?: "—"} · linhas: ${live.lastLineCount} · pontuação de classificação: ${"%.1f".format(live.lastScore)} · captura: ${live.captureSize ?: "—"} · quadros recebidos: ${live.framesAcquired}")
                val sample = if (live.lastText.isNotEmpty()) live.lastText.joinToString("\n") else prefs.lastSample
                if (sample.isNullOrBlank()) Small("Nenhum texto lido ainda. Rode uma leitura ou importe um print.") else { Small("Isto é exatamente o que o app enxergou (use para calibrar):"); Body(sample.take(1500)) }
            }
        }
        items(features) { f -> SCard { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text(f.name, Modifier.weight(1f), color = C.text, fontWeight = FontWeight.SemiBold); StatusChip(f.status) }; Small(f.note) } }
    }
}

@Composable
private fun Sobre() {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SCard { H("Saint Seiya AI Coach"); Body("Copiloto de leitura e recomendação para Saint Seiya Awakening. Você joga; o app só lê a tela autorizada e sugere. Sem automação, sem bots.")
            Small("Projeto pessoal, não afiliado ao publisher. Sem artes oficiais. Dados do jogo vêm só do que você mostra na tela.") } }
    }
}
