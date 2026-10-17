package com.spectra.player.player

import android.os.SystemClock

data class NowPlayingTrack(
    val uri: String,
    val name: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val imageUri: String?,
    /** HTTP artwork (Web API), used when App Remote hasn't delivered a bitmap. */
    val imageUrl: String? = null,
)

data class PlaybackState(
    val track: NowPlayingTrack?,
    val isPlaying: Boolean,
    /** Position reported by Spotify at [capturedAtElapsedMs]. */
    val positionMs: Long,
    val capturedAtElapsedMs: Long,
    val isShuffling: Boolean,
    /** 0 = off, 1 = repeat one, 2 = repeat all (Spotify's Repeat constants). */
    val repeatMode: Int,
) {
    /** Extrapolates the position between player-state events so the seek bar and visualizer stay smooth. */
    fun currentPosition(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Long {
        val estimate = if (isPlaying) positionMs + (nowElapsedMs - capturedAtElapsedMs) else positionMs
        val duration = track?.durationMs ?: return estimate.coerceAtLeast(0)
        return estimate.coerceIn(0, duration)
    }

    companion object {
        val Idle = PlaybackState(
            track = null, isPlaying = false, positionMs = 0,
            capturedAtElapsedMs = 0, isShuffling = false, repeatMode = 0,
        )
    }
}

/** Library track -> the bar's provisional "now playing" entry. */
fun com.spectra.player.data.Track.toNowPlaying() = NowPlayingTrack(
    uri = uri,
    name = name,
    artist = artists,
    album = album,
    durationMs = durationMs,
    imageUri = null,
    imageUrl = imageUrl,
)

/**
 * A list to play a song inside: a playlist, the Liked Songs collection or an album.
 * [index] is the song's position (App Remote needs it); null means "find it by URI" (Web API).
 */
data class PlayContext(val uri: String, val index: Int?)
