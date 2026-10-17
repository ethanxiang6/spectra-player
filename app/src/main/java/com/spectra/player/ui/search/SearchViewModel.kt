package com.spectra.player.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spectra.player.data.Track
import com.spectra.player.data.repository.LibraryRepository
import com.spectra.player.data.userMessage
import com.spectra.player.player.PlayContext
import com.spectra.player.player.SpotifyRemoteManager
import com.spectra.player.player.toNowPlaying
import com.spectra.player.ui.library.ListState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Search across all of Spotify (not only the user's library). */
class SearchViewModel(
    private val library: LibraryRepository,
    private val remote: SpotifyRemoteManager,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow(ListState<Track>(nextOffset = null))
    val results: StateFlow<ListState<Track>> = _results.asStateFlow()

    val playback = remote.playback
    val artwork = remote.artwork
    val messages = remote.messages

    private var searchJob: Job? = null

    fun onQueryChange(text: String) {
        _query.value = text
        searchJob?.cancel()
        val q = text.trim()
        if (q.isEmpty()) {
            _results.value = ListState(nextOffset = null)
            return
        }
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS) // wait until typing pauses
            _results.value = ListState(loading = true, nextOffset = 0)
            load(q, 0)
        }
    }

    fun loadMore() {
        val current = _results.value
        val offset = current.nextOffset ?: return
        if (current.loading || offset == 0) return
        val q = _query.value.trim()
        if (q.isEmpty()) return
        _results.update { it.copy(loading = true, error = null) }
        searchJob = viewModelScope.launch { load(q, offset) }
    }

    private suspend fun load(q: String, offset: Int) {
        try {
            val page = library.searchTracks(q, offset)
            _results.update {
                it.copy(
                    items = if (offset == 0) page.items else it.items + page.items,
                    nextOffset = page.nextOffset,
                    loading = false,
                    error = null,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _results.update { it.copy(loading = false, error = e.userMessage()) }
        }
    }

    /**
     * A search result plays inside its album, so when it ends the album continues rather than
     * Spotify picking unrelated songs.
     */
    fun play(track: Track) {
        val albumUri = track.albumUri
        val context = albumUri?.let {
            val firstDisc = track.discNumber == null || track.discNumber == 1
            val index = track.trackNumber?.takeIf { firstDisc }?.minus(1)
            PlayContext(it, index)
        }
        remote.play(track.uri, track.toNowPlaying(), context)
    }

    fun queue(track: Track) = remote.queue(track.uri, track.name)
    fun togglePlayPause() = remote.togglePlayPause()
    fun skipNext() = remote.skipNext()

    private companion object {
        const val DEBOUNCE_MS = 350L
    }
}
