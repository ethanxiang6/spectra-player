package com.spectra.player.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.spectra.player.data.Track
import com.spectra.player.data.repository.LibraryRepository
import com.spectra.player.data.userMessage
import com.spectra.player.player.SpotifyRemoteManager
import com.spectra.player.player.PlayContext
import com.spectra.player.player.toNowPlaying
import com.spectra.player.ui.navigation.Route
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

class PlaylistViewModel(
    handle: SavedStateHandle,
    private val library: LibraryRepository,
    private val remote: SpotifyRemoteManager,
) : ViewModel() {

    private val route = handle.toRoute<Route.PlaylistDetail>()
    val title: String = route.name
    private val playlistUri = "spotify:playlist:${route.id}"

    private val _tracks = MutableStateFlow(ListState<Track>())
    val tracks: StateFlow<ListState<Track>> = _tracks.asStateFlow()

    val playback = remote.playback
    val artwork = remote.artwork
    val messages = remote.messages

    init {
        loadMore()
    }

    fun loadMore() {
        val current = _tracks.value
        val offset = current.nextOffset ?: return
        if (current.loading) return
        _tracks.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val page = library.playlistTracks(route.id, offset)
                _tracks.update {
                    it.copy(
                        items = if (offset == 0) page.items else it.items + page.items,
                        nextOffset = page.nextOffset,
                        loading = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val restricted = e is HttpException && (e.code() == 403 || e.code() == 404)
                _tracks.update {
                    it.copy(
                        loading = false,
                        nextOffset = if (restricted) null else it.nextOffset,
                        error = if (restricted) {
                            "Spotify only shares the tracks of playlists you own or collaborate on. " +
                                "You can still press Play to start it in Spotify."
                        } else {
                            e.userMessage()
                        },
                    )
                }
            }
        }
    }

    fun playAll() = remote.play(playlistUri)

    /** Plays inside this playlist, starting at [index], so the next songs come from the same playlist. */
    fun play(index: Int, track: Track) =
        remote.play(track.uri, track.toNowPlaying(), PlayContext(playlistUri, index))

    fun queue(track: Track) = remote.queue(track.uri, track.name)
    fun skipNext() = remote.skipNext()
    fun togglePlayPause() = remote.togglePlayPause()
}
