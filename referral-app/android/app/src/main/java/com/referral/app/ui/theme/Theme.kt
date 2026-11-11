package com.referral.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * App theme: brand colours (not wallpaper-based dynamic colour, so the app always looks like itself),
 * Plus Jakarta Sans, rounded shapes, light and dark.
 */
@Composable
fun ReferralTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalExtraColors provides if (darkTheme) DarkExtra else LightExtra) {
        val colors = if (darkTheme) DarkColors else LightColors
        MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes) {
            // Text drawn straight on the background (outside a Surface/Scaffold) otherwise falls back to
            // black, which disappears in dark mode.
            CompositionLocalProvider(LocalContentColor provides colors.onBackground, content = content)
        }
    }
}

/** Brand-only colours: `MaterialTheme.extra.heroGradient`, `.success`, … */
val MaterialTheme.extra: ExtraColors
    @Composable @ReadOnlyComposable get() = LocalExtraColors.current
