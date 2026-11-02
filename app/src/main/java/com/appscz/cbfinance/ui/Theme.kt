package com.appscz.cbfinance.ui

import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Modo claro / oscuro (se recuerda entre aperturas de la app). */
object ThemeState {
    private const val PREFS = "cb_theme"
    var dark by mutableStateOf(false)

    fun load(ctx: Context) {
        dark = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("dark", false)
    }

    fun set(ctx: Context, value: Boolean) {
        dark = value
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("dark", value).apply()
    }
}

/** Paleta tomada del sistema web (base.css, index.css, login.css). Los colores "adaptables" cambian con el modo oscuro. */
object CB {
    val Primary = Color(0xFF2C3E50)
    val Secondary = Color(0xFF34495E)
    val Accent = Color(0xFF3498DB)
    val Success = Color(0xFF27AE60)
    val Warning = Color(0xFFF39C12)
    val Danger = Color(0xFFE74C3C)

    // KPIs del dashboard
    val Green = Color(0xFF10B759)
    val Amber = Color(0xFFFFB900)
    val Red = Color(0xFFE81123)
    val Blue = Color(0xFF0078D4)
    val Teal = Color(0xFF00B294)
    val LoginBlue = Color(0xFF0D6EFD)

    // Adaptables claro/oscuro
    val Bg: Color get() = if (ThemeState.dark) Color(0xFF111A22) else Color(0xFFECF0F1)
    val Card: Color get() = if (ThemeState.dark) Color(0xFF1C2833) else Color.White
    val Title: Color get() = if (ThemeState.dark) Color(0xFFE8EEF3) else Primary
    val Gray: Color get() = if (ThemeState.dark) Color(0xFFA9B4BE) else Color(0xFF605E5C)
    val Line: Color get() = if (ThemeState.dark) Color(0xFF33414E) else Color(0xFFEDEBE9)

    val HeaderBrush: Brush = Brush.linearGradient(listOf(Primary, Secondary))
}

@Composable
fun CBTheme(content: @Composable () -> Unit) {
    val scheme = if (ThemeState.dark) {
        darkColorScheme(
            primary = Color(0xFF3498DB),
            onPrimary = Color.White,
            secondary = CB.Accent,
            onSecondary = Color.White,
            tertiary = CB.Success,
            background = Color(0xFF111A22),
            onBackground = Color(0xFFE8EEF3),
            surface = Color(0xFF1C2833),
            onSurface = Color(0xFFE8EEF3),
            surfaceVariant = Color(0xFF263544),
            onSurfaceVariant = Color(0xFFB6C2CC),
            outline = Color(0xFF52667A),
            error = CB.Danger,
            primaryContainer = Color(0xFF2A4157),
            onPrimaryContainer = Color(0xFFD6EAF8),
            secondaryContainer = Color(0xFF24455F),
            onSecondaryContainer = Color(0xFFD6EAF8)
        )
    } else {
        lightColorScheme(
            primary = CB.Primary,
            onPrimary = Color.White,
            secondary = CB.Accent,
            onSecondary = Color.White,
            tertiary = CB.Success,
            background = Color(0xFFECF0F1),
            onBackground = CB.Primary,
            surface = Color.White,
            onSurface = CB.Primary,
            surfaceVariant = Color(0xFFF4F6F8),
            onSurfaceVariant = Color(0xFF5D6D7E),
            outline = Color(0xFFBFC9D1),
            error = CB.Danger,
            primaryContainer = Color(0xFFDDE6EE),
            onPrimaryContainer = CB.Primary,
            secondaryContainer = Color(0xFFD6EAF8),
            onSecondaryContainer = Color(0xFF1B4F72)
        )
    }
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(24.dp)
    )
    MaterialTheme(colorScheme = scheme, shapes = shapes, content = content)
}
