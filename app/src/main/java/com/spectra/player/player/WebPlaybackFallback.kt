package com.spectra.player.player

import android.os.Build
import android.util.Log
import com.spectra.player.data.remote.PlayBodyDto
import com.spectra.player.data.remote.SpotifyApi
import retrofit2.HttpException

/**
 * Starts playback through the Web API ("Spotify Connect") on the phone's Spotify app. Used when
 * App Remote is connected but refuses a play request.
 */
class WebPlaybackFallback(private val api: SpotifyApi) {

    /** Returns null on success, otherwise a short reason to show the user. */
    suspend fun play(uri: String): String? = try {
        val devices = api.devices().devices.filter { it.id != null && !it.isRestricted }
        val model = Build.MODEL.orEmpty()
        val target = devices.firstOrNull { it.isActive }
            ?: devices.firstOrNull { it.type.equals("Smartphone", true) && model.isNotBlank() && it.name.contains(model, true) }
            ?: devices.firstOrNull { it.type.equals("Smartphone", true) }
            ?: devices.firstOrNull()
        if (target == null) {
            // No device listed: let Spotify pick the most recently used one.
            val response = api.startPlayback(null, bodyFor(uri))
            if (response.isSuccessful) null else describe(response.code(), response.errorBody()?.string())
        } else {
            val response = api.startPlayback(target.id, bodyFor(uri))
            if (response.isSuccessful) null else describe(response.code(), response.errorBody()?.string())
        }
    } catch (e: HttpException) {
        describe(e.code(), e.response()?.errorBody()?.string())
    } catch (e: Exception) {
        Log.w(TAG, "Web playback failed: ${e.javaClass.simpleName}")
        e.javaClass.simpleName
    }

    private fun bodyFor(uri: String) =
        if (uri.startsWith("spotify:track:") || uri.startsWith("spotify:episode:")) {
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
