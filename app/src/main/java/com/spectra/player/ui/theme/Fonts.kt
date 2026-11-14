package com.spectra.player.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.spectra.player.R

/**
 * App typefaces for the star-chart look (the visualizer keeps its own title fonts).
 * Bundled in res/font so they show immediately, offline, and in screenshot tests.
 *
 * - Chart: Cormorant Garamond italic, the hand-lettered feel of constellation names on an atlas.
 * - Catalog: IBM Plex Mono, for catalogue numbers, counts, durations and small labels.
 * - Sans: IBM Plex Sans, for song titles and everything you read.
 *
 * Licences (SIL OFL 1.1) are in /licenses/fonts.
 */
object AppFonts {
    val Display = FontFamily(
        Font(R.font.chart_serif_medium_italic, FontWeight.Medium, FontStyle.Italic),
        Font(R.font.chart_serif_semibold_italic, FontWeight.SemiBold, FontStyle.Italic),
    )

    val Mono = FontFamily(
        Font(R.font.chart_mono_regular, FontWeight.Normal),
        Font(R.font.chart_mono_medium, FontWeight.Medium),
    )

    val Sans = FontFamily(
        Font(R.font.chart_sans_regular, FontWeight.Normal),
        Font(R.font.chart_sans_medium, FontWeight.Medium),
        Font(R.font.chart_sans_semibold, FontWeight.SemiBold),
    )
}
