package com.lagu.songfy.data.repository

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.lagu.songfy.data.remote.dto.ArtistShortDto
import com.lagu.songfy.data.remote.dto.SongDto
import com.lagu.songfy.util.SecurePrefsFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

data class SongHistoryItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val song: SongDto,
    val playedAtTimestamp: Long = System.currentTimeMillis(),
    val progressMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val relativeTimeText: String = "Baru saja",
    val isCompleted: Boolean = false
)

@Serializable
private data class HistoryItemSnapshot(
    val videoId: String,
    val title: String,
    val artistName: String,
    val artistId: String? = null,
    val album: String? = null,
    val duration: String? = null,
    val thumbnailUrl: String? = null,
    val playedAtTimestamp: Long = System.currentTimeMillis(),
    val progressMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val isCompleted: Boolean = false
)

private val historyJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

private const val MAX_HISTORY_SIZE = 50

@Singleton
class SongHistoryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firebaseAuth: FirebaseAuth
) {
    private val _history = MutableStateFlow<List<SongHistoryItem>>(emptyList())
    val history: StateFlow<List<SongHistoryItem>> = _history.asStateFlow()

    private val _totalListeningTimeSeconds = MutableStateFlow(0L)
    val totalListeningTimeSeconds: StateFlow<Long> = _totalListeningTimeSeconds.asStateFlow()

    private var currentUserId: String? = null

    init {
        val initialUid = firebaseAuth.currentUser?.uid
        if (initialUid != null) {
            onUserChanged(initialUid)
        }
        firebaseAuth.addAuthStateListener { auth ->
            onUserChanged(auth.currentUser?.uid)
        }
    }

    // Encrypted storage — riwayat lagu dienkripsi agar tidak bisa dibaca
    // oleh app lain atau tool forensik di device yang di-root.
    private fun getPrefs(uid: String) =
        SecurePrefsFactory.get(context, "songfy_history_${uid}")

    private fun saveToPrefs(uid: String, items: List<SongHistoryItem>, totalSecs: Long) {
        val snapshots = items.take(MAX_HISTORY_SIZE).map { item ->
            HistoryItemSnapshot(
                videoId = item.song.videoId,
                title = item.song.title,
                artistName = item.song.artists.joinToString(", ") { it.name },
                artistId = item.song.artists.firstOrNull()?.id,
                album = item.song.album,
                duration = item.song.duration,
                thumbnailUrl = item.song.thumbnailUrl,
                playedAtTimestamp = item.playedAtTimestamp,
                progressMs = item.progressMs,
                totalDurationMs = item.totalDurationMs,
                isCompleted = item.isCompleted
            )
        }
        val jsonString = try { historyJson.encodeToString(snapshots) } catch (_: Exception) { "[]" }
        getPrefs(uid).edit()
            .putString("history_json", jsonString)
            .putLong("total_listening_secs", totalSecs)
            .apply()
    }

    private fun loadFromPrefs(uid: String): Pair<List<SongHistoryItem>, Long> {
        val prefs = getPrefs(uid)
        val jsonString = prefs.getString("history_json", null) ?: return Pair(emptyList(), 0L)
        val totalSecs = prefs.getLong("total_listening_secs", 0L)
        val snapshots = try {
            historyJson.decodeFromString<List<HistoryItemSnapshot>>(jsonString)
        } catch (_: Exception) { return Pair(emptyList(), 0L) }
        val items = snapshots.map { snap ->
            SongHistoryItem(
                song = SongDto(
                    videoId = snap.videoId,
                    title = snap.title,
                    artists = listOf(ArtistShortDto(id = snap.artistId, name = snap.artistName)),
                    album = snap.album,
                    duration = snap.duration,
                    thumbnailUrl = snap.thumbnailUrl
                ),
                playedAtTimestamp = snap.playedAtTimestamp,
                progressMs = snap.progressMs,
                totalDurationMs = snap.totalDurationMs,
                isCompleted = snap.isCompleted,
                relativeTimeText = formatRelativeTime(snap.playedAtTimestamp)
            )
        }
        return Pair(items, totalSecs)
    }

    fun formatRelativeTime(timestamp: Long): String {
        val diffMs = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
        val diffMins = diffMs / 60_000
        val diffHours = diffMs / 3_600_000
        val diffDays = diffMs / 86_400_000
        return when {
            diffMins < 1 -> "Baru saja"
            diffMins < 60 -> "${diffMins} menit lalu"
            diffHours < 24 -> "${diffHours} jam lalu"
            diffDays == 1L -> "Kemarin"
            diffDays < 7 -> "${diffDays} hari lalu"
            else -> "${diffDays / 7} minggu lalu"
        }
    }

    fun onUserChanged(newUid: String?) {
        if (newUid == currentUserId && currentUserId != null) return
        currentUserId = newUid
        if (newUid == null) {
            _history.value = emptyList()
            _totalListeningTimeSeconds.value = 0L
            return
        }
        val (items, totalSecs) = loadFromPrefs(newUid)
        _history.value = items
        _totalListeningTimeSeconds.value = totalSecs
    }

    private fun resolveActiveUid(): String {
        return currentUserId ?: firebaseAuth.currentUser?.uid ?: "default_user"
    }

    private fun recalculateListeningTime(items: List<SongHistoryItem>, uid: String) {
        val totalSecs = items.sumOf { item ->
            if (item.progressMs > 0L) item.progressMs / 1000
            else if (item.totalDurationMs > 0L) item.totalDurationMs / 1000
            else 180L
        }
        _totalListeningTimeSeconds.value = totalSecs
        saveToPrefs(uid, items, totalSecs)
    }

    fun addSongToHistory(song: SongDto) {
        val uid = resolveActiveUid()
        currentUserId = uid

        val existingList = _history.value.toMutableList()
        val oldIndex = existingList.indexOfFirst { it.song.videoId == song.videoId }
        val oldItem = if (oldIndex >= 0) existingList.removeAt(oldIndex) else null

        val newItem = SongHistoryItem(
            song = song,
            playedAtTimestamp = System.currentTimeMillis(),
            progressMs = oldItem?.progressMs ?: 0L,
            totalDurationMs = oldItem?.totalDurationMs ?: 0L,
            relativeTimeText = "Baru saja",
            isCompleted = false
        )
        existingList.add(0, newItem)
        val capped = existingList.take(MAX_HISTORY_SIZE)
        _history.value = capped
        recalculateListeningTime(capped, uid)
    }

    fun updateSongProgress(song: SongDto, progressMs: Long, totalDurationMs: Long) {
        val uid = resolveActiveUid()
        currentUserId = uid

        val existingList = _history.value.toMutableList()
        val index = existingList.indexOfFirst { it.song.videoId == song.videoId }
        if (index >= 0) {
            val current = existingList[index]
            val dur = if (totalDurationMs > 0L) totalDurationMs else current.totalDurationMs
            val clampedProg = progressMs.coerceIn(0L, dur.coerceAtLeast(1L))

            // Only emit to StateFlow (and trigger UI recomposition) if progress changed
            // by at least 5 seconds. This prevents the player's per-second tick from
            // causing continuous Profile screen recompositions (the "glitch").
            val progressDeltaMs = Math.abs(clampedProg - current.progressMs)
            val updated = current.copy(
                progressMs = clampedProg,
                totalDurationMs = dur,
                playedAtTimestamp = System.currentTimeMillis(),
                relativeTimeText = "Baru saja"
            )
            existingList[index] = updated

            if (progressDeltaMs >= 5_000L) {
                // Significant progress change — update UI and persist
                _history.value = existingList
                recalculateListeningTime(existingList, uid)
            } else {
                // Minor tick — persist to prefs silently without emitting to StateFlow
                saveToPrefs(uid, existingList, _totalListeningTimeSeconds.value)
            }
        }
    }

    fun markSongCompleted(song: SongDto, durationMs: Long) {
        val uid = resolveActiveUid()
        currentUserId = uid

        val existingList = _history.value.toMutableList()
        val index = existingList.indexOfFirst { it.song.videoId == song.videoId }
        val current = if (index >= 0) existingList.removeAt(index) else null
        val dur = if (durationMs > 0L) durationMs else (current?.totalDurationMs ?: 0L)
        val updated = (current ?: SongHistoryItem(song = song)).copy(
            song = song,
            totalDurationMs = dur,
            progressMs = dur,
            isCompleted = true,
            playedAtTimestamp = System.currentTimeMillis(),
            relativeTimeText = "Baru saja"
        )
        existingList.add(0, updated)
        val capped = existingList.take(MAX_HISTORY_SIZE)
        _history.value = capped
        recalculateListeningTime(capped, uid)
    }

    fun clearHistory() {
        val uid = resolveActiveUid()
        _history.value = emptyList()
        _totalListeningTimeSeconds.value = 0L
        getPrefs(uid).edit().clear().apply()
    }
}
