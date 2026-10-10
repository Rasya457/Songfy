package com.lagu.songfy.presentation.home

import com.lagu.songfy.data.remote.dto.SongDto

data class PlaylistCardItem(
    val id: String,
    val title: String,
    val description: String,
    val artworkUrl: String,
    val isLiked: Boolean = false,
    val isPurpleTheme: Boolean = false
)

data class HomeUiState(
    val isLoading: Boolean = false,
    val selectedCategory: String = "All",
    val topPlaylists: List<PlaylistCardItem> = emptyList(),
    val dailySongs: List<SongDto> = emptyList(),
    // Real-time recently played songs from listening history
    val recentlyPlayedSongs: List<SongDto> = emptyList(),
    // Personalized recommendations based on listening history
    val recommendedSongs: List<SongDto> = emptyList(),
    // Top artists derived from history (for UI section title & algorithm)
    val topArtistsFromHistory: List<String> = emptyList(),
    val error: String? = null,
    val userName: String = "User",
    val userAvatarUrl: String? = null
    // NOTE 2026-09-12: userId/isVerified removed — zero readers in Home UI (grep).
    // Ambil dari UserProfileRepository langsung saat dibutuhkan, jangan via state.
)