package com.lagu.songfy

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.auth.FirebaseAuth
import com.lagu.songfy.data.remote.dto.SongDto
import com.lagu.songfy.presentation.artist.ArtistScreen
import com.lagu.songfy.presentation.artist.ArtistPickerScreen
import com.lagu.songfy.presentation.playlist.PlaylistDetailScreen
import com.lagu.songfy.presentation.library.LikedSongsScreen
import com.lagu.songfy.presentation.library.LibraryViewModel
import com.lagu.songfy.presentation.auth.AuthViewModel
import com.lagu.songfy.presentation.auth.SignInScreen
import com.lagu.songfy.presentation.auth.SignUpScreen
import com.lagu.songfy.presentation.navigation.MainTabScreen
import com.lagu.songfy.presentation.player.PlayerScreen
import com.lagu.songfy.presentation.player.PlayerViewModel
import com.lagu.songfy.presentation.player.ExpandablePlayerSheet
import com.lagu.songfy.presentation.player.rememberPlayerSheetState
import com.lagu.songfy.presentation.common.MiniPlayerBar
import com.lagu.songfy.presentation.profile.ProfileViewModel
import com.lagu.songfy.presentation.profile.SettingsScreen
import com.lagu.songfy.presentation.profile.EditProfileScreen
import com.lagu.songfy.presentation.theme.AppSettings
import com.lagu.songfy.presentation.theme.BackgroundNearBlack
import com.lagu.songfy.presentation.theme.SongfyTheme
import com.lagu.songfy.data.repository.LikedSongsRepository
import com.lagu.songfy.data.repository.UserSessionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CurrentScreen {
    SignIn,
    SignUp,
    MainTabs,
    Player,
    Settings,
    EditProfile,
    Artist,
    ArtistPicker,
    LikedSongs,
    PlaylistDetail
}

private fun CurrentScreen.depth(): Int = when (this) {
    CurrentScreen.SignIn -> 0
    CurrentScreen.SignUp -> 1
    CurrentScreen.MainTabs -> 0
    CurrentScreen.Artist,
    CurrentScreen.LikedSongs,
    CurrentScreen.PlaylistDetail,
    CurrentScreen.Settings -> 1
    CurrentScreen.ArtistPicker,
    CurrentScreen.EditProfile -> 2
    CurrentScreen.Player -> 3
}

