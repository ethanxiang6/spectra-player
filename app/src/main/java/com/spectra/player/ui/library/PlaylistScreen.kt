package com.spectra.player.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import com.spectra.player.ui.chart.Reticle
import com.spectra.player.ui.chart.dottedUnderline
import com.spectra.player.ui.components.TrackRow
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
    /** For screenshots: open with the search already showing this text. */
    initialSearch: String? = null,
    autoFocus: Boolean = true,
) {
    // Search inside this playlist.
    var searching by rememberSaveable { mutableStateOf(initialSearch != null) }
    var query by rememberSaveable { mutableStateOf(initialSearch.orEmpty()) }
    val closeSearch = {
        searching = false
        query = ""
    }
    BackHandler(enabled = searching, onBack = closeSearch)
    val matches = remember(state.items, query) { TrackFilter.filter(state.items, query) }
    val filtering = searching && query.isNotBlank()
    // Searching needs the whole playlist, so keep fetching pages while a search is open.
    LaunchedEffect(searching, state.items.size, state.loading, state.nextOffset, state.error) {
        if (searching && state.nextOffset != null && !state.loading && state.error == null) onLoadMore()
    }
    val listState = rememberLazyListState()
    // Jump to the top when the search opens or the query changes (not when coming back to the page).
    var scrolledFor by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(searching, query) {
        val key = if (searching) query else null
        if (key != null && key != scrolledFor) listState.scrollToItem(0)
        scrolledFor = key
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (searching) {
                PlaylistSearchBar(query = query, onQueryChange = { query = it }, onClose = closeSearch, autoFocus = autoFocus)
            } else {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, top = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Chart.ink)
                    }
                    Text("PLAYLIST", style = MaterialTheme.typography.labelMedium, color = Chart.dim, modifier = Modifier.weight(1f))
                    IconButton(onClick = { searching = true }, enabled = state.items.isNotEmpty()) {
                        Reticle(color = if (state.items.isNotEmpty()) Chart.ink else Chart.dim.copy(alpha = 0.4f), size = 22.dp)
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
            state = listState,
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            if (searching) {
                searchItems(
                    query = query,
                    filtering = filtering,
                    matches = matches,
                    state = state,
                    playback = playback,
                    onPlay = onPlay,
                    onQueue = onQueue,
                    onLoadMore = onLoadMore,
                )
                return@LazyColumn
            }
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

/** Search field that replaces the top bar while searching inside the playlist. */
@Composable
private fun PlaylistSearchBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit, autoFocus: Boolean) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 4.dp, end = 4.dp, top = 4.dp)
            .dottedUnderline(Chart.line)
            .padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Close search", tint = Chart.ink)
        }
        Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
            val textStyle = MaterialTheme.typography.headlineSmall.copy(color = Chart.ink)
            if (query.isEmpty()) {
                Text("Search this playlist", style = textStyle, color = Chart.dim.copy(alpha = 0.6f), maxLines = 1)
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

/**
 * Rows while searching: every song until something is typed, then the matches. Each match keeps
 * its catalogue number and plays in its own place in the playlist.
 */
private fun LazyListScope.searchItems(
    query: String,
    filtering: Boolean,
    matches: List<IndexedValue<Track>>,
    state: ListState<Track>,
    playback: PlaybackState,
    onPlay: (Int, Track) -> Unit,
    onQueue: (Track) -> Unit,
    onLoadMore: () -> Unit,
) {
    val stillLoading = state.nextOffset != null && state.error == null
    item(key = "search-rule") {
        ChartRule(
            if (filtering) "Matches" else "All songs",
            meta = if (filtering) "${matches.size}${if (stillLoading) "+" else ""}" else null,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
    }
    items(matches.size, key = { i -> "m_${matches[i].index}_${matches[i].value.uri}" }) { i ->
        val (index, track) = matches[i]
        TrackRow(
            track = track,
            isCurrent = playback.track?.uri == track.uri,
            isPlaying = playback.isPlaying,
            number = index + 1,
            onClick = { onPlay(index, track) },
            onAddToQueue = { onQueue(track) },
        )
    }
    when {
        state.error != null -> item(key = "search-error") { ErrorRow(state.error, onRetry = onLoadMore) }
        stillLoading -> item(key = "search-loading") {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                LoadingFooter()
                Text(
                    "LOADING THE REST OF THE PLAYLIST",
                    style = MaterialTheme.typography.labelSmall,
                    color = Chart.dim,
                )
            }
        }
        filtering && matches.isEmpty() -> item(key = "search-empty") {
            Text(
                "No song in this playlist matches \u201c${query.trim()}\u201d.",
                style = MaterialTheme.typography.bodyMedium,
                color = Chart.dim,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            )
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
                meta.joinToString(" · ").uppercase(),
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
