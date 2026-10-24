package com.spectra.player.ui.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spectra.player.player.SpotifyRemoteManager
import com.spectra.player.visualizer.VisualizerController
import com.spectra.player.visualizer.VisualizerPrefs
import com.spectra.player.visualizer.VisualizerSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

class NowPlayingViewModel(
    private val remote: SpotifyRemoteManager,
    private val visualizer: VisualizerController,
    private val prefs: VisualizerPrefs,
    private val lyricsRepo: com.spectra.player.data.lyrics.LyricsRepository,
) : ViewModel() {

    val settings: StateFlow<VisualizerSettings> = prefs.settings
    fun updateSettings(transform: (VisualizerSettings) -> VisualizerSettings) = prefs.update(transform)

    /** Lyrics of the current song (when the setting is on); None while loading or when unavailable. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val lyrics: StateFlow<com.spectra.player.data.lyrics.Lyrics> =
        kotlinx.coroutines.flow.combine(
            remote.playback.map { it.track }.distinctUntilChanged { a, b -> a?.uri == b?.uri },
            prefs.settings.map { it.showLyrics }.distinctUntilChanged(),
        ) { track, enabled -> if (enabled) track else null }
            .mapLatest { track ->
                if (track == null) com.spectra.player.data.lyrics.Lyrics.None
                else lyricsRepo.lyricsFor(track)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.spectra.player.data.lyrics.Lyrics.None)

    val playback = remote.playback
    val artwork = remote.artwork
    val spectrum = visualizer.spectrum
    val mode = visualizer.mode

    /** True once RECORD_AUDIO is granted; lets the controller open the native FFT tap. */
    private val liveAllowed = MutableStateFlow(false)

    fun setLiveAllowed(allowed: Boolean) {
        liveAllowed.value = allowed
    }

    /** Called while the screen is at least STARTED; the loop is stopped when it leaves the foreground. */
    fun startVisualizer() = visualizer.start(viewModelScope, remote.playback, liveAllowed)
    fun stopVisualizer() = visualizer.stop()

    fun setLiveDelay(ms: Long) {
        visualizer.liveDelayMs = ms.coerceIn(0L, 800L)
    }

    fun togglePlayPause() = remote.togglePlayPause()
    fun skipNext() = remote.skipNext()
    fun skipPrevious() = remote.skipPrevious()
    fun seekTo(positionMs: Long) = remote.seekTo(positionMs)
    fun toggleShuffle() = remote.toggleShuffle()
    fun toggleRepeat() = remote.toggleRepeat()

    override fun onCleared() {
        visualizer.stop()
    }
}
