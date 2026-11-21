package com.zirakusa.brujula.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** Ajustes › Temas: follow the phone, always light or always dark. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Ajustes › Color de acento: the heading line, the north triangle, switches and buttons. [ink] is drawn on the accent. */
enum class Accent(val color: Color, val ink: Color) {
    ROJO(Color(0xFFFF3B30), Color(0xFFFFFFFF)),
    NARANJA(Color(0xFFFF8A3D), Color(0xFF111111)),
    AMARILLO(Color(0xFFF5C518), Color(0xFF111111)),
    VERDE(Color(0xFF4CC38A), Color(0xFF111111)),
    AZUL(Color(0xFF4FA3F7), Color(0xFF111111)),
    MORADO(Color(0xFFA78BFA), Color(0xFF111111)),
}

/** The app's colours for one mode. [tick] is the dial's small marks, [mark] the long ones. */
data class Palette(val background: Color, val card: Color, val raised: Color, val chip: Color, val text: Color, val muted: Color,
    val tick: Color, val light: Boolean)

private val Amoled = Palette(Color(0xFF000000), Color(0xFF1C1C1E), Color(0xFF2A2A2C), Color(0xFF2C2C2E), Color(0xFFF2F2F2),
    Color(0xFF9A9A9E), Color(0xFF6E6E73), light = false)
private val Dark = Amoled.copy(background = Color(0xFF121214), card = Color(0xFF222226), raised = Color(0xFF303034), chip = Color(0xFF34343A))
private val Light = Palette(Color(0xFFF5F5F7), Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFE9E9EC), Color(0xFF151515),
    Color(0xFF7C7C80), Color(0xFFA8A8AE), light = true)

/**
 * The global theme, like Notas: every screen reads its colours here; they follow Ajustes › Temas (state, so a change
 * repaints at once). Default: pure black (AMOLED) with a red accent, like the phone's own compass.
 */
object CompassColors {
    var palette by mutableStateOf(Amoled); private set
    var chosen: com.zirakusa.brujula.ui.theme.Accent by mutableStateOf(com.zirakusa.brujula.ui.theme.Accent.ROJO); private set

    val Background get() = palette.background
    val Card get() = palette.card
    val Raised get() = palette.raised
    val Chip get() = palette.chip
    val Text get() = palette.text
    val Muted get() = palette.muted
    val Tick get() = palette.tick
    val Accent: Color get() = chosen.color
    val OnAccent: Color get() = chosen.ink
    val Danger = Color(0xFFFF6B6B)
    /** The figure-8 on «Calibrar la Brújula», as on the phone's compass. */
    val Guide = Color(0xFF34C759)
    val isLight get() = palette.light

    /** Changes only what differs, so painting with the same theme never asks for another frame. */
    fun use(dark: Boolean, amoled: Boolean, accent: com.zirakusa.brujula.ui.theme.Accent) {
        val next = if (!dark) Light else if (amoled) Amoled else Dark
        if (palette != next) palette = next
        if (chosen != accent) chosen = accent
    }
}

@Composable fun BrujulaTheme(mode: ThemeMode = ThemeMode.DARK, amoled: Boolean = true, accent: Accent = Accent.ROJO, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
    CompassColors.use(dark, amoled, accent)
    val c = CompassColors
    val scheme = if (dark) darkColorScheme(primary = c.Accent, onPrimary = c.OnAccent, background = c.Background, surface = c.Raised,
        surfaceContainer = c.Raised, surfaceContainerHigh = c.Raised, onBackground = c.Text, onSurface = c.Text, onSurfaceVariant = c.Muted, error = c.Danger)
    else lightColorScheme(primary = c.Accent, onPrimary = c.OnAccent, background = c.Background, surface = c.Raised,
        surfaceContainer = c.Raised, surfaceContainerHigh = c.Raised, onBackground = c.Text, onSurface = c.Text, onSurfaceVariant = c.Muted, error = c.Danger)
    MaterialTheme(scheme) { CompositionLocalProvider(LocalContentColor provides c.Text, content = content) }
}
