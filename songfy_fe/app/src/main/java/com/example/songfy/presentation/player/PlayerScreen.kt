package com.lagu.songfy.presentation.player

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Airplay
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import java.util.Locale
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lagu.songfy.data.remote.dto.SongDto
import com.lagu.songfy.data.remote.dto.toHighResThumbnail
import com.lagu.songfy.util.rememberAudioOutputDevice
import com.lagu.songfy.presentation.artist.ArtistViewModel
import com.lagu.songfy.presentation.theme.SongItemCardBackground
import com.lagu.songfy.util.DynamicColorExtractor
import com.lagu.songfy.util.collectSelector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// ROOT: PlayerScreen
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
    artistViewModel: ArtistViewModel = hiltViewModel(),
    onBackClick: () -> Unit = {},
    onArtistClick: (String) -> Unit = {}
) {
    val rootSong by viewModel.uiState.collectSelector(selector = { it.currentSong })
    val isLiked by viewModel.uiState.collectSelector(selector = { it.isLiked })
    val isPlaying by viewModel.uiState.collectSelector(selector = { it.isPlaying })
    val isShuffle by viewModel.uiState.collectSelector(selector = { it.isShuffle })
    val isRepeat by viewModel.uiState.collectSelector(selector = { it.isRepeat })
    val sleepTimer by viewModel.uiState.collectSelector(selector = { it.sleepTimerMinutes })
    val queue by viewModel.uiState.collectSelector(selector = { it.queue })
    val currentIndex by viewModel.uiState.collectSelector(selector = { it.currentIndex })
    val downloadedIds by viewModel.downloadedSongIds.collectAsStateWithLifecycle()
    val downloadingIds by viewModel.downloadingSongIds.collectAsStateWithLifecycle()
    val activeIndex by viewModel.lyricActiveIndex.collectAsStateWithLifecycle()
    val lyrics by viewModel.uiState.collectSelector(selector = { it.lyrics })
    val isSyncedLyrics by viewModel.uiState.collectSelector(selector = { it.isSyncedLyrics })
    val isLoadingLyrics by viewModel.uiState.collectSelector(selector = { it.isLoadingLyrics })
    val lyricOffsetMs by viewModel.uiState.collectSelector(selector = { it.lyricOffsetMs })
    val isTranslationActive by viewModel.uiState.collectSelector(selector = { it.isTranslationActive })
    val translatedLyrics by viewModel.uiState.collectSelector(selector = { it.translatedLyrics })
    val isTranslating by viewModel.uiState.collectSelector(selector = { it.isTranslating })
    val canTranslate by viewModel.uiState.collectSelector(selector = { it.canTranslate })

    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showAudioOutputSheet by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }

    val firstArtist = rootSong?.artists?.firstOrNull()
    val artistIdOrName = firstArtist?.id?.takeIf { it.isNotBlank() } ?: firstArtist?.name.orEmpty()

    LaunchedEffect(artistIdOrName) {
        if (artistIdOrName.isNotBlank()) {
            artistViewModel.loadArtist(artistIdOrName)
        }
    }

    // Back: first closes lyrics mode, then dismisses player
    BackHandler(enabled = showLyrics) {
        showLyrics = false
    }

    // Dynamic dominant color extraction (warm amber fallback matching screenshot)
    val context = LocalContext.current
    var dominantColor by remember { mutableStateOf(Color(0xFFB57016)) }

    LaunchedEffect(rootSong?.videoId) {
        val song = rootSong
        val url = song?.thumbnailUrl.toHighResThumbnail()
        if (song == null || url.isNullOrBlank()) return@LaunchedEffect
        val color = extractDominantColor(context, url)
        if (color != null && rootSong?.videoId == song.videoId) {
            dominantColor = color
        }
    }

    val animatedDominantColor by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "DominantBgColor"
    )

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFF0A0A0A)
    ) {
        AppleMusicNowPlayingScreen(
            currentSong = rootSong,
            isPlaying = isPlaying,
            isLiked = isLiked,
            isShuffle = isShuffle,
            isRepeat = isRepeat,
            dominantColor = animatedDominantColor,
            playbackTickFlow = viewModel.playbackTick,
            showLyrics = showLyrics,
            lyrics = lyrics,
            isSyncedLyrics = isSyncedLyrics,
            isLoadingLyrics = isLoadingLyrics,
            activeIndex = activeIndex,
            lyricOffsetMs = lyricOffsetMs,
            onAdjustLyricOffset = { viewModel.adjustLyricOffset(it) },
            onResetLyricOffset = { viewModel.resetLyricOffset() },
            canTranslate = canTranslate,
            isTranslationActive = isTranslationActive,
            translatedLyrics = translatedLyrics,
            isTranslating = isTranslating,
            onToggleTranslation = { viewModel.toggleTranslation() },
            onBackClick = onBackClick,
            onArtistClick = {
                if (artistIdOrName.isNotBlank()) onArtistClick(artistIdOrName)
            },
            onToggleLike = { viewModel.toggleLike() },
            onOpenMore = { showMoreSheet = true },
            onTogglePlayPause = { viewModel.togglePlayPause() },
            onSkipNext = { viewModel.skipNext() },
            onSkipPrevious = {
                val currentPos = viewModel.playbackTick.value.first
                if (currentPos > 3000L) {
                    viewModel.seekTo(0L)
                } else {
                    viewModel.skipPrevious()
                }
            },
            onSeekDelta = { deltaMs ->
                val currentPos = viewModel.playbackTick.value.first
                val dur = viewModel.uiState.value.durationMs
                viewModel.seekTo((currentPos + deltaMs).coerceIn(0L, dur))
            },
            onSeekFraction = { fraction ->
                viewModel.seekToFraction(fraction)
            },
            onSeekTo = { viewModel.seekTo(it) },
            onToggleLyrics = { showLyrics = !showLyrics },
            onOpenAudioOutput = { showAudioOutputSheet = true },
            onOpenQueue = { showQueueSheet = true }
        )

        // ── More Options Modal Bottom Sheet ──
        if (showMoreSheet && rootSong != null) {
            val song = rootSong!!
            val isDownloaded = downloadedIds.contains(song.videoId)
            val isDownloading = downloadingIds.contains(song.videoId)

            AppleMusicMoreSheet(
                song = song,
                sleepTimerMinutes = sleepTimer,
                isDownloaded = isDownloaded,
                isDownloading = isDownloading,
                onAddToPlaylist = {
                    showMoreSheet = false
                    showAddToPlaylist = true
                },
                onGoToAlbum = {
                    showMoreSheet = false
                },
                onGoToArtist = {
                    showMoreSheet = false
                    if (artistIdOrName.isNotBlank()) onArtistClick(artistIdOrName)
                },
                onShare = {
                    viewModel.shareSong()
                    showMoreSheet = false
                },
                onSetSleepTimer = { mins ->
                    viewModel.setSleepTimer(mins)
                },
                onDownload = {
                    if (isDownloaded) {
                        viewModel.deleteOfflineSong(song.videoId)
                    } else {
                        viewModel.downloadSong(song)
                    }
                    showMoreSheet = false
                },
                onDismiss = { showMoreSheet = false }
            )
        }

        // ── Add to Playlist Modal Sheet ──
        if (showAddToPlaylist) {
            AddToPlaylistSheet(
                song = rootSong,
                onDismiss = { showAddToPlaylist = false }
            )
        }

        // ── Audio Output Modal Bottom Sheet ──
        if (showAudioOutputSheet) {
            AppleMusicAudioOutputSheet(
                onDismiss = { showAudioOutputSheet = false }
            )
        }

        // ── Up Next Queue Modal Bottom Sheet ──
        if (showQueueSheet) {
            AppleMusicQueueSheet(
                currentSong = rootSong,
                currentIndex = currentIndex,
                queue = queue,
                isShuffle = isShuffle,
                isRepeat = isRepeat,
                onToggleShuffle = { viewModel.toggleShuffle() },
                onToggleRepeat = { viewModel.toggleRepeat() },
                onPlaySong = { s -> viewModel.setSong(s, queue) },
                onMoveQueueItem = { from, to -> viewModel.moveQueueItem(from, to) },
                onRemoveQueueItem = { id -> viewModel.removeQueueItem(id) },
                onDismiss = { showQueueSheet = false }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 1. Apple Music Now Playing Screen (In-Place Lyrics Mode)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AppleMusicNowPlayingScreen(
    currentSong: SongDto?,
    isPlaying: Boolean,
    isLiked: Boolean,
    isShuffle: Boolean,
    isRepeat: Boolean,
    dominantColor: Color,
    playbackTickFlow: StateFlow<Triple<Long, Long, Boolean>>,
    showLyrics: Boolean,
    lyrics: List<LyricLine>,
    isSyncedLyrics: Boolean,
    isLoadingLyrics: Boolean,
    activeIndex: Int,
    lyricOffsetMs: Long = 0L,
    onAdjustLyricOffset: (Long) -> Unit = {},
    onResetLyricOffset: () -> Unit = {},
    canTranslate: Boolean = false,
    isTranslationActive: Boolean = false,
    translatedLyrics: Map<Int, String> = emptyMap(),
    isTranslating: Boolean = false,
    onToggleTranslation: () -> Unit = {},
    onBackClick: () -> Unit,
    onArtistClick: () -> Unit,
    onToggleLike: () -> Unit,
    onOpenMore: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekDelta: (Long) -> Unit,
    onSeekFraction: (Float) -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleLyrics: () -> Unit,
    onOpenAudioOutput: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Single progress value drives the entire transition
    val lyricsProgress by animateFloatAsState(
        targetValue = if (showLyrics) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "LyricsProgress"
    )
    // Vivid accent color (boosted saturation+brightness for active lyrics glow)
    val accentColor = remember(dominantColor) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(dominantColor.toArgb(), hsv)
        hsv[1] = (hsv[1] * 1.25f).coerceIn(0.65f, 1.0f)
        hsv[2] = 1.0f
        Color(android.graphics.Color.HSVToColor(hsv))
    }

    // Top gradient color: dominant color darkened ~25-30%
    val topGradientColor = remember(dominantColor) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(dominantColor.toArgb(), hsv)
        hsv[1] = (hsv[1] * 0.95f).coerceIn(0.50f, 0.95f)
        hsv[2] = (hsv[2] * 0.72f).coerceIn(0.30f, 0.65f)
        Color(android.graphics.Color.HSVToColor(hsv))
    }

    // Bottom gradient color: deeper amber (~60% darker)
    val bottomGradientColor = remember(dominantColor) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(dominantColor.toArgb(), hsv)
        hsv[1] = (hsv[1] * 0.92f).coerceIn(0.45f, 0.92f)
        hsv[2] = (hsv[2] * 0.38f).coerceIn(0.14f, 0.32f)
        Color(android.graphics.Color.HSVToColor(hsv))
    }

    // In lyrics mode, darken the background toward near-black
    val lyricsTopColor = remember(dominantColor) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(dominantColor.toArgb(), hsv)
        hsv[1] = (hsv[1] * 0.35f).coerceIn(0.10f, 0.45f)
        hsv[2] = (hsv[2] * 0.18f).coerceIn(0.05f, 0.20f)
        Color(android.graphics.Color.HSVToColor(hsv))
    }

    val effectiveTop = Color(
        red = topGradientColor.red + (lyricsTopColor.red - topGradientColor.red) * lyricsProgress,
        green = topGradientColor.green + (lyricsTopColor.green - topGradientColor.green) * lyricsProgress,
        blue = topGradientColor.blue + (lyricsTopColor.blue - topGradientColor.blue) * lyricsProgress
    )
    val effectiveBottom = run {
        val nearBlack = Color(0xFF121212)
        Color(
            red = bottomGradientColor.red + (nearBlack.red - bottomGradientColor.red) * lyricsProgress,
            green = bottomGradientColor.green + (nearBlack.green - bottomGradientColor.green) * lyricsProgress,
            blue = bottomGradientColor.blue + (nearBlack.blue - bottomGradientColor.blue) * lyricsProgress
        )
    }

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(effectiveTop, effectiveBottom)
    )

    // Drag-down to dismiss gesture (disabled in lyrics mode)
    var dismissOffsetY by remember { mutableFloatStateOf(0f) }
    val animatedDismissOffsetY by animateFloatAsState(
        targetValue = dismissOffsetY,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "DismissOffsetY"
    )

    // Lyrics options sheet state
    var showLyricsOptions by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(brush = gradientBrush)
            }
            .graphicsLayer {
                translationY = if (showLyrics) 0f else animatedDismissOffsetY
                alpha = if (showLyrics) 1f else (1f - (animatedDismissOffsetY / 600f)).coerceIn(0.1f, 1f)
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── 1. Top Handle ──
            AppleMusicTopHandle(
                onDismiss = if (showLyrics) ({ /* consume, back handler closes lyrics */ }) else onBackClick,
                onDragOffset = { offset ->
                    if (!showLyrics) dismissOffsetY = offset
                }
            )

            // ── 2. Upper Area: Artwork (normal) ↔ CompactHeader + LyricsPane (lyrics mode) ──
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // NORMAL MODE: large Artwork
                if (lyricsProgress < 0.99f) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 4.dp, bottom = 12.dp)
                            .graphicsLayer {
                                alpha = (1f - lyricsProgress * 2f).coerceIn(0f, 1f)
                                scaleX = 1f - lyricsProgress * 0.12f
                                scaleY = 1f - lyricsProgress * 0.12f
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val artSize = minOf(maxWidth, maxHeight)
                        AppleMusicArtwork(
                            currentSong = currentSong,
                            isPlaying = isPlaying,
                            dominantColor = dominantColor,
                            modifier = Modifier.size(artSize)
                        )
                    }
                }

                // LYRICS MODE: CompactHeader + LyricsPane
                if (lyricsProgress > 0.01f) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = ((lyricsProgress - 0.3f) / 0.7f).coerceIn(0f, 1f)
                            }
                    ) {
                        // Compact Header
                        LyricsCompactHeader(
                            currentSong = currentSong,
                            accentColor = accentColor,
                            onOpenMore = onOpenMore
                        )

                        Spacer(Modifier.height(12.dp))

                        // Lyrics Pane (fills remaining space)
                        LyricsPane(
                            lyrics = lyrics,
                            isSyncedLyrics = isSyncedLyrics,
                            isLoadingLyrics = isLoadingLyrics,
                            activeIndex = activeIndex,
                            accentColor = accentColor,
                            effectiveTop = effectiveTop,
                            effectiveBottom = effectiveBottom,
                            lyricOffsetMs = lyricOffsetMs,
                            onAdjustLyricOffset = onAdjustLyricOffset,
                            onResetLyricOffset = onResetLyricOffset,
                            onSeekTo = onSeekTo,
                            canTranslate = canTranslate,
                            isTranslationActive = isTranslationActive,
                            translatedLyrics = translatedLyrics,
                            isTranslating = isTranslating,
                            onToggleTranslation = onToggleTranslation,
                            showLyrics = showLyrics,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ── 3. Track Info Row (fades out in lyrics mode) ──
            AnimatedVisibility(
                visible = !showLyrics,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(150))
            ) {
                Column {
                    AppleMusicTrackInfoRow(
                        currentSong = currentSong,
                        isLiked = isLiked,
                        dominantColor = dominantColor,
                        onArtistClick = onArtistClick,
                        onToggleLike = onToggleLike,
                        onOpenMore = onOpenMore
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }

            // ── 4. Progress Scrub Bar (stays in place, always visible) ──
            AppleMusicProgressBar(
                playbackTickFlow = playbackTickFlow,
                song = currentSong,
                onSeekFraction = onSeekFraction
            )

            Spacer(Modifier.height(18.dp))

            // ── 5. Main Playback Controls ──
            AppleMusicMainControls(
                isPlaying = isPlaying,
                onTogglePlayPause = onTogglePlayPause,
                onSkipPrevious = onSkipPrevious,
                onSkipNext = onSkipNext,
                onSeekDelta = onSeekDelta
            )

            Spacer(Modifier.height(18.dp))

            // ── 6. Volume Row ──
            AppleMusicVolumeRow()

            Spacer(Modifier.height(24.dp))

            // ── 7. Bottom Action Row ──
            AppleMusicBottomActionRow(
                showLyrics = showLyrics,
                isShuffle = isShuffle,
                isRepeat = isRepeat,
                accentColor = accentColor,
                onToggleLyrics = onToggleLyrics,
                onOpenAudioOutput = onOpenAudioOutput,
                onOpenQueue = onOpenQueue
            )

            Spacer(Modifier.height(10.dp))
        }
    }

    // Lyrics Options Bottom Sheet
    if (showLyricsOptions) {
        LyricsOptionsSheet(onDismiss = { showLyricsOptions = false })
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. TOP HANDLE (36x4dp, White 40%, drag to dismiss)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AppleMusicTopHandle(
    onDismiss: () -> Unit,
    onDragOffset: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var accumulatedDragY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = {
                        accumulatedDragY = 0f
                    },
                    onDragEnd = {
                        if (accumulatedDragY > 140f) {
                            onDismiss()
                        }
                        accumulatedDragY = 0f
                        onDragOffset(0f)
                    },
                    onDragCancel = {
                        accumulatedDragY = 0f
                        onDragOffset(0f)
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDragY = (accumulatedDragY + dragAmount).coerceAtLeast(0f)
                        onDragOffset(accumulatedDragY)
                    }
                )
            }
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.40f))
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. ARTWORK (Square, 10dp radius, spring scale 1.0 <-> 0.82, tinted soft shadow)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AppleMusicArtwork(
    currentSong: SongDto?,
    isPlaying: Boolean,
    dominantColor: Color,
    modifier: Modifier = Modifier
) {
    // Spring physics matching iOS Apple Music
    val artworkScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.82f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "AppleArtworkScale"
    )

    val artworkTranslationY by animateFloatAsState(
        targetValue = if (isPlaying) 0f else 18f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "AppleArtworkTranslationY"
    )

    val shadowAlpha by animateFloatAsState(
        targetValue = if (isPlaying) 0.65f else 0.25f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "ArtworkShadowAlpha"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = artworkScale
                    scaleY = artworkScale
                    translationY = artworkTranslationY
                }
                .drawBehind {
                    // Soft large tinted glow / shadow behind the artwork
                    val glowRadius = size.width * (if (isPlaying) 0.62f else 0.48f)
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                dominantColor.copy(alpha = shadowAlpha),
                                dominantColor.copy(alpha = shadowAlpha * 0.35f),
                                Color.Transparent
                            ),
                            center = Offset(size.width / 2f, size.height / 2f + 16.dp.toPx()),
                            radius = glowRadius
                        ),
                        topLeft = Offset(-20.dp.toPx(), -8.dp.toPx()),
                        size = Size(size.width + 40.dp.toPx(), size.height + 48.dp.toPx()),
                        cornerRadius = CornerRadius(24.dp.toPx())
                    )
                }
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.30f)),
            contentAlignment = Alignment.Center
        ) {
            // Track change transition: crossfade + subtle 0.96 -> 1 scale-in
            AnimatedContent(
                targetState = (currentSong?.videoId ?: "") to (currentSong?.thumbnailUrl.toHighResThumbnail() ?: ""),
                transitionSpec = {
                    (fadeIn(animationSpec = tween(400, easing = LinearEasing)) +
                     scaleIn(initialScale = 0.96f, animationSpec = tween(400, easing = FastOutSlowInEasing)))
                        .togetherWith(
                            fadeOut(animationSpec = tween(300, easing = LinearEasing))
                        )
                },
                label = "CoverTrackCrossfade"
            ) { (_, url) ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(url)
                        .crossfade(true)
                        .build(),
                    contentDescription = currentSong?.title ?: "Cover Lagu",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. TRACK INFO ROW (Title marquee + Artist | Star + More buttons)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AppleMusicTrackInfoRow(
    currentSong: SongDto?,
    isLiked: Boolean,
    dominantColor: Color,
    onArtistClick: () -> Unit,
    onToggleLike: () -> Unit,
    onOpenMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    // Artist text tint: lighter tint of dominant color matching screenshot
    val artistTint = remember(dominantColor) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(dominantColor.toArgb(), hsv)
        hsv[1] = (hsv[1] * 0.70f).coerceIn(0.25f, 0.65f)
        hsv[2] = (hsv[2] * 1.35f).coerceIn(0.75f, 0.98f)
        Color(android.graphics.Color.HSVToColor(hsv))
    }

    // Star bounce animation
    var starBounced by remember { mutableStateOf(false) }
    val starScale by animateFloatAsState(
        targetValue = if (starBounced) 1.35f else 1.0f,
        animationSpec = spring(dampingRatio = 0.38f, stiffness = Spring.StiffnessMedium),
        finishedListener = { starBounced = false },
        label = "StarBounceAnim"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Song Title + Artist
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = currentSong?.title ?: "Tidak ada lagu",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .basicMarquee(iterations = Int.MAX_VALUE)
            )

            Spacer(Modifier.height(2.dp))

            val artistText = currentSong?.artists?.joinToString(", ") { it.name }
                .takeIf { !it.isNullOrBlank() } ?: "Artis Tidak Diketahui"
            Text(
                text = artistText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = artistTint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onArtistClick
                    )
            )
        }

        // Right: Star Favorite Button + Three Dots More Button
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Star Button (32dp circle, White 20% bg, bounce animation, persisted)
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.20f))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        starBounced = true
                        onToggleLike()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (isLiked) "Hapus dari Favorit" else "Tambah ke Favorit",
                    tint = if (isLiked) Color.White else Color.White.copy(alpha = 0.85f),
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer {
                            scaleX = starScale
                            scaleY = starScale
                        }
                )
            }

            // More Button (32dp circle, White 20% bg, three dots)
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.20f))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenMore()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = "Opsi Lainnya",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 5. PROGRESS BAR (Apple Music scrub bar + Dolby/Lossless badge + Negative time)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AppleMusicProgressBar(
    playbackTickFlow: StateFlow<Triple<Long, Long, Boolean>>,
    song: SongDto?,
    onSeekFraction: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val tick by playbackTickFlow.collectAsStateWithLifecycle()
    val positionMs = tick.first
    val durationMs = tick.second

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val currentProgress = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else 0f

    val activeFraction = if (isDragging) dragFraction else currentProgress
    val displayPosition = if (isDragging && durationMs > 0) {
        (dragFraction * durationMs).toLong()
    } else {
        positionMs
    }

    val remainingMs = (durationMs - displayPosition).coerceAtLeast(0L)

    // Bar expands from 4dp to 10dp while dragging
    val barHeight by animateDpAsState(
        targetValue = if (isDragging) 10.dp else 4.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "BarHeightAnim"
    )

    val isDolby = remember(song) {
        val t = song?.title?.lowercase().orEmpty()
        val a = song?.album?.lowercase().orEmpty()
        t.contains("dolby") || t.contains("atmos") || a.contains("dolby") || a.contains("atmos")
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp)
    ) {
        // Scrub touch area: 36dp hit area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .pointerInput(durationMs) {
                    if (durationMs <= 0) return@pointerInput
                    detectTapGestures { offset ->
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSeekFraction(fraction)
                    }
                }
                .pointerInput(durationMs) {
                    if (durationMs <= 0) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onDragEnd = {
                            onSeekFraction(dragFraction)
                            isDragging = false
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
            ) {
                val h = barHeight.toPx()
                val cy = size.height / 2f
                val totalWidth = size.width
                val activeWidth = totalWidth * activeFraction

                // Inactive Track: White 30%
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.30f),
                    topLeft = Offset(0f, cy - h / 2f),
                    size = Size(totalWidth, h),
                    cornerRadius = CornerRadius(h / 2f)
                )

                // Active Fill: Solid White
                if (activeWidth > 0f) {
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(0f, cy - h / 2f),
                        size = Size(activeWidth, h),
                        cornerRadius = CornerRadius(h / 2f)
                    )
                }

                // Thumb: only visible while dragging
                if (isDragging) {
                    val thumbRadius = 7.dp.toPx()
                    drawCircle(
                        color = Color.White,
                        radius = thumbRadius,
                        center = Offset(activeWidth.coerceIn(thumbRadius, totalWidth - thumbRadius), cy)
                    )
                }
            }
        }

        // Timestamps + Format Badge Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Elapsed Time
            Text(
                text = formatMillis(displayPosition),
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = Color.White.copy(alpha = 0.60f)
            )

            // Format Badge in center (Dolby Atmos or Lossless)
            AppleMusicFormatBadge(isDolby = isDolby)

            // Remaining Time (negative format: -3:08)
            Text(
                text = "-${formatMillis(remainingMs)}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = Color.White.copy(alpha = 0.60f)
            )
        }
    }
}

