package com.spectra.player.ui.settings

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spectra.player.ui.chart.Chart
import com.spectra.player.ui.chart.ChartButton
import com.spectra.player.ui.chart.ChartNote
import com.spectra.player.ui.chart.ChartRule
import com.spectra.player.ui.theme.AppPrefs
import com.spectra.player.ui.theme.AppSettings
import com.spectra.player.ui.theme.CustomIcon
import com.spectra.player.ui.theme.CustomPalette
import com.spectra.player.ui.theme.PalettePreset
import com.spectra.player.visualizer.Backdrop
import kotlin.math.cos
import kotlin.math.sin

/** Editor for the Custom theme: every colour, saved palettes, and a home screen icon to match. */
@Composable
fun ColorEditorScreen(prefs: AppPrefs, onBack: () -> Unit) {
    val settings by prefs.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ColorEditorContent(
        settings = settings,
        onUpdate = prefs::update,
        onBack = onBack,
        onPinIcon = {
            val asked = try {
                CustomIcon.pin(context, settings.custom)
            } catch (e: Exception) {
                false
            }
            if (!asked) Toast.makeText(context, "Your home screen doesn't allow adding icons this way.", Toast.LENGTH_LONG).show()
        },
    )
}

@Composable
fun ColorEditorContent(
    settings: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onBack: () -> Unit,
    onPinIcon: () -> Unit,
    /** For screenshots: which colour's picker starts open. */
    initialOpen: CustomPalette.Slot? = null,
) {
    val palette = settings.custom
    var open by rememberSaveable { mutableStateOf(initialOpen) }
    val inUse = settings.theme == Backdrop.CUSTOM && !settings.monochrome

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
                Text("SETTINGS", style = MaterialTheme.typography.labelMedium, color = Chart.dim)
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
                "Custom colours",
                style = MaterialTheme.typography.headlineLarge,
                color = Chart.ink,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp),
            )
            Text(
                "CHANGES ARE SAVED AS YOU MAKE THEM",
                style = MaterialTheme.typography.labelMedium,
                color = Chart.dim,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 14.dp),
            )

            if (!inUse) {
                ChartNote(
                    heading = "Not in use yet",
                    body = if (settings.monochrome) {
                        "Monochrome is on, so the app is black and white. Switch to your custom colours to see them."
                    } else {
                        "The app is using the ${settings.theme.label} theme. Switch to your custom colours to see them."
                    },
                    tone = Chart.accent,
                    actions = {
                        TextButton(onClick = { onUpdate { it.copy(theme = Backdrop.CUSTOM, monochrome = false) } }) {
                            Text("USE CUSTOM COLOURS")
                        }
                    },
                )
            }

            PalettePreview(palette, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))

            ChartRule("Colours", modifier = Modifier.padding(top = 22.dp, bottom = 6.dp))
            CustomPalette.Slot.entries.forEach { slot ->
                val color = slot.get(palette)
                SlotRow(
                    slot = slot,
                    color = color,
                    open = open == slot,
                    onToggle = { open = if (open == slot) null else slot },
                )
                if (open == slot) {
                    androidx.compose.runtime.key(slot) {
                        ColorPicker(
                            color = color,
                            onColor = { picked -> onUpdate { it.copy(custom = slot.set(it.custom, picked)) } },
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 14.dp),
                        )
                    }
                }
            }
            ChartButton(
                "Reset to the starting colours",
                onClick = { onUpdate { it.copy(custom = CustomPalette()) } },
                modifier = Modifier.padding(start = 20.dp, top = 12.dp),
            )

            ChartRule("Saved palettes", meta = settings.presets.size.takeIf { it > 0 }?.toString(), modifier = Modifier.padding(top = 34.dp, bottom = 10.dp))
            ChartButton(
                "Save these colours",
                filled = true,
                onClick = {
                    onUpdate { s ->
                        var n = s.presets.size + 1
                        while (s.presets.any { it.name == "Palette $n" }) n++
                        s.copy(presets = s.presets + PalettePreset("Palette $n", s.custom))
                    }
                },
                modifier = Modifier.padding(start = 20.dp),
            )
            if (settings.presets.isEmpty()) {
                Caption("Save a set of colours here to come back to it later.")
            }
            settings.presets.forEach { preset ->
                PresetRow(
                    preset = preset,
                    current = preset.palette == palette,
                    onApply = { onUpdate { it.copy(custom = preset.palette) } },
                    onDelete = { onUpdate { s -> s.copy(presets = s.presets - preset) } },
                )
            }

            ChartRule("Home screen icon", modifier = Modifier.padding(top = 34.dp, bottom = 10.dp))
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconPreview(palette, Modifier.size(64.dp))
                Spacer(Modifier.width(16.dp))
                ChartButton("Add to home screen", onClick = onPinIcon)
            }
            Caption(
                "Android doesn't let an app repaint its own icon, so this adds a second Spectra icon in these colours. " +
                    "Some home screens put a small badge on it. Change the colours later and you can add a new one.",
            )
            Spacer(Modifier.height(40.dp))
        }
    }
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

