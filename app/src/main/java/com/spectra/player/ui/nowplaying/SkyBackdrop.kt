package com.spectra.player.ui.nowplaying

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.spectra.player.ui.image.ArtImage
import com.spectra.player.visualizer.Backdrop
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Colours of the visualizer screen: a clear evening sky, darkest overhead, lighter at the horizon. */
object Dusk {
    val SkyTop = Color(0xFF03051A)
    val SkyUpper = Color(0xFF0A0F33)
    val SkyMid = Color(0xFF1B1A4B)
    val SkyLow = Color(0xFF34295F)
    val Horizon = Color(0xFF574170)
    val Haze = Color(0xFFE9A08E)

    val Lavender = Color(0xFFCBB9FF)
    val SkyBlue = Color(0xFF8FCBFF)
    val Rose = Color(0xFFFF9FC8)
    val Peach = Color(0xFFFFC4A6)

    val Text = Color(0xFFF5F2FF)
    val TextDim = Color(0xFFC9C3E6)

    /** Sweep around the ring: cool at the top, warm toward the horizon side. */
    val ring = listOf(Lavender, SkyBlue, Rose, Peach, Lavender)
}

private class Star(
    val x: Float,
    val y: Float,
    val radius: Float,
    val base: Float,
    val speed: Float,
    val phase: Float,
    val bright: Boolean,
)

private const val SHOOTING_CYCLE_S = 12f
private const val SHOOTING_DURATION_S = 0.95f

/** Colours and extras for one [Backdrop]. */
class BackdropLook(
    val stops: List<Pair<Float, Color>>,
    val haze: Color?,
    val hazeAlpha: Float,
    val starDensity: Float,
    /** Sweep-gradient colours for the visualizer ring. */
    val ring: List<Color>,
    val aurora: Boolean = false,
    val nebula: Boolean = false,
    val shootingStars: Boolean = true,
)

fun Backdrop.look(): BackdropLook = when (this) {
    Backdrop.EVENING -> BackdropLook(
        stops = listOf(0f to Dusk.SkyTop, 0.35f to Dusk.SkyUpper, 0.62f to Dusk.SkyMid, 0.86f to Dusk.SkyLow, 1f to Dusk.Horizon),
        haze = Dusk.Haze, hazeAlpha = 0.20f, starDensity = 1f, ring = Dusk.ring,
    )
    Backdrop.MIDNIGHT -> BackdropLook(
        stops = listOf(0f to Color(0xFF010208), 0.5f to Color(0xFF050B1F), 1f to Color(0xFF0F1C3E)),
        haze = Color(0xFF4C6FFF), hazeAlpha = 0.10f, starDensity = 1.5f,
        ring = listOf(Color(0xFF9FD8FF), Color(0xFFDCEBFF), Color(0xFF7FA8FF), Color(0xFFB9C8FF), Color(0xFF9FD8FF)),
    )
    Backdrop.SUNSET -> BackdropLook(
        stops = listOf(0f to Color(0xFF1A1440), 0.32f to Color(0xFF3B1E5A), 0.6f to Color(0xFF7A2E6A), 0.84f to Color(0xFFD0566A), 1f to Color(0xFFF59A6B)),
        haze = Color(0xFFFFC27A), hazeAlpha = 0.28f, starDensity = 0.45f,
        ring = listOf(Color(0xFFFFD3A1), Color(0xFFFF9F8A), Color(0xFFFF7EB0), Color(0xFFFFE6A8), Color(0xFFFFD3A1)),
        shootingStars = false,
    )
    Backdrop.AURORA -> BackdropLook(
        stops = listOf(0f to Color(0xFF020812), 0.55f to Color(0xFF06192A), 1f to Color(0xFF0B2A3A)),
        haze = Color(0xFF2FE0A0), hazeAlpha = 0.08f, starDensity = 1.1f,
        ring = listOf(Color(0xFF6CF5C2), Color(0xFF64D2FF), Color(0xFFB494FF), Color(0xFF9CFFD9), Color(0xFF6CF5C2)),
        aurora = true,
    )
    Backdrop.NEBULA -> BackdropLook(
        stops = listOf(0f to Color(0xFF05030F), 0.5f to Color(0xFF120A2A), 1f to Color(0xFF1E0F3A)),
        haze = null, hazeAlpha = 0f, starDensity = 1.3f,
        ring = listOf(Color(0xFFFF8AD8), Color(0xFFB08CFF), Color(0xFF7FD1FF), Color(0xFFFFB0E6), Color(0xFFFF8AD8)),
        nebula = true,
    )
    Backdrop.OCEAN -> BackdropLook(
        stops = listOf(0f to Color(0xFF010B18), 0.5f to Color(0xFF032238), 1f to Color(0xFF05475F)),
        haze = Color(0xFF3FD0FF), hazeAlpha = 0.14f, starDensity = 0.35f,
        ring = listOf(Color(0xFF7FE7FF), Color(0xFF4FB8FF), Color(0xFFA4F4E2), Color(0xFFCFF7FF), Color(0xFF7FE7FF)),
        shootingStars = false,
    )
    Backdrop.ALBUM -> BackdropLook(
        stops = emptyList(), haze = null, hazeAlpha = 0f, starDensity = 0.8f,
        ring = listOf(Color(0xFFFFFFFF), Color(0xFFE0D8FF), Color(0xFFFFD8E8), Color(0xFFD8F0FF), Color(0xFFFFFFFF)),
    )
    Backdrop.BLACK -> BackdropLook(
        stops = listOf(0f to Color.Black, 1f to Color.Black),
        haze = null, hazeAlpha = 0f, starDensity = 1f, ring = Dusk.ring,
    )
}

