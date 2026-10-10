package com.lagu.songfy.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagu.songfy.data.local.datastore.SearchHistoryPreferences
import com.lagu.songfy.data.remote.api.SongfyApiService
import com.lagu.songfy.data.remote.dto.ArtistShortDto
import com.lagu.songfy.data.remote.dto.SongDto
import com.lagu.songfy.data.repository.SongHistoryRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val apiService: SongfyApiService,
    private val historyRepository: SongHistoryRepository,
    private val searchHistoryPreferences: SearchHistoryPreferences,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SearchUiState(
            viralHits = getInitialViralHits(),
            trendingNow = getInitialTrendingNow(),
            recentReleases = getInitialRecentReleases()
        )
    )
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private val cachedSongs = mutableListOf<SongDto>()

    init {
        observeRecentSearches()
        observePlaybackHistory()
        fetchDiscoveryContent()
    }

    private fun fetchDiscoveryContent() {
        viewModelScope.launch {
            try {
                val viralRes = apiService.search(query = "Today's Top Hits", filter = "songs")
                if (viralRes.songs.isNotEmpty()) {
                    val songs = viralRes.songs
                        .filterNot { it.title.contains("teh hijau", ignoreCase = true) || it.title.contains("bad girl", ignoreCase = true) }
                        .distinctBy { it.videoId }
                    _uiState.update { it.copy(viralHits = songs) }
                }
            } catch (_: Exception) {}

            delay(400)
            if (_uiState.value.isSearchActive) return@launch

            try {
                val trendingRes = apiService.search(query = "Billboard Hot 100", filter = "songs")
                if (trendingRes.songs.isNotEmpty()) {
                    val songs = trendingRes.songs
                        .filterNot { it.title.contains("teh hijau", ignoreCase = true) || it.title.contains("bad girl", ignoreCase = true) }
                        .distinctBy { it.videoId }
                    _uiState.update { it.copy(trendingNow = songs) }
                }
            } catch (_: Exception) {}

            delay(400)
            if (_uiState.value.isSearchActive) return@launch

            try {
                val releasesRes = apiService.search(query = "New Release Hits", filter = "songs")
                if (releasesRes.songs.isNotEmpty()) {
                    val songs = releasesRes.songs
                        .filterNot { it.title.contains("teh hijau", ignoreCase = true) || it.title.contains("bad girl", ignoreCase = true) }
                        .distinctBy { it.videoId }
                    _uiState.update { it.copy(recentReleases = songs) }
                }
            } catch (_: Exception) {}
        }
    }

    private val currentUid: String
        get() = firebaseAuth.currentUser?.uid ?: ""

    private fun observeRecentSearches() {
        viewModelScope.launch {
            val uidFlow = callbackFlow {
                trySend(firebaseAuth.currentUser?.uid ?: "")
                val listener = FirebaseAuth.AuthStateListener { auth ->
                    trySend(auth.currentUser?.uid ?: "")
                }
                firebaseAuth.addAuthStateListener(listener)
                awaitClose { firebaseAuth.removeAuthStateListener(listener) }
            }.distinctUntilChanged()

            uidFlow.flatMapLatest { uid ->
                searchHistoryPreferences.getRecentSearches(uid)
            }.collect { recents ->
                _uiState.update { it.copy(recentSearches = recents) }
            }
        }
    }

    private fun observePlaybackHistory() {
        viewModelScope.launch {
            val initialRecents = searchHistoryPreferences.getRecentSearches(currentUid).first()
            if (initialRecents.isEmpty()) {
                val historyList = historyRepository.history.first()
                if (historyList.isNotEmpty()) {
                    val songs = historyList.map { it.song }.distinctBy { it.videoId }
                    songs.take(10).reversed().forEach { searchHistoryPreferences.addSearch(it, currentUid) }
                }
            }
        }
    }

    fun setSearchActive(active: Boolean) {
        _uiState.update {
            it.copy(
                isSearchActive = active,
                query = if (!active) "" else it.query,
                searchResults = if (!active) emptyList() else it.searchResults,
                artistResults = if (!active) emptyList() else it.artistResults,
                albumResults = if (!active) emptyList() else it.albumResults,
                error = null
            )
        }
    }

    fun dismissBanner() {
        _uiState.update { it.copy(showBanner = false) }
    }

    fun onQueryChanged(newQuery: String) {
        _uiState.update { it.copy(query = newQuery, isSearchActive = true) }
        searchJob?.cancel()

        if (newQuery.isBlank()) {
            _uiState.update {
                it.copy(
                    isSearching = false,
                    searchResults = emptyList(),
                    artistResults = emptyList(),
                    albumResults = emptyList(),
                    error = null
                )
            }
            return
        }

        val trimmed = newQuery.trim()

        // Instant matching from local memory pool + recent searches
        val instantMatches = (_uiState.value.recentSearches + cachedSongs).filter { song ->
            song.title.contains(trimmed, ignoreCase = true) ||
            song.artists.any { it.name.contains(trimmed, ignoreCase = true) } ||
            song.album?.contains(trimmed, ignoreCase = true) == true
        }.distinctBy { it.videoId }

        _uiState.update {
            it.copy(
                isSearching = true,
                searchResults = instantMatches,
                error = null
            )
        }

        searchJob = viewModelScope.launch {
            delay(200)
            executeSearch(trimmed)
        }
    }

    fun onSearchTriggered(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            executeSearch(query)
        }
    }

    private suspend fun executeSearch(query: String) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) return

        _uiState.update { it.copy(isSearching = true, error = null) }
        try {
            val filterParam = when (_uiState.value.selectedFilter) {
                "Artis" -> "artists"
                "Album" -> "albums"
                else -> "songs"
            }
            val response = apiService.search(query = trimmedQuery, filter = filterParam)
            response.songs.forEach { s ->
                if (cachedSongs.none { it.videoId == s.videoId }) {
                    if (cachedSongs.size >= 150) cachedSongs.removeAt(0)
                    cachedSongs.add(s)
                }
            }
            val songs = response.songs.distinctBy { it.videoId }
            val artists = response.artists.distinctBy { it.artistId ?: it.name }
            val albums = response.albums.distinctBy { it.browseId ?: it.title }
            _uiState.update {
                it.copy(
                    isSearching = false,
                    searchResults = songs,
                    artistResults = artists,
                    albumResults = albums,
                    error = null
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isSearching = false,
                    error = if (it.searchResults.isEmpty() && it.artistResults.isEmpty()) (e.localizedMessage ?: "Gagal mencari musik") else null
                )
            }
        }
    }

    fun setFilter(filter: String) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    fun onSongSelected(song: SongDto) {
        viewModelScope.launch {
            searchHistoryPreferences.addSearch(song, currentUid)
        }
    }

    fun removeRecentSearch(videoId: String) {
        viewModelScope.launch {
            searchHistoryPreferences.removeSearch(videoId, currentUid)
        }
    }

    fun setClearConfirmDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showClearConfirmDialog = visible) }
    }

    fun clearAllRecentSearches() {
        viewModelScope.launch {
            searchHistoryPreferences.clearSearches(currentUid)
            _uiState.update {
                it.copy(
                    showClearConfirmDialog = false,
                    recentSearches = emptyList()
                )
            }
        }
    }

    fun showSnackbar(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    companion object {
        fun getInitialViralHits(): List<SongDto> = listOf(
            SongDto(
                videoId = "kPa7bsKwL-8",
                title = "Die With A Smile",
                artists = listOf(ArtistShortDto(name = "Lady Gaga, Bruno Mars")),
                thumbnailUrl = "https://i.ytimg.com/vi/kPa7bsKwL-8/hqdefault.jpg",
                duration = "4:11"
            ),
            SongDto(
                videoId = "eVli-tstM5E",
                title = "Espresso",
                artists = listOf(ArtistShortDto(name = "Sabrina Carpenter")),
                thumbnailUrl = "https://i.ytimg.com/vi/eVli-tstM5E/hqdefault.jpg",
                duration = "2:55"
            ),
            SongDto(
                videoId = "V9PVRfjEBTI",
                title = "BIRDS OF A FEATHER",
                artists = listOf(ArtistShortDto(name = "Billie Eilish")),
                thumbnailUrl = "https://i.ytimg.com/vi/V9PVRfjEBTI/hqdefault.jpg",
                duration = "3:13"
            ),
            SongDto(
                videoId = "n3K0tZ_3Q4U",
                title = "Satu Bulan",
                artists = listOf(ArtistShortDto(name = "Bernadya")),
                thumbnailUrl = "https://i.ytimg.com/vi/n3K0tZ_3Q4U/hqdefault.jpg",
                duration = "3:12"
            ),
            SongDto(
                videoId = "T8q1H0H_r9Y",
                title = "Taste",
                artists = listOf(ArtistShortDto(name = "Sabrina Carpenter")),
                thumbnailUrl = "https://i.ytimg.com/vi/T8q1H0H_r9Y/hqdefault.jpg",
                duration = "2:37"
            ),
            SongDto(
                videoId = "H58vbez_m4E",
                title = "Not Like Us",
                artists = listOf(ArtistShortDto(name = "Kendrick Lamar")),
                thumbnailUrl = "https://i.ytimg.com/vi/H58vbez_m4E/hqdefault.jpg",
                duration = "4:34"
            ),
            SongDto(
                videoId = "ApXoWvfEYVU",
                title = "Sunflower",
                artists = listOf(ArtistShortDto(name = "Post Malone, Swae Lee")),
                thumbnailUrl = "https://i.ytimg.com/vi/ApXoWvfEYVU/hqdefault.jpg",
                duration = "2:38"
            ),
            SongDto(
                videoId = "4NRXx6U8ABQ",
                title = "Blinding Lights",
                artists = listOf(ArtistShortDto(name = "The Weeknd")),
                thumbnailUrl = "https://i.ytimg.com/vi/4NRXx6U8ABQ/hqdefault.jpg",
                duration = "3:20"
            )
        )

        fun getInitialTrendingNow(): List<SongDto> = listOf(
            SongDto(
                videoId = "1RKqOmSkGgM",
                title = "Good Luck, Babe!",
                artists = listOf(ArtistShortDto(name = "Chappell Roan")),
                thumbnailUrl = "https://i.ytimg.com/vi/1RKqOmSkGgM/hqdefault.jpg",
                duration = "3:38"
            ),
            SongDto(
                videoId = "Oa_RSwwpPaA",
                title = "Beautiful Things",
                artists = listOf(ArtistShortDto(name = "Benson Boone")),
                thumbnailUrl = "https://i.ytimg.com/vi/Oa_RSwwpPaA/hqdefault.jpg",
                duration = "3:00"
            ),
            SongDto(
                videoId = "MLlSSJ0z7xM",
                title = "Dancing in the Flames",
                artists = listOf(ArtistShortDto(name = "The Weeknd")),
                thumbnailUrl = "https://i.ytimg.com/vi/MLlSSJ0z7xM/hqdefault.jpg",
                duration = "3:40"
            ),
            SongDto(
                videoId = "kPa7bsKwL-8",
                title = "Die With A Smile",
                artists = listOf(ArtistShortDto(name = "Lady Gaga, Bruno Mars")),
                thumbnailUrl = "https://i.ytimg.com/vi/kPa7bsKwL-8/hqdefault.jpg",
                duration = "4:11"
            )
        )

        fun getInitialRecentReleases(): List<SongDto> = listOf(
            SongDto(
                videoId = "H58vbez_m4E",
                title = "Not Like Us",
                artists = listOf(ArtistShortDto(name = "Kendrick Lamar")),
                thumbnailUrl = "https://i.ytimg.com/vi/H58vbez_m4E/hqdefault.jpg",
                duration = "4:34"
            ),
            SongDto(
                videoId = "MLlSSJ0z7xM",
                title = "Dancing in the Flames",
                artists = listOf(ArtistShortDto(name = "The Weeknd")),
                thumbnailUrl = "https://i.ytimg.com/vi/MLlSSJ0z7xM/hqdefault.jpg",
                duration = "3:40"
            ),
            SongDto(
                videoId = "T8q1H0H_r9Y",
                title = "Taste",
                artists = listOf(ArtistShortDto(name = "Sabrina Carpenter")),
                thumbnailUrl = "https://i.ytimg.com/vi/T8q1H0H_r9Y/hqdefault.jpg",
                duration = "2:37"
            ),
            SongDto(
                videoId = "q3zKK450xJS",
                title = "Fortnight",
                artists = listOf(ArtistShortDto(name = "Taylor Swift")),
                thumbnailUrl = "https://i.ytimg.com/vi/q3zKK450xJS/hqdefault.jpg",
                duration = "3:48"
            )
        )
    }
}
