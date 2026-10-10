package com.spectra.player.ui.library

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spectra.player.data.Playlist
import com.spectra.player.data.PremiumStatus
import com.spectra.player.data.Track
import com.spectra.player.data.UserProfile
import com.spectra.player.player.PlaybackState
import com.spectra.player.player.SpotifyRemoteManager
import com.spectra.player.ui.components.CoverArt
import com.spectra.player.ui.components.MiniPlayer
import com.spectra.player.ui.components.TrackRow

private val Tabs = listOf("Songs", "Playlists", "Offline")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenPlaylist: (Playlist) -> Unit,
    onOpenNowPlaying: () -> Unit,
) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val cached by viewModel.cachedTracks.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val premium by viewModel.premium.collectAsStateWithLifecycle()
    val online by viewModel.isOnline.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val artwork by viewModel.artwork.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Library", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    PremiumChip(premium)
                    ProfileMenu(profile, onSignOut = viewModel::signOut)
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
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ConnectionBanner(connection, onRetry = viewModel::connectRemote)

            PrimaryTabRow(
                selectedTabIndex = tab,
                containerColor = Color.Transparent,
            ) {
                Tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = { Text(label, style = MaterialTheme.typography.labelLarge) },
                    )
                }
            }

            PullToRefreshBox(
                isRefreshing = songs.loading && songs.items.isEmpty(),
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (tab) {
                    0 -> SongsTab(songs, playback, viewModel::loadMoreSongs, viewModel::play)
                    1 -> PlaylistsTab(playlists, onOpenPlaylist, viewModel::loadMorePlaylists)
                    else -> OfflineTab(premium, online, cached, playback, viewModel::play)
                }
            }
        }
    }
}

// ---- Tabs ----------------------------------------------------------------------------------

@Composable
private fun SongsTab(
    state: ListState<Track>,
    playback: PlaybackState,
    onLoadMore: () -> Unit,
    onPlay: (Track) -> Unit,
) {
    if (state.items.isEmpty()) {
        when {
            state.loading -> CenteredBox { CircularProgressIndicator() }
            state.error != null -> ErrorState(state.error, onRetry = onLoadMore)
            else -> EmptyState("No saved songs yet", "Songs you like on Spotify will show up here.")
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), modifier = Modifier.fillMaxSize()) {
        if (state.fromCache) item(key = "cache-note") { NoteCard("You're offline. Showing your last synced songs.") }
        trackItems(state, playback, onLoadMore, onPlay)
    }
}

