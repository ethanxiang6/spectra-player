package com.spectra.player.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.text.TextPaint
import android.text.TextUtils
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import com.spectra.player.MainActivity
import com.spectra.player.R
import com.spectra.player.ui.nowplaying.look
import com.spectra.player.ui.theme.AppSettings
import com.spectra.player.visualizer.Backdrop

/** The colours the widget is drawn in, taken from the app's theme (or black and white). */
class WidgetColors(val bg: Int, val ink: Int, val dim: Int, val accent: Int, val buttons: Int, val outline: Int) {
    companion object {
        fun of(settings: AppSettings): WidgetColors = when {
            settings.monochrome -> WidgetColors(
                bg = Color.BLACK, ink = Color.WHITE, dim = 0xFF9A9A9A.toInt(),
                accent = Color.WHITE, buttons = Color.WHITE, outline = 0xFF2E2E2E.toInt(),
            )
            settings.theme == Backdrop.CUSTOM -> settings.custom.let { p ->
                WidgetColors(
                    bg = p.skyTop, ink = p.text, dim = ColorUtils.blendARGB(p.skyTop, p.text, 0.62f),
                    accent = p.bars1, buttons = p.buttons, outline = ColorUtils.blendARGB(p.skyTop, p.text, 0.16f),
                )
            }
            else -> {
                val look = settings.theme.look()
                val bg = (look.stops.firstOrNull()?.second?.toArgb() ?: 0xFF0B0B10.toInt()) or 0xFF000000.toInt()
                val ink = 0xFFF2EFFC.toInt()
                WidgetColors(
                    bg = bg, ink = ink, dim = ColorUtils.blendARGB(bg, ink, 0.62f),
                    accent = look.ring.firstOrNull()?.toArgb() ?: ink, buttons = ink,
                    outline = ColorUtils.blendARGB(bg, ink, 0.16f),
                )
            }
        }
    }
}

/** Everything the widget shows apart from the progress line. */
class WidgetContent(
    val title: String,
    val artist: String,
    val playing: Boolean,
    val art: Bitmap?,
    val settings: AppSettings,
)

