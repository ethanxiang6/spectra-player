package com.spectra.player.data.repository

import android.content.Context
import com.spectra.player.data.Playlist
import com.spectra.player.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Small on-disk snapshot of the user's saved songs and playlists so the library can still be
 * browsed while offline. This is metadata only (no audio) and is capped; it is wiped on sign-out.
 * Review Spotify's Developer Policy on caching before shipping.
 */
class LibraryCache(context: Context, private val json: Json) {

    @Serializable
    private data class Snapshot(
        val savedTracks: List<Track> = emptyList(),
        val playlists: List<Playlist> = emptyList(),
        val savedAtMs: Long = 0,
    )

    private val file = File(context.filesDir, "library_cache.json")
    private val lock = Mutex()

    suspend fun savedTracks(): List<Track> = read().savedTracks
    suspend fun playlists(): List<Playlist> = read().playlists

    suspend fun writeSavedTracks(offset: Int, items: List<Track>) = update { snap ->
        val merged = if (offset == 0) items else snap.savedTracks + items
        snap.copy(savedTracks = merged.take(MAX_TRACKS))
    }

    suspend fun writePlaylists(offset: Int, items: List<Playlist>) = update { snap ->
        val merged = if (offset == 0) items else snap.playlists + items
        snap.copy(playlists = merged.take(MAX_PLAYLISTS))
    }

    suspend fun clear() = lock.withLock {
        withContext(Dispatchers.IO) { file.delete() }
        Unit
    }

    private suspend fun read(): Snapshot = lock.withLock { readLocked() }

    private suspend fun update(transform: (Snapshot) -> Snapshot) = lock.withLock {
        withContext(Dispatchers.IO) {
            val next = transform(readLocked()).copy(savedAtMs = System.currentTimeMillis())
            file.writeText(json.encodeToString(Snapshot.serializer(), next))
        }
    }

    private suspend fun readLocked(): Snapshot = withContext(Dispatchers.IO) {
        try {
            if (file.exists()) json.decodeFromString(Snapshot.serializer(), file.readText()) else Snapshot()
        } catch (e: Exception) {
            Snapshot()
        }
    }

    private companion object {
        const val MAX_TRACKS = 300
        const val MAX_PLAYLISTS = 100
    }
}
