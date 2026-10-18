package com.spectra.player.visualizer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager

/**
 * Keeps the visualizer to Spotify only. The capture hears the phone's overall audio, so it is used
 * only while Spotify is playing and no other app is playing media; otherwise the visualizer falls
 * back to its timing-based animation and other sounds never drive it.
 */
object SpotifyAudioSession {
    fun onlyOnePlayerActive(context: Context): Boolean {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val media = am.activePlaybackConfigurations.count {
            val usage = it.audioAttributes.usage
            usage == AudioAttributes.USAGE_MEDIA || usage == AudioAttributes.USAGE_GAME ||
                usage == AudioAttributes.USAGE_UNKNOWN
        }
        return media <= 1
    }
}