@Composable
private fun PlaylistsTab(
    state: ListState<Playlist>,
    onOpen: (Playlist) -> Unit,
    onLoadMore: () -> Unit,
) {
    if (state.items.isEmpty()) {
        when {
            state.loading -> CenteredBox { CircularProgressIndicator() }
            state.error != null -> ErrorState(state.error, onRetry = onLoadMore)
            else -> EmptyState("No playlists", "Playlists you create or follow will show up here.")
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(state.items, key = { it.id }) { playlist ->
            PlaylistCard(playlist, onClick = { onOpen(playlist) })
        }
        when {
            state.error != null -> item(span = { GridItemSpan(maxLineSpan) }, key = "error") {
                ErrorRow(state.error, onRetry = onLoadMore)
            }
            state.nextOffset != null -> item(span = { GridItemSpan(maxLineSpan) }, key = "footer") {
                LaunchedEffect(state.items.size) { onLoadMore() }
                LoadingFooter()
            }
        }
    }
}

@Composable
private fun OfflineTab(
    premium: PremiumStatus,
    online: Boolean,
    cached: List<Track>,
    playback: PlaybackState,
    onPlay: (Track) -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), modifier = Modifier.fillMaxSize()) {
        item(key = "status") {
            OfflineStatusCard(premium, online, onOpenSpotify = { context.openSpotify() })
        }
        item(key = "cache-header") {
            Text(
                text = "Last synced songs",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            )
        }
        if (cached.isEmpty()) {
            item(key = "cache-empty") {
                Text(
                    text = "Open Spectra while online once and your most recent saved songs are kept here " +
                        "so you can still browse them without a connection.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        } else {
            itemsIndexed(cached, key = { index, t -> "${index}_${t.uri}" }) { _, track ->
                TrackRow(
                    track = track,
                    isCurrent = playback.track?.uri == track.uri,
                    isPlaying = playback.isPlaying,
                    onClick = { onPlay(track) },
                )
            }
        }
    }
}

// ---- Shared list plumbing (also used by the playlist detail screen) -------------------------

/** Track rows plus an infinite-scroll footer / error row. */
internal fun androidx.compose.foundation.lazy.LazyListScope.trackItems(
    state: ListState<Track>,
    playback: PlaybackState,
    onLoadMore: () -> Unit,
    onPlay: (Track) -> Unit,
) {
    itemsIndexed(state.items, key = { index, t -> "${index}_${t.uri}" }) { _, track ->
        TrackRow(
            track = track,
            isCurrent = playback.track?.uri == track.uri,
            isPlaying = playback.isPlaying,
            onClick = { onPlay(track) },
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

// ---- Pieces --------------------------------------------------------------------------------

@Composable
private fun PlaylistCard(playlist: Playlist, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        CoverArt(
            url = playlist.imageUrl,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            playlist.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val meta = listOfNotNull(
            playlist.trackCount?.let { "$it tracks" },
            playlist.owner.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        if (meta.isNotEmpty()) {
            Text(
                meta,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun OfflineStatusCard(premium: PremiumStatus, online: Boolean, onOpenSpotify: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Offline listening", style = MaterialTheme.typography.titleMedium)
            StatusRow(
                icon = { Icon(Icons.Rounded.Star, null, tint = if (premium == PremiumStatus.PREMIUM) colors.primary else colors.onSurfaceVariant) },
                label = "Spotify plan",
                value = when (premium) {
                    PremiumStatus.PREMIUM -> "Premium"
                    PremiumStatus.FREE -> "Free"
                    PremiumStatus.UNKNOWN -> "Checking…"
                },
            )
            StatusRow(
                icon = {
                    Icon(
                        if (online) Icons.Rounded.Wifi else Icons.Rounded.CloudOff,
                        null,
                        tint = if (online) colors.primary else colors.tertiary,
                    )
                },
                label = "Network",
                value = if (online) "Online" else "Offline",
            )
            Text(
                text = when (premium) {
                    PremiumStatus.PREMIUM ->
                        "Downloads live inside the Spotify app. Spotify doesn't tell third-party apps which " +
                            "songs you've downloaded, so they can't be listed here. Playback of downloaded " +
                            "tracks is handled by Spotify and may still work while you're offline."
                    PremiumStatus.FREE ->
                        "Downloads need Spotify Premium. On Free, playback from other apps is limited to " +
                            "shuffle play."
                    PremiumStatus.UNKNOWN ->
                        "Connect to Spotify to check your plan."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            OutlinedButton(onClick = onOpenSpotify) { Text("Open Spotify") }
        }
    }
}

@Composable
private fun StatusRow(icon: @Composable () -> Unit, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PremiumChip(status: PremiumStatus) {
    val colors = MaterialTheme.colorScheme
    val premium = status == PremiumStatus.PREMIUM
    AssistChip(
        onClick = {},
        label = {
            Text(
                when (status) {
                    PremiumStatus.PREMIUM -> "Premium"
                    PremiumStatus.FREE -> "Free"
                    PremiumStatus.UNKNOWN -> "…"
                },
            )
        },
        leadingIcon = {
            Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
        },
        colors = AssistChipDefaults.assistChipColors(
            labelColor = if (premium) colors.onPrimaryContainer else colors.onSurfaceVariant,
            leadingIconContentColor = if (premium) colors.primary else colors.onSurfaceVariant,
            containerColor = if (premium) colors.primaryContainer else Color.Transparent,
        ),
    )
}

@Composable
private fun ProfileMenu(profile: UserProfile?, onSignOut: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            CoverArt(
                url = profile?.imageUrl,
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
            )
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

@Composable
private fun ConnectionBanner(connection: SpotifyRemoteManager.Connection, onRetry: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    when (connection) {
        is SpotifyRemoteManager.Connection.Failed -> Surface(color = colors.errorContainer) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    connection.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onErrorContainer,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
        SpotifyRemoteManager.Connection.Connecting -> Text(
            "Connecting to Spotify…",
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        else -> Unit
    }
}

@Composable
private fun NoteCard(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
    }
}

@Composable
internal fun LoadingFooter() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onRetry) { Text("Try again") }
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun CenteredBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

private fun Context.openSpotify() {
    val launch = packageManager.getLaunchIntentForPackage("com.spotify.music")
    if (launch != null) startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
