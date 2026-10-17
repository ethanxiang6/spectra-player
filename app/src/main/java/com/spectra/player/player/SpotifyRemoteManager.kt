package com.spectra.player.player

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import java.lang.ref.WeakReference
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
import kotlinx.coroutines.launch
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
class SpotifyRemoteManager(
    private val context: Context,
    private val webPlayback: WebPlaybackFallback? = null,
) {
    private val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate,
    )

    sealed interface Connection {
        data object Disconnected : Connection
        data object Connecting : Connection
        data object Connected : Connection
        data class Failed(val message: String, val spotifyMissing: Boolean = false, val details: String? = null) : Connection
    }

    private var remote: SpotifyAppRemote? = null
    private var playerSubscription: Subscription<PlayerState>? = null
    private var lastImageUri: String? = null
    private var pendingPlayUri: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var attempt = 0

    // Spotify's own sample connects with an Activity context; the "allow this app" sheet and the
    // service binding are more reliable that way than with the bare application context.
    private var foreground: WeakReference<Activity>? = null

    init {
        (context.applicationContext as? Application)?.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(a: Activity, b: Bundle?) { foreground = WeakReference(a) }
                override fun onActivityStarted(a: Activity) { foreground = WeakReference(a) }
                override fun onActivityResumed(a: Activity) { foreground = WeakReference(a) }
                override fun onActivityPaused(a: Activity) = Unit
                override fun onActivityStopped(a: Activity) = Unit
                override fun onActivitySaveInstanceState(a: Activity, b: Bundle) = Unit
                override fun onActivityDestroyed(a: Activity) {
                    if (foreground?.get() === a) foreground = null
                }
            },
        )
    }

    private fun collectLog(): String = try {
        val proc = ProcessBuilder("logcat", "-d", "-t", "600", "-v", "time").redirectErrorStream(true).start()
        val keep = Regex("(?i)spotify|appremote|remote|bind|service|auth|exception|W/|E/")
        val lines = proc.inputStream.bufferedReader().readLines()
            .filter { keep.containsMatchIn(it) && !it.contains("Choreographer") }
            .takeLast(25)
            .map { it.take(170) }
        if (lines.isEmpty()) "(no log lines available)" else lines.joinToString("\n")
    } catch (e: Exception) {
        "(log unavailable: ${e.javaClass.simpleName})"
    }

    private fun spotifyAppInfo(): String = try {
        val v = context.packageManager.getPackageInfo("com.spotify.music", 0).versionName
        "Spotify app $v is installed."
    } catch (e: Exception) {
        "The Spotify app was not found on this phone."
    }

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

    /**
     * The first connection needs the user's approval, which the Spotify app shows in its own
     * screen. Recent Android versions block that screen when Spotify isn't visible ("Background
     * activity launch blocked"), so the connection just waits. This brings Spotify to the front
     * first, then connects, so Spotify is allowed to show the approval. Approval is remembered.
     */
    fun connectViaSpotify(activity: Activity) {
        val launch = context.packageManager.getLaunchIntentForPackage(SPOTIFY_PACKAGE)
        if (launch == null) {
            _connection.value = Connection.Failed(
                "The Spotify app isn't installed. Install it to play music.",
                spotifyMissing = true,
            )
            return
        }
        try {
            activity.startActivity(launch)
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't open Spotify: ${e.javaClass.simpleName}")
        }
        _connection.value = Connection.Connecting
        mainHandler.postDelayed({ connect(force = true) }, SPOTIFY_HANDOFF_DELAY_MS)
    }

    fun connect(force: Boolean = false) {
        if (_connection.value == Connection.Connected) return
        if (!force && _connection.value == Connection.Connecting) return
        _connection.value = Connection.Connecting
        val thisAttempt = ++attempt
        // The SDK can wait forever when the Spotify app never answers (e.g. the fingerprint isn't
        // registered, or Device Broadcast Status is off). Turn that into an actionable message.
        mainHandler.postDelayed({
            if (thisAttempt == attempt && _connection.value == Connection.Connecting) {
                _connection.value = Connection.Failed("$CONNECT_TIMEOUT_MESSAGE ${spotifyAppInfo()}")
                // Own-process log lines (the SDK logs here) so a screenshot shows what it was doing.
                Thread {
                    val log = collectLog()
                    mainHandler.post {
                        val cur = _connection.value
                        if (thisAttempt == attempt && cur is Connection.Failed) {
                            _connection.value = cur.copy(details = log)
                        }
                    }
                }.start()
            }
        }, CONNECT_TIMEOUT_MS)

        val params = ConnectionParams.Builder(AuthConfig.clientId)
            .setRedirectUri(AuthConfig.redirectUri)
            .showAuthView(true) // Spotify shows its one-time "allow this app" sheet
            .build()

        SpotifyAppRemote.connect(
            foreground?.get()?.takeUnless { it.isFinishing || it.isDestroyed } ?: context,
            params,
            object : Connector.ConnectionListener {
                override fun onConnected(appRemote: SpotifyAppRemote) {
                    if (remote != null) {
                        // An earlier attempt already connected; drop the duplicate.
                        SpotifyAppRemote.disconnect(appRemote)
                        return
                    }
                    attempt++
                    remote = appRemote
                    _connection.value = Connection.Connected
                    onConnected(appRemote)
                }

                override fun onFailure(error: Throwable) {
                    Log.w(TAG, "App Remote connect failed: ${error.javaClass.simpleName}")
                    // A newer attempt (e.g. via the Spotify hand-off) supersedes this one.
                    if (thisAttempt != attempt || remote != null) return
                    attempt++
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
        r.playerApi.play(uri).setErrorCallback { error -> playViaWebApi(uri, error) }
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

    /** App Remote refused the play request: try Spotify Connect via the Web API, then explain. */
    private fun playViaWebApi(uri: String, appRemoteError: Throwable) {
        val sdkReason = describeSdkError(appRemoteError)
        Log.w(TAG, "App Remote play failed: $sdkReason")
        val fallback = webPlayback
        if (fallback == null) {
            _messages.tryEmit("Couldn't start playback ($sdkReason).")
            return
        }
        scope.launch {
            val webReason = fallback.play(uri) ?: return@launch // started via the Web API
            _messages.emit("Couldn't start playback. Spotify app: $sdkReason. Web API: $webReason")
        }
    }

    private fun describeSdkError(error: Throwable): String {
        val reasonUri = (error as? com.spotify.protocol.client.error.RemoteClientException)?.reasonUri
        return listOfNotNull(error.javaClass.simpleName, reasonUri, error.message?.take(160))
            .distinct()
            .joinToString(" / ")
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
        else -> Connection.Failed(
            "Couldn't connect to Spotify (${javaClass.simpleName}${message?.let { ": $it" } ?: ""}).",
        )
    }

    private companion object {
        const val TAG = "SpotifyRemote"
        // The SDK itself waits up to 30 s to bind and 30 s for Spotify's hello reply, then reports a
        // specific error. Wait slightly longer than both so that real error wins over our generic one.
        const val CONNECT_TIMEOUT_MS = 65_000L
        const val CONNECT_TIMEOUT_MESSAGE =
            "Spotify needs your approval, but Android stopped it from showing the approval " +
                "screen. Tap \"Approve in Spotify\", allow Spectra there, then come back."
        const val SPOTIFY_PACKAGE = "com.spotify.music"
        const val SPOTIFY_HANDOFF_DELAY_MS = 1_200L
    }
}
