package com.lagu.songfy.presentation.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.lagu.songfy.data.local.datastore.LastPlayedSongPreferences
import com.lagu.songfy.data.repository.SongHistoryRepository
import com.lagu.songfy.data.repository.UserProfileRepository
import com.lagu.songfy.data.repository.UserSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val historyRepository: SongHistoryRepository,
    private val userProfileRepository: UserProfileRepository,
    private val lastPlayedSongPreferences: LastPlayedSongPreferences,
    private val userSessionManager: UserSessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    val thisDeviceName: String = userSessionManager.thisDeviceName
    val serverDeviceName: StateFlow<String?> = userSessionManager.serverDeviceName
    val isCurrentDeviceActive: StateFlow<Boolean> = userSessionManager.isCurrentDeviceSession
    val serverUpdatedAt: StateFlow<Long?> = userSessionManager.serverUpdatedAt

    init {
        observeUserProfile()
        observeHistory()
    }

    private fun observeUserProfile() {
        viewModelScope.launch {
            userProfileRepository.userProfile.collect { profile ->
                _uiState.update {
                    it.copy(
                        username = profile.username,
                        userId = profile.userId,
                        email = profile.email,
                        avatarUrl = profile.avatarUrl,
                        bannerUrl = profile.bannerUrl,
                        isVerified = profile.isVerified,
                        joinDurationText = profile.joinDurationText
                    )
                }
            }
        }
    }

    private fun observeHistory() {
        viewModelScope.launch {
            // Combine both flows so we always have consistent, non-stale values.
            // distinctUntilChanged on history prevents redundant recompositions
            // triggered by PlayerViewModel's per-tick progress updates.
            combine(
                historyRepository.history,
                historyRepository.totalListeningTimeSeconds
            ) { list, totalSecs ->
                val hours = (totalSecs / 3600.0) + (list.size * 0.15)
                val formattedHours = "%.1f".format(hours)
                Triple(list, list.size, formattedHours)
            }.collect { (list, totalSongs, formattedHours) ->
                _uiState.update {
                    it.copy(
                        songHistory = list,
                        totalSongsPlayed = totalSongs,
                        totalHoursListened = formattedHours
                    )
                }
            }
        }
    }

    fun updateAvatarUri(uri: Uri) {
        _uiState.update { it.copy(isUploadingAvatar = true) }
        viewModelScope.launch {
            try {
                userProfileRepository.updateAvatar(uri.toString())
                _uiState.update {
                    it.copy(
                        isUploadingAvatar = false,
                        messageText = "Profile picture updated successfully!"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isUploadingAvatar = false,
                        errorText = "Failed to update avatar: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun updateBannerUri(uri: Uri) {
        _uiState.update { it.copy(isUploadingBanner = true) }
        viewModelScope.launch {
            try {
                userProfileRepository.updateBanner(uri.toString())
                _uiState.update {
                    it.copy(
                        isUploadingBanner = false,
                        messageText = "Banner updated successfully!"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isUploadingBanner = false,
                        errorText = "Failed to update banner: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun updateUsername(newUsername: String) {
        if (newUsername.isBlank()) return
        userProfileRepository.updateUsername(newUsername.trim())
        _uiState.update { it.copy(messageText = "Username updated!") }
    }

    fun changePassword(newPass: String) {
        if (newPass.length < 6) {
            _uiState.update { it.copy(errorText = "Password must be at least 6 characters.") }
            return
        }
        val user = firebaseAuth.currentUser
        user?.updatePassword(newPass)
            ?.addOnSuccessListener {
                _uiState.update { it.copy(messageText = "Password updated successfully!") }
            }
            ?.addOnFailureListener { e ->
                _uiState.update { it.copy(errorText = "Password change failed: ${e.localizedMessage}") }
            }
    }

    fun kickOtherDevices(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            try {
                val success = userSessionManager.kickOtherDevices()
                if (success) {
                    _uiState.update { it.copy(messageText = "Perangkat lain berhasil dikeluarkan!") }
                    onResult(true, "Perangkat lain berhasil dikeluarkan!")
                } else {
                    onResult(false, "Gagal mengeluarkan perangkat lain.")
                }
            } catch (e: Exception) {
                onResult(false, e.localizedMessage ?: "Terjadi kesalahan")
            }
        }
    }

    fun signOut() {
        // Hentikan pemutaran, hapus session listener, dan logout Firebase
        userSessionManager.performLogout()
        _uiState.update { it.copy(isLoggedOut = true) }
    }

    fun clearMessages() {
        // Also reset isLoggedOut so SettingsScreen LaunchedEffect does not re-trigger on reopen
        _uiState.update { it.copy(messageText = null, errorText = null, isLoggedOut = false) }
    }
}