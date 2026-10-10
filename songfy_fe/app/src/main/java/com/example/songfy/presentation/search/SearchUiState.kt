package com.lagu.songfy.presentation.search

import com.lagu.songfy.data.remote.dto.AlbumSummaryDto
import com.lagu.songfy.data.remote.dto.ArtistSummaryDto
import com.lagu.songfy.data.remote.dto.SongDto

data class SearchUiState(
    val query: String = "",
    val isSearchActive: Boolean = false,
    val isSearching: Boolean = false,
    val searchResults: List<SongDto> = emptyList(),
    val artistResults: List<ArtistSummaryDto> = emptyList(),
    val albumResults: List<AlbumSummaryDto> = emptyList(),
    val selectedFilter: String = "Semua",
    val recentSearches: List<SongDto> = emptyList(),
    val viralHits: List<SongDto> = emptyList(),
    val trendingNow: List<SongDto> = emptyList(),
    val recentReleases: List<SongDto> = emptyList(),
    val showBanner: Boolean = false,
    val showClearConfirmDialog: Boolean = false,
    val snackbarMessage: String? = null,
    val error: String? = null
)
