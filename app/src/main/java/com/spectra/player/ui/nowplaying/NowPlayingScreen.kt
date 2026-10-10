package com.spectra.player.ui.nowplaying

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spectra.player.data.formatTime
import com.spectra.player.player.PlaybackState
import com.spectra.player.visualizer.RadialSpectrumVisualizer
import com.spectra.player.visualizer.VisualizerMode
import com.spectra.player.visualizer.VisualizerStyle
import com.spectra.player.visualizer.bassLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val ART_FRACTION = 0.56f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    viewModel: NowPlayingViewModel,
    onBack: () -> Unit,
) {
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val artwork by viewModel.artwork.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val spectrum = viewModel.spectrum.collectAsStateWithLifecycle() // read only in the draw phase

    val context = LocalContext.current
    var micGranted by remember { mutableStateOf(context.hasRecordAudioPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        micGranted = granted
        viewModel.setLiveAllowed(granted)
    }
    var style by rememberSaveable { mutableStateOf(VisualizerStyle.BARS) }

    // Run the visualizer only while the screen is visible.
    LifecycleStartEffect(Unit) {
        val granted = context.hasRecordAudioPermission()
        micGranted = granted
        viewModel.setLiveAllowed(granted)
        viewModel.startVisualizer()
        onStopOrDispose { viewModel.stopVisualizer() }
    }

    // Album art turns slowly while music plays. The angle is read in graphicsLayer, so no recomposition.
    var artAngle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playback.isPlaying) {
        if (playback.isPlaying) {
            var last = androidx.compose.runtime.withFrameNanos { it }
            while (isActive) {
                val now = androidx.compose.runtime.withFrameNanos { it }
                artAngle = (artAngle + (now - last) / 1_000_000_000f * 10f) % 360f
                last = now
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Now Playing", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close player")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RadialSpectrumVisualizer(
                spectrum = { spectrum.value },
                style = style,
                onStyleChange = { style = it },
                modifier = Modifier.fillMaxWidth(),
                innerRadiusFraction = ART_FRACTION,
            ) {
                AlbumDisc(
                    artwork = artwork,
                    modifier = Modifier
                        .fillMaxSize(ART_FRACTION)
                        .graphicsLayer {
                            rotationZ = artAngle
                            val pulse = 1f + 0.05f * spectrum.value.bassLevel()
                            scaleX = pulse
                            scaleY = pulse
                        },
                )
            }

            ModeLabel(mode)
            Spacer(Modifier.height(16.dp))
            TrackHeader(playback)
            Spacer(Modifier.height(8.dp))
            SeekBar(playback, onSeek = viewModel::seekTo)
            Spacer(Modifier.height(8.dp))
            Controls(
                playback = playback,
                onShuffle = viewModel::toggleShuffle,
                onPrevious = viewModel::skipPrevious,
                onPlayPause = viewModel::togglePlayPause,
                onNext = viewModel::skipNext,
                onRepeat = viewModel::toggleRepeat,
            )

            if (!micGranted) {
                Spacer(Modifier.height(24.dp))
                LivePermissionCard(onEnable = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) })
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AlbumDisc(artwork: androidx.compose.ui.graphics.ImageBitmap?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(colors.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (artwork != null) {
            Image(
                bitmap = artwork,
                contentDescription = "Album art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

@Composable
private fun ModeLabel(mode: VisualizerMode) {
    Row(
        modifier = Modifier.padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            Icons.Rounded.GraphicEq,
            contentDescription = null,
            tint = if (mode == VisualizerMode.LIVE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            "${mode.label} · tap the ring to change style",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TrackHeader(playback: PlaybackState) {
    val track = playback.track
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = track?.name ?: "Nothing playing",
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = track?.artist ?: "Pick a song from your library",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** Isolated so the 4 Hz position ticker only recomposes this composable. */
@Composable
private fun SeekBar(playback: PlaybackState, onSeek: (Long) -> Unit) {
    val duration = playback.track?.durationMs ?: 0L
    var position by remember { mutableLongStateOf(0L) }
    var scrub by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(playback) {
        while (isActive) {
            position = playback.currentPosition()
            delay(250)
        }
    }

    val fraction = (scrub ?: if (duration > 0) position.toFloat() / duration else 0f).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = fraction,
            onValueChange = { scrub = it },
            onValueChangeFinished = {
                scrub?.let { onSeek((it * duration).toLong()) }
                scrub = null
            },
            enabled = duration > 0,
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Text(
                (fraction * duration).toLong().formatTime(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                duration.formatTime(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Controls(
    playback: PlaybackState,
    onShuffle: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onRepeat: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onShuffle) {
            Icon(
                Icons.Rounded.Shuffle,
                contentDescription = "Shuffle",
                tint = if (playback.isShuffling) colors.primary else colors.onSurfaceVariant,
            )
        }
        IconButton(onClick = onPrevious, modifier = Modifier.size(52.dp)) {
            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(32.dp))
        }
        FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(68.dp)) {
            Icon(
                if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (playback.isPlaying) "Pause" else "Play",
                modifier = Modifier.size(38.dp),
            )
        }
        IconButton(onClick = onNext, modifier = Modifier.size(52.dp)) {
            Icon(Icons.Rounded.SkipNext, contentDescription = "Next", modifier = Modifier.size(32.dp))
        }
        IconButton(onClick = onRepeat) {
            Icon(
                if (playback.repeatMode == 1) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                contentDescription = "Repeat",
                tint = if (playback.repeatMode != 0) colors.primary else colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LivePermissionCard(onEnable: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Text("Make it react to the real audio", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "Spotify doesn't share raw audio with apps. With microphone permission, Spectra can read " +
                    "the device's output mix (no recording is made or stored). Without it, the visualizer " +
                    "is simulated from the song's tempo and energy.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onEnable) { Text("Enable live visualizer") }
        }
    }
}

private fun Context.hasRecordAudioPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
