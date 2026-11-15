package com.spectra.player.ui.theme

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.SweepGradient
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.spectra.player.MainActivity
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The Spectra icon drawn in the user's custom colours. Android doesn't let an app repaint its
 * real launcher icon, so this is offered as a second icon the user adds to the home screen.
 */
object CustomIcon {
    /** Same drawing as the built-in icons (108-unit adaptive icon grid), in [p]'s colours. */
    fun render(p: CustomPalette, sizePx: Int = 432): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val u = sizePx / 108f
        val c = sizePx / 2f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, sizePx.toFloat(), p.skyTop, p.skyBottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), paint)

        val stars = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.stars }
        val rnd = Random(11)
        repeat(18) {
            val x = rnd.nextFloat() * 108f
            val y = rnd.nextFloat() * 108f
            stars.alpha = (90 + rnd.nextInt(120))
            canvas.drawCircle(x * u, y * u, (0.4f + rnd.nextFloat() * 0.5f) * u, stars)
        }

        val bars = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.4f * u
            strokeCap = Paint.Cap.ROUND
            shader = SweepGradient(c, c, intArrayOf(p.bars1, p.bars2, p.bars1, p.bars2, p.bars1), null)
        }
        val count = 36
        for (i in 0 until count) {
            val a = (i.toFloat() / count * 2 * Math.PI - Math.PI / 2).toFloat()
            // Longer bars on the lower right, like the built-in icon.
            val level = 0.25f + 0.75f * (0.5f + 0.5f * cos(a - 0.9f)).let { it * it }
            val inner = 20.5f * u
            val outer = inner + (3.5f + 7f * level) * u
            canvas.drawLine(c + cos(a) * inner, c + sin(a) * inner, c + cos(a) * outer, c + sin(a) * outer, bars)
        }

        val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.buttons }
        canvas.drawCircle(c, c, 16f * u, disc)
        val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.skyTop }
        canvas.drawCircle(c, c, 5f * u, hole)
        return bitmap
    }

    /** Asks the launcher to add the icon. False when the launcher doesn't support that. */
    fun pin(context: Context, p: CustomPalette): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        val open = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN)
        val shortcut = ShortcutInfoCompat.Builder(context, "spectra_custom_${p.encode().hashCode()}")
            .setShortLabel("Spectra")
            .setIcon(IconCompat.createWithAdaptiveBitmap(render(p)))
            .setIntent(open)
            .build()
        return ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }
}
