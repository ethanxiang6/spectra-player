package com.spectra.player.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.spectra.player.player.PlaybackState
import com.spectra.player.ui.nowplaying.Dusk
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * "Now playing" bar pinned to the bottom of the library: art, song and artist, play/pause and
 * next, with a thin progress line along the top edge. Tapping it opens the visualizer.
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
    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0xFF1A1740), Color(0xFF2A2152))))
            .clickable(onClickLabel = "Open visualizer", onClick = onOpen),
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Dusk.SkyMid),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    artwork != null -> Image(
                        bitmap = artwork,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    track.imageUrl != null -> AsyncImage(
                        model = track.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Dusk.TextDim)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = track.name,
                    style = TextStyle(color = Dusk.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artist,
                    style = TextStyle(color = Dusk.TextDim, fontSize = 13.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onTogglePlay) {
                Icon(
                    imageVector = if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (playback.isPlaying) "Pause" else "Play",
                    tint = Dusk.Text,
                    modifier = Modifier.size(30.dp),
                )
            }
            IconButton(onClick = onNext) {
                Icon(
                    Icons.Rounded.SkipNext,
                    contentDescription = "Next song",
                    tint = Dusk.Text,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        ProgressLine(playback, Modifier.align(Alignment.BottomCenter))
    }
}

/** 2 dp progress line; only this composable updates as the song plays. */
@Composable
private fun ProgressLine(playback: PlaybackState, modifier: Modifier = Modifier) {
    val duration = playback.track?.durationMs ?: 0L
    var fraction by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playback) {
        while (isActive) {
            fraction = if (duration > 0) (playback.currentPosition().toFloat() / duration).coerceIn(0f, 1f) else 0f
            delay(500)
        }
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(2.dp)
            .padding(horizontal = 14.dp)
            .drawBehind {
                drawLine(Color.White.copy(alpha = 0.12f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), size.height)
                drawLine(
                    brush = Brush.horizontalGradient(listOf(Dusk.Lavender, Dusk.Rose)),
                    start = Offset(0f, size.height / 2),
                    end = Offset(size.width * fraction, size.height / 2),
                    strokeWidth = size.height,
                )
            },
    )
}
