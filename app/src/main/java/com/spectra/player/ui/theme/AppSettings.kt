package com.spectra.player.ui.theme

import android.content.Context
import com.spectra.player.visualizer.Backdrop
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Settings for the app itself (library, playlists, search). Separate from the visualizer's. */
data class AppSettings(
    val theme: Backdrop = Backdrop.EVENING,
    val stars: Boolean = true,
    /** Launcher icon: an [AppIcons.Icon.alias] such as "Evening" or "Monochrome". */
    val icon: String = AppIcons.DEFAULT,
    /** Black and white everywhere, on true black (AMOLED). Overrides the theme while on. */
    val monochrome: Boolean = false,
    /** Colours of the Custom theme, and palettes saved under a name. */
    val custom: CustomPalette = CustomPalette(),
    val presets: List<PalettePreset> = emptyList(),
    /** Open the visualizer after this many seconds without a touch while music plays; 0 = never. */
    val autoVisualizerSec: Int = 0,
    /** Release tag the user chose to skip in the update prompt. */
    val skippedUpdate: String = "",
    /** Home screen widget: draw its panel, or leave only the song and buttons over the wallpaper. */
    val widgetBackground: Boolean = true,
) {
    companion object {
        /** Album art only makes sense on the visualizer. */
        val themes: List<Backdrop> = Backdrop.entries.filter { it != Backdrop.ALBUM }
    }
}

class AppPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        prefs.edit()
            .putString(KEY_THEME, next.theme.name)
            .putBoolean(KEY_STARS, next.stars)
            .putString(KEY_ICON, next.icon)
            .putBoolean(KEY_MONO, next.monochrome)
            .putString(KEY_CUSTOM, next.custom.encode())
            .putString(KEY_PRESETS, PalettePreset.encodeAll(next.presets))
            .putInt(KEY_AUTO_VIS, next.autoVisualizerSec)
            .putString(KEY_SKIPPED, next.skippedUpdate)
            .putBoolean(KEY_WIDGET_BG, next.widgetBackground)
            .apply()
    }

    private fun read(): AppSettings {
        val d = AppSettings()
        val theme = prefs.getString(KEY_THEME, null)
            ?.let { name -> AppSettings.themes.firstOrNull { it.name == name } } ?: d.theme
        val icon = AppIcons.find(prefs.getString(KEY_ICON, null) ?: d.icon).alias
        return AppSettings(
            theme = theme,
            stars = prefs.getBoolean(KEY_STARS, d.stars),
            icon = icon,
            monochrome = prefs.getBoolean(KEY_MONO, d.monochrome),
            custom = CustomPalette.decode(prefs.getString(KEY_CUSTOM, null)) ?: d.custom,
            presets = PalettePreset.decodeAll(prefs.getString(KEY_PRESETS, null)),
            autoVisualizerSec = prefs.getInt(KEY_AUTO_VIS, d.autoVisualizerSec),
            skippedUpdate = prefs.getString(KEY_SKIPPED, null) ?: d.skippedUpdate,
            widgetBackground = prefs.getBoolean(KEY_WIDGET_BG, d.widgetBackground),
        )
    }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_STARS = "stars"
        const val KEY_ICON = "icon"
        const val KEY_MONO = "monochrome"
        const val KEY_CUSTOM = "custom_palette"
        const val KEY_PRESETS = "palette_presets"
        const val KEY_AUTO_VIS = "auto_visualizer_sec"
        const val KEY_SKIPPED = "skipped_update"
        const val KEY_WIDGET_BG = "widget_background"
    }
}
