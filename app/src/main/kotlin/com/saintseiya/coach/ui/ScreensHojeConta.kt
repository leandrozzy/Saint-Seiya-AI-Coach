package com.saintseiya.coach.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saintseiya.coach.capture.CaptureState
import com.saintseiya.coach.capture.GameLauncher
import com.saintseiya.coach.capture.Bubble
import com.saintseiya.coach.CoachApp
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.saintseiya.coach.core.*
import com.saintseiya.coach.data.KnightEntity

@Composable
fun AdviceCard(a: Advice) {
    var open by rememberSaveable(a.id) { mutableStateOf(false) }
    SCard(accent = C.gold) {
        Text(a.title, fontWeight = FontWeight.SemiBold, color = C.text)
        Confidence(a.confidence)
        Body("Ganho: ${a.benefit}", muted = true)
        TextButton(onClick = { open = !open }) { Text(if (open) "Ocultar" else "Por quê?") }
        if (open) {
            Body(a.why)
            Small("Dados usados: ${a.dataUsed}")
            Small("Custo: ${a.cost}")
            Small("Alternativa: nenhuma identificada com os dados atuais.")
        }
    }
}

@Composable
fun HojeScreen(vm: AppViewModel, act: Actions, nav: Nav) {
    val live by vm.live.collectAsState()
    val report by vm.report.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val knights by vm.knights.collectAsState()
    val advice by vm.advice.collectAsState()
    val visited by vm.visited.collectAsState()
    val issues by vm.issues.collectAsState()
    val imp by vm.importState.collectAsState()
    val team by vm.teamSuggestion.collectAsState()
    val totalOwned by vm.totalOwned.collectAsState()
    val confirmed = knights.count { it.status == "CONFIRMED" }
    val candidates = knights.count { it.status == "CANDIDATE" }
    var showLast by remember { mutableStateOf(false) }
    var showHow by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val prefs = CoachApp.instance.prefs
    var gamePkg by remember { mutableStateOf(prefs.gamePackage) }
    var picker by remember { mutableStateOf(false) }
    var bubbleOn by remember { mutableStateOf(prefs.bubble) }
    val upd by vm.update.collectAsState()
    if (picker) GamePicker(ctx, onPick = { prefs.gamePackage = it; gamePkg = it; picker = false }, onDismiss = { picker = false })
    if (showHow) AlertDialog(onDismissRequest = { showHow = false },
        title = { Text("Antes de começar") },
        text = { Text("Na janela do Android, escolha “Tela inteira” e toque em “Iniciar agora”.\n\nNo jogo: Cavaleiros › Gerenciar (o app lê o “Total possuído”). Depois toque em “Aprimorar” e use a seta ‹ › ao lado do personagem: cada toque mostra o próximo cavaleiro e o app lê nome, nível, estrelas e atributos. Deixe cada tela ~1 s parada. Por fim abra o Inventário (toque nos itens para ler a QTDE).") },
        confirmButton = { TextButton({ showHow = false; if (gamePkg != null) act.onStartAndPlay() else act.onStart() }) { Text(if (gamePkg != null) "Iniciar e abrir o jogo" else "Entendi, iniciar") } },
        dismissButton = { TextButton({ showHow = false }) { Text("Cancelar") } })

    report?.let { r ->
        AlertDialog(onDismissRequest = { CaptureState.setReport(null) },
            confirmButton = { TextButton({ CaptureState.setReport(null); if (!r.noData) nav.go(1, 1) }) { Text(if (r.noData) "OK" else "Ver conta") } },
            title = { Text(if (r.noData) "Nenhum dado capturado" else "Sessão encerrada") },
            text = { ReportBody(r.reason, r.seen, r.processed, r.readable, r.newK, r.updK, r.issues) })
    }
    if (showLast) sessions.firstOrNull()?.let { s ->
        AlertDialog(onDismissRequest = { showLast = false }, confirmButton = { TextButton({ showLast = false }) { Text("Fechar") } },
            title = { Text("Última sessão (${fmtTime(s.startedAt)})") },
            text = { ReportBody(s.endReason ?: "em andamento", s.framesSeen, s.framesProcessed, s.framesReadable, s.knightsNew, s.knightsUpdated, s.issuesOpened) })
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
        (upd.result as? com.saintseiya.coach.update.UpdateCheck.Available)?.let { a ->
            item { SCard(accent = C.celeste, modifier = Modifier.clickable { nav.go(7, 2) }) { H("Nova versão disponível"); Body("${a.info.name.ifBlank { a.info.tag }} — toque para atualizar (seus dados são mantidos).") } }
        }
        item {
            SCard(accent = if (live.running) C.ok else null) {
                if (live.running) {
                    H("Leitura em andamento")
                    Body("Telas vistas: ${live.framesSeen} · alteradas: ${live.framesChanged} · analisadas: ${live.framesProcessed}")
                    Body("Cavaleiros novos: ${live.knightsNew} · atualizados: ${live.knightsUpdated}")
                    Body("Quadros recebidos do Android: ${live.framesAcquired}${live.captureSize?.let { " ($it)" } ?: ""}", muted = true)
                    Body("Última tela: ${live.lastKind ?: "—"} · linhas de texto lidas: ${live.lastLineCount}", muted = true)
                    if (live.framesAcquired == 0) Small("⚠ Nenhum quadro recebido ainda. Se persistir, encerre e inicie de novo escolhendo “Tela inteira”.")
                    if (live.lastFrameBlack) Small("⚠ O quadro está preto: o jogo pode bloquear captura de tela ou foi escolhido o app errado.")
                    if (live.framesAcquired > 3 && live.framesProcessed > 0 && live.lastLineCount == 0) Small("⚠ Quadros chegam mas o OCR não achou texto. Veja Configurações → Diagnóstico.")
                    live.lastMessage?.let { Small(it) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = act.onStop, colors = ButtonDefaults.buttonColors(containerColor = C.bad), modifier = Modifier.heightIn(min = 48.dp)) { Text("Encerrar sessão") }
                        if (gamePkg != null) OutlinedButton(onClick = act.onOpenGame, modifier = Modifier.heightIn(min = 48.dp)) { Text("Abrir jogo") }
                    }
                } else {
                    H(if (confirmed == 0) "Vamos começar" else "Pronto para ler")
                    Body(if (confirmed == 0) "Toque em Iniciar leitura, autorize a captura, abra o jogo e navegue pela lista de cavaleiros. O Coach lê só o que aparece na tela, no próprio aparelho."
                    else "Inicie uma leitura e navegue pelo jogo; o que não for visto é mantido como está.")
                    live.lastMessage?.let { Small(it) }
                    FlowRowButtons(act, gamePkg?.let { GameLauncher.label(ctx, it) ?: it }, onStart = { showHow = true }, onPickGame = { picker = true },
                        onLast = { showLast = true }, hasLast = sessions.isNotEmpty(), onPend = { nav.go(1, 5) })
                }
            }
        }
        if (imp.running || imp.finished) item {
            SCard {
                H(if (imp.running) "Importando capturas…" else "Importação concluída")
                LinearProgressIndicator(progress = { if (imp.total == 0) 0f else imp.done.toFloat() / imp.total }, modifier = Modifier.fillMaxWidth())
                Body("${imp.done}/${imp.total} imagens · legíveis: ${imp.readable} · cavaleiros novos: ${imp.newK} · atualizados: ${imp.updK}")
                imp.error?.let { Small(it) }
                if (imp.finished && imp.readable == 0) Body("Nenhum dado capturado destas imagens.")
                if (imp.running) TextButton(vm::cancelImport) { Text("Cancelar") } else TextButton(vm::clearImport) { Text("Fechar") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                val pct = Recommender.coveragePercent(visited)
                Box(Modifier.weight(1f)) { SCard { Small("Conta analisada"); Text("$pct%", color = C.celeste, style = MaterialTheme.typography.headlineMedium); Small("${Recommender.coverageChecklist.count { it.first in visited }}/${Recommender.coverageChecklist.size} telas-chave lidas") } }
                Box(Modifier.weight(1f)) { SCard { Small("Cavaleiros confirmados"); Text("$confirmed", color = C.gold, style = MaterialTheme.typography.headlineMedium); Small(when { totalOwned != null -> "de $totalOwned informados pelo jogo"; candidates > 0 -> "+$candidates a confirmar"; else -> "lidos do seu jogo" }) } }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f)) { SCard { Small("Equipes prontas"); Text(if (team.complete) "1" else "0", color = C.gold, style = MaterialTheme.typography.headlineMedium); Small(if (team.complete) "sugestão de 6 disponível" else "faltam cavaleiros confirmados") } }
                Box(Modifier.weight(1f)) { SCard { Small("Pendências"); Text("${issues.size + candidates}", color = if (issues.size + candidates > 0) C.warn else C.ok, style = MaterialTheme.typography.headlineMedium); Small("leituras a revisar") } }
            }
        }
        item {
            SCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Bolha flutuante durante a leitura", color = C.text); Small("Contador e botão Parar sobre o jogo (arrastável). Não toca no jogo.") }
                    Switch(bubbleOn, { bubbleOn = it; prefs.bubble = it })
                }
                if (bubbleOn && !Bubble.canShow(ctx)) {
                    Small("Falta a permissão “Sobrepor a outros apps”.")
                    OutlinedButton({ ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Permitir") }
                }
            }
        }
        item { H("Seu treinador recomenda") }
        if (advice.isEmpty()) item { Empty("Sem recomendações agora", "Quando houver dados suficientes, as prioridades aparecem aqui.") }
        items(advice.take(4), key = { it.id }) { AdviceCard(it) }
        item { H("O que fazer hoje") }
        item { Empty("Sem fonte verificada para tarefas/eventos", "O app não inventa calendário. Eventos, banners e tarefas só aparecem quando houver fonte verificada para o seu servidor (ainda não implementado).") }
    }
}

