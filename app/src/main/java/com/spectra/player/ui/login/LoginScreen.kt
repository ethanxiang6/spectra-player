package com.spectra.player.ui.login

import androidx.compose.ui.unit.sp

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalContext
import com.spectra.player.BuildConfig
import com.spectra.player.auth.AuthConfig
import com.spectra.player.auth.SigningInfo
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spectra.player.auth.AuthState
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun LoginScreen(
    state: AuthState,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Spectra",
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 72.sp, lineHeight = 72.sp),
            )
            Text(
                "Your Spotify library under an evening sky, with a visualizer that moves to the music.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))

            Button(
                onClick = onLogin,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text("Continue with Spotify", style = MaterialTheme.typography.titleMedium)
            }

            when (state) {
                AuthState.InProgress -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        "Finish signing in on Spotify's page, or tap the button to start again.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
                is AuthState.Error -> Text(
                    state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.error,
                    textAlign = TextAlign.Center,
                )
                else -> Unit
            }

            Text(
                "You sign in on Spotify's own page. Spectra never sees your password.",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 8.dp),
            )

            SetupDetails()
        }
    }
}

/**
 * "Redirect URI not matching" almost always means the dashboard value differs from what this build
 * sends. This shows the exact values (long-press to copy) so they can be pasted into
 * developer.spotify.com/dashboard -> your app -> Settings.
 */
@Composable
private fun SetupDetails() {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = !open }) {
        Text(if (open) "Hide details" else "Trouble signing in?")
    }
    if (open) {
        val sha1 = remember { SigningInfo.sha1(context) ?: "unavailable" }
        val id = AuthConfig.clientId
        val idShown = if (id.isBlank()) "(empty - the build has no Client ID)" else id
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DetailRow("Redirect URI (paste exactly)", AuthConfig.redirectUri)
                DetailRow("Client ID in this build", idShown)
                DetailRow("Android package", BuildConfig.APPLICATION_ID)
                DetailRow("Signing SHA-1", sha1)
            }
        }
        Text(
            "In the Spotify dashboard add the Redirect URI above (no spaces or trailing slash) and click Save.",
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Static preview of the radial spectrum used as the app mark. */
@Composable
private fun DecorativeRing(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val half = size.minDimension / 2f
        val inner = half * 0.42f
        val brush = Brush.sweepGradient(listOf(colors.primary, colors.secondary, colors.tertiary, colors.primary), c)
        val bars = 56
        for (i in 0 until bars) {
            val a = i.toFloat() / bars * 6.2831855f
            val level = 0.25f + 0.75f * (0.5f + 0.5f * sin(i * 0.9f) * cos(i * 0.31f))
            val len = (half - inner - 6.dp.toPx()) * level
            val dir = Offset(cos(a), sin(a))
            drawLine(brush, c + dir * inner, c + dir * (inner + len), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}
