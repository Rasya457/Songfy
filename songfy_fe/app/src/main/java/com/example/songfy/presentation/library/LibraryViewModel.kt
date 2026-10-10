package com.lagu.songfy.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.lagu.songfy.data.local.datastore.PlaylistPreferences
import com.lagu.songfy.data.local.datastore.UserPlaylistDto
import com.lagu.songfy.data.local.datastore.UserSavedArtist
import com.lagu.songfy.data.remote.dto.SongDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val playlistPreferences: PlaylistPreferences,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val currentUidFlow = callbackFlow {
        trySend(firebaseAuth.currentUser?.uid ?: "")
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.uid ?: "")
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    private val currentUid: String
        get() = firebaseAuth.currentUser?.uid ?: ""

    val savedArtists: StateFlow<List<UserSavedArtist>> =
        currentUidFlow.flatMapLatest { uid ->
            playlistPreferences.getSavedArtists(uid)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userPlaylists: StateFlow<List<UserPlaylistDto>> =
        currentUidFlow.flatMapLatest { uid ->
            playlistPreferences.getUserPlaylists(uid)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addArtists(artists: List<UserSavedArtist>) {
        viewModelScope.launch {
            playlistPreferences.addArtists(artists, currentUid)
        }
    }

    fun removeArtist(artistName: String) {
        viewModelScope.launch {
            playlistPreferences.removeArtist(artistName, currentUid)
        }
    }

    fun createPlaylist(
        title: String,
        artworkUrl: String? = null,
        onCreated: (UserPlaylistDto) -> Unit = {}
    ) {
        viewModelScope.launch {
            val pl = playlistPreferences.createPlaylist(
                title = title,
                artworkUrl = artworkUrl,
                uid = currentUid
            )
            onCreated(pl)
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            playlistPreferences.deletePlaylist(playlistId, currentUid)
        }
    }

    fun addSongToPlaylist(playlistId: String, song: SongDto) {
        viewModelScope.launch {
            playlistPreferences.addSongToPlaylist(playlistId, song, currentUid)
        }
    }

    fun removeSongFromPlaylist(playlistId: String, videoId: String) {
        viewModelScope.launch {
            playlistPreferences.removeSongFromPlaylist(playlistId, videoId, currentUid)
        }
    }
}
