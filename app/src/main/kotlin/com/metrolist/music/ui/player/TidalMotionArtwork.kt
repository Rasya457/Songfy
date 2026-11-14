package com.metrolist.music.ui.player

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import android.util.LruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

internal object TidalMotionArtwork {
    private const val EMBED_TOKEN = "vNVdglQOjFJJGG2U"
    private val client by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 5_000
                connectTimeoutMillis = 3_000
                socketTimeoutMillis = 5_000
            }
            expectSuccess = false
        }
    }
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val cache = LruCache<String, String>(64)

    suspend fun find(title: String, artist: String, album: String?): String? = withContext(Dispatchers.IO) {
        val key = "${title.lowercase(Locale.ROOT)}|${artist.lowercase(Locale.ROOT)}"
        cache.get(key)?.let { return@withContext it }
        try {
            val response = client.get("https://api.tidal.com/v1/search") {
                parameter("query", listOfNotNull(album?.takeIf(String::isNotBlank), artist, title).joinToString(" "))
                parameter("limit", "10")
                parameter("types", "TRACKS")
                parameter("countryCode", Locale.getDefault().country.takeIf { it.length == 2 } ?: "US")
                header("X-Tidal-Token", EMBED_TOKEN)
                header("User-Agent", "Songfy Android")
            }
            if (!response.status.isSuccess()) return@withContext null
            val tracks = json.parseToJsonElement(response.bodyAsText()).jsonObject
                .getValue("tracks").jsonObject["items"]?.jsonArray ?: return@withContext null
            val wantedTitle = normalize(title)
            val wantedArtist = normalize(artist)
            val wantedAlbum = album?.let(::normalize).orEmpty()
            for (entry in tracks) {
                val track = entry.jsonObject
                val trackTitle = track["title"]?.jsonPrimitive?.contentOrNull ?: continue
                val artists = track["artists"]?.jsonArray?.mapNotNull {
                    it.jsonObject["name"]?.jsonPrimitive?.contentOrNull
                }.orEmpty()
                val albumObject = track["album"]?.jsonObject ?: continue
                val albumTitle = albumObject["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (normalize(trackTitle) != wantedTitle || artists.none { normalize(it) == wantedArtist }) continue
                if (wantedAlbum.isNotBlank() && normalize(albumTitle) != wantedAlbum) continue
                val id = albumObject["videoCover"]?.jsonPrimitive?.contentOrNull ?: continue
                val parts = id.split('-')
                if (parts.size != 5 || parts.any { !it.matches(Regex("[A-Za-z0-9]+")) }) continue
                val url = "https://resources.tidal.com/videos/${parts.joinToString("/")}/1280x1280.mp4"
                cache.put(key, url)
                return@withContext url
            }
            null
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    private fun normalize(value: String) = value.lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
}
