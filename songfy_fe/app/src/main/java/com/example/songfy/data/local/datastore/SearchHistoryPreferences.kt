package com.lagu.songfy.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.auth.FirebaseAuth
import com.lagu.songfy.data.remote.dto.SongDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.searchHistoryDataStore: DataStore<Preferences> by preferencesDataStore(name = "search_history_prefs")

@Singleton
class SearchHistoryPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firebaseAuth: FirebaseAuth
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val legacyKeyRecentSearches = stringPreferencesKey("recent_searches_list")

    private fun keyRecentSearches(uid: String): Preferences.Key<String> {
        val safeUid = uid.ifBlank { firebaseAuth.currentUser?.uid ?: "default" }
        return stringPreferencesKey("${safeUid}_recent_searches_list")
    }

    fun getRecentSearches(uid: String = ""): Flow<List<SongDto>> = context.searchHistoryDataStore.data.map { prefs ->
        val safeUid = uid.ifBlank { firebaseAuth.currentUser?.uid ?: "default" }
        val userKey = keyRecentSearches(safeUid)
        val raw = prefs[userKey] ?: prefs[legacyKeyRecentSearches]
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

    val recentSearches: Flow<List<SongDto>>
        get() = getRecentSearches()

    suspend fun addSearch(song: SongDto, uid: String = "") {
        val safeUid = uid.ifBlank { firebaseAuth.currentUser?.uid ?: "default" }
        val userKey = keyRecentSearches(safeUid)
        context.searchHistoryDataStore.edit { prefs ->
            val currentRaw = prefs[userKey] ?: prefs[legacyKeyRecentSearches]
            val currentList = if (!currentRaw.isNullOrBlank()) {
                try {
                    json.decodeFromString<List<SongDto>>(currentRaw)
                } catch (_: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }

            // Remove existing duplicate and place at the very top of history
            val filtered = currentList.filter { it.videoId != song.videoId }
            val updated = listOf(song) + filtered
            prefs[userKey] = json.encodeToString(updated.take(30))
            if (prefs.contains(legacyKeyRecentSearches)) {
                prefs.remove(legacyKeyRecentSearches)
            }
        }
    }

    suspend fun removeSearch(videoId: String, uid: String = "") {
        val safeUid = uid.ifBlank { firebaseAuth.currentUser?.uid ?: "default" }
        val userKey = keyRecentSearches(safeUid)
        context.searchHistoryDataStore.edit { prefs ->
            val currentRaw = prefs[userKey] ?: prefs[legacyKeyRecentSearches]
            if (!currentRaw.isNullOrBlank()) {
                val currentList = try {
                    json.decodeFromString<List<SongDto>>(currentRaw)
                } catch (_: Exception) {
                    emptyList()
                }
                val updated = currentList.filter { it.videoId != videoId }
                prefs[userKey] = json.encodeToString(updated)
            }
        }
    }

    suspend fun clearSearches(uid: String = "") {
        val safeUid = uid.ifBlank { firebaseAuth.currentUser?.uid ?: "default" }
        val userKey = keyRecentSearches(safeUid)
        context.searchHistoryDataStore.edit { prefs ->
            prefs.remove(userKey)
            if (prefs.contains(legacyKeyRecentSearches)) {
                prefs.remove(legacyKeyRecentSearches)
            }
        }
    }
}
