package com.spectra.player.ui.image

import android.graphics.Bitmap
import android.graphics.Color
import coil.size.Size
import coil.transform.Transformation
import kotlin.math.abs

/**
 * Makes cover art suitable for round and square frames:
 *  1. trims flat borders baked into the image (letterbox bars on video thumbnails, padding),
 *  2. crops the remaining picture to a centred square.
 */
object ArtworkCrop {

    private const val MAX_TRIM_FRACTION = 0.3f // never cut more than 30% from one side
    private const val TOLERANCE = 18 // per-channel difference that still counts as "flat"
    private const val SAMPLES = 48

    fun squareCrop(source: Bitmap): Bitmap {
        val src = if (source.config == Bitmap.Config.HARDWARE) {
            source.copy(Bitmap.Config.ARGB_8888, false) ?: return source
        } else {
            source
        }
        val w = src.width
        val h = src.height
        if (w < 8 || h < 8) return src

        val maxY = (h * MAX_TRIM_FRACTION).toInt()
        val maxX = (w * MAX_TRIM_FRACTION).toInt()
        var top = 0
        while (top < maxY && rowIsFlat(src, top)) top++
        var bottom = h - 1
        while (h - 1 - bottom < maxY && rowIsFlat(src, bottom)) bottom--
        var left = 0
        while (left < maxX && columnIsFlat(src, left, top, bottom)) left++
        var right = w - 1
        while (w - 1 - right < maxX && columnIsFlat(src, right, top, bottom)) right--

        // A completely flat image (or a failed detection) keeps its full area.
        if (bottom - top < h / 3 || right - left < w / 3) {
            top = 0; bottom = h - 1; left = 0; right = w - 1
        }

        val contentW = right - left + 1
        val contentH = bottom - top + 1
        val side = minOf(contentW, contentH)
        val x = left + (contentW - side) / 2
        val y = top + (contentH - side) / 2
        if (x == 0 && y == 0 && side == w && side == h) return src
        return Bitmap.createBitmap(src, x, y, side, side)
    }

    private fun rowIsFlat(b: Bitmap, y: Int): Boolean {
        val reference = b.getPixel(0, y)
        val step = maxOf(1, b.width / SAMPLES)
        var x = 0
        while (x < b.width) {
            if (!close(b.getPixel(x, y), reference)) return false
            x += step
        }
        return close(b.getPixel(b.width - 1, y), reference)
    }

    private fun columnIsFlat(b: Bitmap, x: Int, top: Int, bottom: Int): Boolean {
        val reference = b.getPixel(x, top)
        val step = maxOf(1, (bottom - top) / SAMPLES)
        var y = top
        while (y <= bottom) {
            if (!close(b.getPixel(x, y), reference)) return false
            y += step
        }
        return true
    }

    private fun close(a: Int, b: Int): Boolean =
        abs(Color.red(a) - Color.red(b)) <= TOLERANCE &&
            abs(Color.green(a) - Color.green(b)) <= TOLERANCE &&
            abs(Color.blue(a) - Color.blue(b)) <= TOLERANCE
}

/** Coil transformation applying [ArtworkCrop] to downloaded cover art. */
object SquareArtTransformation : Transformation {
    override val cacheKey: String = "square-art-v1"
    override suspend fun transform(input: Bitmap, size: Size): Bitmap = ArtworkCrop.squareCrop(input)
}

/** Cover art from a URL, border-trimmed and cropped to a centred square. */
@androidx.compose.runtime.Composable
fun ArtImage(
    url: String,
    contentDescription: String?,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val request = androidx.compose.runtime.remember(url) {
        coil.request.ImageRequest.Builder(context)
            .data(url)
            .transformations(SquareArtTransformation)
            .crossfade(true)
            .build()
    }
    coil.compose.AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        alignment = androidx.compose.ui.Alignment.Center,
        modifier = modifier,
    )
}
