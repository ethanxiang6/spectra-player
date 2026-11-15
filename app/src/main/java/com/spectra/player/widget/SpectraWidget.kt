package com.spectra.player.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import com.spectra.player.SpectraApp

/**
 * The 4 by 1 home screen widget. This class only draws a still picture when Android asks for one
 * (the widget was added, resized, or the phone restarted); [WidgetService] does the live work.
 */
class SpectraWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        drawStill(context)
        WidgetService.startIfNeeded(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        drawStill(context)
    }

    companion object {
        /** Draws the widget from what the app knows right now, with the bars at rest. */
        fun drawStill(context: Context) {
            val container = (context.applicationContext as? SpectraApp)?.container ?: return
            val playback = container.remote.playback.value
            val track = playback.track
            val signedIn = container.authManager.state.value is com.spectra.player.auth.AuthState.SignedIn
            val duration = track?.durationMs ?: 0L
            WidgetRenderer.renderAll(
                context,
                WidgetContent(
                    title = track?.name ?: if (signedIn) "Spectra" else "Open Spectra to sign in",
                    artist = track?.artist ?: if (signedIn) "Tap play" else "",
                    playing = playback.isPlaying && track != null,
                    art = WidgetService.lastArt,
                    settings = container.appPrefs.settings.value,
                ),
                levels = FloatArray(WidgetRenderer.BAR_COUNT),
                fraction = if (duration > 0) playback.currentPosition().toFloat() / duration else 0f,
            )
        }
    }
}
