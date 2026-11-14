package com.spectra.player.ui.library

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spectra.player.data.Playlist
import com.spectra.player.data.Track
import com.spectra.player.data.UserProfile
import com.spectra.player.player.PlaybackState
import com.spectra.player.player.SpotifyRemoteManager
import com.spectra.player.ui.chart.Chart
import com.spectra.player.ui.chart.ChartButton
import com.spectra.player.ui.chart.ChartNote
import com.spectra.player.ui.chart.ChartRule
import com.spectra.player.ui.chart.ChartStar
import com.spectra.player.ui.chart.ConstellationMap
import com.spectra.player.ui.chart.Reticle
import com.spectra.player.ui.chart.dottedUnderline
import com.spectra.player.ui.components.CoverArt
import com.spectra.player.ui.components.MiniPlayer
import com.spectra.player.ui.components.TrackRow
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenPlaylist: (Playlist) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val online by viewModel.isOnline.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val artwork by viewModel.artwork.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    val context = LocalContext.current

    LibraryContent(
        songs = songs,
        playlists = playlists,
        profile = profile,
        online = online,
        connection = connection,
        playback = playback,
        artwork = artwork,
        snackbar = snackbar,
        actions = LibraryActions(
            openPlaylist = onOpenPlaylist,
            openNowPlaying = onOpenNowPlaying,
            openSearch = onOpenSearch,
            openSettings = onOpenSettings,
            signOut = viewModel::signOut,
            refresh = viewModel::refresh,
            retryConnect = viewModel::connectRemote,
            approveInSpotify = { context.findActivity()?.let(viewModel::connectViaSpotify) },
            loadMoreSongs = viewModel::loadMoreSongs,
            loadMorePlaylists = viewModel::loadMorePlaylists,
            play = viewModel::play,
            queue = viewModel::queue,
            playLiked = viewModel::playLiked,
            togglePlay = viewModel::togglePlayPause,
            next = viewModel::skipNext,
        ),
    )
}

/** Everything the library page can ask for, so the page itself can be drawn from plain data. */
class LibraryActions(
    val openPlaylist: (Playlist) -> Unit = {},
    val openNowPlaying: () -> Unit = {},
    val openSearch: () -> Unit = {},
    val openSettings: () -> Unit = {},
    val signOut: () -> Unit = {},
    val refresh: () -> Unit = {},
    val retryConnect: () -> Unit = {},
    val approveInSpotify: () -> Unit = {},
    val loadMoreSongs: () -> Unit = {},
    val loadMorePlaylists: () -> Unit = {},
    val play: (Int, Track) -> Unit = { _, _ -> },
    val queue: (Track) -> Unit = {},
    val playLiked: (Boolean) -> Unit = {},
    val togglePlay: () -> Unit = {},
    val next: () -> Unit = {},
)

/** Playlists shown on the map before "Show all". */
private const val MAP_PREVIEW = 8

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryContent(
    songs: ListState<Track>,
    playlists: ListState<Playlist>,
    profile: UserProfile?,
    online: Boolean,
    connection: SpotifyRemoteManager.Connection,
    playback: PlaybackState,
    artwork: ImageBitmap?,
    actions: LibraryActions,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showAll by rememberSaveable { mutableStateOf(false) }
    val showOffline = !online || songs.fromCache

    // Keep fetching playlist pages so the map and its count are complete.
    LaunchedEffect(playlists.items.size, playlists.nextOffset, playlists.loading) {
        if (playlists.items.isNotEmpty() && playlists.nextOffset != null && !playlists.loading && playlists.error == null) {
            actions.loadMorePlaylists()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            MiniPlayer(
                playback = playback,
                artwork = artwork,
                onOpen = actions.openNowPlaying,
                onTogglePlay = actions.togglePlay,
                onNext = actions.next,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = songs.loading && songs.items.isEmpty(),
            onRefresh = actions.refresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "header") {
                    ChartHeader(
                        profile = profile,
                        playlistCount = playlists.total ?: playlists.items.size.takeIf { playlists.items.isNotEmpty() },
                        songCount = songs.total ?: songs.items.size.takeIf { songs.items.isNotEmpty() },
                        onSearch = actions.openSearch,
                        onSettings = actions.openSettings,
                        onSignOut = actions.signOut,
                    )
                }
                item(key = "banner") {
                    ConnectionBanner(connection, onRetry = actions.retryConnect, onApprove = actions.approveInSpotify)
                }
                if (showOffline) {
                    item(key = "offline") {
                        ChartNote("Offline", "Showing the songs saved on this phone.")
                    }
                }

                item(key = "playlists-heading") {
                    ChartRule(
                        "Playlists",
                        meta = (playlists.total ?: playlists.items.size).takeIf { playlists.items.isNotEmpty() }?.toString(),
                        modifier = Modifier.padding(top = 26.dp),
                    )
                }
                item(key = "playlists") {
                    PlaylistMap(
                        state = playlists,
                        likedCount = songs.total ?: songs.items.size,
                        showAll = showAll,
                        onToggleShowAll = { showAll = !showAll },
                        onOpen = actions.openPlaylist,
                        onLiked = {
                            val index = 4 + (if (showOffline) 1 else 0)
                            scope.launch { listState.animateScrollToItem(index) }
                        },
                        onRetry = actions.loadMorePlaylists,
                    )
                }

                item(key = "liked-heading") {
                    Column(Modifier.padding(top = 26.dp, bottom = 6.dp)) {
                        ChartRule("Liked songs", meta = songs.total?.toString())
                        Row(
                            modifier = Modifier.padding(start = 20.dp, top = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ChartButton(
                                "Play",
                                onClick = { actions.playLiked(false) },
                                icon = Icons.Rounded.PlayArrow,
                                filled = true,
                                enabled = songs.items.isNotEmpty(),
                            )
                            ChartButton(
                                "Shuffle",
                                onClick = { actions.playLiked(true) },
                                icon = Icons.Rounded.Shuffle,
                                enabled = songs.items.isNotEmpty(),
                            )
                        }
                    }
                }
                when {
                    songs.items.isEmpty() && songs.loading -> item(key = "songs-loading") { LoadingFooter() }
                    songs.items.isEmpty() && songs.error != null ->
                        item(key = "songs-error") { ErrorRow(songs.error, onRetry = actions.loadMoreSongs) }
                    songs.items.isEmpty() -> item(key = "songs-empty") {
                        Quiet("Songs you like on Spotify show up here.")
                    }
                    else -> trackItems(songs, playback, actions.loadMoreSongs, actions.play, actions.queue)
                }
            }
        }
    }
}

