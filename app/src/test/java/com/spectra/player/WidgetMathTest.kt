package com.spectra.player

import com.spectra.player.widget.WidgetMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetMathTest {
    @Test fun bandsAreAveragedIntoBars() {
        val spectrum = FloatArray(48) { if (it < 24) 1f else 0f }
        val bars = WidgetMath.bars(spectrum, 24)
        assertEquals(24, bars.size)
        assertEquals(1f, bars[0], 0.001f)
        assertEquals(1f, bars[11], 0.001f)
        assertEquals(0f, bars[12], 0.001f)
    }

    @Test fun fewerBandsThanBarsStillFillsEveryBar() {
        val bars = WidgetMath.bars(floatArrayOf(0.5f, 2f), 6)
        assertTrue(bars.all { it in 0f..1f })
        assertEquals(0.5f, bars[0], 0.001f)
        assertEquals(1f, bars[5], 0.001f)
    }

    @Test fun emptySpectrumGivesFlatBars() {
        assertTrue(WidgetMath.bars(FloatArray(0), 8).all { it == 0f })
    }

    @Test fun firstSeekStepRestartsAndTheLastStaysInsideTheSong() {
        assertEquals(0f, WidgetMath.seekFraction(0), 0f)
        assertEquals(0.55f, WidgetMath.seekFraction(5), 0.001f)
        assertTrue(WidgetMath.seekFraction(9) < 1f)
    }

    @Test fun contentWidthHasAFloorAndAFallback() {
        assertEquals(150, WidgetMath.contentWidthDp(0))
        assertEquals(70, WidgetMath.contentWidthDp(120))
        assertEquals(134, WidgetMath.contentWidthDp(320))
    }
}