/** A strip showing the colours together: sky, stars, the ring, text, a button and a slider. */
@Composable
private fun PalettePreview(p: CustomPalette, modifier: Modifier = Modifier) {
    val text = Color(p.text)
    val buttons = Color(p.buttons)
    Box(
        modifier
            .fillMaxWidth()
            .height(168.dp)
            .clip(Chart.corner)
            .background(Brush.verticalGradient(listOf(Color(p.skyTop), Color(p.skyBottom))))
            .border(1.dp, Color.White.copy(alpha = 0.14f), Chart.corner),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val star = Color(p.stars)
            val rnd = kotlin.random.Random(5)
            repeat(34) {
                drawCircle(
                    star.copy(alpha = 0.35f + rnd.nextFloat() * 0.6f),
                    (0.6f + rnd.nextFloat() * 1.1f).dp.toPx(),
                    Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height),
                )
            }
            // The visualizer ring.
            val c = Offset(size.width - 78.dp.toPx(), size.height / 2)
            val a1 = Color(p.bars1)
            val a2 = Color(p.bars2)
            val brush = Brush.sweepGradient(listOf(a1, a2, a1, a2, a1), c)
            val inner = 30.dp.toPx()
            val count = 44
            for (i in 0 until count) {
                val a = i.toFloat() / count * 6.2831855f
                val level = 0.2f + 0.8f * (0.5f + 0.5f * sin(i * 0.9f) * cos(i * 0.31f))
                val len = 22.dp.toPx() * level
                val dir = Offset(cos(a), sin(a))
                drawLine(brush, c + dir * inner, c + dir * (inner + len), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
            }
            drawCircle(text.copy(alpha = 0.25f), inner - 5.dp.toPx(), c, style = Stroke(1.dp.toPx()))
            // A slider.
            val y = size.height - 22.dp.toPx()
            val x0 = 16.dp.toPx()
            val x1 = size.width * 0.52f
            drawLine(text.copy(alpha = 0.22f), Offset(x0, y), Offset(x1, y), 3.dp.toPx(), StrokeCap.Round)
            drawLine(Color(p.sliders), Offset(x0, y), Offset(x0 + (x1 - x0) * 0.6f, y), 3.dp.toPx(), StrokeCap.Round)
            drawCircle(text, 6.dp.toPx(), Offset(x0 + (x1 - x0) * 0.6f, y))
        }
        Column(Modifier.padding(start = 16.dp, top = 16.dp)) {
            Text("Song title", style = MaterialTheme.typography.titleLarge, color = text)
            Text("Artist", style = MaterialTheme.typography.bodyMedium, color = text.copy(alpha = 0.72f))
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .height(34.dp)
                    .background(buttons, Chart.corner)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val on = if (buttons.red * 0.299f + buttons.green * 0.587f + buttons.blue * 0.114f > 0.55f) Color.Black else Color.White
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = on, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("PLAY", style = MaterialTheme.typography.labelLarge, color = on)
            }
        }
    }
}

@Composable
private fun IconPreview(p: CustomPalette, modifier: Modifier = Modifier) {
    val bitmap = remember(p) { CustomIcon.render(p, 216).asImageBitmap() }
    // Launchers crop the middle two thirds of an adaptive icon.
    Box(modifier.clip(CircleShape)) {
        Image(
            bitmap = bitmap,
            contentDescription = "Your custom icon",
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f },
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
    }
}

@Composable
private fun SlotRow(slot: CustomPalette.Slot, color: Int, open: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .background(Color(color), Chart.corner)
                .border(1.dp, if (open) Chart.accent else Chart.line, Chart.corner),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(slot.label, style = MaterialTheme.typography.titleMedium, color = Chart.ink)
            Text(slot.note, style = MaterialTheme.typography.bodySmall, color = Chart.dim)
        }
        Text(CustomPalette.toHex(color), style = MaterialTheme.typography.labelMedium, color = if (open) Chart.accent else Chart.dim)
    }
}

/**
 * Colour picker: a square for how vivid and how bright, a strip for the hue, and a box for
 * typing a hex code. Hue, vividness and brightness are kept here so the hue isn't lost when
 * the colour passes through black, white or grey.
 */
