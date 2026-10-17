package com.spectra.player.ui.nowplaying

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.spectra.player.R
import com.spectra.player.visualizer.VisualizerSettings

/**
 * Fonts for the floating song title on the visualizer only (the rest of the app keeps its theme).
 * They are Google Fonts downloaded on demand through Google Play services, so they add nothing to
 * the APK; until a font arrives (or if it can't be fetched) the default font is shown.
 */
object TitleFonts {

    data class Choice(val id: String, val label: String, val category: String, val family: FontFamily?)

    private val provider = GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = R.array.com_google_android_gms_fonts_certs,
    )

    private val catalogue: List<Pair<String, String>> = listOf(
        // Clean and modern
        "Inter" to "Modern", "Poppins" to "Modern", "Montserrat" to "Modern", "Raleway" to "Modern",
        "Nunito" to "Modern", "Quicksand" to "Modern", "Comfortaa" to "Modern", "Josefin Sans" to "Modern",
        "Outfit" to "Modern", "Space Grotesk" to "Modern", "Sora" to "Modern", "Manrope" to "Modern",
        "Lexend" to "Modern", "Syne" to "Modern", "Unbounded" to "Modern",
        // Bold display
        "Righteous" to "Display", "Bebas Neue" to "Display", "Oswald" to "Display", "Anton" to "Display",
        "Archivo Black" to "Display", "Fredoka" to "Display", "Baloo 2" to "Display", "Bungee" to "Display",
        "Monoton" to "Display", "Abril Fatface" to "Display",
        // Script and handwritten
        "Lobster" to "Script", "Pacifico" to "Script", "Dancing Script" to "Script", "Great Vibes" to "Script",
        "Satisfy" to "Script", "Caveat" to "Script", "Shadows Into Light" to "Script",
        "Permanent Marker" to "Script", "Indie Flower" to "Script", "Amatic SC" to "Script",
        // Serif
        "Playfair Display" to "Serif", "Lora" to "Serif", "Cormorant Garamond" to "Serif", "Cinzel" to "Serif",
        "Libre Baskerville" to "Serif", "DM Serif Display" to "Serif", "Merriweather" to "Serif",
        // Mono, retro and pixel
        "Space Mono" to "Retro", "JetBrains Mono" to "Retro", "VT323" to "Retro", "Press Start 2P" to "Retro",
        "Silkscreen" to "Retro", "Orbitron" to "Retro", "Audiowide" to "Retro", "Major Mono Display" to "Retro",
    )

    val all: List<Choice> = listOf(Choice(VisualizerSettings.DEFAULT_FONT, "Default", "", null)) +
        catalogue.map { (name, category) ->
            Choice(name, name, category, FontFamily(Font(googleFont = GoogleFont(name), fontProvider = provider)))
        }

    fun byId(id: String): Choice = all.firstOrNull { it.id == id } ?: all.first()
}
