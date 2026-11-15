package com.spectra.player.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The colours of the "Custom" theme, as ARGB ints. One palette is shared by the app pages and by
 * the visualizer when its background is set to Custom.
 */
data class CustomPalette(
    val skyTop: Int = 0xFF03051A.toInt(),
    val skyBottom: Int = 0xFF574170.toInt(),
    val stars: Int = 0xFFFFFFFF.toInt(),
    val text: Int = 0xFFF5F2FF.toInt(),
    val buttons: Int = 0xFFCBB9FF.toInt(),
    val sliders: Int = 0xFF8FCBFF.toInt(),
    val bars1: Int = 0xFFCBB9FF.toInt(),
    val bars2: Int = 0xFFFF9FC8.toInt(),
) {
    /**
     * Colours that sit too close to the sky to be seen well, with a short reason each.
     * Text needs the most separation; stars and bars the least.
     */
    fun warnings(): Map<Slot, String> {
        fun vsSky(color: Int) = minOf(contrast(color, skyTop), contrast(color, skyBottom))
        val out = LinkedHashMap<Slot, String>()
        if (vsSky(text) < 3.0) out[Slot.TEXT] = "Close to the sky colour: text will be hard to read"
        if (vsSky(buttons) < 1.8) out[Slot.BUTTONS] = "Close to the sky colour: buttons will be hard to see"
        if (vsSky(sliders) < 1.8) out[Slot.SLIDERS] = "Close to the sky colour: sliders will be hard to see"
        if (vsSky(stars) < 1.5) out[Slot.STARS] = "Close to the sky colour: stars will barely show"
        if (vsSky(bars1) < 1.6) out[Slot.BARS_1] = "Close to the sky colour: bars will be hard to see"
        if (vsSky(bars2) < 1.6) out[Slot.BARS_2] = "Close to the sky colour: bars will be hard to see"
        return out
    }

    fun encode(): String = Slot.entries.joinToString(",") { "%08X".format(it.get(this)) }

    /** The parts of the theme that can be coloured, in the order the editor lists them. */
    enum class Slot(
        val label: String,
        val note: String,
        val get: (CustomPalette) -> Int,
        val set: (CustomPalette, Int) -> CustomPalette,
    ) {
        SKY_TOP("Sky, top", "Background at the top of the screen", { it.skyTop }, { p, c -> p.copy(skyTop = c) }),
        SKY_BOTTOM("Sky, bottom", "Background at the bottom", { it.skyBottom }, { p, c -> p.copy(skyBottom = c) }),
        STARS("Stars", "Background stars", { it.stars }, { p, c -> p.copy(stars = c) }),
        TEXT("Text", "Titles, song names, icons", { it.text }, { p, c -> p.copy(text = c) }),
        BUTTONS("Buttons", "Main buttons, switches, the playing song", { it.buttons }, { p, c -> p.copy(buttons = c) }),
        SLIDERS("Sliders", "Song progress and the visualizer's sliders", { it.sliders }, { p, c -> p.copy(sliders = c) }),
        BARS_1("Bars, first colour", "Visualizer ring", { it.bars1 }, { p, c -> p.copy(bars1 = c) }),
        BARS_2("Bars, second colour", "Visualizer ring blends into this", { it.bars2 }, { p, c -> p.copy(bars2 = c) }),
    }

    companion object {
        fun decode(text: String?): CustomPalette? {
            val parts = text?.split(',') ?: return null
            if (parts.size != Slot.entries.size) return null
            var p = CustomPalette()
            for ((slot, hex) in Slot.entries.zip(parts)) {
                val v = hex.trim().toLongOrNull(16) ?: return null
                p = slot.set(p, v.toInt())
            }
            return p
        }

        /** "#RRGGBB" (or "RRGGBB") to an opaque colour; null when it isn't one. */
        fun parseHex(text: String): Int? {
            val hex = text.trim().removePrefix("#")
            if (hex.length != 6) return null
            val v = hex.toLongOrNull(16) ?: return null
            return (0xFF000000L or v).toInt()
        }

        /** How far apart two colours are in brightness, from 1 (same) to 21 (black on white). */
        fun contrast(a: Int, b: Int): Double {
            val la = luminance(a)
            val lb = luminance(b)
            return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
        }

        private fun luminance(color: Int): Double {
            fun channel(shift: Int): Double {
                val c = ((color shr shift) and 0xFF) / 255.0
                return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
            }
            return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        }

        fun toHex(color: Int): String = "#%06X".format(color and 0xFFFFFF)
    }
}

/** A palette the user saved under a name. */
data class PalettePreset(val name: String, val palette: CustomPalette) {
    companion object {
        fun encodeAll(presets: List<PalettePreset>): String =
            presets.joinToString(";") { "${it.name.replace(Regex("[|;]"), " ")}|${it.palette.encode()}" }

        fun decodeAll(text: String?): List<PalettePreset> =
            text.orEmpty().split(';').mapNotNull { entry ->
                val name = entry.substringBefore('|', "").trim()
                val palette = CustomPalette.decode(entry.substringAfter('|', ""))
                if (name.isNotEmpty() && palette != null) PalettePreset(name, palette) else null
            }
    }
}

fun Int.asColor(): Color = Color(this)
