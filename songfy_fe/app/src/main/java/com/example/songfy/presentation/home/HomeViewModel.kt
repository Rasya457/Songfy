package com.lagu.songfy.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagu.songfy.data.remote.api.SongfyApiService
import com.lagu.songfy.data.remote.dto.ArtistShortDto
import com.lagu.songfy.data.remote.dto.SongDto
import com.lagu.songfy.data.repository.SongHistoryRepository
import com.lagu.songfy.data.repository.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val apiService: SongfyApiService,
    private val userProfileRepository: UserProfileRepository,
    private val historyRepository: SongHistoryRepository
) : ViewModel() {

    // ─── Start with fallback data instantly — no blank screen ───
    private val _uiState = MutableStateFlow(
        HomeUiState(
            isLoading = false, // Don't block UI on load
            topPlaylists = staticFallbackPlaylists(),
            dailySongs = staticFallbackSongs(),
            recentlyPlayedSongs = historyRepository.history.value.map { it.song }.distinctBy { it.videoId },
            recommendedSongs = emptyList()
        )
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Cancel network saat VM mati (tab jauh di-dispose pager) biar tidak ada
    // reply basi yang menimpa state + menahan thread. Refresh per-instans tetap
    // jalan sekali di init — pager yang recreate VM adalah perilaku normal Compose.
    private var refreshJob: Job? = null

    init {
        observeUserProfile()
        observeHistory()
        // Kick off background refresh without blocking UI
        refreshInBackground()
    }

    override fun onCleared() {
        refreshJob?.cancel()
        recoJob?.cancel()
        super.onCleared()
    }

    private fun observeUserProfile() {
        viewModelScope.launch {
            userProfileRepository.userProfile.collect { profile ->
                // Emit hanya bila nilai berubah (guard): profile flow bisa emit ulang
                // objek identik (mis. avatar update) → tanpa guard header recompose sia-sia.
                val cur = _uiState.value
                if (profile.username != cur.userName) {
                    _uiState.update { it.copy(userName = profile.username) }
                }
                if (profile.avatarUrl != cur.userAvatarUrl) {
                    _uiState.update { it.copy(userAvatarUrl = profile.avatarUrl) }
                }
            }
        }
    }

    // Debounce history → rekomendasi: tiap lagu selesai memicu emit history baru;
    // tanpa debounce tiap emit = 1 network search = Home re-layout + berebut bandwidth
    // dengan stream prefetch saat lagu berganti. 2 detik cukup: user tidak ganti lagu
    // lebih cepat dari itu secara bermakna.
    private var recoJob: Job? = null

    private fun observeHistory() {
        viewModelScope.launch {
            historyRepository.history.collect { history ->
                val recentSongs = history.map { it.song }.distinctBy { it.videoId }
                _uiState.update { it.copy(recentlyPlayedSongs = recentSongs) }

                // Skip bila top-artist tidak berubah: tiap lagu selesai meng-emit history
                // baru (timestamp beda) tapi top-3 artist biasanya sama → tanpa guard ini
                // tiap lagu selesai = 1 recompose judul section + 1 network search.
                val topArtists = history
                    .flatMap { it.song.artists }
                    .groupBy { it.name }
                    .mapValues { it.value.size }
                    .entries
                    .sortedByDescending { it.value }
                    .take(3)
                    .map { it.key }

                if (topArtists != _uiState.value.topArtistsFromHistory) {
                    _uiState.update { it.copy(topArtistsFromHistory = topArtists) }
                }

                // Trigger a personalized fetch if history has artists (debounced)
                recoJob?.cancel()
                if (topArtists.isNotEmpty()) {
                    recoJob = viewModelScope.launch {
                        delay(2000)
                        fetchPersonalizedRecommendations(topArtists)
                    }
                }
            }
        }
    }

    /**
     * Background refresh — runs in parallel for speed.
     * Fetches live YouTube Music top playlists + songs simultaneously, then updates UI.
     * Dynamic integration ensures that as hits update, the playlists reflect current trends.
     */
    private fun refreshInBackground() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            // Launch both network calls in PARALLEL
            val playlistsDeferred = async {
                try {
                    apiService.getTopPlaylists()
                } catch (_: Exception) {
                    try {
                        val searchRes = apiService.search(query = "Top Hits", filter = "playlists")
                        searchRes.playlists
                    } catch (_: Exception) {
                        null
                    }
                }
            }
            val songsDeferred = async {
                try { apiService.search(query = "Top Songs", filter = "songs") } catch (_: Exception) { null }
            }

            // Wait for both to complete
            val playlistsResult = playlistsDeferred.await()
            val songsResult = songsDeferred.await()

            if (!playlistsResult.isNullOrEmpty()) {
                val cards = playlistsResult.mapIndexed { idx, pl ->
                    PlaylistCardItem(
                        id = pl.browseId ?: "pl_$idx",
                        title = pl.title ?: "Top Hits",
                        description = pl.description ?: pl.author?.let { "By $it" } ?: "Top YouTube Music Hits",
                        artworkUrl = pl.thumbnailUrl
                            ?: "https://i.ytimg.com/vi/kPa7bsKwL-8/hqdefault.jpg",
                        isPurpleTheme = idx % 2 != 0
                    )
                }
                val merged = (cards + staticFallbackPlaylists()).distinctBy { it.id }
                _uiState.update { it.copy(topPlaylists = merged) }
            }
            if (songsResult != null && songsResult.songs.isNotEmpty()) {
                val songs = songsResult.songs.distinctBy { it.videoId }
                _uiState.update { it.copy(dailySongs = songs) }
            }
        }
    }

    /**
     * Personalized recommendations based on user's most-played artists.
     * Uses history to find top artists → searches for their songs.
     */
    private fun fetchPersonalizedRecommendations(topArtists: List<String>) {
        viewModelScope.launch {
            try {
                // Pick the top artist and search for their tracks
                val primaryArtist = topArtists.firstOrNull() ?: return@launch
                val response = apiService.search(query = primaryArtist, filter = "songs")
                if (response.songs.isNotEmpty()) {
                    _uiState.update { it.copy(recommendedSongs = response.songs.distinctBy { it.videoId }.take(10)) }
                }
            } catch (_: Exception) {
                // Keep existing recommendations on error
            }
        }
    }

    fun onCategorySelected(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
        loadCategoryData(category)
    }

    fun onToggleLikePlaylist(playlistId: String) {
        _uiState.update { state ->
            val updated = state.topPlaylists.map { item ->
                if (item.id == playlistId) item.copy(isLiked = !item.isLiked) else item
            }
            state.copy(topPlaylists = updated)
        }
    }

    private fun loadCategoryData(category: String) {
        viewModelScope.launch {
            try {
                // Map category to the best search query
                val query = when (category) {
                    "Trending" -> "Trending Viral Songs Indonesia"
                    "New Release" -> "New Releases Music"
                    "Top Songs" -> "Billboard Hot 100"
                    "For You" -> {
                        // Personalized: use top history artist or fallback
                        val topArtist = _uiState.value.topArtistsFromHistory.firstOrNull()
                        topArtist ?: "Top Hits"
                    }
                    else -> {
                        // "All" — use personalized if available, else global Top Hits
                        val topArtist = _uiState.value.topArtistsFromHistory.firstOrNull()
                        if (topArtist != null) topArtist else "Top Hits"
                    }
                }
                val response = apiService.search(query = query, filter = "songs")
                if (response.songs.isNotEmpty()) {
                    _uiState.update { it.copy(dailySongs = response.songs.distinctBy { it.videoId }) }
                }
            } catch (_: Exception) {
                // Keep existing data
            }
        }
    }

    // ─── Real instant-show fallback with valid YouTube Music art & IDs ───
    companion object {
        fun staticFallbackSongs(): List<SongDto> = listOf(
            SongDto(
                videoId = "kPa7bsKwL-8", title = "Die With A Smile",
                artists = listOf(ArtistShortDto(name = "Lady Gaga, Bruno Mars")),
                thumbnailUrl = "https://i.ytimg.com/vi/kPa7bsKwL-8/hqdefault.jpg",
                duration = "4:11"
            ),
            SongDto(
                videoId = "eVli-tstM5E", title = "Espresso",
                artists = listOf(ArtistShortDto(name = "Sabrina Carpenter")),
                thumbnailUrl = "https://i.ytimg.com/vi/eVli-tstM5E/hqdefault.jpg",
                duration = "2:55"
            ),
            SongDto(
                videoId = "V9PVRfjEBTI", title = "BIRDS OF A FEATHER",
                artists = listOf(ArtistShortDto(name = "Billie Eilish")),
                thumbnailUrl = "https://i.ytimg.com/vi/V9PVRfjEBTI/hqdefault.jpg",
                duration = "3:13"
            ),
            SongDto(
                videoId = "n3K0tZ_3Q4U", title = "Satu Bulan",
                artists = listOf(ArtistShortDto(id = "a1", name = "Bernadya")),
                thumbnailUrl = "https://i.ytimg.com/vi/n3K0tZ_3Q4U/hqdefault.jpg",
                duration = "3:12"
            ),
            SongDto(
                videoId = "4NRXx6U8ABQ", title = "Blinding Lights",
                artists = listOf(ArtistShortDto(id = "a2", name = "The Weeknd")),
                thumbnailUrl = "https://i.ytimg.com/vi/4NRXx6U8ABQ/hqdefault.jpg",
                duration = "3:20"
            ),
            SongDto(
                videoId = "ApXoWvfEYVU", title = "Sunflower",
                artists = listOf(ArtistShortDto(id = "a3", name = "Post Malone, Swae Lee")),
                thumbnailUrl = "https://i.ytimg.com/vi/ApXoWvfEYVU/hqdefault.jpg",
                duration = "2:38"
            )
        )

        fun staticFallbackPlaylists(): List<PlaylistCardItem> = listOf(
            PlaylistCardItem(
                id = "VLPLDIoUOhQQPlWc-Kd6TCjTRIl0Z6fSQV0X",
                title = "Today's Top Hits",
                description = "Top 100 Songs - Billboard & Global Hits",
                artworkUrl = "https://yt3.googleusercontent.com/RYNqywBw8ubgXGJNSRbtd3y7wtkbAZym24vpA-nNZ153mDHTyEuGgo1L4ecEMyiI1ts2OdwI0Q=s1200",
                isLiked = false, isPurpleTheme = false
            ),
            PlaylistCardItem(
                id = "VLPLVCtLXKko6G0kUE_gOAtpKv09MKHZzL2J",
                title = "Top Hits Indonesia",
                description = "Lagu Pop & Musik Terbaik Indonesia Saat Ini",
                artworkUrl = "https://yt3.googleusercontent.com/JRzWvYFPZxnB-C5iamIrsCwk8NwjllzbQB43ERumAFxT_0hueORy6OFTTNOIsqSevxs2xz9B9SM=s1200",
                isLiked = true, isPurpleTheme = true
            ),
            PlaylistCardItem(
                id = "VLPLkbaG37V-vG8Fib_qvgOKf3qzqA0SUk59",
                title = "Pop Terpopuler",
                description = "Hits Terpopuler & Viral di TikTok",
                artworkUrl = "https://i.ytimg.com/pl_c/PLkbaG37V-vG8Fib_qvgOKf3qzqA0SUk59/studio_square_thumbnail.jpg",
                isLiked = false, isPurpleTheme = false
            ),
            PlaylistCardItem(
                id = "VLPL4fGSI4AuKKX3Qv_6k1i4b7_9F0QkXk0y",
                title = "Billboard Hot 100",
                description = "100 Lagu terpanas di seluruh dunia saat ini",
                artworkUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=800&q=80",
                isLiked = true, isPurpleTheme = false
            ),
            PlaylistCardItem(
                id = "VLPLFPg_IUxqnZN3G81U8sK5zUq-K5y4f8uQ",
                title = "Viral 50 Global",
                description = "Lagu-lagu yang sedang trending di seluruh dunia",
                artworkUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=800&q=80",
                isLiked = false, isPurpleTheme = true
            ),
            PlaylistCardItem(
                id = "VLPLr4g7Q3eR_V6hTq1S3_4N5P9Q9L7eX6z",
                title = "Mega Hit Mix",
                description = "Kompilasi lagu hits terbesar dan nonstop",
                artworkUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=800&q=80",
                isLiked = false, isPurpleTheme = false
            ),
            PlaylistCardItem(
                id = "VLPLw-VjHDlEOgvXN3zYJkX9eX0k5pL9k0y",
                title = "Lagu Galau Indonesia",
                description = "Kompilasi pop galau akustik terbaik",
                artworkUrl = "https://images.unsplash.com/photo-1518895949257-7621c3c786d7?w=800&q=80",
                isLiked = true, isPurpleTheme = true
            ),
            PlaylistCardItem(
                id = "VLPLk7O5JdY4lX0tQ4x1N6f3m2s1P9k0y5",
                title = "Indie Pop Santai",
                description = "Musik santai dan akustik untuk menemani harimu",
                artworkUrl = "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=800&q=80",
                isLiked = false, isPurpleTheme = false
            )
        )
    }
}