// Format Badge (Center badge under progress bar)
@Composable
private fun AppleMusicFormatBadge(isDolby: Boolean) {
    if (isDolby) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Dolby Double-D icon glyph
            Canvas(modifier = Modifier.size(width = 12.dp, height = 9.dp)) {
                val w = size.width
                val h = size.height
                // Left D
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.65f),
                    topLeft = Offset(0f, 0f),
                    size = Size(w * 0.45f, h),
                    cornerRadius = CornerRadius(1.5f.dp.toPx())
                )
                // Right D
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.65f),
                    topLeft = Offset(w * 0.55f, 0f),
                    size = Size(w * 0.45f, h),
                    cornerRadius = CornerRadius(1.5f.dp.toPx())
                )
            }
            Text(
                text = "Dolby Atmos",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.65f)
            )
        }
    } else {
        Box(
            modifier = Modifier
                .border(
                    width = 0.8.dp,
                    color = Color.White.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(3.dp)
                )
                .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            Text(
                text = "Lossless",
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.70f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 6. MAIN PLAYBACK CONTROLS (Centered, large, pure white glyphs, no circles)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AppleMusicMainControls(
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSeekDelta: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rewind Button (double-chevron-left ~40dp): tap = prev / restart, long-press = fast rewind
        AppleMusicRewindForwardButton(
            isForward = false,
            onTap = onSkipPrevious,
            onSeekDelta = onSeekDelta
        )

        // Center Play / Pause Button (~56dp white glyph with icon morph)
        var playPressed by remember { mutableStateOf(false) }
        val playScale by animateFloatAsState(
            targetValue = if (playPressed) 0.88f else 1.0f,
            animationSpec = spring(stiffness = Spring.StiffnessMedium),
            label = "PlayBtnScale"
        )

        Box(
            modifier = Modifier
                .size(68.dp)
                .graphicsLayer {
                    scaleX = playScale
                    scaleY = playScale
                }
                .drawBehind {
                    if (playPressed) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.18f),
                            radius = size.maxDimension * 0.55f
                        )
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            playPressed = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            tryAwaitRelease()
                            playPressed = false
                        },
                        onTap = {
                            onTogglePlayPause()
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = isPlaying,
                transitionSpec = {
                    (scaleIn(initialScale = 0.7f, animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                     fadeIn(animationSpec = tween(150)))
                        .togetherWith(
                            scaleOut(targetScale = 0.7f, animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                            fadeOut(animationSpec = tween(150))
                        )
                },
                label = "PlayPauseMorphGlyph"
            ) { playing ->
                Icon(
                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "Jeda" else "Putar",
                    tint = Color.White,
                    modifier = Modifier.size(56.dp)
                )
            }
        }

        // Forward Button (double-chevron-right ~40dp): tap = next, long-press = fast forward
        AppleMusicRewindForwardButton(
            isForward = true,
            onTap = onSkipNext,
            onSeekDelta = onSeekDelta
        )
    }
}

// Reusable Rewind / Forward button with tap + continuous long press seek
@Composable
private fun AppleMusicRewindForwardButton(
    isForward: Boolean,
    onTap: () -> Unit,
    onSeekDelta: (Long) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    var isPressed by remember { mutableStateOf(false) }
    var lastTapTime by remember { mutableStateOf(0L) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "RewindForwardScale"
    )

    Box(
        modifier = Modifier
            .size(52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                if (isPressed) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.16f),
                        radius = size.maxDimension * 0.50f
                    )
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val job = coroutineScope.launch {
                            delay(400) // threshold for long-press
                            while (isActive) {
                                onSeekDelta(if (isForward) 5000L else -5000L)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                delay(320)
                            }
                        }
                        tryAwaitRelease()
                        job.cancel()
                        isPressed = false
                    },
                    onTap = {
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime >= 400L) {
                            lastTapTime = now
                            onTap()
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isForward) Icons.Default.FastForward else Icons.Default.FastRewind,
            contentDescription = if (isForward) "Lagu Berikutnya / Maju Cepat" else "Lagu Sebelumnya / Mundur Cepat",
            tint = Color.White,
            modifier = Modifier.size(40.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 7. VOLUME ROW (Speaker-low | Slider | Speaker-high bound to AudioManager)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AppleMusicVolumeRow(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val maxVolume = remember {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }
    var currentVolume by remember {
        mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
    }

    // Hardware volume change listener
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                    currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                }
            }
        }
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        context.registerReceiver(receiver, filter)
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    val volumeFraction = (currentVolume.toFloat() / maxVolume).coerceIn(0f, 1f)
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val activeFraction = if (isDragging) dragFraction else volumeFraction

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Speaker-low icon
        Icon(
            imageVector = Icons.AutoMirrored.Filled.VolumeDown,
            contentDescription = "Volume Rendah",
            tint = Color.White.copy(alpha = 0.70f),
            modifier = Modifier
                .size(20.dp)
                .clickable {
                    currentVolume = 0
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                }
        )

        Spacer(Modifier.width(12.dp))

        // Volume Slider (Matching progress bar aesthetic)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(32.dp)
                .pointerInput(maxVolume) {
                    detectTapGestures { offset ->
                        val f = (offset.x / size.width).coerceIn(0f, 1f)
                        val targetVol = (f * maxVolume).roundToInt().coerceIn(0, maxVolume)
                        currentVolume = targetVol
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                    }
                }
                .pointerInput(maxVolume) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            val targetVol = (dragFraction * maxVolume).roundToInt().coerceIn(0, maxVolume)
                            currentVolume = targetVol
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                            isDragging = false
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            val targetVol = (dragFraction * maxVolume).roundToInt().coerceIn(0, maxVolume)
                            currentVolume = targetVol
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
            ) {
                val h = 4.dp.toPx()
                val cy = size.height / 2f
                val totalWidth = size.width
                val activeWidth = totalWidth * activeFraction

                // Inactive Track: White 30%
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.30f),
                    topLeft = Offset(0f, cy - h / 2f),
                    size = Size(totalWidth, h),
                    cornerRadius = CornerRadius(h / 2f)
                )

                // Active Track: White
                if (activeWidth > 0f) {
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(0f, cy - h / 2f),
                        size = Size(activeWidth, h),
                        cornerRadius = CornerRadius(h / 2f)
                    )
                }

                // Small resting/dragging white thumb
                val thumbRadius = if (isDragging) 6.dp.toPx() else 4.dp.toPx()
                drawCircle(
                    color = Color.White,
                    radius = thumbRadius,
                    center = Offset(activeWidth.coerceIn(thumbRadius, totalWidth - thumbRadius), cy)
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // Speaker-high icon
        Icon(
            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = "Volume Tinggi",
            tint = Color.White.copy(alpha = 0.70f),
            modifier = Modifier
                .size(20.dp)
                .clickable {
                    currentVolume = maxVolume
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
                }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 8. BOTTOM ACTION ROW (Lyrics | Audio Output | Queue)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AppleMusicBottomActionRow(
    showLyrics: Boolean,
    isShuffle: Boolean,
    isRepeat: Boolean,
    accentColor: Color,
    onToggleLyrics: () -> Unit,
    onOpenAudioOutput: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val audioDevice = rememberAudioOutputDevice()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Left: Lyrics toggle button — icon only, no bubble
        Box(
            modifier = Modifier
                .size(44.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggleLyrics
                ),
            contentAlignment = Alignment.Center
        ) {
            AppleMusicLyricsIcon(
                tint = if (showLyrics) Color.White else Color.White.copy(alpha = 0.70f),
                modifier = Modifier.size(22.dp)
            )
        }

        // 2. Center: Dynamic Audio Output (Headphones vs Smartphone)
        IconButton(
            onClick = onOpenAudioOutput,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = if (audioDevice.isHeadphones) Icons.Default.Headphones else Icons.Default.Smartphone,
                contentDescription = if (audioDevice.isHeadphones) "Earphone Terhubung" else "Speaker Ponsel",
                tint = if (audioDevice.isHeadphones) Color(0xFF1ED760) else Color.White.copy(alpha = 0.70f),
                modifier = Modifier.size(24.dp)
            )
        }

        // 3. Right: Queue Icon with accent badge if shuffle/repeat active
        Box(contentAlignment = Alignment.TopEnd) {
            IconButton(
                onClick = onOpenQueue,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                    contentDescription = "Antrean Lagu",
                    tint = Color.White.copy(alpha = 0.70f),
                    modifier = Modifier.size(24.dp)
                )
            }
            if (isShuffle || isRepeat) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .offset(x = (-6).dp, y = 10.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
            }
        }
    }
}

