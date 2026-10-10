package com.lagu.songfy.presentation.artist

import com.lagu.songfy.data.remote.dto.ArtistDetailDto

data class ArtistUiState(
    val isLoading: Boolean = false,
    val artistDetail: ArtistDetailDto? = null,
    val isFollowing: Boolean = false,
    val showAllSongs: Boolean = false,
    val activeTab: String = "Musik",
    val error: String? = null
)
