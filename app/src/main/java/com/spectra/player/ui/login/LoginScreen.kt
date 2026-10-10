package com.spectra.player.ui.login

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
import androidx.compose.runtime.Composable
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
            .background(Brush.verticalGradient(listOf(Color(0xFF171230), colors.background)))
            .systemBarsPadding()
            .padding(32.dp),
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DecorativeRing(Modifier.size(180.dp))
            Spacer(Modifier.height(8.dp))
            Text("Spectra", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Your Spotify library, in motion.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onLogin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text("Continue with Spotify", style = MaterialTheme.typography.labelLarge)
            }

            when (state) {
                AuthState.InProgress -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        "Waiting for Spotify… tap the button to start over.",
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
                "You'll sign in on Spotify's own page. Spectra never sees your password.",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
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
