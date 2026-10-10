package com.lagu.songfy.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lagu.songfy.data.remote.dto.SongDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class UserSavedArtist(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val imageUrl: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)

@Serializable
data class UserPlaylistDto(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val description: String = "Playlist dibuat oleh kamu",
    val artworkUrl: String? = null,
    val songs: List<SongDto> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

private val Context.playlistDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_playlist_prefs")

@Singleton
class PlaylistPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val legacyPlaylistsKey = stringPreferencesKey("_user_playlists_list")
    private val legacyArtistsKey = stringPreferencesKey("_saved_artists_list")

    private fun resolveUid(uid: String): String {
        return uid.ifBlank {
            try {
                com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "default"
            } catch (_: Exception) {
                "default"
            }
        }
    }

    private fun keyArtists(uid: String) = stringPreferencesKey("${resolveUid(uid)}_saved_artists_list")
    private fun keyPlaylists(uid: String) = stringPreferencesKey("${resolveUid(uid)}_user_playlists_list")

    // ────────────────────────────
    // ARTIST MANAGEMENT
    // ────────────────────────────
    fun getSavedArtists(uid: String): Flow<List<UserSavedArtist>> = context.playlistDataStore.data.map { prefs ->
        val raw = prefs[keyArtists(uid)] ?: prefs[legacyArtistsKey]
        if (!raw.isNullOrBlank()) {
            try {
                json.decodeFromString<List<UserSavedArtist>>(raw)
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    suspend fun saveArtists(artists: List<UserSavedArtist>, uid: String = "") {
        context.playlistDataStore.edit { prefs ->
            prefs[keyArtists(uid)] = json.encodeToString(artists)
        }
    }

    suspend fun addArtists(newArtists: List<UserSavedArtist>, uid: String = "") {
        context.playlistDataStore.edit { prefs ->
            val raw = prefs[keyArtists(uid)]
            val currentList = if (!raw.isNullOrBlank()) {
                try {
                    json.decodeFromString<List<UserSavedArtist>>(raw)
                } catch (_: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }

            // Hindari duplikat nama artis
            val existingNames = currentList.map { it.name.trim().lowercase() }.toSet()
            val filteredNew = newArtists.filter { !existingNames.contains(it.name.trim().lowercase()) }
            val updated = currentList + filteredNew
            prefs[keyArtists(uid)] = json.encodeToString(updated)
        }
    }

    suspend fun removeArtist(artistName: String, uid: String = "") {
        context.playlistDataStore.edit { prefs ->
            val raw = prefs[keyArtists(uid)]
            if (!raw.isNullOrBlank()) {
                try {
                    val currentList = json.decodeFromString<List<UserSavedArtist>>(raw)
                    val updated = currentList.filterNot { it.name.equals(artistName, ignoreCase = true) }
                    prefs[keyArtists(uid)] = json.encodeToString(updated)
                } catch (_: Exception) {}
            }
        }
    }

    // ────────────────────────────
    // PLAYLIST MANAGEMENT
    // ────────────────────────────
    fun getUserPlaylists(uid: String): Flow<List<UserPlaylistDto>> = context.playlistDataStore.data.map { prefs ->
        val raw = prefs[keyPlaylists(uid)] ?: prefs[legacyPlaylistsKey]
        if (!raw.isNullOrBlank()) {
            try {
                json.decodeFromString<List<UserPlaylistDto>>(raw)
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    suspend fun createPlaylist(
        title: String,
        description: String = "Playlist dibuat oleh kamu",
        artworkUrl: String? = null,
        uid: String = ""
    ): UserPlaylistDto {
        val newPl = UserPlaylistDto(
            title = title.trim().ifBlank { "Playlist Baru" },
            description = description,
            artworkUrl = artworkUrl
        )
        context.playlistDataStore.edit { prefs ->
            val raw = prefs[keyPlaylists(uid)]
            val current = if (!raw.isNullOrBlank()) {
                try { json.decodeFromString<List<UserPlaylistDto>>(raw) } catch (_: Exception) { emptyList() }
            } else emptyList()
            prefs[keyPlaylists(uid)] = json.encodeToString(current + newPl)
        }
        return newPl
    }

    suspend fun addSongToPlaylist(playlistId: String, song: SongDto, uid: String = ""): Boolean {
        var added = false
        context.playlistDataStore.edit { prefs ->
            val raw = prefs[keyPlaylists(uid)]
            val current = if (!raw.isNullOrBlank()) {
                try { json.decodeFromString<List<UserPlaylistDto>>(raw) } catch (_: Exception) { emptyList() }
            } else emptyList()

            val updated = current.map { pl ->
                if (pl.id == playlistId) {
                    val alreadyIn = pl.songs.any { it.videoId == song.videoId }
                    if (!alreadyIn) {
                        added = true
                        val newArt = pl.artworkUrl ?: song.thumbnailUrl
                        pl.copy(songs = pl.songs + song, artworkUrl = newArt)
                    } else pl
                } else pl
            }
            prefs[keyPlaylists(uid)] = json.encodeToString(updated)
        }
        return added
    }

    suspend fun removeSongFromPlaylist(playlistId: String, videoId: String, uid: String = ""): Boolean {
        var removed = false
        context.playlistDataStore.edit { prefs ->
            val raw = prefs[keyPlaylists(uid)]
            val current = if (!raw.isNullOrBlank()) {
                try { json.decodeFromString<List<UserPlaylistDto>>(raw) } catch (_: Exception) { emptyList() }
            } else emptyList()

            val updated = current.map { pl ->
                if (pl.id == playlistId) {
                    val hadIt = pl.songs.any { it.videoId == videoId }
                    if (hadIt) {
                        removed = true
                        val newSongs = pl.songs.filterNot { it.videoId == videoId }
                        val newArt = newSongs.firstOrNull()?.thumbnailUrl
                        pl.copy(songs = newSongs, artworkUrl = newArt)
                    } else pl
                } else pl
            }
            prefs[keyPlaylists(uid)] = json.encodeToString(updated)
        }
        return removed
    }

    suspend fun syncPlaylistSongs(playlistTitle: String, description: String, songs: List<SongDto>, uid: String = "") {
        context.playlistDataStore.edit { prefs ->
            val raw = prefs[keyPlaylists(uid)]
            val current = if (!raw.isNullOrBlank()) {
                try { json.decodeFromString<List<UserPlaylistDto>>(raw) } catch (_: Exception) { emptyList() }
            } else emptyList()

            var found = false
            val updated = current.map { pl ->
                if (pl.title.equals(playlistTitle, ignoreCase = true)) {
                    found = true
                    val newArt = songs.firstOrNull()?.thumbnailUrl ?: pl.artworkUrl
                    pl.copy(songs = songs, artworkUrl = newArt)
                } else pl
            }

            val finalList = if (!found) {
                val newPl = UserPlaylistDto(
                    title = playlistTitle,
                    description = description,
                    artworkUrl = songs.firstOrNull()?.thumbnailUrl,
                    songs = songs
                )
                updated + newPl
            } else {
                updated
            }
            prefs[keyPlaylists(uid)] = json.encodeToString(finalList)
        }
    }

    suspend fun updatePlaylistInfo(
        playlistId: String,
        newTitle: String,
        newDescription: String,
        newArtworkUrl: String? = null,
        uid: String = ""
    ) {
        context.playlistDataStore.edit { prefs ->
            val raw = prefs[keyPlaylists(uid)]
            val current = if (!raw.isNullOrBlank()) {
                try { json.decodeFromString<List<UserPlaylistDto>>(raw) } catch (_: Exception) { emptyList() }
            } else emptyList()

            val updated = current.map { pl ->
                if (pl.id == playlistId) {
                    pl.copy(
                        title = newTitle.trim().ifBlank { pl.title },
                        description = newDescription.trim(),
                        artworkUrl = newArtworkUrl ?: pl.artworkUrl
                    )
                } else pl
            }
            prefs[keyPlaylists(uid)] = json.encodeToString(updated)
        }
    }

    suspend fun deletePlaylist(playlistId: String, uid: String = ""): Boolean {
        var deleted = false
        context.playlistDataStore.edit { prefs ->
            val raw = prefs[keyPlaylists(uid)]
            val current = if (!raw.isNullOrBlank()) {
                try { json.decodeFromString<List<UserPlaylistDto>>(raw) } catch (_: Exception) { emptyList() }
            } else emptyList()

            val updated = current.filterNot { pl ->
                if (pl.id == playlistId) {
                    deleted = true
                    true
                } else false
            }
            prefs[keyPlaylists(uid)] = json.encodeToString(updated)
        }
        return deleted
    }

    suspend fun duplicatePlaylist(playlistId: String, uid: String = ""): UserPlaylistDto? {
        var duplicate: UserPlaylistDto? = null
        context.playlistDataStore.edit { prefs ->
            val raw = prefs[keyPlaylists(uid)]
            val current = if (!raw.isNullOrBlank()) {
                try { json.decodeFromString<List<UserPlaylistDto>>(raw) } catch (_: Exception) { emptyList() }
            } else emptyList()

            val target = current.firstOrNull { it.id == playlistId }
            if (target != null) {
                duplicate = UserPlaylistDto(
                    title = "${target.title} (Salinan)",
                    description = target.description,
                    artworkUrl = target.artworkUrl,
                    songs = target.songs
                )
                prefs[keyPlaylists(uid)] = json.encodeToString(current + duplicate!!)
            }
        }
        return duplicate
    }
}

