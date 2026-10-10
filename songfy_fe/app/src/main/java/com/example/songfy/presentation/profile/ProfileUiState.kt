package com.lagu.songfy.presentation.profile

import com.lagu.songfy.data.repository.SongHistoryItem

data class ProfileUiState(
    val username: String = "Rasya Decca",
    val userId: String = "#1305114",
    val email: String = "",
    val isVerified: Boolean = true,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val totalHoursListened: String = "12.5",
    val totalSongsPlayed: Int = 0,
    val joinDurationText: String = "1 Bulan",
    val friendsCount: Int = 0,
    val songHistory: List<SongHistoryItem> = emptyList(),
    val isUploadingAvatar: Boolean = false,
    val isUploadingBanner: Boolean = false,
    val messageText: String? = null,
    val errorText: String? = null,
    val isLoggedOut: Boolean = false
)
