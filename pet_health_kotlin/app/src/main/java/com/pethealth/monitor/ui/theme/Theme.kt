package com.pethealth.monitor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Shared accent / status hues (same in both themes). */
val Accent = Color(0xFFC6F35E)
val AccentOn = Color(0xFF10180A)
val Amber = Color(0xFFFFB454)
val Coral = Color(0xFFFF8073)
val Sky = Color(0xFF7CC4FF)
val Teal = Color(0xFF5EEAD4)

// Backward-compatible aliases used by older call sites
val Forest = Accent
val ForestDark = Color(0xFF8BC34A)
val Leaf = Accent
val Sand = Color(0xFF0C1214)
val PageBg = Color(0xFF0C1214)
val Ink = Color(0xFFEDF2F0)
val Mist = Color(0xFF25343A)
val Stone = Color(0xFF9FB1AE)
val StoneSoft = Color(0xFF1B2529)

@Immutable
data class PhColors(
    val background: Color,
    val surface: Color,
    val surfaceSoft: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val navBg: Color,
    val accent: Color,
    val accentOn: Color,
    val amber: Color,
    val coral: Color,
    val sky: Color,
    val teal: Color,
    val warnBg: Color,
    val warnFg: Color,
    val dangerBg: Color,
    val dangerFg: Color,
    val okBg: Color,
    val okFg: Color,
    val isDark: Boolean
)

val DarkPhColors = PhColors(
    background = Color(0xFF0C1214),
    surface = Color(0xFF141C1F),
    surfaceSoft = Color(0xFF1B2529),
    border = Color(0xFF25343A),
    text = Color(0xFFEDF2F0),
    muted = Color(0xFF9FB1AE),
    navBg = Color(0xFF10171A),
    accent = Accent,
    accentOn = AccentOn,
    amber = Amber,
    coral = Coral,
    sky = Sky,
    teal = Teal,
    warnBg = Color(0xFF33270F),
    warnFg = Amber,
    dangerBg = Color(0xFF2A1215),
    dangerFg = Coral,
    okBg = Color(0xFF10180A),
    okFg = Accent,
    isDark = true
)

val LightPhColors = PhColors(
    background = Color(0xFFEDF2F0),
    surface = Color(0xFFFFFFFF),
    surfaceSoft = Color(0xFFE4EBE8),
    border = Color(0xFFD0DAD7),
    text = Color(0xFF141C1F),
    muted = Color(0xFF5A6B68),
    navBg = Color(0xFFFFFFFF),
    accent = Color(0xFF6BA52E),
    accentOn = Color(0xFFFFFFFF),
    amber = Color(0xFFD97706),
    coral = Color(0xFFE11D48),
    sky = Color(0xFF0284C7),
    teal = Color(0xFF0D9488),
    warnBg = Color(0xFFFFF7ED),
    warnFg = Color(0xFFC2410C),
    dangerBg = Color(0xFFFFF1F2),
    dangerFg = Color(0xFFBE123C),
    okBg = Color(0xFFECFDF5),
    okFg = Color(0xFF166534),
    isDark = false
)

val LocalPhColors = staticCompositionLocalOf { DarkPhColors }

private val darkScheme = darkColorScheme(
    primary = Accent,
    onPrimary = AccentOn,
    secondary = Sky,
    onSecondary = Color(0xFF0C1214),
    background = DarkPhColors.background,
    onBackground = DarkPhColors.text,
    surface = DarkPhColors.surface,
    onSurface = DarkPhColors.text,
    error = Coral,
    onError = Color(0xFF1A0B08)
)

private val lightScheme = lightColorScheme(
    primary = LightPhColors.accent,
    onPrimary = Color.White,
    secondary = LightPhColors.sky,
    onSecondary = Color.White,
    background = LightPhColors.background,
    onBackground = LightPhColors.text,
    surface = LightPhColors.surface,
    onSurface = LightPhColors.text,
    error = LightPhColors.coral,
    onError = Color.White
)

private val type = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp
    )
)

@Composable
fun PetHealthTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) DarkPhColors else LightPhColors
    CompositionLocalProvider(LocalPhColors provides colors) {
        MaterialTheme(
            colorScheme = if (dark) darkScheme else lightScheme,
            typography = type,
            content = content
        )
    }
}
