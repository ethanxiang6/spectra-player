package com.spectra.player.visualizer

import kotlin.math.exp

/**
 * Per-band envelope follower: fast attack so beats punch, slow release so bars fall gracefully.
 * A light 3-tap blur keeps neighbouring bars coherent. Pure Kotlin.
 */
class SpectrumSmoother(
    private val size: Int,
    private val attackMs: Float = 60f,
    private val releaseMs: Float = 200f,
) {
    private val state = FloatArray(size)
    private val blurred = FloatArray(size)

    /** Advances the envelope by [dtMs] toward [target] and returns a fresh copy of the result. */
    fun step(target: FloatArray, dtMs: Long): FloatArray {
        for (i in 0 until size) {
            val left = target[(i - 1).coerceAtLeast(0)]
            val right = target[(i + 1).coerceAtMost(size - 1)]
            blurred[i] = target[i] * 0.6f + (left + right) * 0.2f
        }
        for (i in 0 until size) {
            val tau = if (blurred[i] > state[i]) attackMs else releaseMs
            val alpha = 1f - exp(-dtMs / tau)
            state[i] += (blurred[i] - state[i]) * alpha
        }
        return state.copyOf()
    }
}
