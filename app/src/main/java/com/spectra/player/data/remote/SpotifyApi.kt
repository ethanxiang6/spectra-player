package com.spectra.player.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** https://api.spotify.com/ — the bearer token is attached by [BearerInterceptor]. */
interface SpotifyApi {

    @GET("v1/me")
    suspend fun me(): UserDto

    /** Saved ("Liked") songs. */
    @GET("v1/me/tracks")
    suspend fun savedTracks(
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): PagingDto<SavedTrackDto>

    @GET("v1/me/playlists")
    suspend fun playlists(
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): PagingDto<PlaylistDto>

    /** Replaces `/playlists/{id}/tracks`. Only returns items for playlists the user owns or collaborates on. */
    @GET("v1/playlists/{id}/items")
    suspend fun playlistItems(
        @Path("id") playlistId: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): PagingDto<PlaylistItemDto>

    /** Deprecated for apps created after 27 Nov 2024; used only to shape the simulated visualizer. */
    @GET("v1/audio-features/{id}")
    suspend fun audioFeatures(@Path("id") trackId: String): AudioFeaturesDto
}
