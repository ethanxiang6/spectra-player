package com.spectra.player.data

import com.spectra.player.data.remote.AudioFeaturesDto
import com.spectra.player.data.remote.ImageDto
import com.spectra.player.data.remote.PlaylistDto
import com.spectra.player.data.remote.TrackDto
import com.spectra.player.data.remote.UserDto
import kotlin.math.abs

/** Picks the image closest to ~300px: sharp on a list row, cheap to decode. */
private fun List<ImageDto>?.bestImage(target: Int = 300): String? =
    this?.minByOrNull { abs((it.width ?: target) - target) }?.url

fun TrackDto.toDomain() = Track(
    id = id,
    uri = uri,
    name = name,
    artists = artists.orEmpty().joinToString(", ") { it.name },
    album = album?.name.orEmpty(),
    imageUrl = album?.images.bestImage(),
    durationMs = durationMs,
    isLocal = isLocal,
    albumUri = album?.uri,
    trackNumber = trackNumber,
    discNumber = discNumber,
)

fun PlaylistDto.toDomain() = Playlist(
    id = id,
    name = name,
    owner = owner?.displayName ?: owner?.id.orEmpty(),
    imageUrl = images.bestImage(),
    trackCount = (items ?: tracks)?.total,
)

fun UserDto.toDomain() = UserProfile(
    id = id,
    displayName = displayName ?: id,
    imageUrl = images.bestImage(120),
    product = product,
)

fun AudioFeaturesDto.toDomain() = AudioProfile(
    tempo = tempo,
    energy = energy,
    loudnessDb = loudness,
    danceability = danceability,
    valence = valence,
    fromSpotify = true,
)
