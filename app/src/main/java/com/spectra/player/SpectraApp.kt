package com.spectra.player

import android.app.Application

class SpectraApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        com.spectra.player.visualizer.SpotifyAudioSession.init(this)
    }
}
