package com.spectra.player.ui.navigation

import kotlinx.serialization.Serializable

/** Type-safe Navigation Compose destinations (Navigation 2.8+). */
sealed interface Route {
    @Serializable data object Login : Route
    @Serializable data object Library : Route
    @Serializable data class PlaylistDetail(val id: String, val name: String) : Route
    @Serializable data object NowPlaying : Route
}
