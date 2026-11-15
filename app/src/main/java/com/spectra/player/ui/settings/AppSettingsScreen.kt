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
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.horizontalScroll
import com.spectra.player.ui.chart.ChartChip
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
    onEditColors: () -> Unit = {},
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
        onEditColors = onEditColors,
    )
}

@Composable
fun AppSettingsContent(
    settings: AppSettings,
    profile: UserProfile?,
    premium: PremiumStatus,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onIcon: (String) -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
    onEditColors: () -> Unit = {},
    /** For screenshots: show the custom time fields. */
    initialCustomTime: Boolean = false,
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
            ChartButton(
                "Edit custom colours",
                onClick = onEditColors,
                icon = Icons.Rounded.Palette,
                modifier = Modifier.padding(start = 20.dp, bottom = 10.dp),
            )
            ChartSwitchRow("Stars in the background", settings.stars, onChange = { on -> onUpdate { it.copy(stars = on) } })

            Section("App icon")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(AppIcons.all, key = { it.alias }) { icon ->
                    IconChoice(
                        icon = icon,
                        selected = icon.alias == settings.icon,
                        onClick = {
                            onUpdate { it.copy(icon = icon.alias) }
                            onIcon(icon.alias)
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
            var customTime by rememberSaveable {
                mutableStateOf(initialCustomTime || AutoOpenChoices.none { it.first == settings.autoVisualizerSec })
            }
            Text(
                "Open by itself",
                style = MaterialTheme.typography.titleMedium,
                color = Chart.ink,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Caption("While music is playing, the visualizer opens after the screen hasn't been touched for this long.")
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AutoOpenChoices.forEach { (seconds, label) ->
                    ChartChip(
                        label,
                        selected = !customTime && settings.autoVisualizerSec == seconds,
                        onClick = {
                            customTime = false
                            onUpdate { it.copy(autoVisualizerSec = seconds) }
                        },
                    )
                }
                ChartChip(
                    "Custom",
                    selected = customTime,
                    onClick = {
                        customTime = true
                        // Coming from Off: start from one minute rather than nothing.
                        if (settings.autoVisualizerSec <= 0) onUpdate { it.copy(autoVisualizerSec = 60) }
                    },
                )
            }
            if (customTime) {
                DurationFields(
                    seconds = settings.autoVisualizerSec,
                    onSeconds = { total -> onUpdate { it.copy(autoVisualizerSec = total) } },
                )
            }
            Caption("The visualizer has its own settings. Open it, tap the screen, then the gear.")
            Spacer(Modifier.height(40.dp))
        }
    }
}

/** Minutes and seconds boxes for a time of the user's own, 5 seconds to 5 minutes. */
@Composable
private fun DurationFields(seconds: Int, onSeconds: (Int) -> Unit) {
    var minutes by remember { mutableStateOf((seconds / 60).toString()) }
    var secs by remember { mutableStateOf((seconds % 60).toString()) }
    fun push(m: String, s: String) {
        val total = (m.toIntOrNull() ?: 0) * 60 + (s.toIntOrNull() ?: 0)
        if (total > 0) onSeconds(total.coerceIn(MIN_AUTO_OPEN_SEC, MAX_AUTO_OPEN_SEC))
    }
    Row(
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NumberBox(minutes, "Minutes") {
            minutes = it
            push(it, secs)
        }
        Text("  MIN    ", style = MaterialTheme.typography.labelMedium, color = Chart.dim)
        NumberBox(secs, "Seconds") {
            secs = it
            push(minutes, it)
        }
        Text("  SEC", style = MaterialTheme.typography.labelMedium, color = Chart.dim)
    }
    Caption(
        "Opens after ${describeDuration(seconds)} without a touch. " +
            "Anything from $MIN_AUTO_OPEN_SEC seconds to ${MAX_AUTO_OPEN_SEC / 60} minutes.",
    )
}

@Composable
private fun NumberBox(value: String, label: String, onChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = { typed -> onChange(typed.filter { it.isDigit() }.take(if (label == "Minutes") 1 else 2)) },
        singleLine = true,
        textStyle = MaterialTheme.typography.labelLarge.copy(color = Chart.ink, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(Chart.accent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier
            .width(56.dp)
            .border(1.dp, Chart.line, Chart.corner)
            .padding(vertical = 10.dp)
            .semantics { contentDescription = label },
    )
}

internal fun describeDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return listOfNotNull(
        if (m > 0) "$m min" else null,
        if (s > 0 || m == 0) "$s s" else null,
    ).joinToString(" ")
}

private const val MIN_AUTO_OPEN_SEC = 5
private const val MAX_AUTO_OPEN_SEC = 300

private val AutoOpenChoices = listOf(0 to "Off", 15 to "15 s", 30 to "30 s", 60 to "1 min", 120 to "2 min", 300 to "5 min")

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
