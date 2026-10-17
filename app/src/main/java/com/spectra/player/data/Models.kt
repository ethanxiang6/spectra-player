package com.spectra.player.data

import kotlinx.serialization.Serializable

/** UI-facing models. [Track] and [Playlist] are @Serializable so the offline cache can store them. */

@Serializable
data class Track(
    val id: String?,
    val uri: String,
    val name: String,
    val artists: String,
    val album: String,
    val imageUrl: String?,
    val durationMs: Long,
    val isLocal: Boolean = false,
    /** Album context and position, used to keep search results playing within their album. */
    val albumUri: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
) {
    val subtitle: String get() = listOf(artists, album).filter { it.isNotBlank() }.joinToString(" · ")
}

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val owner: String,
    val imageUrl: String?,
    val trackCount: Int?,
)

data class UserProfile(
    val id: String,
    val displayName: String,
    val imageUrl: String?,
    /** Raw `product` from /me, when Spotify still returns it. */
    val product: String?,
)

enum class PremiumStatus {
    PREMIUM, FREE, UNKNOWN;

    companion object {
        /**
         * Premium detection with two independent signals:
         *  1. Web API `GET /me` -> `product` ("premium" | "free" | "open"). Spotify removed this
         *     field for development-mode apps in Feb 2026, so it is often null now.
         *  2. App Remote `userApi.capabilities.canPlayOnDemand`, true only on Premium.
         */
        fun resolve(product: String?, canPlayOnDemand: Boolean?): PremiumStatus =
            when (product?.lowercase()) {
                "premium" -> PREMIUM
                "free", "open" -> FREE
                else -> when (canPlayOnDemand) {
                    true -> PREMIUM
                    false -> FREE
                    null -> UNKNOWN
                }
            }
    }
}

/** What the visualizer's simulated mode feeds on. */
data class AudioProfile(
    val tempo: Float,
    val energy: Float,
    val loudnessDb: Float,
    val danceability: Float,
    val valence: Float,
    val fromSpotify: Boolean,
) {
    companion object {
        val Default = AudioProfile(
            tempo = 110f, energy = 0.6f, loudnessDb = -8f,
            danceability = 0.6f, valence = 0.5f, fromSpotify = false,
        )
    }
}

fun Long.formatTime(): String {
    val totalSeconds = (this / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
