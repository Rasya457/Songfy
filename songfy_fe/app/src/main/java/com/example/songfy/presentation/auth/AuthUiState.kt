package com.lagu.songfy.presentation.auth

import com.google.firebase.auth.FirebaseAuth

data class AuthUiState(
    val emailInput: String = "",
    val passwordInput: String = "",
    val confirmPasswordInput: String = "",
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val isGoogleLoading: Boolean = false,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val authError: String? = null,
    val isAuthenticated: Boolean = FirebaseAuth.getInstance().currentUser != null,
    // true setelah daftar — menandakan email verifikasi terkirim,
    // SignUpScreen harus tampilkan pesan "Cek emailmu" bukan langsung masuk.
    val isEmailVerificationSent: Boolean = false
)

