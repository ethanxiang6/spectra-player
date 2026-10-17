package com.spectra.player

import com.spectra.player.data.AudioProfile
import com.spectra.player.visualizer.BandLeveler
import com.spectra.player.visualizer.BandMapper
import com.spectra.player.visualizer.LiveShaper
import com.spectra.player.visualizer.SimulatedSpectrum
import com.spectra.player.visualizer.SpectrumSmoother
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SpectrumMathTest {

    @Test
    fun toneLandsInTheBandThatContainsItsFrequency() {
        val sampleRate = 44_100
        val fftSize = 1024
        val bands = 32
        val mapper = BandMapper(bands, fftSize, sampleRate)

        val bin = 100 // ~4.3 kHz
        val fft = ByteArray(fftSize)
        fft[2 * bin] = 100 // real part; imaginary stays 0

        val out = FloatArray(bands)
        val peak = mapper.map(fft, out)
        assertTrue(peak > 0f)

        val hz = bin * sampleRate / fftSize.toFloat()
        val expected = (0 until bands).first { hz >= mapper.edgesHz[it] && hz < mapper.edgesHz[it + 1] }
        val loudest = out.indices.maxByOrNull { out[it] }!!
        assertTrue("loudest=$loudest expected=$expected", abs(loudest - expected) <= 1)
    }

    @Test
    fun silenceMapsToZero() {
        val mapper = BandMapper(bands = 24, fftSize = 1024, sampleRateHz = 48_000)
        val out = FloatArray(24) { 1f }
        assertEquals(0f, mapper.map(ByteArray(1024), out), 0f)
        assertTrue(out.all { it == 0f })
    }

    @Test
    fun bandEdgesAreIncreasingAndStayBelowNyquist() {
        val mapper = BandMapper(bands = 48, fftSize = 1024, sampleRateHz = 22_050)
        for (i in 0 until 48) assertTrue(mapper.edgesHz[i] < mapper.edgesHz[i + 1])
        assertTrue(mapper.edgesHz.last() <= 22_050 / 2f + 0.5f)
    }

    @Test
    fun simulatedSpectrumStaysInRangeAndIsSilentWhenPaused() {
        val sim = SimulatedSpectrum(48)
        val out = FloatArray(48)
        for (pos in 0L..20_000L step 137L) {
            sim.frame(pos, playing = true, profile = AudioProfile.Default, seed = 42, out = out)
            assertTrue(out.all { it in 0f..1f })
        }
        sim.frame(1_000, playing = false, profile = AudioProfile.Default, seed = 42, out = out)
        assertTrue(out.all { it == 0f })
    }

    @Test
    fun kickOnTheBeatLiftsTheLowBandsMoreThanTheTailOfTheBeat() {
        val sim = SimulatedSpectrum(48)
        val profile = AudioProfile.Default.copy(tempo = 120f) // 500 ms per beat
        val onBeat = FloatArray(48)
        val lateInBeat = FloatArray(48)
        sim.frame(0, true, profile, seed = 7, out = onBeat)
        sim.frame(450, true, profile, seed = 7, out = lateInBeat)
        assertTrue(onBeat.take(4).sum() > lateInBeat.take(4).sum())
    }

    @Test
    fun smootherAttacksFasterThanItReleases() {
        val smoother = SpectrumSmoother(size = 8)
        val up = smoother.step(FloatArray(8) { 1f }, dtMs = 16)[3]
        val peakBeforeRelease = up
        val down = smoother.step(FloatArray(8), dtMs = 16)[3]
        val risen = up - 0f
        val fallen = peakBeforeRelease - down
        assertTrue("risen=$risen fallen=$fallen", risen > fallen)
    }

    @Test
    fun levelerEvensOutLoudBassAndQuietTrebleAndCapsHeight() {
        val leveler = BandLeveler(2)
        val frame = FloatArray(2)
        repeat(120) {
            frame[0] = 0.9f // bass: always loud
            frame[1] = 0.25f // treble: always quiet
            leveler.apply(frame)
        }
        assertTrue("bass=${frame[0]} treble=${frame[1]}", abs(frame[0] - frame[1]) < 0.1f)
        assertTrue(frame[0] <= 0.8f && frame[1] <= 0.8f)
    }

    @Test
    fun liveShaperKeepsTheRingRoundAndJumpsOnABassHit() {
        val bands = 48
        val shaper = LiveShaper(bands)
        val frame = FloatArray(bands)
        // Steady music, loud bass and quiet treble: ring should be close to a circle.
        repeat(240) {
            for (i in 0 until bands) frame[i] = if (i < 6) 0.8f else 0.3f
            shaper.apply(frame)
        }
        val spread = frame.max() - frame.min()
        assertTrue("spread=$spread", spread < 0.2f)
        val resting = frame.average()

        // A kick: bass jumps.
        for (i in 0 until bands) frame[i] = if (i < 6) 1f else 0.3f
        shaper.apply(frame)
        assertTrue("resting=$resting after=${frame.average()}", frame.average() > resting + 0.1)
        assertTrue(frame.all { it <= 0.72f })
    }
}