/**
 * Full-screen background for the visualizer. [time] (seconds) and [energy] (0..1, bass) are read
 * only while drawing, so it animates every frame without recomposing anything.
 */
@Composable
fun SkyBackdrop(
    backdrop: Backdrop,
    showStars: Boolean,
    artwork: ImageBitmap?,
    imageUrl: String?,
    time: () -> Float,
    energy: () -> Float,
    modifier: Modifier = Modifier,
) {
    val look = backdrop.look()
    Box(modifier.background(Color.Black)) {
        if (backdrop == Backdrop.ALBUM) {
            // The cover, heavily blurred and darkened (blur needs Android 12+; older devices get a dark cover).
            val blurred = Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = 1.3f; scaleY = 1.3f }
                .blur(60.dp)
            when {
                artwork != null -> Image(artwork, null, contentScale = ContentScale.Crop, modifier = blurred)
                imageUrl != null -> ArtImage(url = imageUrl, contentDescription = null, modifier = blurred)
            }
            Spacer(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.6f)))),
            )
        }
        SkyCanvas(look, showStars, time, energy, Modifier.fillMaxSize())
    }
}

@Composable
private fun SkyCanvas(
    look: BackdropLook,
    showStars: Boolean,
    time: () -> Float,
    energy: () -> Float,
    modifier: Modifier,
) {
    Spacer(
        modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val dp = density
            val rnd = Random(20261017)

            val count = if (showStars) ((w * h) / (dp * dp) / 1500f * look.starDensity).toInt().coerceIn(30, 380) else 0
            val stars = List(count) {
                val bright = rnd.nextFloat() < 0.08f
                Star(
                    x = rnd.nextFloat() * w,
                    // Bias toward the top: the sky near the horizon is too light for many stars.
                    y = rnd.nextFloat().pow(1.6f) * h * 0.9f,
                    radius = if (bright) (1.4f + rnd.nextFloat() * 0.9f) * dp else (0.45f + rnd.nextFloat() * 0.85f) * dp,
                    base = if (bright) 0.75f + rnd.nextFloat() * 0.25f else 0.25f + rnd.nextFloat() * 0.6f,
                    speed = 0.35f + rnd.nextFloat() * 1.4f,
                    phase = rnd.nextFloat() * 6.2831855f,
                    bright = bright,
                )
            }

            val sky = if (look.stops.size >= 2) Brush.verticalGradient(*look.stops.toTypedArray()) else null
            val haze = look.haze?.let {
                Brush.radialGradient(
                    colors = listOf(it.copy(alpha = look.hazeAlpha), it.copy(alpha = look.hazeAlpha * 0.3f), Color.Transparent),
                    center = Offset(w / 2f, h * 1.08f),
                    radius = w * 1.0f,
                )
            }

            onDrawBehind {
                sky?.let { drawRect(it) }
                haze?.let { drawRect(it) }

                val t = time()
                val e = energy().coerceIn(0f, 1f)

                if (look.nebula) drawNebula(t, e, w, h)
                if (look.aurora) drawAurora(t, e, w, h, dp)

                for (s in stars) {
                    val heightFade = 1f - 0.75f * (s.y / h).pow(1.5f)
                    var a = s.base * (0.62f + 0.38f * sin(t * s.speed + s.phase)) * heightFade
                    var r = s.radius
                    if (s.bright) {
                        a = (a + 0.3f * e).coerceAtMost(1f)
                        r *= 1f + 0.25f * e
                        drawCircle(Color.White, r * 3.6f, Offset(s.x, s.y), alpha = a * 0.14f)
                    }
                    drawCircle(Color.White, r, Offset(s.x, s.y), alpha = a.coerceIn(0f, 1f))
                }

                if (showStars && look.shootingStars) drawShootingStar(t, w, h, dp)
            }
        },
    )
}

