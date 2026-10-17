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
            .apply()
    }

    private fun read(): AppSettings {
        val d = AppSettings()
        val theme = prefs.getString(KEY_THEME, null)
            ?.let { name -> AppSettings.themes.firstOrNull { it.name == name } } ?: d.theme
        return AppSettings(theme = theme, stars = prefs.getBoolean(KEY_STARS, d.stars))
    }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_STARS = "stars"
    }
}
