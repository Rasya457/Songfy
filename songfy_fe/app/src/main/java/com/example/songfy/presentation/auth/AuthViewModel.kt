package com.lagu.songfy.presentation.auth

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.GoogleAuthProvider
import com.lagu.songfy.data.repository.UserSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val userSessionManager: UserSessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        checkCurrentUser()
    }

    fun checkCurrentUser() {
        val user = firebaseAuth.currentUser
        if (user != null) {
            _uiState.update { it.copy(isAuthenticated = true) }
        }
    }

    fun onEmailChanged(email: String) {
        _uiState.update {
            it.copy(
                emailInput = email,
                emailError = null,
                authError = null
            )
        }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update {
            it.copy(
                passwordInput = password,
                passwordError = null,
                authError = null
            )
        }
    }

    fun onConfirmPasswordChanged(confirmPassword: String) {
        _uiState.update {
            it.copy(
                confirmPasswordInput = confirmPassword,
                confirmPasswordError = null,
                authError = null
            )
        }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun toggleConfirmPasswordVisibility() {
        _uiState.update { it.copy(isConfirmPasswordVisible = !it.isConfirmPasswordVisible) }
    }

    fun clearErrors() {
        _uiState.update {
            it.copy(
                emailError = null,
                passwordError = null,
                confirmPasswordError = null,
                authError = null
            )
        }
    }

    fun signInWithEmail() {
        val state = _uiState.value
        val email = state.emailInput.trim()
        val password = state.passwordInput.trim()

        var hasError = false
        var emailErr: String? = null
        var passwordErr: String? = null

        if (email.isEmpty()) {
            emailErr = "Email address cannot be empty"
            hasError = true
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailErr = "Please enter a valid email address"
            hasError = true
        }

        if (password.isEmpty()) {
            passwordErr = "Password cannot be empty"
            hasError = true
        } else if (password.length < 8) {
            passwordErr = "Password must be at least 8 characters"
            hasError = true
        }

        if (hasError) {
            _uiState.update {
                it.copy(
                    emailError = emailErr,
                    passwordError = passwordErr
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, authError = null) }
            try {
                firebaseAuth.signInWithEmailAndPassword(email, password).await()
                userSessionManager.startSession()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isAuthenticated = true
                    )
                }
            } catch (e: Exception) {
                val errorMsg = parseFirebaseAuthException(e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        authError = errorMsg
                    )
                }
            }
        }
    }

    fun signUpWithEmail() {
        val state = _uiState.value
        val email = state.emailInput.trim()
        val password = state.passwordInput.trim()
        val confirmPassword = state.confirmPasswordInput.trim()

        var hasError = false
        var emailErr: String? = null
        var passwordErr: String? = null
        var confirmPasswordErr: String? = null

        if (email.isEmpty()) {
            emailErr = "Email address cannot be empty"
            hasError = true
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailErr = "Please enter a valid email address"
            hasError = true
        }

        if (password.isEmpty()) {
            passwordErr = "Password cannot be empty"
            hasError = true
        } else if (password.length < 8) {
            passwordErr = "Password must be at least 8 characters"
            hasError = true
        }

        if (confirmPassword.isEmpty()) {
            confirmPasswordErr = "Please confirm your password"
            hasError = true
        } else if (password != confirmPassword) {
            confirmPasswordErr = "Passwords do not match"
            hasError = true
        }

        if (hasError) {
            _uiState.update {
                it.copy(
                    emailError = emailErr,
                    passwordError = passwordErr,
                    confirmPasswordError = confirmPasswordErr
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, authError = null) }
            try {
                firebaseAuth.createUserWithEmailAndPassword(email, password).await()
                // Kirim email verifikasi — user tidak langsung masuk.
                // isAuthenticated tetap false sampai user verifikasi email dan login ulang.
                try {
                    firebaseAuth.currentUser?.sendEmailVerification()?.await()
                } catch (_: Exception) {
                    // Verifikasi gagal terkirim tapi akun sudah dibuat — tetap lanjut.
                }
                // Sign out agar user harus login ulang setelah verifikasi email.
                firebaseAuth.signOut()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isAuthenticated = false,
                        isEmailVerificationSent = true
                    )
                }
            } catch (e: Exception) {
                val errorMsg = parseFirebaseAuthException(e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        authError = errorMsg
                    )
                }
            }
        }
    }

    /** Reset email verification state setelah user dismiss dialog. */
    fun dismissEmailVerification() {
        _uiState.update { it.copy(isEmailVerificationSent = false) }
    }

    fun signInWithGoogleToken(idToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGoogleLoading = true, authError = null) }
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                firebaseAuth.signInWithCredential(credential).await()
                userSessionManager.startSession()
                _uiState.update {
                    it.copy(
                        isGoogleLoading = false,
                        isAuthenticated = true
                    )
                }
            } catch (e: Exception) {
                val errorMsg = parseFirebaseAuthException(e)
                _uiState.update {
                    it.copy(
                        isGoogleLoading = false,
                        authError = "Google authentication failed: $errorMsg"
                    )
                }
            }
        }
    }

    fun setAuthError(message: String) {
        _uiState.update { it.copy(authError = message, isLoading = false, isGoogleLoading = false) }
    }

    fun signOut() {
        userSessionManager.performLogout()
        _uiState.update {
            AuthUiState(isAuthenticated = false)
        }
    }

    private fun parseFirebaseAuthException(e: Exception): String {
        return when (e) {
            is FirebaseAuthInvalidUserException -> "No account found with this email address."
            is FirebaseAuthInvalidCredentialsException -> "Incorrect email or password. Please try again."
            is FirebaseAuthUserCollisionException -> "An account with this email address already exists."
            is FirebaseAuthWeakPasswordException -> "The password is too weak. Please use at least 6 characters."
            is FirebaseTooManyRequestsException -> "Too many unsuccessful attempts. Please try again later."
            else -> e.localizedMessage?.takeIf { it.isNotBlank() } ?: "Authentication failed. Please check your network connection."
        }
    }
}