// iOS UIKit Push / Pop navigation physics specs
private val IosSlideSpec = tween<IntOffset>(
    durationMillis = 380,
    easing = CubicBezierEasing(0.28f, 0f, 0.2f, 1f)
)
private val IosFadeSpec = tween<Float>(
    durationMillis = 380,
    easing = CubicBezierEasing(0.28f, 0f, 0.2f, 1f)
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var likedSongsRepository: LikedSongsRepository

    @Inject
    lateinit var userSessionManager: UserSessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.init(this)
        enableEdgeToEdge()
        setContent {
            SongfyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AppSettings.colors.background
                ) {
                    val authViewModel: AuthViewModel = hiltViewModel()
                    val profileViewModel: ProfileViewModel = hiltViewModel()
                    val libraryViewModel: LibraryViewModel = hiltViewModel()
                    val playerViewModel: PlayerViewModel = hiltViewModel()
                    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
                    val forceLogoutMsg by userSessionManager.forceLogoutMessage.collectAsStateWithLifecycle()

                    // Request notification permission for playback notification on Android 13+ (TIRAMISU)
                    val context = LocalContext.current
                    val notificationPermissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { /* granted -> notification will show */ }

                    LaunchedEffect(Unit) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        if (FirebaseAuth.getInstance().currentUser != null) {
                            userSessionManager.startSession()
                        }
                    }

                    // Synchronously check if an authenticated user session already exists
                    val isUserCurrentlyLoggedIn = remember { FirebaseAuth.getInstance().currentUser != null }
                    var currentScreen by remember {
                        mutableStateOf(if (isUserCurrentlyLoggedIn) CurrentScreen.MainTabs else CurrentScreen.SignIn)
                    }

                    // Full Player expansion state (Spotify-style overlay: keeps underlying screens mounted)
                    var isPlayerExpanded by remember { mutableStateOf(false) }

                    // ExpandablePlayerSheet state — gesture-driven mini→full player
                    val playerSheetState = rememberPlayerSheetState()

                    // Sync isPlayerExpanded ↔ sheet progress (legacy callers still set isPlayerExpanded)
                    LaunchedEffect(isPlayerExpanded) {
                        if (isPlayerExpanded && playerSheetState.progress < 0.5f) {
                            playerSheetState.expand()
                        } else if (!isPlayerExpanded && playerSheetState.progress > 0.5f) {
                            playerSheetState.collapse()
                        }
                    }

                    // Active tab state — always start at Home (tab 0), not persisted
                    var activeMainTab by remember { mutableIntStateOf(0) }
                    // Artist navigation state
                    var currentArtistId by remember { mutableStateOf("") }
                    // Playlist navigation state
                    var currentPlaylistId by remember { mutableStateOf("") }
                    var currentPlaylistTitle by remember { mutableStateOf("") }
                    var currentPlaylistArtwork by remember { mutableStateOf<String?>(null) }

                    // Only navigate to MainTabs when user successfully authenticates from SignIn / SignUp
                    LaunchedEffect(authState.isAuthenticated) {
                        if (authState.isAuthenticated && (currentScreen == CurrentScreen.SignIn || currentScreen == CurrentScreen.SignUp)) {
                            userSessionManager.startSession()
                            currentScreen = CurrentScreen.MainTabs
                        }
                    }

                    // Legacy compatibility: redirect Player screen to modal overlay
                    LaunchedEffect(currentScreen) {
                        if (currentScreen == CurrentScreen.Player) {
                            currentScreen = CurrentScreen.MainTabs
                            isPlayerExpanded = true
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        // ── iOS Navigation Layer: Smooth Push / Pop Parallax Transition ──
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = {
                                val isForward = when {
                                    targetState == CurrentScreen.MainTabs -> false
                                    initialState == CurrentScreen.MainTabs -> true
                                    initialState == CurrentScreen.SignIn && targetState == CurrentScreen.MainTabs -> true
                                    initialState == CurrentScreen.Settings && targetState == CurrentScreen.SignIn -> false
                                    else -> targetState.depth() >= initialState.depth()
                                }

                                if (isForward) {
                                    // PUSH: Masuk dari kanan 100%, halaman sebelumnya geser ke kiri 28% dengan parallax dan meredup
                                    (slideInHorizontally(
                                        initialOffsetX = { fullWidth -> fullWidth },
                                        animationSpec = IosSlideSpec
                                    ) + fadeIn(
                                        animationSpec = IosFadeSpec,
                                        initialAlpha = 0.85f
                                    )).togetherWith(
                                        slideOutHorizontally(
                                            targetOffsetX = { fullWidth -> (-fullWidth * 0.28f).toInt() },
                                            animationSpec = IosSlideSpec
                                        ) + fadeOut(
                                            animationSpec = IosFadeSpec,
                                            targetAlpha = 0.65f
                                        )
                                    ).apply {
                                        targetContentZIndex = 1f
                                    }
                                } else {
                                    // POP: Halaman saat ini keluar ke kanan 100%, halaman sebelumnya kembali dari kiri -28% parallax ke 0
                                    (slideInHorizontally(
                                        initialOffsetX = { fullWidth -> (-fullWidth * 0.28f).toInt() },
                                        animationSpec = IosSlideSpec
                                    ) + fadeIn(
                                        animationSpec = IosFadeSpec,
                                        initialAlpha = 0.65f
                                    )).togetherWith(
                                        slideOutHorizontally(
                                            targetOffsetX = { fullWidth -> fullWidth },
                                            animationSpec = IosSlideSpec
                                        ) + fadeOut(
                                            animationSpec = IosFadeSpec,
                                            targetAlpha = 0.85f
                                        )
                                    ).apply {
                                        targetContentZIndex = 0f
                                    }
                                }
                            },
                            label = "ScreenTransition",
                            modifier = Modifier.fillMaxSize()
                        ) { targetScreen ->
                            when (targetScreen) {
                                CurrentScreen.SignIn -> {
                                    SignInScreen(
                                        viewModel = authViewModel,
                                        onNavigateToSignUp = {
                                            authViewModel.clearErrors()
                                            currentScreen = CurrentScreen.SignUp
                                        },
                                        onAuthSuccess = {
                                            currentScreen = CurrentScreen.MainTabs
                                        }
                                    )
                                }
                                CurrentScreen.SignUp -> {
                                    BackHandler {
                                        authViewModel.clearErrors()
                                        currentScreen = CurrentScreen.SignIn
                                    }
                                    SignUpScreen(
                                        viewModel = authViewModel,
                                        onNavigateToSignIn = {
                                            authViewModel.clearErrors()
                                            currentScreen = CurrentScreen.SignIn
                                        },
                                        onAuthSuccess = {
                                            currentScreen = CurrentScreen.MainTabs
                                        }
                                    )
                                }
                                CurrentScreen.MainTabs -> {
                                    MainTabScreen(
                                        initialTab = activeMainTab,
                                        onTabChanged = { activeMainTab = it },
                                        onSongClick = { song ->
                                            playerViewModel.setSong(song)
                                            isPlayerExpanded = true
                                        },
                                        onPlaylistClick = { playlist ->
                                            currentPlaylistId = playlist.id
                                            currentPlaylistTitle = playlist.title
                                            currentPlaylistArtwork = playlist.artworkUrl
                                            currentScreen = CurrentScreen.PlaylistDetail
                                        },
                                        onNavigateToPlayer = {
                                            isPlayerExpanded = true
                                        },
                                        onNavigateToSettings = {
                                            currentScreen = CurrentScreen.Settings
                                        },
                                        onNavigateToLikedSongs = {
                                            currentScreen = CurrentScreen.LikedSongs
                                        },
                                        onArtistClick = { artistId ->
                                            currentArtistId = artistId
                                            currentScreen = CurrentScreen.Artist
                                        },
                                        onAddArtistClick = {
                                            currentScreen = CurrentScreen.ArtistPicker
                                        },
                                        profileViewModel = profileViewModel,
                                        playerViewModel = playerViewModel,
                                        playerSheetState = playerSheetState
                                    )
                                }
                                CurrentScreen.Settings -> {
                                    BackHandler {
                                        currentScreen = CurrentScreen.MainTabs
                                    }
                                    SettingsScreen(
                                        viewModel = profileViewModel,
                                        onBackClick = {
                                            currentScreen = CurrentScreen.MainTabs
                                        },
                                        onNavigateToEditProfile = {
                                            currentScreen = CurrentScreen.EditProfile
                                        },
                                        onLogoutSuccess = {
                                            isPlayerExpanded = false
                                            userSessionManager.performLogout()
                                            currentScreen = CurrentScreen.SignIn
                                        }
                                    )
                                }
                                CurrentScreen.EditProfile -> {
                                    BackHandler {
                                        currentScreen = CurrentScreen.Settings
                                    }
                                    EditProfileScreen(
                                        viewModel = profileViewModel,
                                        onBackClick = {
                                            currentScreen = CurrentScreen.Settings
                                        }
                                    )
                                }
                                CurrentScreen.Artist -> {
                                    BackHandler {
                                        currentScreen = CurrentScreen.MainTabs
                                    }
                                    ArtistScreen(
                                        artistIdOrName = currentArtistId,
                                        playerUiState = playerViewModel.uiState.collectAsStateWithLifecycle().value,
                                        onSongClick = { song, queue ->
                                            playerViewModel.setSong(song, queue = queue)
                                            isPlayerExpanded = true
                                        },
                                        onBackClick = {
                                            currentScreen = CurrentScreen.MainTabs
                                        },
                                        onTogglePlayPause = { playerViewModel.togglePlayPause() },
                                        onNavigateToPlayer = { isPlayerExpanded = true },
                                        onTabSelected = { targetTab ->
                                            activeMainTab = targetTab
                                            currentScreen = CurrentScreen.MainTabs
                                        }
                                    )
                                }
                                CurrentScreen.ArtistPicker -> {
                                    BackHandler {
                                        currentScreen = CurrentScreen.MainTabs
                                    }
                                    ArtistPickerScreen(
                                        onBackClick = {
                                            currentScreen = CurrentScreen.MainTabs
                                        },
                                        onDone = { artists ->
                                            if (artists.isNotEmpty()) {
                                                libraryViewModel.addArtists(artists)
                                            }
                                            activeMainTab = 2
                                            currentScreen = CurrentScreen.MainTabs
                                        }
                                    )
                                }
                                CurrentScreen.LikedSongs -> {
                                    BackHandler {
                                        currentScreen = CurrentScreen.MainTabs
                                    }
                                    LikedSongsScreen(
                                        likedSongsRepository = likedSongsRepository,
                                        onBackClick = {
                                            currentScreen = CurrentScreen.MainTabs
                                        },
                                        onSongClick = { song ->
                                            playerViewModel.setSong(song)
                                            isPlayerExpanded = true
                                        },
                                        onPlayAllClick = { songs ->
                                            if (songs.isNotEmpty()) {
                                                playerViewModel.setSong(songs.first(), queue = songs)
                                                isPlayerExpanded = true
                                            }
                                        }
                                    )
                                }
                                CurrentScreen.PlaylistDetail -> {
                                    BackHandler {
                                        currentScreen = CurrentScreen.MainTabs
                                    }
                                    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
                                    PlaylistDetailScreen(
                                        playlistId = currentPlaylistId,
                                        fallbackTitle = currentPlaylistTitle,
                                        fallbackArtwork = currentPlaylistArtwork,
                                        currentPlayingVideoId = playerState.currentSong?.videoId,
                                        onBackClick = {
                                            currentScreen = CurrentScreen.MainTabs
                                        },
                                        onSongClick = { song, queue ->
                                            playerViewModel.setSong(song, queue = queue)
                                            isPlayerExpanded = true
                                        },
                                        onArtistClick = { artistName ->
                                            currentArtistId = artistName
                                            currentScreen = CurrentScreen.Artist
                                        },
                                        onNavigateToPlayer = {
                                            playerSheetState.expand()
                                            isPlayerExpanded = true
                                        },
                                        onNavigateToSearch = {
                                            activeMainTab = 1
                                            currentScreen = CurrentScreen.MainTabs
                                        },
                                        playerViewModel = playerViewModel
                                    )
                                }
                                CurrentScreen.Player -> {
                                    // Kept for exhaustive when; handled by modal overlay
                                    Box(modifier = Modifier.fillMaxSize())
                                }
                            }
                        }

                        // ── ExpandablePlayerSheet: gesture-driven mini→full player ──
                        // Menggantikan AnimatedVisibility yang pakai enter/exit animation statis.
                        // progress dibaca di graphicsLayer → ZERO recompose saat drag.
                        // Navbar turun otomatis karena playerSheetState di-pass ke MainTabScreen.
                        val playerUiState by playerViewModel.uiState.collectAsStateWithLifecycle()
                        // On PlaylistDetail, the screen has its own Apple Music pill-style mini player.
                        // Only show the global sheet on other screens (or when sheet is expanding to full player).
                        val showGlobalSheet = playerUiState.currentSong != null &&
                            (currentScreen != CurrentScreen.PlaylistDetail || playerSheetState.progress > 0.01f)
                        if (showGlobalSheet) {
                            ExpandablePlayerSheet(
                                state = playerSheetState,
                                navBarHeight = if (currentScreen == CurrentScreen.MainTabs || currentScreen == CurrentScreen.Artist) {
                                    72.dp
                                } else {
                                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 4.dp
                                },
                                miniPlayerContent = {
                                    MiniPlayerBar(
                                        uiState = playerUiState,
                                        onTogglePlayPause = { playerViewModel.togglePlayPause() },
                                        onNavigateToPlayer = {
                                            playerSheetState.expand()
                                        },
                                        playbackTickFlow = playerViewModel.playbackTick
                                    )
                                },
                                fullPlayerContent = {
                                    PlayerScreen(
                                        viewModel = playerViewModel,
                                        onBackClick = { 
                                            playerSheetState.collapse()
                                            isPlayerExpanded = false
                                        },
                                        onArtistClick = { artistId ->
                                            playerSheetState.collapse()
                                            isPlayerExpanded = false
                                            currentArtistId = artistId
                                            currentScreen = CurrentScreen.Artist
                                        }
                                    )
                                }
                            )
                        }

                        // ── Force Logout Alert Dialog (1 Akun = 1 Perangkat) ──
                        if (forceLogoutMsg != null) {
                            androidx.compose.material3.AlertDialog(
                                onDismissRequest = {
                                    userSessionManager.dismissForceLogout()
                                    isPlayerExpanded = false
                                    currentScreen = CurrentScreen.SignIn
                                },
                                title = {
                                    androidx.compose.material3.Text(
                                        text = "Sesi Berakhir",
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        color = AppSettings.colors.textPrimary
                                    )
                                },
                                text = {
                                    androidx.compose.material3.Text(
                                        text = forceLogoutMsg ?: "Akunmu login di perangkat lain.",
                                        color = AppSettings.colors.textSecondary
                                    )
                                },
                                confirmButton = {
                                    androidx.compose.material3.TextButton(
                                        onClick = {
                                            userSessionManager.dismissForceLogout()
                                            isPlayerExpanded = false
                                            currentScreen = CurrentScreen.SignIn
                                        }
                                    ) {
                                        androidx.compose.material3.Text(
                                            text = "OK",
                                            color = AppSettings.colors.accentBlue,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                        )
                                    }
                                },
                                containerColor = AppSettings.colors.cardBackground,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
