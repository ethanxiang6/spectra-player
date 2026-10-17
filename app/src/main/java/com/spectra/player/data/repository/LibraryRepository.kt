package com.spectra.player.data.repository

import com.spectra.player.data.AudioProfile
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
        Page(items, nextOffsetOf(page.next, offset, page.items.size))
    } catch (e: IOException) {
        val cached = if (offset == 0) cache.savedTracks() else emptyList()
        if (cached.isNotEmpty()) Page(cached, nextOffset = null, fromCache = true) else throw e
    }

    suspend fun playlists(offset: Int): Page<Playlist> = try {
        val page = api.playlists(PAGE_SIZE, offset)
        val items = page.items.map { it.toDomain() }
        cache.writePlaylists(offset, items)
        Page(items, nextOffsetOf(page.next, offset, page.items.size))
    } catch (e: IOException) {
        val cached = if (offset == 0) cache.playlists() else emptyList()
        if (cached.isNotEmpty()) Page(cached, nextOffset = null, fromCache = true) else throw e
    }

    suspend fun playlistTracks(playlistId: String, offset: Int): Page<Track> {
        val page = api.playlistItems(playlistId, PAGE_SIZE, offset)
        val items = page.items.mapNotNull { (it.item ?: it.track)?.toDomain() }
        return Page(items, nextOffsetOf(page.next, offset, page.items.size))
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
