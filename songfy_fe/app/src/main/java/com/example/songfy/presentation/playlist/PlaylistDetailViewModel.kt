package com.lagu.songfy.presentation.playlist

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.lagu.songfy.data.local.datastore.PlaylistPreferences
import com.lagu.songfy.data.local.datastore.UserPlaylistDto
import com.lagu.songfy.data.remote.api.SongfyApiService
import com.lagu.songfy.data.remote.dto.ArtistShortDto
import com.lagu.songfy.data.remote.dto.SongDto
import com.lagu.songfy.data.repository.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val playlistPreferences: PlaylistPreferences,
    private val apiService: SongfyApiService,
    private val userProfileRepository: UserProfileRepository
) : ViewModel() {

    private val currentUid: String
        get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    private val _playlist = MutableStateFlow<UserPlaylistDto?>(null)
    val playlist: StateFlow<UserPlaylistDto?> = _playlist.asStateFlow()

    private val _userName = MutableStateFlow("Kamu")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _userAvatarUrl = MutableStateFlow<String?>(null)
    val userAvatarUrl: StateFlow<String?> = _userAvatarUrl.asStateFlow()

    private val _recommendedSongs = MutableStateFlow<List<SongDto>>(emptyList())
    val recommendedSongs: StateFlow<List<SongDto>> = _recommendedSongs.asStateFlow()

    private val _isRecommendationsLoading = MutableStateFlow(false)
    val isRecommendationsLoading: StateFlow<Boolean> = _isRecommendationsLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SongDto>>(emptyList())
    val searchResults: StateFlow<List<SongDto>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _inPlaylistQuery = MutableStateFlow("")
    val inPlaylistQuery: StateFlow<String> = _inPlaylistQuery.asStateFlow()

    private val _sortOption = MutableStateFlow(PlaylistSortOption.DEFAULT)
    val sortOption: StateFlow<PlaylistSortOption> = _sortOption.asStateFlow()

    private var currentPlaylistId: String = ""
    private var searchJob: Job? = null
    private var playlistObserverJob: Job? = null

    fun setInPlaylistQuery(query: String) {
        _inPlaylistQuery.value = query
    }

    fun setSortOption(option: PlaylistSortOption) {
        _sortOption.value = option
    }

    fun getRelativeTime(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val minutes = (diff / (1000 * 60)).coerceAtLeast(1)
        return when {
            minutes < 60 -> "$minutes mnt"
            minutes < 1440 -> "${minutes / 60} jam"
            else -> "${minutes / 1440} hr"
        }
    }

    init {
        observeUserProfile()
    }

    private fun observeUserProfile() {
        viewModelScope.launch {
            userProfileRepository.userProfile.collect { profile ->
                if (profile.username.isNotBlank()) {
                    _userName.value = profile.username
                } else {
                    val firebaseName = FirebaseAuth.getInstance().currentUser?.displayName
                    if (!firebaseName.isNullOrBlank()) {
                        _userName.value = firebaseName
                    }
                }
                if (!profile.avatarUrl.isNullOrBlank()) {
                    _userAvatarUrl.value = profile.avatarUrl
                }
            }
        }
    }

    fun loadPlaylist(playlistId: String, fallbackTitle: String, fallbackArtwork: String? = null) {
        currentPlaylistId = playlistId
        playlistObserverJob?.cancel()

        playlistObserverJob = viewModelScope.launch {
            playlistPreferences.getUserPlaylists(currentUid).collect { playlists ->
                val found = playlists.firstOrNull { it.id == playlistId || it.title.equals(fallbackTitle, ignoreCase = true) }
                if (found != null) {
                    _playlist.value = found
                } else if (_playlist.value == null) {
                    // Create or provide transient item if not yet saved in datastore
                    _playlist.value = UserPlaylistDto(
                        id = playlistId,
                        title = fallbackTitle.ifBlank { "Playlist Saya" },
                        artworkUrl = fallbackArtwork
                    )
                }
            }
        }

        val titleLower = fallbackTitle.lowercase().trim()
        val idLower = playlistId.lowercase().trim()
        val isExcluded = titleLower.contains("like song") || titleLower.contains("lagu offline") ||
                titleLower == "offline" || idLower.contains("offline") || idLower.contains("like_song") || idLower.contains("liked")
        if (!isExcluded) {
            loadRecommendations()
        } else {
            _recommendedSongs.value = emptyList()
        }
    }

    fun loadRecommendations() {
        viewModelScope.launch {
            _isRecommendationsLoading.value = true
            try {
                // Curated default songs matching Spotify reference
                val fallbackCurated = listOf(
                    SongDto(
                        videoId = "otCpCn0l4Wo",
                        title = "Shape of My Heart",
                        artists = listOf(ArtistShortDto(name = "Backstreet Boys")),
                        thumbnailUrl = "https://i.ytimg.com/vi/otCpCn0l4Wo/hqdefault.jpg",
                        duration = "3:50"
                    ),
                    SongDto(
                        videoId = "1ZRbJ_gWfoc",
                        title = "Magnolia",
                        artists = listOf(ArtistShortDto(name = "Magnolia Celebration")),
                        thumbnailUrl = "https://i.ytimg.com/vi/1ZRbJ_gWfoc/hqdefault.jpg",
                        duration = "3:30"
                    ),
                    SongDto(
                        videoId = "fV84eBwH4L8",
                        title = "Loose",
                        artists = listOf(ArtistShortDto(name = "Daniel Caesar")),
                        thumbnailUrl = "https://i.ytimg.com/vi/fV84eBwH4L8/hqdefault.jpg",
                        duration = "3:05"
                    ),
                    SongDto(
                        videoId = "k5F09kGk1vA",
                        title = "Foto kita blur",
                        artists = listOf(ArtistShortDto(name = "Sal Priadi")),
                        thumbnailUrl = "https://i.ytimg.com/vi/k5F09kGk1vA/hqdefault.jpg",
                        duration = "3:40"
                    ),
                    SongDto(
                        videoId = "aX7_K_P6hHQ",
                        title = "Pelangi",
                        artists = listOf(ArtistShortDto(name = "HiVi!")),
                        thumbnailUrl = "https://i.ytimg.com/vi/aX7_K_P6hHQ/hqdefault.jpg",
                        duration = "3:34"
                    ),
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
                        videoId = "n3K0tZ_3Q4U",
                        title = "Satu Bulan",
                        artists = listOf(ArtistShortDto(name = "Bernadya")),
                        thumbnailUrl = "https://i.ytimg.com/vi/n3K0tZ_3Q4U/hqdefault.jpg",
                        duration = "3:12"
                    )
                )

                val currentSongs = _playlist.value?.songs ?: emptyList()
                val fetchedSongs = if (currentSongs.isNotEmpty()) {
                    // Try to fetch recommendations based on artist of the first song
                    val artistQuery = currentSongs.first().artists.firstOrNull()?.name ?: currentSongs.first().title
                    try {
                        val searchRes = apiService.search(query = artistQuery, filter = "songs")
                        val existingIds = currentSongs.map { it.videoId }.toSet()
                        val filtered = searchRes.songs.filterNot { existingIds.contains(it.videoId) }
                        if (filtered.isNotEmpty()) filtered.take(10) else fallbackCurated
                    } catch (_: Exception) {
                        fallbackCurated
                    }
                } else {
                    fallbackCurated
                }

                _recommendedSongs.value = fetchedSongs
            } catch (_: Exception) {
            } finally {
                _isRecommendationsLoading.value = false
            }
        }
    }

    fun updateCover(imageUri: Uri, context: Context) {
        viewModelScope.launch {
            try {
                val coversDir = File(context.filesDir, "playlist_covers")
                if (!coversDir.exists()) coversDir.mkdirs()

                val targetFile = File(coversDir, "cover_${currentPlaylistId}_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(imageUri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val savedUri = Uri.fromFile(targetFile).toString()
                playlistPreferences.updatePlaylistInfo(
                    playlistId = currentPlaylistId,
                    newTitle = _playlist.value?.title ?: "Playlist",
                    newDescription = _playlist.value?.description ?: "",
                    newArtworkUrl = savedUri,
                    uid = currentUid
                )

                _playlist.update { it?.copy(artworkUrl = savedUri) }
            } catch (_: Exception) {}
        }
    }

    fun updateTitleAndDescription(newTitle: String, newDescription: String) {
        viewModelScope.launch {
            playlistPreferences.updatePlaylistInfo(
                playlistId = currentPlaylistId,
                newTitle = newTitle,
                newDescription = newDescription,
                newArtworkUrl = null,
                uid = currentUid
            )
            _playlist.update { it?.copy(title = newTitle, description = newDescription) }
        }
    }

    fun addSong(song: SongDto) {
        // Optimistic UI update: instantly update _playlist so UI swaps immediately without delay
        _playlist.update { current ->
            if (current != null) {
                val already = current.songs.any { it.videoId == song.videoId }
                if (!already) {
                    val updatedSongs = current.songs + song
                    val newArt = current.artworkUrl ?: song.thumbnailUrl
                    current.copy(songs = updatedSongs, artworkUrl = newArt)
                } else current
            } else {
                UserPlaylistDto(
                    id = currentPlaylistId,
                    title = "Playlist",
                    songs = listOf(song),
                    artworkUrl = song.thumbnailUrl
                )
            }
        }

        viewModelScope.launch {
            val plId = currentPlaylistId
            if (plId.isNotBlank()) {
                val userPlaylists = playlistPreferences.getUserPlaylists(currentUid).firstOrNull() ?: emptyList()
                val exists = userPlaylists.any { it.id == plId }
                if (!exists) {
                    val current = _playlist.value
                    val created = playlistPreferences.createPlaylist(
                        title = current?.title ?: "Playlist",
                        description = current?.description ?: "",
                        uid = currentUid
                    )
                    currentPlaylistId = created.id
                    playlistPreferences.addSongToPlaylist(playlistId = created.id, song = song, uid = currentUid)
                } else {
                    playlistPreferences.addSongToPlaylist(playlistId = plId, song = song, uid = currentUid)
                }
            }
        }
    }

    fun removeSong(videoId: String) {
        // Optimistic in-memory update
        _playlist.update { current ->
            if (current != null) {
                val updatedSongs = current.songs.filterNot { it.videoId == videoId }
                current.copy(songs = updatedSongs)
            } else null
        }
        viewModelScope.launch {
            playlistPreferences.removeSongFromPlaylist(playlistId = currentPlaylistId, videoId = videoId, uid = currentUid)
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }

        searchJob = viewModelScope.launch {
            delay(400) // Debounce
            _isSearching.value = true
            try {
                val res = apiService.search(query = query.trim(), filter = "songs")
                _searchResults.value = res.songs
            } catch (_: Exception) {
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun deletePlaylist(onDeleted: () -> Unit) {
        viewModelScope.launch {
            playlistPreferences.deletePlaylist(playlistId = currentPlaylistId, uid = currentUid)
            onDeleted()
        }
    }

    fun duplicatePlaylist(onDuplicated: (String) -> Unit) {
        viewModelScope.launch {
            val copy = playlistPreferences.duplicatePlaylist(playlistId = currentPlaylistId, uid = currentUid)
            if (copy != null) {
                onDuplicated(copy.id)
            }
        }
    }
}

enum class PlaylistSortOption(val displayName: String) {
    DEFAULT("Kustom / Default"),
    TITLE_ASC("Judul (A-Z)"),
    ARTIST_ASC("Artis (A-Z)")
}
