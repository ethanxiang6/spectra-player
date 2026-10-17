package com.spectra.player.ui.library

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.size

import androidx.compose.ui.Alignment

import androidx.compose.material.icons.rounded.Shuffle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spectra.player.ui.components.MiniPlayer

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            MiniPlayer(
                playback = playback,
                artwork = artwork,
                onOpen = onOpenNowPlaying,
                onTogglePlay = viewModel::togglePlayPause,
                onNext = viewModel::skipNext,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            item(key = "header") {
                PlaylistHeader(
                    title = viewModel.title,
                    coverUrl = viewModel.coverUrl,
                    owner = viewModel.owner,
                    trackCount = viewModel.trackCount,
                    onPlay = { viewModel.playFromStart(shuffle = false) },
                    onShuffle = { viewModel.playFromStart(shuffle = true) },
                )
            }
            if (state.items.isEmpty() && state.loading) {
                item(key = "loading") { LoadingFooter() }
            } else if (state.items.isEmpty() && state.error != null) {
                item(key = "error-empty") { ErrorRow(state.error!!, onRetry = viewModel::loadMore) }
            } else if (state.items.isEmpty() && state.nextOffset == null) {
                item(key = "empty") {
                    Text(
                        "This playlist has no songs yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                trackItems(state, playback, viewModel::loadMore, viewModel::play, viewModel::queue)
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
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, bottom = 12.dp)) {
        com.spectra.player.ui.components.CoverArt(
            url = coverUrl,
            modifier = Modifier.size(208.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(20.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (owner.isNotBlank()) {
            Text(
                "By $owner",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when (trackCount) {
                    null -> ""
                    1 -> "1 song"
                    else -> "$trackCount songs"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onShuffle) {
                Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle playlist")
            }
            androidx.compose.foundation.layout.Spacer(Modifier.size(4.dp))
            androidx.compose.material3.FilledIconButton(onClick = onPlay, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play playlist", modifier = Modifier.size(32.dp))
            }
        }
    }
}
