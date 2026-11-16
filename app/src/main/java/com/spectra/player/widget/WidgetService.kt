package com.spectra.player.widget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import com.spectra.player.AppContainer
import com.spectra.player.MainActivity
import com.spectra.player.R
import com.spectra.player.SpectraApp
import com.spectra.player.auth.AuthState
import com.spectra.player.player.SpotifyRemoteManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Keeps the home screen widget live. Android only lets an app keep working with no screen open
 * from a foreground service, so this runs while music plays (and for a few minutes after it
 * stops), follows Spotify, and moves the widget's progress line once a second while the phone's
 * screen is on. The widget's buttons arrive here as commands.
 */
class WidgetService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container: AppContainer get() = (application as SpectraApp).container
    private val art = MutableStateFlow<Bitmap?>(lastArt)
    private var line: Bitmap? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        goForeground()
        container.remote.backgroundWatch = true
        ensureConnected()
        scope.launch { followArtwork() }
        scope.launch { followSong() }
        scope.launch { drawProgress() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        val remote = container.remote
        when (intent?.action) {
            ACTION_TOGGLE -> {
                ensureConnected()
                remote.togglePlayPauseAnywhere()
            }
            ACTION_NEXT -> {
                ensureConnected()
                remote.skipNext()
            }
            ACTION_PREVIOUS -> {
                ensureConnected()
                remote.skipPrevious()
            }
            ACTION_SEEK -> {
                val duration = remote.playback.value.track?.durationMs ?: 0L
                val step = intent.getIntExtra(EXTRA_STEP, -1)
                if (duration > 0 && step >= 0) {
                    remote.seekTo((WidgetMath.seekFraction(step) * duration).toLong())
                    showProgress()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        container.remote.backgroundWatch = false
        scope.cancel()
        SpectraWidget.drawStill(this)
        super.onDestroy()
    }

    private fun signedIn() = container.authManager.state.value is AuthState.SignedIn

    private fun ensureConnected() {
        if (!signedIn()) return
        val state = container.remote.connection.value
        if (state is SpotifyRemoteManager.Connection.Disconnected || state is SpotifyRemoteManager.Connection.Failed) {
            container.remote.connect()
        }
    }

    /** Redraws song, cover, buttons and colours whenever one of them changes. */
    private suspend fun followSong() {
        // While paused the position only changes when someone moves it (a seek in the app), so it
        // counts as a change too; while playing the progress loop moves the line.
        data class Shown(val uri: String?, val title: String?, val artist: String?, val playing: Boolean, val pausedAt: Long)
        combine(
            container.remote.playback.map {
                Shown(it.track?.uri, it.track?.name, it.track?.artist, it.isPlaying, if (it.isPlaying) -1L else it.positionMs)
            }
                .distinctUntilChanged(),
            container.appPrefs.settings,
            art,
            container.authManager.state.map { it is AuthState.SignedIn }.distinctUntilChanged(),
        ) { _, _, _, _ -> Unit }.collectLatest {
            SpectraWidget.drawStill(this)
        }
    }

    /** The cover from Spotify when it has one, otherwise fetched from the song's picture link. */
    private suspend fun followArtwork() {
        combine(
            container.remote.artwork,
            container.remote.playback.map { it.track?.uri to it.track?.imageUrl }.distinctUntilChanged(),
        ) { fromSpotify, (uri, url) -> Triple(fromSpotify, uri, url) }.collectLatest { (fromSpotify, uri, url) ->
            val bitmap = when {
                fromSpotify != null -> runCatching { fromSpotify.asAndroidBitmap() }.getOrNull()
                url != null -> withContext(Dispatchers.IO) {
                    runCatching {
                        val request = ImageRequest.Builder(this@WidgetService).data(url).size(256).allowHardware(false).build()
                        imageLoader.execute(request).drawable?.toBitmap()
                    }.getOrNull()
                }
                else -> null
            }
            // Keep the old cover while the same song's new one is still loading.
            if (bitmap != null || uri != artUri) {
                artUri = uri
                lastArt = bitmap
                art.value = bitmap
            }
        }
    }

    /** Moves the progress line once a second, and decides when this service should stop. */
    private suspend fun drawProgress() {
        val remote = container.remote
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        var idleSince = SystemClock.elapsedRealtime()
        while (scope.isActive) {
            if (WidgetRenderer.widgetIds(this).isEmpty()) {
                stopSelf()
                return
            }
            val playback = remote.playback.value
            val now = SystemClock.elapsedRealtime()
            val playing = playback.isPlaying && playback.track != null
            // Keep going while Spectra is open, so changes made in the app always reach the widget.
            if (playing || remote.appVisible) {
                idleSince = now
            } else if (now - idleSince > IDLE_STOP_MS) {
                stopSelf()
                return
            }
            if (playing && power.isInteractive) showProgress()
            delay(TICK_MS)
        }
    }

    private fun showProgress() {
        val playback = container.remote.playback.value
        val duration = playback.track?.durationMs ?: 0L
        val fraction = if (duration > 0) playback.currentPosition().toFloat() / duration else 0f
        line = WidgetRenderer.renderProgress(this, container.appPrefs.settings.value, fraction, line)
    }

    private fun goForeground() {
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Home screen widget", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "Shown while the Spectra widget follows what Spotify is playing."
                    setShowBadge(false)
                },
            )
            val open = PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_widget_play)
                .setContentTitle("Spectra widget")
                .setContentText("Following what Spotify is playing")
                .setContentIntent(open)
                .setOngoing(true)
                .build()
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't go to the foreground: ${e.javaClass.simpleName}")
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.spectra.player.widget.TOGGLE"
        const val ACTION_NEXT = "com.spectra.player.widget.NEXT"
        const val ACTION_PREVIOUS = "com.spectra.player.widget.PREVIOUS"
        const val ACTION_SEEK = "com.spectra.player.widget.SEEK"
        const val EXTRA_STEP = "step"

        private const val TAG = "SpectraWidget"
        private const val CHANNEL = "widget"
        private const val NOTIFICATION_ID = 41
        private const val TICK_MS = 1_000L
        private const val IDLE_STOP_MS = 3 * 60_000L

        /** The last cover drawn, kept so a still redraw (resize, service stop) doesn't lose it. */
        @Volatile
        var lastArt: Bitmap? = null
            private set
        @Volatile
        private var artUri: String? = null

        /** Starts the service when a widget is on the home screen. Android may refuse from the background. */
        fun startIfNeeded(context: Context) {
            if (WidgetRenderer.widgetIds(context).isEmpty()) return
            try {
                context.startForegroundService(Intent(context, WidgetService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Widget service not started: ${e.javaClass.simpleName}")
            }
        }
    }
}
