package com.referral.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Brand palette, taken from the app icon: teal -> indigo, with an amber accent.
object Brand {
    val Teal900 = Color(0xFF0B4F4A)
    val Teal700 = Color(0xFF0F766E)
    val Teal500 = Color(0xFF14B8A6)
    val Teal100 = Color(0xFFCCF5EF)
    val Teal50 = Color(0xFFEFFCF9)
    val Indigo900 = Color(0xFF172554)
    val Indigo800 = Color(0xFF1E3A8A)
    val Indigo600 = Color(0xFF3B5BDB)
    val Indigo100 = Color(0xFFDDE5FF)
    val Amber500 = Color(0xFFF59E0B)
    val Amber400 = Color(0xFFFCD34D)
    val Amber100 = Color(0xFFFEF3C7)
    val Ink = Color(0xFF0F172A)
    val Slate600 = Color(0xFF475569)
    val Slate400 = Color(0xFF94A3B8)
    val Slate200 = Color(0xFFE2E8F0)
    val Slate100 = Color(0xFFF1F5F9)
    val Canvas = Color(0xFFF6F8FB)
    val Night = Color(0xFF0B1220)
    val NightSurface = Color(0xFF131C2E)
    val NightSurfaceHigh = Color(0xFF1B2638)
}

val LightColors: ColorScheme = lightColorScheme(
    primary = Brand.Teal700,
    onPrimary = Color.White,
    primaryContainer = Brand.Teal100,
    onPrimaryContainer = Brand.Teal900,
    secondary = Brand.Indigo800,
    onSecondary = Color.White,
    secondaryContainer = Brand.Indigo100,
    onSecondaryContainer = Brand.Indigo900,
    tertiary = Color(0xFFB45309),
    onTertiary = Color.White,
    tertiaryContainer = Brand.Amber100,
    onTertiaryContainer = Color(0xFF78350F),
    background = Brand.Canvas,
    onBackground = Brand.Ink,
    surface = Color.White,
    onSurface = Brand.Ink,
    surfaceVariant = Brand.Slate100,
    onSurfaceVariant = Brand.Slate600,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBFCFE),
    surfaceContainer = Brand.Slate100,
    surfaceContainerHigh = Color(0xFFEAEFF5),
    surfaceContainerHighest = Brand.Slate200,
    outline = Color(0xFFCBD5E1),
    outlineVariant = Brand.Slate200,
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
)

val DarkColors: ColorScheme = darkColorScheme(
    primary = Brand.Teal500,
    onPrimary = Color(0xFF00201D),
    primaryContainer = Color(0xFF134E4A),
    onPrimaryContainer = Brand.Teal100,
    secondary = Color(0xFF93A8FF),
    onSecondary = Brand.Indigo900,
    secondaryContainer = Color(0xFF26356B),
    onSecondaryContainer = Brand.Indigo100,
    tertiary = Brand.Amber400,
    onTertiary = Color(0xFF3B2600),
    tertiaryContainer = Color(0xFF4A3410),
    onTertiaryContainer = Brand.Amber100,
    background = Brand.Night,
    onBackground = Color(0xFFE5EAF2),
    surface = Brand.NightSurface,
    onSurface = Color(0xFFE5EAF2),
    surfaceVariant = Brand.NightSurfaceHigh,
    onSurfaceVariant = Color(0xFF9AA8BD),
    surfaceContainerLowest = Brand.Night,
    surfaceContainerLow = Color(0xFF0F1828),
    surfaceContainer = Brand.NightSurface,
    surfaceContainerHigh = Brand.NightSurfaceHigh,
    surfaceContainerHighest = Color(0xFF243146),
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF263245),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF5B1A1A),
    onErrorContainer = Color(0xFFFECACA),
)

/** Colours Material's scheme has no slot for: the brand gradient and status colours. */
@Immutable
data class ExtraColors(
    val heroGradient: Brush,
    val onHero: Color,
    val onHeroMuted: Color,
    val accent: Color,          // amber: highlights, match badges, "new"
    val onAccent: Color,
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val cardBorder: Color,
)

val LightExtra = ExtraColors(
    heroGradient = Brush.linearGradient(listOf(Brand.Teal700, Brand.Indigo800)),
    onHero = Color.White,
    onHeroMuted = Color(0xCCFFFFFF),
    accent = Brand.Amber400,
    onAccent = Color(0xFF3B2600),
    success = Color(0xFF15803D),
    successContainer = Color(0xFFDCFCE7),
    onSuccessContainer = Color(0xFF14532D),
    warning = Color(0xFFB45309),
    warningContainer = Brand.Amber100,
    onWarningContainer = Color(0xFF78350F),
    info = Brand.Indigo600,
    infoContainer = Brand.Indigo100,
    onInfoContainer = Brand.Indigo900,
    cardBorder = Color(0xFFE6EBF2),
)

val DarkExtra = ExtraColors(
    heroGradient = Brush.linearGradient(listOf(Color(0xFF0D5C56), Color(0xFF1A2F6E))),
    onHero = Color.White,
    onHeroMuted = Color(0xB3FFFFFF),
    accent = Brand.Amber400,
    onAccent = Color(0xFF3B2600),
    success = Color(0xFF4ADE80),
    successContainer = Color(0xFF143D26),
    onSuccessContainer = Color(0xFFBBF7D0),
    warning = Brand.Amber400,
    warningContainer = Color(0xFF4A3410),
    onWarningContainer = Brand.Amber100,
    info = Color(0xFF93A8FF),
    infoContainer = Color(0xFF26356B),
    onInfoContainer = Brand.Indigo100,
    cardBorder = Color(0xFF223047),
)

val LocalExtraColors = staticCompositionLocalOf { LightExtra }