// Custom Apple Music Speech Bubble with Quotes Icon
@Composable
private fun AppleMusicLyricsIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Speech bubble rounded rect
        drawRoundRect(
            color = tint,
            topLeft = Offset(0f, 0f),
            size = Size(w, h * 0.82f),
            cornerRadius = CornerRadius(4.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.8.dp.toPx())
        )

        // Little speech tail
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.22f, h * 0.75f),
            size = Size(w * 0.20f, h * 0.25f),
            cornerRadius = CornerRadius(1.5f.dp.toPx())
        )

        // Two quotation mark dots/slits
        drawCircle(
            color = tint,
            radius = 1.4.dp.toPx(),
            center = Offset(w * 0.38f, h * 0.42f)
        )
        drawCircle(
            color = tint,
            radius = 1.4.dp.toPx(),
            center = Offset(w * 0.62f, h * 0.42f)
        )
    }
}

// Custom Apple Music AirPlay / Audio Output Broadcast Icon
@Composable
private fun AppleMusicAirPlayIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Upward solid triangle (AirPlay speaker source)
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w / 2f, h * 0.48f)
            lineTo(w * 0.72f, h * 0.86f)
            lineTo(w * 0.28f, h * 0.86f)
            close()
        }
        drawPath(path = path, color = tint)

        // Outer concentric broadcast arch
        drawArc(
            color = tint,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(w * 0.10f, h * 0.08f),
            size = Size(w * 0.80f, h * 0.75f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.8.dp.toPx())
        )

        // Inner broadcast arch
        drawArc(
            color = tint,
            startAngle = 210f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = Offset(w * 0.24f, h * 0.22f),
            size = Size(w * 0.52f, h * 0.52f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.8.dp.toPx())
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LYRICS COMPACT HEADER (shown when lyrics mode is active)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun LyricsCompactHeader(
    currentSong: SongDto?,
    accentColor: Color,
    onOpenMore: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 56dp thumbnail
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(currentSong?.thumbnailUrl.toHighResThumbnail())
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(6.dp))
        )

        Spacer(Modifier.width(12.dp))

        // Title + Artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = currentSong?.title ?: "Memutar Musik",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = currentSong?.artists?.joinToString(", ") { it.name } ?: "",
                fontSize = 14.sp,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(8.dp))

        // More button (32dp circle)
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f))
                .clickable(onClick = onOpenMore),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MoreHoriz,
                contentDescription = "Opsi",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ─────────────────────────────────────────────────────────────────────────────
