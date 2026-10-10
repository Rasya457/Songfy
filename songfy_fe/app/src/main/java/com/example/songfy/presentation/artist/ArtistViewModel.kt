package com.lagu.songfy.presentation.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagu.songfy.data.remote.api.SongfyApiService
import com.lagu.songfy.data.remote.dto.ArtistDetailDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArtistViewModel @Inject constructor(
    private val apiService: SongfyApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArtistUiState())
    val uiState: StateFlow<ArtistUiState> = _uiState.asStateFlow()

    private var currentArtistId: String? = null
    private val artistCache = mutableMapOf<String, ArtistDetailDto>()

    fun loadArtist(artistIdOrName: String) {
        if (artistIdOrName.isBlank()) return
        if (currentArtistId == artistIdOrName && _uiState.value.artistDetail != null) return

        currentArtistId = artistIdOrName

        val cached = artistCache[artistIdOrName]
        if (cached != null) {
            _uiState.update { it.copy(isLoading = false, artistDetail = cached, error = null) }
            return
        }

        _uiState.update { it.copy(isLoading = true, artistDetail = null, error = null) }

        viewModelScope.launch {
            try {
                val detail = apiService.getArtist(artistIdOrName)
                if (artistCache.size > 20) artistCache.clear()
                artistCache[artistIdOrName] = detail
                if (currentArtistId == artistIdOrName) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            artistDetail = detail,
                            error = null
                        )
                    }
                }
            } catch (e: Exception) {
                if (currentArtistId == artistIdOrName) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.localizedMessage ?: "Gagal memuat profil artis"
                        )
                    }
                }
            }
        }
    }

    fun toggleFollow() {
        _uiState.update { it.copy(isFollowing = !it.isFollowing) }
    }

    fun toggleShowAllSongs() {
        _uiState.update { it.copy(showAllSongs = !it.showAllSongs) }
    }

    fun selectTab(tab: String) {
        _uiState.update { it.copy(activeTab = tab) }
    }
}
