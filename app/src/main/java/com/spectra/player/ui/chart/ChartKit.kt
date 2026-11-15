package com.spectra.player.ui.chart

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The star-chart design language shared by the library, playlist, search and settings pages:
 * hairlines, small star markers, catalogue labels in mono, and square-cornered controls.
 */
object Chart {
    /** Main ink: titles, star cores. */
    val ink: Color @Composable get() = MaterialTheme.colorScheme.onSurface

    /** Secondary ink: artists, counts, captions. */
    val dim: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

    /** Hairlines: rules, frames, constellation lines. */
    val line: Color @Composable get() = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)

    /** The one bright accent: the playing song, the main button. */
    val accent: Color @Composable get() = MaterialTheme.colorScheme.primary

    /** Song progress: the accent, or the Custom theme's own slider colour. */
    val slider: Color
        @Composable get() = if (com.spectra.player.ui.nowplaying.Dusk.appCustom) MaterialTheme.colorScheme.secondary else accent

    val corner = RoundedCornerShape(2.dp)

    /** A dark soft halo behind label text, so lines that pass under a label don't hurt reading. */
    val labelHalo = Shadow(color = Color.Black.copy(alpha = 0.9f), blurRadius = 12f)

    /** Three-digit catalogue number: 007, 042, 214. */
    fun catalogNumber(n: Int): String = if (n < 1000) "%03d".format(n) else n.toString()
}

/**
 * A star as drawn on a chart: a soft glow, a solid core and, for bright ones, four thin
 * diffraction spikes. [radius] is the core radius.
 */
fun DrawScope.drawChartStar(
    center: Offset,
    radius: Float,
    core: Color,
    glow: Color,
    spikes: Float = 0f,
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(glow.copy(alpha = glow.alpha * 0.55f), Color.Transparent),
            center = center,
            radius = radius * 4f,
        ),
        radius = radius * 4f,
        center = center,
    )
    if (spikes > 0f) {
        val len = radius * 2.8f * spikes
        val w = (radius * 0.28f).coerceAtLeast(1f)
        val spikeColor = core.copy(alpha = 0.8f * spikes)
        drawLine(spikeColor, Offset(center.x - len, center.y), Offset(center.x + len, center.y), w)
        drawLine(spikeColor, Offset(center.x, center.y - len), Offset(center.x, center.y + len), w)
    }
    drawCircle(core, radius, center)
}

/** A single catalogue marker: a dim point, or a bright twinkling star for the current song. */
@Composable
fun StarMark(current: Boolean, playing: Boolean, modifier: Modifier = Modifier, size: Dp = 18.dp) {
    val accent = Chart.accent
    val dim = Chart.dim
    val twinkle = if (current && playing) {
        val t = rememberInfiniteTransition(label = "twinkle")
        val v by t.animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
            label = "twinkle",
        )
        v
    } else 1f
    Canvas(modifier.size(size)) {
        val c = Offset(this.size.width / 2, this.size.height / 2)
        if (current) {
            drawChartStar(c, 2.4.dp.toPx(), accent, accent, spikes = twinkle)
        } else {
            drawCircle(dim.copy(alpha = 0.55f), 1.3.dp.toPx(), c)
        }
    }
}

/** A telescope reticle, used as the search mark. */
@Composable
fun Reticle(modifier: Modifier = Modifier, color: Color = Chart.dim, size: Dp = 20.dp) {
    Canvas(modifier.size(size)) {
        val c = Offset(this.size.width / 2, this.size.height / 2)
        val r = this.size.minDimension * 0.3f
        val stroke = 1.2.dp.toPx()
        drawCircle(color, r, c, style = Stroke(stroke))
        val inner = r + 1.5.dp.toPx()
        val outer = this.size.minDimension / 2
        drawLine(color, Offset(c.x - outer, c.y), Offset(c.x - inner, c.y), stroke)
        drawLine(color, Offset(c.x + inner, c.y), Offset(c.x + outer, c.y), stroke)
        drawLine(color, Offset(c.x, c.y - outer), Offset(c.x, c.y - inner), stroke)
        drawLine(color, Offset(c.x, c.y + inner), Offset(c.x, c.y + outer), stroke)
        drawCircle(color, 1.dp.toPx(), c)
    }
}

/**
 * Section rule: `LIKED SONGS · 214 ──────────┤` in the catalogue voice, with the hairline
 * running to the edge and a tick at its end.
 */
@Composable
fun ChartRule(
    label: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val line = Chart.line
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelLarge, color = Chart.ink)
        if (meta != null) {
            Text(
                " · $meta",
                style = MaterialTheme.typography.labelLarge,
                color = Chart.dim,
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .weight(1f)
                .height(12.dp)
                .drawBehind {
                    val y = size.height / 2
                    val w = 1.dp.toPx()
                    drawLine(line, Offset(0f, y), Offset(size.width, y), w)
                    drawLine(line, Offset(size.width - w / 2, 0f), Offset(size.width - w / 2, size.height), w)
                },
        )
        trailing()
    }
}