// LYRICS PANE (LazyColumn + top/bottom fade overlay + iOS spring auto-scroll)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun LyricsPane(
    lyrics: List<LyricLine>,
    isSyncedLyrics: Boolean,
    isLoadingLyrics: Boolean,
    activeIndex: Int,
    accentColor: Color,
    effectiveTop: Color,
    effectiveBottom: Color,
    lyricOffsetMs: Long = 0L,
    onAdjustLyricOffset: (Long) -> Unit = {},
    onResetLyricOffset: () -> Unit = {},
    onSeekTo: (Long) -> Unit,
    canTranslate: Boolean = false,
    isTranslationActive: Boolean = false,
    translatedLyrics: Map<Int, String> = emptyMap(),
    isTranslating: Boolean = false,
    onToggleTranslation: () -> Unit = {},
    showLyrics: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Inisialisasi posisi awal LazyList langsung ke baris aktif bila sudah tersedia
    val initialSnapIndex = remember {
        if (lyrics.isNotEmpty() && activeIndex in lyrics.indices) {
            (activeIndex - 1).coerceAtLeast(0)
        } else 0
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialSnapIndex)
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Detect ONLY actual user dragging (not programmatic scroll)
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    var userInteractedAt by remember { mutableStateOf(0L) }

    LaunchedEffect(isDragged) {
        if (isDragged) {
            userInteractedAt = System.currentTimeMillis()
        }
    }

    // Saat panel lirik dibuka atau data lirik baru masuk:
    // Langsung INSTANT SNAP (scrollToItem) ke baris vokal yang sedang diputar.
    // Tidak ada animasi scroll panjang dari awal lirik ("jangan ke scroll dulu").
    var hasInitialSnapped by remember(lyrics) { mutableStateOf(false) }
    LaunchedEffect(showLyrics, lyrics, activeIndex) {
        if (showLyrics && lyrics.isNotEmpty() && isSyncedLyrics && !hasInitialSnapped) {
            if (activeIndex in lyrics.indices) {
                val snapTarget = (activeIndex - 1).coerceAtLeast(0)
                try {
                    listState.scrollToItem(snapTarget)
                    hasInitialSnapped = true
                } catch (_: Exception) {}
            }
        }
    }

    // Auto-scroll saat lagu berjalan: smooth spring glide ke ~32% viewport
    LaunchedEffect(activeIndex, isSyncedLyrics) {
        if (lyrics.isEmpty() || !isSyncedLyrics || activeIndex !in lyrics.indices) return@LaunchedEffect
        val now = System.currentTimeMillis()
        // If user manually touched recently, pause auto-scroll for 3.5s
        if (now - userInteractedAt < 3500L) return@LaunchedEffect

        val viewportHeight = listState.layoutInfo.viewportSize.height
        val targetY = if (viewportHeight > 0) (viewportHeight * 0.32f).toInt() else 0

        val item = listState.layoutInfo.visibleItemsInfo.find { it.index == activeIndex }
        if (item != null) {
            // Baris vokal terlihat di layar -> glide halus dengan spring
            val delta = (item.offset - targetY).toFloat()
            if (kotlin.math.abs(delta) > 4f) {
                try {
                    listState.animateScrollBy(
                        value = delta,
                        animationSpec = spring(
                            dampingRatio = 0.85f,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            }
        } else {
            // Baris belum terlihat di viewport (misal baru buka atau baru seek) ->
            // LANGSUNG SNAP tanpa animasi scroll panjang lewat puluhan baris
            try {
                val snapTarget = (activeIndex - 1).coerceAtLeast(0)
                listState.scrollToItem(snapTarget)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        if (isLoadingLyrics && lyrics.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = accentColor,
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 2.5.dp
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Memuat lirik...",
                        fontSize = 15.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        } else if (lyrics.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Lirik belum tersedia",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.80f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Lyrics list: native GPU composition without expensive Offscreen FBO allocations
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 44.dp, bottom = 90.dp)
            ) {
                itemsIndexed(
                    lyrics,
                    key = { index, line -> "${index}_${line.timeMs}" }
                ) { index, line ->
                    val isLineActive = index == activeIndex && isSyncedLyrics
                    LyricLineItem(
                        line = line,
                        isActive = isLineActive,
                        accentColor = accentColor,
                        isTranslationActive = isTranslationActive,
                        translatedText = translatedLyrics[index],
                        onTap = {
                            if (isSyncedLyrics) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                userInteractedAt = 0L // reset drag lock immediately
                                val targetSeekPos = (line.timeMs - lyricOffsetMs).coerceAtLeast(0L)
                                onSeekTo(targetSeekPos)
                                // Immediate spring glide to 32% optical center
                                scope.launch {
                                    val viewportHeight = listState.layoutInfo.viewportSize.height
                                    val targetY = if (viewportHeight > 0) (viewportHeight * 0.32f).toInt() else 0
                                    val targetItem = listState.layoutInfo.visibleItemsInfo.find { it.index == index }
                                    if (targetItem != null) {
                                        val delta = (targetItem.offset - targetY).toFloat()
                                        listState.animateScrollBy(
                                            value = delta,
                                            animationSpec = spring(
                                                dampingRatio = 0.85f,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }

            // Top soft edge fade overlay (hardware accelerated, non-blocking)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(effectiveTop, Color.Transparent)
                        )
                    )
            )

            // Bottom soft edge fade overlay (hardware accelerated, non-blocking)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, effectiveBottom.copy(alpha = 0.96f))
                        )
                    )
            )

            // Floating Translate button (bottom-right)
            // HANYA muncul untuk lagu yang bahasanya BUKAN Indonesia dan BUKAN Inggris (canTranslate)
            if (canTranslate) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 84.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (isTranslationActive) accentColor.copy(alpha = 0.32f)
                            else Color.Black.copy(alpha = 0.55f)
                        )
                        .border(
                            width = if (isTranslationActive) 1.5.dp else 1.dp,
                            color = if (isTranslationActive) accentColor else Color.White.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(18.dp)
                        )
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleTranslation()
                        }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isTranslating) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp),
                                color = accentColor
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = "Terjemahkan ke Bahasa Indonesia",
                                tint = if (isTranslationActive) accentColor else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = "ID",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isTranslationActive) accentColor else Color.White.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LYRIC LINE ITEM (iOS Apple Music style: 1.04x scale & accent glow for active)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun LyricLineItem(
    line: LyricLine,
    isActive: Boolean,
    accentColor: Color,
    isTranslationActive: Boolean = false,
    translatedText: String? = null,
    onTap: () -> Unit
) {
    if (line.isInstrumental) {
        // Baris instrumental dilewati — tidak tampilkan titik-titik
        return
    }

    // Smooth spring scale for active vs inactive lines
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.04f else 0.96f,
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "LyricScale"
    )

    // Smooth spring color transition
    val color by animateColorAsState(
        targetValue = if (isActive) accentColor else Color.White.copy(alpha = 0.38f),
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "LyricColor"
    )

    val translationColor by animateColorAsState(
        targetValue = if (isActive) accentColor.copy(alpha = 0.82f) else Color.White.copy(alpha = 0.28f),
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "TranslationColor"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap
            )
    ) {
        // Baris lirik asli
        Text(
            text = line.text,
            fontSize = 28.sp,
            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
            color = color,
            lineHeight = 38.sp
        )

        // Terjemahan Bahasa Indonesia di bawah lirik asli
        if (isTranslationActive && !translatedText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = translatedText,
                fontSize = if (isActive) 18.sp else 16.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                color = translationColor,
                lineHeight = 24.sp
            )
        }
    }
}

