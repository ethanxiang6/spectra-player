package com.spectra.player

import com.spectra.player.widget.WidgetMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetMathTest {
    @Test fun firstSeekStepRestartsAndTheLastStaysInsideTheSong() {
        assertEquals(0f, WidgetMath.seekFraction(0), 0f)
        assertEquals(0.525f, WidgetMath.seekFraction(10), 0.001f)
        assertTrue(WidgetMath.seekFraction(19) < 1f)
    }

    @Test fun contentWidthHasAFloorAndAFallback() {
        assertEquals(150, WidgetMath.contentWidthDp(0))
        assertEquals(70, WidgetMath.contentWidthDp(120))
        assertEquals(134, WidgetMath.contentWidthDp(320))
    }
}
