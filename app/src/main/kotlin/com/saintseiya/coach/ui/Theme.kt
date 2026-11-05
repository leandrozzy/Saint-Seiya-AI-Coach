package com.saintseiya.coach.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object C {
    val bg = Color(0xFF0B1020)
    val surface = Color(0xFF141B30)
    val surface2 = Color(0xFF1C2542)
    val gold = Color(0xFFE8B84B)
    val celeste = Color(0xFF5CC8FF)
    val text = Color(0xFFEAF0FF)
    val muted = Color(0xFF9AA7C7)
    val ok = Color(0xFF5FD38D)
    val warn = Color(0xFFFFB454)
    val bad = Color(0xFFFF6B6B)
}

private val scheme = darkColorScheme(
    primary = C.gold, onPrimary = Color(0xFF1A1405),
    secondary = C.celeste, onSecondary = Color(0xFF00212E),
    background = C.bg, onBackground = C.text,
    surface = C.surface, onSurface = C.text,
    surfaceVariant = C.surface2, onSurfaceVariant = C.muted,
    error = C.bad, outline = Color(0xFF3A4670),
)

@Composable
fun CoachTheme(content: @Composable () -> Unit) {
    // Tema escuro único (grafite/azul profundo + dourado/celeste): contraste AA sobre o fundo.
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
