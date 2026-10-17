package com.spectra.player.ui.settings

import com.spectra.player.ui.components.CoverArt

import com.spectra.player.data.UserProfile

import com.spectra.player.data.PremiumStatus

import androidx.compose.ui.Alignment

import androidx.compose.material3.TextButton

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.width

import androidx.compose.foundation.layout.size

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spectra.player.ui.nowplaying.BackdropTile
import com.spectra.player.ui.nowplaying.SwitchRow
import com.spectra.player.ui.theme.AppPrefs
import com.spectra.player.ui.theme.AppSettings

/** Settings for the app (library, playlists, search). The visualizer keeps its own settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    prefs: AppPrefs,
    profile: UserProfile?,
    premium: PremiumStatus,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
) {
    val settings by prefs.settings.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
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
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            )
            Heading("Theme")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(AppSettings.themes, key = { it.name }) { theme ->
                    BackdropTile(
                        backdrop = theme,
                        selected = theme == settings.theme,
                        onClick = { prefs.update { it.copy(theme = theme) } },
                    )
                }
            }
            Text(
                "Sets the sky and colours for the library, playlists and search.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
            SwitchRow("Stars in the background", settings.stars) { on -> prefs.update { it.copy(stars = on) } }
            Heading("Account")
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverArt(url = profile?.imageUrl, modifier = Modifier.size(48.dp), shape = CircleShape)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile?.displayName ?: "Spotify account", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when (premium) {
                            PremiumStatus.PREMIUM -> "Spotify Premium"
                            PremiumStatus.FREE -> "Spotify Free: songs play shuffled only"
                            PremiumStatus.UNKNOWN -> "Checking your plan"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = onSignOut, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text("Sign out", color = MaterialTheme.colorScheme.error)
            }

            Heading("Visualizer")
            Text(
                "The visualizer has its own settings. Open it, tap the screen, then the gear.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 12.dp),
    )
}
