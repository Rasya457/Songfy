package com.lagu.songfy.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.lyricsDataStore: DataStore<Preferences> by preferencesDataStore(name = "lyrics_settings_prefs")

@Singleton
class LyricsPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firebaseAuth: FirebaseAuth
) {
    companion object {
        private val KEY_TEXT_SIZE_SP = floatPreferencesKey("lyrics_text_size_sp")
        private val KEY_BLUR_INACTIVE = booleanPreferencesKey("lyrics_blur_inactive")
        private val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("lyrics_keep_screen_on")
        private val KEY_LAST_LYRICS_MODE = booleanPreferencesKey("lyrics_last_mode_active")
        private val LEGACY_KEY_IS_TRANSLATION_ENABLED = booleanPreferencesKey("lyrics_translation_enabled")
    }

    private fun getSafeUid(uid: String = ""): String {
        return uid.ifBlank { firebaseAuth.currentUser?.uid ?: "default" }
    }

    private fun keyTranslation(uid: String) = booleanPreferencesKey("${getSafeUid(uid)}_lyrics_translation_enabled")
    private fun keyLyricOffset(videoId: String, uid: String) = longPreferencesKey("${getSafeUid(uid)}_lyric_offset_$videoId")
    private fun legacyKeyLyricOffset(videoId: String) = longPreferencesKey("lyric_offset_$videoId")

    fun isTranslationEnabled(uid: String = ""): Flow<Boolean> = context.lyricsDataStore.data.map { prefs ->
        val userKey = keyTranslation(uid)
        prefs[userKey] ?: prefs[LEGACY_KEY_IS_TRANSLATION_ENABLED] ?: false
    }

    val isTranslationEnabled: Flow<Boolean>
        get() = isTranslationEnabled("")

    val textSizeSp: Flow<Float> = context.lyricsDataStore.data.map { prefs ->
        prefs[KEY_TEXT_SIZE_SP] ?: 28f // default Medium 28sp
    }

    val blurInactive: Flow<Boolean> = context.lyricsDataStore.data.map { prefs ->
        prefs[KEY_BLUR_INACTIVE] ?: true
    }

    val keepScreenOn: Flow<Boolean> = context.lyricsDataStore.data.map { prefs ->
        prefs[KEY_KEEP_SCREEN_ON] ?: true
    }

    val lastLyricsMode: Flow<Boolean> = context.lyricsDataStore.data.map { prefs ->
        prefs[KEY_LAST_LYRICS_MODE] ?: false
    }

    suspend fun setTranslationEnabled(enabled: Boolean, uid: String = "") {
        val userKey = keyTranslation(uid)
        context.lyricsDataStore.edit { prefs ->
            prefs[userKey] = enabled
        }
    }

    suspend fun setTextSizeSp(sizeSp: Float) {
        context.lyricsDataStore.edit { it[KEY_TEXT_SIZE_SP] = sizeSp }
    }

    suspend fun setBlurInactive(enabled: Boolean) {
        context.lyricsDataStore.edit { it[KEY_BLUR_INACTIVE] = enabled }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        context.lyricsDataStore.edit { it[KEY_KEEP_SCREEN_ON] = enabled }
    }

    suspend fun setLastLyricsMode(active: Boolean) {
        context.lyricsDataStore.edit { it[KEY_LAST_LYRICS_MODE] = active }
    }

    suspend fun getLyricOffsetMs(videoId: String, uid: String = ""): Long {
        if (videoId.isBlank()) return 0L
        return try {
            val userKey = keyLyricOffset(videoId, uid)
            val legacyKey = legacyKeyLyricOffset(videoId)
            context.lyricsDataStore.data.map { prefs ->
                prefs[userKey] ?: prefs[legacyKey] ?: 0L
            }.first()
        } catch (_: Exception) {
            0L
        }
    }

    suspend fun setLyricOffsetMs(videoId: String, offsetMs: Long, uid: String = "") {
        if (videoId.isBlank()) return
        try {
            val userKey = keyLyricOffset(videoId, uid)
            val legacyKey = legacyKeyLyricOffset(videoId)
            context.lyricsDataStore.edit { prefs ->
                if (offsetMs == 0L) {
                    prefs.remove(userKey)
                    prefs.remove(legacyKey)
                } else {
                    prefs[userKey] = offsetMs
                }
            }
        } catch (_: Exception) {}
    }
}
