package com.spectra.player.ui.navigation

import kotlinx.serialization.Serializable

/** Type-safe Navigation Compose destinations (Navigation 2.8+). */
sealed interface Route {
    @Serializable data object Login : Route
    @Serializable data object Library : Route
    @Serializable data class PlaylistDetail(
        val id: String,
        val name: String,
        val imageUrl: String? = null,
        val owner: String = "",
        val trackCount: Int = -1,
        /** [KIND_PLAYLIST] or [KIND_ALBUM]: an album opens in the same page. */
        val kind: String = KIND_PLAYLIST,
    ) : Route
    @Serializable data class Artist(val id: String, val name: String, val imageUrl: String? = null) : Route
    @Serializable data object Colors : Route
    @Serializable data object NowPlaying : Route
    @Serializable data object Search : Route
    @Serializable data object Settings : Route

    companion object {
        const val KIND_PLAYLIST = "playlist"
        const val KIND_ALBUM = "album"
    }
}
