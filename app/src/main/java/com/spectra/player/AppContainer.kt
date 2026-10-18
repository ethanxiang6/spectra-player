package com.spectra.player

import android.content.Context
import com.spectra.player.auth.SecureTokenStore
import com.spectra.player.auth.SpotifyAccountsApi
import com.spectra.player.auth.SpotifyAuthManager
import com.spectra.player.data.remote.BearerInterceptor
import com.spectra.player.data.remote.RateLimitInterceptor
import com.spectra.player.data.remote.SpotifyApi
import com.spectra.player.data.remote.TokenAuthenticator
import com.spectra.player.data.repository.LibraryCache
import com.spectra.player.data.repository.LibraryRepository
import com.spectra.player.data.repository.NetworkMonitor
import com.spectra.player.player.SpotifyRemoteManager
import com.spectra.player.player.WebPlaybackFallback
import com.spectra.player.visualizer.VisualizerController
import com.spectra.player.visualizer.VisualizerPrefs
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Manual dependency container (kept deliberately small; swap for Hilt/Koin if the app grows).
 * Lives in [SpectraApp] so auth, the App Remote connection and the visualizer outlive screens.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }
    private val jsonMediaType = "application/json".toMediaType()

    // --- Auth: token endpoint uses a plain client (no bearer header, no authenticator loop) ---
    private val secureStore = SecureTokenStore(appContext)
    private val accountsApi: SpotifyAccountsApi = Retrofit.Builder()
        .baseUrl("https://accounts.spotify.com/")
        .client(
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build(),
        )
        .addConverterFactory(json.asConverterFactory(jsonMediaType))
        .build()
        .create(SpotifyAccountsApi::class.java)

    val authManager = SpotifyAuthManager(secureStore, accountsApi)

    // --- Web API ---
    private val apiClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(BearerInterceptor(authManager))
        .addInterceptor(RateLimitInterceptor())
        .authenticator(TokenAuthenticator(authManager))
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                        redactHeader("Authorization")
                    },
                )
            }
        }
        .build()

    private val spotifyApi: SpotifyApi = Retrofit.Builder()
        .baseUrl("https://api.spotify.com/")
        .client(apiClient)
        .addConverterFactory(json.asConverterFactory(jsonMediaType))
        .build()
        .create(SpotifyApi::class.java)

    // --- Data / playback / visualizer ---
    val network = NetworkMonitor(appContext)
    val library = LibraryRepository(spotifyApi, LibraryCache(appContext, json))
    val remote = SpotifyRemoteManager(appContext, WebPlaybackFallback(spotifyApi))
    val visualizer = VisualizerController(library, appContext)
    val visualizerPrefs = VisualizerPrefs(appContext)
    val appPrefs = com.spectra.player.ui.theme.AppPrefs(appContext)
}
