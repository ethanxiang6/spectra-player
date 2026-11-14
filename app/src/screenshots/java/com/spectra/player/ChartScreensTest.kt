package com.spectra.player

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.spectra.player.data.Playlist
import com.spectra.player.data.PremiumStatus
import com.spectra.player.data.Track
import com.spectra.player.data.UserProfile
import com.spectra.player.player.NowPlayingTrack
import com.spectra.player.player.PlaybackState
import com.spectra.player.player.SpotifyRemoteManager
import com.spectra.player.ui.library.LibraryActions
import com.spectra.player.ui.library.LibraryContent
import com.spectra.player.ui.library.ListState
import com.spectra.player.ui.library.PlaylistContent
import com.spectra.player.ui.nowplaying.Dusk
import com.spectra.player.ui.nowplaying.SkyBackdrop
import com.spectra.player.ui.search.SearchContent
import com.spectra.player.ui.settings.AppSettingsContent
import com.spectra.player.ui.theme.AppSettings
import com.spectra.player.ui.theme.SpectraTheme
import com.spectra.player.visualizer.Backdrop
import org.junit.Rule
import org.junit.Test

/**
 * Screenshots of the app pages with made-up data, for checking the design before a release.
 * Runs only with `-Pscreenshots` (see app/build.gradle.kts and the Screenshots workflow).
 */
class ChartScreensTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
        theme = "android:Theme.Material.NoActionBar",
        maxPercentDifference = 100.0,
    )

    private val songs = listOf(
        "Blurry Nights" to "BoyWithUke",
        "Past Lives" to "sapientdream",
        "Arcade" to "Duncan Laurence",
        "Monody" to "TheFatRat, Laura Brehm",
        "Heather" to "Conan Gray",
        "Sweater Weather" to "The Neighbourhood",
        "Lovely" to "Billie Eilish, Khalid",
        "Daylight" to "David Kushner",
        "Another Love" to "Tom Odell",
        "Snowfall" to "Øneheart, reidenshi",
        "Glimpse of Us" to "Joji",
    ).mapIndexed { i, (name, artist) ->
        Track("id$i", "spotify:track:$i", name, artist, "Album", null, (150_000L + i * 13_000L))
    }

    private val playlists = listOf(
        "Late Night Drive" to 42, "Chill" to 18, "Gym" to 63, "Rainy Mornings" to 27,
        "Phonk" to 115, "Lo-fi to study to" to 204, "Road trip 2025" to 51, "Sad hours" to 9,
        "Nightcore" to 33, "Anime openings" to 76,
    ).mapIndexed { i, (n, c) -> Playlist("pl$i", n, "Ethan", null, c) }

    private val profile = UserProfile("ethan", "Ethan Xiang", null, "premium")

    private fun playback(playing: Boolean = true) = PlaybackState(
        track = NowPlayingTrack("spotify:track:1", "Past Lives", "sapientdream", "Album", 160_000L, null),
        isPlaying = playing,
        positionMs = 64_000L,
        capturedAtElapsedMs = SystemClock.elapsedRealtime(),
        isShuffling = false,
        repeatMode = 2,
    )

    private fun shot(name: String, mono: Boolean = false, theme: Backdrop = Backdrop.EVENING, content: @Composable () -> Unit) {
        Dusk.mono = mono
        paparazzi.snapshot(name) {
            SpectraTheme(theme = theme, monochrome = mono) {
                Box(Modifier.fillMaxSize()) {
                    SkyBackdrop(
                        backdrop = if (mono) Backdrop.BLACK else theme,
                        showStars = true,
                        artwork = null,
                        imageUrl = null,
                        time = { 0f },
                        energy = { 0f },
                        modifier = Modifier.fillMaxSize(),
                    )
                    content()
                }
            }
        }
    }

    @Composable
    private fun library() = LibraryContent(
        songs = ListState(songs, nextOffset = null, total = 214),
        playlists = ListState(playlists, nextOffset = null, total = playlists.size),
        profile = profile,
        online = true,
        connection = SpotifyRemoteManager.Connection.Connected,
        playback = playback(),
        artwork = null,
        actions = LibraryActions(),
    )

    @Test fun library_evening() = shot("library_evening") { library() }

    /** The whole library page on a very tall screen, to see the map and the song list together. */
    @Test fun library_full() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_6.copy(screenHeight = 5200))
        shot("library_full") { library() }
    }

    @Test fun library_midnight() = shot("library_midnight", theme = Backdrop.MIDNIGHT) { library() }

    @Test fun library_mono() = shot("library_mono", mono = true) { library() }

    @Test fun library_needs_approval() = shot("library_approval") {
        LibraryContent(
            songs = ListState(songs.take(4), nextOffset = null, total = 214),
            playlists = ListState(playlists.take(3), nextOffset = null),
            profile = profile,
            online = true,
            connection = SpotifyRemoteManager.Connection.Failed("Spotify didn't answer. Is it installed and signed in?"),
            playback = playback(playing = false),
            artwork = null,
            actions = LibraryActions(),
        )
    }

    @Test fun playlist() = shot("playlist") {
        PlaylistContent(
            title = "Late Night Drive",
            coverUrl = null,
            owner = "Ethan",
            trackCount = 42,
            state = ListState(songs, nextOffset = null),
            playback = playback(),
            artwork = null,
            onBack = {}, onOpenNowPlaying = {}, onPlayAll = {}, onPlay = { _, _ -> }, onQueue = {},
            onLoadMore = {}, onTogglePlay = {}, onNext = {},
        )
    }

    @Test fun playlist_search() = shot("playlist_search") {
        PlaylistContent(
            title = "Late Night Drive",
            coverUrl = null,
            owner = "Ethan",
            trackCount = 42,
            state = ListState(songs, nextOffset = null),
            playback = playback(),
            artwork = null,
            onBack = {}, onOpenNowPlaying = {}, onPlayAll = {}, onPlay = { _, _ -> }, onQueue = {},
            onLoadMore = {}, onTogglePlay = {}, onNext = {},
            initialSearch = "he",
            autoFocus = false,
        )
    }

    @Test fun search() = shot("search") {
        SearchContent(
            query = "past lives",
            results = ListState(songs.drop(1).take(6), nextOffset = null),
            playback = playback(),
            artwork = null,
            onQueryChange = {}, onBack = {}, onOpenNowPlaying = {}, onPlay = {}, onQueue = {},
            onLoadMore = {}, onTogglePlay = {}, onNext = {},
            autoFocus = false,
        )
    }

    @Test fun search_empty() = shot("search_empty", theme = Backdrop.SUNSET) {
        SearchContent(
            query = "",
            results = ListState(nextOffset = null),
            playback = playback(),
            artwork = null,
            onQueryChange = {}, onBack = {}, onOpenNowPlaying = {}, onPlay = {}, onQueue = {},
            onLoadMore = {}, onTogglePlay = {}, onNext = {},
            autoFocus = false,
        )
    }

    @Test fun settings() = shot("settings") {
        AppSettingsContent(
            settings = AppSettings(),
            profile = profile,
            premium = PremiumStatus.PREMIUM,
            onUpdate = {}, onIcon = {}, onSignOut = {}, onBack = {},
        )
    }

    @Test fun settings_mono() = shot("settings_mono", mono = true) {
        AppSettingsContent(
            settings = AppSettings(monochrome = true),
            profile = profile,
            premium = PremiumStatus.PREMIUM,
            onUpdate = {}, onIcon = {}, onSignOut = {}, onBack = {},
        )
    }
}