// ---- Header ---------------------------------------------------------------------------------

@Composable
private fun ChartHeader(
    profile: UserProfile?,
    playlistCount: Int?,
    songCount: Int?,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onSignOut: () -> Unit,
) {
    // No status-bar padding here: the Scaffold already keeps the page below the status bar.
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "SPECTRA",
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 5.sp),
                color = Chart.ink,
                modifier = Modifier.weight(1f),
            )
            ProfileMenu(profile, onSettings = onSettings, onSignOut = onSignOut)
        }
        val first = profile?.displayName?.substringBefore(' ')?.takeIf { it.isNotBlank() }
        Text(
            if (first != null) "$first's sky" else "Your sky",
            style = MaterialTheme.typography.displaySmall,
            color = Chart.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
        )
        val counts = listOfNotNull(
            playlistCount?.let { if (it == 1) "1 playlist" else "$it playlists" },
            songCount?.let { if (it == 1) "1 liked song" else "$it liked songs" },
        )
        if (counts.isNotEmpty()) {
            Text(
                counts.joinToString(" · ").uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = Chart.dim,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp),
            )
        }
        Row(
            modifier = Modifier
                .padding(start = 20.dp, end = 20.dp, top = 16.dp)
                .fillMaxWidth()
                .clickable(onClickLabel = "Search", onClick = onSearch)
                .dottedUnderline(Chart.line)
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Reticle()
            Spacer(Modifier.width(12.dp))
            Text(
                "Search songs, artists, albums",
                style = MaterialTheme.typography.bodyLarge,
                color = Chart.dim,
            )
        }
    }
}

@Composable
private fun ProfileMenu(profile: UserProfile?, onSettings: () -> Unit, onSignOut: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        // Avatar inside a thin orbit ring.
        Box(
            Modifier
                .size(48.dp)
                .clickable(onClickLabel = "Account and settings") { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(40.dp).border(1.dp, Chart.line, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                CoverArt(url = profile?.imageUrl, modifier = Modifier.size(32.dp), shape = CircleShape)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            profile?.let {
                Text(
                    it.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            DropdownMenuItem(
                text = { Text("App settings") },
                leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                onClick = {
                    open = false
                    onSettings()
                },
            )
            DropdownMenuItem(
                text = { Text("Sign out") },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null) },
                onClick = {
                    open = false
                    onSignOut()
                },
            )
        }
    }
}

// ---- Playlist map ---------------------------------------------------------------------------

@Composable
private fun PlaylistMap(
    state: ListState<Playlist>,
    likedCount: Int,
    showAll: Boolean,
    onToggleShowAll: () -> Unit,
    onOpen: (Playlist) -> Unit,
    onLiked: () -> Unit,
    onRetry: () -> Unit,
) {
    when {
        state.items.isEmpty() && state.loading -> LoadingFooter()
        state.items.isEmpty() && state.error != null -> ErrorRow(state.error, onRetry = onRetry)
        else -> Column {
            val shown = if (showAll) state.items else state.items.take(MAP_PREVIEW)
            val stars = remember(shown, likedCount) {
                buildList {
                    add(ChartStar("liked", "Liked songs", songsLabel(likedCount), likedCount, pole = true))
                    shown.forEach { add(ChartStar(it.id, it.name, it.trackCount?.let(::songsLabel), it.trackCount ?: 0)) }
                }
            }
            ConstellationMap(
                stars = stars,
                onTap = { i -> if (i == 0) onLiked() else onOpen(shown[i - 1]) },
                modifier = Modifier.padding(top = 4.dp),
            )
            if (state.items.isEmpty()) {
                Quiet("Playlists you make or follow appear here as their own stars.")
            }
            val total = state.total ?: state.items.size
            if (state.items.size > MAP_PREVIEW) {
                TextButton(onClick = onToggleShowAll, modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        if (showAll) "SHOW FEWER" else "SHOW ALL $total",
                        style = MaterialTheme.typography.labelLarge,
                        color = Chart.accent,
                    )
                }
            }
        }
    }
}

