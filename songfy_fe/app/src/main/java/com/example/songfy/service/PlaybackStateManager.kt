package com.lagu.songfy.service

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.lagu.songfy.data.local.datastore.LastPlayedSongPreferences
import com.lagu.songfy.data.remote.api.SongfyApiService
import com.lagu.songfy.data.remote.dto.SongDto
import com.lagu.songfy.data.repository.OfflineSongRepository
import com.lagu.songfy.data.repository.SongHistoryRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackStateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exoPlayer: ExoPlayer,
    private val apiService: SongfyApiService,
    private val lastPlayedSongPreferences: LastPlayedSongPreferences,
    private val historyRepository: SongHistoryRepository,
    private val firebaseAuth: FirebaseAuth,
    private val playbackEventBus: PlaybackEventBus,
    private val offlineSongRepository: OfflineSongRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _currentSong = MutableStateFlow<SongDto?>(null)
    val currentSong: StateFlow<SongDto?> = _currentSong.asStateFlow()

    private val _queue = MutableStateFlow<List<SongDto>>(emptyList())
    val queue: StateFlow<List<SongDto>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _isRepeat = MutableStateFlow(false)
    val isRepeat: StateFlow<Boolean> = _isRepeat.asStateFlow()

    private var audioJob: Job? = null
    private var autoQueueJob: Job? = null
    private var genreQueueJob: Job? = null
    private var prefetchJob: Job? = null
    private var errorRetryCount = 0
    private val MAX_ERROR_RETRIES = 2
    private var lastSkipTime = 0L
    private val playbackHistory = ArrayDeque<SongDto>()

    private val streamUrlCache = ConcurrentHashMap<String, String>()
    private val artworkCache = ConcurrentHashMap<String, ByteArray>()

    init {
        // Collect PlaybackEventBus commands (Next, Prev, Stop from Notification / Lockscreen)
        scope.launch {
            playbackEventBus.events.collect { event ->
                when (event) {
                    PlaybackEvent.SKIP_NEXT -> skipNext()
                    PlaybackEvent.SKIP_PREVIOUS -> skipPrevious()
                    PlaybackEvent.STOP -> stopPlayback()
                }
            }
        }

        // Auto-advance when song ends and track playback position when paused
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    errorRetryCount = 0
                }
                if (playbackState == Player.STATE_ENDED) {
                    onSongEnded()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem == null) return
                val targetId = mediaItem.mediaId
                val q = _queue.value
                val matchedIndex = q.indexOfFirst { it.videoId == targetId }
                if (matchedIndex != -1) {
                    val nextSong = q[matchedIndex]
                    val previous = _currentSong.value
                    if (previous != null && previous.videoId != nextSong.videoId) {
                        historyRepository.markSongCompleted(previous, exoPlayer.duration.coerceAtLeast(0L))
                        playbackHistory.addLast(previous)
                        if (playbackHistory.size > 20) playbackHistory.removeFirst()
                    }
                    _currentSong.value = nextSong
                    _currentIndex.value = matchedIndex

                    val uid = firebaseAuth.currentUser?.uid ?: ""
                    scope.launch {
                        try {
                            lastPlayedSongPreferences.saveLastPlayedSong(nextSong, uid, 0L)
                        } catch (_: Exception) {}
                    }
                    historyRepository.addSongToHistory(nextSong)

                    // Safely prune finished media items asynchronously without mutating timeline during transition callback
                    scope.launch(Dispatchers.Main) {
                        try {
                            while (exoPlayer.currentMediaItemIndex > 0) {
                                exoPlayer.removeMediaItem(0)
                            }
                        } catch (_: Exception) {}
                    }

                    // Immediately trigger background prefetch for upcoming and previous songs
                    prefetchUpcoming(q, matchedIndex, _isShuffle.value)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val song = _currentSong.value ?: return
                val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                val dur = if (exoPlayer.duration > 0) exoPlayer.duration else 0L
                val uid = firebaseAuth.currentUser?.uid ?: ""
                scope.launch {
                    try {
                        lastPlayedSongPreferences.saveLastPlayedPosition(pos, uid)
                    } catch (_: Exception) {}
                }
                historyRepository.updateSongProgress(song, pos, dur)
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("PlaybackStateManager", "ExoPlayer error: ${error.errorCodeName} (${error.errorCode}) - ${error.message}", error)
                val failedSong = _currentSong.value
                if (failedSong != null) {
                    streamUrlCache.remove(failedSong.videoId)
                }
                // Avoid infinite skip loop when spamming next
                if (errorRetryCount < MAX_ERROR_RETRIES) {
                    errorRetryCount++
                    scope.launch {
                        kotlinx.coroutines.delay(500L)
                        if (exoPlayer.playerError != null) {
                            skipNext(isAutoAdvance = true)
                        }
                    }
                } else {
                    errorRetryCount = 0
                }
            }
        })
    }

    fun setShuffle(shuffle: Boolean) {
        _isShuffle.value = shuffle
        prefetchUpcoming(_queue.value, _currentIndex.value, shuffle)
    }

    fun setRepeat(repeat: Boolean) {
        _isRepeat.value = repeat
        try {
            exoPlayer.repeatMode = if (repeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        } catch (_: Exception) {}
    }

    fun startPlaybackService() {
        try {
            val serviceIntent = Intent(context, MusicPlaybackService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            Log.w("PlaybackStateManager", "Failed to start MusicPlaybackService: ")
        }
    }

    fun setSong(
        song: SongDto,
        newQueue: List<SongDto> = emptyList(),
        index: Int = -1,
        startPositionMs: Long = 0L
    ) {
        val q = if (newQueue.isNotEmpty()) newQueue else listOf(song)
        val idx = if (index >= 0) index else q.indexOfFirst { it.videoId == song.videoId }.coerceAtLeast(0)

        val previous = _currentSong.value
        if (previous != null && previous.videoId != song.videoId) {
            playbackHistory.addLast(previous)
            if (playbackHistory.size > 20) playbackHistory.removeFirst()
        }

        _currentSong.value = song
        _queue.value = q
        _currentIndex.value = idx

        val uid = firebaseAuth.currentUser?.uid ?: ""
        scope.launch {
            try {
                lastPlayedSongPreferences.saveLastPlayedSong(song, uid, startPositionMs)
                lastPlayedSongPreferences.saveLastPlayedQueue(q, uid)
            } catch (_: Exception) {}
        }
        historyRepository.addSongToHistory(song)
        if (startPositionMs > 0L) {
            historyRepository.updateSongProgress(song, startPositionMs, 0L)
        }

        startPlaybackService()

        audioJob?.cancel()
        audioJob = scope.launch {
            playAudio(song, startPositionMs)
        }
    }

    fun appendQueue(songs: List<SongDto>) {
        if (songs.isEmpty()) return
        val currentIds = _queue.value.map { it.videoId }.toSet()
        val newSongs = songs.filter { it.videoId !in currentIds }
        if (newSongs.isNotEmpty()) {
            _queue.value = _queue.value + newSongs
        }
    }

    private var lastPlayPauseTime = 0L

    fun togglePlayPause() {
        val now = System.currentTimeMillis()
        if (now - lastPlayPauseTime < 300L) return
        lastPlayPauseTime = now

        val current = _currentSong.value
        if (exoPlayer.isPlaying) {
            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            exoPlayer.pause()
            val dur = if (exoPlayer.duration > 0) exoPlayer.duration else 0L
            val uid = firebaseAuth.currentUser?.uid ?: ""
            scope.launch {
                try {
                    lastPlayedSongPreferences.saveLastPlayedPosition(pos, uid)
                } catch (_: Exception) {}
            }
            if (current != null) {
                historyRepository.updateSongProgress(current, pos, dur)
            }
        } else {
            startPlaybackService()
            if (exoPlayer.mediaItemCount > 0 && exoPlayer.playbackState != Player.STATE_IDLE && exoPlayer.playerError == null) {
                if (exoPlayer.playbackState == Player.STATE_ENDED) {
                    exoPlayer.seekTo(0L)
                }
                exoPlayer.play()
            } else if (current != null) {
                val pos = if (exoPlayer.playbackState != Player.STATE_ENDED) exoPlayer.currentPosition.coerceAtLeast(0L) else 0L
                setSong(current, _queue.value, _currentIndex.value, startPositionMs = pos)
            }
        }
    }

    fun getCachedStreamUrl(videoId: String): String? = streamUrlCache[videoId]

    fun putCachedStreamUrl(videoId: String, url: String) {
        if (streamUrlCache.size > 100) streamUrlCache.clear()
        streamUrlCache[videoId] = url
    }

    fun getCachedArtwork(videoId: String): ByteArray? = artworkCache[videoId]

    fun putCachedArtwork(videoId: String, bytes: ByteArray) {
        if (artworkCache.size > 50) artworkCache.clear()
        artworkCache[videoId] = bytes
    }

    private suspend fun playAudio(song: SongDto, startPositionMs: Long = 0L) {
        val artistName = song.artists.joinToString(", ") { it.name }.ifBlank { "Unknown Artist" }

        // Start background decode of artwork if not already cached
        val cachedArtwork = artworkCache[song.videoId]
        val artworkDeferred = if (cachedArtwork == null && !song.thumbnailUrl.isNullOrBlank()) {
            scope.async(Dispatchers.IO) {
                loadArtworkBytes(song.thumbnailUrl)?.also {
                    if (artworkCache.size > 50) artworkCache.clear()
                    artworkCache[song.videoId] = it
                }
            }
        } else null

        var streamUrl: String? = null
        // Pakai getOfflineAudioUri() yang menghasilkan content:// via FileProvider.
        // file:// URI diblokir Android 7+ untuk akses lintas proses — ExoPlayer butuh content://.
        val offlineUri = offlineSongRepository.getOfflineAudioUri(song.videoId)
        if (offlineUri != null) {
            streamUrl = offlineUri.toString()
            Log.d("PlaybackStateManager", "Playing offline: $streamUrl")
        }

        if (streamUrl.isNullOrBlank()) {
            streamUrl = streamUrlCache[song.videoId]
        }

        // Fast path: if stream URL is already in cache, start ExoPlayer immediately with zero delay!
        if (!streamUrl.isNullOrBlank()) {
            withContext(Dispatchers.Main) {
                try {
                    val metadataBuilder = MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(artistName)
                        .setArtworkUri(song.thumbnailUrl?.let { Uri.parse(it) })

                    if (cachedArtwork != null && cachedArtwork.isNotEmpty()) {
                        metadataBuilder.setArtworkData(cachedArtwork, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    }

                    val mediaItem = MediaItem.Builder()
                        .setMediaId(song.videoId)
                        .setUri(streamUrl)
                        .setMediaMetadata(metadataBuilder.build())
                        .build()

                    exoPlayer.setMediaItem(mediaItem)
                    if (startPositionMs > 0L) {
                        exoPlayer.seekTo(startPositionMs)
                    } else {
                        exoPlayer.seekTo(0L)
                    }
                    exoPlayer.prepare()
                    exoPlayer.play()
                } catch (e: Exception) {
                    Log.w("PlaybackStateManager", "Fast playAudio failed: ${e.message}")
                }
            }
            prefetchUpcoming(_queue.value, _currentIndex.value, _isShuffle.value)
            return
        }

        // Not yet cached: pause current audio so old track doesn't linger while resolving URL
        withContext(Dispatchers.Main) {
            exoPlayer.pause()
        }

        if (streamUrl.isNullOrBlank()) {
            try {
                val res = withContext(Dispatchers.IO) {
                    apiService.getStreamUrl(song.videoId)
                }
                if (res.audioUrl.isNotBlank()) {
                    streamUrl = res.audioUrl
                    if (streamUrlCache.size > 100) streamUrlCache.clear()
                    streamUrlCache[song.videoId] = streamUrl
                }
            } catch (e: Exception) {
                Log.w("PlaybackStateManager", "Direct stream url fetch failed: ${e.message}")
            }
        }

        if (streamUrl.isNullOrBlank()) {
            try {
                val query = "${song.title} $artistName".trim()
                if (query.isNotBlank()) {
                    val searchRes = withContext(Dispatchers.IO) {
                        apiService.search(query = query, filter = "songs")
                    }
                    val matched = searchRes.songs.firstOrNull()
                    if (matched != null) {
                        val res = withContext(Dispatchers.IO) {
                            apiService.getStreamUrl(matched.videoId)
                        }
                        if (res.audioUrl.isNotBlank()) {
                            streamUrl = res.audioUrl
                            if (streamUrlCache.size > 100) streamUrlCache.clear()
                            streamUrlCache[song.videoId] = streamUrl
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        if (_currentSong.value?.videoId != song.videoId) return

        if (!streamUrl.isNullOrBlank()) {
            try {
                val metadataBuilder = MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(artistName)
                    .setArtworkUri(song.thumbnailUrl?.let { Uri.parse(it) })

                if (cachedArtwork != null && cachedArtwork.isNotEmpty()) {
                    metadataBuilder.setArtworkData(cachedArtwork, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                }

                val mediaItem = MediaItem.Builder()
                    .setMediaId(song.videoId)
                    .setUri(streamUrl)
                    .setMediaMetadata(metadataBuilder.build())
                    .build()

                // Play immediately on main dispatcher
                withContext(Dispatchers.Main) {
                    exoPlayer.setMediaItem(mediaItem)
                    if (startPositionMs > 0L) {
                        exoPlayer.seekTo(startPositionMs)
                    } else {
                        exoPlayer.seekTo(0L)
                    }
                    exoPlayer.prepare()
                    exoPlayer.play()
                }

                // Immediately trigger background prefetch for upcoming songs
                prefetchUpcoming(_queue.value, _currentIndex.value, _isShuffle.value)

                // If artwork was not cached yet, attach it asynchronously once downloaded
                if (cachedArtwork == null && artworkDeferred != null) {
                    scope.launch(Dispatchers.IO) {
                        val bytes = try { artworkDeferred.await() } catch (_: Exception) { null }
                        if (bytes != null && bytes.isNotEmpty() && _currentSong.value?.videoId == song.videoId) {
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
                Log.e("PlaybackStateManager", "ExoPlayer playback failed: ", e)
            }
        }
    }

    private fun prefetchUpcoming(queue: List<SongDto>, currentIndex: Int, isShuffle: Boolean) {
        prefetchJob?.cancel()
        // Allow prefetch even for single-item queues — it still resolves URLs for prev wrap-around
        if (queue.isEmpty()) return

        val nextIndex = if (isShuffle && queue.size > 1) {
            queue.indices.filter { it != currentIndex }.randomOrNull() ?: ((currentIndex + 1) % queue.size)
        } else if (currentIndex < queue.size - 1) {
            currentIndex + 1
        } else {
            // Wrap around to first song so forward skip from last is instant
            if (queue.size > 1) 0 else -1
        }

        val prevIndex = if (currentIndex > 0) currentIndex - 1 else if (queue.size > 1) queue.size - 1 else -1

        val nextSong = if (nextIndex >= 0) queue.getOrNull(nextIndex) else null
        val prevSong = if (prevIndex >= 0) queue.getOrNull(prevIndex) else null

        prefetchJob = scope.launch(Dispatchers.IO) {
            // 1. Resolve and cache stream URL for the next song
            if (nextSong != null && streamUrlCache[nextSong.videoId] == null) {
                val nextOfflineFile = offlineSongRepository.getOfflineFile(nextSong.videoId)
                if (nextOfflineFile != null && nextOfflineFile.exists() && nextOfflineFile.length() > 1024L) {
                    streamUrlCache[nextSong.videoId] = Uri.fromFile(nextOfflineFile).toString()
                } else {
                    try {
                        val res = apiService.getStreamUrl(nextSong.videoId)
                        if (res.audioUrl.isNotBlank()) {
                            streamUrlCache[nextSong.videoId] = res.audioUrl
                            Log.d("PlaybackStateManager", "Prefetched next: ${nextSong.title}")
                        }
                    } catch (e: Exception) {
                        Log.w("PlaybackStateManager", "Prefetch next failed: ${e.message}")
                    }
                }
            }

            // 2. Resolve and cache stream URL for previous song (so Back button is instant!)
            if (prevSong != null && streamUrlCache[prevSong.videoId] == null) {
                val prevOfflineFile = offlineSongRepository.getOfflineFile(prevSong.videoId)
                if (prevOfflineFile != null && prevOfflineFile.exists() && prevOfflineFile.length() > 1024L) {
                    streamUrlCache[prevSong.videoId] = Uri.fromFile(prevOfflineFile).toString()
                } else {
                    try {
                        val res = apiService.getStreamUrl(prevSong.videoId)
                        if (res.audioUrl.isNotBlank()) {
                            streamUrlCache[prevSong.videoId] = res.audioUrl
                            Log.d("PlaybackStateManager", "Prefetched prev: ${prevSong.title}")
                        }
                    } catch (e: Exception) {
                        Log.w("PlaybackStateManager", "Prefetch prev failed: ${e.message}")
                    }
                }
            }

            // 3. Decode and cache artwork in background
            if (nextSong != null && artworkCache[nextSong.videoId] == null && !nextSong.thumbnailUrl.isNullOrBlank()) {
                loadArtworkBytes(nextSong.thumbnailUrl)?.let { bytes ->
                    if (artworkCache.size > 50) artworkCache.clear()
                    artworkCache[nextSong.videoId] = bytes
                }
            }

            // 4. Pre-queue next MediaItem into ExoPlayer so it can pre-buffer in background.
            // IMPORTANT: Only do this after the stream URL is confirmed cached to avoid a race
            // where mediaItemCount is 0 (setMediaItem not yet called by playAudio).
            // We use a small delay to yield to the main thread so playAudio's setMediaItem runs first.
            val nextUrl = nextSong?.let { streamUrlCache[it.videoId] }
            if (nextSong != null && !nextUrl.isNullOrBlank()) {
                // Small yield to ensure playAudio's setMediaItem has executed on Main before we add
                kotlinx.coroutines.delay(300L)
                withContext(Dispatchers.Main) {
                    try {
                        val artistName = nextSong.artists.joinToString(", ") { it.name }.ifBlank { "Unknown Artist" }
                        val metadata = MediaMetadata.Builder()
                            .setTitle(nextSong.title)
                            .setArtist(artistName)
                            .setArtworkUri(nextSong.thumbnailUrl?.let { Uri.parse(it) })
                            .build()
                        val nextMediaItem = MediaItem.Builder()
                            .setMediaId(nextSong.videoId)
                            .setUri(nextUrl)
                            .setMediaMetadata(metadata)
                            .build()

                        // Ensure ExoPlayer has exactly the current item at index 0; add/replace next at index 1
                        when {
                            exoPlayer.mediaItemCount > 1 -> exoPlayer.replaceMediaItem(1, nextMediaItem)
                            exoPlayer.mediaItemCount == 1 -> exoPlayer.addMediaItem(nextMediaItem)
                            // mediaItemCount == 0 means playAudio hasn't run yet — skip to avoid corrupting state
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private suspend fun loadArtworkBytes(url: String?): ByteArray? {
        if (url.isNullOrBlank()) return null
        return withContext(Dispatchers.IO) {
            try {
                val loader = Coil.imageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .size(512)
                    .build()
                val result = loader.execute(request)
                val drawable = (result as? SuccessResult)?.drawable
                if (drawable is BitmapDrawable) {
                    val stream = ByteArrayOutputStream()
                    drawable.bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                    stream.toByteArray()
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }

    fun skipNext(isAutoAdvance: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!isAutoAdvance && now - lastSkipTime < 400L) return
        lastSkipTime = now
        val q = _queue.value
        if (q.isEmpty()) return
        val current = _currentSong.value
        if (current != null) {
            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            val dur = if (exoPlayer.duration > 0) exoPlayer.duration else 0L
            historyRepository.updateSongProgress(current, pos, dur)
        }
        val safeIndex = _currentIndex.value.coerceIn(0, q.size - 1)
        val isLastInQueue = safeIndex >= q.size - 1

        val nextIndex = if (_isShuffle.value && q.size > 1) {
            q.indices.filter { it != safeIndex }.randomOrNull() ?: ((safeIndex + 1) % q.size)
        } else if (isLastInQueue) {
            0
        } else {
            safeIndex + 1
        }
        val nextSong = q.getOrNull(nextIndex) ?: return

        // Fast path: if ExoPlayer already pre-buffered the next track AND the stream URL is cached
        // Only use ExoPlayer's internal seek if we have more than 1 item AND the cached stream URL
        // matches — this avoids the race where mediaItemCount > 1 but the pre-buffered item is stale.
        val cachedNextUrl = streamUrlCache[nextSong.videoId]
        if (!cachedNextUrl.isNullOrBlank() && exoPlayer.mediaItemCount > 1 && exoPlayer.hasNextMediaItem()) {
            val nextMediaItem = exoPlayer.getMediaItemAt(exoPlayer.nextMediaItemIndex)
            if (nextMediaItem.mediaId == nextSong.videoId) {
                _currentSong.value = nextSong
                _currentIndex.value = nextIndex
                exoPlayer.seekToNextMediaItem()
                exoPlayer.play()
                prefetchUpcoming(q, nextIndex, _isShuffle.value)
                return
            }
        }

        // Standard path: setSong will use streamUrlCache for instant playback if already prefetched
        setSong(nextSong, q, nextIndex)
    }

    fun skipPrevious() {
        val now = System.currentTimeMillis()
        if (now - lastSkipTime < 400L) return
        lastSkipTime = now
        val q = _queue.value
        if (q.isEmpty()) return
        if (exoPlayer.currentPosition > 3000L) {
            exoPlayer.seekTo(0L)
            return
        }
        val current = _currentSong.value
        if (current != null) {
            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            val dur = if (exoPlayer.duration > 0) exoPlayer.duration else 0L
            historyRepository.updateSongProgress(current, pos, dur)
        }
        val safeIndex = _currentIndex.value.coerceIn(0, q.size - 1)
        if (safeIndex > 0) {
            setSong(q[safeIndex - 1], q, safeIndex - 1)
        } else if (playbackHistory.isNotEmpty()) {
            val prevSong = playbackHistory.removeLast()
            val newQueue = listOf(prevSong) + q.filter { it.videoId != prevSong.videoId }
            setSong(prevSong, newQueue, 0)
        } else {
            val prevIndex = q.size - 1
            setSong(q[prevIndex], q, prevIndex)
        }
    }

    private fun onSongEnded() {
        val finishedSong = _currentSong.value ?: return
        historyRepository.markSongCompleted(finishedSong, exoPlayer.duration.coerceAtLeast(0L))

        if (_isRepeat.value) {
            exoPlayer.seekTo(0L)
            exoPlayer.play()
            return
        }
        skipNext(isAutoAdvance = true)
    }

    /**
     * Spotify-style genre radio algorithm.
     *
     * Unlike searching by artist name (which yields same-artist songs), this function
     * derives GENRE / MOOD signals from the current song and searches for similar music
     * from DIFFERENT artists — exactly like Spotify Radio / DJ.
     *
     * Strategy:
     * 1. Extract genre/mood keywords from the song title (strip featured artists, brackets, etc.)
     * 2. Build 3 independent queries:
     *    a) Title-keyword "mix" — e.g. "Levitating mix" → yields same vibe, different artists
     *    b) Mood discovery query — randomly chosen from a pool of mood/genre terms
     *    c) Genre discovery query — randomly chosen global trending/genre terms
     * 3. Run all 3 in parallel on IO dispatcher
     * 4. Merge, deduplicate, and enforce artist diversity:
     *    → Each artist may appear AT MOST ONCE in the new batch
     *    → The current/finished artist is entirely excluded from the first 8 slots
     * 5. Shuffle the diverse pool, then append to the current queue.
     */
    private fun ensureUpcomingGenreQueue(baseSong: SongDto, playImmediatelyIfEnded: Boolean = false) {
        val currentQ = _queue.value
        val curIdx = _currentIndex.value
        val remaining = (currentQ.size - 1 - curIdx).coerceAtLeast(0)
        // If we already have 4 or more upcoming songs, avoid duplicate network calls unless immediate play is needed
        if (remaining >= 4 && !playImmediatelyIfEnded) return

        genreQueueJob?.cancel()
        genreQueueJob = scope.launch(Dispatchers.IO) {
            try {
                val baseArtist = baseSong.artists.firstOrNull()?.name?.trim() ?: ""

                // ── Step 1: Extract clean title keywords (strip [feat], (official), etc.) ──
                val cleanTitle = baseSong.title
                    .replace(Regex("(?i)\\(.*?official.*?\\)|\\[.*?official.*?\\]"), "")
                    .replace(Regex("(?i)\\(.*?feat\\.?.*?\\)|\\[.*?feat\\.?.*?\\]"), "")
                    .replace(Regex("(?i)\\(.*?ft\\.?.*?\\)|\\[.*?ft\\.?.*?\\]"), "")
                    .replace(Regex("(?i)\\s*-\\s*(official|audio|video|lyrics?|hd|4k).*"), "")
                    .replace(Regex("[^\\w\\s]"), "")
                    .trim()

                val titleKeywords = cleanTitle
                    .split(" ")
                    .filter { it.length > 3 && it.lowercase() !in setOf("with", "from", "live", "remix", "version") }
                    .take(2)
                    .joinToString(" ")
                    .ifBlank { cleanTitle.take(20) }

                // ── Step 2: Build 3 diverse genre-signal queries ───────────────────────────
                // Query A: title-based genre radio ("Levitating mix", "Blinding Lights type beat")
                val titleQuery = "$titleKeywords mix"

                // Query B: mood/vibe pool (rotated randomly for variety)
                val moodPool = listOf(
                    "top pop songs", "chill vibes music", "feel good hits", "upbeat songs",
                    "sad songs playlist", "love songs acoustic", "energetic workout songs",
                    "indie pop hits", "alternative rock hits", "rnb songs 2024",
                    "lagu viral indonesia", "lagu hits terbaru", "musik pop indonesia",
                    "lagu romantis indonesia", "lagu galau indonesia", "pop barat hits"
                )
                val moodQuery = moodPool[kotlin.random.Random.nextInt(moodPool.size)]

                // Query C: genre/era discovery (rotated randomly)
                val genrePool = listOf(
                    "top 50 songs", "viral music 2024", "best songs 2024", "new music friday",
                    "song of the year", "radio hits", "most played songs", "trending songs",
                    "music chart hits", "popular songs this week", "hits terbaru 2024",
                    "top charts indonesia", "spotify top hits", "youtube music trending"
                )
                val genreQuery = genrePool[kotlin.random.Random.nextInt(genrePool.size)]

                // ── Step 3: Fetch all 3 queries in parallel ────────────────────────────────
                val (resA, resB, resC) = run {
                    val dA = async {
                        try { apiService.search(query = titleQuery, filter = "songs").songs } catch (_: Exception) { emptyList() }
                    }
                    val dB = async {
                        try { apiService.search(query = moodQuery, filter = "songs").songs } catch (_: Exception) { emptyList() }
                    }
                    val dC = async {
                        try { apiService.search(query = genreQuery, filter = "songs").songs } catch (_: Exception) { emptyList() }
                    }
                    Triple(dA.await(), dB.await(), dC.await())
                }

                withContext(Dispatchers.Main) {
                    val latestQueue = _queue.value
                    val existingIds = latestQueue.map { it.videoId }.toSet()

                    // ── Step 4: Merge + deduplicate + artist diversity filter ──────────────
                    val seen = mutableSetOf<String>()
                    val merged = (resA + resB + resC).filter { song ->
                        val isNew = seen.add(song.videoId)
                        isNew && song.videoId !in existingIds && song.videoId != baseSong.videoId
                    }

                    // Enforce: each artist appears at most once; base artist excluded from first 8
                    val artistSeen = mutableSetOf<String>()
                    val diverse = mutableListOf<SongDto>()
                    val overflow = mutableListOf<SongDto>() // base artist songs as last-resort padding

                    for (song in merged.shuffled()) {
                        val songArtist = song.artists.firstOrNull()?.name?.trim()?.lowercase() ?: ""
                        val isSameAsBase = baseArtist.isNotBlank() &&
                            songArtist.isNotBlank() &&
                            songArtist == baseArtist.lowercase()

                        if (isSameAsBase) {
                            overflow.add(song) // defer same-artist songs to end
                        } else if (artistSeen.add(songArtist)) {
                            diverse.add(song) // only one song per artist
                        }
                        if (diverse.size >= 10) break
                    }

                    // Pad with deferred same-artist songs only if we don't have enough
                    val finalBatch = if (diverse.size < 5) {
                        (diverse + overflow.take(4 - diverse.size)).take(10)
                    } else {
                        diverse.take(10)
                    }

                    if (finalBatch.isNotEmpty()) {
                        val updatedQueue = latestQueue + finalBatch
                        _queue.value = updatedQueue
                        Log.d("PlaybackStateManager", "Added ${finalBatch.size} genre-diverse songs after '${baseSong.title}' (${finalBatch.map { it.artists.firstOrNull()?.name }})")

                        if (playImmediatelyIfEnded && (exoPlayer.playbackState == Player.STATE_ENDED || !exoPlayer.isPlaying)) {
                            val nextSong = finalBatch.first()
                            val nextIdx = updatedQueue.indexOfFirst { it.videoId == nextSong.videoId }
                            setSong(nextSong, updatedQueue, nextIdx)
                        } else {
                            prefetchUpcoming(updatedQueue, _currentIndex.value, _isShuffle.value)
                        }
                    } else if (playImmediatelyIfEnded) {
                        val q = _queue.value
                        if (q.isNotEmpty()) setSong(q.first(), q, 0)
                    }
                }
            } catch (e: Exception) {
                Log.w("PlaybackStateManager", "ensureUpcomingGenreQueue failed: ${e.message}")
                if (playImmediatelyIfEnded) {
                    withContext(Dispatchers.Main) {
                        val q = _queue.value
                        if (q.isNotEmpty()) setSong(q.first(), q, 0)
                    }
                }
            }
        }
    }



    fun stopPlayback() {
        audioJob?.cancel()
        autoQueueJob?.cancel()
        genreQueueJob?.cancel()
        val song = _currentSong.value
        if (song != null) {
            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            val dur = if (exoPlayer.duration > 0) exoPlayer.duration else 0L
            val uid = firebaseAuth.currentUser?.uid ?: ""
            scope.launch {
                try {
                    lastPlayedSongPreferences.saveLastPlayedPosition(pos, uid)
                } catch (_: Exception) {}
            }
            historyRepository.updateSongProgress(song, pos, dur)
        }
        try {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        } catch (_: Exception) {}
        _currentSong.value = null
    }

    /**
     * Membersihkan seluruh antrian, pemutaran, cache, dan menghentikan foreground service saat logout.
     */
    fun resetOnLogout() {
        audioJob?.cancel()
        autoQueueJob?.cancel()
        genreQueueJob?.cancel()
        prefetchJob?.cancel()
        try {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        } catch (_: Exception) {}
        _currentSong.value = null
        _queue.value = emptyList()
        _currentIndex.value = 0
        playbackHistory.clear()
        streamUrlCache.clear()
        artworkCache.clear()
        try {
            val serviceIntent = Intent(context, MusicPlaybackService::class.java)
            context.stopService(serviceIntent)
        } catch (_: Exception) {}
    }
}
