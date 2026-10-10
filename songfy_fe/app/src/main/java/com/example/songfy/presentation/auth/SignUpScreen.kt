package com.lagu.songfy.presentation.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.lagu.songfy.R
import com.lagu.songfy.presentation.theme.*

@Composable
fun SignUpScreen(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
    onNavigateToSignIn: () -> Unit = {},
    onAuthSuccess: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Redirect to Home when authenticated (Google sign-in, or email verified + re-login)
    LaunchedEffect(uiState.isAuthenticated) {
        if (uiState.isAuthenticated) {
            onAuthSuccess()
        }
    }

    // Email verification sent dialog
    if (uiState.isEmailVerificationSent) {
        AlertDialog(
            onDismissRequest = {
                viewModel.dismissEmailVerification()
                onNavigateToSignIn()
            },
            containerColor = Color(0xFF1A1A2E),
            shape = RoundedCornerShape(20.dp),
            icon = {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(AccentSilver, AccentSilverDim))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = BackgroundNearBlack,
                        modifier = Modifier.size(26.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "Cek Emailmu! 📧",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryWhite,
                    fontSize = 20.sp
                )
            },
            text = {
                Text(
                    text = "Akun berhasil dibuat! Kami sudah kirim link verifikasi ke email kamu.\n\nKlik link tersebut, lalu login kembali untuk mulai streaming.",
                    color = TextSecondaryGray,
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissEmailVerification()
                        onNavigateToSignIn()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentSilver,
                        contentColor = BackgroundNearBlack
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Login Sekarang", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Google Sign-In launcher setup
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            account?.idToken?.let { token ->
                viewModel.signInWithGoogleToken(token)
            } ?: run {
                viewModel.setAuthError("Google Sign-In returned an empty token.")
            }
        } catch (e: ApiException) {
            if (e.statusCode == 10) {
                viewModel.setAuthError("Google Sign-In Code 10: SHA-1 fingerprint belum terdaftar di Firebase Console. Silakan gunakan Login Email & Password atau mendaftarkan SHA-1.")
            } else if (e.statusCode != 12501) { // 12501 = user cancelled
                viewModel.setAuthError("Google Sign-In failed (code ${e.statusCode})")
            }
        } catch (e: Exception) {
            viewModel.setAuthError("Google Sign-In error: ${e.localizedMessage}")
        }
    }

    fun launchGoogleSignIn() {
        val webClientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            "975555734156-rickp9inilcrul6292cpk78ia06m13nn.apps.googleusercontent.com"
        }
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        val client = GoogleSignIn.getClient(context, gso)
        googleSignInLauncher.launch(client.signInIntent)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundNearBlack)
    ) {
        // Ambient background purple glow matching Songfy identity
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x2B8B5CF6),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ----------------------------------------------------
            // 1. TOP APP BAR (Back Button + Centered Title)
            // ----------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button to return to Sign In
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(SurfacePillInactive)
                        .clickable(onClick = onNavigateToSignIn),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Sign In",
                        tint = TextPrimaryWhite,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Text(
                    text = "Sign up",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimaryWhite,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                // Balance Spacer
                Spacer(modifier = Modifier.size(38.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ----------------------------------------------------
            // 2. BRAND LOGO & HEADLINE
            // ----------------------------------------------------
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    AccentSilver,
                                    AccentSilverDim
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "Songfy Logo",
                        tint = BackgroundNearBlack,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Songfy",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryWhite,
                    letterSpacing = (-0.5).sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Create your account to start streaming",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryWhite,
                letterSpacing = (-0.6).sp,
                lineHeight = 34.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(26.dp))

            // ----------------------------------------------------
            // 3. FIREBASE AUTH ERROR BANNER
            // ----------------------------------------------------
            if (uiState.authError != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x2DF43F5E))
                        .border(1.dp, Color(0xFFF43F5E), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Error",
                            tint = Color(0xFFF43F5E),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = uiState.authError!!,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimaryWhite
                        )
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            // ----------------------------------------------------
            // 4. EMAIL INPUT FIELD
            // ----------------------------------------------------
            CustomInputBox(
                label = "Email",
                value = uiState.emailInput,
                onValueChange = { viewModel.onEmailChanged(it) },
                placeholder = "name@example.com",
                errorMessage = uiState.emailError,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                enabled = !uiState.isLoading && !uiState.isGoogleLoading
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ----------------------------------------------------
            // 5. PASSWORD INPUT FIELD WITH SHOW/HIDE
            // ----------------------------------------------------
            CustomInputBox(
                label = "Password",
                value = uiState.passwordInput,
                onValueChange = { viewModel.onPasswordChanged(it) },
                placeholder = "At least 8 characters",
                errorMessage = uiState.passwordError,
                isPassword = true,
                isPasswordVisible = uiState.isPasswordVisible,
                onTogglePasswordVisibility = { viewModel.togglePasswordVisibility() },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                enabled = !uiState.isLoading && !uiState.isGoogleLoading
            )

            // Password Strength Indicator
            val pwd = uiState.passwordInput
            val (strengthLabel, strengthColor, strengthFraction) = when {
                pwd.length >= 12 && pwd.any { it.isDigit() } && pwd.any { it.isUpperCase() } ->
                    Triple("Strong 💪", Color(0xFF22C55E), 1f)
                pwd.length >= 8 && (pwd.any { it.isDigit() } || pwd.any { it.isUpperCase() }) ->
                    Triple("Fair 😐", Color(0xFFF59E0B), 0.6f)
                pwd.isNotEmpty() ->
                    Triple("Weak 😟", Color(0xFFEF4444), 0.25f)
                else -> Triple("", Color.Transparent, 0f)
            }
            if (pwd.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(strengthFraction)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(strengthColor)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = strengthLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = strengthColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))


            // ----------------------------------------------------
            CustomInputBox(
                label = "Confirm Password",
                value = uiState.confirmPasswordInput,
                onValueChange = { viewModel.onConfirmPasswordChanged(it) },
                placeholder = "Re-enter password",
                errorMessage = uiState.confirmPasswordError,
                isPassword = true,
                isPasswordVisible = uiState.isConfirmPasswordVisible,
                onTogglePasswordVisibility = { viewModel.toggleConfirmPasswordVisibility() },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        viewModel.signUpWithEmail()
                    }
                ),
                enabled = !uiState.isLoading && !uiState.isGoogleLoading
            )

            Spacer(modifier = Modifier.height(26.dp))

            // ----------------------------------------------------
            // 7. PRIMARY CREATE ACCOUNT BUTTON
            // ----------------------------------------------------
            Button(
                onClick = {
                    keyboardController?.hide()
                    viewModel.signUpWithEmail()
                },
                enabled = !uiState.isLoading && !uiState.isGoogleLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .shadow(
                        elevation = 12.dp,
                        shape = CircleShape,
                        ambientColor = AccentSilverDim.copy(alpha = 0.3f),
                        spotColor = AccentSilverDim.copy(alpha = 0.5f)
                    ),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentSilver,
                    contentColor = BackgroundNearBlack,
                    disabledContainerColor = AccentSilver.copy(alpha = 0.5f),
                    disabledContentColor = BackgroundNearBlack.copy(alpha = 0.5f)
                )
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = BackgroundNearBlack,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Text(
                        text = "Create Account",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // ----------------------------------------------------
            // 8. HORIZONTAL DIVIDER WITH "or"
            // ----------------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = Color.White.copy(alpha = 0.15f)
                )
                Text(
                    text = "or",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = TextSecondaryGray,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = Color.White.copy(alpha = 0.15f)
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            // ----------------------------------------------------
            // 9. GOOGLE SIGN IN BUTTON
            // ----------------------------------------------------
            OutlinedButton(
                onClick = {
                    keyboardController?.hide()
                    launchGoogleSignIn()
                },
                enabled = !uiState.isLoading && !uiState.isGoogleLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = CircleShape,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = SurfacePillInactive,
                    contentColor = TextPrimaryWhite
                )
            ) {
                if (uiState.isGoogleLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = TextPrimaryWhite,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        GoogleLogoIcon(size = 20.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Continue with Google",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimaryWhite
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ----------------------------------------------------
            // 10. FOOTER LINK (Already have an account? Sign In)
            // ----------------------------------------------------
            Row(
                modifier = Modifier
                    .clickable(onClick = onNavigateToSignIn)
                    .padding(vertical = 8.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Already have an account? ",
                    fontSize = 14.sp,
                    color = TextSecondaryGray
                )
                Text(
                    text = "Sign In",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentSilver
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
