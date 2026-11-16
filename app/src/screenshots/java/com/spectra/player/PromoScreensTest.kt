package com.spectra.player

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
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
import com.spectra.player.ui.nowplaying.NowPlayingStill
import com.spectra.player.ui.nowplaying.SkyBackdrop
import com.spectra.player.ui.settings.AppSettingsContent
import com.spectra.player.ui.theme.AppSettings
import com.spectra.player.ui.theme.SpectraTheme
import com.spectra.player.visualizer.Backdrop
import com.spectra.player.visualizer.VisualizerSettings
import com.spectra.player.visualizer.VisualizerStyle
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Frames for the Spectra promo video: the real app screens with made-up library data, rendered
 * at 720 px wide. The visualizer frames follow `src/screenshots/promo/frames.txt` (one line per
 * video frame, with the spectrum of the soundtrack at that moment).
 * Run: ./gradlew -Pscreenshots :app:recordPaparazziDebug --tests '*PromoScreensTest*'
 */
class PromoScreensTest {
    private val promo = DeviceConfig.PIXEL_6.copy(
        screenWidth = 720,
        screenHeight = 1600,
        xdpi = 280,
        ydpi = 280,
        // 280 dpi, so the 720 px page is about as many dp wide as a phone.
        density = Density.create(280),
    )

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = promo,
        theme = "android:Theme.Material.NoActionBar",
        maxPercentDifference = 100.0,
        // Full 720 px frames (Paparazzi otherwise shrinks snapshots to 1000 px tall).
        useDeviceResolution = true,
    )

    private val liked = listOf(
        "Bad Things" to (197_000L to "BoyWithUke"),
        "Nightmare" to (191_000L to "BoyWithUke"),
        "Before I Die" to (112_000L to "BoyWithUke"),
        "Toxic" to (161_000L to "BoyWithUke"),
        "Ghost" to (150_000L to "BoyWithUke"),
        "Migraine" to (171_000L to "BoyWithUke"),
        "Understand" to (185_000L to "BoyWithUke"),
        "Long Drives" to (162_000L to "BoyWithUke"),
        "Sick of U" to (176_000L to "BoyWithUke, BTS"),
        "Lush" to (152_000L to "BoyWithUke"),
        "Trauma" to (169_000L to "BoyWithUke"),
        "IDGAF (with blackbear)" to (140_000L to "BoyWithUke, blackbear"),
        "Burn" to (158_000L to "BoyWithUke"),
        "Fever Dream" to (166_000L to "BoyWithUke"),
    ).mapIndexed { i, (name, d) ->
        Track("t$i", "spotify:track:t$i", name, d.second, "BoyWithUke", null, d.first)
    }

    private val nightmare = liked[1]
    private val idgaf = liked[11]

    private val playlists = listOf(
        Playlist("liked", "sorrow", "Ethan", null, 19),
        Playlist("p2", "Marino", "Ethan", null, 19),
        Playlist("p3", "BoyWithUke", "Ethan", null, 88),
    )

    private val profile = UserProfile("ethan", "Ethan Xiang", null, "premium")

    private fun playback(track: Track?, playing: Boolean = true, positionMs: Long = 64_000L) = PlaybackState(
        track = track?.let { NowPlayingTrack(it.uri, it.name, it.artists, it.album, it.durationMs, null) },
        isPlaying = playing,
        positionMs = positionMs,
        capturedAtElapsedMs = SystemClock.elapsedRealtime(),
        isShuffling = false,
        repeatMode = 0,
    )

    /** A made-up cover: soft grey rings, nothing from a real album. */
    private val cover: ImageBitmap by lazy {
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(
            size * 0.38f, size * 0.34f, size * 0.8f,
            intArrayOf(0xFF8A8A8A.toInt(), 0xFF3A3A3A.toInt(), 0xFF0E0E0E.toInt()),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        for (k in 1..6) {
            paint.strokeWidth = 3f + k
            paint.color = 0x22FFFFFF
            canvas.drawCircle(size * 0.62f, size * 0.66f, k * 34f, paint)
        }
        bitmap.asImageBitmap()
    }

    private fun shot(name: String, mono: Boolean = true, theme: Backdrop = Backdrop.EVENING, content: @Composable () -> Unit) {
        Dusk.mono = mono
        Dusk.appCustom = false
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
    private fun library(track: Track, playing: Boolean, search: String? = null) = LibraryContent(
        songs = ListState(liked, nextOffset = null, total = 144),
        playlists = ListState(playlists, nextOffset = null, total = 3),
        profile = profile,
        online = true,
        connection = SpotifyRemoteManager.Connection.Connected,
        playback = playback(track, playing),
        artwork = cover,
        actions = LibraryActions(),
        initialLikedSearch = search,
    )

    @Test fun library() {
        shot("p_lib_top") { library(nightmare, true) }
    }

    @Test fun libraryTall() {
        paparazzi.unsafeUpdateConfig(deviceConfig = promo.copy(screenHeight = 3400))
        shot("p_lib_tall_a") { library(liked[0], false) }
        shot("p_lib_tall_b") { library(nightmare, true) }
    }

    @Test fun likedSearch() {
        for (q in listOf("i", "id", "idg", "idga")) shot("p_search_$q") { library(nightmare, true, search = q) }
    }

    @Test fun playlist() {
        paparazzi.unsafeUpdateConfig(deviceConfig = promo.copy(screenHeight = 3000))
        shot("p_playlist") {
            PlaylistContent(
                title = "BoyWithUke",
                coverUrl = null,
                owner = "Ethan",
                trackCount = 88,
                state = ListState(liked.shuffled(java.util.Random(3)), nextOffset = null),
                playback = playback(nightmare),
                artwork = cover,
                onBack = {}, onOpenNowPlaying = {}, onPlayAll = {}, onPlay = { _, _ -> }, onQueue = {},
                onLoadMore = {}, onTogglePlay = {}, onNext = {},
            )
        }
    }

    @Test fun settingsThemes() {
        paparazzi.unsafeUpdateConfig(deviceConfig = promo.copy(screenHeight = 2400))
        val themes = listOf(Backdrop.EVENING, Backdrop.SUNSET, Backdrop.AURORA, Backdrop.NEBULA, Backdrop.MIDNIGHT)
        for (t in themes) {
            shot("p_set_${t.name.lowercase()}", mono = false, theme = t) {
                AppSettingsContent(
                    settings = AppSettings(theme = t),
                    profile = profile,
                    premium = PremiumStatus.PREMIUM,
                    onUpdate = {}, onIcon = {}, onSignOut = {}, onBack = {},
                )
            }
        }
        shot("p_set_mono", mono = true) {
            AppSettingsContent(
                settings = AppSettings(monochrome = true),
                profile = profile,
                premium = PremiumStatus.PREMIUM,
                onUpdate = {}, onIcon = {}, onSignOut = {}, onBack = {},
            )
        }
    }

    @Test fun visualizerFrames() {
        Dusk.mono = true
        Dusk.appCustom = false
        val lines = File("src/screenshots/promo/frames.txt").readLines().filter { it.isNotBlank() }
        for (line in lines) {
            val p = line.split("|")
            val track = if (p[1] == "0") nightmare else idgaf
            val style = VisualizerStyle.valueOf(p[2])
            val controls = p[3] == "1"
            val lyrics = p[4] == "1"
            val seconds = p[5].toFloat()
            val position = p[6].toLong()
            val spectrum = p[7].split(",").map { it.toFloat() * 0.82f }.toFloatArray()
            paparazzi.snapshot("v_${p[0]}") {
                SpectraTheme(theme = Backdrop.BLACK, monochrome = true) {
                    NowPlayingStill(
                        playback = playback(track, true, position),
                        artwork = cover,
                        spectrum = spectrum,
                        settings = VisualizerSettings(backdrop = Backdrop.BLACK, style = style),
                        seconds = seconds,
                        artAngle = (seconds * 8f) % 360f,
                        controlsVisible = controls,
                        lyricsRoom = lyrics,
                    )
                }
            }
        }
    }
}
