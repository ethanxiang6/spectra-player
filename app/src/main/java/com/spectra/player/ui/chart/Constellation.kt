package com.spectra.player.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** One labelled star on the map: a playlist, or the "pole star" for Liked songs. */
data class ChartStar(
    val key: String,
    val name: String,
    val meta: String?,
    /** Number of songs; sets how bright (big) the star is. */
    val weight: Int,
    /** Drawn in the accent colour with spikes, like a pole star. */
    val pole: Boolean = false,
)

private class Placed(
    val x: Float,
    val y: Float,
    val radius: Float,
    val labelX: Float,
    val labelY: Float,
    val labelWidth: Float,
    val group: Int,
)

private class MapLayout(val stars: List<Placed>, val height: Float, val fieldStars: List<Triple<Float, Float, Float>>)

/**
 * Playlists drawn as a star map. Each playlist is a star sized by its song count with its name
 * beside it in italic serif; neighbours are joined into small constellations by thin lines,
 * over a faint graticule. Positions come from the playlist ids, so the map stays the same
 * from one visit to the next.
 */
@Composable
fun ConstellationMap(
    stars: List<ChartStar>,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (stars.isEmpty()) return
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val nameStyle = haloed(MaterialTheme.typography.headlineSmall.copy(fontSize = 23.sp, lineHeight = 26.sp))
    val metaStyle = haloed(MaterialTheme.typography.labelSmall)
    val ink = Chart.ink
    val dim = Chart.dim
    val line = Chart.line
    val accent = Chart.accent

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val width = constraints.maxWidth.toFloat()
        val layout = remember(stars, width, density) {
            layoutMap(stars, width, density) { text, meta ->
                val w1 = measurer.measure(text, nameStyle, maxLines = 1).size.width
                val w2 = if (meta != null) measurer.measure(meta, metaStyle, maxLines = 1).size.width else 0
                maxOf(w1, w2).toFloat()
            }
        }
        val heightDp = with(density) { layout.height.toDp() }

        Box(Modifier.fillMaxWidth().height(heightDp).clipToBounds()) {
            Canvas(Modifier.fillMaxWidth().height(heightDp)) {
                // Graticule: declination arcs and hour lines around a pole off the top right.
                val pole = Offset(size.width * 1.1f, -size.height * 0.15f)
                val grid = line.copy(alpha = line.alpha * 0.35f)
                val step = 110.dp.toPx()
                var r = step
                val far = hypot(size.width * 1.2f, size.height * 1.2f)
                while (r < far) {
                    drawCircle(grid, r, pole, style = Stroke(0.8.dp.toPx()))
                    r += step
                }
                for (k in 0 until 9) {
                    val a = (PI / 2 + k * PI / 16).toFloat()
                    drawLine(grid, pole, Offset(pole.x + cos(a) * far, pole.y + sin(a) * far), 0.8.dp.toPx())
                }
                // Scale ticks down the left margin.
                val tick = 31.dp.toPx()
                var ty = tick
                var n = 0
                while (ty < size.height) {
                    val len = if (n % 2 == 0) 7.dp.toPx() else 4.dp.toPx()
                    drawLine(line, Offset(0f, ty), Offset(len, ty), 1.dp.toPx())
                    ty += tick
                    n++
                }
                // Faint field stars.
                layout.fieldStars.forEach { (x, y, rr) -> drawCircle(dim.copy(alpha = 0.35f), rr, Offset(x, y)) }

                // Constellation lines, gapped around each star like a printed chart.
                val placed = layout.stars
                for (i in 1 until placed.size) {
                    val a = placed[i - 1]
                    val b = placed[i]
                    if (a.group != b.group) continue
                    val dx = b.x - a.x
                    val dy = b.y - a.y
                    val d = hypot(dx, dy)
                    if (d < 1f) continue
                    val ux = dx / d
                    val uy = dy / d
                    val ga = a.radius + 6.dp.toPx()
                    val gb = b.radius + 6.dp.toPx()
                    drawLine(
                        line.copy(alpha = 0.5f),
                        Offset(a.x + ux * ga, a.y + uy * ga),
                        Offset(b.x - ux * gb, b.y - uy * gb),
                        1.dp.toPx(),
                    )
                }
                // Stars.
                placed.forEachIndexed { i, p ->
                    val star = stars[i]
                    if (star.pole) {
                        drawChartStar(Offset(p.x, p.y), p.radius, accent, accent, spikes = 1f)
                    } else {
                        val bright = p.radius > 4.dp.toPx()
                        drawChartStar(Offset(p.x, p.y), p.radius, ink, accent.copy(alpha = 0.6f), spikes = if (bright) 0.7f else 0f)
                    }
                }
            }

            layout.stars.forEachIndexed { i, p ->
                val star = stars[i]
                Column(
                    Modifier
                        .offset { IntOffset(p.labelX.roundToInt(), p.labelY.roundToInt()) }
                        .width(with(density) { p.labelWidth.toDp() } + 8.dp)
                        .clickable(role = Role.Button, onClickLabel = "Open ${star.name}") { onTap(i) }
                        .padding(end = 8.dp),
                ) {
                    Text(
                        star.name,
                        style = nameStyle,
                        color = if (star.pole) accent else ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (star.meta != null) {
                        Text(star.meta.uppercase(), style = metaStyle, color = dim, maxLines = 1)
                    }
                }
                // The star itself is a target too.
                val hit = with(density) { 40.dp.toPx() }
                Box(
                    Modifier
                        .offset { IntOffset((p.x - hit / 2).roundToInt(), (p.y - hit / 2).roundToInt()) }
                        .width(40.dp)
                        .height(40.dp)
                        .clickable(role = Role.Button, onClickLabel = "Open ${star.name}") { onTap(i) },
                )
            }
        }
    }
}

