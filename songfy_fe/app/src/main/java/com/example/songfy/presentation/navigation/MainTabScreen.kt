package com.lagu.songfy.presentation.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lagu.songfy.data.remote.dto.SongDto
import com.lagu.songfy.presentation.common.MiniPlayerBar
import com.lagu.songfy.presentation.home.HomeScreen
import com.lagu.songfy.presentation.home.HomeViewModel
import com.lagu.songfy.presentation.home.PlaylistCardItem
import com.lagu.songfy.presentation.library.LibraryScreen
import com.lagu.songfy.presentation.player.PlayerSheetState
import com.lagu.songfy.presentation.player.PlayerViewModel
import com.lagu.songfy.presentation.profile.ProfileScreen
import com.lagu.songfy.presentation.profile.ProfileViewModel
import com.lagu.songfy.presentation.search.SearchScreen
import com.lagu.songfy.presentation.theme.BackgroundNearBlack
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.launch

// AnimationSpec untuk navigasi tab — tween 300ms FastOutSlowInEasing: sangat halus, presisi, dan stabil tanpa bounce
private val TAB_ANIM_SPEC = tween<Float>(
    durationMillis = 300,
    easing = FastOutSlowInEasing
)

@Composable
fun MainTabScreen(
    onSongClick: (SongDto) -> Unit = {},
    onPlaylistClick: (PlaylistCardItem) -> Unit = {},
    onNavigateToPlayer: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToLikedSongs: () -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onAddArtistClick: () -> Unit = {},
    initialTab: Int = 0,
    onTabChanged: (Int) -> Unit = {},
    onTabReselected: ((Int) -> Unit)? = null,
    homeViewModel: HomeViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
    playerSheetState: PlayerSheetState? = null,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(initialPage = initialTab, pageCount = { 4 })
    val hazeState = remember { HazeState() }
    val coroutineScope = rememberCoroutineScope()

    // PERF: playerUiState TIDAK lagi di-observe di sini.
    // Dulu: setiap 250ms tick dari playbackTick menyebabkan playerUiState emit baru
    // → MainTabScreen recompose → Box container re-measure → HorizontalPager re-layout
    // → semua 4 halaman ikut re-draw → swipe lag.
    // Fix: pindahkan observasi ke FloatingBottomControls (composable terpisah) sehingga
    // hanya control bar yang recompose, bukan pager.

    val tabBackStack = remember { mutableStateListOf<Int>() }

    // Sync ke caller saat page settle (setelah animasi selesai)
    LaunchedEffect(pagerState.settledPage) {
        onTabChanged(pagerState.settledPage)
    }

    // Sync initialTab dari navigasi eksternal
    LaunchedEffect(initialTab) {
        if (pagerState.currentPage != initialTab && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(page = initialTab, animationSpec = TAB_ANIM_SPEC)
        }
    }

    // Lambda stabil: tidak recreate saat state lain berubah (karena tidak ada playerUiState
    // di scope ini lagi), sehingga child composable tidak perlu recompose.
    val navigateToTab: (Int) -> Unit = remember(pagerState, coroutineScope, tabBackStack) {
        { targetIndex ->
            if (pagerState.currentPage != targetIndex && !pagerState.isScrollInProgress) {
                if (tabBackStack.isEmpty() || tabBackStack.last() != pagerState.currentPage) {
                    tabBackStack.add(pagerState.currentPage)
                }
                coroutineScope.launch {
                    pagerState.animateScrollToPage(
                        page = targetIndex,
                        animationSpec = TAB_ANIM_SPEC
                    )
                }
            }
        }
    }

    BackHandler(enabled = tabBackStack.isNotEmpty()) {
        val previousTab = tabBackStack.removeAt(tabBackStack.lastIndex)
        coroutineScope.launch {
            pagerState.animateScrollToPage(page = previousTab, animationSpec = TAB_ANIM_SPEC)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundNearBlack)
    ) {
        // beyondViewportPageCount = 3: Seluruh 4 tab (Home, Search, Library, Profile) sudah di-render
        // dan stand-by di scene graph GPU sejak awal. Saat swipe antar halaman, ZERO instansiasi,
        // ZERO pengukuran mendadak di tengah gesture jari -> 100% mulus 120 FPS tanpa patah-patah.
        // Pager tidak dibungkus glassSource agar render langsung native ke GPU canvas tanpa offscreen layer.
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .haze(hazeState),
            beyondViewportPageCount = 3,
            key = { page -> page }
        ) { page ->
            when (page) {
                0 -> HomeScreen(
                    viewModel = homeViewModel,
                    onSongClick = onSongClick,
                    onPlaylistClick = onPlaylistClick,
                    onArtistClick = onArtistClick,
                    onSearchClick = { navigateToTab(1) },
                    onLibraryClick = { navigateToTab(2) },
                    onProfileClick = { navigateToTab(3) }
                )
                1 -> SearchScreen(
                    onSongClick = onSongClick,
                    onArtistClick = onArtistClick,
                    onAddToQueue = { song -> playerViewModel.addToQueue(song) }
                )
                2 -> LibraryScreen(
                    homeViewModel = homeViewModel,
                    onSongClick = onSongClick,
                    onPlaylistClick = onPlaylistClick,
                    onSearchClick = { navigateToTab(1) },
                    onArtistClick = onArtistClick,
                    onAddArtistClick = onAddArtistClick
                )
                3 -> ProfileScreen(
                    viewModel = profileViewModel,
                    canGoBack = tabBackStack.isNotEmpty(),
                    onBackClick = {
                        if (tabBackStack.isNotEmpty()) {
                            val previousTab = tabBackStack.removeAt(tabBackStack.lastIndex)
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(
                                    page = previousTab,
                                    animationSpec = TAB_ANIM_SPEC
                                )
                            }
                        }
                    },
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToLikedSongs = onNavigateToLikedSongs,
                    onSongClickWithPos = { song, pos ->
                        playerViewModel.setSong(song, startPositionMs = pos)
                        onNavigateToPlayer()
                    },
                    onSongClick = onSongClick
                )
            }
        }

        // KRITIS: FloatingBottomControls adalah composable terpisah.
        // Seluruh observasi playerUiState + playbackTick ada DI DALAM composable ini,
        // bukan di MainTabScreen. Artinya setiap tick/state change hanya recompose
        // FloatingBottomControls — bukan HorizontalPager, bukan semua 4 halaman.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            FloatingBottomControls(
                pagerState = pagerState,
                playerViewModel = playerViewModel,
                playerSheetState = playerSheetState,
                hazeState = hazeState,
                onNavigateToPlayer = onNavigateToPlayer,
                onTabSelected = navigateToTab,
                onTabReselected = { reselectedIndex ->
                    if (onTabReselected != null) {
                        onTabReselected(reselectedIndex)
                    } else if (reselectedIndex == 3 && tabBackStack.isNotEmpty()) {
                        // Pop Profile backstack to root
                        tabBackStack.clear()
                    }
                }
            )
        }
    }
}

