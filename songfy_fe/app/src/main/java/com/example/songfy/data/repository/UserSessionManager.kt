package com.lagu.songfy.data.repository

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.lagu.songfy.data.remote.api.SongfyApiService
import com.lagu.songfy.data.remote.dto.SessionStartRequestDto
import com.lagu.songfy.service.PlaybackStateManager
import com.lagu.songfy.util.SecurePrefsFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: SongfyApiService,
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val playbackStateManager: PlaybackStateManager
) {
    private val tag = "UserSessionManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val prefs = SecurePrefsFactory.get(context, "songfy_session")

    val thisDeviceName: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    private val _forceLogoutMessage = MutableStateFlow<String?>(null)
    val forceLogoutMessage: StateFlow<String?> = _forceLogoutMessage.asStateFlow()

    private val _currentUidFlow = MutableStateFlow<String?>(firebaseAuth.currentUser?.uid)
    val currentUidFlow: StateFlow<String?> = _currentUidFlow.asStateFlow()

    private val _serverDeviceName = MutableStateFlow<String?>(null)
    val serverDeviceName: StateFlow<String?> = _serverDeviceName.asStateFlow()

    private val _serverSessionId = MutableStateFlow<String?>(null)
    val serverSessionId: StateFlow<String?> = _serverSessionId.asStateFlow()

    private val _serverUpdatedAt = MutableStateFlow<Long?>(null)
    val serverUpdatedAt: StateFlow<Long?> = _serverUpdatedAt.asStateFlow()

    private val _isCurrentDeviceSession = MutableStateFlow(true)
    val isCurrentDeviceSession: StateFlow<Boolean> = _isCurrentDeviceSession.asStateFlow()

    private var localSessionId: String? = prefs.getString("local_session_id", null)
    private var sessionListener: ListenerRegistration? = null
    private var isStartingSession = false

    init {
        firebaseAuth.addAuthStateListener { auth ->
            val uid = auth.currentUser?.uid
            _currentUidFlow.value = uid
            if (uid != null) {
                // If user is logged in and not currently listening, start session or listen
                if (sessionListener == null) {
                    scope.launch {
                        startSession()
                    }
                }
            } else {
                stopSessionListener()
                localSessionId = null
                prefs.edit().remove("local_session_id").apply()
            }
        }
    }

    /**
     * Memulai sesi di backend dan memasang Firestore realtime listener
     * untuk mendeteksi single-session / 1 akun = 1 perangkat.
     */
    suspend fun startSession(): Boolean = withContext(Dispatchers.IO) {
        val user = firebaseAuth.currentUser ?: return@withContext false
        if (isStartingSession) return@withContext false
        isStartingSession = true

        try {
            val token = try {
                user.getIdToken(false).await().token
            } catch (e: Exception) {
                Log.w(tag, "Failed to get Firebase ID token: ${e.message}")
                null
            } ?: return@withContext false

            val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
            val request = SessionStartRequestDto(deviceName = deviceName)
            val authHeader = "Bearer $token"

            val response = apiService.startSession(authHeader = authHeader, request = request)
            localSessionId = response.sessionId
            prefs.edit().putString("local_session_id", response.sessionId).apply()
            Log.i(tag, "Session started successfully: ${response.sessionId} on device: $deviceName")

            listenToActiveSession(user.uid)
            return@withContext true
        } catch (e: Exception) {
            Log.e(tag, "Failed to start session via backend", e)
            // Fallback: listen anyway using existing localSessionId if available
            listenToActiveSession(user.uid)
            return@withContext false
        } finally {
            isStartingSession = false
        }
    }

    private fun listenToActiveSession(uid: String) {
        stopSessionListener()

        sessionListener = firestore.collection("users")
            .document(uid)
            .collection("session")
            .document("active")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(tag, "Firestore session listener error: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot == null || !snapshot.exists()) {
                    _serverDeviceName.value = null
                    _serverSessionId.value = null
                    _serverUpdatedAt.value = null
                    _isCurrentDeviceSession.value = true
                    return@addSnapshotListener
                }

                val serverSessionId = snapshot.getString("activeSessionId")
                val otherDeviceName = snapshot.getString("deviceName") ?: "perangkat lain"
                val updatedTimestamp = snapshot.getTimestamp("updatedAt")?.toDate()?.time

                _serverSessionId.value = serverSessionId
                _serverDeviceName.value = otherDeviceName
                _serverUpdatedAt.value = updatedTimestamp

                val currentLocal = localSessionId
                val isMatch = serverSessionId != null && currentLocal != null && serverSessionId == currentLocal
                _isCurrentDeviceSession.value = isMatch

                // Jika serverSessionId sudah berganti dan berbeda dari sessionId perangkat ini,
                // berarti ada perangkat baru yang baru saja login!
                if (!serverSessionId.isNullOrBlank() && !currentLocal.isNullOrBlank() && serverSessionId != currentLocal) {
                    Log.w(tag, "Force logout triggered: serverSessionId=$serverSessionId != localSessionId=$currentLocal (Device: $otherDeviceName)")
                    handleForceLogout(otherDeviceName)
                }
            }
    }

    private fun handleForceLogout(otherDeviceName: String) {
        stopSessionListener()
        localSessionId = null
        prefs.edit().remove("local_session_id").apply()

        // Hentikan pemutaran musik dan bersihkan state
        playbackStateManager.resetOnLogout()

        // Logout Firebase Auth
        try {
            firebaseAuth.signOut()
        } catch (e: Exception) {
            Log.w(tag, "Error signing out during force logout: ${e.message}")
        }

        // Tampilkan pesan logout paksa yang menyertakan nama perangkat baru
        _forceLogoutMessage.value = "Akunmu login di perangkat lain ($otherDeviceName)"
    }

    fun dismissForceLogout() {
        _forceLogoutMessage.value = null
    }

    /**
     * Memutus perangkat lain dan mengambil alih sesi secara eksklusif untuk perangkat ini.
     * Mengenerate sessionId baru di server sehingga perangkat lain langsung ditendang keluar.
     */
    suspend fun kickOtherDevices(): Boolean {
        return startSession()
    }

    fun performLogout() {
        stopSessionListener()
        localSessionId = null
        prefs.edit().remove("local_session_id").apply()
        playbackStateManager.resetOnLogout()
        try {
            firebaseAuth.signOut()
        } catch (e: Exception) {
            Log.w(tag, "Error during manual logout: ${e.message}")
        }
    }

    private fun stopSessionListener() {
        sessionListener?.remove()
        sessionListener = null
        _serverDeviceName.value = null
        _serverSessionId.value = null
        _serverUpdatedAt.value = null
        _isCurrentDeviceSession.value = true
    }
}
