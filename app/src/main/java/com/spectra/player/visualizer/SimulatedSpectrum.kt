package com.spectra.player.visualizer

import com.spectra.player.data.AudioProfile
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

/**
 * Fallback when raw audio can't be captured. Builds a believable spectrum from a track's
 * tempo / energy / loudness / danceability (Spotify Audio Features, or a default profile):
 *
 *  - a kick on every bar start excites the low bands, a snare on beats 2 and 4 the mids,
 *    off-beat hi-hats the highs, all locked to the real playback position;
 *  - a spectral tilt plus slow per-band wobble (seeded by the track) keeps bars alive between hits.
 *
 * It is synchronized to the music but does not analyse it. Pure Kotlin.
 */
class SimulatedSpectrum(private val bands: Int) {

    fun frame(positionMs: Long, playing: Boolean, profile: AudioProfile, seed: Int, out: FloatArray) {
        if (!playing) {
            out.fill(0f)
            return
        }

        val beatMs = 60_000f / profile.tempo.coerceIn(40f, 220f)
        val beats = positionMs / beatMs
        val phase = beats - floor(beats)                 // 0..1 inside the current beat
        val beatInBar = floor(beats).toInt() and 3

        val kick = exp(-phase * 5.5f) * (if (beatInBar == 0) 1f else 0.8f)
        val snare = if (beatInBar % 2 == 1) exp(-phase * 7f) else 0f
        val hat = exp(-((phase + 0.5f) % 1f) * 9f)

        val loudness = ((profile.loudnessDb + 40f) / 40f).coerceIn(0f, 1f)
        val gain = (0.45f + 0.55f * loudness) * (0.40f + 0.60f * profile.energy.coerceIn(0f, 1f))
        val hatAmount = 0.35f + 0.65f * profile.danceability.coerceIn(0f, 1f)

        val t = positionMs / 1000f
        val s = (seed and 0xFFFF) / 65_535f

        for (i in 0 until bands) {
            val x = i / (bands - 1f)
            val tilt = (1f - x).pow(1.4f) * 0.55f + 0.12f
            val wobble = 0.55f +
                0.25f * sin(t * (1.1f + 0.9f * s) + i * 0.62f + s * 6.28f) +
                0.20f * sin(t * 2.7f - i * 0.37f + s * 3.1f)

            val low = kick * bump(x, 0.07f, 0.09f)
            val mid = snare * bump(x, 0.40f, 0.16f)
            val high = hat * hatAmount * bump(x, 0.82f, 0.14f)

            out[i] = ((tilt * wobble * 0.65f + low * 0.95f + mid * 0.60f + high * 0.50f) * gain)
                .coerceIn(0f, 1f)
        }
    }

    /** Gaussian bump centred on [c] with width [w]. */
    private fun bump(x: Float, c: Float, w: Float): Float {
        val d = (x - c) / w
        return exp(-d * d / 2f)
    }
}
