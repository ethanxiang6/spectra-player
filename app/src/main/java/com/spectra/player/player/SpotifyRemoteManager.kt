package com.spectra.player.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.spectra.player.auth.AuthConfig
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.Image
import com.spotify.protocol.types.ImageUri
import com.spotify.protocol.types.PlayerState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Thin, Compose-friendly wrapper over the Spotify App Remote SDK.
 *
 * App Remote controls playback inside the installed Spotify app. It exposes player state,
 * artwork and transport controls, but NOT raw audio (hence the visualizer fallbacks) and NOT
 * the user's downloaded-track list.
 */
class SpotifyRemoteManager(private val context: Context) {

    sealed interface Connection {
        data object Disconnected : Connection
        data object Connecting : Connection
        data object Connected : Connection
        data class Failed(val message: String, val spotifyMissing: Boolean = false) : Connection
    }

    private var remote: SpotifyAppRemote? = null
    private var playerSubscription: Subscription<PlayerState>? = null
    private var lastImageUri: String? = null
    private var pendingPlayUri: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var attempt = 0

    private val _connection = MutableStateFlow<Connection>(Connection.Disconnected)
    val connection: StateFlow<Connection> = _connection.asStateFlow()

    private val _playback = MutableStateFlow(PlaybackState.Idle)
    val playback: StateFlow<PlaybackState> = _playback.asStateFlow()

    private val _artwork = MutableStateFlow<ImageBitmap?>(null)
    val artwork: StateFlow<ImageBitmap?> = _artwork.asStateFlow()

    /** True on Premium (on-demand playback allowed), false on Free, null until known. */
    private val _canPlayOnDemand = MutableStateFlow<Boolean?>(null)
    val canPlayOnDemand: StateFlow<Boolean?> = _canPlayOnDemand.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** One-off messages for a snackbar (e.g. "Premium required"). */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun connect() {
        when (_connection.value) {
            Connection.Connecting, Connection.Connected -> return
            else -> Unit
        }
        _connection.value = Connection.Connecting
        val thisAttempt = ++attempt
        // The SDK can wait forever when the Spotify app never answers (e.g. the fingerprint isn't
        // registered, or Device Broadcast Status is off). Turn that into an actionable message.
        mainHandler.postDelayed({
            if (thisAttempt == attempt && _connection.value == Connection.Connecting) {
                _connection.value = Connection.Failed(CONNECT_TIMEOUT_MESSAGE)
            }
        }, CONNECT_TIMEOUT_MS)

        val params = ConnectionParams.Builder(AuthConfig.clientId)
            .setRedirectUri(AuthConfig.redirectUri)
            .showAuthView(true) // Spotify shows its one-time "allow this app" sheet
            .build()

        SpotifyAppRemote.connect(
            context,
            params,
            object : Connector.ConnectionListener {
                override fun onConnected(appRemote: SpotifyAppRemote) {
                    attempt++
                    remote = appRemote
                    _connection.value = Connection.Connected
                    onConnected(appRemote)
                }

                override fun onFailure(error: Throwable) {
                    Log.w(TAG, "App Remote connect failed: ${error.javaClass.simpleName}")
                    attempt++
                    remote = null
                    _connection.value = error.toFailure()
                }
            },
        )
    }

    fun disconnect() {
        attempt++
        playerSubscription?.cancel()
        playerSubscription = null
        remote?.let { SpotifyAppRemote.disconnect(it) }
        remote = null
        lastImageUri = null
        pendingPlayUri = null
        _playback.value = PlaybackState.Idle
        _artwork.value = null
        _canPlayOnDemand.value = null
        _connection.value = Connection.Disconnected
    }

    // ---- Transport -------------------------------------------------------------------------

    /** Plays a Spotify URI (track, album or playlist). Connects first if needed. */
    fun play(uri: String) {
        val r = remote
        if (r == null || _connection.value != Connection.Connected) {
            pendingPlayUri = uri
            connect()
            return
        }
        r.playerApi.play(uri).setErrorCallback { reportError("Couldn't start playback", it) }
    }

    fun togglePlayPause() {
        val api = remote?.playerApi ?: return
        if (_playback.value.isPlaying) api.pause() else api.resume()
    }