@Composable
private fun FlowRowButtons(act: Actions, gameLabel: String?, onStart: () -> Unit, onPickGame: () -> Unit, onLast: () -> Unit, hasLast: Boolean, onPend: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text(if (gameLabel != null) "Iniciar leitura e abrir $gameLabel" else "Iniciar leitura")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (gameLabel != null) OutlinedButton(onClick = act.onOpenGame, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Abrir jogo") }
            OutlinedButton(onClick = onPickGame, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(if (gameLabel == null) "Escolher o jogo" else "Trocar jogo", maxLines = 1) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = act.onPickImages, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Importar prints", maxLines = 1) }
            OutlinedButton(onClick = onLast, enabled = hasLast, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Última sessão", maxLines = 1) }
            OutlinedButton(onClick = onPend, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Pendências", maxLines = 1) }
        }
    }
}

@Composable
private fun GamePicker(ctx: android.content.Context, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val all = remember { GameLauncher.launchable(ctx) }
    val guess = remember { GameLauncher.guess(all) }
    var q by remember { mutableStateOf("") }
    val list = (if (q.isBlank()) guess + all.filter { it !in guess } else all.filter { Norm.fold(it.label).contains(Norm.fold(q)) })
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onDismiss) { Text("Fechar") } },
        title = { Text("Qual é o jogo?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (guess.isNotEmpty() && q.isBlank()) Small("Sugestões no topo (nome parecido com Saint Seiya).")
                OutlinedTextField(q, { q = it }, label = { Text("Pesquisar app") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(list, key = { it.pkg }) { a ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(a.pkg) }.padding(vertical = 10.dp)) {
                            Text(a.label, color = if (a in guess) C.gold else C.text, fontWeight = FontWeight.SemiBold); Small(a.pkg)
                        }
                    }
                }
            }
        })
}

