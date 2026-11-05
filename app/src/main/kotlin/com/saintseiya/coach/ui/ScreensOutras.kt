package com.saintseiya.coach.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saintseiya.coach.core.*

@Composable
fun Unavailable(title: String, status: String, why: String, real: String? = null) {
    SCard(accent = if (status == "PARCIAL") C.warn else C.bad) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { H(title); StatusChip(status) }
        Body(why)
        real?.let { Small("O que já funciona: $it") }
    }
}

// ---------------- Equipes ----------------
@Composable
fun EquipesScreen(vm: AppViewModel, nav: Nav) {
    val titles = listOf("Construtor", "Salvas", "Comparar", "PvP / PvE / Chefes", "Sinergias / Velocidade")
    val sel = nav.sub[2] ?: 0
    Column {
        SectionTabs(titles, sel) { nav.sub[2] = it }
        Spacer(Modifier.height(8.dp))
        when (sel) {
            0 -> Construtor(vm)
            1 -> SavedTeams(vm, compare = false)
            2 -> SavedTeams(vm, compare = true)
            3 -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Unavailable("Equipes por modo (PvP, PvE, Chefes)", "INDISPONÍVEL", "Exigem regras e dados de habilidades verificados por modo, que ainda não estão no catálogo. Sem eles o app não finge ter um time 'ideal' por modo.", "Construtor usa seus cavaleiros confirmados, nível e função informada.") }
            }
            else -> LazyColumn { item { Unavailable("Sinergias, ordem de ação e velocidade", "INDISPONÍVEL", "Dependem de habilidades, atributos e Cosmos lidos/verificados. A leitura de ficha ainda extrai só nome e nível.", null) } }
        }
    }
}

@Composable
private fun Construtor(vm: AppViewModel) {
    val s by vm.teamSuggestion.collectAsState()
    var saved by remember { mutableStateOf(false) }
    var swapIdx by rememberSaveable { mutableStateOf<Int?>(null) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            SCard(accent = C.gold) {
                H("Equipe sugerida (somente cavaleiros que você possui)")
                Confidence(s.confidence)
                if (!s.complete) Badge("preliminar", C.warn)
                s.warnings.forEach { Small("⚠ $it") }
            }
        }
        if (s.members.isEmpty()) item { Empty("Sem cavaleiros confirmados", "Faça uma leitura pela lista de cavaleiros ou adicione manualmente em Minha Conta.") }
        items(s.members.size) { i ->
            val m = s.members[i]
            SCard(accent = if (swapIdx == i) C.celeste else null) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${i + 1}. ${m.displayName}", fontWeight = FontWeight.SemiBold, color = C.text)
                        Small("Nível ${m.level ?: "desconhecido"} · Função ${m.role?.label ?: "desconhecida"}")
                    }
                    TextButton({ swapIdx = if (swapIdx == i) null else i }) { Text("Substituir?") }
                }
                if (swapIdx == i) {
                    if (s.bench.isEmpty()) Small("Sem substitutos possuídos confirmados.")
                    s.bench.forEach { b ->
                        val d = if (b.level != null && m.level != null) b.level!! - m.level!! else null
                        Body("${b.displayName}: nível ${b.level ?: "?"}" + (d?.let { " (${if (it >= 0) "+" else ""}$it vs. atual)" } ?: "") + " · função ${b.role?.label ?: "desconhecida"}", muted = true)
                    }
                    Small("Impacto real em dano/controle não é calculado: faltam habilidades e atributos lidos. Custo de evolução: desconhecido.")
                }
            }
        }
        if (s.members.isNotEmpty()) {
            item { H("Por que estes?"); s.explanation.forEach { Body("• $it") } }
            item {
                Button({ vm.saveTeam("Equipe sugerida", s.members.map { it.key }, s.warnings.firstOrNull()); saved = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (saved) "Salvo (nova versão)" else "Salvar como nova versão") }
                Small("Salvar nunca sobrescreve: cada vez cria uma versão nova.")
            }
        }
        if (s.bench.isNotEmpty()) item { H("Banco (substitutos possuídos)"); Body(s.bench.joinToString { "${it.displayName} (Lv.${it.level ?: "?"})" }, muted = true) }
    }
}

