package com.spectra.player.auth

import com.spectra.player.BuildConfig

object AuthConfig {
    /** From local.properties (`spotify.clientId`) or the SPOTIFY_CLIENT_ID env var. */
    val clientId: String = BuildConfig.SPOTIFY_CLIENT_ID

    /** Must be registered verbatim in the Spotify dashboard and match the manifest intent filter. */
    val redirectUri: String = BuildConfig.SPOTIFY_REDIRECT_URI

    const val AUTHORIZE_URL = "https://accounts.spotify.com/authorize"

    /**
     * Web API scopes. App Remote asks the Spotify app for its own scopes separately.
     * - user-read-private: profile (+ `product`, where Spotify still returns it)
     * - user-library-read: saved songs
     * - playlist-read-private / -collaborative: the user's playlists
     */
    val scopes = listOf(
        "user-read-private",
        "user-library-read",
        "playlist-read-private",
        "playlist-read-collaborative",
    )

    val isConfigured: Boolean get() = clientId.isNotBlank()
}
