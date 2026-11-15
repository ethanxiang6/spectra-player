package com.spectra.player.data.repository

import com.spectra.player.data.AudioProfile
import com.spectra.player.data.Hit
import com.spectra.player.data.HitKind
import com.spectra.player.data.toHit
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.spectra.player.data.Playlist
import com.spectra.player.data.Track
import com.spectra.player.data.UserProfile
import com.spectra.player.data.remote.SpotifyApi
import com.spectra.player.data.toDomain
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Single source of truth for the user's Spotify library.
 * Network first; when the device is offline the last cached snapshot is served instead.
 */
class LibraryRepository(
    private val api: SpotifyApi,
    private val cache: LibraryCache,
) {
    data class Page<T>(
        val items: List<T>,
        /** Offset to request next, or null at the end of the list. */
        val nextOffset: Int?,
        val fromCache: Boolean = false,
        /** Size of the whole list on Spotify, when known. */
        val total: Int? = null,
    )

    private val profiles = ConcurrentHashMap<String, AudioProfile>()

    /** Flips on the first 403: Audio Features is closed to apps created after 27 Nov 2024. */
    @Volatile
    private var audioFeaturesBlocked = false

    suspend fun profile(): UserProfile = api.me().toDomain()

    suspend fun savedTracks(offset: Int): Page<Track> = try {
        val page = api.savedTracks(PAGE_SIZE, offset)
        val items = page.items.mapNotNull { it.track?.toDomain() }
        cache.writeSavedTracks(offset, items)
        Page(items, nextOffsetOf(page.next, offset, page.items.size), total = page.total)
    } catch (e: IOException) {
        val cached = if (offset == 0) cache.savedTracks() else emptyList()
        if (cached.isNotEmpty()) Page(cached, nextOffset = null, fromCache = true) else throw e
    }

    suspend fun playlists(offset: Int): Page<Playlist> = try {
        val page = api.playlists(PAGE_SIZE, offset)
        val items = page.items.map { it.toDomain() }
        cache.writePlaylists(offset, items)
        Page(items, nextOffsetOf(page.next, offset, page.items.size), total = page.total)
    } catch (e: IOException) {
        val cached = if (offset == 0) cache.playlists() else emptyList()
        if (cached.isNotEmpty()) Page(cached, nextOffset = null, fromCache = true) else throw e
    }

    suspend fun playlistTracks(playlistId: String, offset: Int): Page<Track> {
        return try {
            val page = api.playlistItems(playlistId, PAGE_SIZE, offset)
            val items = page.items.mapNotNull { (it.item ?: it.track)?.toDomain() }
            cache.writePlaylistTracks(playlistId, offset, items)
            Page(items, nextOffsetOf(page.next, offset, page.items.size), total = page.total)
        } catch (e: IOException) {
            // Offline: the songs saved the last time this playlist was opened.
            val cached = if (offset == 0) cache.playlistTracks(playlistId) else emptyList()
            if (cached.isNotEmpty()) Page(cached, nextOffset = null, fromCache = true) else throw e
        }
    }

    /** An album's songs. They come without cover or album name, so those are filled in here. */
    suspend fun albumTracks(albumId: String, offset: Int, albumName: String, imageUrl: String?): Page<Track> {
        val page = api.albumTracks(albumId, PAGE_SIZE, offset)
        val items = page.items.map {
            it.toDomain().copy(album = albumName, imageUrl = imageUrl, albumUri = "spotify:album:$albumId")
        }
        return Page(items, nextOffsetOf(page.next, offset, page.items.size), total = page.total)
    }

    /** An artist's albums and singles, newest first. */
    suspend fun artistAlbums(artistId: String, offset: Int): Page<Hit> {
        val page = api.artistAlbums(artistId, "album,single", SEARCH_PAGE_SIZE, offset)
        return Page(page.items.mapNotNull { it.toHit() }, nextOffsetOf(page.next, offset, page.items.size), total = page.total)
    }

    /** Artists, albums or playlists from the whole catalogue. */
    suspend fun search(query: String, kind: HitKind, offset: Int): Page<Hit> {
        val result = api.search(query, type = kind.apiType, limit = SEARCH_PAGE_SIZE, offset = offset)
        return when (kind) {
            HitKind.ARTISTS -> result.artists?.let { p ->
                Page(p.items.mapNotNull { it.toHit() }, nextOffsetOf(p.next, offset, p.items.size))
            }
            HitKind.ALBUMS -> result.albums?.let { p ->
                Page(p.items.mapNotNull { it.toHit() }, nextOffsetOf(p.next, offset, p.items.size))
            }
            HitKind.PLAYLISTS -> result.playlists?.let { p ->
                Page(p.items.mapNotNull { it?.toHit() }, nextOffsetOf(p.next, offset, p.items.size))
            }
            HitKind.SONGS -> null
        } ?: Page(emptyList(), nextOffset = null)
    }

    // ---- Liked songs ----

    private val _likedChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    /** Fires after a song was added to or removed from Liked songs. */
    val likedChanges: SharedFlow<Unit> = _likedChanges.asSharedFlow()

    /** True/false, or null when Spotify wouldn't say (offline, local file, old sign-in). */
    suspend fun isLiked(trackUri: String): Boolean? {
        if (!trackUri.startsWith("spotify:track:")) return null
        return try {
            val modern = api.libraryContains(trackUri)
            if (modern.isSuccessful) {
                modern.body()?.firstOrNull()
            } else {
                val old = api.tracksContain(trackUri.substringAfterLast(':'))
                if (old.isSuccessful) old.body()?.firstOrNull() else null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Adds or removes a liked song. Returns null on success, else a message for the user. */
    suspend fun setLiked(trackUri: String, liked: Boolean): String? {
        if (!trackUri.startsWith("spotify:track:")) return "Only songs from Spotify's catalogue can be liked."
        return try {
            var response = if (liked) api.saveToLibrary(trackUri) else api.removeFromLibrary(trackUri)
            if (!response.isSuccessful && response.code() !in listOf(401, 429)) {
                val id = trackUri.substringAfterLast(':')
                val old = if (liked) api.saveTracks(id) else api.removeTracks(id)
                if (old.isSuccessful || response.code() == 404) response = old
            }
            when {
                response.isSuccessful -> {
                    _likedChanges.tryEmit(Unit)
                    null
                }
                response.code() == 401 || response.code() == 403 ->
                    "Spotify needs a new permission for liking songs. Sign out and sign in again once (profile picture, Sign out)."
                else -> "Spotify couldn't change your Liked songs (${response.code()})."
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            "No connection. Check your network and try again."
        } catch (e: Exception) {
            "Couldn't change your Liked songs. Please try again."
        }
    }

    /** Songs from the whole catalogue, not only the user's library. */
    suspend fun searchTracks(query: String, offset: Int): Page<Track> {
        val page = api.search(query, type = "track", limit = SEARCH_PAGE_SIZE, offset = offset).tracks
            ?: return Page(emptyList(), nextOffset = null)
        val items = page.items.map { it.toDomain() }
        return Page(items, nextOffsetOf(page.next, offset, page.items.size))
    }

    suspend fun cachedSavedTracks(): List<Track> = cache.savedTracks()

    suspend fun clearCache() = cache.clear()

    /**
     * Tempo / energy / loudness for the simulated visualizer. Never throws: any failure
     * (403 for new apps, 404, offline, bad JSON) yields [AudioProfile.Default].
     */
    suspend fun audioProfile(trackUri: String): AudioProfile {
        if (audioFeaturesBlocked || trackUri.startsWith("spotify:local:")) return AudioProfile.Default
        profiles[trackUri]?.let { return it }
        val id = trackUri.substringAfterLast(':')
        return try {
            api.audioFeatures(id).toDomain().also { profiles[trackUri] = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            if (e.code() == 403) audioFeaturesBlocked = true
            AudioProfile.Default
        } catch (e: Exception) {
            AudioProfile.Default
        }
    }

    private fun nextOffsetOf(next: String?, offset: Int, received: Int): Int? =
        if (next != null && received > 0) offset + received else null

    private companion object {
        const val PAGE_SIZE = 50
        // Development-mode apps get at most 10 search results per request.
        const val SEARCH_PAGE_SIZE = 10
    }
}