// Three animated pulsing dots for instrumental breaks (animates only when active)
@Composable
private fun InstrumentalDots(accentColor: Color, isActive: Boolean) {
    if (!isActive) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                )
            }
        }
        return
    }

    val transition = rememberInfiniteTransition(label = "InstrumentalPulse")
    val pulse1 by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            tween(600, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "Dot1"
    )
    val pulse2 by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            tween(600, delayMillis = 200, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "Dot2"
    )
    val pulse3 by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            tween(600, delayMillis = 400, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "Dot3"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        listOf(pulse1, pulse2, pulse3).forEach { pulse ->
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = pulse))
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LYRICS OPTIONS BOTTOM SHEET
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LyricsOptionsSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1E1E26)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Opsi Lirik",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.height(16.dp))

            // Text size options
            Text(
                text = "Ukuran Teks",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.60f),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Kecil" to 22f, "Sedang" to 28f, "Besar" to 34f).forEach { (label, _) ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable { /* persist via LyricsPreferences */ }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(label, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Keep screen on
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Jaga layar menyala", fontSize = 15.sp, color = Color.White)
                Box(
                    modifier = Modifier
                        .size(width = 44.dp, height = 26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Color(0xFF5AC8FA))
                        .clickable {
                            val activity = context as? Activity
                            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                )
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AUDIO OUTPUT BOTTOM SHEET (AirPlay Style Device Picker)
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppleMusicAudioOutputSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val audioDeviceState = rememberAudioOutputDevice()

    val devices = remember(audioDeviceState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val list = audioDevices.map { dev ->
                val name = dev.productName?.toString().takeIf { !it.isNullOrBlank() }
                    ?: when (dev.type) {
                        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> "Speaker Ponsel"
                        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Headphone Kabel"
                        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Perangkat Bluetooth"
                        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "Audio USB"
                        else -> "Perangkat Eksternal"
                    }
                val isHeadphone = dev.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                                  dev.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                                  dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                                  dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                                  dev.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                                  dev.type == AudioDeviceInfo.TYPE_USB_HEADSET
                val icon = when (dev.type) {
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> Icons.Default.Smartphone
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> Icons.Default.Headphones
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> Icons.Default.Bluetooth
                    else -> if (isHeadphone) Icons.Default.Headphones else Icons.Default.Speaker
                }
                val isExternal = dev.type != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER && dev.type != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE
                Triple(name, icon, isHeadphone)
            }.distinctBy { it.first }

            // Put active output device at the top
            list.sortedByDescending { if (audioDeviceState.isHeadphones) it.third else !it.third }
        } else {
            listOf(Triple("Speaker Ponsel", Icons.Default.Smartphone, false))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1E1E26)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Output Audio",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color.White.copy(alpha = 0.7f))
                }
            }

            Spacer(Modifier.height(14.dp))

            // Devices list
            devices.forEachIndexed { index, (name, icon, isExternal) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }

                    // Checkmark on first/active device
                    if (index == 0) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Aktif",
                            tint = Color(0xFF1ED760),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            // Quick Bluetooth & sound settings launcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                    .clickable {
                        try {
                            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                        onDismiss()
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint = Color(0xFF5AC8FA),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = "Pengaturan Bluetooth & Perangkat",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.90f)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// UP NEXT QUEUE BOTTOM SHEET (Reorder, remove, shuffle, repeat)
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppleMusicQueueSheet(
    currentSong: SongDto?,
    currentIndex: Int,
    queue: List<SongDto>,
    isShuffle: Boolean,
    isRepeat: Boolean,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onPlaySong: (SongDto) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onRemoveQueueItem: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val upcoming = remember(queue, currentIndex) {
        if (currentIndex in queue.indices) {
            queue.drop(currentIndex + 1)
        } else queue
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1E1E26)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        ) {
            // Header with Shuffle & Repeat toggles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Berikutnya",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle toggle
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isShuffle) Color.White.copy(alpha = 0.22f) else Color.Transparent)
                            .clickable(onClick = onToggleShuffle),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Acak",
                            tint = if (isShuffle) Color(0xFF5AC8FA) else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Repeat toggle
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isRepeat) Color.White.copy(alpha = 0.22f) else Color.Transparent)
                            .clickable(onClick = onToggleRepeat),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isRepeat) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            contentDescription = "Ulangi",
                            tint = if (isRepeat) Color(0xFF5AC8FA) else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Close
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            // Now Playing Card
            if (currentSong != null) {
                Text(
                    text = "SEDANG DIPUTAR",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.50f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 3.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SongItemCardBackground)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = currentSong.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentSong.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5AC8FA),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = currentSong.artists.joinToString(", ") { it.name },
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.60f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    AppleMusicEqualizerBars()
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 6.dp))
            }

            // Up Next Songs List
            Text(
                text = "BERIKUTNYA DALAM ANTREAN (${upcoming.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.50f),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            if (upcoming.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 340.dp)
                ) {
                    itemsIndexed(
                        upcoming,
                        key = { index, song -> "${index}_${song.videoId}" }
                    ) { index, song ->
                        val actualQueueIndex = currentIndex + 1 + index

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 3.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SongItemCardBackground)
                                .clickable { onPlaySong(song) }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = song.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = song.artists.joinToString(", ") { it.name },
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.60f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Reorder Move Up
                            if (index > 0) {
                                IconButton(
                                    onClick = { onMoveQueueItem(actualQueueIndex, actualQueueIndex - 1) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Geser ke Atas",
                                        tint = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Reorder Move Down
                            if (index < upcoming.size - 1) {
                                IconButton(
                                    onClick = { onMoveQueueItem(actualQueueIndex, actualQueueIndex + 1) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Geser ke Bawah",
                                        tint = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Remove
                            IconButton(
                                onClick = { onRemoveQueueItem(song.videoId) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Hapus dari Antrean",
                                    tint = Color.White.copy(alpha = 0.45f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Tidak ada lagu berikutnya dalam antrean",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.60f)
                    )
                }
            }
        }
    }
}

// Animated Equalizer Bars for Now Playing indicator
@Composable
private fun AppleMusicEqualizerBars() {
    val transition = rememberInfiniteTransition(label = "EqualizerTransition")
    val bar1 by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
        label = "Bar1"
    )
    val bar2 by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse),
        label = "Bar2"
    )
    val bar3 by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(480, easing = LinearEasing), RepeatMode.Reverse),
        label = "Bar3"
    )

    Row(
        modifier = Modifier.size(width = 18.dp, height = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight(bar1)
                .clip(RoundedCornerShape(1.5.dp))
                .background(Color(0xFF5AC8FA))
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight(bar2)
                .clip(RoundedCornerShape(1.5.dp))
                .background(Color(0xFF5AC8FA))
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight(bar3)
                .clip(RoundedCornerShape(1.5.dp))
                .background(Color(0xFF5AC8FA))
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MORE OPTIONS BOTTOM SHEET (Add to playlist, album, artist, share, timer, download)
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppleMusicMoreSheet(
    song: SongDto,
    sleepTimerMinutes: Int?,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    onAddToPlaylist: () -> Unit,
    onGoToAlbum: () -> Unit,
    onGoToArtist: () -> Unit,
    onShare: () -> Unit,
    onSetSleepTimer: (Int?) -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSleepPicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1E1E26)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            // Header: Cover + Title + Artist
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = song.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = song.artists.joinToString(", ") { it.name },
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 8.dp))

            // 1. Add to playlist
            AppleMusicMoreItem(
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                title = "Tambahkan ke Playlist",
                onClick = onAddToPlaylist
            )

            // 2. Go to album
            if (!song.album.isNullOrBlank()) {
                AppleMusicMoreItem(
                    icon = Icons.Default.Album,
                    title = "Lihat Album: ${song.album}",
                    onClick = {
                        onGoToAlbum()
                        onDismiss()
                    }
                )
            }

            // 3. Go to artist
            AppleMusicMoreItem(
                icon = Icons.Default.Person,
                title = "Lihat Artis",
                onClick = onGoToArtist
            )

            // 4. Share
            AppleMusicMoreItem(
                icon = Icons.Default.Share,
                title = "Bagikan Lagu",
                onClick = onShare
            )

            // 5. Sleep timer
            AppleMusicMoreItem(
                icon = Icons.Default.Timer,
                title = if (sleepTimerMinutes != null) "Timer Tidur: Aktif ($sleepTimerMinutes mnt)" else "Timer Tidur",
                onClick = { showSleepPicker = !showSleepPicker }
            )

            if (showSleepPicker) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(5, 15, 30, 45, 60).forEach { mins ->
                        val isSelected = sleepTimerMinutes == mins
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF5AC8FA) else Color.White.copy(alpha = 0.12f))
                                .clickable {
                                    onSetSleepTimer(mins)
                                    showSleepPicker = false
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "$mins mnt",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) Color.Black else Color.White
                            )
                        }
                    }
                    if (sleepTimerMinutes != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .clickable {
                                    onSetSleepTimer(null)
                                    showSleepPicker = false
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Matikan", fontSize = 12.sp, color = Color(0xFFFF6B6B))
                        }
                    }
                }
            }

            // 6. Download
            AppleMusicMoreItem(
                icon = Icons.Default.Download,
                title = if (isDownloaded) "Hapus dari Unduhan" else if (isDownloading) "Mengunduh..." else "Unduh Lagu",
                onClick = onDownload
            )
        }
    }
}

@Composable
private fun AppleMusicMoreItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PALETTE EXTRACTION & HELPERS
// ─────────────────────────────────────────────────────────────────────────────
private suspend fun extractDominantColor(
    context: Context,
    imageUrl: String
): Color? = DynamicColorExtractor.extract(context, imageUrl)

private fun formatMillis(millis: Long): String {
    val s = (millis / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}