@Composable
fun ReportBody(reason: String, seen: Int, processed: Int, readable: Int, newK: Int, updK: Int, issues: Int) {
    val why = when (reason) {
        "USER" -> "Encerrada por você"
        "PROJECTION_REVOKED" -> "A autorização de captura foi encerrada pelo sistema ou por você (barra de status). Inicie novamente para continuar."
        "INTERRUPTED" -> "O app foi fechado/morto durante a sessão"
        "DONE" -> "Concluída"
        else -> reason
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Body(why)
        if (processed == 0 || readable == 0) Body("Nenhum dado capturado: nenhuma tela com texto reconhecível foi analisada.")
        Body("Telas vistas: $seen · analisadas: $processed · legíveis: $readable")
        Body("Capturado (novo): $newK · atualizado: $updK · pendências: $issues")
    }
}

// ---------------- Minha Conta ----------------
@Composable
fun ContaScreen(vm: AppViewModel, nav: Nav) {
    val titles = listOf("Visão geral", "Cavaleiros", "Cosmos", "Recursos", "Formações", "Pendências", "Histórico")
    val sel = nav.sub[1] ?: 0
    Column {
        SectionTabs(titles, sel) { nav.sub[1] = it }
        Spacer(Modifier.height(8.dp))
        when (sel) {
            0 -> ContaGeral(vm)
            1 -> Cavaleiros(vm)
            2 -> CosmosInfo(vm)
            3 -> ResourcesList(vm)
            4 -> FormacoesSalvas(vm)
            5 -> Pendencias(vm)
            else -> HistoricoCapturas(vm)
        }
    }
}