object WidgetRenderer {
    fun widgetIds(context: Context): IntArray = try {
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, SpectraWidget::class.java))
    } catch (e: Exception) {
        IntArray(0)
    }

    /** Draws the whole widget: song, cover, buttons and the progress line. */
    fun renderAll(context: Context, content: WidgetContent, fraction: Float) {
        val manager = AppWidgetManager.getInstance(context)
        val colors = WidgetColors.of(content.settings)
        for (id in widgetIds(context)) {
            try {
                val widthDp = contentWidthDp(manager, id)
                val views = RemoteViews(context.packageName, R.layout.widget_spectra)
                val panel = if (content.settings.widgetBackground) android.view.View.VISIBLE else android.view.View.GONE
                views.setViewVisibility(R.id.widget_bg, panel)
                views.setViewVisibility(R.id.widget_outline, panel)
                views.setInt(R.id.widget_bg, "setColorFilter", colors.bg)
                views.setInt(R.id.widget_outline, "setColorFilter", colors.outline)
                views.setImageViewBitmap(R.id.widget_art, artBitmap(context, content.art, colors, content.settings.monochrome))
                views.setImageViewBitmap(R.id.widget_title, titleBitmap(context, content.title, content.artist, colors, widthDp))
                views.setImageViewBitmap(R.id.widget_bars, progressBitmap(context, fraction, colors, widthDp, null))
                views.setImageViewResource(
                    R.id.widget_play,
                    if (content.playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                )
                for (button in intArrayOf(R.id.widget_prev, R.id.widget_play, R.id.widget_next)) {
                    views.setInt(button, "setColorFilter", colors.buttons)
                }
                val open = PendingIntent.getActivity(
                    context, 0,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                views.setOnClickPendingIntent(R.id.widget_art, open)
                views.setOnClickPendingIntent(R.id.widget_title, open)
                views.setOnClickPendingIntent(R.id.widget_prev, command(context, 1, WidgetService.ACTION_PREVIOUS))
                views.setOnClickPendingIntent(R.id.widget_play, command(context, 2, WidgetService.ACTION_TOGGLE))
                views.setOnClickPendingIntent(R.id.widget_next, command(context, 3, WidgetService.ACTION_NEXT))
                SEEK_IDS.forEachIndexed { step, viewId ->
                    views.setOnClickPendingIntent(viewId, command(context, 10 + step, WidgetService.ACTION_SEEK, step))
                }
                manager.updateAppWidget(id, views)
            } catch (e: Exception) {
                // A widget that can't be drawn right now keeps what it showed before.
            }
        }
    }

    /** Redraws only the progress line: this runs once a second while music plays. */
    fun renderProgress(context: Context, settings: AppSettings, fraction: Float, reuse: Bitmap?): Bitmap? {
        val manager = AppWidgetManager.getInstance(context)
        val ids = widgetIds(context)
        if (ids.isEmpty()) return reuse
        return try {
            val bitmap = progressBitmap(context, fraction, WidgetColors.of(settings), contentWidthDp(manager, ids[0]), reuse)
            val views = RemoteViews(context.packageName, R.layout.widget_spectra)
            views.setImageViewBitmap(R.id.widget_bars, bitmap)
            manager.partiallyUpdateAppWidget(ids, views)
            bitmap
        } catch (e: Exception) {
            reuse
        }
    }

    private fun command(context: Context, code: Int, action: String, step: Int = -1): PendingIntent =
        PendingIntent.getForegroundService(
            context, code,
            Intent(context, WidgetService::class.java).setAction(action).putExtra(WidgetService.EXTRA_STEP, step),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun contentWidthDp(manager: AppWidgetManager, id: Int): Int = WidgetMath.contentWidthDp(
        try {
            manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        } catch (e: Exception) {
            0
        },
    )

    private fun Context.px(dp: Float): Float = dp * resources.displayMetrics.density

    /** Song title in the app's italic serif, then the artist in small capitals. */
    private fun titleBitmap(context: Context, title: String, artist: String, colors: WidgetColors, widthDp: Int): Bitmap {
        val w = context.px(widthDp.toFloat()).toInt().coerceAtLeast(8)
        val h = context.px(20f).toInt().coerceAtLeast(8)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val scaled = context.resources.displayMetrics.density * context.resources.configuration.fontScale.coerceIn(0.85f, 1.15f)
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = font(context, R.font.chart_serif_semibold_italic)
            textSize = 17f * scaled
            color = colors.ink
        }
        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = font(context, R.font.chart_mono_regular)
            textSize = 9.5f * scaled
            letterSpacing = 0.1f
            color = colors.dim
        }
        val gap = context.px(8f)
        val upper = artist.uppercase()
        val titleRoom = if (upper.isBlank()) w.toFloat() else w * 0.64f
        val shownTitle = TextUtils.ellipsize(title, titlePaint, titleRoom, TextUtils.TruncateAt.END).toString()
        val titleWidth = titlePaint.measureText(shownTitle)
        val baseline = h * 0.76f
        canvas.drawText(shownTitle, 0f, baseline, titlePaint)
        val artistRoom = w - titleWidth - gap
        if (upper.isNotBlank() && artistRoom > context.px(28f)) {
            val shownArtist = TextUtils.ellipsize(upper, artistPaint, artistRoom, TextUtils.TruncateAt.END).toString()
            canvas.drawText(shownArtist, titleWidth + gap, baseline, artistPaint)
        }
        return bitmap
    }

    private fun font(context: Context, id: Int) = try {
        ResourcesCompat.getFont(context, id)
    } catch (e: Exception) {
        null
    }

    /** A thin line with a knob at the playhead: how far the song has got, and where to tap to jump. */
    private fun progressBitmap(context: Context, fraction: Float, colors: WidgetColors, widthDp: Int, reuse: Bitmap?): Bitmap {
        val w = context.px(widthDp.toFloat()).toInt().coerceAtLeast(8)
        val h = context.px(26f).toInt().coerceAtLeast(8)
        val bitmap = if (reuse != null && !reuse.isRecycled && reuse.width == w && reuse.height == h) {
            reuse.also { it.eraseColor(Color.TRANSPARENT) }
        } else {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        }
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val knob = context.px(5f)
        val thick = context.px(3f)
        val y = h / 2f
        val left = knob
        val right = w - knob
        val at = left + (right - left) * fraction.coerceIn(0f, 1f)
        val rect = RectF(left, y - thick / 2f, right, y + thick / 2f)
        paint.color = ColorUtils.setAlphaComponent(colors.dim, 120)
        canvas.drawRoundRect(rect, thick / 2f, thick / 2f, paint)
        paint.color = colors.accent
        rect.right = at
        canvas.drawRoundRect(rect, thick / 2f, thick / 2f, paint)
        canvas.drawCircle(at, y, knob, paint)
        return bitmap
    }

    /** The cover with rounded corners, in grey when the app is in black and white. */
    private fun artBitmap(context: Context, art: Bitmap?, colors: WidgetColors, mono: Boolean): Bitmap {
        val size = context.px(44f).toInt().coerceAtLeast(8)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
        val radius = context.px(10f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (art != null && !art.isRecycled && art.width > 0 && art.height > 0) {
            val shader = BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val scale = size / minOf(art.width, art.height).toFloat()
            shader.setLocalMatrix(
                android.graphics.Matrix().apply {
                    setScale(scale, scale)
                    postTranslate((size - art.width * scale) / 2f, (size - art.height * scale) / 2f)
                },
            )
            paint.shader = shader
            if (mono) paint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
            canvas.drawRoundRect(rect, radius, radius, paint)
        } else {
            // No cover yet: a quiet ring, like the reticle in the app.
            paint.color = ColorUtils.blendARGB(colors.bg, colors.ink, 0.10f)
            canvas.drawRoundRect(rect, radius, radius, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = context.px(1.5f)
            paint.color = colors.dim
            canvas.drawCircle(size / 2f, size / 2f, size * 0.2f, paint)
        }
        return bitmap
    }

    private val SEEK_IDS = intArrayOf(
        R.id.widget_seek_0,
        R.id.widget_seek_1,
        R.id.widget_seek_2,
        R.id.widget_seek_3,
        R.id.widget_seek_4,
        R.id.widget_seek_5,
        R.id.widget_seek_6,
        R.id.widget_seek_7,
        R.id.widget_seek_8,
        R.id.widget_seek_9,
        R.id.widget_seek_10,
        R.id.widget_seek_11,
        R.id.widget_seek_12,
        R.id.widget_seek_13,
        R.id.widget_seek_14,
        R.id.widget_seek_15,
        R.id.widget_seek_16,
        R.id.widget_seek_17,
        R.id.widget_seek_18,
        R.id.widget_seek_19,
    )
}
