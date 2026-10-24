package com.spectra.player

import com.spectra.player.data.lyrics.LyricsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsParseTest {
    @Test
    fun parsesTimestampsInOrderIncludingRepeatedLines() {
        val lrc = """
            [ar: Someone]
            [00:12.50] First line
            [00:15.2]Second line
            [01:02.345][00:20.00] Chorus
            [00:30.00]
        """.trimIndent()
        val lines = LyricsRepository.parse(lrc)!!.lines
        assertEquals(listOf(12_500L, 15_200L, 20_000L, 30_000L, 62_345L), lines.map { it.timeMs })
        assertEquals(listOf("First line", "Second line", "Chorus", "", "Chorus"), lines.map { it.text })
    }

    @Test
    fun noTimestampsMeansNoSyncedLyrics() {
        assertNull(LyricsRepository.parse("just plain words\nmore words"))
        assertNull(LyricsRepository.parse(null))
    }
}
