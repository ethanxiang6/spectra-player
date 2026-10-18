package com.spectra.player.visualizer

import android.os.SystemClock
import com.spectra.player.data.AudioProfile
import com.spectra.player.data.repository.LibraryRepository
import com.spectra.player.player.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

enum class VisualizerMode(val label: String) {
    LIVE("Live · Spotify audio"),
    FEATURES("Simulated · Spotify audio features"),
    DEFAULT("Simulated · default profile"),
    IDLE("Waiting for playback"),
}

/**
 * Produces one smoothed spectrum frame (~60 fps) and decides where it comes from:
 *
 *  1. LIVE      - real FFT from the global output mix (needs RECORD_AUDIO).
 *  2. SIMULATED - tempo/energy/loudness-driven spectrum locked to the playback position.
 *
 * The switch is automatic: if the live tap is unavailable, denied, or silent for
 * [LIVE_SILENCE_TIMEOUT_MS] while music is playing, simulation takes over; if live audio
 * returns, it takes control back.
 */
class VisualizerController(
    private val library: LibraryRepository,
    private val context: android.content.Context,
    val bandCount: Int = 48,
) {
    /** How long to hold live frames back so beats line up with what you hear (see [LiveDelay]). */
    @Volatile
    var liveDelayMs: Long = 0L

    private val _spectrum = MutableStateFlow(FloatArray(bandCount))
    val spectrum: StateFlow<FloatArray> = _spectrum.asStateFlow()

    private val _mode = MutableStateFlow(VisualizerMode.IDLE)
    val mode: StateFlow<VisualizerMode> = _mode.asStateFlow()

    private var job: Job? = null

    fun start(
        scope: CoroutineScope,
        playback: StateFlow<PlaybackState>,
        liveAllowed: StateFlow<Boolean>,
    ) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.Default) { frameLoop(playback, liveAllowed) }
    }

    fun stop() {
        job?.cancel()
        job = null
        _spectrum.value = FloatArray(bandCount)
        _mode.value = VisualizerMode.IDLE
    }

    private suspend fun frameLoop(
        playback: StateFlow<PlaybackState>,
        liveAllowed: StateFlow<Boolean>,
    ) = coroutineScope {
        // Track changes -> (re)load the audio profile used by the simulation.
        val profile = AtomicReference(AudioProfile.Default)
        launch {
            playback.map { it.track?.uri }.distinctUntilChanged().collectLatest { uri ->
                profile.set(AudioProfile.Default)
                if (uri != null) profile.set(library.audioProfile(uri))
            }
        }

        val live = FloatArray(bandCount)
        val liveDelayed = FloatArray(bandCount)
        val delayLine = LiveDelay(bandCount)
        val simulated = FloatArray(bandCount)
        val simulator = SimulatedSpectrum(bandCount)
        val smoother = SpectrumSmoother(bandCount)

        var tap: NativeSpectrumTap? = null
        var tapSession = -1
        var tapUnavailable = false
        var onlySpotify = true
        var lastGateCheck = 0L
        var silentMs = 0L
        var last = SystemClock.elapsedRealtime()

        try {
            while (isActive) {
                val now = SystemClock.elapsedRealtime()
                val dt = (now - last).coerceIn(1L, 100L)
                last = now

                val pb = playback.value
                val playing = pb.isPlaying && pb.track != null

                // Open / close the native tap as permission or the user's choice changes.
                // The overall output mix. (Attaching to Spotify's own audio session silenced
                // playback on some phones, so the mix is used and gated to Spotify below.)
                val wanted = 0
                if (liveAllowed.value) {
                    if (tap != null && tapSession != wanted) {
                        tap.release()
                        tap = null
                        tapUnavailable = false
                    }
                    if (tap == null && !tapUnavailable) {
                        tap = NativeSpectrumTap.create(bandCount, wanted)
                        tapSession = wanted
                        if (tap == null) tapUnavailable = true
                    }
                } else if (tap != null) {
                    tap.release()
                    tap = null
                    tapUnavailable = false
                }

                // On the overall mix, only listen while Spotify is the one playing.
                if (tapSession == 0 && now - lastGateCheck > GATE_CHECK_MS) {
                    lastGateCheck = now
                    onlySpotify = SpotifyAudioSession.onlyOnePlayerActive(context)
                }
                val listening = playing && (tapSession != 0 || onlySpotify)

                val peak = if (listening) tap?.read(live) ?: -1f else -1f
                if (peak > SILENCE_PEAK) silentMs = 0L else if (playing) silentMs += dt
                val liveUsable = peak >= 0f && silentMs < LIVE_SILENCE_TIMEOUT_MS

                val target: FloatArray
                if (liveUsable) {
                    delayLine.push(now, live, liveDelayMs, liveDelayed)
                    target = liveDelayed
                    _mode.value = VisualizerMode.LIVE
                } else {
                    val p = profile.get()
                    simulator.frame(
                        positionMs = pb.currentPosition(now),
                        playing = playing,
                        profile = p,
                        seed = pb.track?.uri?.hashCode() ?: 0,
                        out = simulated,
                    )
                    target = simulated
                    _mode.value = when {
                        !playing -> VisualizerMode.IDLE
                        p.fromSpotify -> VisualizerMode.FEATURES
                        else -> VisualizerMode.DEFAULT
                    }
                }

                _spectrum.value = smoother.step(target, dt)
                delay(FRAME_MS)
            }
        } finally {
            tap?.release()
        }
    }

    private companion object {
        const val FRAME_MS = 16L
        const val GATE_CHECK_MS = 500L
        const val SILENCE_PEAK = 0.02f
        const val LIVE_SILENCE_TIMEOUT_MS = 1_500L
    }
}
