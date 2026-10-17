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
    private val shaper = LiveShaper(bands)
    private val mapper = BandMapper(
        bands = bands,
        fftSize = fft.size,
        sampleRateHz = visualizer.samplingRate / 1000, // reported in milliHertz
    )

    /** Fills [out] with band levels. Returns the peak level, or -1 if the capture failed. */
    fun read(out: FloatArray): Float {
        val status = try {
            visualizer.getFft(fft)
        } catch (e: Exception) {
            Visualizer.ERROR
        }
        if (status != Visualizer.SUCCESS) return -1f
        val peak = mapper.map(fft, out)
        shaper.apply(out)
        return peak // raw peak: used for silence detection
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
