package com.spectra.player.visualizer

import kotlin.math.max

/**
 * Turns a live spectrum into a ring that stays close to a circle and jumps on the beat.
 *
 *  - Beat: a sudden rise in bass energy (kick / drums) compared with its recent average. It
 *    lifts the whole ring at once, then falls back quickly.
 *  - Detail: each band, scaled against its own recent peak, adds only a small ripple; the vocal
 *    / melody range (mids) is weighted lowest so singing moves the ring less than drums.
 *
 * Input: raw band levels (0..1, bass first). Output written back in place.
 */
class LiveShaper(private val bands: Int) {
    private val leveler = BandLeveler(bands)
    private val detail = FloatArray(bands)
    private var bassAverage = 0f
    private var bassPeak = 0.2f
    private var beat = 0f

    fun apply(levels: FloatArray) {
        // 1. Beat from the lowest bands (~40-150 Hz).
        val bassBands = max(1, bands / 8)
        var bass = 0f
        for (i in 0 until bassBands) bass += levels[i]
        bass /= bassBands
        bassAverage += (bass - bassAverage) * AVERAGE_RATE
        bassPeak = max(max(bass, bassPeak * PEAK_DECAY), MIN_BASS_PEAK)
        val onset = ((bass - bassAverage * ONSET_RATIO) / bassPeak).coerceIn(0f, 1f)
        beat = max(beat * BEAT_DECAY, (onset * BEAT_GAIN).coerceAtMost(1f))

        // 2. Per-band detail, evened out by the leveler, then weighted down.
        levels.copyInto(detail)
        leveler.apply(detail)
        var mean = 0f
        for (i in 0 until bands) mean += detail[i]
        mean /= bands

        for (i in 0 until bands) {
            val f = i.toFloat() / bands
            val weight = when {
                f < 0.2f -> 0.55f   // bass
                f < 0.7f -> 0.30f   // vocals / melody: least
                else -> 0.45f       // hats, cymbals
            }
            // Ripple around the ring's average, so the shape stays round.
            val ripple = weight * (detail[i] - mean) + 0.5f * weight * mean
            levels[i] = (REST + BEAT_HEIGHT * beat + ripple).coerceIn(0f, MAX_LEVEL)
        }
    }

    private companion object {
        const val AVERAGE_RATE = 0.06f   // per frame: bass average follows over ~0.3 s
        const val PEAK_DECAY = 0.995f
        const val MIN_BASS_PEAK = 0.15f
        const val ONSET_RATIO = 1.05f
        const val BEAT_GAIN = 2.2f
        const val BEAT_DECAY = 0.88f     // per frame: a jump settles in ~0.25 s
        const val REST = 0.10f           // resting ring height
        const val BEAT_HEIGHT = 0.42f
        const val MAX_LEVEL = 0.72f
    }
}