    fun skipNext() { remote?.playerApi?.skipNext() }
    fun skipPrevious() { remote?.playerApi?.skipPrevious() }
    fun seekTo(positionMs: Long) { remote?.playerApi?.seekTo(positionMs) }
    fun toggleShuffle() { remote?.playerApi?.toggleShuffle() }
    fun toggleRepeat() { remote?.playerApi?.toggleRepeat() }

    // ---- Internals -------------------------------------------------------------------------

    private fun onConnected(appRemote: SpotifyAppRemote) {
        playerSubscription?.cancel()
        val subscription = appRemote.playerApi.subscribeToPlayerState()
        subscription.setEventCallback { state -> handlePlayerState(state) }
        subscription.setErrorCallback { Log.w(TAG, "Player state subscription error: ${it.javaClass.simpleName}") }
        playerSubscription = subscription

        appRemote.userApi.capabilities
            .setResultCallback { caps -> _canPlayOnDemand.value = caps.canPlayOnDemand }
            .setErrorCallback { Log.w(TAG, "Capabilities unavailable: ${it.javaClass.simpleName}") }

        pendingPlayUri?.let { uri ->
            pendingPlayUri = null
            play(uri)
        }
    }

    private fun handlePlayerState(state: PlayerState) {
        val t = state.track
        _playback.value = PlaybackState(
            track = t?.let {
                NowPlayingTrack(
                    uri = it.uri,
                    name = it.name.orEmpty(),
                    artist = it.artist?.name.orEmpty(),
                    album = it.album?.name.orEmpty(),
                    durationMs = it.duration,
                    imageUri = it.imageUri?.raw,
                )
            },
            isPlaying = !state.isPaused,
            positionMs = state.playbackPosition,
            capturedAtElapsedMs = SystemClock.elapsedRealtime(),
            isShuffling = state.playbackOptions?.isShuffling ?: false,
            repeatMode = state.playbackOptions?.repeatMode ?: 0,
        )

        val raw = t?.imageUri?.raw
        if (raw != lastImageUri) {
            lastImageUri = raw
            if (t?.imageUri != null) loadArtwork(t.imageUri) else _artwork.value = null
        }
    }

    private fun loadArtwork(uri: ImageUri) {
        remote?.imagesApi?.getImage(uri, Image.Dimension.LARGE)
            ?.setResultCallback { bitmap -> _artwork.value = bitmap.asImageBitmap() }
            ?.setErrorCallback { Log.w(TAG, "Artwork load failed: ${it.javaClass.simpleName}") }
    }

    private fun reportError(prefix: String, error: Throwable) {
        val premiumNeeded = error.message?.contains("PREMIUM", ignoreCase = true) == true
        _messages.tryEmit(
            if (premiumNeeded) "$prefix: Spotify Premium is required for on-demand playback."
            else "$prefix.",
        )
    }

    /** Maps SDK exceptions by name so a renamed class never breaks compilation. */
    private fun Throwable.toFailure(): Connection.Failed = when (javaClass.simpleName) {
        "CouldNotFindSpotifyApp" ->
            Connection.Failed("The Spotify app isn't installed. Install it to play music.", spotifyMissing = true)
        "NotLoggedInException" ->
            Connection.Failed("Open the Spotify app and log in, then try again.")
        "UserNotAuthorizedException", "AuthenticationFailedException" ->
            Connection.Failed(
                "Spotify didn't authorize this app. Check the Client ID, package name, " +
                    "SHA-1 fingerprint and redirect URI in the Spotify dashboard.",
            )
        "OfflineModeException" ->
            Connection.Failed("Spotify is in offline mode. Go online in the Spotify app to connect.")
        else -> Connection.Failed(message ?: "Couldn't connect to Spotify.")
    }

    private companion object {
        const val TAG = "SpotifyRemote"
        const val CONNECT_TIMEOUT_MS = 12_000L
        const val CONNECT_TIMEOUT_MESSAGE =
            "Spotify didn't respond. Open the Spotify app once, turn on Settings > Playback > " +
                "Device Broadcast Status, and make sure this app's SHA-1 is registered under " +
                "Android Packages in the Spotify dashboard. Then tap Retry."
    }
}
