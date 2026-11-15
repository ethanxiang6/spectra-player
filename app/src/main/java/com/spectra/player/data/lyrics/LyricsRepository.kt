package com.spectra.player.data.lyrics

import com.spectra.player.player.NowPlayingTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/** One timed line: [timeMs] is when it starts. */
data class LyricLine(val timeMs: Long, val text: String)

sealed interface Lyrics {
    data object None : Lyrics
    data class Synced(val lines: List<LyricLine>) : Lyrics

    /** Lyrics without timings: shown by estimating the place from how far the song is. */
    data class Plain(val lines: List<String>) : Lyrics
}

/**
 * Time-synced lyrics from LRCLIB (https://lrclib.net), a free, open lyrics database.
 * Spotify doesn't offer lyrics to other apps. Results are cached per song for the session.
 */
class LyricsRepository(
    private val client: OkHttpClient,
    private val json: Json,
) {
    @Serializable
    private data class LrcLibDto(
        val trackName: String? = null,
        val artistName: String? = null,
        val duration: Double? = null,
        val instrumental: Boolean = false,
        @SerialName("syncedLyrics") val synced: String? = null,
        @SerialName("plainLyrics") val plain: String? = null,
    )

    private val cache = ConcurrentHashMap<String, Lyrics>()

    suspend fun lyricsFor(track: NowPlayingTrack): Lyrics {
        cache[track.uri]?.let { return it }
        val result = try {
            withContext(Dispatchers.IO) { fetch(track) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Lyrics.None // offline or service down: try again next time
        }
        cache[track.uri] = result
        return result
    }

    private fun fetch(track: NowPlayingTrack): Lyrics {
        val artist = track.artist.substringBefore(',').trim()
        val seconds = track.durationMs / 1000.0

        // 1. Exact match on title, artist, album and length.
        val exact = "$BASE/api/get".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", track.name)
            .addQueryParameter("artist_name", artist)
            .addQueryParameter("album_name", track.album)
            .addQueryParameter("duration", Math.round(seconds).toString())
            .build()
        var plain: Lyrics.Plain? = null
        get(exact.toString())?.let { body ->
            val dto = json.decodeFromString<LrcLibDto>(body)
            parse(dto.synced)?.let { return it }
            plain = parsePlain(dto.plain)
        }

        // 2. Search by title and artist; take the closest length that has synced lyrics.
        val search = "$BASE/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", track.name)
            .addQueryParameter("artist_name", artist)
            .build()
        val body = get(search.toString()) ?: return plain ?: Lyrics.None
        val close = json.decodeFromString<List<LrcLibDto>>(body)
            .filter { it.duration == null || seconds <= 0 || abs(it.duration - seconds) < 4 }
            .sortedBy { abs((it.duration ?: seconds) - seconds) }
        parse(close.firstOrNull { !it.synced.isNullOrBlank() }?.synced)?.let { return it }
        // No timed lyrics anywhere: fall back to plain text.
        return plain ?: close.firstNotNullOfOrNull { parsePlain(it.plain) } ?: Lyrics.None
    }

    private fun get(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Spectra (https://github.com/ethanxiang6/spectra-player)")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    companion object {
        private const val BASE = "https://lrclib.net"
        private val TAG = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")

        /** Plain lyrics as non-empty lines, or null when there are too few to be worth showing. */
        fun parsePlain(text: String?): Lyrics.Plain? {
            val lines = text.orEmpty().lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
            return if (lines.size >= 4) Lyrics.Plain(lines) else null
        }

        /**
         * Which plain line to show at [positionMs]: lines are spread evenly over the middle of the
         * song (singing rarely starts in the first seconds or runs to the very end).
         */
        fun plainIndex(lineCount: Int, positionMs: Long, durationMs: Long): Int {
            if (lineCount == 0 || durationMs <= 0) return -1
            val start = durationMs * 0.06f
            val end = durationMs * 0.94f
            if (positionMs < start) return -1
            val f = ((positionMs - start) / (end - start)).coerceIn(0f, 0.9999f)
            return (f * lineCount).toInt()
        }

        /** Parses LRC text ("[mm:ss.xx] line"; a line may carry several timestamps). */
        fun parse(lrc: String?): Lyrics.Synced? {
            if (lrc.isNullOrBlank()) return null
            val lines = mutableListOf<LyricLine>()
            for (raw in lrc.lineSequence()) {
                val stamps = TAG.findAll(raw).toList()
                if (stamps.isEmpty()) continue
                val text = raw.substring(stamps.last().range.last + 1).trim()
                for (m in stamps) {
                    val min = m.groupValues[1].toLong()
                    val sec = m.groupValues[2].toLong()
                    val frac = m.groupValues[3]
                    val ms = when (frac.length) {
                        0 -> 0L
                        1 -> frac.toLong() * 100
                        2 -> frac.toLong() * 10
                        else -> frac.take(3).toLong()
                    }
                    lines += LyricLine(min * 60_000 + sec * 1000 + ms, text)
                }
            }
            lines.sortBy { it.timeMs }
            return if (lines.any { it.text.isNotEmpty() }) Lyrics.Synced(lines) else null
        }
    }
}
