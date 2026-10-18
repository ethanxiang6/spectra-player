package com.spectra.player.visualizer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.os.Build

/**
 * Lets the visualizer listen to Spotify only.
 *
 * Spotify announces its audio session to equalizer apps with
 * [AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION]; with that id the visualizer taps
 * Spotify's own audio instead of everything the phone plays. The id is remembered across restarts.
 */
object SpotifyAudioSession {
    private const val SPOTIFY = "com.spotify.music"
    private const val PREFS = "spotify_audio_session"
    private const val KEY = "session"

    @Volatile
    var sessionId: Int? = null
        private set

    private var appContext: Context? = null

    /** Called once from the Application: restores the last id and listens for new ones. */
    fun init(context: Context) {
        appContext = context.applicationContext
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, 0)
        if (stored != 0) sessionId = stored
        val filter = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        val receiver = Receiver()
        if (Build.VERSION.SDK_INT >= 33) {
            context.applicationContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.applicationContext.registerReceiver(receiver, filter)
        }
    }

    /** The stored id stopped working (Spotify restarted): forget it until Spotify announces a new one. */
    fun invalidate() {
        sessionId = null
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.remove(KEY)?.apply()
    }

    internal fun handle(context: Context, intent: Intent) {
        if (intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) != SPOTIFY) return
        val id = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        when (intent.action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> if (id != 0) {
                sessionId = id
                prefs.edit().putInt(KEY, id).apply()
            }
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> if (sessionId == id) {
                sessionId = null
                prefs.edit().remove(KEY).apply()
            }
        }
    }

    /**
     * Without Spotify's session id the only option is the phone's overall audio. That is used
     * only while no other app is playing media, so other sounds never drive the visualizer.
     */
    fun onlyOnePlayerActive(context: Context): Boolean {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val media = am.activePlaybackConfigurations.count {
            val usage = it.audioAttributes.usage
            usage == AudioAttributes.USAGE_MEDIA || usage == AudioAttributes.USAGE_GAME ||
                usage == AudioAttributes.USAGE_UNKNOWN
        }
        return media <= 1
    }

    class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = handle(context, intent)
    }
}
