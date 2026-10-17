package com.spectra.player.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Wire models for the Spotify Web API. Everything optional is nullable/defaulted because
 * Spotify has been trimming fields (Feb 2026 dev-mode changes removed `product` from /me,
 * renamed playlist `tracks` -> `items`, etc.) and the Json config ignores unknown keys.
 */

@Serializable
data class ImageDto(val url: String, val width: Int? = null, val height: Int? = null)

@Serializable
data class PagingDto<T>(
    val items: List<T> = emptyList(),
    val total: Int = 0,
    val next: String? = null,
)

@Serializable
data class UserDto(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    val images: List<ImageDto>? = null,
    /** "premium" | "free" | "open". Not returned to dev-mode apps since Feb 2026. */
    val product: String? = null,
)

@Serializable
data class ArtistDto(val name: String = "")

@Serializable
data class AlbumDto(val name: String = "", val images: List<ImageDto>? = null)

@Serializable
data class TrackDto(
    val id: String? = null,
    val uri: String,
    val name: String = "",
    val artists: List<ArtistDto>? = null,
    val album: AlbumDto? = null,
    @SerialName("duration_ms") val durationMs: Long = 0,
    @SerialName("is_local") val isLocal: Boolean = false,
)

@Serializable
data class SavedTrackDto(
    @SerialName("added_at") val addedAt: String? = null,
    val track: TrackDto? = null,
)

@Serializable
data class OwnerDto(
    val id: String? = null,
    @SerialName("display_name") val displayName: String? = null,
)

@Serializable
data class CountDto(val total: Int = 0)

@Serializable
data class PlaylistDto(
    val id: String,
    val name: String = "",
    val owner: OwnerDto? = null,
    val images: List<ImageDto>? = null,
    /** Pre-2026 shape. */
    val tracks: CountDto? = null,
    /** Shape after the Feb 2026 rename. */
    val items: CountDto? = null,
)

/** `/playlists/{id}/items` wraps each entry as `item` (older responses used `track`). */
@Serializable
data class PlaylistItemDto(
    val item: TrackDto? = null,
    val track: TrackDto? = null,
)

/** Deprecated for new apps (Nov 2024): expect 403, the app then falls back to a default profile. */
@Serializable
data class AudioFeaturesDto(
    val tempo: Float = 110f,
    val energy: Float = 0.6f,
    val loudness: Float = -8f,
    val danceability: Float = 0.6f,
    val valence: Float = 0.5f,
)

// ---- Playback (Web API fallback when App Remote refuses to start a track) ----

@Serializable
data class DevicesDto(val devices: List<DeviceDto> = emptyList())

@Serializable
data class DeviceDto(
    val id: String? = null,
    val name: String = "",
    val type: String = "",
    @SerialName("is_active") val isActive: Boolean = false,
    @SerialName("is_restricted") val isRestricted: Boolean = false,
)

@Serializable
data class PlayBodyDto(
    val uris: List<String>? = null,
    @SerialName("context_uri") val contextUri: String? = null,
)
