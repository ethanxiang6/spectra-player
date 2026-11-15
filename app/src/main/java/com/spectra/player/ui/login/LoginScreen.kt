package com.spectra.player.ui.login

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spectra.player.BuildConfig
import com.spectra.player.auth.AuthConfig
import com.spectra.player.auth.AuthState
import com.spectra.player.auth.SigningInfo
import com.spectra.player.ui.chart.Chart
import com.spectra.player.ui.chart.ChartButton
import com.spectra.player.ui.chart.ChartNote
import com.spectra.player.ui.chart.ChartRule
import com.spectra.player.ui.chart.drawChartStar
import kotlin.math.hypot

/** First page: a small constellation, the name, and one button that opens Spotify's sign-in. */
@Composable
fun LoginScreen(
    state: AuthState,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            "SPECTRA",
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 5.sp),
            color = Chart.ink,
            modifier = Modifier.padding(start = 20.dp, top = 14.dp),
        )
        LoginConstellation(Modifier.fillMaxWidth().height(250.dp).padding(top = 8.dp))

        Text(
            "Spectra",
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 68.sp, lineHeight = 70.sp),
            color = Chart.ink,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Text(
            "Your Spotify library drawn as a night sky, with a visualizer that moves to the music.",
            style = MaterialTheme.typography.bodyLarge,
            color = Chart.dim,
            modifier = Modifier.padding(start = 20.dp, end = 40.dp, top = 10.dp),
        )

        ChartButton(
            "Continue with Spotify",
            onClick = onLogin,
            filled = true,
            modifier = Modifier
                .padding(start = 20.dp, end = 20.dp, top = 30.dp)
                .fillMaxWidth()
                .height(52.dp),
        )

        when (state) {
            AuthState.InProgress -> Row(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp, color = Chart.dim)
                Text(
                    "Finish signing in on Spotify's page, or tap the button to start again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Chart.dim,
                )
            }
            is AuthState.Error -> ChartNote(
                heading = "Sign-in didn't finish",
                body = state.message,
                tone = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 10.dp),
                extra = { Spacer(Modifier.height(6.dp)) },
            )
            else -> Unit
        }

        Text(
            "YOU SIGN IN ON SPOTIFY'S OWN PAGE. SPECTRA NEVER SEES YOUR PASSWORD.",
            style = MaterialTheme.typography.labelSmall,
            color = Chart.dim,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp),
        )

        SetupDetails()
        Spacer(Modifier.height(24.dp))
    }
}

/** A fixed little constellation over orbit arcs, in the same hand as the playlist map. */
@Composable
private fun LoginConstellation(modifier: Modifier = Modifier) {
    val ink = Chart.ink
    val dim = Chart.dim
    val line = Chart.line
    val accent = Chart.accent
    Canvas(modifier) {
        val pole = Offset(size.width * 1.05f, -size.height * 0.2f)
        val grid = line.copy(alpha = line.alpha * 0.4f)
        var r = 90.dp.toPx()
        val far = hypot(size.width, size.height) * 1.3f
        while (r < far) {
            drawCircle(grid, r, pole, style = Stroke(0.8.dp.toPx()))
            r += 90.dp.toPx()
        }
        val rnd = kotlin.random.Random(42)
        repeat(40) {
            drawCircle(
                dim.copy(alpha = 0.35f),
                (0.6f + rnd.nextFloat() * 0.9f).dp.toPx(),
                Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height),
            )
        }
        // x, y (fractions of the box) and core radius in dp.
        val stars = listOf(
            Triple(0.16f, 0.70f, 3.2f), Triple(0.30f, 0.42f, 2.2f), Triple(0.47f, 0.56f, 4.4f),
            Triple(0.62f, 0.30f, 2.6f), Triple(0.80f, 0.44f, 3.4f), Triple(0.70f, 0.74f, 2.0f),
        )
        val points = stars.map { Offset(it.first * size.width, it.second * size.height) }
        val links = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 2 to 5)
        links.forEach { (a, b) ->
            val p = points[a]
            val q = points[b]
            val d = hypot(q.x - p.x, q.y - p.y)
            val u = Offset((q.x - p.x) / d, (q.y - p.y) / d)
            val gap = 9.dp.toPx()
            drawLine(line.copy(alpha = 0.5f), p + u * gap, q - u * gap, 1.dp.toPx())
        }
        points.forEachIndexed { i, p ->
            val radius = stars[i].third.dp.toPx()
            if (i == 2) drawChartStar(p, radius, accent, accent, spikes = 1f)
            else drawChartStar(p, radius, ink, accent.copy(alpha = 0.6f), spikes = if (stars[i].third > 3f) 0.7f else 0f)
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
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = !open }, modifier = Modifier.padding(start = 8.dp, top = 4.dp)) {
        Text(if (open) "HIDE DETAILS" else "TROUBLE SIGNING IN?")
    }
    if (open) {
        val sha1 = remember { SigningInfo.sha1(context) ?: "unavailable" }
        val id = AuthConfig.clientId
        val idShown = if (id.isBlank()) "(empty - the build has no Client ID)" else id
        ChartRule("Setup", modifier = Modifier.padding(bottom = 8.dp))
        SelectionContainer {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailRow("Redirect URI (paste exactly)", AuthConfig.redirectUri)
                DetailRow("Client ID in this build", idShown)
                DetailRow("Android package", BuildConfig.APPLICATION_ID)
                DetailRow("Signing SHA-1", sha1)
            }
        }
        Text(
            "In the Spotify dashboard add the Redirect URI above (no spaces or trailing slash) and click Save.",
            style = MaterialTheme.typography.bodySmall,
            color = Chart.dim,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Chart.dim)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Chart.ink)
    }
}
