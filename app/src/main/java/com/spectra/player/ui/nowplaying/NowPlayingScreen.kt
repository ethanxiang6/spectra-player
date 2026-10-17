package com.spectra.player.ui.nowplaying

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.spectra.player.data.formatTime
import com.spectra.player.player.NowPlayingTrack
import com.spectra.player.player.PlaybackState
import com.spectra.player.visualizer.RadialSpectrumVisualizer
import com.spectra.player.visualizer.VisualizerMode
import com.spectra.player.visualizer.VisualizerStyle
import com.spectra.player.visualizer.bassLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.sin

private const val ART_FRACTION = 0.5f
private const val CONTROLS_HIDE_MS = 4_500L

/**
 * Full-screen visualizer: an evening sky, the radial spectrum around the album art, and the song
 * title floating underneath. No controls are shown until the screen is tapped; they fade out
 * again after a few seconds or on the next tap.
 */
@Composable
fun NowPlayingScreen(
    viewModel: NowPlayingViewModel,
    onBack: () -> Unit,
) {
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val artwork by viewModel.artwork.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val spectrum = viewModel.spectrum.collectAsStateWithLifecycle() // read only while drawing
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var micGranted by remember { mutableStateOf(context.hasRecordAudioPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        micGranted = granted
        viewModel.setLiveAllowed(granted)
        viewModel.updateSettings { it.copy(askedForMic = true) }
    }

    var controlsVisible by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var lastInteraction by remember { mutableLongStateOf(0L) }
    val touched = { lastInteraction = SystemClock.uptimeMillis() }

    LifecycleStartEffect(Unit) {
        val granted = context.hasRecordAudioPermission()
        micGranted = granted
        viewModel.setLiveAllowed(granted)
        viewModel.startVisualizer()
        onStopOrDispose { viewModel.stopVisualizer() }
    }

    ImmersiveMode()

    // One clock for the sky, the floating title and the slowly turning album art.
    var seconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (isActive) {
            seconds = (withFrameNanos { it } - start) / 1_000_000_000f
        }
    }
    var artAngle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playback.isPlaying, settings.spinArt) {
        if (!settings.spinArt) artAngle = 0f
        if (playback.isPlaying && settings.spinArt) {
            var last = withFrameNanos { it }
            while (isActive) {
                val now = withFrameNanos { it }
                artAngle = (artAngle + (now - last) / 1_000_000_000f * 8f) % 360f
                last = now
            }
        }
    }

    LaunchedEffect(controlsVisible, lastInteraction, settingsOpen) {
        if (controlsVisible && !settingsOpen) {
            delay(CONTROLS_HIDE_MS)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Dusk.SkyTop)
            .pointerInput(Unit) {
                detectTapGestures {
                    controlsVisible = !controlsVisible
                    touched()
                }
            },
    ) {
        SkyBackdrop(
            backdrop = settings.backdrop,
            showStars = settings.stars,
            artwork = artwork,
            imageUrl = playback.track?.imageUrl,
            time = { seconds },
            energy = { spectrum.value.bassLevel() },
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.8f))
            RadialSpectrumVisualizer(
                spectrum = { spectrum.value },
                style = settings.style,
                onStyleChange = { next -> viewModel.updateSettings { it.copy(style = next) } },
                modifier = Modifier.fillMaxWidth(0.94f),
                innerRadiusFraction = ART_FRACTION,
                colors = settings.backdrop.look().ring,
                interactive = false,
                glow = true,
            ) {
                AlbumDisc(
                    artwork = artwork,
                    imageUrl = playback.track?.imageUrl,
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
            Spacer(Modifier.height(40.dp))
            FloatingTitle(
                track = playback.track,
                time = { seconds },
                fontFamily = TitleFonts.byId(settings.fontId).family,
            )
            Spacer(Modifier.weight(1.2f))
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(350)),
        ) {
            ControlsOverlay(
                playback = playback,
                style = settings.style,
                mode = mode,
                micGranted = micGranted,
                onClose = onBack,
                onStyle = { viewModel.updateSettings { it.copy(style = it.style.next()) }; touched() },
                onSettings = { settingsOpen = true; touched() },
                onEnableLive = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO); touched() },
                onShuffle = { viewModel.toggleShuffle(); touched() },
                onPrevious = { viewModel.skipPrevious(); touched() },
                onPlayPause = { viewModel.togglePlayPause(); touched() },
                onNext = { viewModel.skipNext(); touched() },
                onRepeat = { viewModel.toggleRepeat(); touched() },
                onSeek = { viewModel.seekTo(it); touched() },
                onScrub = touched,
            )
        }

        // One-time question: should the ring react to the real audio?
        if (!micGranted && !settings.askedForMic && !controlsVisible) {
            LiveAudioPrompt(
                onAllow = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                onLater = { viewModel.updateSettings { it.copy(askedForMic = true) } },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(20.dp),
            )
        }
    }

    if (settingsOpen) {
        VisualizerSettingsSheet(
            settings = settings,
            sampleTitle = playback.track?.name ?: "Spectra",
            liveGranted = micGranted,
            mode = mode,
            onChange = viewModel::updateSettings,
            onEnableLive = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            onDismiss = { settingsOpen = false; touched() },
        )
    }
}