private fun songsLabel(n: Int) = if (n == 1) "1 song" else "$n songs"

@Composable
private fun Quiet(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = Chart.dim,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

// ---- Shared list plumbing (also used by the playlist and search pages) ----------------------

/** Catalogue rows plus an infinite-scroll footer / error row. */
internal fun LazyListScope.trackItems(
    state: ListState<Track>,
    playback: PlaybackState,
    onLoadMore: () -> Unit,
    onPlay: (Int, Track) -> Unit,
    onQueue: (Track) -> Unit,
) {
    itemsIndexed(state.items, key = { index, t -> "${index}_${t.uri}" }) { index, track ->
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
        state.error != null -> item(key = "error") { ErrorRow(state.error, onRetry = onLoadMore) }
        state.nextOffset != null -> item(key = "footer") {
            // Entering composition (or the list growing while still visible) requests the next page.
            LaunchedEffect(state.items.size) { onLoadMore() }
            LoadingFooter()
        }
    }
}

// ---- Connection ------------------------------------------------------------------------------

@Composable
private fun ConnectionBanner(
    connection: SpotifyRemoteManager.Connection,
    onRetry: () -> Unit,
    onApprove: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    when (connection) {
        is SpotifyRemoteManager.Connection.Failed -> {
            var showDetails by remember { mutableStateOf(false) }
            ChartNote(
                heading = "No signal from Spotify",
                body = connection.message,
                tone = colors.error,
                modifier = Modifier.padding(top = 14.dp),
                actions = {
                    if (!connection.spotifyMissing) {
                        TextButton(onClick = onApprove) { Text("APPROVE IN SPOTIFY") }
                    }
                    TextButton(onClick = onRetry) { Text("RETRY") }
                    if (connection.details != null) {
                        TextButton(onClick = { showDetails = !showDetails }) {
                            Text(if (showDetails) "HIDE" else "DETAILS")
                        }
                    }
                },
                extra = {
                    if (showDetails) {
                        connection.details?.let { details ->
                            androidx.compose.foundation.text.selection.SelectionContainer {
                                Text(
                                    details,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Chart.dim,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                            }
                        }
                    }
                },
            )
        }
        SpotifyRemoteManager.Connection.NeedsApproval -> ChartNote(
            heading = "Approval needed",
            body = "To play music, Spotify needs to approve Spectra once. Tap the button, allow Spectra in Spotify, then come back.",
            tone = colors.primary,
            modifier = Modifier.padding(top = 14.dp),
            actions = { TextButton(onClick = onApprove) { Text("APPROVE IN SPOTIFY") } },
        )
        SpotifyRemoteManager.Connection.Connecting -> Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.dp, color = Chart.dim)
            Spacer(Modifier.width(10.dp))
            Text(
                "CONNECTING TO SPOTIFY",
                style = MaterialTheme.typography.labelMedium,
                color = Chart.dim,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onApprove) { Text("APPROVE") }
        }
        else -> Unit
    }
}

internal tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

// ---- Small states --------------------------------------------------------------------------

@Composable
internal fun LoadingFooter() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 1.5.dp, color = Chart.dim)
    }
}

@Composable
internal fun ErrorRow(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = Chart.dim,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onRetry) { Text("TRY AGAIN") }
    }
}

@Composable
internal fun ErrorState(message: String, onRetry: () -> Unit) = CenteredBox { ErrorRow(message, onRetry) }

@Composable
internal fun EmptyState(title: String, body: String) = CenteredBox {
    Column(
        Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = Chart.dim,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun CenteredBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
