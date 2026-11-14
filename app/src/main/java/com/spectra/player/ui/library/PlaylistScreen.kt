package com.spectra.player.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spectra.player.data.Track
import com.spectra.player.player.PlaybackState
import com.spectra.player.ui.chart.Chart
import com.spectra.player.ui.chart.ChartButton
import com.spectra.player.ui.chart.ChartRule
import com.spectra.player.ui.chart.plateMarks
import com.spectra.player.ui.components.CoverArt
import com.spectra.player.ui.components.MiniPlayer

@Composable
fun PlaylistScreen(
    viewModel: PlaylistViewModel,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit,
) {
    val state by viewModel.tracks.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val artwork by viewModel.artwork.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    PlaylistContent(
        title = viewModel.title,
        coverUrl = viewModel.coverUrl,
        owner = viewModel.owner,
        trackCount = viewModel.trackCount,
        state = state,
        playback = playback,
        artwork = artwork,
        snackbar = snackbar,
        onBack = onBack,
        onOpenNowPlaying = onOpenNowPlaying,
        onPlayAll = viewModel::playFromStart,
        onPlay = viewModel::play,
        onQueue = viewModel::queue,
        onLoadMore = viewModel::loadMore,
        onTogglePlay = viewModel::togglePlayPause,
        onNext = viewModel::skipNext,
    )
}

@Composable
fun PlaylistContent(
    title: String,
    coverUrl: String?,
    owner: String,
    trackCount: Int?,
    state: ListState<Track>,
    playback: PlaybackState,
    artwork: ImageBitmap?,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onPlayAll: (shuffle: Boolean) -> Unit,
    onPlay: (Int, Track) -> Unit,
    onQueue: (Track) -> Unit,
    onLoadMore: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                Modifier.statusBarsPadding().padding(start = 4.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Chart.ink)
                }
                Text("PLAYLIST", style = MaterialTheme.typography.labelMedium, color = Chart.dim)
            }
        },
        bottomBar = {
            MiniPlayer(
                playback = playback,
                artwork = artwork,
                onOpen = onOpenNowPlaying,
                onTogglePlay = onTogglePlay,
                onNext = onNext,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            item(key = "header") {
                PlaylistHeader(
                    title = title,
                    coverUrl = coverUrl,
                    owner = owner,
                    trackCount = trackCount,
                    enabled = state.items.isNotEmpty(),
                    onPlay = { onPlayAll(false) },
                    onShuffle = { onPlayAll(true) },
                )
            }
            if (state.items.isEmpty() && state.loading) {
                item(key = "loading") { LoadingFooter() }
            } else if (state.items.isEmpty() && state.error != null) {
                item(key = "error-empty") { ErrorRow(state.error, onRetry = onLoadMore) }
            } else if (state.items.isEmpty() && state.nextOffset == null) {
                item(key = "empty") {
                    Text(
                        "This playlist has no songs yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Chart.dim,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            } else {
                trackItems(state, playback, onLoadMore, onPlay, onQueue)
            }
        }
    }
}

@Composable
private fun PlaylistHeader(
    title: String,
    coverUrl: String?,
    owner: String,
    trackCount: Int?,
    enabled: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        // The cover as a photographic plate, with registration marks at the corners.
        CoverArt(
            url = coverUrl,
            modifier = Modifier
                .padding(start = 28.dp, top = 18.dp)
                .plateMarks(Chart.line)
                .size(176.dp),
            shape = Chart.corner,
        )
        Text(
            title,
            style = MaterialTheme.typography.headlineLarge,
            color = Chart.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp),
        )
        val meta = listOfNotNull(
            owner.takeIf { it.isNotBlank() }?.let { "by $it" },
            trackCount?.let { if (it == 1) "1 song" else "$it songs" },
        )
        if (meta.isNotEmpty()) {
            Text(
                meta.joinToString("  ·  ").uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = Chart.dim,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp),
            )
        }
        Row(
            modifier = Modifier.padding(start = 20.dp, top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ChartButton("Play", onClick = onPlay, icon = Icons.Rounded.PlayArrow, filled = true, enabled = enabled)
            ChartButton("Shuffle", onClick = onShuffle, icon = Icons.Rounded.Shuffle, enabled = enabled)
        }
        ChartRule("Songs", modifier = Modifier.padding(top = 26.dp, bottom = 4.dp))
    }
}
