package com.spectra.player.visualizer

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Backgrounds for the full-screen visualizer. */
enum class Backdrop(val label: String) {
    EVENING("Evening sky"),
    MIDNIGHT("Midnight"),
    SUNSET("Sunset"),
    AURORA("Aurora"),
    NEBULA("Nebula"),
    OCEAN("Deep ocean"),
    ALBUM("Album art"),
    BLACK("Black"),
}

/** Everything the user can customise on the visualizer screen. */
data class VisualizerSettings(
    val backdrop: Backdrop = Backdrop.EVENING,
    val spinArt: Boolean = true,
    val stars: Boolean = true,
    val style: VisualizerStyle = VisualizerStyle.BARS,
    /** Google Font family name for the floating song title, or [DEFAULT_FONT]. */
    val fontId: String = DEFAULT_FONT,
    /** The one-time "match the real audio" prompt has been answered. */
    val askedForMic: Boolean = false,
    /** Use the phone's audio output (needs the microphone permission). Off: timing-based animation. */
    val liveAudio: Boolean = false,
) {
    companion object {
        const val DEFAULT_FONT = "default"
    }
}

/** Persists [VisualizerSettings] in SharedPreferences and exposes them as a flow. */
class VisualizerPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("visualizer", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<VisualizerSettings> = _settings.asStateFlow()

    fun update(transform: (VisualizerSettings) -> VisualizerSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        prefs.edit()
            .putString(KEY_BACKDROP, next.backdrop.name)
            .putBoolean(KEY_SPIN, next.spinArt)
            .putBoolean(KEY_STARS, next.stars)
            .putString(KEY_STYLE, next.style.name)
            .putString(KEY_FONT, next.fontId)
            .putBoolean(KEY_ASKED_MIC, next.askedForMic)
            .putBoolean(KEY_LIVE, next.liveAudio)
            .apply()
    }

    private fun read(): VisualizerSettings {
        val defaults = VisualizerSettings()
        return VisualizerSettings(
            backdrop = enumOr(prefs.getString(KEY_BACKDROP, null), defaults.backdrop),
            spinArt = prefs.getBoolean(KEY_SPIN, defaults.spinArt),
            stars = prefs.getBoolean(KEY_STARS, defaults.stars),
            style = enumOr(prefs.getString(KEY_STYLE, null), defaults.style),
            fontId = prefs.getString(KEY_FONT, null) ?: defaults.fontId,
            askedForMic = prefs.getBoolean(KEY_ASKED_MIC, defaults.askedForMic),
            liveAudio = prefs.getBoolean(KEY_LIVE, defaults.liveAudio),
        )
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: fallback

    private companion object {
        const val KEY_BACKDROP = "backdrop"
        const val KEY_SPIN = "spin_art"
        const val KEY_STARS = "stars"
        const val KEY_STYLE = "style"
        const val KEY_FONT = "font"
        const val KEY_ASKED_MIC = "asked_mic"
        const val KEY_LIVE = "live_audio"
    }
}
