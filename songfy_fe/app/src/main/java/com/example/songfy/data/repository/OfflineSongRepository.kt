package com.lagu.songfy.data.repository

import android.content.Context
import androidx.core.content.FileProvider
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.lagu.songfy.data.local.datastore.PlaylistPreferences
import com.lagu.songfy.data.remote.api.SongfyApiService
import com.lagu.songfy.data.remote.dto.SongDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class OfflineSongRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: SongfyApiService,           // default (60s) — for search
    @Named("download") private val downloadApiService: SongfyApiService, // long timeout — for download
    private val playlistPreferences: PlaylistPreferences,
    private val firebaseAuth: FirebaseAuth
) {
    private val tag = "OfflineSongRepo"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private fun activeUid(): String = firebaseAuth.currentUser?.uid ?: ""

    private val legacyOfflineDir: File
        get() = File(context.filesDir, "offline_songs")

    private val legacyMetadataFile: File
        get() = File(context.filesDir, "offline_songs_meta.json")

    private fun getOfflineDir(uid: String = activeUid()): File {
        val safeUid = uid.ifBlank { "anonymous" }
        return File(context.filesDir, "users/$safeUid/offline_songs").apply {
            if (!exists()) mkdirs()
        }
    }

    private fun getMetadataFile(uid: String = activeUid()): File {
        val safeUid = uid.ifBlank { "anonymous" }
        val userDir = File(context.filesDir, "users/$safeUid").apply {
            if (!exists()) mkdirs()
        }
        return File(userDir, "offline_songs_meta.json")
    }

    private val _downloadedSongs = MutableStateFlow<List<SongDto>>(emptyList())
    val downloadedSongs: StateFlow<List<SongDto>> = _downloadedSongs.asStateFlow()

    private val _downloadedSongIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedSongIds: StateFlow<Set<String>> = _downloadedSongIds.asStateFlow()

    private val _downloadingSongIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingSongIds: StateFlow<Set<String>> = _downloadingSongIds.asStateFlow()

    init {
        val initialUid = firebaseAuth.currentUser?.uid
        if (initialUid != null) {
            migrateLegacyDownloads(initialUid)
            loadOfflineMetadata(initialUid)
        }

        firebaseAuth.addAuthStateListener { auth ->
            val uid = auth.currentUser?.uid
            if (uid != null) {
                migrateLegacyDownloads(uid)
                loadOfflineMetadata(uid)
            } else {
                _downloadedSongs.value = emptyList()
                _downloadedSongIds.value = emptySet()
            }
        }
    }

    /**
     * Memindahkan data unduhan lama (global) ke direktori user yang sedang aktif
     * agar data lagu offline user tidak hilang saat migrasi partisi UID.
     */
    private fun migrateLegacyDownloads(targetUid: String) {
        if (targetUid.isBlank()) return
        try {
            val targetDir = getOfflineDir(targetUid)
            val targetMeta = getMetadataFile(targetUid)

            if (legacyOfflineDir.exists()) {
                val legacyFiles = legacyOfflineDir.listFiles() ?: emptyArray()
                for (file in legacyFiles) {
                    val dest = File(targetDir, file.name)
                    if (!dest.exists()) {
                        file.copyTo(dest, overwrite = true)
                    }
                    file.delete()
                }
                legacyOfflineDir.delete()
            }

            if (legacyMetadataFile.exists()) {
                if (!targetMeta.exists()) {
                    legacyMetadataFile.copyTo(targetMeta, overwrite = true)
                }
                legacyMetadataFile.delete()
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to migrate legacy downloads", e)
        }
    }

    fun loadOfflineMetadata(uid: String = activeUid()) {
        scope.launch {
            try {
                val metaFile = getMetadataFile(uid)
                val userOfflineDir = getOfflineDir(uid)

                if (metaFile.exists()) {
                    val raw = metaFile.readText()
                    if (raw.isNotBlank()) {
                        val list = json.decodeFromString<List<SongDto>>(raw)
                        val valid = list.filter { song ->
                            val f = File(userOfflineDir, "${song.videoId}.m4a")
                            f.exists() && f.length() > 1024L
                        }
                        _downloadedSongs.value = valid
                        _downloadedSongIds.value = valid.map { it.videoId }.toSet()
                        return@launch
                    }
                }

                val files = userOfflineDir.listFiles() ?: emptyArray()
                val ids = files.filter { it.extension == "m4a" && it.length() > 1024L }
                    .map { it.nameWithoutExtension }.toSet()
                _downloadedSongIds.value = ids
                _downloadedSongs.value = emptyList()
            } catch (e: Exception) {
                Log.e(tag, "Failed to load offline metadata for $uid", e)
            }
        }
    }

    private fun saveOfflineMetadata(list: List<SongDto>, uid: String = activeUid()) {
        try {
            getMetadataFile(uid).writeText(json.encodeToString(list))
        } catch (e: Exception) {
            Log.e(tag, "Failed to save offline metadata for $uid", e)
        }
    }

    fun isDownloaded(videoId: String): Boolean {
        if (_downloadedSongIds.value.contains(videoId)) return true
        val f = File(getOfflineDir(), "$videoId.m4a")
        return f.exists() && f.length() > 1024L
    }

    fun getOfflineFile(videoId: String): File? {
        val f = File(getOfflineDir(), "$videoId.m4a")
        return if (f.exists() && f.length() > 1024L) f else null
    }

    /**
     * Kembalikan content:// URI untuk file offline menggunakan FileProvider.
     * WAJIB pakai content:// (bukan file://) karena Android 7+ memblokir file:// URI
     * antar proses — ExoPlayer berjalan di context berbeda dan perlu content URI.
     */
    fun getOfflineAudioUri(videoId: String): android.net.Uri? {
        val file = getOfflineFile(videoId) ?: return null
        return try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            Log.e(tag, "FileProvider getUriForFile failed for $videoId, fallback to file://: ${e.message}")
            // Fallback ke file:// untuk Android 6 ke bawah (tidak ada StrictMode)
            android.net.Uri.fromFile(file)
        }
    }

    suspend fun downloadSong(song: SongDto): Boolean = withContext(Dispatchers.IO) {
        val currentUid = activeUid()
        val userOfflineDir = getOfflineDir(currentUid)

        if (isDownloaded(song.videoId)) {
            ensureAddedToOfflinePlaylist(song, currentUid)
            return@withContext true
        }

        _downloadingSongIds.update { it + song.videoId }

        val targetFile = File(userOfflineDir, "${song.videoId}.m4a")
        val tempFile = File(userOfflineDir, "${song.videoId}.m4a.tmp")

        try {
            Log.d(tag, "Downloading via backend proxy for ${song.title} (${song.videoId})")
            val response = downloadApiService.downloadAudio(
                videoId = song.videoId,
                title = song.title
            )

            if (!response.isSuccessful) {
                Log.e(tag, "Backend proxy download failed: HTTP ${response.code()} for ${song.videoId}")

                // Fallback: try search for a matching video ID and download that
                val query = "${song.title} ${song.artists.joinToString(" ") { it.name }}".trim()
                if (query.isNotBlank()) {
                    Log.d(tag, "Trying search fallback for: $query")
                    val searchRes = runCatching { apiService.search(query = query, filter = "songs") }.getOrNull()
                    val fallbackId = searchRes?.songs?.firstOrNull()?.videoId
                    if (fallbackId != null && fallbackId != song.videoId) {
                        Log.d(tag, "Fallback: downloading $fallbackId instead of ${song.videoId}")
                        val fallbackResponse = downloadApiService.downloadAudio(videoId = fallbackId, title = song.title)
                        if (fallbackResponse.isSuccessful) {
                            return@withContext writeResponseToFile(
                                body = fallbackResponse.body(),
                                tempFile = tempFile,
                                targetFile = targetFile,
                                song = song,
                                uid = currentUid,
                                tag = tag
                            )
                        }
                    }
                }
                return@withContext false
            }

            return@withContext writeResponseToFile(
                body = response.body(),
                tempFile = tempFile,
                targetFile = targetFile,
                song = song,
                uid = currentUid,
                tag = tag
            )
        } catch (e: Exception) {
            Log.e(tag, "Download failed for ${song.videoId}: ${e.javaClass.simpleName} — ${e.message}")
            try { tempFile.delete() } catch (_: Exception) {}
            return@withContext false
        } finally {
            _downloadingSongIds.update { it - song.videoId }
        }
    }

    private suspend fun writeResponseToFile(
        body: okhttp3.ResponseBody?,
        tempFile: File,
        targetFile: File,
        song: SongDto,
        uid: String,
        tag: String
    ): Boolean {
        if (body == null) {
            Log.e(tag, "Response body null for ${song.videoId}")
            return false
        }

        var downloadedBytes = 0L
        body.byteStream().use { input ->
            FileOutputStream(tempFile).use { output ->
                val buffer = ByteArray(64 * 1024)  // 64KB buffer — matches backend chunk size
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                }
                output.flush()
            }
        }

        Log.d(tag, "Downloaded ${downloadedBytes / 1024}KB for ${song.title}")

        if (tempFile.length() < 1024L) {
            Log.e(tag, "Downloaded file too small (${tempFile.length()} bytes) — aborting")
            tempFile.delete()
            return false
        }

        if (targetFile.exists()) targetFile.delete()
        val renamed = tempFile.renameTo(targetFile)
        if (!renamed) {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
        }

        val currentList = _downloadedSongs.value.filterNot { it.videoId == song.videoId }
        val updatedList = currentList + song
        _downloadedSongs.value = updatedList
        _downloadedSongIds.update { it + song.videoId }
        saveOfflineMetadata(updatedList, uid)

        ensureAddedToOfflinePlaylist(song, uid)

        Log.i(tag, "Song downloaded successfully: ${song.title} (${targetFile.length() / 1024}KB) for user $uid")
        return true
    }

    private suspend fun ensureAddedToOfflinePlaylist(song: SongDto, uid: String = activeUid()) {
        try {
            if (uid.isBlank()) return
            val userPlaylists = playlistPreferences.getUserPlaylists(uid).first()
            var offlinePlaylist = userPlaylists.firstOrNull { it.title.equals("Lagu Offline", ignoreCase = true) }

            if (offlinePlaylist == null) {
                offlinePlaylist = playlistPreferences.createPlaylist(
                    title = "Lagu Offline",
                    description = "Kumpulan lagu yang diunduh untuk diputar offline",
                    uid = uid
                )
            }

            playlistPreferences.addSongToPlaylist(offlinePlaylist.id, song, uid)
        } catch (e: Exception) {
            Log.e(tag, "Failed to add song to Lagu Offline playlist", e)
        }
    }

    suspend fun deleteOfflineSong(videoId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentUid = activeUid()
            val userOfflineDir = getOfflineDir(currentUid)
            val f = File(userOfflineDir, "$videoId.m4a")
            if (f.exists()) f.delete()

            val updatedList = _downloadedSongs.value.filterNot { it.videoId == videoId }
            _downloadedSongs.value = updatedList
            _downloadedSongIds.update { it - videoId }
            saveOfflineMetadata(updatedList, currentUid)

            if (currentUid.isNotBlank()) {
                val userPlaylists = playlistPreferences.getUserPlaylists(currentUid).first()
                val offlinePlaylist = userPlaylists.firstOrNull { it.title.equals("Lagu Offline", ignoreCase = true) }
                if (offlinePlaylist != null) {
                    playlistPreferences.removeSongFromPlaylist(offlinePlaylist.id, videoId, currentUid)
                }
            }

            Log.i(tag, "Deleted offline song: $videoId for user $currentUid")
            return@withContext true
        } catch (e: Exception) {
            Log.e(tag, "Failed to delete offline song $videoId", e)
            return@withContext false
        }
    }
}
