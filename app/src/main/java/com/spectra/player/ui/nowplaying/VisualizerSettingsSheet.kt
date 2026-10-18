package com.spectra.player.ui.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spectra.player.visualizer.Backdrop
import com.spectra.player.visualizer.VisualizerMode
import com.spectra.player.visualizer.VisualizerSettings
import com.spectra.player.visualizer.VisualizerStyle

private val SheetColor = Color(0xFF14122B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualizerSettingsSheet(
    settings: VisualizerSettings,
    sampleTitle: String,
    liveGranted: Boolean,
    mode: VisualizerMode,
    onChange: ((VisualizerSettings) -> VisualizerSettings) -> Unit,
    onLiveChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onPlayIntro: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = SheetColor,
        contentColor = Dusk.Text,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            item(key = "audio") {
                SectionTitle("Audio")
                SwitchRow("React to the real audio", settings.liveAudio && liveGranted, onLiveChange)
                Text(
                    if (settings.liveAudio && liveGranted && mode == VisualizerMode.LIVE) "Reacting to the music you hear."
                    else "Off: the ring follows the song's timing. On: it reads the phone's audio output " +
                        "(needs the microphone permission; nothing is recorded).",
                    style = TextStyle(color = Dusk.TextDim, fontSize = 13.sp, lineHeight = 18.sp),
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                if (settings.liveAudio && liveGranted) {
                    SyncSlider(settings.syncOffsetMs) { ms -> onChange { it.copy(syncOffsetMs = ms) } }
                }
            }
            item(key = "backdrop") {
                SectionTitle("Background")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(Backdrop.entries.toList(), key = { it.name }) { backdrop ->
                        BackdropTile(
                            backdrop = backdrop,
                            selected = backdrop == settings.backdrop,
                            onClick = { onChange { it.copy(backdrop = backdrop) } },
                        )
                    }
                }
            }
            item(key = "ring") {
                SectionTitle("Ring")
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VisualizerStyle.entries.forEach { style ->
                        FilterChip(
                            selected = style == settings.style,
                            onClick = { onChange { it.copy(style = style) } },
                            label = { Text(style.label()) },
                            colors = FilterChipDefaults.filterChipColors(
                                labelColor = Dusk.TextDim,
                                selectedContainerColor = Dusk.Lavender.copy(alpha = 0.25f),
                                selectedLabelColor = Dusk.Text,
                            ),
                        )
                    }
                }
            }
            item(key = "switches") {
                Spacer(Modifier.height(8.dp))
                SwitchRow("Spin the album art", settings.spinArt) { on -> onChange { it.copy(spinArt = on) } }
                SwitchRow("Stars", settings.stars) { on -> onChange { it.copy(stars = on) } }
            }
            item(key = "intro") {
                SectionTitle("Opening animation")
                MsSlider(
                    label = "Length",
                    valueMs = settings.introDurationMs,
                    range = 200..3000,
                    step = 50,
                    onChange = { ms -> onChange { it.copy(introDurationMs = ms) } },
                )
                MsSlider(
                    label = "Delay before it starts",
                    valueMs = settings.introDelayMs,
                    range = -1500..1500,
                    step = 20,
                    onChange = { ms -> onChange { it.copy(introDelayMs = ms) } },
                )
                androidx.compose.material3.TextButton(
                    onClick = onPlayIntro,
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Text("Play intro", color = Dusk.Lavender)
                }
            }
            item(key = "font-title") {
                SectionTitle("Title font")
                Text(
                    "Only changes the song name and artist on this screen.",
                    style = TextStyle(color = Dusk.TextDim, fontSize = 13.sp),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                )
            }
            items(TitleFonts.all, key = { it.id }) { choice ->
                FontRow(
                    choice = choice,
                    sample = sampleTitle,
                    selected = choice.id == settings.fontId,
                    onClick = { onChange { it.copy(fontId = choice.id) } },
                )
            }
        }
    }
}

@Composable
private fun MsSlider(label: String, valueMs: Int, range: IntRange, step: Int, onChange: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = TextStyle(color = Dusk.Text, fontSize = 14.sp), modifier = Modifier.weight(1f))
            Text(
                if (kotlin.math.abs(valueMs) >= 1000) "%.2f s".format(valueMs / 1000f) else "$valueMs ms",
                style = TextStyle(color = Dusk.TextDim, fontSize = 13.sp),
            )
        }
        androidx.compose.material3.Slider(
            value = valueMs.toFloat(),
            onValueChange = { onChange(kotlin.math.round(it / step).toInt() * step) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = Dusk.Text,
                activeTrackColor = Dusk.Lavender,
                inactiveTrackColor = Color.White.copy(alpha = 0.18f),
            ),
        )
    }
}

@Composable
private fun SyncSlider(offsetMs: Int, onChange: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            "Beat sync: " + when {
                offsetMs == 0 -> "automatic"
                offsetMs > 0 -> "+$offsetMs ms (later)"
                else -> "$offsetMs ms (earlier)"
            },
            style = TextStyle(color = Dusk.Text, fontSize = 14.sp),
        )
        androidx.compose.material3.Slider(
            value = offsetMs.toFloat(),
            onValueChange = { onChange(((it / 20f).toInt() * 20)) },
            valueRange = -200f..400f,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = Dusk.Text,
                activeTrackColor = Dusk.Lavender,
                inactiveTrackColor = Color.White.copy(alpha = 0.18f),
            ),
        )
        Text(
            "If the ring jumps before you hear the beat, slide right. If it jumps after, slide left.",
            style = TextStyle(color = Dusk.TextDim, fontSize = 12.sp),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = TextStyle(color = Dusk.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 10.dp),
    )
}

@Composable
internal fun BackdropTile(backdrop: Backdrop, selected: Boolean, onClick: () -> Unit) {
    val look = backdrop.look()
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .size(width = 72.dp, height = 104.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (look.stops.size >= 2) Brush.verticalGradient(*look.stops.toTypedArray())
                    else Brush.verticalGradient(listOf(Color(0xFF3A3150), Color(0xFF1C1828))),
                )
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) Dusk.Lavender else Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(14.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (backdrop == Backdrop.ALBUM) {
                Icon(Icons.Rounded.Image, contentDescription = null, tint = Dusk.TextDim)
            } else {
                // A little ring of the backdrop's own colours.
                Box(
                    Modifier
                        .size(30.dp)
                        .border(3.dp, Brush.sweepGradient(look.ring), RoundedCornerShape(50)),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            backdrop.label,
            style = TextStyle(
                color = if (selected) Dusk.Text else Dusk.TextDim,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
        )
    }
}

@Composable
internal fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = TextStyle(color = Dusk.Text, fontSize = 15.sp), modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Dusk.Lavender,
                checkedThumbColor = Dusk.SkyUpper,
            ),
        )
    }
}

@Composable
private fun FontRow(choice: TitleFonts.Choice, sample: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (selected) Dusk.Lavender.copy(alpha = 0.10f) else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                sample,
                style = TextStyle(
                    color = Dusk.Text,
                    fontSize = 22.sp,
                    fontFamily = choice.family,
                    fontWeight = if (choice.family == null) FontWeight.SemiBold else FontWeight.Normal,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (choice.category.isEmpty()) choice.label else "${choice.label} · ${choice.category}",
                style = TextStyle(color = Dusk.TextDim, fontSize = 12.sp),
            )
        }
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = Dusk.Lavender)
        }
    }
}

internal fun VisualizerStyle.label(): String = when (this) {
    VisualizerStyle.BARS -> "Bars"
    VisualizerStyle.WAVE -> "Wave"
    VisualizerStyle.DOTS -> "Dots"
}
