package com.saintseiya.coach.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

fun fmtTime(t: Long?): String = if (t == null || t == 0L) "—" else SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(Date(t))

@Composable
fun SCard(modifier: Modifier = Modifier, accent: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = C.surface),
        border = BorderStroke(1.dp, accent?.copy(alpha = 0.55f) ?: Color(0xFF26325A)),
    ) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content) }
}

@Composable fun H(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, color = C.gold, fontWeight = FontWeight.SemiBold)
@Composable fun Body(text: String, muted: Boolean = false) = Text(text, style = MaterialTheme.typography.bodyMedium, color = if (muted) C.muted else C.text)
@Composable fun Small(text: String) = Text(text, fontSize = 12.sp, color = C.muted)

@Composable
fun Badge(text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Confidence(c: Double) {
    val (label, col) = when {
        c >= 0.75 -> "alta" to C.ok
        c >= 0.45 -> "média" to C.warn
        else -> "baixa" to C.bad
    }
    Badge("confiança $label (${(c * 100).toInt()}%)", col)
}

@Composable
fun Empty(title: String, hint: String) {
    SCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.Info, null, tint = C.celeste)
            Column { Text(title, fontWeight = FontWeight.SemiBold, color = C.text); Small(hint) }
        }
    }
}

@Composable
fun StatusChip(status: String) {
    when (status) {
        "REAL" -> Badge("REAL", C.ok)
        "PARCIAL" -> Badge("PARCIAL", C.warn)
        else -> Badge("INDISPONÍVEL", C.bad)
    }
}

@Composable
fun SectionTabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    ScrollableTabRow(selectedTabIndex = selected, containerColor = Color.Transparent, edgePadding = 0.dp, contentColor = C.gold) {
        titles.forEachIndexed { i, t -> Tab(selected == i, { onSelect(i) }, text = { Text(t, maxLines = 1) }) }
    }
}
