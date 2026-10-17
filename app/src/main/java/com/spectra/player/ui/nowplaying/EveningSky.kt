package com.spectra.player.ui.nowplaying

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
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

/**
 * Full-screen sky. [time] (seconds) and [energy] (0..1, bass) are read only while drawing, so the
 * sky animates every frame without recomposing anything.
 */
@Composable
fun EveningSky(
    time: () -> Float,
    energy: () -> Float,
    modifier: Modifier = Modifier,
) {
    Spacer(
        modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val dp = density
            val rnd = Random(20261017)

            val count = ((w * h) / (dp * dp) / 1500f).toInt().coerceIn(90, 260)
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

            val sky = Brush.verticalGradient(
                0f to Dusk.SkyTop,
                0.35f to Dusk.SkyUpper,
                0.62f to Dusk.SkyMid,
                0.86f to Dusk.SkyLow,
                1f to Dusk.Horizon,
            )
            val haze = Brush.radialGradient(
                colors = listOf(Dusk.Haze.copy(alpha = 0.20f), Dusk.Haze.copy(alpha = 0.06f), Color.Transparent),
                center = Offset(w / 2f, h * 1.08f),
                radius = w * 1.0f,
            )

            onDrawBehind {
                drawRect(sky)
                drawRect(haze)

                val t = time()
                val e = energy().coerceIn(0f, 1f)

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

                drawShootingStar(t, w, h, dp)
            }
        },
    )
}

/** One shooting star in most 12 s windows, at a random moment, place and direction. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawShootingStar(t: Float, w: Float, h: Float, dp: Float) {
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
