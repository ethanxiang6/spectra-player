package com.spectra.player.visualizer

import kotlin.math.max
import kotlin.math.pow

/**
 * Evens out a live spectrum around the ring. Music has far more energy in the bass than in the
 * treble, so raw levels max out the low bands (top of the ring) while the high bands (bottom)
 * barely move. Each band is scaled against its own recent peak, then compressed and capped so
 * nothing sits pinned at full height.
 */
class BandLeveler(bands: Int) {
    private val envelope = FloatArray(bands) { START_ENVELOPE }

    fun apply(levels: FloatArray) {
        for (i in levels.indices) {
            if (i >= envelope.size) break
            val raw = levels[i]
            envelope[i] = max(max(raw, envelope[i] * DECAY), MIN_ENVELOPE)
            val relative = (raw / envelope[i]).coerceIn(0f, 1f)
            levels[i] = MAX_LEVEL * relative.pow(CURVE)
        }
    }

    private companion object {
        const val START_ENVELOPE = 0.3f
        const val DECAY = 0.996f        // per frame (~60 fps): a band's ceiling halves in ~3 s
        const val MIN_ENVELOPE = 0.12f  // keeps near-silent bands from being blown up into noise
        const val CURVE = 1.35f         // soft: only real peaks reach the top of the range
        const val MAX_LEVEL = 0.78f     // toned down: bars never fill the whole ring
    }
}
