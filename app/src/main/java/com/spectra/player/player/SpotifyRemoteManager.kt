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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
        /** Spotify hasn't approved Spectra yet; the approval screen must be shown with Spotify visible. */
        data object NeedsApproval : Connection
        data class Failed(val message: String, val spotifyMissing: Boolean = false, val details: String? = null) : Connection
    }

    private var remote: SpotifyAppRemote? = null
    private var playerSubscription: Subscription<PlayerState>? = null
    private var lastImageUri: String? = null
    private var pendingPlay: PendingPlay? = null

    private data class PendingPlay(val uri: String, val context: PlayContext?)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var attempt = 0

    // The SDK keeps a single static connector, so only one connect may run at a time. Starting a
    // second one while the first is pending corrupts both (calls then fail with
    // SpotifyDisconnectedException).
    private var inFlight = false
    private var connectAgainWithAuthView: Boolean? = null
    private var lastConnectedAt = 0L

    /** True once App Remote has delivered a player state on the current connection. */
    private var appRemoteStateSeen = false
    private var startedActivities = 0

    // Spotify's own sample connects with an Activity context; the "allow this app" sheet and the
    // service binding are more reliable that way than with the bare application context.
    private var foreground: WeakReference<Activity>? = null

    init {
        (context.applicationContext as? Application)?.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(a: Activity, b: Bundle?) { foreground = WeakReference(a) }
                override fun onActivityStarted(a: Activity) {
                    foreground = WeakReference(a)
                    startedActivities++
                    if (startedActivities == 1) scope.launch { refreshFromWeb() }
                }
                override fun onActivityResumed(a: Activity) { foreground = WeakReference(a) }
                override fun onActivityPaused(a: Activity) = Unit
                override fun onActivityStopped(a: Activity) {
                    startedActivities = (startedActivities - 1).coerceAtLeast(0)
                }
                override fun onActivitySaveInstanceState(a: Activity, b: Bundle) = Unit
                override fun onActivityDestroyed(a: Activity) {
                    if (foreground?.get() === a) foreground = null
                }
            },
        )
        // Backup "now playing" feed: if App Remote isn't delivering player state (not connected,
        // or its subscription is silent), ask the Web API what's playing while the app is visible.
        scope.launch {
            while (isActive) {
                delay(WEB_POLL_MS)
                if (startedActivities > 0 && !appRemoteStateSeen) refreshFromWeb()
            }
        }
    }

    private suspend fun refreshFromWeb() {
        if (appRemoteStateSeen) return
        val state = webPlayback?.currentlyPlaying() ?: return
        if (appRemoteStateSeen) return
        val previous = _playback.value
        if (previous.track?.uri != state.track?.uri) {
            _artwork.value = null
            lastImageUri = null
        }
        _playback.value = state.copy(isShuffling = previous.isShuffling, repeatMode = previous.repeatMode)
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
        mainHandler.postDelayed({ requestConnect(showAuthView = true) }, SPOTIFY_HANDOFF_DELAY_MS)
    }

    /**
     * Connects without asking Spotify to show its approval screen (that screen is blocked unless
     * Spotify is visible). If Spectra isn't approved yet this ends in [Connection.NeedsApproval].
     */
    fun connect(force: Boolean = false) {
        if (!force && _connection.value == Connection.NeedsApproval) return
        requestConnect(showAuthView = false)
    }

    private fun requestConnect(showAuthView: Boolean) {
        val live = remote
        if (live != null && live.isConnected) {
            _connection.value = Connection.Connected
            return
        }
        if (live != null) dropRemote()
        _connection.value = Connection.Connecting
        if (inFlight) {
            // Let the running attempt finish, then go again (with the approval screen if asked).
            connectAgainWithAuthView = (connectAgainWithAuthView ?: false) || showAuthView
            return
        }
        startSdkConnect(showAuthView)
    }

    private fun startSdkConnect(showAuthView: Boolean) {
        inFlight = true
        connectAgainWithAuthView = null
        val thisAttempt = ++attempt
        mainHandler.postDelayed({
            if (thisAttempt == attempt && _connection.value == Connection.Connecting) {
                _connection.value = Connection.Failed("$CONNECT_TIMEOUT_MESSAGE ${spotifyAppInfo()}")
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
            .showAuthView(showAuthView)
            .build()

        SpotifyAppRemote.connect(
            foreground?.get()?.takeUnless { it.isFinishing || it.isDestroyed } ?: context,
            params,
            object : Connector.ConnectionListener {
                private var connected: SpotifyAppRemote? = null

                override fun onConnected(appRemote: SpotifyAppRemote) {
                    mainHandler.post {
                        connected = appRemote
                        inFlight = false
                        attempt++
                        remote = appRemote
                        lastConnectedAt = SystemClock.elapsedRealtime()
                        connectAgainWithAuthView = null
                        _connection.value = Connection.Connected
                        onConnected(appRemote)
                    }
                }

                override fun onFailure(error: Throwable) {
                    mainHandler.post {
                        Log.w(TAG, "App Remote failure: ${error.javaClass.simpleName}")
                        val wasConnected = connected
                        if (wasConnected != null) {
                            // The live connection was closed by Spotify (or by us).
                            if (remote === wasConnected) onConnectionLost()
                            return@post
                        }
                        inFlight = false
                        val again = connectAgainWithAuthView
                        if (again != null) {
                            startSdkConnect(again)
                            return@post
                        }
                        if (thisAttempt == attempt) {
                            attempt++
                            _connection.value = error.toFailure()
                        }
                    }
                }
            },
        )
    }

    private fun onConnectionLost() {
        dropRemote()
        val connectedFor = SystemClock.elapsedRealtime() - lastConnectedAt
        if (connectedFor > AUTO_RECONNECT_MIN_UPTIME_MS) {
            requestConnect(showAuthView = false)
        } else {
            _connection.value = Connection.Failed(
                "Spotify closed the connection right after it opened. Tap Retry, or Approve in Spotify.",
            )
        }
    }

    /** Forgets the current App Remote instance without touching the SDK's shared connector. */
    private fun dropRemote() {
        appRemoteStateSeen = false
        playerSubscription?.cancel()
        playerSubscription = null
        remote = null
        _connection.value = Connection.Disconnected
    }

    fun disconnect() {
        attempt++
        appRemoteStateSeen = false
        playerSubscription?.cancel()
        playerSubscription = null
        remote?.let { SpotifyAppRemote.disconnect(it) }
        remote = null
        lastImageUri = null
        pendingPlay = null
        connectAgainWithAuthView = null
        _playback.value = PlaybackState.Idle
        _artwork.value = null
        _canPlayOnDemand.value = null
        _connection.value = Connection.Disconnected
    }

    // ---- Transport -------------------------------------------------------------------------

    /** Plays a Spotify URI (track, album or playlist). Connects first if needed. */
    /**
     * Plays [uri]. With a [context] (a playlist, the Liked Songs collection or an album) playback
     * starts at that song *inside* the context, so it carries on through the same list instead of
     * Spotify picking songs on its own.
     */
    fun play(uri: String, hint: NowPlayingTrack? = null, context: PlayContext? = null) {
        if (hint != null) showProvisional(hint)
        val r = remote
        if (r == null || !r.isConnected) {
            pendingPlay = PendingPlay(uri, context)
            if (_connection.value == Connection.NeedsApproval) {
                _messages.tryEmit("Tap \"Approve in Spotify\" first, then try again.")
            } else {
                connect(force = true)
            }
            return
        }
        if (context != null && context.index == null) {
            // App Remote can only start a context by index; let the Web API find the song by URI.
            playViaWebApi(uri, context, appRemoteError = null)
            return
        }
        val index = context?.index
        val call = if (context != null && index != null) {
            r.playerApi.skipToIndex(context.uri, index)
        } else {
            r.playerApi.play(uri)
        }
        val isList = context != null || !uri.startsWith("spotify:track:")
        call.setResultCallback { if (isList) mainHandler.post { stayInContext(r) } }
        call.setErrorCallback { error ->
            mainHandler.post {
                if (error is com.spotify.android.appremote.api.error.SpotifyDisconnectedException ||
                    error is com.spotify.android.appremote.api.error.SpotifyConnectionTerminatedException
                ) {
                    // Stale connection: reconnect and play once connected.
                    if (remote === r) dropRemote()
                    pendingPlay = PendingPlay(uri, context)
                    requestConnect(showAuthView = false)
                } else {
                    playViaWebApi(uri, context, error)
                }
            }
        }
    }

    /**
     * When the end of a playlist is reached, Spotify's Autoplay would continue with recommended
     * songs. Repeat-all keeps playback inside the list instead (only if repeat was off).
     */
    private fun stayInContext(r: SpotifyAppRemote) {
        if (_playback.value.repeatMode == 0 && r.isConnected) r.playerApi.setRepeat(REPEAT_ALL)
    }

    /** Adds a song to Spotify's queue (plays after the current song). */
    fun queue(uri: String, name: String) {
        val r = liveRemote()
        if (r == null) {
            webQueue(uri, name)
            return
        }
        r.playerApi.queue(uri)
            .setResultCallback { _messages.tryEmit("Added \"$name\" to the queue") }
            .setErrorCallback { mainHandler.post { webQueue(uri, name) } }
    }

    private fun webQueue(uri: String, name: String) {
        val web = webPlayback
        scope.launch {
            val ok = web?.queue(uri) ?: false
            _messages.emit(
                if (ok) "Added \"$name\" to the queue"
                else "Couldn't add to the queue. Start playing something first, then try again.",
            )
        }
    }

    /** Shows the tapped song in the bar right away; the real player state replaces it. */
    private fun showProvisional(track: NowPlayingTrack) {
        val previous = _playback.value
        if (previous.track?.uri != track.uri) {
            _artwork.value = null
            lastImageUri = null
        }
        _playback.value = PlaybackState(
            track = track,
            isPlaying = true,
            positionMs = 0,
            capturedAtElapsedMs = SystemClock.elapsedRealtime(),
            isShuffling = previous.isShuffling,
            repeatMode = previous.repeatMode,
        )
    }

    private fun liveRemote(): SpotifyAppRemote? = remote?.takeIf { it.isConnected }

    fun togglePlayPause() {
        val playing = _playback.value.isPlaying
        _playback.value = _playback.value.let {
            it.copy(isPlaying = !playing, positionMs = it.currentPosition(), capturedAtElapsedMs = SystemClock.elapsedRealtime())
        }
        val api = liveRemote()?.playerApi
        if (api != null) {
            if (playing) api.pause() else api.resume()
        } else {
            webCommand { if (playing) it.pause() else it.resume() }
        }
    }

    fun skipNext() {
        liveRemote()?.playerApi?.skipNext() ?: webCommand { it.next() }
    }

    fun skipPrevious() {
        liveRemote()?.playerApi?.skipPrevious() ?: webCommand { it.previous() }
    }

    private fun webCommand(block: suspend (WebPlaybackFallback) -> Boolean) {
        val web = webPlayback ?: return
        scope.launch {
            if (!block(web)) _messages.emit("Spotify didn't respond. Open Spotify and try again.")
            delay(700)
            refreshFromWeb()
        }
    }
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

        pendingPlay?.let { pending ->
            pendingPlay = null
            play(pending.uri, context = pending.context)
        }
    }

    private fun handlePlayerState(state: PlayerState) {
        appRemoteStateSeen = true
        val t = state.track
        val knownUrl = _playback.value.track?.takeIf { it.uri == t?.uri }?.imageUrl
        _playback.value = PlaybackState(
            track = t?.let {
                NowPlayingTrack(
                    uri = it.uri,
                    name = it.name.orEmpty(),
                    artist = it.artist?.name.orEmpty(),
                    album = it.album?.name.orEmpty(),
                    durationMs = it.duration,
                    imageUri = it.imageUri?.raw,
                    imageUrl = knownUrl,
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
            ?.setResultCallback { bitmap ->
                // Trim baked-in borders and centre-crop to a square, off the main thread.
                scope.launch {
                    val art = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        com.spectra.player.ui.image.ArtworkCrop.squareCrop(bitmap).asImageBitmap()
                    }
                    if (lastImageUri == uri.raw) _artwork.value = art
                }
            }
            ?.setErrorCallback { Log.w(TAG, "Artwork load failed: ${it.javaClass.simpleName}") }
    }

    /** App Remote refused the play request: try Spotify Connect via the Web API, then explain. */
    /** App Remote refused (or can't do) the play request: try Spotify Connect via the Web API. */
    private fun playViaWebApi(uri: String, context: PlayContext?, appRemoteError: Throwable?) {
        val sdkReason = appRemoteError?.let { describeSdkError(it) }
        if (sdkReason != null) Log.w(TAG, "App Remote play failed: $sdkReason")
        val fallback = webPlayback
        if (fallback == null) {
            _messages.tryEmit("Couldn't start playback (${sdkReason ?: "no connection"}).")
            return
        }
        scope.launch {
            val webReason = fallback.play(uri, context)
            if (webReason == null) { // started via the Web API
                delay(800)
                refreshFromWeb()
                return@launch
            }
            _messages.emit(
                if (sdkReason != null) "Couldn't start playback. Spotify app: $sdkReason. Web API: $webReason"
                else "Couldn't start playback: $webReason",
            )
        }
    }

    private fun describeSdkError(error: Throwable): String {
        val reasonUri = (error as? com.spotify.protocol.client.error.RemoteClientException)?.reasonUri
        return listOfNotNull(error.javaClass.simpleName, reasonUri, error.message?.take(160))
            .distinct()
            .joinToString(" / ")
    }

    /** Maps SDK exceptions by name so a renamed class never breaks compilation. */
    private fun Throwable.toFailure(): Connection = when (javaClass.simpleName) {
        "CouldNotFindSpotifyApp" ->
            Connection.Failed("The Spotify app isn't installed. Install it to play music.", spotifyMissing = true)
        "NotLoggedInException" ->
            Connection.Failed("Open the Spotify app and log in, then try again.")
        "UserNotAuthorizedException" -> Connection.NeedsApproval
        "AuthenticationFailedException" ->
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
        const val AUTO_RECONNECT_MIN_UPTIME_MS = 5_000L
        const val WEB_POLL_MS = 4_000L
        const val REPEAT_ALL = 2 // com.spotify.protocol.types.Repeat.ALL
    }
}
