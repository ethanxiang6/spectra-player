package com.spectra.player.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spectra.player.data.Hit
import com.spectra.player.data.HitKind
import com.spectra.player.ui.chart.Chart

/** An artist, album or playlist in a list: picture (round for artists), name, and a mono caption. */
@Composable
fun HitRow(hit: Hit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(
            url = hit.imageUrl,
            modifier = Modifier.size(48.dp),
            shape = if (hit.kind == HitKind.ARTISTS) CircleShape else Chart.corner,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                hit.title,
                style = MaterialTheme.typography.titleMedium,
                color = Chart.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (hit.subtitle.isNotBlank()) {
                Text(
                    hit.subtitle.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Chart.dim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
