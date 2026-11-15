package com.spectra.player

import com.spectra.player.data.UpdateChecker
import com.spectra.player.data.lyrics.LyricsRepository
import com.spectra.player.ui.theme.CustomPalette
import com.spectra.player.ui.theme.PalettePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateAndPaletteTest {
    @Test fun newerVersions() {
        assertTrue(UpdateChecker.isNewer("v1.14.0", "1.13.0"))
        assertTrue(UpdateChecker.isNewer("v1.14.0", "1.13.0-debug.92"))
        assertTrue(UpdateChecker.isNewer("v2.0", "1.99.9"))
        assertTrue(UpdateChecker.isNewer("v1.10.0", "1.9.0"))
    }

    @Test fun sameOrOlderOrUnreadable() {
        assertFalse(UpdateChecker.isNewer("v1.13.0", "1.13.0"))
        assertFalse(UpdateChecker.isNewer("v1.12.0", "1.13.0"))
        assertFalse(UpdateChecker.isNewer("ui-preview", "1.13.0"))
    }

    @Test fun paletteRoundTrip() {
        val p = CustomPalette(skyTop = 0xFF102030.toInt(), bars2 = 0xFFABCDEF.toInt())
        assertEquals(p, CustomPalette.decode(p.encode()))
        assertNull(CustomPalette.decode("nope"))
        val presets = listOf(PalettePreset("Sea | glass; one", p), PalettePreset("Two", CustomPalette()))
        val back = PalettePreset.decodeAll(PalettePreset.encodeAll(presets))
        assertEquals(2, back.size)
        assertEquals(p, back[0].palette)
        assertEquals("Two", back[1].name)
    }

    @Test fun hexParsing() {
        assertEquals(0xFF1A2B3C.toInt(), CustomPalette.parseHex("#1a2b3c"))
        assertEquals(0xFFFFFFFF.toInt(), CustomPalette.parseHex("FFFFFF"))
        assertNull(CustomPalette.parseHex("#12"))
        assertEquals("#1A2B3C", CustomPalette.toHex(0xFF1A2B3C.toInt()))
    }

    @Test fun plainLyrics() {
        assertNull(LyricsRepository.parsePlain("one\ntwo"))
        assertEquals(4, LyricsRepository.parsePlain("a\n\nb\n c \nd")!!.lines.size)
        assertEquals(-1, LyricsRepository.plainIndex(10, 1_000, 200_000))
        assertEquals(0, LyricsRepository.plainIndex(10, 13_000, 200_000))
        assertEquals(9, LyricsRepository.plainIndex(10, 199_000, 200_000))
    }
}
