package com.spectra.player.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spectra.player.data.PremiumStatus
import com.spectra.player.data.UserProfile
import com.spectra.player.ui.chart.Chart
import com.spectra.player.ui.chart.ChartButton
import com.spectra.player.ui.chart.ChartRule
import com.spectra.player.ui.chart.ChartSwitchRow
import com.spectra.player.ui.chart.drawChartStar
import com.spectra.player.ui.chart.plateMarks
import com.spectra.player.ui.components.CoverArt
import com.spectra.player.ui.nowplaying.look
import com.spectra.player.ui.theme.AppIcons
import com.spectra.player.ui.theme.AppPrefs
import com.spectra.player.ui.theme.AppSettings
import com.spectra.player.visualizer.Backdrop

/** Settings for the app (library, playlists, search). The visualizer keeps its own settings. */
@Composable
fun AppSettingsScreen(
    prefs: AppPrefs,
    profile: UserProfile?,
    premium: PremiumStatus,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
) {
    val settings by prefs.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    AppSettingsContent(
        settings = settings,
        profile = profile,
        premium = premium,
        onUpdate = prefs::update,
        onIcon = { AppIcons.apply(context, it) },
        onSignOut = onSignOut,
        onBack = onBack,
    )
}

@Composable
fun AppSettingsContent(
    settings: AppSettings,
    profile: UserProfile?,
    premium: PremiumStatus,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onIcon: (Backdrop) -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                Modifier.statusBarsPadding().padding(start = 4.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Chart.ink)
                }
                Text("SPECTRA", style = MaterialTheme.typography.labelMedium, color = Chart.dim)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                "Settings",
                style = MaterialTheme.typography.displaySmall,
                color = Chart.ink,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp),
            )
            Text(
                "FOR THE LIBRARY, PLAYLISTS AND SEARCH",
                style = MaterialTheme.typography.labelMedium,
                color = Chart.dim,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
            )

            Section("Theme")
            ChartSwitchRow(
                "Monochrome",
                settings.monochrome,
                onChange = { on -> onUpdate { it.copy(monochrome = on) } },
                note = if (settings.monochrome) {
                    "Black and white on true black, including the visualizer and album covers. Turn it off to use a sky below."
                } else {
                    "Black and white on true black, for AMOLED screens."
                },
            )
            Spacer(Modifier.height(10.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(AppSettings.themes, key = { it.name }) { theme ->
                    SkySwatch(
                        backdrop = theme,
                        selected = theme == settings.theme && !settings.monochrome,
                        onClick = { onUpdate { it.copy(theme = theme, monochrome = false) } },
                    )
                }
            }
            Caption("The sky and colours behind every page except the visualizer.")
            ChartSwitchRow("Stars in the background", settings.stars, onChange = { on -> onUpdate { it.copy(stars = on) } })

            Section("App icon")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(AppIcons.all, key = { it.alias }) { icon ->
                    IconChoice(
                        icon = icon,
                        selected = icon.theme == settings.icon,
                        onClick = {
                            onUpdate { it.copy(icon = icon.theme) }
                            onIcon(icon.theme)
                        },
                    )
                }
            }
            Caption("Your home screen can take a few seconds to show the new icon.")

            Section("Account")
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(52.dp).border(1.dp, Chart.line, CircleShape), contentAlignment = Alignment.Center) {
                    CoverArt(url = profile?.imageUrl, modifier = Modifier.size(42.dp), shape = CircleShape)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile?.displayName ?: "Spotify account", style = MaterialTheme.typography.titleMedium, color = Chart.ink)
                    Text(
                        when (premium) {
                            PremiumStatus.PREMIUM -> "SPOTIFY PREMIUM"
                            PremiumStatus.FREE -> "SPOTIFY FREE · SHUFFLE ONLY"
                            PremiumStatus.UNKNOWN -> "CHECKING YOUR PLAN"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = Chart.dim,
                    )
                }
            }
            ChartButton(
                "Sign out",
                onClick = onSignOut,
                icon = Icons.AutoMirrored.Rounded.Logout,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 20.dp, top = 14.dp),
            )

            Section("Visualizer")
            Caption("The visualizer has its own settings. Open it, tap the screen, then the gear.")
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun Section(text: String) {
    ChartRule(text, modifier = Modifier.padding(top = 34.dp, bottom = 10.dp))
}

@Composable
private fun Caption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = Chart.dim,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

/** A small rectangle of the sky with one star in its accent colours; chosen one gets plate marks. */
@Composable
private fun SkySwatch(backdrop: Backdrop, selected: Boolean, onClick: () -> Unit) {
    val look = backdrop.look()
    val mark = if (selected) Chart.accent else Color.Transparent
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 8.dp),
    ) {
        Box(
            Modifier
                .plateMarks(mark, inset = 5.dp, arm = 10.dp)
                .size(width = 62.dp, height = 88.dp)
                .clip(Chart.corner)
                .background(
                    if (look.stops.size >= 2) Brush.verticalGradient(*look.stops.toTypedArray())
                    else Brush.verticalGradient(listOf(Color(0xFF3A3150), Color(0xFF1C1828))),
                )
                .border(1.dp, Color.White.copy(alpha = 0.12f), Chart.corner),
        ) {
            val ring = look.ring
            Canvas(Modifier.fillMaxSize()) {
                drawChartStar(Offset(size.width * 0.62f, size.height * 0.34f), 2.6.dp.toPx(), Color.White, ring.first(), spikes = 0.8f)
                drawCircle(Color.White.copy(alpha = 0.6f), 0.9.dp.toPx(), Offset(size.width * 0.28f, size.height * 0.2f))
                drawCircle(Color.White.copy(alpha = 0.45f), 0.8.dp.toPx(), Offset(size.width * 0.36f, size.height * 0.62f))
                drawCircle(ring.getOrElse(2) { ring.first() }, 1.6.dp.toPx(), Offset(size.width * 0.74f, size.height * 0.78f))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            backdrop.label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Chart.accent else Chart.dim,
        )
    }
}

@Composable
private fun IconChoice(icon: AppIcons.Icon, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(4.dp),
    ) {
        // The chosen icon sits inside an orbit ring.
        Box(
            Modifier
                .size(74.dp)
                .border(1.dp, if (selected) Chart.accent else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(60.dp).clip(CircleShape)) {
                // Adaptive-icon layers drawn like a round launcher icon (the foreground is 108dp with a
                // 72dp safe zone, so it is scaled up to fill the circle the way launchers crop it).
                Image(painterResource(icon.background), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Image(
                    painterResource(icon.foreground),
                    null,
                    modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.45f; scaleY = 1.45f },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            icon.alias.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Chart.accent else Chart.dim,
        )
    }
}
