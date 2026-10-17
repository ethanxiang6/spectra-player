package com.spectra.player.visualizer

import kotlin.math.max

/**
 * Turns a live spectrum into a ring that reacts to the rhythm rather than to the singing.
 *
 *  - Kick ("bum"): sudden *increase* in bass energy between frames (spectral flux), not the
 *    bass level itself, so it fires on the hit and not on a sustained bassline.
 *  - Snare / clap ("pt"): the same in the upper-mid range, where snare cracks live.
 *    Both lift the whole ring; the kick more than the snare.
 *  - Detail: every band, scaled against its own recent peak, adds texture so the ring keeps an
 *    organic shape; the vocal range is weighted lowest.
 *
 * Input: raw band levels (0..1, bass first). Output written back in place.
 */
class LiveShaper(private val bands: Int) {
    private val leveler = BandLeveler(bands)
    private val detail = FloatArray(bands)
    private val previous = FloatArray(bands)

    private val kick = OnsetDetector(gain = 2.4f)
    private val snare = OnsetDetector(gain = 2.0f)
    private var kickLevel = 0f
    private var snareLevel = 0f

    fun apply(levels: FloatArray) {
        val bassEnd = max(1, bands / 8)                 // ~40-150 Hz
        val snareStart = (bands * 0.55f).toInt()        // ~1.5 kHz
        val snareEnd = (bands * 0.80f).toInt()          // ~5 kHz

        val kickFlux = flux(levels, 0, bassEnd)
        val snareFlux = flux(levels, snareStart, snareEnd)
        levels.copyInto(previous)

        kickLevel = max(kickLevel * KICK_DECAY, kick.strength(kickFlux))
        snareLevel = max(snareLevel * SNARE_DECAY, snare.strength(snareFlux))

        levels.copyInto(detail)
        leveler.apply(detail)

        for (i in 0 until bands) {
            val f = i.toFloat() / bands
            val weight = when {
                f < 0.2f -> 0.55f   // bass
                f < 0.55f -> 0.30f  // vocals / melody: least
                else -> 0.45f       // snare, hats, cymbals
            }
            val lift = KICK_HEIGHT * kickLevel + SNARE_HEIGHT * snareLevel
            levels[i] = (REST + lift + weight * detail[i]).coerceIn(0f, MAX_LEVEL)
        }
    }

    /** Average positive change of bands [from, until) since the previous frame. */
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
        const val KICK_DECAY = 0.86f    // per frame: a kick settles in ~0.2 s
        const val SNARE_DECAY = 0.80f   // snares are shorter
        const val REST = 0.06f
        const val KICK_HEIGHT = 0.34f
        const val SNARE_HEIGHT = 0.20f
        const val MAX_LEVEL = 0.75f
    }
}