/**
 * Composable terpisah untuk floating bottom controls (MiniPlayer + Navbar).
 *
 * MENGAPA DIPISAHKAN:
 * - playerUiState berubah setiap tick (250ms saat lagu diputar)
 * - Kalau observe di MainTabScreen → recompose MainTabScreen → recompose HorizontalPager
 *   → re-draw semua 4 halaman per 250ms → swipe berat / lag
 * - Dengan dipisahkan: hanya FloatingBottomControls yang recompose saat tick,
 *   HorizontalPager dan semua halaman tetap stabil = swipe smooth 60/120fps.
 *
 * NAVBAR INDICATOR REAL-TIME:
 * - Pakai pagerState.currentPage (bukan settledPage) untuk selectedIndex navbar
 * - currentPage update real-time saat swipe berlangsung → indikator bergerak langsung
 * - settledPage hanya dipakai untuk sinkronisasi ke caller (onTabChanged)
 */
@Composable
private fun FloatingBottomControls(
    pagerState: PagerState,
    playerViewModel: PlayerViewModel,
    playerSheetState: PlayerSheetState?,
    hazeState: HazeState,
    onNavigateToPlayer: () -> Unit,
    onTabSelected: (Int) -> Unit,
    onTabReselected: ((Int) -> Unit)? = null
) {
    // playerUiState di-observe di sini — recompose hanya FloatingBottomControls
    val playerUiState by playerViewModel.uiState.collectAsStateWithLifecycle()

    // currentPage: update real-time saat swipe → navbar indikator langsung mengikuti
    // settledPage sudah dipakai di LaunchedEffect di MainTabScreen untuk onTabChanged
    val currentPage = pagerState.currentPage

    // Navbar slide down saat sheet expand — di graphicsLayer agar ZERO recompose
    // MiniPlayer juga tidak perlu ada saat ExpandablePlayerSheet aktif (sheet sudah punya mini)
    val usesSheet = playerSheetState != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .navigationBarsPadding()
            .padding(bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // MiniPlayerBar hanya ditampilkan saat TIDAK pakai ExpandablePlayerSheet
        // (sheet memiliki mini player sendiri di dalam)
        if (!usesSheet) {
            AnimatedVisibility(
                visible = playerUiState.currentSong != null,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(180)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(160))
            ) {
                MiniPlayerBar(
                    uiState = playerUiState,
                    onTogglePlayPause = { playerViewModel.togglePlayPause() },
                    onNavigateToPlayer = onNavigateToPlayer,
                    playbackTickFlow = playerViewModel.playbackTick
                )
            }
        }

        // iOS 26 Liquid Glass Navbar with backdrop blur, liquid lens, and AGSL shader
        LiquidGlassNavBar(
            selectedIndex = currentPage,
            onTabSelected = onTabSelected,
            onTabReselected = onTabReselected,
            hazeState = hazeState,
            pagerPosition = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
            isFrozen = { pagerState.isScrollInProgress },
            includeBottomPadding = false,
            sheetProgress = if (playerSheetState != null) { { playerSheetState.progress } } else null
        )
    }
}
