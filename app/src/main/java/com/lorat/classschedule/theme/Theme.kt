package com.lorat.classschedule.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * A deliberately plain, high-contrast palette: white grid, near-black text, one blue accent —
 * the same restrained look WakeUp课程表 uses by default. Dynamic colour is off so the grid
 * always reads the same on every device.
 */
private val Accent = Color(0xFF3D6BE5)
private val AccentDark = Color(0xFF8FB0FF)

private val DarkColorScheme = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color(0xFF10214D),
    secondary = AccentDark,
    background = Color(0xFF101114),
    onBackground = Color(0xFFE8EAED),
    surface = Color(0xFF101114),
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF26282E),
    onSurfaceVariant = Color(0xFF9AA0A6),
    surfaceContainerLowest = Color(0xFF0B0C0E),
    surfaceContainerLow = Color(0xFF16181B),
    surfaceContainer = Color(0xFF1B1D21),
    surfaceContainerHigh = Color(0xFF212429),
    surfaceContainerHighest = Color(0xFF26282E),
    surfaceDim = Color(0xFF0B0C0E),
    surfaceBright = Color(0xFF33363C),
    outline = Color(0xFF6B7280),
    outlineVariant = Color(0xFF3A3D43),
)

private val LightColorScheme = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = Accent,
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1F2126),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F2126),
    surfaceVariant = Color(0xFFF1F3F4),
    onSurfaceVariant = Color(0xFF6B7280),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFAFB),
    surfaceContainer = Color(0xFFF6F7F8),
    surfaceContainerHigh = Color(0xFFF1F3F4),
    surfaceContainerHighest = Color(0xFFECEEF0),
    surfaceDim = Color(0xFFE3E5E7),
    surfaceBright = Color(0xFFFFFFFF),
    outline = Color(0xFF9AA0A6),
    outlineVariant = Color(0xFFDADCE0),
)

@Composable
fun ClassScheduleTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