@Composable
private fun ContaGeral(vm: AppViewModel) {
    val knights by vm.knights.collectAsState(); val visited by vm.visited.collectAsState(); val total by vm.totalOwned.collectAsState()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            SCard(accent = C.gold) {
                H("Coleção de cavaleiros")
                val read = knights.count { it.status == "CONFIRMED" }
                if (total != null) {
                    Body("Lidos: $read de $total (total informado pelo próprio jogo)")
                    LinearProgressIndicator(progress = { (read.toFloat() / total!!).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                } else Body("Lidos: $read. Abra Casa dos Cavaleiros › Gerenciar para o app ler o total possuído.")
            }
        }
        item {
            SCard {
                H("Cobertura da conta")
                Body("Confirmados: ${knights.count { it.status == "CONFIRMED" }} · Candidatos: ${knights.count { it.status == "CANDIDATE" }} · Vistos fora da coleção: ${knights.count { it.status == "SEEN_ELSEWHERE" }}")
                Small("Cobertura = telas-chave já lidas ÷ total do checklist (métrica explícita, não estimativa).")
            }
        }
        items(Recommender.coverageChecklist) { (k, name) ->
            val ok = k in visited
            SCard { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning, null, tint = if (ok) C.ok else C.warn)
                Column(Modifier.weight(1f)) { Text(name, color = C.text); Small(if (ok) "já lida" else "ainda não lida — visite esta tela com a leitura ligada") }
                if (k == ScreenKind.COSMOS || k == ScreenKind.INVENTORY || k == ScreenKind.TEAM_FORMATION) Badge("só reconhece a tela", C.warn)
            } }
        }
    }
}

