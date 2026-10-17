package com.spectra.player.visualizer

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Shapes a live spectrum the way classic circular visualizers look: a thin ring at rest, with
 * broad, smooth bulges where the music is *changing*, never a mirror image.
 *
 *  - Each band shows how far it rises above its own recent average, so steady sound stays near
 *    the ring and new sound pushes out. A small share of the plain level keeps some texture.
 *  - Kicks ("bum") make a big bulge in the bass part of the ring; snares/claps ("pt") a smaller one
 *    in their part; vocals and melody are weighted lowest.
 *  - A wide circular blur turns neighbouring bars into smooth humps.
 *
 * Input: raw band levels (0..1, bass first). Output written back in place.
 */
class LiveShaper(private val bands: Int) {
    private val leveler = BandLeveler(bands)
    private val detail = FloatArray(bands)
    private val previous = FloatArray(bands)
    private val average = FloatArray(bands)
    private val deviation = FloatArray(bands) { MIN_DEVIATION }
    private val shaped = FloatArray(bands)

    private val kick = OnsetDetector(gain = 2.4f)
    private val snare = OnsetDetector(gain = 2.0f)
    private var kickLevel = 0f
    private var snareLevel = 0f

    private val bassEnd = max(1, bands / 8)            // ~40-150 Hz
    private val snareStart = (bands * 0.55f).toInt()   // ~1.5 kHz
    private val snareEnd = (bands * 0.80f).toInt()     // ~5 kHz

    fun apply(levels: FloatArray) {
        kickLevel = max(kickLevel * KICK_DECAY, kick.strength(flux(levels, 0, bassEnd)))
        snareLevel = max(snareLevel * SNARE_DECAY, snare.strength(flux(levels, snareStart, snareEnd)))
        levels.copyInto(previous)

        levels.copyInto(detail)
        leveler.apply(detail)

        for (i in 0 until bands) {
            val raw = levels[i]
            // How far this band is above its own recent average (0..1).
            val above = max(0f, raw - average[i])
            deviation[i] = max(max(above, deviation[i] * DEVIATION_DECAY), MIN_DEVIATION)
            val excess = (above / deviation[i]).coerceIn(0f, 1f).pow(1.3f)
            average[i] += (raw - average[i]) * AVERAGE_RATE

            val f = i.toFloat() / bands
            val weight = when {
                f < 0.2f -> 1.0f    // bass
                f < 0.55f -> 0.55f  // vocals / melody: least
                else -> 0.75f       // snare, hats, cymbals
            }
            val presence = detail[i] / BandLevelerMax
            var v = weight * (0.75f * excess + 0.25f * presence)
            if (i < bands / 5) v += KICK_BOOST * kickLevel * (1f - i / (bands / 5f))
            if (i in snareStart until snareEnd) v += SNARE_BOOST * snareLevel
            shaped[i] = v
        }

        // Wide circular blur: neighbouring bars rise together into smooth humps.
        for (i in 0 until bands) {
            var sum = 0f
            var norm = 0f
            for (k in -BLUR_RADIUS..BLUR_RADIUS) {
                val w = (BLUR_RADIUS + 1 - kotlin.math.abs(k)).toFloat()
                sum += shaped[(i + k + bands) % bands] * w
                norm += w
            }
            levels[i] = min(sum / norm * OUTPUT_GAIN, MAX_LEVEL)
        }
    }

    private fun flux(levels: FloatArray, from: Int, until: Int): Float {
        var sum = 0f
        for (i in from until until) sum += max(0f, levels[i] - previous[i])
        return sum / max(1, until - from)
    }

    /** Adaptive threshold: a hit is a flux clearly above its recent average. */
    private class OnsetDetector(private val gain: Float) {
        private var average = 0f
        private var peak = MIN_PEAK

        fun strength(flux: Float): Float {
            peak = max(max(flux, peak * PEAK_DECAY), MIN_PEAK)
            val s = ((flux - average * THRESHOLD) / peak * gain).coerceIn(0f, 1f)
            average += (flux - average) * AVERAGE_RATE
            return s
        }

        companion object {
            const val MIN_PEAK = 0.02f
            const val PEAK_DECAY = 0.995f
            const val THRESHOLD = 1.5f
            const val AVERAGE_RATE = 0.08f
        }
    }

    private companion object {
        const val BandLevelerMax = 0.78f
        const val AVERAGE_RATE = 0.03f      // per frame: ~0.5 s memory
        const val DEVIATION_DECAY = 0.995f
        const val MIN_DEVIATION = 0.04f
        const val KICK_DECAY = 0.86f
        const val SNARE_DECAY = 0.80f
        const val KICK_BOOST = 0.85f
        const val SNARE_BOOST = 0.45f
        const val BLUR_RADIUS = 3
        const val OUTPUT_GAIN = 1.15f
        const val MAX_LEVEL = 0.9f
    }
}
