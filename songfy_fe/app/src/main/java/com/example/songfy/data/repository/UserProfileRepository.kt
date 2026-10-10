package com.lagu.songfy.data.repository

import android.content.Context
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.lagu.songfy.util.SecurePrefsFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class UserProfile(
    val username: String = "User",
    val email: String = "",
    val userId: String = "#1305114",
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val isVerified: Boolean = true,
    val joinDurationText: String = "1 Bulan"
)

@Singleton
class UserProfileRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firebaseAuth: FirebaseAuth
) {
    // Encrypted storage — data sensitif (avatar URI, banner URI, username) dienkripsi
    // dengan AES256-GCM sehingga tidak bisa dibaca meski di device yang di-root.
    private fun getPrefs(uid: String) =
        SecurePrefsFactory.get(context, "songfy_profile_${uid}")

    private val _userProfile = MutableStateFlow(computeProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private val authListener = FirebaseAuth.AuthStateListener { _ ->
        _userProfile.value = computeProfile()
    }

    init {
        firebaseAuth.addAuthStateListener(authListener)
    }

    private fun getProfileImagesDir(): File =
        File(context.filesDir, "profile_images").also { it.mkdirs() }

    private fun computeProfile(): UserProfile {
        val user = firebaseAuth.currentUser ?: return UserProfile()
        val uid = user.uid
        val prefs = getPrefs(uid)

        val savedAvatar = prefs.getString("avatar_uri", null)
        val savedBanner = prefs.getString("banner_uri", null)
        val savedUsername = prefs.getString("username", null)

        val email = user.email ?: "user@songfy.app"
        val displayName = savedUsername?.takeIf { it.isNotBlank() }
            ?: user.displayName?.takeIf { it.isNotBlank() }
            ?: email.substringBefore("@").replaceFirstChar { it.uppercase() }

        // Validate stored URIs - local files must still exist
        val avatarUrl = when {
            savedAvatar == null -> user.photoUrl?.toString()
            savedAvatar.startsWith("http") -> savedAvatar
            savedAvatar.startsWith("file://") -> {
                val f = File(savedAvatar.removePrefix("file://"))
                if (f.exists() && f.length() > 0) savedAvatar else user.photoUrl?.toString()
            }
            else -> user.photoUrl?.toString()
        }

        val bannerUrl = when {
            savedBanner == null -> null
            savedBanner.startsWith("http") -> savedBanner
            savedBanner.startsWith("file://") -> {
                val f = File(savedBanner.removePrefix("file://"))
                if (f.exists() && f.length() > 0) savedBanner else null
            }
            else -> null
        }

        val uidShort = uid.take(6)
        val creationTime = user.metadata?.creationTimestamp ?: System.currentTimeMillis()
        val daysSinceJoin = ((System.currentTimeMillis() - creationTime) / (1000 * 60 * 60 * 24)).coerceAtLeast(1)
        val joinText = if (daysSinceJoin >= 30) "${daysSinceJoin / 30} Bulan" else "$daysSinceJoin Hari"

        return UserProfile(
            username = displayName,
            email = email,
            userId = "#$uidShort",
            avatarUrl = avatarUrl,
            bannerUrl = bannerUrl,
            isVerified = true,
            joinDurationText = joinText
        )
    }

    /**
     * Copy a content:// URI to app internal storage and return a file:// URI string.
     * Uses timestamp in filename to prevent Coil stale cache issues when user replaces photo.
     * Cleans up any older photos of the same prefix for this user.
     */
    private fun copyUriToInternalStorage(sourceUri: Uri, prefix: String, uid: String): String? {
        return try {
            val dir = getProfileImagesDir()
            // Clean up previous files for this prefix & user
            dir.listFiles { _, name -> name.startsWith("${prefix}_${uid}_") }?.forEach { it.delete() }

            val destFile = File(dir, "${prefix}_${uid}_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (destFile.exists() && destFile.length() > 0) {
                "file://${destFile.absolutePath}"
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun refresh() {
        _userProfile.value = computeProfile()
    }

    fun updateAvatar(uriString: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        val persistedUri = if (uriString.startsWith("content://")) {
            copyUriToInternalStorage(Uri.parse(uriString), "avatar", uid) ?: uriString
        } else {
            uriString
        }
        getPrefs(uid).edit().putString("avatar_uri", persistedUri).apply()
        refresh()
    }

    fun updateBanner(uriString: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        val persistedUri = if (uriString.startsWith("content://")) {
            copyUriToInternalStorage(Uri.parse(uriString), "banner", uid) ?: uriString
        } else {
            uriString
        }
        getPrefs(uid).edit().putString("banner_uri", persistedUri).apply()
        refresh()
    }

    fun updateUsername(newUsername: String) {
        if (newUsername.isBlank()) return
        val uid = firebaseAuth.currentUser?.uid ?: return
        getPrefs(uid).edit().putString("username", newUsername.trim()).apply()
        refresh()
    }

    fun clearLocalPrefs() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        val dir = getProfileImagesDir()
        dir.listFiles { _, name -> name.startsWith("avatar_${uid}") || name.startsWith("banner_${uid}") }?.forEach { it.delete() }
        getPrefs(uid).edit().clear().apply()
        refresh()
    }
}