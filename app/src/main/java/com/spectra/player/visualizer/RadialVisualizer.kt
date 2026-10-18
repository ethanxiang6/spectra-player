package com.spectra.player.visualizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

enum class VisualizerStyle {
    BARS, WAVE, DOTS;

    fun next(): VisualizerStyle = entries[(ordinal + 1) % entries.size]
}

private const val TAU = 6.2831855f
private const val HALF_PI = 1.5707964f
private const val PI_F = 3.1415927f

/** Average of the lowest bands: drives the pulse of the glow ring and the album art. */
fun FloatArray.bassLevel(): Float {
    val n = min(4, size)
    if (n == 0) return 0f
    var sum = 0f
    for (i in 0 until n) sum += this[i]
    return sum / n
}

/**
 * Radial spectrum analyzer drawn on a Compose [Canvas].
 *
 * The spectrum is mirrored left/right around the vertical axis (bass at the top, treble at the
 * bottom) and drawn outward from a central disc, where [centerContent] (album art) sits.
 *
 * Interaction: tap cycles BARS -> WAVE -> DOTS; touching or dragging pushes up the bars nearest
 * the finger, like a magnet.
 *
 * [spectrum] is a lambda so the ~60 fps frames are read in the *draw* phase only: the surrounding
 * UI never recomposes per frame.
 */
@Composable
fun RadialSpectrumVisualizer(
    spectrum: () -> FloatArray,
    style: VisualizerStyle,
    onStyleChange: (VisualizerStyle) -> Unit,
    modifier: Modifier = Modifier,
    /** Radius of the central disc as a fraction of half the visualizer's size (the art's diameter = this x width). */
    innerRadiusFraction: Float = 0.56f,
    /** Sweep-gradient colours; defaults to the theme's primary / secondary / tertiary. */
    colors: List<Color>? = null,
    /** False: no tap / drag handling, so a parent can use taps (e.g. to show controls). */
    interactive: Boolean = true,
    /** Draws a soft, wide underlay so the ring glows against a dark background. */
    glow: Boolean = false,
    /** True: left/right mirror image. False: frequencies sweep once around the ring (asymmetric). */
    mirrored: Boolean = true,
    /** Degrees to turn the ring (not the centre content); read while drawing. */
    ringRotation: () -> Float = { 0f },
    /**
     * Intro progress, read while drawing: 0 = nothing, dots pop in from the top down both sides
     * until 1 (full ring of dots), then the bars grow in until 1.3. Default: fully shown.
     */
    reveal: () -> Float = { 2f },
    centerContent: @Composable BoxScope.() -> Unit = {},
) {
    var touch by remember { mutableStateOf<Offset?>(null) }
    val scheme = MaterialTheme.colorScheme
    val palette = remember(scheme.primary, scheme.secondary, scheme.tertiary, colors) {
        colors ?: listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.primary)
    }

    val gestures = if (interactive) {
        Modifier
            .semantics { contentDescription = "Audio visualizer. Tap to change style." }
            .pointerInput(style) {
                detectTapGestures(onTap = { onStyleChange(style.next()) })
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { touch = it },
                    onDrag = { change, _ -> touch = change.position },
                    onDragEnd = { touch = null },
                    onDragCancel = { touch = null },
                )
            }
    } else {
        Modifier.semantics { contentDescription = "Audio visualizer" }
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .then(gestures),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = ringRotation() }) {
            val bins = spectrum()
            if (bins.isEmpty()) return@Canvas

            val center = Offset(size.width / 2f, size.height / 2f)
            val half = min(size.width, size.height) / 2f
            val inner = half * (innerRadiusFraction + 0.06f) // ring sits just outside the album art
            val maxLen = half - inner - 4.dp.toPx()
            val brush = Brush.sweepGradient(palette, center)
            val touchAngle = touch?.let { atan2(it.y - center.y, it.x - center.x) }

            // Bass-reactive glow ring behind everything.
            val bass = bins.bassLevel() * revealGrowth(reveal())
            drawCircle(
                color = palette[0].copy(alpha = 0.10f + 0.30f * bass),
                radius = inner * (1.0f + 0.10f * bass),
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )

            val rv = reveal()
            when (style) {
                VisualizerStyle.BARS -> drawBars(bins, center, inner, maxLen, brush, touchAngle, glow, mirrored, rv)
                VisualizerStyle.WAVE -> drawWave(bins, center, inner, maxLen, brush, touchAngle, glow, mirrored, rv)
                VisualizerStyle.DOTS -> drawDots(bins, center, inner, maxLen, brush, touchAngle, glow, mirrored, rv)
            }
        }
        centerContent()
    }
}

// ---- Intro: dots pop in from the top, down both sides, then grow into bars ----------------

/** 0..1 for a slot during the intro: 0 = not shown yet, 1 = fully popped in. */
private fun revealPop(slot: Int, slots: Int, reveal: Float): Float {
    if (reveal >= 1f) return 1f
    val appearAt = slot.toFloat() / slots * 0.9f        // one clockwise sweep from the top
    val t = ((reveal - appearAt) / 0.1f).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)                         // smoothstep: each dot eases in
}

