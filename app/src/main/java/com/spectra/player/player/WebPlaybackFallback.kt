package com.spectra.player.player

import android.os.Build
import android.util.Log
import android.os.SystemClock
import com.spectra.player.data.remote.OffsetDto
import com.spectra.player.data.remote.PlayBodyDto
import com.spectra.player.data.remote.SpotifyApi
import retrofit2.HttpException

/**
 * Starts playback through the Web API ("Spotify Connect") on the phone's Spotify app. Used when
 * App Remote is connected but refuses a play request.
 */
class WebPlaybackFallback(private val api: SpotifyApi) {

    /** Returns null on success, otherwise a short reason to show the user. */
    suspend fun play(uri: String, context: PlayContext? = null): String? = try {
        val devices = api.devices().devices.filter { it.id != null && !it.isRestricted }
        val model = Build.MODEL.orEmpty()
        val target = devices.firstOrNull { it.isActive }
            ?: devices.firstOrNull { it.type.equals("Smartphone", true) && model.isNotBlank() && it.name.contains(model, true) }
            ?: devices.firstOrNull { it.type.equals("Smartphone", true) }
            ?: devices.firstOrNull()
        if (target == null) {
            // No device listed: let Spotify pick the most recently used one.
            val response = api.startPlayback(null, bodyFor(uri, context))
            if (response.isSuccessful) null else describe(response.code(), response.errorBody()?.string())
        } else {
            val response = api.startPlayback(target.id, bodyFor(uri, context))
            if (response.isSuccessful) null else describe(response.code(), response.errorBody()?.string())
        }
    } catch (e: HttpException) {
        describe(e.code(), e.response()?.errorBody()?.string())
    } catch (e: Exception) {
        Log.w(TAG, "Web playback failed: ${e.javaClass.simpleName}")
        e.javaClass.simpleName
    }

    /** What Spotify is playing right now, or null if nothing / unavailable. */
    suspend fun currentlyPlaying(): PlaybackState? = try {
        val response = api.currentlyPlaying()
        val dto = response.body()
        val item = dto?.item
        if (!response.isSuccessful || dto == null || item == null) {
            null
        } else {
            PlaybackState(
                track = NowPlayingTrack(
                    uri = item.uri,
                    name = item.name,
                    artist = item.artists.orEmpty().joinToString(", ") { it.name },
                    album = item.album?.name.orEmpty(),
                    durationMs = item.durationMs,
                    imageUri = null,
                    imageUrl = item.album?.images?.maxByOrNull { it.width ?: 0 }?.url,
                ),
                isPlaying = dto.isPlaying,
                positionMs = dto.progressMs ?: 0L,
                capturedAtElapsedMs = SystemClock.elapsedRealtime(),
                isShuffling = false,
                repeatMode = 0,
            )
        }
    } catch (e: Exception) {
        Log.w(TAG, "currently-playing failed: ${e.javaClass.simpleName}")
        null
    }

    suspend fun pause(): Boolean = call { api.pausePlayback() }
    suspend fun resume(): Boolean = call { api.startPlayback(null, PlayBodyDto()) }
    suspend fun next(): Boolean = call { api.nextTrack() }
    suspend fun previous(): Boolean = call { api.previousTrack() }

    private suspend fun call(block: suspend () -> retrofit2.Response<Unit>): Boolean = try {
        block().isSuccessful
    } catch (e: Exception) {
        Log.w(TAG, "Web playback call failed: ${e.javaClass.simpleName}")
        false
    }

    suspend fun queue(uri: String): Boolean = call { api.addToQueue(uri, null) }
    suspend fun repeatList(): Boolean = call { api.setRepeat("context") }
    suspend fun shuffle(on: Boolean): Boolean = call { api.setShuffle(on) }
    suspend fun seek(positionMs: Long): Boolean = call { api.seek(positionMs) }

    private fun bodyFor(uri: String, context: PlayContext?) =
        if (context != null) {
            PlayBodyDto(contextUri = context.uri, offset = OffsetDto(uri = uri))
        } else if (uri.startsWith("spotify:track:") || uri.startsWith("spotify:episode:")) {
            PlayBodyDto(uris = listOf(uri))
        } else {
            PlayBodyDto(contextUri = uri)
        }

    private fun describe(code: Int, body: String?): String {
        val detail = body?.let { Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
        return when {
            code == 401 || (code == 403 && detail?.contains("scope", true) == true) ->
                "Sign out and sign in again so Spectra can control playback ($code${detail?.let { ": $it" } ?: ""})."
            code == 403 && detail?.contains("premium", true) == true -> "Spotify Premium is required ($detail)."
            code == 404 -> "No active Spotify device. Open Spotify, play anything once, then try again."
            else -> "Web API $code${detail?.let { ": $it" } ?: ""}"
        }
    }

    private companion object {
        const val TAG = "WebPlayback"
    }
}