@Composable
private fun SavedTeams(vm: AppViewModel, compare: Boolean) {
    val teams by vm.teams.collectAsState(); val knights by vm.knights.collectAsState()
    var a by rememberSaveable { mutableStateOf<Long?>(null) }; var b by rememberSaveable { mutableStateOf<Long?>(null) }
    fun names(csv: String) = csv.split(",").map { k -> knights.firstOrNull { it.key == k } }
    if (teams.isEmpty()) { Empty("Nenhuma equipe salva", "Salve uma sugestão no Construtor."); return }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        items(teams, key = { it.id }) { t ->
            SCard(accent = if (compare && (t.id == a || t.id == b)) C.celeste else null) {
                Text("${t.name} · v${t.version}", fontWeight = FontWeight.SemiBold, color = C.text); Small(fmtTime(t.createdAt))
                Body(names(t.memberKeys).joinToString { it?.let { k -> "${k.displayName} (Lv.${k.level ?: "?"})" } ?: "?" })
                if (compare) TextButton({ if (a == null || (a != null && b != null)) { a = t.id; b = null } else b = t.id }) { Text("Selecionar para comparar") }
            }
        }
        if (compare) {
            val ta = teams.firstOrNull { it.id == a }; val tb = teams.firstOrNull { it.id == b }
            item {
                if (ta == null || tb == null) Small("Selecione duas versões.") else {
                    val ka = ta.memberKeys.split(",").toSet(); val kb = tb.memberKeys.split(",").toSet()
                    SCard(accent = C.celeste) {
                        H("Diferenças")
                        Body("Só na primeira: ${(ka - kb).joinToString { k -> knights.firstOrNull { it.key == k }?.displayName ?: k }.ifBlank { "—" }}")
                        Body("Só na segunda: ${(kb - ka).joinToString { k -> knights.firstOrNull { it.key == k }?.displayName ?: k }.ifBlank { "—" }}")
                        Small("Sem dados de habilidades/atributos, a comparação é só de composição e nível.")
                    }
                }
            }
        }
    }
}

// ---------------- Batalhas ----------------
@Composable
fun BatalhasScreen(vm: AppViewModel, nav: Nav) {
    val titles = listOf("Histórico", "Copiloto", "Pick & Ban", "Analisar adversário", "Counters")
    val sel = nav.sub[3] ?: 0
    val battles by vm.battles.collectAsState()
    Column {
        SectionTabs(titles, sel) { nav.sub[3] = it }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            when (sel) {
                0 -> {
                    item {
                        val w = battles.count { it.outcome == "VICTORY" }; val l = battles.count { it.outcome == "DEFEAT" }
                        SCard { H("Resultados observados"); Body("Vitórias: $w · Derrotas: $l · Total observado: ${battles.size}")
                            Small(if (battles.size < 20) "Amostra pequena (${battles.size}): não indica causa nem tendência confiável." else "Amostra moderada; ainda sem causalidade.")
                            Small("O app registra só 'vitória/derrota' quando a tela de resultado é lida. Modo, equipe e dano ainda não são extraídos.") }
                    }
                    if (battles.isEmpty()) item { Empty("Nenhuma batalha observada", "Com a leitura ligada, telas de Vitória/Derrota são registradas automaticamente.") }
                    items(battles, key = { it.id }) { b -> SCard { Text(if (b.outcome == "VICTORY") "Vitória" else "Derrota", color = if (b.outcome == "VICTORY") C.ok else C.bad, fontWeight = FontWeight.SemiBold); Small(fmtTime(b.observedAt)) } }
                }
                1 -> item { Unavailable("Copiloto em batalha", "INDISPONÍVEL", "Recomendações em tempo real exigem reconhecer unidades, energia e efeitos em combate e (opcionalmente) um cartão flutuante. Isso não foi construído; o app não finge fazê-lo.", "Detecta telas de resultado (vitória/derrota) e registra no histórico. Nunca toca no jogo.") }
                2 -> item { Unavailable("Pick & Ban", "PARCIAL", "A tela de Pick & Ban é reconhecida como tipo de tela; picks/bans visíveis ainda NÃO são extraídos nem recomendados.", "Cavaleiros vistos aí nunca viram 'possuídos'.") }
                3 -> item { Unavailable("Analisar adversário", "INDISPONÍVEL", "Sem leitura de formação adversária nem base verificada de habilidades, qualquer análise seria invenção.") }
                else -> item { Unavailable("Counters", "INDISPONÍVEL", "Exige base de conhecimento verificada (fontes datadas por servidor). Ver docs/GAME_KNOWLEDGE_SOURCES.md.") }
            }
        }
    }
}

// ---------------- Evolução ----------------
@Composable
fun EvolucaoScreen(vm: AppViewModel, nav: Nav) {
    val titles = listOf("Plano de evolução", "Habilidades / Sentidos / Armaduras", "Cosmos", "Metas")
    val sel = nav.sub[4] ?: 0
    val advice by vm.advice.collectAsState()
    Column {
        SectionTabs(titles, sel) { nav.sub[4] = it }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            when (sel) {
                0 -> {
                    item { SCard { H("Plano baseado só em dados lidos"); Body("Hoje o plano usa níveis lidos e a equipe sugerida. Custos de materiais ainda são DESCONHECIDOS (não há leitura de materiais) — por isso a confiança é baixa e o custo vem marcado como desconhecido.") } }
                    if (advice.isEmpty()) item { Empty("Sem itens no plano", "Leia sua coleção para gerar prioridades.") }
                    items(advice, key = { it.id }) { AdviceCard(it) }
                }
                1 -> item { Unavailable("Habilidades, Sétimo/Oitavo Sentido, Armaduras", "INDISPONÍVEL", "A leitura de ficha extrai nome e nível; níveis de habilidade, sentidos e armaduras ainda não são lidos.") }
                2 -> item { Unavailable("Recomendação de Cosmos", "INDISPONÍVEL", "Cosmos ainda não são extraídos (só a tela é reconhecida).") }
                else -> item { Unavailable("Metas e cálculo de custo", "INDISPONÍVEL", "Sem custos verificados do jogo, o app não calcula necessidades de recursos para não inventar números.") }
            }
        }
    }
}

