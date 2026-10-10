package com.lagu.songfy.presentation.profile

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.Coil
import coil.compose.AsyncImage
import com.lagu.songfy.presentation.theme.AppSettings
import com.lagu.songfy.presentation.theme.AppThemeMode
import kotlinx.coroutines.delay

@OptIn(coil.annotation.ExperimentalCoilApi::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
    onBackClick: () -> Unit = {},
    onNavigateToEditProfile: () -> Unit = {},
    onLogoutSuccess: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val serverDeviceName by viewModel.serverDeviceName.collectAsStateWithLifecycle()
    val isCurrentDeviceActive by viewModel.isCurrentDeviceActive.collectAsStateWithLifecycle()
    val thisDeviceName = viewModel.thisDeviceName
    val context = LocalContext.current
    val colors = AppSettings.colors

    var showDevicesDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showSupportDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    var isKickingDevices by remember { mutableStateOf(false) }
    var deviceFeedbackMsg by remember { mutableStateOf<String?>(null) }
    var newPasswordInput by remember { mutableStateOf("") }
    var cacheClearedMessage by remember { mutableStateOf<String?>(null) }

    // Observe logout
    val isLoggedOut = uiState.isLoggedOut
    LaunchedEffect(isLoggedOut) {
        if (isLoggedOut) {
            viewModel.clearMessages()
            onLogoutSuccess()
        }
    }

    // Auto dismiss cache banner
    LaunchedEffect(cacheClearedMessage) {
        if (cacheClearedMessage != null) {
            delay(2000)
            cacheClearedMessage = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // ----------------------------------------------------
            // 1. iOS HEADER
            // ----------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back arrow
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.cardBackground)
                        .border(0.5.dp, colors.cardBorder, CircleShape)
                        .clickable(onClick = onBackClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Center Title
                Text(
                    text = "Settings",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )

                // Placeholder for symmetry
                Spacer(modifier = Modifier.size(36.dp))
            }

            // Notification / Banner feedback
            AnimatedVisibility(
                visible = cacheClearedMessage != null || uiState.messageText != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val bannerText = cacheClearedMessage ?: uiState.messageText ?: ""
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.accentBlue.copy(alpha = 0.15f))
                        .border(1.dp, colors.accentBlue.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = colors.accentBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = bannerText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.accentBlue
                        )
                    }
                }
            }

            // Scrollable Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // ----------------------------------------------------
                // 2. PROFILE SECTION
                // ----------------------------------------------------
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 12.dp, start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Circular Profile Photo
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .background(colors.cardBackground)
                            .border(0.5.dp, colors.cardBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (uiState.avatarUrl != null) {
                            AsyncImage(
                                model = uiState.avatarUrl,
                                contentDescription = "Profile Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(colors.accentBlue),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = uiState.username.take(1).uppercase(),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Username, Email & Edit Profile Button
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = uiState.username.ifBlank { "User" },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = uiState.email.ifBlank { "user@songfy.com" },
                            fontSize = 13.sp,
                            color = colors.textSecondary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Blue "Edit profile" pill button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(colors.accentBlue)
                                .clickable(onClick = onNavigateToEditProfile)
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Edit profile",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }

                // ----------------------------------------------------
                // 3. PREFERENCES SECTION
                // ----------------------------------------------------
                IOSSectionHeader(title = "Preferences")

                IOSCardGroup {
                    // Notifications and Sounds
                    IOSSettingsRow(
                        title = "Notifications and sounds",
                        icon = Icons.Outlined.Notifications,
                        trailingContent = {
                            Switch(
                                checked = AppSettings.notificationsEnabled,
                                onCheckedChange = { isChecked ->
                                    AppSettings.setNotifications(context, isChecked)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = colors.accentBlue,
                                    checkedBorderColor = Color.Transparent,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = colors.switchTrackOff,
                                    uncheckedBorderColor = Color.Transparent
                                ),
                                modifier = Modifier.scale(0.85f)
                            )
                        }
                    )

                    IOSDivider()

                    // Theme
                    IOSSettingsRow(
                        title = "Theme",
                        icon = Icons.Outlined.Palette,
                        onClick = { showThemeDialog = true },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (AppSettings.currentTheme == AppThemeMode.Dark) "Dark" else "Light",
                                    fontSize = 15.sp,
                                    color = colors.textSecondary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = colors.textSecondary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    )
                }

                // ----------------------------------------------------
                // 4. ACCOUNT SECTION
                // ----------------------------------------------------
                IOSSectionHeader(title = "Account")

                IOSCardGroup {
                    // Perangkat / Devices
                    val isOtherDeviceActive = serverDeviceName != null && serverDeviceName != thisDeviceName
                    IOSSettingsRow(
                        title = "Perangkat",
                        icon = Icons.Default.Smartphone,
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isOtherDeviceActive) "1 HP lain aktif" else "HP ini aktif",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isOtherDeviceActive) colors.accentRed else colors.accentBlue
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = colors.textSecondary.copy(alpha = 0.5f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        onClick = {
                            deviceFeedbackMsg = null
                            showDevicesDialog = true
                        }
                    )

                    IOSDivider()

                    // Password
                    IOSSettingsRow(
                        title = "Password",
                        icon = Icons.Outlined.Lock,
                        onClick = { showPasswordDialog = true }
                    )

                    IOSDivider()

                    // Support
                    IOSSettingsRow(
                        title = "Support",
                        icon = Icons.Outlined.Info,
                        onClick = { showSupportDialog = true }
                    )

                    IOSDivider()

                    // Clear Cache
                    IOSSettingsRow(
                        title = "Clear cache",
                        icon = Icons.Outlined.Folder,
                        onClick = { showClearCacheDialog = true }
                    )

                    IOSDivider()

                    // Terms and Privacy Policy
                    IOSSettingsRow(
                        title = "Terms and Privacy Policy",
                        icon = Icons.Outlined.Shield,
                        onClick = { showTermsDialog = true }
                    )

                    IOSDivider()

                    // Logout (Red Accent)
                    IOSSettingsRow(
                        title = "Logout",
                        icon = Icons.AutoMirrored.Outlined.ExitToApp,
                        iconColor = colors.accentRed,
                        titleColor = colors.accentRed,
                        onClick = { showLogoutDialog = true },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = colors.accentRed.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(36.dp))
            }
        }

        // ----------------------------------------------------
        // DIALOGS & ACTION SHEETS
        // ----------------------------------------------------

        // Theme Dialog
        if (showThemeDialog) {
            AlertDialog(
                onDismissRequest = { showThemeDialog = false },
                title = {
                    Text(
                        text = "Choose Theme",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    AppSettings.setTheme(context, AppThemeMode.Light)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Light Mode",
                                fontSize = 15.sp,
                                color = colors.textPrimary
                            )
                            if (AppSettings.currentTheme == AppThemeMode.Light) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = colors.accentBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = colors.divider, thickness = 0.5.dp)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    AppSettings.setTheme(context, AppThemeMode.Dark)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Dark Mode",
                                fontSize = 15.sp,
                                color = colors.textPrimary
                            )
                            if (AppSettings.currentTheme == AppThemeMode.Dark) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = colors.accentBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showThemeDialog = false }) {
                        Text("Done", color = colors.accentBlue, fontWeight = FontWeight.SemiBold)
                    }
                },
                containerColor = colors.cardBackground,
                shape = RoundedCornerShape(16.dp)
            )
        }

        // Change Password Dialog
        if (showPasswordDialog) {
            AlertDialog(
                onDismissRequest = {
                    showPasswordDialog = false
                    newPasswordInput = ""
                },
                title = {
                    Text(
                        text = "Change Password",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Enter a new password with at least 6 characters.",
                            fontSize = 13.sp,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedTextField(
                            value = newPasswordInput,
                            onValueChange = { newPasswordInput = it },
                            placeholder = { Text("New password", color = colors.textSecondary) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary,
                                focusedBorderColor = colors.accentBlue,
                                unfocusedBorderColor = colors.cardBorder,
                                focusedContainerColor = colors.inputBackground,
                                unfocusedContainerColor = colors.inputBackground
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newPasswordInput.length >= 6) {
                                viewModel.changePassword(newPasswordInput)
                                showPasswordDialog = false
                                newPasswordInput = ""
                            }
                        },
                        enabled = newPasswordInput.length >= 6
                    ) {
                        Text(
                            text = "Update",
                            color = if (newPasswordInput.length >= 6) colors.accentBlue else colors.textSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showPasswordDialog = false
                        newPasswordInput = ""
                    }) {
                        Text("Cancel", color = colors.textSecondary)
                    }
                },
                containerColor = colors.cardBackground,
                shape = RoundedCornerShape(16.dp)
            )
        }

        // Support Dialog
        if (showSupportDialog) {
            AlertDialog(
                onDismissRequest = { showSupportDialog = false },
                title = {
                    Text(
                        text = "Songfy Support",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Need help or have suggestions? We'd love to hear from you.",
                            fontSize = 13.sp,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Email: support@songfy.app",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "App Version: 1.0.0 (Build 42)",
                            fontSize = 13.sp,
                            color = colors.textSecondary
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSupportDialog = false }) {
                        Text("Close", color = colors.accentBlue, fontWeight = FontWeight.SemiBold)
                    }
                },
                containerColor = colors.cardBackground,
                shape = RoundedCornerShape(16.dp)
            )
        }

        // Clear Cache Dialog
        if (showClearCacheDialog) {
            AlertDialog(
                onDismissRequest = { showClearCacheDialog = false },
                title = {
                    Text(
                        text = "Clear Cache",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to clear temporary cache and image files? This will free up storage space.",
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showClearCacheDialog = false
                            try {
                                Coil.imageLoader(context).diskCache?.clear()
                                Coil.imageLoader(context).memoryCache?.clear()
                                context.cacheDir.deleteRecursively()
                            } catch (_: Exception) {}
                            cacheClearedMessage = "Cache cleared successfully!"
                        }
                    ) {
                        Text(
                            text = "Clear",
                            color = colors.accentRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearCacheDialog = false }) {
                        Text("Cancel", color = colors.textSecondary)
                    }
                },
                containerColor = colors.cardBackground,
                shape = RoundedCornerShape(16.dp)
            )
        }

        // Terms and Privacy Policy Dialog
        if (showTermsDialog) {
            AlertDialog(
                onDismissRequest = { showTermsDialog = false },
                title = {
                    Text(
                        text = "Terms & Privacy Policy",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "1. Privacy Commitment\nSongfy values your personal data. We do not sell your personal information to third parties. Listening history is strictly used to improve your recommendations.\n\n2. Audio Streaming\nMusic content streamed through Songfy is provided for personal, non-commercial entertainment.\n\n3. Account Security\nYou are responsible for maintaining the confidentiality of your login credentials.",
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = colors.textSecondary
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showTermsDialog = false }) {
                        Text("Done", color = colors.accentBlue, fontWeight = FontWeight.SemiBold)
                    }
                },
                containerColor = colors.cardBackground,
                shape = RoundedCornerShape(16.dp)
            )
        }

        // Logout Confirmation Dialog
        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = {
                    Text(
                        text = "Log Out",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to log out of your Songfy account?",
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showLogoutDialog = false
                            viewModel.signOut()
                        }
                    ) {
                        Text(
                            text = "Log Out",
                            color = colors.accentRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutDialog = false }) {
                        Text("Cancel", color = colors.textSecondary)
                    }
                },
                containerColor = colors.cardBackground,
                shape = RoundedCornerShape(16.dp)
            )
        }

        // Manage Devices Dialog
        if (showDevicesDialog) {
            val isOtherDeviceActive = serverDeviceName != null && serverDeviceName != thisDeviceName

            AlertDialog(
                onDismissRequest = {
                    if (!isKickingDevices) {
                        showDevicesDialog = false
                        deviceFeedbackMsg = null
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Smartphone,
                            contentDescription = null,
                            tint = colors.accentBlue,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Kelola Perangkat",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Songfy menerapkan aturan 1 Akun = 1 Perangkat untuk privasi dan kelancaran streaming.",
                            fontSize = 12.sp,
                            color = colors.textSecondary,
                            lineHeight = 16.sp
                        )

                        // 1. Current Device Card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.cardBackground)
                                .border(1.dp, if (!isOtherDeviceActive) colors.accentBlue.copy(alpha = 0.4f) else colors.cardBorder, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(colors.accentBlue.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Smartphone,
                                        contentDescription = null,
                                        tint = colors.accentBlue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = thisDeviceName,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = "Perangkat saat ini (HP ini)",
                                        fontSize = 11.sp,
                                        color = colors.textSecondary
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            if (!isOtherDeviceActive) colors.accentBlue.copy(alpha = 0.15f)
                                            else colors.cardBorder.copy(alpha = 0.3f)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (!isOtherDeviceActive) "Aktif" else "Terhubung",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (!isOtherDeviceActive) colors.accentBlue else colors.textSecondary
                                    )
                                }
                            }
                        }

                        // 2. Other Device Card / Status
                        if (isOtherDeviceActive) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.accentRed.copy(alpha = 0.08f))
                                    .border(1.dp, colors.accentRed.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(colors.accentRed.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Smartphone,
                                                contentDescription = null,
                                                tint = colors.accentRed,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = serverDeviceName ?: "Perangkat Lain",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.textPrimary
                                            )
                                            Text(
                                                text = "Sesi aktif di server",
                                                fontSize = 11.sp,
                                                color = colors.accentRed
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .background(colors.accentRed.copy(alpha = 0.15f))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "HP Lain",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.accentRed
                                            )
                                        }
                                    }

                                    Text(
                                        text = "Perangkat di atas memiliki sesi aktif di server. Kamu bisa logout paksa perangkat tersebut agar akun ini sepenuhnya dikuasai oleh HP ini.",
                                        fontSize = 12.sp,
                                        color = colors.textSecondary,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        } else {
                            // No other device active
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.inputBackground)
                                    .padding(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = colors.accentBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Hanya HP ini yang terhubung ke akunmu. Aman!",
                                        fontSize = 12.sp,
                                        color = colors.textSecondary
                                    )
                                }
                            }
                        }

                        // Feedback message if action taken
                        if (deviceFeedbackMsg != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.accentBlue.copy(alpha = 0.15f))
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = deviceFeedbackMsg ?: "",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.accentBlue
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    if (isOtherDeviceActive) {
                        Button(
                            onClick = {
                                isKickingDevices = true
                                deviceFeedbackMsg = null
                                viewModel.kickOtherDevices { success, msg ->
                                    isKickingDevices = false
                                    deviceFeedbackMsg = msg
                                }
                            },
                            enabled = !isKickingDevices,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accentRed,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isKickingDevices) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = "Logout Paksa HP Lain",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        TextButton(
                            onClick = {
                                isKickingDevices = true
                                deviceFeedbackMsg = null
                                viewModel.kickOtherDevices { success, msg ->
                                    isKickingDevices = false
                                    deviceFeedbackMsg = if (success) "Sesi HP ini berhasil diperbarui!" else msg
                                }
                            },
                            enabled = !isKickingDevices
                        ) {
                            Text(
                                text = "Segarkan Sesi",
                                color = colors.accentBlue,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showDevicesDialog = false
                        deviceFeedbackMsg = null
                    }) {
                        Text(
                            text = "Tutup",
                            color = colors.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                },
                containerColor = colors.cardBackground,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

// ----------------------------------------------------
// HELPER COMPOSABLES FOR iOS LOOK & FEEL
// ----------------------------------------------------

@Composable
fun IOSSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = AppSettings.colors.textSecondary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
fun IOSCardGroup(
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = AppSettings.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.cardBackground)
            .border(0.5.dp, colors.cardBorder, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
fun IOSDivider() {
    HorizontalDivider(
        color = AppSettings.colors.divider,
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = 50.dp) // Native iOS indent after the icon
    )
}

@Composable
fun IOSSettingsRow(
    title: String,
    icon: ImageVector,
    iconColor: Color = AppSettings.colors.iconTint,
    titleColor: Color = AppSettings.colors.textPrimary,
    trailingContent: @Composable () -> Unit = {
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = AppSettings.colors.textSecondary.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp)
        )
    },
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            color = titleColor,
            modifier = Modifier.weight(1f)
        )

        trailingContent()
    }
}
