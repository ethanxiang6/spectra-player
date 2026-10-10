package com.spectra.player.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spectra.player.auth.SpotifyAuthManager
import com.spectra.player.data.Playlist
import com.spectra.player.data.PremiumStatus
import com.spectra.player.data.Track
import com.spectra.player.data.UserProfile
import com.spectra.player.data.repository.LibraryRepository
import com.spectra.player.data.repository.NetworkMonitor
import com.spectra.player.data.userMessage
import com.spectra.player.player.SpotifyRemoteManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Paged list state shared by the songs / playlists / playlist-detail screens. */
data class ListState<T>(
    val items: List<T> = emptyList(),
    val loading: Boolean = false,
    /** Next offset to request; null once the end (or a cache fallback) is reached. */
    val nextOffset: Int? = 0,
    val error: String? = null,
    val fromCache: Boolean = false,
)

class LibraryViewModel(
    private val auth: SpotifyAuthManager,
    private val library: LibraryRepository,
    private val remote: SpotifyRemoteManager,
    network: NetworkMonitor,
) : ViewModel() {

    private val _songs = MutableStateFlow(ListState<Track>())
    val songs: StateFlow<ListState<Track>> = _songs.asStateFlow()

    private val _playlists = MutableStateFlow(ListState<Playlist>())
    val playlists: StateFlow<ListState<Playlist>> = _playlists.asStateFlow()

    private val _cachedTracks = MutableStateFlow<List<Track>>(emptyList())
    val cachedTracks: StateFlow<List<Track>> = _cachedTracks.asStateFlow()

    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile: StateFlow<UserProfile?> = _profile.asStateFlow()

    val premium: StateFlow<PremiumStatus> =
        combine(_profile, remote.canPlayOnDemand) { profile, onDemand ->
            PremiumStatus.resolve(profile?.product, onDemand)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PremiumStatus.UNKNOWN)

    val isOnline: StateFlow<Boolean> = network.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), network.currentlyOnline())

    val connection = remote.connection
    val playback = remote.playback
    val artwork = remote.artwork
    val messages = remote.messages

    init {
        loadProfile()
        loadMoreSongs()
        loadMorePlaylists()
        refreshCachedTracks()
        remote.connect()
    }

    fun loadMoreSongs() {
        val current = _songs.value
        val offset = current.nextOffset ?: return
        if (current.loading) return
        _songs.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val page = library.savedTracks(offset)
                _songs.update {
                    it.copy(
                        items = if (offset == 0) page.items else it.items + page.items,
                        nextOffset = page.nextOffset,
                        loading = false,
                        fromCache = page.fromCache,
                    )
                }
                refreshCachedTracks()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _songs.update { it.copy(loading = false, error = e.userMessage()) }
            }
        }
    }

    fun loadMorePlaylists() {
        val current = _playlists.value
        val offset = current.nextOffset ?: return
        if (current.loading) return
        _playlists.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val page = library.playlists(offset)
                _playlists.update {
                    it.copy(
                        items = if (offset == 0) page.items else it.items + page.items,
                        nextOffset = page.nextOffset,
                        loading = false,
                        fromCache = page.fromCache,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _playlists.update { it.copy(loading = false, error = e.userMessage()) }
            }
        }
    }

    fun refresh() {
        _songs.value = ListState()
        _playlists.value = ListState()
        loadProfile()
        loadMoreSongs()
        loadMorePlaylists()
    }

    fun connectRemote() = remote.connect()
    fun play(track: Track) = remote.play(track.uri)
    fun togglePlayPause() = remote.togglePlayPause()

    fun signOut() {
        remote.disconnect()
        auth.signOut()
        viewModelScope.launch { library.clearCache() }
    }

    private fun loadProfile() {
        viewModelScope.launch {
            try {
                _profile.value = library.profile()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Non-fatal: Premium falls back to the App Remote capability signal.
            }
        }
    }

    private fun refreshCachedTracks() {
        viewModelScope.launch { _cachedTracks.value = library.cachedSavedTracks() }
    }
}
