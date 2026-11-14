package com.metrolist.music.lyrics

import android.content.Context
import android.text.Html
import com.metrolist.music.betterlyrics.TTMLParser
import com.metrolist.music.constants.EnableAdditionalLyricsSourcesKey
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException

private suspend fun <T> lyricResult(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    Result.failure(error)
}

private object SupplementalLyricsHttp {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 6_500
                connectTimeoutMillis = 4_000
                socketTimeoutMillis = 6_500
            }
            expectSuccess = false
        }
    }

    suspend fun get(url: String, configure: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {}): String? =
        runCatching {
            client.get(url) {
                header("User-Agent", "Songfy/1.0 (Android)")
                configure()
            }.takeIf { it.status.isSuccess() }?.bodyAsText()
        }.getOrNull()

    fun parseJson(body: String) = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()

    fun normalize(value: String) = value.lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    fun plainLyrics(text: String): String = text.lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .joinToString("\n")
}

internal abstract class SupplementalLyricsProvider : LyricsProvider {
    override fun isEnabled(context: Context): Boolean =
        context.dataStore[EnableAdditionalLyricsSourcesKey] ?: true
}

internal object LrcRedLyricsProvider : SupplementalLyricsProvider() {
    override val name = "lrc.red"

    override suspend fun getLyrics(
        context: Context, id: String, title: String, artist: String, duration: Int, album: String?,
    ): Result<String> = lyricResult {
        val search = SupplementalLyricsHttp.get("https://lrc.red/search.json") {
            parameter("q", "$title $artist")
        } ?: error("Search failed")
        val hits = SupplementalLyricsHttp.parseJson(search)?.get("hits")?.jsonArray ?: error("No matches")
        val wantedTitle = SupplementalLyricsHttp.normalize(title)
        val wantedArtist = SupplementalLyricsHttp.normalize(artist)
        val isrc = hits.mapNotNull { it.jsonObject }.firstNotNullOfOrNull { hit ->
            val hitTitle = SupplementalLyricsHttp.normalize(hit["title"]?.jsonPrimitive?.contentOrNull.orEmpty())
            val hitArtist = SupplementalLyricsHttp.normalize(hit["artist"]?.jsonPrimitive?.contentOrNull.orEmpty())
            val hitIsrc = hit["isrc"]?.jsonPrimitive?.contentOrNull.orEmpty().uppercase()
            hitIsrc.takeIf {
                hitTitle == wantedTitle && hitArtist.contains(wantedArtist) &&
                    Regex("[A-Z]{2}[A-Z0-9]{3}\\d{7}").matches(it)
            }
        } ?: error("No exact match")
        val ttml = SupplementalLyricsHttp.get("https://lrc.red/s/$isrc.ttml") ?: error("Lyrics unavailable")
        TTMLParser.toLRC(TTMLParser.parseTTML(ttml)).takeIf(String::isNotBlank) ?: error("Empty lyrics")
    }
}

internal object UnisonLyricsProvider : SupplementalLyricsProvider() {
    override val name = "Unison"

    override suspend fun getLyrics(
        context: Context, id: String, title: String, artist: String, duration: Int, album: String?,
    ): Result<String> = lyricResult {
        val body = SupplementalLyricsHttp.get("https://unison.boidu.dev/lyrics") {
            parameter("song", title)
            parameter("artist", artist)
            if (!album.isNullOrBlank()) parameter("album", album)
            if (duration > 0) parameter("duration", duration.toString())
        } ?: error("Search failed")
        val data = SupplementalLyricsHttp.parseJson(body)?.get("data")?.jsonObject ?: error("No lyrics")
        if (SupplementalLyricsHttp.parseJson(body)?.get("success")?.jsonPrimitive?.contentOrNull != "true") {
            error("No lyrics")
        }
        val lyrics = data["lyrics"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
            ?: error("Empty lyrics")
        when {
            data["format"]?.jsonPrimitive?.contentOrNull.equals("ttml", true) ->
                TTMLParser.toLRC(TTMLParser.parseTTML(lyrics)).takeIf(String::isNotBlank) ?: error("Invalid TTML")
            data["syncType"]?.jsonPrimitive?.contentOrNull.equals("plain", true) ->
                SupplementalLyricsHttp.plainLyrics(lyrics)
            else -> lyrics
        }
    }
}

internal object MegalobizLyricsProvider : SupplementalLyricsProvider() {
    override val name = "Megalobiz"
    private val link = Regex("""href=[\"'](/lrc/maker/download/[^\"']+)[\"']""", RegexOption.IGNORE_CASE)
    private val lyricBody = Regex(
        """id=[\"']lrc_[^\"']*_details[\"'][^>]*>(.*?)</span>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    override suspend fun getLyrics(
        context: Context, id: String, title: String, artist: String, duration: Int, album: String?,
    ): Result<String> = lyricResult {
        val search = SupplementalLyricsHttp.get("https://www.megalobiz.com/searchall") {
            parameter("qry", "$artist $title")
        } ?: error("Search failed")
        val path = link.find(search)?.groupValues?.get(1)?.replace("&amp;", "&") ?: error("No match")
        val page = SupplementalLyricsHttp.get("https://www.megalobiz.com$path") ?: error("Page unavailable")
        val raw = lyricBody.find(page)?.groupValues?.get(1) ?: error("No lyrics")
        Html.fromHtml(raw.replace(Regex("(?i)<br\\s*/?>"), "<br>"), Html.FROM_HTML_MODE_LEGACY)
            .toString().trim().takeIf(String::isNotBlank) ?: error("Empty lyrics")
    }
}

internal object GeniusLyricsProvider : SupplementalLyricsProvider() {
    override val name = "Genius"

    override suspend fun getLyrics(
        context: Context, id: String, title: String, artist: String, duration: Int, album: String?,
    ): Result<String> = lyricResult {
        val query = URLEncoder.encode("$artist $title", Charsets.UTF_8.name())
        val search = SupplementalLyricsHttp.get("https://genius.com/api/search/multi?q=$query")
            ?: error("Search failed")
        val sections = SupplementalLyricsHttp.parseJson(search)?.get("response")?.jsonObject
            ?.get("sections")?.jsonArray ?: error("No matches")
        val hits = sections.firstOrNull {
            it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "song"
        }?.jsonObject?.get("hits")?.jsonArray ?: error("No songs")
        val wantedTitle = SupplementalLyricsHttp.normalize(title)
        val wantedArtist = SupplementalLyricsHttp.normalize(artist)
        val url = hits.mapNotNull { it.jsonObject["result"]?.jsonObject }.firstNotNullOfOrNull { hit ->
            val hitTitle = SupplementalLyricsHttp.normalize(hit["title"]?.jsonPrimitive?.contentOrNull.orEmpty())
            val hitArtist = SupplementalLyricsHttp.normalize(hit["artist_names"]?.jsonPrimitive?.contentOrNull.orEmpty())
            hit["url"]?.jsonPrimitive?.contentOrNull?.takeIf {
                hitTitle == wantedTitle && hitArtist.contains(wantedArtist)
            }
        } ?: error("No exact match")
        val html = SupplementalLyricsHttp.get(url) ?: error("Song page unavailable")
        val containers = Regex(
            """<div[^>]*data-lyrics-container=[\"']true[\"'][^>]*>(.*?)</div>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).findAll(html).joinToString("\n") { it.groupValues[1] }
        Html.fromHtml(containers, Html.FROM_HTML_MODE_LEGACY).toString().trim()
            .takeIf(String::isNotBlank) ?: error("No lyrics text")
    }
}
