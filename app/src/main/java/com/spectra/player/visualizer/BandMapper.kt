package com.spectra.player.visualizer

import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Turns an `android.media.audiofx.Visualizer` FFT capture into [bands] log-spaced magnitudes (0..1).
 *
 * Capture layout (n = capture size): fft[0] = DC real, fft[1] = Nyquist real, then for k in 1 until n/2
 * the pair (real = fft[2k], imag = fft[2k+1]), both signed bytes. Pure Kotlin: no Android types.
 */
class BandMapper(
    private val bands: Int,
    fftSize: Int,
    sampleRateHz: Int,
    minHz: Float = 40f,
    maxHz: Float = 16_000f,
) {
    /** Band edges in Hz (size = bands + 1), log-spaced like human pitch perception. */
    val edgesHz: FloatArray

    private val firstBin = IntArray(bands)
    private val lastBin = IntArray(bands)

    init {
        val top = maxHz.coerceAtMost(sampleRateHz / 2f)
        edgesHz = FloatArray(bands + 1) { i -> minHz * (top / minHz).pow(i / bands.toFloat()) }

        val binWidth = sampleRateHz.toFloat() / fftSize
        val maxBin = fftSize / 2 - 1
        for (i in 0 until bands) {
            val start = (edgesHz[i] / binWidth).toInt().coerceIn(1, maxBin)
            val end = (ceil(edgesHz[i + 1] / binWidth).toInt() - 1).coerceIn(start, maxBin)
            firstBin[i] = start
            lastBin[i] = end
        }
    }

    /** Fills [out] (size >= bands) with normalized band levels and returns the peak level. */
    fun map(fft: ByteArray, out: FloatArray): Float {
        var peak = 0f
        for (i in 0 until bands) {
            var strongest = 0f
            for (k in firstBin[i]..lastBin[i]) {
                val re = fft[2 * k].toInt()
                val im = fft[2 * k + 1].toInt()
                val magnitude = sqrt((re * re + im * im).toFloat())
                if (magnitude > strongest) strongest = magnitude
            }
            val linear = (strongest / 128f).coerceIn(0f, 1f)
            val compressed = ln(1f + 9f * linear) / LN_10      // gentle log curve: quiet detail stays visible
            val tilt = 1f + 0.9f * i / bands                     // highs carry less energy; lift them
            val level = (compressed * tilt).coerceIn(0f, 1f)
            out[i] = level
            if (level > peak) peak = level
        }
        return peak
    }

    private companion object {
        const val LN_10 = 2.3025851f
    }
}