@Composable
private fun ColorPicker(color: Int, onColor: (Int) -> Unit, modifier: Modifier = Modifier) {
    var hsv by remember { mutableStateOf(FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) }) }
    var dragging by remember { mutableStateOf(false) }
    // The colour changed from outside (a preset, reset, typed hex): follow it. Not while a finger
    // is on the picker, and not for the picker's own changes coming back.
    val seen = remember { intArrayOf(color) }
    if (color != seen[0]) {
        seen[0] = color
        if (!dragging && !sameColor(android.graphics.Color.HSVToColor(hsv), color)) {
            hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) }
        }
    }
    val emit by androidx.compose.runtime.rememberUpdatedState(onColor)
    fun set(h: Float = hsv[0], s: Float = hsv[1], v: Float = hsv[2]) {
        val next = floatArrayOf(h, s, v)
        hsv = next
        emit(android.graphics.Color.HSVToColor(next))
    }
    val pureHue = Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f)))

    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(Chart.corner)
                .background(Brush.horizontalGradient(listOf(Color.White, pureHue)))
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                .border(1.dp, Chart.line, Chart.corner)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        fun at(p: Offset) = set(
                            s = (p.x / size.width).coerceIn(0f, 1f),
                            v = 1f - (p.y / size.height).coerceIn(0f, 1f),
                        )
                        dragging = true
                        at(down.position)
                        drag(down.id) { change ->
                            change.consume()
                            at(change.position)
                        }
                        dragging = false
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val p = Offset(hsv[1] * size.width, (1f - hsv[2]) * size.height)
                drawCircle(Color.Black, 9.dp.toPx(), p, style = Stroke(3.dp.toPx()))
                drawCircle(Color.White, 9.dp.toPx(), p, style = Stroke(1.5.dp.toPx()))
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(30.dp)
                .clip(Chart.corner)
                .background(
                    Brush.horizontalGradient(
                        List(7) { i -> Color(android.graphics.Color.HSVToColor(floatArrayOf(i * 60f, 1f, 1f))) },
                    ),
                )
                .border(1.dp, Chart.line, Chart.corner)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        fun at(p: Offset) = set(h = (p.x / size.width).coerceIn(0f, 1f) * 359.9f)
                        dragging = true
                        at(down.position)
                        drag(down.id) { change ->
                            change.consume()
                            at(change.position)
                        }
                        dragging = false
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val x = hsv[0] / 360f * size.width
                drawLine(Color.Black, Offset(x, 0f), Offset(x, size.height), 5.dp.toPx())
                drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
            }
        }
        Spacer(Modifier.height(12.dp))
        HexField(color, onColor)
    }
}

@Composable
private fun HexField(color: Int, onColor: (Int) -> Unit) {
    val shown = CustomPalette.toHex(color)
    var text by remember { mutableStateOf(shown) }
    // Follow the picker, unless what's typed already means the same colour.
    val seen = remember { intArrayOf(color) }
    if (color != seen[0]) {
        seen[0] = color
        if (CustomPalette.parseHex(text) != (color or 0xFF000000.toInt())) text = shown
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("HEX", style = MaterialTheme.typography.labelMedium, color = Chart.dim)
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = text,
            onValueChange = { typed ->
                text = typed.take(7)
                CustomPalette.parseHex(typed)?.let(onColor)
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.labelLarge.copy(color = Chart.ink),
            cursorBrush = SolidColor(Chart.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
            modifier = Modifier
                .width(120.dp)
                .border(1.dp, Chart.line, Chart.corner)
                .padding(horizontal = 12.dp, vertical = 9.dp),
        )
    }
}

/** The same colour, give or take the rounding of a trip through hue/vividness/brightness. */
private fun sameColor(a: Int, b: Int): Boolean {
    fun close(shift: Int) = kotlin.math.abs(((a shr shift) and 0xFF) - ((b shr shift) and 0xFF)) <= 2
    return close(16) && close(8) && close(0)
}

@Composable
private fun PresetRow(preset: PalettePreset, current: Boolean, onApply: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Use ${preset.name}", onClick = onApply)
            .padding(start = 20.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                preset.name + if (current) "  ·  IN USE" else "",
                style = MaterialTheme.typography.titleMedium,
                color = if (current) Chart.accent else Chart.ink,
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.border(1.dp, Chart.line, Chart.corner)) {
                CustomPalette.Slot.entries.forEach { slot ->
                    Box(Modifier.size(width = 26.dp, height = 14.dp).background(Color(slot.get(preset.palette))))
                }
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.Close, contentDescription = "Delete ${preset.name}", tint = Chart.dim)
        }
    }
}
