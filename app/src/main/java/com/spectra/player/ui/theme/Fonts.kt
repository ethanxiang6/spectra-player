package com.spectra.player.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.spectra.player.R

/**
 * App typefaces (not used by the visualizer, which has its own title fonts).
 * Instrument Serif for page titles and section headings; DM Sans for everything else.
 * Downloaded through Google Play services; the system font shows until they arrive.
 */
object AppFonts {
    private val provider = GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = R.array.com_google_android_gms_fonts_certs,
    )

    val Display = FontFamily(
        Font(googleFont = GoogleFont("Instrument Serif"), fontProvider = provider, weight = FontWeight.Normal),
    )

    private val dmSans = GoogleFont("DM Sans")
    val Sans = FontFamily(
        Font(googleFont = dmSans, fontProvider = provider, weight = FontWeight.Normal),
        Font(googleFont = dmSans, fontProvider = provider, weight = FontWeight.Medium),
        Font(googleFont = dmSans, fontProvider = provider, weight = FontWeight.SemiBold),
    )
}
