package com.spectra.player.ui.search

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.spectra.player.data.Hit
import com.spectra.player.data.repository.LibraryRepository
import com.spectra.player.data.userMessage
import com.spectra.player.player.SpotifyRemoteManager
import com.spectra.player.ui.chart.Chart
import com.spectra.player.ui.chart.ChartRule
import com.spectra.player.ui.components.CoverArt
import com.spectra.player.ui.components.HitRow
import com.spectra.player.ui.components.MiniPlayer
import com.spectra.player.ui.library.ErrorRow
import com.spectra.player.ui.library.ListState
import com.spectra.player.ui.library.LoadingFooter
import com.spectra.player.ui.navigation.Route
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** An artist opened from search: their albums and singles, each of which opens as a song list. */
class ArtistViewModel(
    handle: SavedStateHandle,
    private val library: LibraryRepository,
    private val remote: SpotifyRemoteManager,
) : ViewModel() {
    private val route = handle.toRoute<Route.Artist>()
    val name: String = route.name
    val imageUrl: String? = route.imageUrl

    private val _albums = MutableStateFlow(ListState<Hit>())
    val albums: StateFlow<ListState<Hit>> = _albums.asStateFlow()

    val playback = remote.playback
    val artwork = remote.artwork

    init {
        loadMore()
    }

    fun loadMore() {
        val current = _albums.value
        val offset = current.nextOffset ?: return
        if (current.loading) return
        _albums.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val page = library.artistAlbums(route.id, offset)
                _albums.update {
                    it.copy(
                        items = if (offset == 0) page.items else it.items + page.items,
                        nextOffset = page.nextOffset,
                        loading = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _albums.update { it.copy(loading = false, error = e.userMessage()) }
            }
        }
    }

    fun togglePlayPause() = remote.togglePlayPause()
    fun skipNext() = remote.skipNext()
}

@Composable
fun ArtistScreen(
    viewModel: ArtistViewModel,
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenAlbum: (Hit) -> Unit,
) {
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val artwork by viewModel.artwork.collectAsStateWithLifecycle()

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
                Text("ARTIST", style = MaterialTheme.typography.labelMedium, color = Chart.dim)
            }
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
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            item(key = "header") {
                Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    // The artist's picture inside a thin orbit ring.
                    Box(
                        Modifier
                            .padding(start = 20.dp, top = 14.dp)
                            .size(132.dp)
                            .border(1.dp, Chart.line, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        CoverArt(url = viewModel.imageUrl, modifier = Modifier.size(116.dp), shape = CircleShape)
                    }
                    Text(
                        viewModel.name,
                        style = MaterialTheme.typography.headlineLarge,
                        color = Chart.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp),
                    )
                    ChartRule("Albums and singles", modifier = Modifier.padding(top = 24.dp, bottom = 4.dp))
                }
            }
            when {
                albums.items.isEmpty() && albums.loading -> item(key = "loading") { LoadingFooter() }
                albums.items.isEmpty() && albums.error != null ->
                    item(key = "error") { ErrorRow(albums.error ?: "", onRetry = viewModel::loadMore) }
                albums.items.isEmpty() -> item(key = "empty") {
                    Text(
                        "Spotify lists no albums for this artist.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Chart.dim,
                        modifier = Modifier.padding(20.dp),
                    )
                }
                else -> {
                    items(albums.items.size, key = { i -> "${i}_${albums.items[i].id}" }) { i ->
                        val album = albums.items[i]
                        HitRow(album, onClick = { onOpenAlbum(album) })
                    }
                    when {
                        albums.error != null -> item(key = "more-error") {
                            ErrorRow(albums.error ?: "", onRetry = viewModel::loadMore)
                        }
                        albums.nextOffset != null -> item(key = "footer") {
                            LaunchedEffect(albums.items.size) { viewModel.loadMore() }
                            LoadingFooter()
                        }
                    }
                }
            }
        }
    }
}