// ---------------- Recursos ----------------
@Composable
fun RecursosScreen(vm: AppViewModel, nav: Nav) {
    val titles = listOf("Carteira", "Planejador", "Invocações / Banners", "Eventos / Farm", "Alertas")
    val sel = nav.sub[5] ?: 0
    val res by vm.resources.collectAsState()
    Column {
        SectionTabs(titles, sel) { nav.sub[5] = it }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            when (sel) {
                0 -> {
                    item { SCard { H("Carteira (última leitura)"); Small("Valores têm data da leitura; podem estar desatualizados. Só entram itens com rótulo de texto legível.") } }
                    if (res.isEmpty()) item { Empty("Nada lido ainda", "Abra o inventário com a leitura ligada.") }
                    items(res, key = { it.key }) { r -> SCard { Row { Column(Modifier.weight(1f)) { Text(r.label, color = C.text); Small("lido ${fmtTime(r.observedAt)}") }; Text("%,d".format(r.value), color = C.gold, fontWeight = FontWeight.Bold) } } }
                }
                1 -> item { Unavailable("Planejador", "INDISPONÍVEL", "Precisa de custos verificados e leitura de materiais.") }
                2 -> item { Unavailable("Invocações e banners", "INDISPONÍVEL", "Sem fonte verificada por servidor/região. Nunca haverá promessa de taxa ou garantia sem regra confirmada.") }
                3 -> item { Unavailable("Eventos e farm", "INDISPONÍVEL", "Sem fonte verificada de calendário para o seu servidor.") }
                else -> item { Unavailable("Alertas e notificações", "INDISPONÍVEL", "Notificações de tarefas ainda não implementadas.") }
            }
        }
    }
}

// ---------------- Aprendizado ----------------
@Composable
fun AprendizadoScreen(vm: AppViewModel, nav: Nav) {
    val titles = listOf("Recomendações dadas", "Qualidade das leituras", "Linha do tempo")
    val sel = nav.sub[6] ?: 0
    val rec by vm.recLog.collectAsState(); val obs by vm.observations.collectAsState(); val sessions by vm.sessions.collectAsState(); val knights by vm.knights.collectAsState()
    Column {
        SectionTabs(titles, sel) { nav.sub[6] = it }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            when (sel) {
                0 -> {
                    item { Small("Guardamos o conselho, os dados usados e a confiança. O app NÃO assume que você seguiu o conselho: ainda não há detecção de ação posterior.") }
                    if (rec.isEmpty()) item { Empty("Sem histórico", "Os conselhos exibidos em Hoje/Evolução ficam registrados aqui.") }
                    items(rec, key = { it.id }) { r -> SCard { Text(r.title, color = C.text, fontWeight = FontWeight.SemiBold); Small("${fmtTime(r.shownAt)} · dados: ${r.dataUsed} · confiança ${(r.confidence * 100).toInt()}%") } }
                }
                1 -> {
                    item {
                        val total = sessions.sumOf { it.framesProcessed }; val readable = sessions.sumOf { it.framesReadable }
                        SCard { H("Qualidade das leituras"); Body("Telas analisadas: $total · com texto reconhecível: $readable")
                            val byKind = obs.groupingBy { it.kind }.eachCount().entries.sortedByDescending { it.value }
                            byKind.forEach { (k, n) -> Small("${runCatching { ScreenKind.valueOf(k).label }.getOrDefault(k)}: $n") }
                            val unknown = obs.count { it.kind == "UNKNOWN" }
                            if (obs.isNotEmpty()) Small("Não reconhecidas: $unknown de ${obs.size} (últimas ${obs.size}). Muitas 'não reconhecidas' indicam que as palavras-chave precisam de calibração com o jogo real.")
                            Small("Confiança média dos cavaleiros: ${if (knights.isEmpty()) "—" else "${(knights.map { it.confidence }.average() * 100).toInt()}%"}") }
                    }
                }
                else -> {
                    if (sessions.isEmpty()) item { Empty("Sem eventos", "Sessões e mudanças aparecem em ordem cronológica.") }
                    items(sessions, key = { it.id }) { s -> SCard { Text(fmtTime(s.startedAt), color = C.gold); Body("${if (s.source == "LIVE") "Leitura" else "Importação"}: +${s.knightsNew} novos, ${s.knightsUpdated} atualizados") } }
                }
            }
        }
    }
}
