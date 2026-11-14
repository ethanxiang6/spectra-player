package com.spectra.player.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spectra.player.data.Track
import com.spectra.player.data.formatTime
import com.spectra.player.ui.chart.Chart
import com.spectra.player.ui.chart.StarMark

/**
 * A song as a line in a star catalogue:
 * `014  ·  Past Lives              2:41  ⋮`
 * `        sapientdream`
 * The current song gets a bright twinkling star and the accent colour.
 */
@Composable
fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 1-based catalogue number; hidden when null. */
    number: Int? = null,
    /** When set, a ⋮ menu offers "Add to queue". */
    onAddToQueue: (() -> Unit)? = null,
) {
    val title = if (isCurrent) Chart.accent else Chart.ink
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = if (onAddToQueue != null) 2.dp else 20.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (number != null) {
            Text(
                Chart.catalogNumber(number),
                style = MaterialTheme.typography.labelMedium,
                color = if (isCurrent) Chart.accent else Chart.dim.copy(alpha = 0.7f),
                textAlign = TextAlign.End,
                modifier = Modifier.width(34.dp),
            )
        }
        StarMark(
            current = isCurrent,
            playing = isPlaying,
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .semantics { if (isCurrent) contentDescription = if (isPlaying) "Playing" else "Current song" },
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = track.name,
                style = MaterialTheme.typography.titleMedium,
                color = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artists,
                style = MaterialTheme.typography.bodyMedium,
                color = Chart.dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (track.durationMs > 0) {
            Spacer(Modifier.width(10.dp))
            Text(
                track.durationMs.formatTime(),
                style = MaterialTheme.typography.labelMedium,
                color = Chart.dim.copy(alpha = 0.8f),
            )
        }
        if (onAddToQueue != null) {
            TrackMenu(onPlay = onClick, onAddToQueue = onAddToQueue)
        }
    }
}

@Composable
private fun TrackMenu(onPlay: () -> Unit, onAddToQueue: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "More options", tint = Chart.dim)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Play") },
                leadingIcon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null) },
                onClick = {
                    open = false
                    onPlay()
                },
            )
            DropdownMenuItem(
                text = { Text("Add to queue") },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null) },
                onClick = {
                    open = false
                    onAddToQueue()
                },
            )
        }
    }
}
