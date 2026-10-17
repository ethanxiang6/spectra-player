package com.spectra.player.ui.library

import androidx.compose.ui.draw.clip

import androidx.compose.foundation.background

import androidx.compose.foundation.layout.height

import androidx.compose.material3.FilledIconButton

import androidx.compose.material.icons.rounded.PlayArrow

import androidx.compose.material.icons.rounded.Shuffle

import androidx.compose.foundation.lazy.items

import androidx.compose.foundation.lazy.LazyRow

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
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
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

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
        PullToRefreshBox(
            isRefreshing = songs.loading && songs.items.isEmpty(),
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "header") {
                    Greeting(
                        profile = profile,
                        onSettings = onOpenSettings,
                        onSignOut = viewModel::signOut,
                    )
                }
                item(key = "search") { SearchPill(onClick = onOpenSearch) }
                item(key = "banner") {
                    ConnectionBanner(
                        connection,
                        onRetry = viewModel::connectRemote,
                        onApprove = { context.findActivity()?.let(viewModel::connectViaSpotify) },
                    )
                }
                if (!online || songs.fromCache) {
                    item(key = "offline") { NoteCard("You're offline. Showing the songs saved on this phone.") }
                }

                item(key = "playlists-heading") { SectionHeading("Playlists") }
                item(key = "playlists") { PlaylistShelf(playlists, onOpenPlaylist, viewModel::loadMorePlaylists) }

                item(key = "liked-heading") {
                    LikedHeading(
                        enabled = songs.items.isNotEmpty(),
                        onPlay = { viewModel.playLiked(shuffle = false) },
                        onShuffle = { viewModel.playLiked(shuffle = true) },
                    )
                }
                when {
                    songs.items.isEmpty() && songs.loading -> item(key = "songs-loading") { LoadingFooter() }
                    songs.items.isEmpty() && songs.error != null ->
                        item(key = "songs-error") { ErrorRow(songs.error!!, onRetry = viewModel::loadMoreSongs) }
                    songs.items.isEmpty() -> item(key = "songs-empty") {
                        Quiet("Songs you like on Spotify show up here.")
                    }
                    else -> trackItems(songs, playback, viewModel::loadMoreSongs, viewModel::play, viewModel::queue)
                }
            }
        }
    }
}

// ---- Header and sections -------------------------------------------------------------------

@Composable
private fun Greeting(profile: UserProfile?, onSettings: () -> Unit, onSignOut: () -> Unit) {
    val hour = remember { java.time.LocalTime.now().hour }
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                greeting,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                profile?.displayName?.substringBefore(' ') ?: "Your library",
                style = MaterialTheme.typography.displaySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ProfileMenu(profile, onSettings = onSettings, onSignOut = onSignOut)
    }
}

@Composable
private fun SearchPill(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClickLabel = "Search", onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(
            "Search songs, artists, albums",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 30.dp, bottom = 12.dp),
    )
}

@Composable
private fun LikedHeading(enabled: Boolean, onPlay: () -> Unit, onShuffle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 30.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Liked songs", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        IconButton(onClick = onShuffle, enabled = enabled) {
            Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle liked songs")
        }
        Spacer(Modifier.width(4.dp))
        FilledIconButton(onClick = onPlay, enabled = enabled, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play liked songs", modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun PlaylistShelf(
    state: ListState<Playlist>,
    onOpen: (Playlist) -> Unit,
    onLoadMore: () -> Unit,
) {
    when {
        state.items.isEmpty() && state.loading -> LoadingFooter()
        state.items.isEmpty() && state.error != null -> ErrorRow(state.error, onRetry = onLoadMore)
        state.items.isEmpty() -> Quiet("Playlists you make or follow show up here.")
        else -> LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(state.items, key = { it.id }) { playlist ->
                PlaylistTile(playlist, onClick = { onOpen(playlist) })
            }
            if (state.nextOffset != null) {
                item(key = "more") {
                    LaunchedEffect(state.items.size) { onLoadMore() }
                    Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistTile(playlist: Playlist, onClick: () -> Unit) {
    Column(
        Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        CoverArt(
            url = playlist.imageUrl,
            modifier = Modifier.size(140.dp),
            shape = RoundedCornerShape(10.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            playlist.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        playlist.trackCount?.let {
            Text(
                if (it == 1) "1 song" else "$it songs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Quiet(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

// ---- Shared list plumbing (also used by the playlist detail screen) -------------------------

/** Track rows plus an infinite-scroll footer / error row. */
internal fun androidx.compose.foundation.lazy.LazyListScope.trackItems(
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

// ---- Pieces --------------------------------------------------------------------------------

@Composable
private fun ProfileMenu(profile: UserProfile?, onSettings: () -> Unit, onSignOut: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            CoverArt(
                url = profile?.imageUrl,
                modifier = Modifier.size(40.dp),
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

@Composable
private fun ConnectionBanner(
    connection: SpotifyRemoteManager.Connection,
    onRetry: () -> Unit,
    onApprove: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    when (connection) {
        is SpotifyRemoteManager.Connection.Failed -> Surface(color = colors.errorContainer) {
            var showDetails by remember { mutableStateOf(false) }
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)) {
                Text(
                    connection.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onErrorContainer,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!connection.spotifyMissing) {
                        TextButton(onClick = onApprove) { Text("Approve in Spotify") }
                    }
                    TextButton(onClick = onRetry) { Text("Retry") }
                    connection.details?.let {
                        TextButton(onClick = { showDetails = !showDetails }) {
                            Text(if (showDetails) "Hide details" else "Details")
                        }
                    }
                }
                if (showDetails) {
                    connection.details?.let { details ->
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            Text(details, style = MaterialTheme.typography.labelSmall, color = colors.onErrorContainer)
                        }
                    }
                }
            }
        }
        SpotifyRemoteManager.Connection.NeedsApproval -> Surface(color = colors.secondaryContainer) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "To play music, Spotify needs to approve Spectra once. Tap the button, allow Spectra in Spotify, then come back.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onApprove) { Text("Approve in Spotify") }
            }
        }
        SpotifyRemoteManager.Connection.Connecting -> Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Connecting to Spotify…",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onApprove) { Text("Approve in Spotify") }
        }
        else -> Unit
    }
}

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
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