/**
 * One star per row, scattered sideways. Stars are grouped into constellations of three to five;
 * inside a group each star stays within a short sideways step of the one before, so the lines
 * between them run steeply and mostly clear the labels.
 */
private fun layoutMap(
    stars: List<ChartStar>,
    width: Float,
    density: Density,
    measure: (String, String?) -> Float,
): MapLayout = with(density) {
    val rowH = 64.dp.toPx()
    val top = 30.dp.toPx()
    val left = 30.dp.toPx()
    val right = 16.dp.toPx()
    val gap = 14.dp.toPx()
    val maxStep = 72.dp.toPx()
    val nameHalf = 13.dp.toPx()
    val maxWeight = stars.maxOf { it.weight }.coerceAtLeast(1)

    val placed = ArrayList<Placed>(stars.size)
    var group = 0
    var leftInGroup = 0
    var prevX = 0f
    stars.forEachIndexed { i, star ->
        val rnd = Random(star.key.hashCode())
        if (leftInGroup == 0) {
            group++
            leftInGroup = 3 + rnd.nextInt(3)
        }
        val labelW = measure(star.name, star.meta).coerceAtMost(width * 0.66f)
        val xMax = (width - right - labelW - gap).coerceAtLeast(left)
        val startOfGroup = placed.isEmpty() || placed.last().group != group
        val x = if (startOfGroup) {
            left + rnd.nextFloat() * (xMax - left)
        } else {
            (prevX + (rnd.nextFloat() * 2f - 1f) * maxStep).coerceIn(left, xMax)
        }
        val y = top + i * rowH + (rnd.nextFloat() - 0.5f) * 14.dp.toPx() + (if (startOfGroup && i > 0) 6.dp.toPx() else 0f)
        val magnitude = (log10(star.weight + 1f) / log10(maxWeight + 1f)).coerceIn(0f, 1f)
        val radius = if (star.pole) 4.5.dp.toPx() else (1.8f + 3.2f * magnitude).dp.toPx()
        placed += Placed(
            x = x,
            y = y,
            radius = radius,
            labelX = x + gap,
            labelY = y - nameHalf,
            labelWidth = labelW,
            group = group,
        )
        prevX = x
        leftInGroup--
    }
    val height = top + stars.size * rowH + 4.dp.toPx()

    // A sprinkling of faint background stars, the same every time for this list.
    val field = Random(stars.size * 31 + stars.first().key.hashCode())
    val fieldStars = List((stars.size * 3).coerceAtMost(90)) {
        Triple(field.nextFloat() * width, field.nextFloat() * height, (0.6f + field.nextFloat() * 0.9f).dp.toPx())
    }
    MapLayout(placed, height, fieldStars)
}