@Composable
private fun Cavaleiros(vm: AppViewModel) {
    val knights by vm.knights.collectAsState()
    var q by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("CONFIRMED") }
    var selected by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    val list = knights.filter { (filter == "ALL" || it.status == filter) && (q.isBlank() || Norm.fold(it.displayName).contains(Norm.fold(q))) }
    selected?.let { key -> knights.firstOrNull { it.key == key }?.let { KnightDialog(vm, it) { selected = null } } }
    if (adding) AddKnightDialog({ adding = false }) { n, l -> vm.addKnight(n, l); adding = false }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(q, { q = it }, label = { Text("Pesquisar") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Filled.Search, null) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("CONFIRMED" to "Confirmados", "CANDIDATE" to "Candidatos", "SEEN_ELSEWHERE" to "Vistos", "ALL" to "Todos").forEach { (k, l) ->
                FilterChip(filter == k, { filter = k }, label = { Text(l, maxLines = 1) })
            }
        }
        TextButton({ adding = true }) { Icon(Icons.Filled.Add, null); Text("Adicionar manualmente") }
        if (list.isEmpty()) Empty("Nenhum cavaleiro aqui", "Rode uma leitura passando pela lista de cavaleiros (ou importe prints). Nada é inventado: a lista só mostra o que foi lido ou informado por você.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(list, key = { it.key }) { k ->
                SCard(Modifier.clickable { selected = k.key }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(k.displayName, fontWeight = FontWeight.SemiBold, color = C.text)
                            Small("Nível ${k.level ?: "?"}${k.stars?.let { " · ★$it" } ?: ""}${k.rarity?.let { " · $it" } ?: ""} · Função ${k.role?.let { Role.valueOf(it).label } ?: "desconhecida"} · visto ${fmtTime(k.lastSeenAt)}")
                            if (k.pv != null) Small("PV ${k.pv} · ATQ C ${k.atqC ?: "?"} · DEF C ${k.defC ?: "?"} · ATQ F ${k.atqF ?: "?"} · DEF F ${k.defF ?: "?"} · VEL ${k.vel ?: "?"}")
                        }
                        when (k.status) {
                            "CONFIRMED" -> Badge("possuído", C.ok); "CANDIDATE" -> Badge("a confirmar", C.warn); else -> Badge("não possuído", C.muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddKnightDialog(onDismiss: () -> Unit, onAdd: (String, Int?) -> Unit) {
    var n by remember { mutableStateOf("") }; var l by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Adicionar cavaleiro") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(n, { n = it }, label = { Text("Nome (como aparece no jogo)") }, singleLine = true)
            OutlinedTextField(l, { l = it.filter(Char::isDigit) }, label = { Text("Nível (opcional)") }, singleLine = true)
        } },
        confirmButton = { TextButton({ onAdd(n, l.toIntOrNull()) }, enabled = Norm.key(n).length >= 3) { Text("Adicionar") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar") } })
}

@Composable
private fun KnightDialog(vm: AppViewModel, k: KnightEntity, onDismiss: () -> Unit) {
    val hist by vm.historyOf(k.key).collectAsState(emptyList())
    var lv by remember(k.key, k.level) { mutableStateOf(k.level?.toString() ?: "") }
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onDismiss) { Text("Fechar") } },
        title = { Text(k.displayName) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Confidence(k.confidence)
                    Small("Primeira leitura ${fmtTime(k.firstSeenAt)} · última ${fmtTime(k.lastSeenAt)} · observações: ${k.sightings} · origem: observação #${k.sourceObservationId ?: "manual"}")
                    if (k.levelLocked) Small("Nível corrigido manualmente (o OCR não sobrescreve).")
                    Body("Estrelas: ${k.stars ?: "desconhecido"} · Raridade: ${k.rarity ?: "desconhecida"}")
                    Body("PV ${k.pv ?: "?"} · ATQ C ${k.atqC ?: "?"} · DEF C ${k.defC ?: "?"}\nATQ F ${k.atqF ?: "?"} · DEF F ${k.defF ?: "?"} · Velocidade ${k.vel ?: "?"}")
                }
                item {
                    OutlinedTextField(lv, { lv = it.filter(Char::isDigit) }, label = { Text("Nível (corrigir)") }, singleLine = true)
                    TextButton({ vm.setLevel(k.key, lv.toIntOrNull()) }) { Text("Salvar nível") }
                }
                item {
                    Small("Função (informada por você; o app ainda não lê isto do jogo)")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Role.entries.forEach { r -> FilterChip(k.role == r.name, { vm.setRole(k.key, if (k.role == r.name) null else r) }, label = { Text(r.label, maxLines = 1) }) }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (k.status != "CONFIRMED") Button({ vm.setStatus(k.key, KnightStatus.CONFIRMED) }) { Text("Eu possuo") }
                        if (k.status != "REJECTED") OutlinedButton({ vm.setStatus(k.key, KnightStatus.REJECTED); onDismiss() }) { Text("Não possuo") }
                    }
                }
                item { H("Histórico de mudanças") }
                if (hist.isEmpty()) item { Small("Sem mudanças registradas.") }
                items(hist) { h -> Small("${fmtTime(h.at)} · ${h.field}: ${h.oldValue ?: "?"} → ${h.newValue ?: "?"} (${if (h.origin == "MANUAL") "manual" else "leitura #${h.observationId}"})") }
            }
        })
}

@Composable
private fun CosmosInfo(vm: AppViewModel) {
    val visited by vm.visited.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SCard(accent = C.warn) {
            Row { H("Cosmos"); Spacer(Modifier.width(8.dp)); StatusChip("PARCIAL") }
            Body("O app reconhece que você abriu a tela de Cosmos (${if (ScreenKind.COSMOS in visited) "já reconhecida" else "ainda não vista"}), mas NÃO extrai peças, atributos nem equipamento ainda.")
            Small("Motivo: precisa de amostras reais da tela do jogo para calibrar o reconhecimento. Registrado como pendência no ROADMAP.")
        }
    }
}

