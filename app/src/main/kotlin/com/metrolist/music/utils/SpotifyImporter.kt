package com.metrolist.music.utils

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.models.toMediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

data class SpotifyImportTrack(
    val title: String,
    val artist: String,
)

data class SpotifyImportSummary(
    val playlistId: String,
    val playlistTitle: String,
    val totalTracks: Int,
    val importedCount: Int,
)

object SpotifyImporter {
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val PLAYLIST_ID = Regex("""[A-Za-z0-9]{22}""")
    private val NEXT_DATA_REGEX = Regex(
        """<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""",
        RegexOption.DOT_MATCHES_ALL,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private fun isSpotifyHost(host: String): Boolean =
        host == "spotify.com" || host.endsWith(".spotify.com")

    fun extractPlaylistId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        if (trimmed.startsWith("spotify:playlist:")) {
            val id = trimmed.removePrefix("spotify:playlist:").substringBefore("?").substringBefore("/")
            return id.takeIf { PLAYLIST_ID.matches(it) }
        }

        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
        if (!isSpotifyHost(uri.host?.lowercase().orEmpty())) return null
        val segments = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        val playlistIdx = segments.indexOf("playlist")
        if (playlistIdx == -1 || playlistIdx + 1 >= segments.size) return null
        return segments[playlistIdx + 1].takeIf { PLAYLIST_ID.matches(it) }
    }

    suspend fun fetchPlaylistTracks(playlistId: String): Pair<String, List<SpotifyImportTrack>> =
        withContext(Dispatchers.IO) {
            val url = "https://open.spotify.com/embed/playlist/$playlistId"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            val html = httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful || body.isBlank()) {
                    error("Gagal memuat playlist Spotify (HTTP ${response.code}). Pastikan playlist bersifat publik.")
                }
                body
            }

            val match = NEXT_DATA_REGEX.find(html)
                ?: error("Metadata playlist Spotify tidak ditemukan. Pastikan link valid dan playlist publik.")

            val root = json.parseToJsonElement(match.groupValues[1]).jsonObject
            val props = root["props"]?.jsonObject?.get("pageProps")?.jsonObject
            val stateData = props?.get("state")?.jsonObject
                ?.get("data")?.jsonObject
                ?.get("entity")?.jsonObject
                ?: props?.get("entity")?.jsonObject
                ?: error("Playlist Spotify tidak dapat diakses atau bersifat privat.")

            val playlistTitle = stateData["name"]?.jsonPrimitive?.contentOrNull
                ?: stateData["title"]?.jsonPrimitive?.contentOrNull
                ?: "Spotify Import"

            val trackListJson = stateData["trackList"]?.jsonArray
                ?: stateData["tracks"]?.jsonArray
                ?: JsonArray(emptyList())

            val tracks = mutableListOf<SpotifyImportTrack>()
            for (element in trackListJson) {
                val obj = element.jsonObject
                val title = obj["title"]?.jsonPrimitive?.contentOrNull
                    ?: obj["name"]?.jsonPrimitive?.contentOrNull
                    ?: continue
                val subtitle = obj["subtitle"]?.jsonPrimitive?.contentOrNull
                    ?: obj["artists"]?.jsonArray?.joinToString(", ") {
                        it.jsonObject["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    }
                    ?: ""
                if (title.isNotBlank()) {
                    tracks.add(SpotifyImportTrack(title = title.trim(), artist = subtitle.trim()))
                }
            }

            if (tracks.isEmpty()) {
                error("Tidak ada lagu yang ditemukan di playlist Spotify tersebut.")
            }

            playlistTitle to tracks
        }

    suspend fun importPlaylist(
        urlInput: String,
        database: MusicDatabase,
        onProgress: (completed: Int, total: Int, currentTrack: String) -> Unit,
    ): SpotifyImportSummary = withContext(Dispatchers.IO) {
        val playlistId = extractPlaylistId(urlInput)
            ?: error("Link playlist Spotify tidak valid. Contoh: https://open.spotify.com/playlist/...")

        val (playlistTitle, tracks) = fetchPlaylistTracks(playlistId)
        val total = tracks.size
        val semaphore = Semaphore(4)
        val completed = AtomicInteger(0)

        val resolvedIds: List<String?> = coroutineScope {
            tracks.map { track ->
                async {
                    semaphore.withPermit {
                        val query = if (track.artist.isNotBlank()) {
                            "${track.title} - ${track.artist}"
                        } else {
                            track.title
                        }
                        var foundId: String? = null
                        YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).onSuccess { result ->
                            val firstSong = result.items.distinctBy { it.id }.firstOrNull() as? SongItem
                            if (firstSong != null) {
                                runCatching {
                                    database.insert(firstSong.toMediaMetadata())
                                }
                                foundId = firstSong.id
                            }
                        }
                        val done = completed.incrementAndGet()
                        withContext(Dispatchers.Main) {
                            onProgress(done, total, track.title)
                        }
                        foundId
                    }
                }
            }.awaitAll()
        }

        val matchedIds = resolvedIds.filterNotNull()
        if (matchedIds.isEmpty()) {
            error("Tidak ada lagu dari playlist \"$playlistTitle\" yang cocok di YouTube Music.")
        }

        val entity = PlaylistEntity(
            name = playlistTitle,
            bookmarkedAt = LocalDateTime.now(),
            isEditable = true,
        )
        database.query {
            insert(entity)
        }
        database.addSongsToPlaylist(
            Playlist(playlist = entity, songCount = 0, songThumbnails = emptyList()),
            matchedIds.map { it to null },
        )

        SpotifyImportSummary(
            playlistId = entity.id,
            playlistTitle = playlistTitle,
            totalTracks = total,
            importedCount = matchedIds.size,
        )
    }
}
