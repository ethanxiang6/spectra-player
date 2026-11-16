package com.spectra.player.widget

/** The widget's small sums, kept apart from Android so they can be tested. */
object WidgetMath {
    /** How many places along the progress line can be tapped to jump there. */
    const val SEEK_STEPS = 20

    /** Where in the song a tap on seek step [step] lands: the first step restarts the song. */
    fun seekFraction(step: Int, steps: Int = SEEK_STEPS): Float =
        if (step <= 0) 0f else ((step + 0.5f) / steps).coerceIn(0f, 0.98f)

    /** Width left for the title and progress line, in dp, in a widget [widgetDp] wide. */
    fun contentWidthDp(widgetDp: Int): Int =
        if (widgetDp <= 0) 150 else (widgetDp - 10 - 44 - 10 - 2 - 38 - 40 - 38 - 4).coerceAtLeast(70)
}