/** Three slowly drifting colour clouds. */
private fun DrawScope.drawNebula(t: Float, e: Float, w: Float, h: Float) {
    val clouds = listOf(
        Triple(Color(0xFFB04CFF), 0.30f to 0.28f, 0.0f),
        Triple(Color(0xFFFF4FA3), 0.72f to 0.42f, 2.1f),
        Triple(Color(0xFF3F8CFF), 0.45f to 0.70f, 4.2f),
    )
    for ((color, pos, phase) in clouds) {
        val cx = w * (pos.first + 0.06f * sin(t * 0.05f + phase))
        val cy = h * (pos.second + 0.04f * cos(t * 0.04f + phase))
        val radius = w * (0.55f + 0.05f * sin(t * 0.07f + phase))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.22f + 0.10f * e), color.copy(alpha = 0.06f), Color.Transparent),
                center = Offset(cx, cy),
                radius = radius,
            ),
            radius = radius,
            center = Offset(cx, cy),
        )
    }
}

/** Curtains of light: thin vertical strokes whose height and brightness ripple along the width. */
private fun DrawScope.drawAurora(t: Float, e: Float, w: Float, h: Float, dp: Float) {
    val ribbons = listOf(Color(0xFF3CFFB0), Color(0xFF38D6FF), Color(0xFFA67CFF))
    val step = 4f * dp
    for ((r, color) in ribbons.withIndex()) {
        val baseY = h * (0.16f + 0.09f * r)
        val amp = h * 0.045f
        var x = 0f
        while (x <= w) {
            val u = x / w * 6.2831855f
            val y = baseY + amp * sin(u * 1.3f + t * 0.22f * (r + 1) + r) + amp * 0.5f * sin(u * 3.1f - t * 0.37f)
            val height = h * (0.07f + 0.04f * sin(u * 2.2f + t * 0.3f + r))
            val glow = (0.5f + 0.5f * sin(u * 4.5f + t * 0.5f + r * 2f)) * (0.75f + 0.5f * e)
            // Soft outer curtain, then a brighter core: a cheap vertical fade without per-line brushes.
            drawLine(color, Offset(x, y - height), Offset(x, y + height * 0.35f), strokeWidth = step * 1.2f, alpha = 0.05f * glow)
            drawLine(color, Offset(x, y - height * 0.45f), Offset(x, y + height * 0.12f), strokeWidth = step * 1.2f, alpha = 0.10f * glow)
            x += step
        }
    }
}

/** One shooting star in most 12 s windows, at a random moment, place and direction. */
private fun DrawScope.drawShootingStar(t: Float, w: Float, h: Float, dp: Float) {
    val cycle = floor(t / SHOOTING_CYCLE_S).toInt()
    val rr = Random(cycle * 7919 + 13)
    if (rr.nextFloat() > 0.75f) return
    val startAt = 1f + rr.nextFloat() * (SHOOTING_CYCLE_S - SHOOTING_DURATION_S - 1f)
    val local = t - cycle * SHOOTING_CYCLE_S - startAt
    if (local < 0f || local > SHOOTING_DURATION_S) return

    val p = local / SHOOTING_DURATION_S
    val leftward = rr.nextBoolean()
    val angle = 0.35f + rr.nextFloat() * 0.3f // radians below horizontal
    val dir = Offset(if (leftward) -cos(angle) else cos(angle), sin(angle))
    val origin = Offset(w * (0.2f + rr.nextFloat() * 0.6f), h * (0.04f + rr.nextFloat() * 0.3f))
    val travel = w * 0.5f
    val head = origin + dir * (travel * p)
    val tail = head - dir * (110f * dp * (0.4f + 0.6f * sin(p * PI.toFloat())))
    val alpha = sin(p * PI.toFloat())

    drawLine(
        brush = Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.85f * alpha)),
            start = tail,
            end = head,
        ),
        start = tail,
        end = head,
        strokeWidth = 1.6f * dp,
        cap = StrokeCap.Round,
    )
    drawCircle(Color.White, 2.2f * dp, head, alpha = 0.25f * alpha)
}
