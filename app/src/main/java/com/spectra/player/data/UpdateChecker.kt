package com.spectra.player.data

import com.spectra.player.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/** A newer release on GitHub than the one installed. */
data class UpdateInfo(val tag: String, val pageUrl: String, val apkUrl: String?)

/**
 * Asks GitHub for the latest Spectra release and compares it with this build. It's a plain,
 * anonymous request to GitHub's public API: nothing about the user or their Spotify account is sent.
 */
class UpdateChecker(private val json: Json) {

    @Serializable
    private data class ReleaseDto(
        @SerialName("tag_name") val tag: String = "",
        @SerialName("html_url") val url: String = "",
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<AssetDto> = emptyList(),
    )

    @Serializable
    private data class AssetDto(
        val name: String = "",
        @SerialName("browser_download_url") val url: String = "",
    )

    /** The newer release, or null when up to date (or GitHub can't be reached). */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(LATEST).openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "Spectra")
            try {
                if (connection.responseCode != 200) return@withContext null
                val release = json.decodeFromString(ReleaseDto.serializer(), connection.inputStream.bufferedReader().readText())
                if (release.draft || release.prerelease || !isNewer(release.tag, BuildConfig.VERSION_NAME)) {
                    null
                } else {
                    UpdateInfo(
                        tag = release.tag,
                        pageUrl = release.url,
                        apkUrl = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }?.url,
                    )
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val LATEST = "https://api.github.com/repos/ethanxiang6/spectra-player/releases/latest"

        /** "v1.14.0" is newer than "1.13.0" or "1.13.0-debug.92"; anything unreadable is not. */
        fun isNewer(tag: String, installed: String): Boolean {
            val a = numbers(tag) ?: return false
            val b = numbers(installed) ?: return false
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }

        private fun numbers(version: String): List<Int>? {
            val core = version.trim().removePrefix("v").removePrefix("V").substringBefore('-')
            val parts = core.split('.').map { it.toIntOrNull() ?: return null }
            return parts.takeIf { it.isNotEmpty() }
        }
    }
}
