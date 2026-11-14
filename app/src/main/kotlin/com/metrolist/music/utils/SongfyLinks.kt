package com.metrolist.music.utils

import android.net.Uri

/**
 * Canonical public share URLs and deep-link path normalization for Songfy.
 * Recipients without the app land on GitHub Pages with APK download and fallback options.
 */
object SongfyLinks {
    const val WEB_HOST = "rasya457.github.io"
    const val WEB_PATH_PREFIX = "/Songfy"
    const val BASE_URL = "https://$WEB_HOST$WEB_PATH_PREFIX"
    const val SCHEME = "songfy"

    fun watchUrl(videoId: String?): String =
        videoId?.takeIf { it.isNotBlank() }?.let { "$BASE_URL/watch?v=$it" } ?: BASE_URL

    fun playlistUrl(listId: String?): String =
        listId?.takeIf { it.isNotBlank() }?.let { "$BASE_URL/playlist?list=$it" } ?: BASE_URL

    fun artistUrl(channelId: String?): String =
        channelId?.takeIf { it.isNotBlank() }?.let { "$BASE_URL/channel/$it" } ?: BASE_URL

    fun fromYouTubeUrl(url: String): String =
        when {
            url.startsWith("https://music.youtube.com/") ->
                "$BASE_URL/${url.removePrefix("https://music.youtube.com/")}"
            url.startsWith("https://www.youtube.com/") ->
                "$BASE_URL/${url.removePrefix("https://www.youtube.com/")}"
            url.startsWith("https://youtube.com/") ->
                "$BASE_URL/${url.removePrefix("https://youtube.com/")}"
            else -> url
        }

    /** Normalizes GitHub Pages (/Songfy/...) and songfy://... URIs to standard YT path segments. */
    fun normalizePathSegments(uri: Uri): List<String> =
        when {
            uri.scheme.equals(SCHEME, ignoreCase = true) -> {
                val host = uri.host?.takeIf { it.isNotBlank() && !it.equals("open", ignoreCase = true) }
                listOfNotNull(host) + uri.pathSegments
            }
            uri.host.equals(WEB_HOST, ignoreCase = true) &&
                uri.pathSegments.firstOrNull().equals("Songfy", ignoreCase = true) -> {
                uri.pathSegments.drop(1)
            }
            else -> uri.pathSegments
        }
}

