package com.lagu.songfy.presentation.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.lagu.songfy.data.local.datastore.LastPlayedSongPreferences
import com.lagu.songfy.data.local.datastore.PlaylistPreferences
import com.lagu.songfy.data.remote.api.SongfyApiService
import com.lagu.songfy.data.remote.dto.SongDto
import com.lagu.songfy.data.repository.OfflineSongRepository
import com.lagu.songfy.data.repository.SongHistoryRepository
import com.lagu.songfy.service.MusicPlaybackService
import com.lagu.songfy.service.PlaybackEvent
import com.lagu.songfy.service.PlaybackEventBus
import com.lagu.songfy.data.local.datastore.LyricsPreferences
import com.lagu.songfy.service.PlaybackStateManager
import com.lagu.songfy.util.LyricsTranslationManager
import com.lagu.songfy.util.SecurePrefsFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exoPlayer: ExoPlayer,
    private val apiService: SongfyApiService,
    private val historyRepository: SongHistoryRepository,
    private val lastPlayedSongPreferences: LastPlayedSongPreferences,
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val playbackEventBus: PlaybackEventBus,
    private val playbackStateManager: PlaybackStateManager,
    private val offlineSongRepository: OfflineSongRepository,
    private val playlistPreferences: PlaylistPreferences,
    private val lyricsPreferences: LyricsPreferences,
    private val lyricsTranslationManager: LyricsTranslationManager
) : ViewModel() {

    val downloadedSongIds: StateFlow<Set<String>> = offlineSongRepository.downloadedSongIds
    val downloadingSongIds: StateFlow<Set<String>> = offlineSongRepository.downloadingSongIds

    fun downloadSong(song: SongDto) {
        viewModelScope.launch {
            offlineSongRepository.downloadSong(song)
        }
    }

    fun deleteOfflineSong(videoId: String) {
        viewModelScope.launch {
            offlineSongRepository.deleteOfflineSong(videoId)
        }
    }

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var progressTrackerJob: Job? = null
    private var autoQueueJob: Job? = null
    private var prefetchJob: Job? = null

    private var audioJob: Job? = null
    private var lyricsJob: Job? = null
    private var sleepTimerJob: Job? = null

    // Consecutive stream-error recovery attempts for the current song (reset on setSong).
    private var errorRetryCount = 0
    private companion object {
        const val MAX_ERROR_RETRIES = 3
        // Cooldown antar skip (next/prev) untuk mencegah spam yang bisa crash/error.
        // 400ms cepat & responsif saat ditekan, namun tetap aman mencegah spam.
        const val SKIP_COOLDOWN_MS = 400L
    }

    // Timestamp terakhir kali skip dilakukan (next atau previous)
    private var lastSkipTimeMs = 0L

    // In-memory stream URL cache for instant song switching (cap 50) — ConcurrentHashMap
    // prevents ConcurrentModificationException when accessed from both Dispatchers.IO and Main.
    private val streamUrlCache = ConcurrentHashMap<String, String>()
    // In-memory lyrics cache: videoId -> Pair(lyrics, isSynced) (cap 50) — thread-safe
    private val lyricsCache = ConcurrentHashMap<String, Pair<List<LyricLine>, Boolean>>()

    // Track current user so we can detect account switches
    private var currentUserId: String? = null

    // Encrypted storage — daftar lagu yang di-like adalah data sensitif pengguna.
    private fun getLikedPrefs(uid: String) =
        SecurePrefsFactory.get(context, "songfy_liked_songs_$uid")

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    val duration = exoPlayer.duration
                    if (duration > 0) {
                        _uiState.update { it.copy(durationMs = duration) }
                    }
                }
                Player.STATE_ENDED -> {
                    // Handled centrally by singleton PlaybackStateManager
                }
                else -> {}
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
            val (lastPos, lastDur) = _playbackTick.value
            _playbackTick.value = Triple(
                if (isPlaying) exoPlayer.currentPosition.coerceAtLeast(0L) else lastPos,
                if (exoPlayer.duration > 0) exoPlayer.duration else lastDur,
                isPlaying
            )
            if (isPlaying) {
                startProgressTracker()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e("PlayerViewModel", "ExoPlayer playback error: ${error.message}", error)
            _uiState.update { it.copy(isPlaying = false) }
        }
    }

    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        val newUid = auth.currentUser?.uid
        if (newUid != currentUserId) {
            onUserChanged(newUid)
        }
    }

    init {
        exoPlayer.addListener(playerListener)
        firebaseAuth.addAuthStateListener(authStateListener)

        currentUserId = firebaseAuth.currentUser?.uid
        historyRepository.onUserChanged(currentUserId)

        // Synchronize with background PlaybackStateManager (e.g. track switches from Notification / Lockscreen)
        viewModelScope.launch {
            playbackStateManager.currentSong.collect { song ->
                if (song != null && song.videoId != _uiState.value.currentSong?.videoId) {
                    val uid = currentUserId ?: ""
                    val isLiked = getLikedPrefs(uid).getBoolean(song.videoId, false)
                    val cachedLyrics = lyricsCache[song.videoId]
                    val cachedLang = lyricsTranslationManager.getCachedLanguage(song.videoId)
                    val cachedTrans = lyricsTranslationManager.getCachedTranslation(song.videoId) ?: emptyMap()
                    val dur = parseDurationStringToMs(song.duration) ?: 262000L
                    _uiState.update {
                        it.copy(
                            currentSong = song,
                            queue = playbackStateManager.queue.value,
                            currentIndex = playbackStateManager.currentIndex.value,
                            durationMs = dur,
                            isLiked = isLiked,
                            lyrics = cachedLyrics?.first ?: emptyList(),
                            isSyncedLyrics = cachedLyrics?.second ?: false,
                            isLoadingLyrics = cachedLyrics == null,
                            detectedLanguage = cachedLang,
                            translatedLyrics = cachedTrans,
                            isTranslating = false,
                            translationError = null
                        )
                    }
                    if (cachedLyrics == null) {
                        lyricsJob?.cancel()
                        lyricsJob = viewModelScope.launch {
                            fetchLyrics(song)
                        }
                    } else if (cachedLyrics.first.isNotEmpty()) {
                        detectAndPrepareTranslation(song, cachedLyrics.first)
                    }
                    startProgressTracker()

                    // Prefetch lyrics for next song in queue for instant lyric display
                    val q = playbackStateManager.queue.value
                    val curIdx = playbackStateManager.currentIndex.value
                    val nextIdx = (curIdx + 1).takeIf { it < q.size }
                    if (nextIdx != null) {
                        val upcoming = q[nextIdx]
                        if (!lyricsCache.containsKey(upcoming.videoId)) {
                            viewModelScope.launch {
                                try {
                                    val aName = upcoming.artists.joinToString(", ") { it.name }.ifBlank { "Unknown Artist" }
                                    val lyricsRes = apiService.getLyrics(
                                        videoId = upcoming.videoId,
                                        trackName = cleanTrackTitle(upcoming.title),
                                        artistName = aName
                                    )
                                    val parsed = extractLyrics(lyricsRes.syncedLyrics, lyricsRes.plainLyrics)
                                    if (lyricsCache.size > 50) lyricsCache.clear()
                                    lyricsCache[upcoming.videoId] = parsed
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
            }
        }

        // Continuously synchronize dynamic queue populated by PlaybackStateManager
        viewModelScope.launch {
            playbackStateManager.queue.collect { newQueue ->
                if (newQueue.isNotEmpty()) {
                    _uiState.update { it.copy(queue = newQueue) }
                }
            }
        }
        viewModelScope.launch {
            playbackStateManager.currentIndex.collect { newIdx ->
                _uiState.update { it.copy(currentIndex = newIdx) }
            }
        }

        // Listen for stop event from MediaNotification / Lockscreen
        viewModelScope.launch {
            playbackEventBus.events.collect { event ->
                if (event == PlaybackEvent.STOP) {
                    _playbackTick.value = Triple(0L, _uiState.value.durationMs, false)
                    _uiState.update { it.copy(isPlaying = false) }
                }
            }
        }

        // Sync translation enabled preference from DataStore
        viewModelScope.launch {
            lyricsPreferences.isTranslationEnabled.collect { enabled ->
                _uiState.update { it.copy(isTranslationActive = enabled) }
                val st = _uiState.value
                val song = st.currentSong
                if (enabled && st.canTranslate && song != null && st.lyrics.isNotEmpty() && st.translatedLyrics.isEmpty()) {
                    fetchTranslation(song.videoId, st.detectedLanguage ?: "ja", st.lyrics)
                }
            }
        }

        // Restore active or last played song for the current user from DataStore
        viewModelScope.launch {
            val uid = currentUserId ?: ""
            val savedSong = lastPlayedSongPreferences.lastPlayedSongForUser(uid).firstOrNull()
            val savedPos = lastPlayedSongPreferences.lastPlayedPositionForUser(uid).firstOrNull() ?: 0L
            if (exoPlayer.mediaItemCount > 0) {
                val currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
                val currentDur = if (exoPlayer.duration > 0) exoPlayer.duration else (parseDurationStringToMs(savedSong?.duration) ?: 262000L)
                val isPlaying = exoPlayer.isPlaying
                _playbackTick.value = Triple(currentPos, currentDur, isPlaying)
                if (savedSong != null) {
                    val isLiked = getLikedPrefs(uid).getBoolean(savedSong.videoId, false)
                    val cachedLyrics = lyricsCache[savedSong.videoId]
                    val cachedLang = lyricsTranslationManager.getCachedLanguage(savedSong.videoId)
                    val cachedTrans = lyricsTranslationManager.getCachedTranslation(savedSong.videoId) ?: emptyMap()
                    _uiState.update {
                        it.copy(
                            currentSong = savedSong,
                            queue = if (it.queue.isNotEmpty()) it.queue else listOf(savedSong),
                            durationMs = currentDur,
                            isLiked = isLiked,
                            isPlaying = isPlaying,
                            lyrics = cachedLyrics?.first ?: emptyList(),
                            isSyncedLyrics = cachedLyrics?.second ?: false,
                            isLoadingLyrics = cachedLyrics == null,
                            detectedLanguage = cachedLang,
                            translatedLyrics = cachedTrans
                        )
                    }
                    if (cachedLyrics == null) {
                        lyricsJob?.cancel()
                        lyricsJob = viewModelScope.launch {
                            fetchLyrics(savedSong)
                        }
                    } else if (cachedLyrics.first.isNotEmpty()) {
                        detectAndPrepareTranslation(savedSong, cachedLyrics.first)
                    }
                }
                if (isPlaying) {
                    startProgressTracker()
                }
            } else if (savedSong != null) {
                val dur = parseDurationStringToMs(savedSong.duration) ?: 262000L
                val isLiked = getLikedPrefs(uid).getBoolean(savedSong.videoId, false)
                val clampedSavedPos = savedPos.coerceIn(0L, dur)
                _playbackTick.value = Triple(clampedSavedPos, dur, false)
                _uiState.update {
                    it.copy(
                        currentSong = savedSong,
                        queue = listOf(savedSong),
                        durationMs = dur,
                        isLiked = isLiked,
                        isPlaying = false,
                        isLoadingLyrics = true
                    )
                }
                lyricsJob?.cancel()
                lyricsJob = viewModelScope.launch {
                    fetchLyrics(savedSong)
                }
            } else {
                _uiState.update { it.copy(currentSong = null, queue = emptyList()) }
            }
            startProgressTracker()
        }
    }

    private fun onUserChanged(newUid: String?) {
        currentUserId = newUid
        historyRepository.onUserChanged(newUid)

        progressTrackerJob?.cancel()
        autoQueueJob?.cancel()
        prefetchJob?.cancel()
        audioJob?.cancel()
        lyricsJob?.cancel()
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        streamUrlCache.clear()
        lyricsCache.clear()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()

        _uiState.value = PlayerUiState()

        if (newUid == null) {
            return
        }

        viewModelScope.launch {
            val savedSong = lastPlayedSongPreferences.lastPlayedSongForUser(newUid).firstOrNull()
            val savedPos = lastPlayedSongPreferences.lastPlayedPositionForUser(newUid).firstOrNull() ?: 0L
            if (savedSong != null) {
                val dur = parseDurationStringToMs(savedSong.duration) ?: 262000L
                val isLiked = getLikedPrefs(newUid).getBoolean(savedSong.videoId, false)
                val clampedSavedPos = savedPos.coerceIn(0L, dur)
                _playbackTick.value = Triple(clampedSavedPos, dur, false)
                _lyricActiveIndex.value = 0
                _uiState.update {
                    it.copy(
                        currentSong = savedSong,
                        queue = listOf(savedSong),
                        durationMs = dur,
                        isLiked = isLiked,
                        isPlaying = false,
                        isLoadingLyrics = true
                    )
                }
                lyricsJob?.cancel()
                lyricsJob = viewModelScope.launch {
                    fetchLyrics(savedSong)
                }
            }
            startProgressTracker()
        }
    }

    fun playPlaylist(playlistId: String, title: String = "Playlist", fallbackArtwork: String? = null) {
        viewModelScope.launch {
            // 1. Cek apakah ini playlist lokal buatan user (termasuk "Lagu Offline")
            try {
                val uid = currentUserId ?: firebaseAuth.currentUser?.uid ?: ""
                val userPlaylists = playlistPreferences.getUserPlaylists(uid).firstOrNull() ?: emptyList()
                val userPl = userPlaylists.firstOrNull { it.id == playlistId || it.title.equals(title, ignoreCase = true) }
                if (userPl != null && userPl.songs.isNotEmpty()) {
                    setSong(userPl.songs.first(), queue = userPl.songs)
                    return@launch
                }
            } catch (_: Exception) {}

            try {
                if (playlistId.startsWith("VL") || playlistId.startsWith("PL") || playlistId.startsWith("MPRE") || playlistId.startsWith("RD")) {
                    val detail = apiService.getPlaylist(playlistId)
                    if (detail.tracks.isNotEmpty()) {
                        setSong(detail.tracks.first(), queue = detail.tracks)
                        return@launch
                    }
                }
            } catch (_: Exception) {}

            try {
                val searchRes = apiService.search(query = title, filter = "songs")
                if (searchRes.songs.isNotEmpty()) {
                    setSong(searchRes.songs.first(), queue = searchRes.songs)
                    return@launch
                }
            } catch (_: Exception) {}

            val fallbackSong = SongDto(
                videoId = playlistId,
                title = title,
                artists = emptyList(),
                thumbnailUrl = fallbackArtwork
            )
            setSong(fallbackSong)
        }
    }

    fun setSong(song: SongDto, queue: List<SongDto> = emptyList(), startPositionMs: Long = 0L) {
        val currentQueue = if (queue.isNotEmpty()) queue else listOf(song)
        val idx = currentQueue.indexOfFirst { it.videoId == song.videoId }.coerceAtLeast(0)
        val isLiked = currentUserId?.let { getLikedPrefs(it).getBoolean(song.videoId, false) } ?: false
        errorRetryCount = 0

        // 1. Immediately cut off previous audio
        // 1. Immediately cut off previous audio safely
        try {
            if (exoPlayer.isPlaying) {
                exoPlayer.pause()
            }
            if (exoPlayer.mediaItemCount > 0) {
                if (startPositionMs > 0L) {
                    exoPlayer.seekTo(startPositionMs)
                } else {
                    exoPlayer.seekTo(0L)
                }
            }
        } catch (_: Exception) {}

        // 2. Check instant lyrics cache
        val cachedLyrics = lyricsCache[song.videoId]
        val initialLyrics = cachedLyrics?.first ?: emptyList()
        val initialIsSynced = cachedLyrics?.second ?: false
        val initialIsLoadingLyrics = cachedLyrics == null
        val cachedLang = lyricsTranslationManager.getCachedLanguage(song.videoId)
        val cachedTrans = lyricsTranslationManager.getCachedTranslation(song.videoId) ?: emptyMap()

        val dur = parseDurationStringToMs(song.duration) ?: 262000L
        _playbackTick.value = Triple(startPositionMs, dur, true)
        _lyricActiveIndex.value = -1
        _uiState.update {
            it.copy(
                currentSong = song,
                queue = currentQueue,
                currentIndex = idx,
                durationMs = dur,
                isLiked = isLiked,
                isPlaying = true,
                lyrics = initialLyrics,
                isSyncedLyrics = initialIsSynced,
                isLoadingLyrics = initialIsLoadingLyrics,
                lyricOffsetMs = 0L,
                detectedLanguage = cachedLang,
                translatedLyrics = cachedTrans,
                isTranslating = false,
                translationError = null,
                error = null
            )
        }

        viewModelScope.launch {
            try {
                val savedOffset = lyricsPreferences.getLyricOffsetMs(song.videoId)
                if (_uiState.value.currentSong?.videoId == song.videoId) {
                    _uiState.update { it.copy(lyricOffsetMs = savedOffset) }
                    val st = _uiState.value
                    if (st.isSyncedLyrics && st.lyrics.isNotEmpty()) {
                        val activeIdx = calculateActiveIndex(startPositionMs, savedOffset, st.lyrics)
                        if (activeIdx != _lyricActiveIndex.value) _lyricActiveIndex.value = activeIdx
                    }
                }
            } catch (_: Exception) {}
        }

        viewModelScope.launch {
            try {
                val uid = currentUserId ?: ""
                lastPlayedSongPreferences.saveLastPlayedSong(song, uid, startPositionMs)
            } catch (_: Exception) {}
        }

        historyRepository.addSongToHistory(song)
        if (startPositionMs > 0L) {
            historyRepository.updateSongProgress(song, startPositionMs, dur)
        }

        // Delegate to singleton PlaybackStateManager
        playbackStateManager.setSong(song, currentQueue, idx, startPositionMs)

        lyricsJob?.cancel()
        if (cachedLyrics == null) {
            lyricsJob = viewModelScope.launch {
                fetchLyrics(song)
            }
        } else if (initialLyrics.isNotEmpty()) {
            detectAndPrepareTranslation(song, initialLyrics)
        }

        startProgressTracker()

        // Prefetch next track stream AND lyrics in advance for instantaneous transition
        prefetchNextSong(currentQueue, idx, _uiState.value.isShuffle)
    }

    private fun cleanTrackTitle(title: String): String {
        return title
            .replace(Regex("""(?i)\(.*?official.*?\)|\[.*?official.*?\]"""), "")
            .replace(Regex("""(?i)\(.*?video.*?\)|\[.*?video.*?\]"""), "")
            .replace(Regex("""(?i)\(.*?audio.*?\)|\[.*?audio.*?\]"""), "")
            .replace(Regex("""(?i)\(.*?lyrics?.*?\)|\[.*?lyrics?.*?\]"""), "")
            .replace(Regex("""(?i)\(.*?feat\..*?\)|\[.*?feat\..*?\]"""), "")
            .replace(Regex("""(?i)\(.*?ft\..*?\)|\[.*?ft\..*?\]"""), "")
            .replace(Regex("""(?i)\s*-\s*(official|video|audio|lyrics?)\b.*"""), "")
            .trim()
            .ifBlank { title }
    }

    private fun prefetchNextSong(queue: List<SongDto>, currentIndex: Int, isShuffle: Boolean) {
        prefetchJob?.cancel()
        if (queue.isEmpty()) return

        val nextIndex = if (isShuffle && queue.size > 1) {
            val candidates = queue.indices.filter { it != currentIndex }
            candidates.randomOrNull() ?: currentIndex
        } else if (currentIndex < queue.size - 1) {
            currentIndex + 1
        } else {
            0
        }

        val prevIndex = if (currentIndex > 0) currentIndex - 1 else queue.size - 1
        val nextSong = queue.getOrNull(nextIndex)
        val prevSong = if (prevIndex != nextIndex) queue.getOrNull(prevIndex) else null

        prefetchJob = viewModelScope.launch(Dispatchers.IO) {
            // 1. Prefetch stream URL untuk next song
            if (nextSong != null) {
                val existingStream = playbackStateManager.getCachedStreamUrl(nextSong.videoId) ?: streamUrlCache[nextSong.videoId]
                if (existingStream == null) {
                    try {
                        val res = apiService.getStreamUrl(nextSong.videoId)
                        if (res.audioUrl.isNotBlank()) {
                            if (streamUrlCache.size > 50) streamUrlCache.clear()
                            streamUrlCache[nextSong.videoId] = res.audioUrl
                            playbackStateManager.putCachedStreamUrl(nextSong.videoId, res.audioUrl)
                        }
                    } catch (_: Exception) {}
                }
            }

            // 2. Prefetch stream URL untuk previous song agar tombol Back responsif instan
            if (prevSong != null) {
                val existingPrevStream = playbackStateManager.getCachedStreamUrl(prevSong.videoId) ?: streamUrlCache[prevSong.videoId]
                if (existingPrevStream == null) {
                    try {
                        val res = apiService.getStreamUrl(prevSong.videoId)
                        if (res.audioUrl.isNotBlank()) {
                            if (streamUrlCache.size > 50) streamUrlCache.clear()
                            streamUrlCache[prevSong.videoId] = res.audioUrl
                            playbackStateManager.putCachedStreamUrl(prevSong.videoId, res.audioUrl)
                        }
                    } catch (_: Exception) {}
                }
            }

            // 3. Prefetch artwork & lyrics in parallel
            if (nextSong != null) {
                val existingArtwork = playbackStateManager.getCachedArtwork(nextSong.videoId) ?: artworkCache[nextSong.videoId]
                if (existingArtwork == null) {
                    loadArtworkBytes(nextSong.thumbnailUrl)?.also {
                        if (artworkCache.size > 50) artworkCache.clear()
                        artworkCache[nextSong.videoId] = it
                        playbackStateManager.putCachedArtwork(nextSong.videoId, it)
                    }
                }

                if (!lyricsCache.containsKey(nextSong.videoId)) {
                    try {
                        val artistName = nextSong.artists.joinToString(", ") { it.name }.ifBlank { "Unknown Artist" }
                        val durationSec = parseDurationToSeconds(nextSong.duration)
                        val lyricsRes = apiService.getLyrics(
                            videoId = nextSong.videoId,
                            trackName = cleanTrackTitle(nextSong.title),
                            artistName = artistName,
                            duration = durationSec
                        )
                        val parsed = extractLyrics(lyricsRes.syncedLyrics, lyricsRes.plainLyrics)
                        if (lyricsCache.size > 50) lyricsCache.clear()
                        lyricsCache[nextSong.videoId] = parsed
                        Log.d("PlayerViewModel", "Prefetched next lyrics for ${nextSong.title}")
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun onSongEnded() {
        val finishedSong = _uiState.value.currentSong ?: return
        val durationMs = _uiState.value.durationMs

        historyRepository.markSongCompleted(finishedSong, durationMs)
        val uid = currentUserId ?: ""
        viewModelScope.launch {
            try {
                lastPlayedSongPreferences.saveLastPlayedPosition(0L, uid)
            } catch (_: Exception) {}
        }

        val queue = _uiState.value.queue
        val currentIndex = _uiState.value.currentIndex

        // Handle "Akhir lagu" sleep timer
        if (_uiState.value.sleepTimerMinutes == -1) {
            exoPlayer.pause()
            _uiState.update { it.copy(isPlaying = false, sleepTimerMinutes = null) }
            return
        }

        if (_uiState.value.isRepeat) {
            seekTo(0L)
            if (exoPlayer.mediaItemCount > 0) exoPlayer.play()
            return
        }

        val isLastInQueue = currentIndex >= queue.size - 1
        if (isLastInQueue && !_uiState.value.isShuffle) {
            fetchSmartNextQueue(finishedSong)
        } else {
            skipNext()
        }
    }

    /**
     * Genre-aware auto-queue algorithm — improved.
     *
     * Strategy:
     * 1. Query 1: "[artist] similar artists" — most relevant, finds songs from same scene
     * 2. Query 2: genre-based query detected from artist name / song title
     * 3. Query 3: title keywords for variety
     * 4. Merge, deduplicate, diverse filter, take best 12.
     *
     * fetchSmartNextQueue is now also called proactively 30s before song ends
     * via startProgressTracker to eliminate the dead-air gap.
     */
    private fun fetchSmartNextQueue(finishedSong: SongDto) {
        autoQueueJob?.cancel()
        autoQueueJob = viewModelScope.launch {
            try {
                val finishedArtist = finishedSong.artists.firstOrNull()?.name?.trim() ?: ""
                val currentQueueIds = _uiState.value.queue.map { it.videoId }.toSet()

                // ── Query 1: artis yang sama + similar → paling relevan ─────────────────
                val artistQuery = if (finishedArtist.isNotBlank()) {
                    "$finishedArtist similar songs"
                } else {
                    finishedSong.title.take(25) + " similar"
                }

                // ── Query 2: genre berdasarkan artis & judul ──────────────────────────
                val titleLower = finishedSong.title.lowercase()
                val artistLower = finishedArtist.lowercase()
                val detectedGenreQuery = detectGenreQuery(titleLower, artistLower, finishedArtist)

                // ── Query 3: judul keywords untuk variasi ─────────────────────────────
                val titleWords = finishedSong.title
                    .replace(Regex("[^\\w\\s]"), "")
                    .split(" ")
                    .filter { it.length > 3 && it.lowercase() !in setOf("feat", "with", "from", "live", "remix", "official", "audio", "video") }
                    .take(2)
                val titleQuery = titleWords.joinToString(" ").ifBlank { finishedSong.title.take(20) }

                // ── Fetch semua 3 queries secara paralel ─────────────────────────────
                val (res1, res2, res3) = withContext(Dispatchers.IO) {
                    val d1 = async {
                        try { apiService.search(query = artistQuery, filter = "songs").songs } catch (_: Exception) { emptyList() }
                    }
                    val d2 = async {
                        try { apiService.search(query = detectedGenreQuery, filter = "songs").songs } catch (_: Exception) { emptyList() }
                    }
                    val d3 = async {
                        try { apiService.search(query = titleQuery, filter = "songs").songs } catch (_: Exception) { emptyList() }
                    }
                    Triple(d1.await(), d2.await(), d3.await())
                }

                // ── Merge, deduplicate ─────────────────────────────────────────────────
                val seen = mutableSetOf<String>()
                val merged = (res1 + res2 + res3).filter { song ->
                    val isNew = seen.add(song.videoId)
                    isNew &&
                    song.videoId != finishedSong.videoId &&
                    song.videoId !in currentQueueIds
                }

                // ── Batasi max 3 lagu dari artis yang sama persis agar ada variasi ────
                var sameArtistCount = 0
                val diversePool = merged.filter { song ->
                    val songArtist = song.artists.firstOrNull()?.name?.trim() ?: ""
                    val isSameArtist = finishedArtist.isNotBlank() &&
                        songArtist.isNotBlank() &&
                        songArtist.equals(finishedArtist, ignoreCase = true)
                    if (isSameArtist) {
                        sameArtistCount++
                        sameArtistCount <= 3
                    } else {
                        true
                    }
                }

                val nextQueue = diversePool.take(12)
                val songsToAdd = if (nextQueue.isNotEmpty()) nextQueue else merged.take(6)
                if (songsToAdd.isNotEmpty()) {
                    playbackStateManager.appendQueue(songsToAdd)
                    _uiState.update { it.copy(queue = playbackStateManager.queue.value) }
                }
            } catch (_: Exception) {
                // Ignore prefetch network failure; queue loop handles natural wrap-around
            }
        }
    }

    /**
     * Deteksi genre berdasarkan nama artis dan kata kunci judul.
     * Strategi baru: prioritaskan nama artis sebagai anchor (paling akurat),
     * lalu fallback ke genre dari kata kunci judul.
     * Mengembalikan search query yang relevan untuk lagu sejenis.
     */
    private fun detectGenreQuery(titleLower: String, artistLower: String, artistName: String): String {
        // Prioritas 1: Deteksi berdasarkan nama artis (lebih akurat dari keyword judul)
        return when {
            // K-Pop — artis spesifik
            listOf("bts", "blackpink", "twice", "nct", "aespa", "stray kids", "seventeen",
                   "ive", "newjeans", "txt", "enhypen", "itzy", "red velvet", "exo", "shinee",
                   "bigbang", "2ne1", "super junior", "got7", "monsta x").any { artistLower.contains(it) } ||
            listOf("kpop", "k-pop").any { titleLower.contains(it) || artistLower.contains(it) } ->
                "kpop hits ${if (artistName.isNotBlank()) "like $artistName" else "terbaru"}"

            // Hip-hop / Rap Indonesia
            listOf("rap", "hip hop", "hip-hop", "freestyle", "diss", "bars", "flow", "drill", "trap",
                   "hiphop").any { titleLower.contains(it) || artistLower.contains(it) } ->
                "hip hop rap Indonesia hits"

            // R&B / Soul
            listOf("r&b", "rnb", "soul", "groove", "slow jam").any { titleLower.contains(it) || artistLower.contains(it) } ->
                "rnb soul hits Indonesia"

            // EDM / Electronic
            listOf("edm", "dj ", "house", "techno", "trance", "dubstep", "electronic").any {
                titleLower.contains(it) || artistLower.contains(it)
            } -> "edm electronic dance hits"

            // Rock / Indie / Metal
            listOf("rock", "indie", "punk", "metal", "grunge", "band", "alternative").any {
                titleLower.contains(it) || artistLower.contains(it)
            } -> "indie rock alternative hits Indonesia"

            // Pop Indonesia — keyword bahasa Indonesia di judul
            listOf("cinta", "sayang", "hati", "rindu", "kamu", "aku", "kita", "kasih",
                   "malam", "mimpi", "pergi", "datang", "pulang", "jangan", "masih",
                   "lagi", "saja", "pernah", "bisa", "untuk").any { titleLower.contains(it) } ->
                if (artistName.isNotBlank()) "lagu pop indonesia mirip $artistName"
                else "lagu pop indonesia hits terbaru"

            // Ballad / Sad songs (English)
            listOf("sad", "broken", "alone", "miss", "lost", "pain", "cry", "tears",
                   "goodbye", "heartbreak", "without").any { titleLower.contains(it) } ->
                "sad songs ballad emotional hits"

            // Fallback: gunakan nama artis langsung → paling relevan
            artistName.isNotBlank() ->
                "songs like $artistName"

            else -> "top hits terbaru"
        }
    }



    private fun startPlaybackService() {
        // startForegroundService (bukan startService): di Android 8+ hanya ini yang
        // menaikkan MediaSessionService ke foreground â€” startService biasa tidak
        // memanggil startForeground sehingga sistem bebas kill proses saat swipe.
        // Di Android 12+ bisa lempar ForegroundServiceStartNotAllowedException bila
        // dipicu dari background: tangkap agar tidak crash, musik tetap jalan selama
        // proses hidup (foreground didapat lagi saat user kembali).
        try {
            val serviceIntent = Intent(context, MusicPlaybackService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            Log.w("PlayerViewModel", "Failed to start MusicPlaybackService: ${e.message}")
        }
    }

    // isLoadingAudio/isLoadingAutoQueue tidak di-emit lagi (tidak ada observer UI);
    // isLoadingLyrics tetap dipakai preview + full lyrics (lyrics.isEmpty() saja ambigu
    // dengan "lirik memang tidak ada").
    private suspend fun fetchAudioStream(song: SongDto, resumePositionMs: Long = 0L) {
        val artistName = song.artists.joinToString(", ") { it.name }.ifBlank { "Unknown Artist" }

        // Fetch artwork in parallel with stream URL resolution
        val artworkDeferred = viewModelScope.async(Dispatchers.IO) {
            artworkCache[song.videoId] ?: loadArtworkBytes(song.thumbnailUrl)?.also {
                if (artworkCache.size > 30) artworkCache.clear()
                artworkCache[song.videoId] = it
            }
        }

        var resolvedVideoId = song.videoId
        var streamUrl: String? = playbackStateManager.getCachedStreamUrl(song.videoId) ?: streamUrlCache[song.videoId]

        // 1. Try direct videoId if not prefetched
        if (streamUrl.isNullOrBlank()) {
            try {
                val streamRes = apiService.getStreamUrl(resolvedVideoId)
                if (streamRes.audioUrl.isNotBlank()) {
                    streamUrl = streamRes.audioUrl
                    if (streamUrlCache.size > 30) streamUrlCache.clear()
                    streamUrlCache[song.videoId] = streamUrl
                    playbackStateManager.putCachedStreamUrl(song.videoId, streamUrl)
                }
            } catch (e: Exception) {
                Log.w("PlayerViewModel", "Direct stream resolution failed for ${song.videoId}: ${e.message}")
            }
        }

        // 2. Auto-healing fallback search if direct ID failed
        if (streamUrl.isNullOrBlank()) {
            try {
                val query = "${song.title} $artistName".trim()
                val searchRes = apiService.search(query = query, filter = "songs")
                val matched = searchRes.songs.firstOrNull()
                if (matched != null) {
                    resolvedVideoId = matched.videoId
                    val streamRes = apiService.getStreamUrl(resolvedVideoId)
                    if (streamRes.audioUrl.isNotBlank()) {
                        streamUrl = streamRes.audioUrl
                        if (streamUrlCache.size > 30) streamUrlCache.clear()
                        streamUrlCache[song.videoId] = streamUrl
                        playbackStateManager.putCachedStreamUrl(song.videoId, streamUrl)
                        if (song.thumbnailUrl.isNullOrBlank() && !matched.thumbnailUrl.isNullOrBlank()) {
                            _uiState.update { it.copy(currentSong = it.currentSong?.copy(thumbnailUrl = matched.thumbnailUrl)) }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Fallback search and stream resolution failed", e)
            }
        }

        // 3. Play audio through ExoPlayer. Stale-guard: only touch the player if this
        // song is still current â€” otherwise a late network reply overwrites the new
        // track the user already switched to (double audio / wrong position = crash reports).
        if (_uiState.value.currentSong?.videoId != song.videoId) {
            return
        }
        if (!streamUrl.isNullOrBlank()) {
            try {
                val cachedBytes = artworkCache[song.videoId]
                val metadataBuilder = MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(artistName)
                    .setArtworkUri(song.thumbnailUrl?.let { Uri.parse(it) })

                if (cachedBytes != null && cachedBytes.isNotEmpty()) {
                    metadataBuilder.setArtworkData(cachedBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                }
                val mediaMetadata = metadataBuilder.build()

                val mediaItem = MediaItem.Builder()
                    .setUri(streamUrl)
                    .setMediaMetadata(mediaMetadata)
                    .build()

                exoPlayer.setMediaItem(mediaItem)
                if (resumePositionMs > 0L) {
                    exoPlayer.seekTo(resumePositionMs)
                } else {
                    exoPlayer.seekTo(0L)
                }
                exoPlayer.prepare()
                exoPlayer.play()
                _uiState.update { it.copy(isPlaying = true) }

                // Attach artwork asynchronously once downloaded without delaying audio playback
                if (cachedBytes == null) {
                    viewModelScope.launch(Dispatchers.IO) {
                        val bytes = try { artworkDeferred.await() } catch (_: Exception) { null }
                        if (bytes != null && bytes.isNotEmpty() && _uiState.value.currentSong?.videoId == song.videoId) {
                            withContext(Dispatchers.Main) {
                                try {
                                    val updatedMetadata = metadataBuilder
                                        .setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                                        .build()
                                    val updatedItem = mediaItem.buildUpon()
                                        .setMediaMetadata(updatedMetadata)
                                        .build()
                                    exoPlayer.replaceMediaItem(exoPlayer.currentMediaItemIndex, updatedItem)
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "ExoPlayer setMediaItem failed", e)
            }
        } else {
            // No stream resolved at all â€” surface it instead of hanging on a spinner.
            _uiState.update { it.copy(error = "Tidak bisa memuat audio. Cek koneksi lalu coba lagi.") }
        }
    }

    private suspend fun fetchLyrics(song: SongDto) {
        val artistName = song.artists.joinToString(", ") { it.name }.ifBlank { "Unknown Artist" }
        try {
            // Kirim duration (detik) agar lrclib pilih versi lagu yang SAMA dengan YouTube.
            // Tanpa duration, lrclib bisa pilih radio edit / live version yang timing-nya beda.
            val durationSec = parseDurationToSeconds(song.duration)
            val lyricsRes = apiService.getLyrics(
                videoId = song.videoId,
                trackName = cleanTrackTitle(song.title),
                artistName = artistName,
                duration = durationSec
            )
            val parsedResult = extractLyrics(lyricsRes.syncedLyrics, lyricsRes.plainLyrics)
            if (lyricsCache.size > 30) lyricsCache.clear()
            lyricsCache[song.videoId] = parsedResult

            // Only update UI if this song is still current
            if (_uiState.value.currentSong?.videoId == song.videoId) {
                val savedOffset = lyricsPreferences.getLyricOffsetMs(song.videoId)
                val currentPos = _playbackTick.value.first
                val searchIdx = if (parsedResult.second && parsedResult.first.isNotEmpty()) {
                    calculateActiveIndex(currentPos, savedOffset, parsedResult.first)
                } else -1
                _lyricActiveIndex.value = searchIdx
                _uiState.update {
                    it.copy(
                        lyrics = parsedResult.first,
                        isSyncedLyrics = parsedResult.second,
                        isLoadingLyrics = false,
                        lyricOffsetMs = savedOffset
                    )
                }
                if (parsedResult.first.isNotEmpty()) {
                    detectAndPrepareTranslation(song, parsedResult.first)
                }
            }
        } catch (e: Exception) {
            Log.w("PlayerViewModel", "Fetch lyrics failed for ${song.title}: ${e.message}")
            if (_uiState.value.currentSong?.videoId == song.videoId) {
                _uiState.update { it.copy(lyrics = emptyList(), isSyncedLyrics = false, isLoadingLyrics = false) }
            }
        }
    }

    private fun extractLyrics(syncedLyrics: String?, plainLyrics: String?): Pair<List<LyricLine>, Boolean> {
        if (!syncedLyrics.isNullOrBlank()) {
            val parsedLines = parseLrc(syncedLyrics)
            if (parsedLines.isNotEmpty()) {
                return Pair(parsedLines, true)
            }
        }
        if (!plainLyrics.isNullOrBlank()) {
            val lines = plainLyrics.lines()
                .filter { it.isNotBlank() }
                .map { LyricLine(text = it.trim()) }
            if (lines.isNotEmpty()) {
                return Pair(lines, false)
            }
        }
        return Pair(emptyList(), false)
    }

    private fun parseLrc(lrcContent: String): List<LyricLine> {
        val tagRegex = Regex("""\[(\d{1,2}):(\d{2})[.:](\d{2,3})\]""")
        val result = mutableListOf<LyricLine>()
        lrcContent.lineSequence().forEach { line ->
            val trimmed = line.trim()
            val matches = tagRegex.findAll(trimmed).toList()
            if (matches.isNotEmpty()) {
                val lastMatch = matches.last()
                val text = trimmed.substring(lastMatch.range.last + 1).trim()
                if (text.isNotBlank()) {
                    for (match in matches) {
                        val min = match.groupValues[1].toLongOrNull() ?: 0L
                        val sec = match.groupValues[2].toLongOrNull() ?: 0L
                        val msStr = match.groupValues[3]
                        val ms = msStr.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                        val totalMs = (min * 60 + sec) * 1000 + ms
                        result.add(LyricLine(timeMs = totalMs, text = text))
                    }
                }
            }
        }
        return result.sortedBy { it.timeMs }
    }

    /**
     * Hitung baris aktif lirik secara efisien dengan binary search.
     * Mengembalikan -1 jika posisi audio masih di intro (belum mencapai baris vokal pertama).
     */
    private fun calculateActiveIndex(
        positionMs: Long,
        offsetMs: Long,
        lyrics: List<LyricLine>
    ): Int {
        if (lyrics.isEmpty()) return -1
        val effectivePos = (positionMs + offsetMs).coerceAtLeast(0L)

        // Intro: belum ada baris lirik yang mulai dinyanyikan
        if (effectivePos < lyrics.first().timeMs) {
            return -1
        }
        if (effectivePos >= lyrics.last().timeMs) {
            return lyrics.lastIndex
        }

        val searchIdx = lyrics.binarySearchBy(effectivePos) { it.timeMs }
        return if (searchIdx >= 0) {
            searchIdx
        } else {
            val insertionPoint = -(searchIdx + 1)
            (insertionPoint - 1).coerceAtLeast(0)
        }
    }

    /**
     * Konversi string durasi "mm:ss" atau "h:mm:ss" (dari SongDto.duration) ke detik.
     * Dipakai untuk parameter duration di getLyrics agar lrclib pilih versi yang
     * durasinya sama dengan audio YouTube (bukan radio edit / live version).
     * Return null jika format tidak dikenali.
     */
    private fun parseDurationToSeconds(duration: String?): Int? {
        if (duration.isNullOrBlank()) return null
        return try {
            val parts = duration.trim().split(":")
            when (parts.size) {
                2 -> parts[0].toInt() * 60 + parts[1].toInt()       // "mm:ss"
                3 -> parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toInt() // "h:mm:ss"
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    // Progress dibagi dua biar UI ringan:
    // - playbackState: posisi+durasi+isPlaying, update 1x/detik (slider + mini bar).
    //   PlayerScreen/FullLyricsScreen ambil flow ini SAJA untuk hal yang tidak butuh lirik.
    // - lyricActiveIndex: cuma emit saat baris lirik BERGANTI (bukan tiap detik).
    // Sebelumnya satu StateFlow PlayerUiState di-emit tiap 300ms â†’ seluruh PlayerScreen,
    // MiniPlayerBar, dan kedua daftar lirik recompose 3x/detik. Itu biang lag + jank.
    private val _playbackTick = MutableStateFlow(Triple(0L, 262000L, false))
    val playbackTick: StateFlow<Triple<Long, Long, Boolean>> = _playbackTick.asStateFlow()

    private val _lyricActiveIndex = MutableStateFlow(0)
    val lyricActiveIndex: StateFlow<Int> = _lyricActiveIndex.asStateFlow()

    private val artworkCache = ConcurrentHashMap<String, ByteArray>()

    private suspend fun loadArtworkBytes(url: String?): ByteArray? {
        if (url.isNullOrBlank()) return null
        return withContext(Dispatchers.IO) {
            try {
                val loader = coil.Coil.imageLoader(context)
                val request = coil.request.ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .size(512)
                    .build()
                val result = loader.execute(request)
                val drawable = (result as? coil.request.SuccessResult)?.drawable
                if (drawable is android.graphics.drawable.BitmapDrawable) {
                    val stream = ByteArrayOutputStream()
                    drawable.bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, stream)
                    stream.toByteArray()
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun startProgressTracker() {
        progressTrackerJob?.cancel()
        progressTrackerJob = viewModelScope.launch {
            // PERF: Adaptive tick rate:
            // - ~100ms (10 Hz) saat lirik tersinkron aktif → presisi vokal tanpa boros CPU
            // - 250ms  (4 Hz)  saat tidak ada lirik / lirik statis → progress bar sudah cukup
            var saveIntervalCounter = 0
            while (isActive) {
                val st = _uiState.value
                val hasSyncedLyrics = st.isSyncedLyrics && st.lyrics.isNotEmpty()
                val tickIntervalMs = if (hasSyncedLyrics) 100L else 250L
                delay(tickIntervalMs)

                if (exoPlayer.mediaItemCount > 0 && exoPlayer.isPlaying) {
                    val pos = exoPlayer.currentPosition
                    val dur = if (exoPlayer.duration > 0) exoPlayer.duration else st.durationMs
                    val cappedPos = pos.coerceAtMost(dur)

                    if (hasSyncedLyrics) {
                        val idx = calculateActiveIndex(cappedPos, st.lyricOffsetMs, st.lyrics)
                        if (idx != _lyricActiveIndex.value) _lyricActiveIndex.value = idx
                    }

                    // Emit progress tick
                    _playbackTick.value = Triple(cappedPos, dur, true)

                    // Simpan posisi berkala tiap ~3 detik
                    saveIntervalCounter += tickIntervalMs.toInt()
                    if (saveIntervalCounter >= 3000) {
                        saveIntervalCounter = 0
                        val uid = currentUserId ?: ""
                        val song = st.currentSong
                        if (song != null) {
                            lastPlayedSongPreferences.saveLastPlayedPosition(cappedPos, uid)
                            historyRepository.updateSongProgress(song, cappedPos, dur)
                        }
                    }

                    // Proactive smart-queue prefetch: mulai fetch antrian baru 30 detik
                    // sebelum lagu terakhir habis → eliminasi dead-air gap antar lagu.
                    // Guard: hanya kalau ini lagu terakhir, tidak shuffle, dan autoQueue idle.
                    if (dur > 0L) {
                        val remainingMs = dur - cappedPos
                        val isLastInQueue = st.currentIndex >= st.queue.size - 1
                        val isAutoQueueIdle = autoQueueJob?.isActive != true
                        if (remainingMs in 5_000L..30_000L && isLastInQueue && !st.isShuffle && isAutoQueueIdle) {
                            val song = st.currentSong
                            if (song != null) {
                                fetchSmartNextQueue(song)
                            }
                        }
                    }
                } else if (_playbackTick.value.third) {
                    val (pos, dur) = _playbackTick.value
                    _playbackTick.value = Triple(pos, dur, false)
                    saveIntervalCounter = 0
                }
            }
        }
    }


    private var lastTogglePlayPauseTime = 0L

    fun togglePlayPause() {
        val now = System.currentTimeMillis()
        if (now - lastTogglePlayPauseTime < 350L) return
        lastTogglePlayPauseTime = now

        val isCurrentlyPlaying = _uiState.value.isPlaying || exoPlayer.isPlaying
        val uiSong = _uiState.value.currentSong

        if (isCurrentlyPlaying) {
            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            val dur = if (exoPlayer.duration > 0) exoPlayer.duration else _uiState.value.durationMs
            _playbackTick.value = Triple(pos, dur, false)
            _uiState.update { it.copy(isPlaying = false) }
            playbackStateManager.togglePlayPause()
        } else {
            // Optimistically update UI so user immediately sees active state
            val (curPos, curDur, _) = _playbackTick.value
            val dur = if (curDur > 0) curDur else _uiState.value.durationMs
            _playbackTick.value = Triple(curPos, dur, true)
            _uiState.update { it.copy(isPlaying = true, error = null) }

            if (exoPlayer.mediaItemCount > 0 && exoPlayer.playbackState != Player.STATE_IDLE && exoPlayer.playerError == null) {
                playbackStateManager.togglePlayPause()
            } else if (uiSong != null) {
                setSong(uiSong, _uiState.value.queue, startPositionMs = curPos)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceIn(0L, _uiState.value.durationMs)
        if (exoPlayer.mediaItemCount > 0) exoPlayer.seekTo(clamped)
        val st = _uiState.value
        if (st.isSyncedLyrics && st.lyrics.isNotEmpty()) {
            val idx = calculateActiveIndex(clamped, st.lyricOffsetMs, st.lyrics)
            if (idx != _lyricActiveIndex.value) _lyricActiveIndex.value = idx
        }
        _playbackTick.value = Triple(clamped, st.durationMs, st.isPlaying)
        val uid = currentUserId ?: ""
        val song = _uiState.value.currentSong
        viewModelScope.launch {
            try {
                lastPlayedSongPreferences.saveLastPlayedPosition(clamped, uid)
            } catch (_: Exception) {}
        }
        if (song != null) {
            historyRepository.updateSongProgress(song, clamped, _uiState.value.durationMs)
        }
    }

    /**
     * Sesuaikan offset lirik manual per-lagu (deltaMs, misal +500ms atau -500ms).
     * Disimpan per videoId di DataStore agar lagu yang meleset cukup dikoreksi 1x.
     */
    fun adjustLyricOffset(deltaMs: Long) {
        val songId = _uiState.value.currentSong?.videoId ?: return
        val newOffset = _uiState.value.lyricOffsetMs + deltaMs
        _uiState.update { it.copy(lyricOffsetMs = newOffset) }
        viewModelScope.launch {
            lyricsPreferences.setLyricOffsetMs(songId, newOffset)
        }
        val st = _uiState.value
        if (st.isSyncedLyrics && st.lyrics.isNotEmpty()) {
            val currentPos = _playbackTick.value.first
            val idx = calculateActiveIndex(currentPos, newOffset, st.lyrics)
            if (idx != _lyricActiveIndex.value) _lyricActiveIndex.value = idx
        }
    }

    /** Reset offset lirik lagu saat ini kembali ke 0.0s */
    fun resetLyricOffset() {
        val songId = _uiState.value.currentSong?.videoId ?: return
        _uiState.update { it.copy(lyricOffsetMs = 0L) }
        viewModelScope.launch {
            lyricsPreferences.setLyricOffsetMs(songId, 0L)
        }
        val st = _uiState.value
        if (st.isSyncedLyrics && st.lyrics.isNotEmpty()) {
            val currentPos = _playbackTick.value.first
            val idx = calculateActiveIndex(currentPos, 0L, st.lyrics)
            if (idx != _lyricActiveIndex.value) _lyricActiveIndex.value = idx
        }
    }

    private var translationJob: Job? = null

    private fun detectAndPrepareTranslation(song: SongDto, lyrics: List<LyricLine>) {
        if (lyrics.isEmpty()) return
        val cachedLang = lyricsTranslationManager.getCachedLanguage(song.videoId)
        if (cachedLang != null) {
            if (_uiState.value.currentSong?.videoId == song.videoId) {
                _uiState.update { it.copy(detectedLanguage = cachedLang) }
                if (cachedLang != "id" && cachedLang != "in" && cachedLang != "en" && cachedLang != "und") {
                    if (_uiState.value.isTranslationActive) {
                        fetchTranslation(song.videoId, cachedLang, lyrics)
                    } else {
                        prefetchTranslation(song.videoId, cachedLang, lyrics)
                    }
                }
            }
            return
        }

        viewModelScope.launch {
            val aName = song.artists.joinToString(", ") { it.name }.ifBlank { "Unknown Artist" }
            val lang = lyricsTranslationManager.detectLanguage(
                lyrics = lyrics,
                songTitle = song.title,
                artistName = aName,
                videoId = song.videoId
            )
            if (_uiState.value.currentSong?.videoId == song.videoId) {
                _uiState.update { it.copy(detectedLanguage = lang) }
                if (lang != null && lang != "id" && lang != "in" && lang != "en" && lang != "und") {
                    if (_uiState.value.isTranslationActive) {
                        fetchTranslation(song.videoId, lang, lyrics)
                    } else {
                        prefetchTranslation(song.videoId, lang, lyrics)
                    }
                }
            }
        }
    }

    /**
     * Toggle status aktif terjemahan lirik.
     * Tersimpan di DataStore dan langsung memicu terjemahan jika belum ada di cache.
     */
    fun toggleTranslation() {
        val currentState = _uiState.value
        val newActive = !currentState.isTranslationActive
        viewModelScope.launch {
            lyricsPreferences.setTranslationEnabled(newActive)
        }
        _uiState.update { it.copy(isTranslationActive = newActive) }

        if (newActive) {
            val song = currentState.currentSong
            val lang = currentState.detectedLanguage
            if (song != null && lang != null && currentState.canTranslate && currentState.lyrics.isNotEmpty()) {
                if (currentState.translatedLyrics.isEmpty()) {
                    fetchTranslation(song.videoId, lang, currentState.lyrics)
                }
            }
        }
    }

    fun fetchTranslation(videoId: String, sourceLang: String, lyrics: List<LyricLine>) {
        if (lyrics.isEmpty()) return
        translationJob?.cancel()
        translationJob = viewModelScope.launch {
            _uiState.update { it.copy(isTranslating = true, translationError = null) }
            try {
                val result = lyricsTranslationManager.translateLyrics(videoId, sourceLang, lyrics)
                if (_uiState.value.currentSong?.videoId == videoId) {
                    _uiState.update {
                        it.copy(
                            translatedLyrics = result,
                            isTranslating = false,
                            translationError = if (result.isEmpty()) "Gagal menerjemahkan lirik" else null
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Translation failed: ${e.message}", e)
                if (_uiState.value.currentSong?.videoId == videoId) {
                    _uiState.update { it.copy(isTranslating = false, translationError = e.message) }
                }
            }
        }
    }

    private fun prefetchTranslation(videoId: String, sourceLang: String, lyrics: List<LyricLine>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = lyricsTranslationManager.translateLyrics(videoId, sourceLang, lyrics)
                if (_uiState.value.currentSong?.videoId == videoId && result.isNotEmpty()) {
                    _uiState.update { it.copy(translatedLyrics = result) }
                }
            } catch (e: Exception) {
                Log.d("PlayerViewModel", "Prefetch translation background: ${e.message}")
            }
        }
    }

    fun seekToFraction(fraction: Float) {
        val target = (fraction.coerceIn(0f, 1f) * _uiState.value.durationMs).toLong()
        seekTo(target)
    }

    /** Append to end of queue (Spotify semantics: duplicates allowed). */
    fun addToQueue(song: SongDto) {
        _uiState.update { current ->
            current.copy(queue = current.queue + song)
        }
    }

    /** Insert song right after currently playing track (Spotify "Play Next"). */
    fun playNext(song: SongDto) {
        _uiState.update { current ->
            val playingId = current.currentSong?.videoId
            // Same-as-current: duplicate it (don't remove the playing occurrence).
            // Other song: move existing occurrence(s) to right after current.
            val base = if (song.videoId == playingId) current.queue
                else current.queue.filterNot { it.videoId == song.videoId }
            val playingIdx = base.indexOfFirst { it.videoId == playingId }.coerceAtLeast(0)
            val insertAt = (playingIdx + 1).coerceIn(0, base.size)
            val newQueue = base.toMutableList().apply { add(insertAt, song) }
            val newIndex = newQueue.indexOfFirst { it.videoId == playingId }
                .let { if (it == -1) 0 else it }
            current.copy(queue = newQueue, currentIndex = newIndex)
        }
    }

    fun removeQueueItem(videoId: String) {
        _uiState.update { current ->
            if (videoId == current.currentSong?.videoId) return@update current
            if (current.queue.none { it.videoId == videoId }) return@update current
            val newQueue = current.queue.filterNot { it.videoId == videoId }
            val newIndex = newQueue.indexOfFirst { it.videoId == current.currentSong?.videoId }
                .let { if (it == -1) 0 else it }
            current.copy(queue = newQueue, currentIndex = newIndex)
        }
    }

    fun moveQueueItem(from: Int, to: Int) {
        _uiState.update { current ->
            val q = current.queue
            if (from !in q.indices || to !in q.indices || from == to) return@update current
            if (from == current.currentIndex || to == current.currentIndex) return@update current
            val newQueue = q.toMutableList().apply { add(to, removeAt(from)) }
            val playingId = current.currentSong?.videoId
            val newIndex = newQueue.indexOfFirst { it.videoId == playingId }.coerceAtLeast(0)
            current.copy(queue = newQueue, currentIndex = newIndex)
        }
    }

    /** Sleep timer: pause playback after [minutes]. Null cancels. */
    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _uiState.update { it.copy(sleepTimerMinutes = minutes) }
        if (minutes == null || minutes <= 0) return
        sleepTimerJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            if (isActive) {
                exoPlayer.pause()
                _uiState.update { it.copy(isPlaying = false, sleepTimerMinutes = null) }
            }
        }
    }

    fun skipNext() {
        val now = System.currentTimeMillis()
        if (now - lastSkipTimeMs < SKIP_COOLDOWN_MS) return  // debounce — abaikan spam tap
        lastSkipTimeMs = now
        playbackStateManager.skipNext()
    }

    fun skipPrevious() {
        val now = System.currentTimeMillis()
        if (now - lastSkipTimeMs < SKIP_COOLDOWN_MS) return  // debounce — abaikan spam tap
        lastSkipTimeMs = now
        playbackStateManager.skipPrevious()
    }

    fun toggleLike() {
        val song = _uiState.value.currentSong ?: return
        val newLiked = !_uiState.value.isLiked
        _uiState.update { it.copy(isLiked = newLiked) }

        val uid = currentUserId ?: return
        try {
            getLikedPrefs(uid).edit().putBoolean(song.videoId, newLiked).apply()
        } catch (_: Exception) {}

        viewModelScope.launch {
            // Otomatis sinkronkan ke playlist 'like song'
            try {
                val userPlaylists = playlistPreferences.getUserPlaylists(uid).firstOrNull() ?: emptyList()
                var likePlaylist = userPlaylists.firstOrNull { it.title.equals("like song", ignoreCase = true) }
                if (newLiked) {
                    if (likePlaylist == null) {
                        likePlaylist = playlistPreferences.createPlaylist(
                            title = "like song",
                            description = "Koleksi lagu yang kamu sukai",
                            uid = uid
                        )
                    }
                    playlistPreferences.addSongToPlaylist(likePlaylist.id, song, uid)
                } else {
                    if (likePlaylist != null) {
                        playlistPreferences.removeSongFromPlaylist(likePlaylist.id, song.videoId, uid)
                    }
                }
            } catch (e: Exception) {
                Log.w("PlayerViewModel", "Sync like song to playlist error: ${e.message}")
            }

            try {
                val docRef = firestore.collection("users")
                    .document(uid)
                    .collection("liked_songs")
                    .document(song.videoId)

                if (newLiked) {
                    docRef.set(
                        mapOf(
                            "videoId" to song.videoId,
                            "title" to song.title,
                            "artists" to song.artists.map { mapOf("id" to it.id, "name" to it.name) },
                            "thumbnailUrl" to song.thumbnailUrl,
                            "duration" to song.duration,
                            "likedAt" to System.currentTimeMillis()
                        )
                    )
                } else {
                    docRef.delete()
                }
            } catch (e: Exception) {
                Log.w("PlayerViewModel", "Firestore sync like error: ${e.message}")
            }
        }
    }

    fun toggleShuffle() { _uiState.update { it.copy(isShuffle = !it.isShuffle) } }

    fun toggleRepeat() {
        _uiState.update { it.copy(isRepeat = !it.isRepeat) }
        exoPlayer.repeatMode = if (_uiState.value.isRepeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    /**
     * Share current song via Android share intent.
     * Generates a YouTube Music link and a readable message.
     */
    fun shareSong() {
        val song = _uiState.value.currentSong ?: return
        val artistName = song.artists.joinToString(", ") { it.name }.ifBlank { "Songfy" }
        val ytMusicUrl = "https://music.youtube.com/watch?v=${song.videoId}"
        val shareText = "🎵 Lagi dengerin \"${song.title}\" by $artistName di Songfy!\n\n$ytMusicUrl"

        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, shareText)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "${song.title} - $artistName")
        }
        val chooser = android.content.Intent.createChooser(intent, "Bagikan lagu via")
        chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.w("PlayerViewModel", "Share intent failed: ${e.message}")
        }
    }

    // NOTE 2026-09-12: setLyricsOpen/setVolume/increaseVolume/decreaseVolume/
    // setEqPreset/generateWaveformAmplitudes/useFallbackLyrics removed â€” zero callers
    // in UI (grep). Volume/EQ belong in a settings sheet when actually built;
    // resurrect from git history, don't re-add dead weight to the hot state.

    private fun parseDurationStringToMs(durationStr: String?): Long? {
        if (durationStr.isNullOrBlank()) return null
        val parts = durationStr.split(":")
        return when (parts.size) {
            2 -> {
                val min = parts[0].toLongOrNull() ?: return null
                val sec = parts[1].toLongOrNull() ?: return null
                (min * 60 + sec) * 1000
            }
            3 -> {
                val hr = parts[0].toLongOrNull() ?: return null
                val min = parts[1].toLongOrNull() ?: return null
                val sec = parts[2].toLongOrNull() ?: return null
                (hr * 3600 + min * 60 + sec) * 1000
            }
            else -> null
        }
    }

    override fun onCleared() {
        super.onCleared()
        progressTrackerJob?.cancel()
        autoQueueJob?.cancel()
        prefetchJob?.cancel()
        audioJob?.cancel()
        lyricsJob?.cancel()
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        firebaseAuth.removeAuthStateListener(authStateListener)
        exoPlayer.removeListener(playerListener)
    }
}

