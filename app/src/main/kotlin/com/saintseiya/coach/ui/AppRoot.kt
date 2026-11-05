package com.saintseiya.coach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.res.Configuration

class Actions(val onStart: () -> Unit, val onStop: () -> Unit, val onPickImages: () -> Unit)

/** Navegação com estado preservado: aba principal + sub-aba por tela. */
class Nav {
    var tab by mutableIntStateOf(0)
    val sub = mutableStateMapOf<Int, Int>()
    fun go(t: Int, s: Int = 0) { tab = t; sub[t] = s }
}

enum class MainTab(val label: String, val icon: ImageVector) {
    HOJE("Hoje", Icons.Filled.Home),
    CONTA("Minha Conta", Icons.Filled.Person),
    EQUIPES("Equipes", Icons.Filled.Face),
    BATALHAS("Batalhas", Icons.Filled.Star),
    EVOLUCAO("Evolução", Icons.Filled.KeyboardArrowUp),
    RECURSOS("Recursos", Icons.Filled.ShoppingCart),
    APRENDIZADO("Aprendizado", Icons.Filled.DateRange),
    CONFIG("Configurações", Icons.Filled.Settings),
}

@Composable
fun AppRoot(vm: AppViewModel, actions: Actions) {
    val nav = remember { Nav() }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val account = vm.accounts.collectAsState().value.firstOrNull { it.id == vm.activeAccount.collectAsState().value }
    val live by vm.live.collectAsState()

    Surface(color = C.bg, modifier = Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            if (landscape) {
                NavigationRail(containerColor = C.surface, modifier = Modifier.fillMaxHeight()) {
                    Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                        MainTab.entries.forEach { t ->
                            NavigationRailItem(selected = nav.tab == t.ordinal, onClick = { nav.go(t.ordinal, nav.sub[t.ordinal] ?: 0) },
                                icon = { Icon(t.icon, t.label) }, label = { Text(t.label, fontSize = 10.sp, maxLines = 1) },
                                colors = NavigationRailItemDefaults.colors(selectedIconColor = C.bg, indicatorColor = C.gold, selectedTextColor = C.gold, unselectedIconColor = C.muted, unselectedTextColor = C.muted))
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                TopStatus(account?.name ?: "—", account?.server, live.running, live.lastKind)
                Box(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    when (MainTab.entries[nav.tab]) {
                        MainTab.HOJE -> HojeScreen(vm, actions, nav)
                        MainTab.CONTA -> ContaScreen(vm, nav)
                        MainTab.EQUIPES -> EquipesScreen(vm, nav)
                        MainTab.BATALHAS -> BatalhasScreen(vm, nav)
                        MainTab.EVOLUCAO -> EvolucaoScreen(vm, nav)
                        MainTab.RECURSOS -> RecursosScreen(vm, nav)
                        MainTab.APRENDIZADO -> AprendizadoScreen(vm, nav)
                        MainTab.CONFIG -> ConfigScreen(vm, nav)
                    }
                }
                if (!landscape) {
                    Row(Modifier.fillMaxWidth().background(C.surface).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.SpaceEvenly) {
                        MainTab.entries.forEach { t ->
                            val sel = nav.tab == t.ordinal
                            Column(Modifier.clickable { nav.go(t.ordinal, nav.sub[t.ordinal] ?: 0) }.widthIn(min = 76.dp).heightIn(min = 56.dp).padding(horizontal = 6.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Icon(t.icon, t.label, tint = if (sel) C.gold else C.muted)
                                Text(t.label, fontSize = 10.sp, maxLines = 1, color = if (sel) C.gold else C.muted, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopStatus(account: String, server: String?, running: Boolean, lastKind: String?) {
    Row(Modifier.fillMaxWidth().background(C.surface).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Saint Seiya AI Coach", color = C.gold, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("$account · ${server ?: "servidor desconhecido"}", color = C.muted, fontSize = 12.sp, maxLines = 1)
        }
        if (running) Badge("● LENDO${lastKind?.let { " · $it" } ?: ""}", C.ok) else Badge("captura parada", C.muted)
    }
}
