package com.spectra.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.lerp
import com.spectra.player.ui.nowplaying.look
import com.spectra.player.visualizer.Backdrop
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Strict Material 3 dark theme: there is deliberately no light scheme and no dynamic color,
 * so the visualizer's mint / violet / pink accents always sit on the same near-black canvas.
 */
private val SpectraDarkColors = darkColorScheme(
    primary = Color(0xFF45E0A2),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF005139),
    onPrimaryContainer = Color(0xFF69FCBC),
    secondary = Color(0xFF9D8CFF),
    onSecondary = Color(0xFF1E0B6B),
    secondaryContainer = Color(0xFF3A2A8C),
    onSecondaryContainer = Color(0xFFE3DEFF),
    tertiary = Color(0xFFFF7EBB),
    onTertiary = Color(0xFF5B0034),
    tertiaryContainer = Color(0xFF7D1E52),
    onTertiaryContainer = Color(0xFFFFD8E8),
    background = Color(0xFF0B0B10),
    onBackground = Color(0xFFE6E4EE),
    surface = Color(0xFF0B0B10),
    onSurface = Color(0xFFE6E4EE),
    surfaceVariant = Color(0xFF23222D),
    onSurfaceVariant = Color(0xFFC8C5D4),
    surfaceContainerLowest = Color(0xFF07070B),
    surfaceContainerLow = Color(0xFF121218),
    surfaceContainer = Color(0xFF16161D),
    surfaceContainerHigh = Color(0xFF1D1C25),
    surfaceContainerHighest = Color(0xFF272631),
    outline = Color(0xFF918FA0),
    outlineVariant = Color(0xFF343340),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val SpectraTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
)

@Composable
fun SpectraTheme(
    theme: Backdrop? = null,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = theme?.let { remember(it) { schemeFor(it) } } ?: SpectraDarkColors,
        typography = SpectraTypography,
        content = content,
    )
}

/**
 * Dark scheme taken from a sky theme: accents from its ring colours, surfaces tinted with its
 * darkest sky colour. The background is transparent so the sky drawn behind every screen shows.
 */
private fun schemeFor(theme: Backdrop): androidx.compose.material3.ColorScheme {
    val look = theme.look()
    val base = look.stops.firstOrNull()?.second ?: Color(0xFF0B0B10)
    val mid = look.stops.getOrNull(look.stops.size / 2)?.second ?: base
    val (p, s, t) = Triple(look.ring[0], look.ring[1], look.ring[2])
    fun tint(c: Color, amount: Float) = lerp(c, Color.White, amount)
    return darkColorScheme(
        primary = p,
        onPrimary = base,
        primaryContainer = lerp(base, p, 0.35f),
        onPrimaryContainer = tint(p, 0.6f),
        secondary = s,
        onSecondary = base,
        secondaryContainer = lerp(base, s, 0.3f),
        onSecondaryContainer = tint(s, 0.6f),
        tertiary = t,
        onTertiary = base,
        tertiaryContainer = lerp(base, t, 0.3f),
        onTertiaryContainer = tint(t, 0.6f),
        background = Color.Transparent,
        onBackground = Color(0xFFF2EFFC),
        surface = lerp(base, mid, 0.5f),
        onSurface = Color(0xFFF2EFFC),
        surfaceVariant = tint(lerp(base, mid, 0.6f), 0.08f),
        onSurfaceVariant = Color(0xFFC9C4E0),
        surfaceContainerLowest = base.copy(alpha = 0.85f),
        surfaceContainerLow = tint(base, 0.03f).copy(alpha = 0.88f),
        surfaceContainer = tint(lerp(base, mid, 0.4f), 0.05f).copy(alpha = 0.92f),
        surfaceContainerHigh = tint(lerp(base, mid, 0.5f), 0.08f).copy(alpha = 0.95f),
        surfaceContainerHighest = tint(lerp(base, mid, 0.6f), 0.12f),
        outline = Color(0xFF938EAE),
        outlineVariant = tint(base, 0.18f),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
    )
}
