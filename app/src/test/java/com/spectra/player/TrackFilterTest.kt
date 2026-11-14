package com.spectra.player

import com.spectra.player.data.Track
import com.spectra.player.ui.library.TrackFilter
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackFilterTest {
    private fun t(name: String, artist: String, album: String = "") =
        Track(null, "spotify:track:$name", name, artist, album, null, 1000L)

    private val list = listOf(
        t("Blurry Nights", "BoyWithUke"),
        t("Past Lives", "sapientdream", "Past Lives"),
        t("Snowfall", "Øneheart, reidenshi"),
        t("Halo", "Beyoncé", "I Am... Sasha Fierce"),
        t("少女A", "椎名もた"),
    )

    private fun names(q: String) = TrackFilter.filter(list, q).map { it.value.name }

    @Test fun blankQueryKeepsEverythingInOrder() {
        assertEquals(list.map { it.name }, names("  "))
    }

    @Test fun matchesTitleAndArtistWords() {
        assertEquals(listOf("Past Lives"), names("lives sapient"))
        assertEquals(listOf("Blurry Nights"), names("boywith"))
    }

    @Test fun ignoresCaseAndAccents() {
        assertEquals(listOf("Snowfall"), names("ONEHEART"))
        assertEquals(listOf("Halo"), names("beyonce"))
    }

    @Test fun matchesAlbumAndNonLatin() {
        assertEquals(listOf("Halo"), names("sasha"))
        assertEquals(listOf("少女A"), names("少女"))
    }

    @Test fun keepsOriginalPositions() {
        assertEquals(listOf(2), TrackFilter.filter(list, "snow").map { it.index })
    }

    @Test fun noMatch() {
        assertEquals(emptyList<String>(), names("zzz"))
    }
}
