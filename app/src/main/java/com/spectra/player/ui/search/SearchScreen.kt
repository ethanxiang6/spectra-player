package com.spectra.player.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spectra.player.data.Track
import com.spectra.player.player.PlaybackState
import com.spectra.player.ui.chart.Chart
import com.spectra.player.ui.chart.ChartRule
import com.spectra.player.ui.chart.Reticle
import com.spectra.player.ui.chart.dottedUnderline
import com.spectra.player.ui.components.MiniPlayer
import com.spectra.player.ui.components.TrackRow
import com.spectra.player.ui.library.ErrorRow
import com.spectra.player.ui.library.ListState
import com.spectra.player.ui.library.LoadingFooter

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit,
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val artwork by viewModel.artwork.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    SearchContent(
        query = query,
        results = results,
        playback = playback,
        artwork = artwork,
        snackbar = snackbar,
        onQueryChange = viewModel::onQueryChange,
        onBack = onBack,
        onOpenNowPlaying = onOpenNowPlaying,
        onPlay = viewModel::play,
        onQueue = viewModel::queue,
        onLoadMore = viewModel::loadMore,
        onTogglePlay = viewModel::togglePlayPause,
        onNext = viewModel::skipNext,
    )
}

@Composable
fun SearchContent(
    query: String,
    results: ListState<Track>,
    playback: PlaybackState,
    artwork: ImageBitmap?,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onPlay: (Track) -> Unit,
    onQueue: (Track) -> Unit,
    onLoadMore: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    autoFocus: Boolean = true,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(Modifier.statusBarsPadding().padding(bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Chart.ink)
                    }
                    Text("SEARCH", style = MaterialTheme.typography.labelMedium, color = Chart.dim)
                }
                // The query is typed in the chart's italic serif, over a dotted rule.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 6.dp)
                        .dottedUnderline(Chart.line)
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Reticle(color = if (query.isNotEmpty()) Chart.accent else Chart.dim, size = 22.dp)
                    Spacer(Modifier.width(14.dp))
                    Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        val textStyle = MaterialTheme.typography.headlineMedium.copy(color = Chart.ink)
                        if (query.isEmpty()) {
                            Text("Songs, artists, albums", style = textStyle, color = Chart.dim.copy(alpha = 0.6f), maxLines = 1)
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle = textStyle,
                            cursorBrush = SolidColor(Chart.accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = Chart.dim)
                        }
                    }
                }
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
            when {
                query.isBlank() -> item(key = "hint") {
                    Hint("Look for anything on Spotify. A song you play from here carries on with the rest of its album.")
                }
                results.items.isEmpty() && results.loading -> item(key = "loading") { LoadingFooter() }
                results.items.isEmpty() && results.error != null ->
                    item(key = "error") { ErrorRow(results.error, onRetry = { onQueryChange(query) }) }
                results.items.isEmpty() -> item(key = "empty") {
                    Hint("Nothing matches “$query”. Try fewer words or check the spelling.")
                }
                else -> resultRows(results, playback, onPlay = { track ->
                    keyboard?.hide()
                    onPlay(track)
                }, onQueue = onQueue, onLoadMore = onLoadMore)
            }
        }
    }
}

private fun LazyListScope.resultRows(
    results: ListState<Track>,
    playback: PlaybackState,
    onPlay: (Track) -> Unit,
    onQueue: (Track) -> Unit,
    onLoadMore: () -> Unit,
) {
    item(key = "rule") { ChartRule("Results", modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)) }
    items(results.items.size, key = { i -> "${i}_${results.items[i].uri}" }) { i ->
        val track = results.items[i]
        TrackRow(
            track = track,
            isCurrent = playback.track?.uri == track.uri,
            isPlaying = playback.isPlaying,
            number = i + 1,
            onClick = { onPlay(track) },
            onAddToQueue = { onQueue(track) },
        )
    }
    when {
        results.error != null -> item(key = "more-error") { ErrorRow(results.error, onRetry = onLoadMore) }
        results.nextOffset != null -> item(key = "footer") {
            LaunchedEffect(results.items.size) { onLoadMore() }
            LoadingFooter()
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = Chart.dim,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
    )
}
