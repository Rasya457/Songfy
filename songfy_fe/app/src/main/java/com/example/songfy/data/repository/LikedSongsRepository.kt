package com.lagu.songfy.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.lagu.songfy.data.remote.dto.ArtistShortDto
import com.lagu.songfy.data.remote.dto.SongDto
import com.lagu.songfy.data.local.datastore.PlaylistPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository yang mengelola daftar lagu yang di-like user.
 * Data disimpan di Firestore: users/{uid}/liked_songs/{videoId}
 * dan di-sync real-time menggunakan Firestore snapshot listener.
 */
@Singleton
class LikedSongsRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
    private val playlistPreferences: PlaylistPreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _likedSongs = MutableStateFlow<List<SongDto>>(emptyList())
    val likedSongs: StateFlow<List<SongDto>> = _likedSongs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var listenerRegistration: ListenerRegistration? = null
    private var currentUserId: String? = null

    init {
        // Listen to auth state changes to start/stop Firestore listener
        firebaseAuth.addAuthStateListener { auth ->
            val uid = auth.currentUser?.uid
            if (uid != currentUserId) {
                stopListening()
                currentUserId = uid
                if (uid != null) {
                    startListening(uid)
                } else {
                    _likedSongs.value = emptyList()
                }
            }
        }

        // Initialize for current user if already logged in
        firebaseAuth.currentUser?.uid?.let { uid ->
            currentUserId = uid
            startListening(uid)
        }
    }

    private fun startListening(uid: String) {
        _isLoading.value = true
        listenerRegistration = firestore
            .collection("users")
            .document(uid)
            .collection("liked_songs")
            .orderBy("likedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("LikedSongsRepository", "Firestore listener error: ${error.message}")
                    _isLoading.value = false
                    return@addSnapshotListener
                }

                val songs = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val videoId = doc.getString("videoId") ?: return@mapNotNull null
                        val title = doc.getString("title") ?: return@mapNotNull null
                        val thumbnailUrl = doc.getString("thumbnailUrl")
                        val duration = doc.getString("duration")

                        @Suppress("UNCHECKED_CAST")
                        val artistsRaw = doc.get("artists") as? List<Map<String, Any?>>
                        val artists = artistsRaw?.map { artistMap ->
                            ArtistShortDto(
                                id = artistMap["id"] as? String,
                                name = (artistMap["name"] as? String) ?: "Unknown Artist"
                            )
                        } ?: emptyList()

                        SongDto(
                            videoId = videoId,
                            title = title,
                            artists = artists,
                            thumbnailUrl = thumbnailUrl,
                            duration = duration
                        )
                    } catch (e: Exception) {
                        Log.w("LikedSongsRepository", "Failed to parse liked song: ${e.message}")
                        null
                    }
                } ?: emptyList()

                _likedSongs.value = songs
                _isLoading.value = false

                scope.launch {
                    try {
                        playlistPreferences.syncPlaylistSongs(
                            playlistTitle = "like song",
                            description = "Kumpulan lagu yang kamu sukai",
                            songs = songs,
                            uid = uid
                        )
                    } catch (e: Exception) {
                        Log.w("LikedSongsRepository", "Failed to sync like song playlist: ${e.message}")
                    }
                }
            }
    }

    private fun stopListening() {
        listenerRegistration?.remove()
        listenerRegistration = null
    }
}
