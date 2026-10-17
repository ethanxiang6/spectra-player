package com.spectra.player.visualizer

import android.media.audiofx.Visualizer
import android.os.Build

/**
 * Real-time FFT of the device's global output mix via `android.media.audiofx.Visualizer`
 * attached to audio session 0.
 *
 * Caveats (all handled by [VisualizerController]'s fallback):
 *  - needs RECORD_AUDIO + MODIFY_AUDIO_SETTINGS;
 *  - session 0 is deprecated-in-spirit and unsupported or silent on some devices / Android versions;
 *  - it hears *everything* playing on the phone, not only Spotify;
 *  - protected content may come back silent.
 */
class NativeSpectrumTap private constructor(
    private val visualizer: Visualizer,
    bands: Int,
) {
    private val fft = ByteArray(visualizer.captureSize)
    private val mapper = BandMapper(
        bands = bands,
        fftSize = fft.size,
        sampleRateHz = visualizer.samplingRate / 1000, // reported in milliHertz
    )

    /** Slowly decaying loudest-recent level, used as an automatic gain. */
    private var envelope = 0.35f

    /**
     * Fills [out] with band levels (auto-gained so quiet and loud songs both fill the ring).
     * Returns the raw peak level, or -1 if the capture failed.
     */
    fun read(out: FloatArray): Float {
        val status = try {
            visualizer.getFft(fft)
        } catch (e: Exception) {
            Visualizer.ERROR
        }
        if (status != Visualizer.SUCCESS) return -1f
        val peak = mapper.map(fft, out)
        // Follow peaks instantly, fall back over a few seconds; never boost silence into noise.
        envelope = maxOf(peak, envelope * ENVELOPE_DECAY, MIN_ENVELOPE)
        val gain = TARGET_PEAK / envelope
        for (i in out.indices) out[i] = (out[i] * gain).coerceIn(0f, 1f)
        return peak
    }

    fun release() {
        try {
            visualizer.setEnabled(false)
            visualizer.release()
        } catch (e: Exception) {
            // already released
        }
    }

    companion object {
        private const val ENVELOPE_DECAY = 0.996f // per frame (~60 fps): halves in ~3 s
        private const val MIN_ENVELOPE = 0.18f
        private const val TARGET_PEAK = 0.95f

        /** Null when the device or permission state doesn't allow tapping the output mix. */
        fun create(bands: Int): NativeSpectrumTap? {
            var v: Visualizer? = null
            return try {
                v = Visualizer(0)
                v.setEnabled(false) // capture size can only change while disabled
                v.setCaptureSize(Visualizer.getCaptureSizeRange()[1])
                if (Build.VERSION.SDK_INT >= 28) {
                    // Independent of the device volume, so bars look the same quiet or loud.
                    v.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED)
                }
                v.setEnabled(true)
                NativeSpectrumTap(v, bands)
            } catch (e: Exception) {
                try {
                    v?.release()
                } catch (ignored: Exception) {
                }
                null
            }
        }
    }
}