@Composable
private fun ResourcesList(vm: AppViewModel) {
    val res by vm.resources.collectAsState()
    if (res.isEmpty()) Empty("Nenhum recurso lido", "Visite o inventário com a leitura ligada. Valores só aparecem se lidos em texto na tela (ícones sem rótulo não são lidos).")
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(res, key = { it.key }) { r -> SCard { Row { Column(Modifier.weight(1f)) { Text(r.label, color = C.text); Small("lido em ${fmtTime(r.observedAt)} · confiança ${(r.confidence * 100).toInt()}%") }; Text("%,d".format(r.value), color = C.gold, fontWeight = FontWeight.Bold) } } }
    }
}

@Composable
private fun FormacoesSalvas(vm: AppViewModel) {
    val teams by vm.teams.collectAsState(); val knights by vm.knights.collectAsState()
    if (teams.isEmpty()) Empty("Nenhuma formação salva", "Salve uma equipe na aba Equipes. Cada salvamento cria uma nova versão (sem sobrescrever).")
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(teams, key = { it.id }) { t ->
            SCard { Text("${t.name} · v${t.version}", fontWeight = FontWeight.SemiBold, color = C.text); Small(fmtTime(t.createdAt))
                Body(t.memberKeys.split(",").joinToString { key -> knights.firstOrNull { it.key == key }?.displayName ?: key }) }
        }
    }
}

@Composable
private fun Pendencias(vm: AppViewModel) {
    val issues by vm.issues.collectAsState(); val knights by vm.knights.collectAsState()
    val cands = knights.filter { it.status == "CANDIDATE" }
    if (issues.isEmpty() && cands.isEmpty()) Empty("Sem pendências", "Leituras ambíguas ou conflitantes aparecem aqui para você revisar.")
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        if (cands.any { it.level != null }) item { Button({ vm.confirmAllCandidates() }) { Text("Confirmar todos os candidatos com nível") } }
        items(cands, key = { "c" + it.key }) { k ->
            SCard(accent = C.warn) {
                Text("Candidato: ${k.displayName} (Lv.${k.level ?: "?"})", color = C.text, fontWeight = FontWeight.SemiBold)
                Small("Visto ${k.sightings}x em tela de coleção. Nome ainda fora do catálogo — confirme se é um cavaleiro seu e o nome está certo.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ vm.setStatus(k.key, KnightStatus.CONFIRMED) }) { Text("Confirmar") }
                    OutlinedButton({ vm.setStatus(k.key, KnightStatus.REJECTED) }) { Text("Descartar (leitura errada)") }
                }
            }
        }
        items(issues, key = { "i" + it.id }) { i ->
            SCard { Text(i.kind, color = C.warn, fontWeight = FontWeight.SemiBold); Body(i.detail); Small(fmtTime(i.createdAt)); TextButton({ vm.resolveIssue(i.id) }) { Text("Marcar como resolvida") } }
        }
    }
}

@Composable
private fun HistoricoCapturas(vm: AppViewModel) {
    val sessions by vm.sessions.collectAsState(); val obs by vm.observations.collectAsState()
    if (sessions.isEmpty()) Empty("Sem capturas ainda", "Cada sessão de leitura ou importação aparece aqui, com o motivo do encerramento.")
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        items(sessions, key = { "s" + it.id }) { s ->
            SCard { Text("${if (s.source == "LIVE") "Leitura ao vivo" else "Importação"} · ${fmtTime(s.startedAt)}", fontWeight = FontWeight.SemiBold, color = C.text)
                Small("fim: ${s.endedAt?.let { fmtTime(it) } ?: "em andamento"} · motivo: ${s.endReason ?: "—"}")
                Small("telas ${s.framesSeen} · analisadas ${s.framesProcessed} · legíveis ${s.framesReadable} · novos ${s.knightsNew} · atualizados ${s.knightsUpdated}")
                if (s.framesProcessed == 0) Badge("nenhum dado capturado", C.warn) }
        }
        item { H("Últimas telas analisadas") }
        items(obs.take(40), key = { "o" + it.id }) { o ->
            Small("${fmtTime(o.observedAt)} · ${runCatching { ScreenKind.valueOf(o.kind).label }.getOrDefault(o.kind)} (score ${"%.1f".format(o.kindScore)}) · ${o.lineCount} linhas${if (o.summary.isNotBlank()) " · ${o.summary}" else ""}")
        }
    }
}