/** Bars stay as dots until the ring is complete, then grow in. */
private fun revealGrowth(reveal: Float): Float {
    val t = ((reveal - 1f) / 0.25f).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun fract(x: Float): Float = x - kotlin.math.floor(x)

/** Slot 0 is straight up; slots run clockwise; the second half mirrors the first. */
private fun slotAngle(slot: Int, slots: Int): Float = slot.toFloat() / slots * TAU - HALF_PI

private fun mirroredIndex(slot: Int, bands: Int): Int = if (slot < bands) slot else 2 * bands - 1 - slot

/** Level shown in [slot] of a ring with 2 x bands slots; unmirrored rings interpolate between bands. */
private fun levelFor(bins: FloatArray, slot: Int, mirrored: Boolean): Float {
    if (mirrored) return bins[mirroredIndex(slot, bins.size)]
    val pos = slot / 2f
    val a = pos.toInt() % bins.size
    val b = (a + 1) % bins.size // wraps: the sweep closes smoothly at the top
    val t = pos - pos.toInt()
    return bins[a] * (1f - t) + bins[b] * t
}

/** Band shown in [slot] of a ring with 2 x [bands] slots. */
private fun bandFor(slot: Int, bands: Int, mirrored: Boolean): Int =
    if (mirrored) mirroredIndex(slot, bands) else (slot / 2).coerceAtMost(bands - 1)

/** Extra height near the finger: gaussian falloff over ~14 degrees. */
private fun touchBoost(angle: Float, touchAngle: Float?): Float {
    if (touchAngle == null) return 0f
    var d = abs(angle - touchAngle) % TAU
    if (d > PI_F) d = TAU - d
    return 0.45f * exp(-(d * d) / (2f * 0.25f * 0.25f))
}

private fun DrawScope.drawBars(
    bins: FloatArray,
    center: Offset,
    inner: Float,
    maxLen: Float,
    brush: Brush,
    touchAngle: Float?,
    glow: Boolean,
    mirrored: Boolean,
    reveal: Float,
) {
    val slots = bins.size * 2
    val barWidth = (TAU * inner / slots) * 0.55f
    for (slot in 0 until slots) {
        val angle = slotAngle(slot, slots)
        val pop = revealPop(slot, slots, reveal)
        if (pop <= 0f) continue
        val level = min(1f, levelFor(bins, slot, mirrored) + touchBoost(angle, touchAngle)) * revealGrowth(reveal)
        val length = max(barWidth, level * maxLen)
        val dir = Offset(cos(angle), sin(angle))
        if (glow) {
            drawLine(
                brush = brush,
                start = center + dir * inner,
                end = center + dir * (inner + length),
                strokeWidth = barWidth * 2.8f,
                cap = StrokeCap.Round,
                alpha = (0.14f + 0.18f * level) * pop,
            )
        }
        drawLine(
            brush = brush,
            start = center + dir * inner,
            end = center + dir * (inner + length),
            strokeWidth = barWidth * (0.4f + 0.6f * pop),
            cap = StrokeCap.Round,
            alpha = pop,
        )
    }
}

private fun DrawScope.drawWave(
    bins: FloatArray,
    center: Offset,
    inner: Float,
    maxLen: Float,
    brush: Brush,
    touchAngle: Float?,
    glow: Boolean,
    mirrored: Boolean,
    reveal: Float,
) {
    val n = bins.size * 2
    if (reveal <= 0f) return
    val points = Array(n) { slot ->
        val angle = slotAngle(slot, n)
        val level = min(1f, levelFor(bins, slot, mirrored) + touchBoost(angle, touchAngle)) * revealGrowth(reveal)
        val r = inner + level * maxLen
        Offset(center.x + cos(angle) * r, center.y + sin(angle) * r)
    }

    // Closed, smoothed outline: quadratic curves through the midpoints of consecutive samples.
    fun mid(a: Offset, b: Offset) = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
    val path = Path().apply {
        val start = mid(points[n - 1], points[0])
        moveTo(start.x, start.y)
        for (i in 0 until n) {
            val control = points[i]
            val end = mid(points[i], points[(i + 1) % n])
            quadraticTo(control.x, control.y, end.x, end.y)
        }
        close()
    }
    val fade = reveal.coerceIn(0f, 1f)
    drawPath(path, brush = brush, alpha = 0.16f * fade)
    if (glow) drawPath(path, brush = brush, alpha = 0.22f * fade, style = Stroke(width = 10.dp.toPx(), join = StrokeJoin.Round))
    drawPath(path, brush = brush, alpha = fade, style = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round))
}

private fun DrawScope.drawDots(
    bins: FloatArray,
    center: Offset,
    inner: Float,
    maxLen: Float,
    brush: Brush,
    touchAngle: Float?,
    glow: Boolean,
    mirrored: Boolean,
    reveal: Float,
) {
    val slots = bins.size * 2
    for (slot in 0 until slots) {
        val angle = slotAngle(slot, slots)
        val pop = revealPop(slot, slots, reveal)
        if (pop <= 0f) continue
        val level = min(1f, levelFor(bins, slot, mirrored) + touchBoost(angle, touchAngle)) * revealGrowth(reveal)
        val dir = Offset(cos(angle), sin(angle))
        val radius = (1.6.dp.toPx() + level * 2.8.dp.toPx()) * pop
        // Faint resting ring + the reactive dot that lifts off it.
        drawCircle(Color.White.copy(alpha = 0.08f), 1.4.dp.toPx(), center + dir * inner)
        if (glow) drawCircle(brush, radius * 2.6f, center + dir * (inner + level * maxLen), alpha = 0.18f)
        drawCircle(brush, radius, center + dir * (inner + level * maxLen))
    }
}