/**
 * Square-cornered button with a hairline frame, label in the catalogue voice.
 * [filled] is the one primary action on a page.
 */
@Composable
fun ChartButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    filled: Boolean = false,
    enabled: Boolean = true,
    tint: Color? = null,
) {
    val accent = tint ?: Chart.accent
    val content = when {
        !enabled -> Chart.dim.copy(alpha = 0.5f)
        filled -> MaterialTheme.colorScheme.onPrimary
        tint != null -> tint
        else -> Chart.ink
    }
    Row(
        modifier = modifier
            .height(40.dp)
            .then(
                if (filled && enabled) Modifier.background(accent, Chart.corner)
                else Modifier.border(1.dp, if (tint != null) tint.copy(alpha = 0.6f) else Chart.line, Chart.corner),
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, color = content)
    }
}

/** A small square-cornered choice: filled when selected. Used for search kinds and setting options. */
@Composable
fun ChartChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(32.dp)
            .then(
                if (selected) Modifier.background(Chart.accent, Chart.corner)
                else Modifier.border(1.dp, Chart.line, Chart.corner),
            )
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else Chart.dim,
        )
    }
}

/** Two-position switch in the chart style: `OFF | ON`, the active side filled. */
@Composable
fun ChartSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val accent = Chart.accent
    Row(modifier.border(1.dp, Chart.line, Chart.corner)) {
        listOf(false to "OFF", true to "ON").forEach { (value, label) ->
            val active = value == checked
            Box(
                Modifier
                    .width(44.dp)
                    .height(30.dp)
                    .then(if (active) Modifier.background(if (value) accent else Chart.line, Chart.corner) else Modifier)
                    .clickable(role = Role.Switch) { onChange(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        active && value -> MaterialTheme.colorScheme.onPrimary
                        active -> Chart.ink
                        else -> Chart.dim
                    },
                )
            }
        }
    }
}

/** Setting row: label (and optional note) on the left, [ChartSwitch] on the right. */
@Composable
fun ChartSwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, note: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = Chart.ink)
            if (note != null) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = Chart.dim, modifier = Modifier.padding(top = 2.dp))
            }
        }
        ChartSwitch(checked, onChange)
    }
}

/**
 * Corner brackets around a picture, like the registration marks on a photographic plate.
 * Drawn [inset] outside the content.
 */
fun Modifier.plateMarks(color: Color, inset: Dp = 6.dp, arm: Dp = 12.dp): Modifier = drawWithContent {
    drawContent()
    val o = inset.toPx()
    val a = arm.toPx()
    val w = 1.dp.toPx()
    val l = -o
    val t = -o
    val r = size.width + o
    val b = size.height + o
    // top-left, top-right, bottom-left, bottom-right
    drawLine(color, Offset(l, t), Offset(l + a, t), w); drawLine(color, Offset(l, t), Offset(l, t + a), w)
    drawLine(color, Offset(r, t), Offset(r - a, t), w); drawLine(color, Offset(r, t), Offset(r, t + a), w)
    drawLine(color, Offset(l, b), Offset(l + a, b), w); drawLine(color, Offset(l, b), Offset(l, b - a), w)
    drawLine(color, Offset(r, b), Offset(r - a, b), w); drawLine(color, Offset(r, b), Offset(r, b - a), w)
}

/** A dotted hairline along the bottom edge, used under the search field. */
fun Modifier.dottedUnderline(color: Color): Modifier = drawBehind {
    val y = size.height - 0.5.dp.toPx()
    drawLine(
        color,
        Offset(0f, y),
        Offset(size.width, y),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx())),
    )
}

/**
 * A boxed note with a mono heading, e.g. `SIGNAL LOST` over the connection error.
 * [tone] colours the frame and heading.
 */
@Composable
fun ChartNote(
    heading: String,
    body: String,
    modifier: Modifier = Modifier,
    tone: Color = Chart.dim,
    actions: @Composable RowScope.() -> Unit = {},
    extra: @Composable () -> Unit = {},
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .border(1.dp, tone.copy(alpha = 0.55f), Chart.corner)
            .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
    ) {
        Text(heading.uppercase(), style = MaterialTheme.typography.labelMedium, color = tone)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Chart.ink, modifier = Modifier.padding(end = 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        extra()
    }
}

/** Text style for labels that sit over chart lines. */
@Composable
fun haloed(style: TextStyle): TextStyle = style.copy(shadow = Chart.labelHalo)
