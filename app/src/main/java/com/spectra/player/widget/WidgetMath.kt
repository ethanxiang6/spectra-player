package com.spectra.player.widget

/** The widget's small sums, kept apart from Android so they can be tested. */
object WidgetMath {
    /** How many places along the bars can be tapped to jump there. */
    const val SEEK_STEPS = 10

    /** Averages the visualizer's bands down to [count] bars. */
    fun bars(spectrum: FloatArray, count: Int, out: FloatArray = FloatArray(count)): FloatArray {
        if (spectrum.isEmpty()) {
            out.fill(0f)
            return out
        }
        for (i in 0 until count) {
            val from = i * spectrum.size / count
            val to = maxOf(from + 1, (i + 1) * spectrum.size / count).coerceAtMost(spectrum.size)
            var sum = 0f
            for (j in from until to) sum += spectrum[j]
            out[i] = (sum / (to - from)).coerceIn(0f, 1f)
        }
        return out
    }

    /** Where in the song a tap on seek step [step] lands: the first step restarts the song. */
    fun seekFraction(step: Int, steps: Int = SEEK_STEPS): Float =
        if (step <= 0) 0f else ((step + 0.5f) / steps).coerceIn(0f, 0.98f)

    /** Width left for the title and bars, in dp, in a widget [widgetDp] wide. */
    fun contentWidthDp(widgetDp: Int): Int =
        if (widgetDp <= 0) 150 else (widgetDp - 10 - 44 - 10 - 2 - 38 - 40 - 38 - 4).coerceAtLeast(70)
}
