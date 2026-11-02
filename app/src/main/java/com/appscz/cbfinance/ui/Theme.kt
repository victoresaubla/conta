package com.appscz.cbfinance.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Paleta tomada del sistema web (base.css, index.css, login.css). */
object CB {
    val Primary = Color(0xFF2C3E50)
    val Secondary = Color(0xFF34495E)
    val Accent = Color(0xFF3498DB)
    val Success = Color(0xFF27AE60)
    val Warning = Color(0xFFF39C12)
    val Danger = Color(0xFFE74C3C)
    val Bg = Color(0xFFECF0F1)
    val DarkText = Color(0xFF2C3E50)

    // KPIs del dashboard
    val Green = Color(0xFF10B759)
    val Amber = Color(0xFFFFB900)
    val Red = Color(0xFFE81123)
    val Blue = Color(0xFF0078D4)
    val Teal = Color(0xFF00B294)
    val Gray = Color(0xFF605E5C)
    val GrayLight = Color(0xFFF3F2F1)
    val Line = Color(0xFFEDEBE9)
    val LoginBlue = Color(0xFF0D6EFD)

    val HeaderBrush: Brush = Brush.linearGradient(listOf(Primary, Secondary))
}

@Composable
fun CBTheme(content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = CB.Primary,
        onPrimary = Color.White,
        secondary = CB.Accent,
        onSecondary = Color.White,
        tertiary = CB.Success,
        background = CB.Bg,
        onBackground = CB.DarkText,
        surface = Color.White,
        onSurface = CB.DarkText,
        surfaceVariant = Color(0xFFF4F6F8),
        onSurfaceVariant = Color(0xFF5D6D7E),
        outline = Color(0xFFBFC9D1),
        error = CB.Danger,
        primaryContainer = Color(0xFFDDE6EE),
        onPrimaryContainer = CB.Primary,
        secondaryContainer = Color(0xFFD6EAF8),
        onSecondaryContainer = Color(0xFF1B4F72)
    )
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(24.dp)
    )
    MaterialTheme(colorScheme = scheme, shapes = shapes, content = content)
}
