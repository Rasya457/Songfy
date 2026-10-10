package com.lagu.songfy.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lagu.songfy.data.remote.dto.ArtistShortDto
import com.lagu.songfy.data.remote.dto.SongDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "last_played_song_prefs")

@Singleton
class LastPlayedSongPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    companion object {
        // Prefix keys with uid so different users never share last-played data
        private fun keyVideoId(uid: String) = stringPreferencesKey("${uid}_last_video_id")
        private fun keyTitle(uid: String) = stringPreferencesKey("${uid}_last_title")
        private fun keyArtistName(uid: String) = stringPreferencesKey("${uid}_last_artist_name")
        private fun keyArtistId(uid: String) = stringPreferencesKey("${uid}_last_artist_id")
        private fun keyAlbum(uid: String) = stringPreferencesKey("${uid}_last_album")
        private fun keyDuration(uid: String) = stringPreferencesKey("${uid}_last_duration")
        private fun keyThumbnailUrl(uid: String) = stringPreferencesKey("${uid}_last_thumbnail_url")
        private fun keyPositionMs(uid: String) = longPreferencesKey("${uid}_last_position_ms")
        private fun keyQueue(uid: String) = stringPreferencesKey("${uid}_last_queue_list")
    }

    /**
     * Read last played song for a specific user (by uid).
     * Returns null if no song saved for that user.
     */
    fun lastPlayedSongForUser(uid: String): Flow<SongDto?> = context.dataStore.data.map { preferences ->
        val videoId = preferences[keyVideoId(uid)]
        val title = preferences[keyTitle(uid)]
        if (videoId != null && title != null) {
            val artistName = preferences[keyArtistName(uid)] ?: "Unknown Artist"
            val artistId = preferences[keyArtistId(uid)]
            val album = preferences[keyAlbum(uid)]
            val duration = preferences[keyDuration(uid)]
            val thumbnailUrl = preferences[keyThumbnailUrl(uid)]
            SongDto(
                videoId = videoId,
                title = title,
                artists = listOf(ArtistShortDto(id = artistId, name = artistName)),
                album = album,
                duration = duration,
                thumbnailUrl = thumbnailUrl
            )
        } else {
            null
        }
    }

    /**
     * Read last played position in milliseconds for a specific user (by uid).
     */
    fun lastPlayedPositionForUser(uid: String): Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[keyPositionMs(uid)] ?: 0L
    }

    /**
     * Read last played queue for a specific user (by uid).
     */
    fun lastPlayedQueueForUser(uid: String): Flow<List<SongDto>> = context.dataStore.data.map { preferences ->
        val raw = preferences[keyQueue(uid)]
        if (!raw.isNullOrBlank()) {
            try {
                json.decodeFromString<List<SongDto>>(raw)
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    /**
     * Kept for backward compatibility - reads from the anonymous/empty-uid slot.
     * New code should use [lastPlayedSongForUser].
     */
    val lastPlayedSong: Flow<SongDto?> = lastPlayedSongForUser("")

    suspend fun saveLastPlayedSong(song: SongDto, uid: String = "", positionMs: Long = 0L) {
        context.dataStore.edit { preferences ->
            preferences[keyVideoId(uid)] = song.videoId
            preferences[keyTitle(uid)] = song.title
            val artistName = song.artists.joinToString(", ") { it.name }.ifBlank { "Unknown Artist" }
            preferences[keyArtistName(uid)] = artistName
            song.artists.firstOrNull()?.id?.let { preferences[keyArtistId(uid)] = it }
            song.album?.let { preferences[keyAlbum(uid)] = it }
            song.duration?.let { preferences[keyDuration(uid)] = it }
            song.thumbnailUrl?.let { preferences[keyThumbnailUrl(uid)] = it }
            if (positionMs > 0L) {
                preferences[keyPositionMs(uid)] = positionMs
            }
        }
    }

    suspend fun saveLastPlayedPosition(positionMs: Long, uid: String = "") {
        context.dataStore.edit { preferences ->
            preferences[keyPositionMs(uid)] = positionMs.coerceAtLeast(0L)
        }
    }

    suspend fun saveLastPlayedQueue(queue: List<SongDto>, uid: String = "") {
        if (uid.isBlank() || queue.isEmpty()) return
        context.dataStore.edit { preferences ->
            try {
                preferences[keyQueue(uid)] = json.encodeToString(queue.take(50))
            } catch (_: Exception) {}
        }
    }

    /**
     * Clear last played song data for a specific user on logout.
     */
    suspend fun clearForUser(uid: String) {
        context.dataStore.edit { preferences ->
            preferences.remove(keyVideoId(uid))
            preferences.remove(keyTitle(uid))
            preferences.remove(keyArtistName(uid))
            preferences.remove(keyArtistId(uid))
            preferences.remove(keyAlbum(uid))
            preferences.remove(keyDuration(uid))
            preferences.remove(keyThumbnailUrl(uid))
            preferences.remove(keyPositionMs(uid))
            preferences.remove(keyQueue(uid))
        }
    }
}
