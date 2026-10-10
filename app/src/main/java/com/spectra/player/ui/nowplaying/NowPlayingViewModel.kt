package com.spectra.player.ui.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spectra.player.player.SpotifyRemoteManager
import com.spectra.player.visualizer.VisualizerController
import kotlinx.coroutines.flow.MutableStateFlow

class NowPlayingViewModel(
    private val remote: SpotifyRemoteManager,
    private val visualizer: VisualizerController,
) : ViewModel() {

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
