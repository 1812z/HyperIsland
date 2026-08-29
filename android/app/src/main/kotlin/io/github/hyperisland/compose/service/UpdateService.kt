package io.github.hyperisland.compose.service

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class AppUpdate(
    val version: String,
    val releaseUrl: String,
    val changelog: String,
)

internal object UpdateService {
    suspend fun fetchIfNewer(currentVersion: String): AppUpdate? = withContext(Dispatchers.IO) {
        val connection = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = NETWORK_TIMEOUT_MILLIS
            readTimeout = NETWORK_TIMEOUT_MILLIS
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "HyperIsland/$currentVersion")
        }
        try {
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("GitHub release request failed with HTTP $responseCode")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val release = JSONObject(body)
            val remoteVersion = release.optString("tag_name").removePrefix("v")
            if (remoteVersion.isBlank() || !isNewer(remoteVersion, currentVersion)) {
                return@withContext null
            }
            AppUpdate(
                version = remoteVersion,
                releaseUrl = release.optString("html_url").ifBlank { RELEASES_URL },
                changelog = release.optString("body"),
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun isNewer(remote: String, current: String): Boolean {
        val remoteParts = versionParts(remote)
        val currentParts = versionParts(current)
        for (index in 0 until VERSION_PART_COUNT) {
            if (remoteParts[index] > currentParts[index]) return true
            if (remoteParts[index] < currentParts[index]) return false
        }
        return false
    }

    private fun versionParts(version: String): List<Int> = version
        .removePrefix("v")
        .split('.')
        .take(VERSION_PART_COUNT)
        .map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        .let { parts -> List(VERSION_PART_COUNT) { index -> parts.getOrElse(index) { 0 } } }
}

private const val LATEST_RELEASE_API =
    "https://api.github.com/repos/1812z/HyperIsland/releases/latest"
private const val RELEASES_URL = "https://github.com/1812z/HyperIsland/releases"
private const val NETWORK_TIMEOUT_MILLIS = 10_000
private const val VERSION_PART_COUNT = 3
