package com.spectra.player.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spectra.player.player.PlaybackState
import com.spectra.player.ui.chart.Chart
import com.spectra.player.ui.chart.drawChartStar
import com.spectra.player.ui.image.ArtImage
import com.spectra.player.ui.nowplaying.Dusk
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * "Now playing" bar pinned to the bottom: the cover as a small planet with an orbit ring, where
 * a little moon travels round as the song plays; title, artist, play/pause and next. A hairline
 * scale along the bottom edge shows the same progress. Tapping it opens the visualizer.
 */
@Composable
fun MiniPlayer(
    playback: PlaybackState,
    artwork: ImageBitmap?,
    onOpen: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = playback.track ?: return
    val fraction = rememberProgress(playback)
    val accent = Chart.accent
    val line = Chart.line

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.94f), Chart.corner)
            .border(1.dp, line, Chart.corner)
            .clickable(onClickLabel = "Open visualizer", onClick = onOpen),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 2.dp, top = 10.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Planet and orbit.
            Box(
                Modifier
                    .size(52.dp)
                    .drawBehind {
                        val c = Offset(size.width / 2, size.height / 2)
                        val r = size.minDimension / 2 - 1.dp.toPx()
                        drawCircle(line, r, c, style = Stroke(1.dp.toPx()))
                        val a = (fraction.value * 2 * PI - PI / 2).toFloat()
                        drawChartStar(Offset(c.x + cos(a) * r, c.y + sin(a) * r), 2.dp.toPx(), accent, accent)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        artwork != null -> Image(
                            bitmap = artwork,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            colorFilter = Dusk.artFilter,
                            modifier = Modifier.fillMaxSize(),
                        )
                        track.imageUrl != null -> ArtImage(
                            url = track.imageUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                        )
                        else -> Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Chart.dim, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (playback.isPlaying) "NOW PLAYING" else "PAUSED",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (playback.isPlaying) accent else Chart.dim,
                )
                Text(
                    text = track.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Chart.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = Chart.dim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onTogglePlay) {
                Icon(
                    imageVector = if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (playback.isPlaying) "Pause" else "Play",
                    tint = Chart.ink,
                    modifier = Modifier.size(28.dp),
                )
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Next song", tint = Chart.ink, modifier = Modifier.size(26.dp))
            }
        }
        ProgressScale(fraction, Modifier.align(Alignment.BottomCenter))
    }
}

/** Song progress 0..1, refreshed twice a second; only the drawing that reads it updates. */
@Composable
private fun rememberProgress(playback: PlaybackState): androidx.compose.runtime.State<Float> {
    val duration = playback.track?.durationMs ?: 0L
    val state = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playback) {
        while (isActive) {
            state.floatValue = if (duration > 0) (playback.currentPosition().toFloat() / duration).coerceIn(0f, 1f) else 0f
            delay(500)
        }
    }
    return state
}

/** Hairline with small ticks, like the scale on an instrument; the played part in the accent. */
@Composable
private fun ProgressScale(fraction: androidx.compose.runtime.State<Float>, modifier: Modifier = Modifier) {
    val accent = Chart.accent
    val line = Chart.line
    Box(
        modifier
            .fillMaxWidth()
            .height(5.dp)
            .padding(horizontal = 12.dp)
            .drawBehind {
                val y = size.height - 1.dp.toPx()
                val w = 1.dp.toPx()
                val ticks = 24
                for (i in 0..ticks) {
                    val x = size.width * i / ticks
                    val h = if (i % 6 == 0) 4.dp.toPx() else 2.dp.toPx()
                    drawLine(line, Offset(x, y), Offset(x, y - h), w)
                }
                drawLine(line, Offset(0f, y), Offset(size.width, y), w)
                drawLine(accent, Offset(0f, y), Offset(size.width * fraction.value, y), 1.5.dp.toPx())
            },
    )
}
