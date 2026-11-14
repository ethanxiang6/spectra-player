package com.spectra.player.ui.library

import com.spectra.player.data.Track
import java.text.Normalizer
import java.util.Locale

/**
 * Finds songs in a list by title, artist or album. Every word of the query has to appear
 * somewhere ("lives sapient" finds Past Lives by sapientdream). Case and accents are ignored,
 * so "oneheart" finds Øneheart and "beyonce" finds Beyoncé.
 *
 * Results keep their position in the original list, so playing one plays it in its place in
 * the playlist.
 */
object TrackFilter {
    fun filter(items: List<Track>, query: String): List<IndexedValue<Track>> {
        val words = fold(query).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return items.withIndex().toList()
        return items.withIndex().filter { (_, t) ->
            val haystack = fold("${t.name} ${t.artists} ${t.album}")
            words.all { it in haystack }
        }
    }

    /** Lower case, accents removed, and a few letters that don't decompose mapped by hand. */
    internal fun fold(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        val sb = StringBuilder(decomposed.length)
        for (c in decomposed) {
            when {
                Character.getType(c) == Character.NON_SPACING_MARK.toInt() -> Unit
                c == 'ø' -> sb.append('o')
                c == 'æ' -> sb.append("ae")
                c == 'œ' -> sb.append("oe")
                c == 'ß' -> sb.append("ss")
                c == 'ł' -> sb.append('l')
                c == 'đ' -> sb.append('d')
                c.isWhitespace() -> sb.append(' ')
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }
}