@Composable
private fun LiveAudioPrompt(onAllow: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xE6161330))
            .pointerInput(Unit) { detectTapGestures { } } // don't toggle the controls underneath
            .padding(18.dp),
    ) {
        Text(
            "Make the visualizer match the music?",
            style = TextStyle(color = Dusk.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Spotify doesn't share its audio with other apps. With microphone permission Spectra reads the " +
                "phone's audio output, so the ring follows the real beat and melody. Nothing is recorded or stored.",
            style = TextStyle(color = Dusk.TextDim, fontSize = 13.sp, lineHeight = 18.sp),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onLater) { Text("Not now", color = Dusk.TextDim) }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onAllow) { Text("Allow", color = Dusk.Lavender, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun AlbumDisc(artwork: ImageBitmap?, imageUrl: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Dusk.SkyMid)
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        when {
            artwork != null -> Image(
                bitmap = artwork,
                contentDescription = "Album art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            imageUrl != null -> AsyncImage(
                model = imageUrl,
                contentDescription = "Album art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            else -> Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Dusk.TextDim,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

private val TitleShadow = Shadow(color = Color(0xAA000000), offset = Offset(0f, 2f), blurRadius = 18f)

/** Song name and artist, no background, drifting gently up and down. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FloatingTitle(track: NowPlayingTrack?, time: () -> Float, fontFamily: FontFamily?) {
    AnimatedContent(
        targetState = track,
        contentKey = { it?.uri },
        transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(300)) },
        label = "title",
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = sin(time() * 0.7f) * 4.dp.toPx() },
    ) { current ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        ) {
            Text(
                text = current?.name ?: "Nothing playing",
                style = TextStyle(
                    color = Dusk.Text,
                    fontSize = 26.sp,
                    lineHeight = 32.sp,
                    fontFamily = fontFamily,
                    fontWeight = if (fontFamily == null) FontWeight.SemiBold else FontWeight.Normal,
                    letterSpacing = 0.2.sp,
                    shadow = TitleShadow,
                ),
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2_000),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = current?.artist ?: "Pick a song from your library",
                style = TextStyle(
                    color = Dusk.TextDim,
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    fontFamily = fontFamily,
                    shadow = TitleShadow,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ControlsOverlay(
    playback: PlaybackState,
    style: VisualizerStyle,
    mode: VisualizerMode,
    micGranted: Boolean,
    onClose: () -> Unit,
    onStyle: () -> Unit,
    onSettings: () -> Unit,
    onEnableLive: () -> Unit,
    onShuffle: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onRepeat: () -> Unit,
    onSeek: (Long) -> Unit,
    onScrub: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        // Top: close + visualizer style.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Close visualizer",
                    tint = Dusk.Text,
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                mode.label,
                style = TextStyle(color = Dusk.TextDim, fontSize = 12.sp, letterSpacing = 0.4.sp),
            )
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onStyle) {
                Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = Dusk.Lavender, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(style.label(), color = Dusk.Text)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = "Visualizer settings", tint = Dusk.Text)
            }
        }

        // Bottom: seek bar + transport.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(start = 24.dp, end = 24.dp, top = 56.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SeekBar(playback, onSeek = onSeek, onScrub = onScrub)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onShuffle) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (playback.isShuffling) Dusk.Lavender else Dusk.TextDim.copy(alpha = 0.7f),
                    )
                }
                IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = Dusk.Text, modifier = Modifier.size(34.dp))
                }
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Dusk.Text.copy(alpha = 0.92f)),
                ) {
                    Icon(
                        if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playback.isPlaying) "Pause" else "Play",
                        tint = Dusk.SkyMid,
                        modifier = Modifier.size(40.dp),
                    )
                }
                IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = Dusk.Text, modifier = Modifier.size(34.dp))
                }
                IconButton(onClick = onRepeat) {
                    Icon(
                        if (playback.repeatMode == 1) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = "Repeat",
                        tint = if (playback.repeatMode != 0) Dusk.Lavender else Dusk.TextDim.copy(alpha = 0.7f),
                    )
                }
            }
            if (!micGranted) {
                TextButton(onClick = onEnableLive) {
                    Icon(Icons.Rounded.Mic, contentDescription = null, tint = Dusk.TextDim, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "React to the real audio",
                        style = TextStyle(color = Dusk.TextDim, fontSize = 13.sp),
                    )
                }
            }
        }
    }
}

/** Isolated so the 4 Hz position ticker only recomposes this composable. */
@Composable
private fun SeekBar(playback: PlaybackState, onSeek: (Long) -> Unit, onScrub: () -> Unit) {
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
            onValueChange = {
                scrub = it
                onScrub()
            },
            onValueChangeFinished = {
                scrub?.let { onSeek((it * duration).toLong()) }
                scrub = null
            },
            enabled = duration > 0,
            colors = SliderDefaults.colors(
                thumbColor = Dusk.Text,
                activeTrackColor = Dusk.Lavender,
                inactiveTrackColor = Color.White.copy(alpha = 0.18f),
            ),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Text(
                (fraction * duration).toLong().formatTime(),
                style = TextStyle(color = Dusk.TextDim, fontSize = 12.sp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                duration.formatTime(),
                style = TextStyle(color = Dusk.TextDim, fontSize = 12.sp),
            )
        }
    }
}

/** Hides the status and navigation bars and keeps the screen on while the visualizer is open. */
@Composable
private fun ImmersiveMode() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = false
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Context.hasRecordAudioPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
