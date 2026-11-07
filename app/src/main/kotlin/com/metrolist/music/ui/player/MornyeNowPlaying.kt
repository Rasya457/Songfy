/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * MornyeNowPlaying.kt
 * Apple Music / Mornye unified morphing Now Playing screen.
 * Single screen, 3 modes: PLAYER ↔ LYRICS ↔ QUEUE
 */

package com.metrolist.music.ui.player

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import com.metrolist.music.ui.utils.CoverBlurHelper
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.hypot
import kotlin.math.pow

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import androidx.room.withTransaction
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalDownloadUtil
import com.metrolist.music.LocalNavController
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.lyrics.LrcParser
import com.metrolist.music.lyrics.LyricsUtils
import com.metrolist.music.lyrics.SyncLine
import com.metrolist.music.lyrics.toSyncLine
import com.metrolist.music.playback.ExoDownloadService
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.ui.component.BottomSheetState
import com.metrolist.music.ui.component.CastAudioDialog
import com.metrolist.music.ui.component.LocalBottomSheetPageState
import com.metrolist.music.ui.component.LyricsOffsetDialog
import com.metrolist.music.ui.component.PlayerSheetMotion
import com.metrolist.music.ui.component.PlayerSleepTimerDialog
import com.metrolist.music.ui.component.PlayerSliderTrack
import com.metrolist.music.ui.component.SongOptionsMenu
import com.metrolist.music.ui.component.SyncedLyricsView
import com.metrolist.music.ui.menu.AddToPlaylistDialog
import com.metrolist.music.ui.utils.ShowMediaInfo
import com.songfy.ui.icons.SongfyIconStyle
import com.songfy.ui.icons.SongfyIcons
import com.songfy.ui.icons.SongfyOutputKind
import com.songfy.ui.icons.SongfyPlayerGlyph
import com.songfy.ui.icons.rememberSongfyAudioOutputState
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

enum class NowPlayingMode { PLAYER, LYRICS, QUEUE }

// In-memory cache for parsed lyrics to eliminate UI jank when opening the player
private val lyricsCache = LruCache<String, List<SyncLine>>(60)

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.getDefault(), "%d:%02d", totalSec / 60, totalSec % 60)
}

// ─────────────────────────────────────────────────────────────────────────────
// Main entry point
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MornyeNowPlaying(
    state: BottomSheetState,
    positionState: MutableLongState,
    durationState: MutableLongState,
    effectivePositionProvider: () -> Long,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsStateWithLifecycle()
    val repeatMode by playerConnection.repeatMode.collectAsStateWithLifecycle()
    val currentLyrics by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val currentFormat by playerConnection.currentFormat.collectAsStateWithLifecycle(initialValue = null)

    val bottomSheetPageState = LocalBottomSheetPageState.current
    val context = LocalContext.current
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    val navController = LocalNavController.current
    val downloadUtil = LocalDownloadUtil.current
    val download by downloadUtil.getDownload(mediaMetadata?.id ?: "").collectAsStateWithLifecycle(initialValue = null)

    var showSongOptionsMenu by rememberSaveable { mutableStateOf(false) }
    var showLyricsOffsetDialog by rememberSaveable { mutableStateOf(false) }
    var showSleepTimerDialog by rememberSaveable { mutableStateOf(false) }
    var showCastDialog by rememberSaveable { mutableStateOf(false) }
    var showChoosePlaylistDialog by rememberSaveable { mutableStateOf(false) }

    var mode by remember { mutableStateOf(NowPlayingMode.PLAYER) }
    val isPlayerMode = mode == NowPlayingMode.PLAYER

    val closeInteraction = remember { MutableInteractionSource() }
    val artTitleInteraction = remember { MutableInteractionSource() }

    LaunchedEffect(mediaMetadata?.id, currentLyrics) {
        if (mediaMetadata != null && currentLyrics == null) {
            delay(500)
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val ep = EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        com.metrolist.music.di.LyricsHelperEntryPoint::class.java,
                    )
                    val result = ep.lyricsHelper().getLyrics(mediaMetadata!!)
                    database.query {
                        upsert(LyricsEntity(mediaMetadata!!.id, result.lyrics, result.provider))
                    }
                } catch (_: Exception) {}
            }
        }
    }

    // Cached ImageRequests for ultra-smooth 60fps opening & morphing
    val coverImageRequest = remember(mediaMetadata?.thumbnailUrl) {
        ImageRequest.Builder(context)
            .data(mediaMetadata?.thumbnailUrl)
            .crossfade(200)
            .build()
    }

    // Pre-computed artwork blur (worker thread 3-pass box blur, LruCache(8), 360ms crossfade)
    var currentBlurredBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previousBlurredBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val crossfadeAnim = remember { Animatable(1f) }

    LaunchedEffect(mediaMetadata?.thumbnailUrl) {
        val url = mediaMetadata?.thumbnailUrl
        if (url.isNullOrBlank()) {
            currentBlurredBitmap = null
            previousBlurredBitmap = null
            return@LaunchedEffect
        }
        val fastCached = CoverBlurHelper.getCached(url)
        val targetBitmap = fastCached ?: CoverBlurHelper.getOrComputeBlur(context, url)
        if (targetBitmap != null && targetBitmap != currentBlurredBitmap) {
            if (currentBlurredBitmap != null) {
                previousBlurredBitmap = currentBlurredBitmap
                currentBlurredBitmap = targetBitmap
                crossfadeAnim.snapTo(0f)
                crossfadeAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
                )
                previousBlurredBitmap = null
            } else {
                currentBlurredBitmap = targetBitmap
                crossfadeAnim.snapTo(1f)
            }
        }
    }

    // Smootherstep (6t^5 - 15t^4 + 10t^3) gradient stops for feathering sharp hero artwork into backdrop
    val smootherstepStops = remember {
        val stops = mutableListOf<Pair<Float, Color>>()
        stops.add(0.0f to Color.White)
        stops.add(0.40f to Color.White)
        val steps = 16
        for (i in 1..steps) {
            val t = i.toFloat() / steps
            val fraction = 0.40f + 0.60f * t
            val st = t * t * t * (t * (t * 6f - 15f) + 10f)
            val alpha = (1f - st).coerceIn(0f, 1f)
            stops.add(fraction to Color.White.copy(alpha = alpha))
        }
        stops.toTypedArray()
    }

    // 8-step Top Scrim gradient behind status bar with alpha proportional to (1 - progress)^1.5
    val topScrimStops = remember {
        Array(8) { i ->
            val progress = i / 7f
            val a = (1f - progress).toDouble().pow(1.5).toFloat() * 0.75f
            progress to Color.Black.copy(alpha = a)
        }
    }

    // Lyrics caching: check LruCache first (0ms), otherwise parse off the UI thread
    val lrcText = remember(currentLyrics, mediaMetadata?.id) {
        currentLyrics?.takeIf { it.id == mediaMetadata?.id }?.lyrics?.trim()
    }
    var parsedLyrics by remember(mediaMetadata?.id) {
        mutableStateOf(emptyList<SyncLine>())
    }

    LaunchedEffect(mediaMetadata?.id, lrcText) {
        val songId = mediaMetadata?.id ?: return@LaunchedEffect
        if (lrcText == null) {
            parsedLyrics = emptyList()
            return@LaunchedEffect
        }
        val id = "$songId:${lrcText.hashCode()}"
        val cached = lyricsCache.get(id)
        if (cached != null) {
            parsedLyrics = cached
            return@LaunchedEffect
        }

        if (lrcText.isNullOrBlank() || lrcText == LyricsEntity.LYRICS_NOT_FOUND) {
            parsedLyrics = emptyList()
            return@LaunchedEffect
        }

        val parsed = withContext(Dispatchers.Default) {
            try {
                val entries = LyricsUtils.parseLyrics(lrcText)
                if (entries.isNotEmpty()) {
                    entries.mapIndexed { idx, entry ->
                        val nextEntryTime = entries.getOrNull(idx + 1)?.time ?: (entry.time + 3500L)
                        entry.toSyncLine(nextEntryTime)
                    }
                } else {
                    val lrcLines = LrcParser.parse(lrcText)
                    if (lrcLines.isNotEmpty()) {
                        lrcLines
                    } else {
                        lrcText.lines().mapNotNull { line ->
                            val trimmed = line.trim()
                            if (trimmed.isNotBlank() && !trimmed.startsWith("[")) {
                                SyncLine(startMs = -1L, endMs = -1L, text = trimmed)
                            } else null
                        }
                    }
                }
            } catch (_: Exception) {
                lrcText.lines().mapNotNull { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotBlank() && !trimmed.startsWith("[")) {
                        SyncLine(startMs = -1L, endMs = -1L, text = trimmed)
                    } else null
                }
            }
        }
        lyricsCache.put(id, parsed)
        parsedLyrics = parsed
    }

    val isEpisode = currentSong?.song?.isEpisode == true
    val isFavorite = if (isEpisode) currentSong?.song?.inLibrary != null else currentSong?.song?.liked == true

    val audioInfo = remember(currentFormat) {
        currentFormat?.let {
            val codec = it.mimeType.substringAfter("/").substringBefore(";").uppercase()
            val kbps = if (it.bitrate > 0) "${it.bitrate / 1000} kbps" else null
            val khz = it.sampleRate?.let { sr -> "${sr / 1000.0} kHz" } ?: "44.1 kHz"
            listOfNotNull(codec, kbps, khz).joinToString("  •  ")
        } ?: "FLAC  •  16-bit  •  44.1 kHz"
    }

    val lyricsOffset = currentSong?.song?.lyricsOffset ?: 0
    val currentEffectiveProvider by rememberUpdatedState(effectivePositionProvider)

    val positionMs = positionState.longValue
    val durationMs = durationState.longValue

    val title = mediaMetadata?.title?.takeIf { it.isNotBlank() } ?: "Unknown Title"
    val artist = mediaMetadata?.artists?.joinToString { it.name }?.takeIf { it.isNotBlank() } ?: "Unknown Artist"

    val onMoreClick: () -> Unit = {
        showSongOptionsMenu = true
    }

    // Cover morph animation: 0f = PLAYER (large, center), 1f = COMPACT (small, top-left)
    val transitionProgress by animateFloatAsState(
        targetValue = if (isPlayerMode) 0f else 1f,
        animationSpec = PlayerSheetMotion.CoverMorphSpring,
        label = "coverTransition",
    )

    // Smooth animation progress for 3-dots menu popover and background blur
    val menuAnimProgress by animateFloatAsState(
        targetValue = if (showSongOptionsMenu) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 280f,
        ),
        label = "menuAnim",
    )

    // Smooth animation progress for audio output dialog and background blur
    val castAnimProgress by animateFloatAsState(
        targetValue = if (showCastDialog) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 280f,
        ),
        label = "castAnim",
    )

    // Combined overlay progress and blur radius for both 3-dots menu and audio output dialog
    val overlayAnimProgress = maxOf(menuAnimProgress, castAnimProgress)
    val overlayBlurRadius = (overlayAnimProgress * 25f).dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black), // Layer 1: Pure black base
    ) {
        // ── Layer 2: Backdrop Blur (Pre-blurred 128px, 3-pass worker box blur, LruCache(8), 360ms crossfade) ──
        val prevBmp = previousBlurredBitmap
        val currBmp = currentBlurredBitmap

        if (prevBmp != null && !prevBmp.isRecycled) {
            Image(
                bitmap = prevBmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.05f),
            )
        }
        if (currBmp != null && !currBmp.isRecycled) {
            Image(
                bitmap = currBmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.05f)
                    .graphicsLayer {
                        alpha = if (prevBmp != null) crossfadeAnim.value else 1f
                    },
            )
        }
        // Dark contrast overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.40f)),
        )

        // ── Layer 3: Hero Banner (sharp artwork full-bleed, down to 58% height, smootherstep feather) ──
        if (mediaMetadata?.thumbnailUrl != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.58f)
                    .graphicsLayer {
                        translationY = -transitionProgress * 90.dp.toPx()
                        val s = 1f - (transitionProgress * 0.10f)
                        scaleX = s
                        scaleY = s
                        alpha = (1f - transitionProgress * 1.35f).coerceIn(0f, 1f)
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                colorStops = smootherstepStops,
                                startY = 0f,
                                endY = size.height,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            ) {
                AsyncImage(
                    model = coverImageRequest,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // ── Layer 4: Top Scrim (8-step gradient behind status bar) ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .background(
                    Brush.verticalGradient(colorStops = topScrimStops),
                ),
        )

        // ── Layer 5: Content Layer ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .let {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && overlayBlurRadius > 0.dp) {
                        it.blur(overlayBlurRadius)
                    } else it
                }
                .graphicsLayer {
                    val s = 1f - (overlayAnimProgress * 0.035f)
                    scaleX = s
                    scaleY = s
                    alpha = 1f - (overlayAnimProgress * 0.20f)
                },
        ) {
            // Drag handle pill and "Playing from ..." header text
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.40f))
                        .clickable(
                            interactionSource = closeInteraction,
                            indication = null,
                        ) { onClose() },
                )

                Spacer(modifier = Modifier.height(6.dp))

                val albumText = mediaMetadata?.album?.title?.takeIf { it.isNotBlank() } ?: mediaMetadata?.title ?: ""
                if (albumText.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.playing_from, albumText),
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Center area: Morphs between Hero Player mode & Compact Lyrics/Queue mode
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                // When in LYRICS or QUEUE mode: slides up smoothly from below (pulled upward)
                if (transitionProgress > 0.01f) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationY = (1f - transitionProgress) * 260.dp.toPx()
                                alpha = ((transitionProgress - 0.15f) * 1.20f).coerceIn(0f, 1f)
                            },
                    ) {
                        // Compact header in Lyrics / Queue mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                                .clickable(
                                    interactionSource = artTitleInteraction,
                                    indication = null,
                                    onClick = { mode = NowPlayingMode.PLAYER },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = coverImageRequest,
                                contentDescription = title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = artist,
                                    color = Color.White.copy(alpha = 0.65f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(
                                onClick = onMoreClick,
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(
                                    imageVector = SongfyIcons.Ellipsis,
                                    contentDescription = "More",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }

                        // Body: Synced Lyrics or Queue
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        ) {
                            if (mode == NowPlayingMode.LYRICS) {
                                SyncedLyricsView(
                                    lines = parsedLyrics,
                                    positionProvider = { currentEffectiveProvider() + lyricsOffset },
                                    onSeek = { playerConnection.player.seekTo((it - lyricsOffset).coerceAtLeast(0L)) },
                                    isLoading = lrcText == null,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else if (mode == NowPlayingMode.QUEUE) {
                                QueueContent(
                                    playerConnection = playerConnection,
                                    shuffleModeEnabled = shuffleModeEnabled,
                                    repeatMode = repeatMode,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }

                // When in PLAYER mode: Title, artist, ⋯ button and single-line lyrics preview
                if (transitionProgress < 0.99f) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationY = -transitionProgress * 60.dp.toPx()
                                alpha = (1f - transitionProgress * 1.45f).coerceIn(0f, 1f)
                            },
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        // Title, Artist, and Circular ⋯ Button (as in screenshot)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = title,
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = artist,
                                    color = Color.White.copy(alpha = 0.65f),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Circular ⋯ button
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.22f))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(bounded = true, radius = 18.dp),
                                        onClick = onMoreClick,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = SongfyIcons.Ellipsis,
                                    contentDescription = "More options",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Single-line lyrics preview ("Kata-katanya segera tiba" / synced text)
                        val currentSyncedLineText = remember(parsedLyrics, positionMs) {
                            if (parsedLyrics.isEmpty()) null
                            else {
                                val line = parsedLyrics.find { positionMs in it.startMs..it.endMs }
                                    ?: parsedLyrics.lastOrNull { positionMs >= it.startMs }
                                line?.text?.takeIf { it.isNotBlank() }
                            }
                        }
                        val previewText = currentSyncedLineText
                            ?: if (Locale.getDefault().language == "in" || Locale.getDefault().language == "id") "Kata-katanya segera tiba"
                            else stringResource(R.string.lyrics_arriving_soon)

                        Text(
                            text = previewText,
                            color = Color.White.copy(alpha = 0.78f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    mode = NowPlayingMode.LYRICS
                                },
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }

            // ── Bottom Controls (always visible) ──
            BottomControls(
                positionMs = positionMs,
                durationMs = durationMs,
                isPlaying = isPlaying,
                audioInfo = audioInfo,
                onSeek = { playerConnection.player.seekTo(it) },
                onPlayPause = {
                    if (isPlaying) playerConnection.player.pause()
                    else playerConnection.player.play()
                },
                onNext = { playerConnection.player.seekToNext() },
                onPrev = { playerConnection.player.seekToPrevious() },
                mode = mode,
                onLyricsClick = {
                    mode = if (mode == NowPlayingMode.LYRICS) NowPlayingMode.PLAYER else NowPlayingMode.LYRICS
                },
                onCastClick = {
                    showCastDialog = true
                },
                onQueueClick = {
                    mode = if (mode == NowPlayingMode.QUEUE) NowPlayingMode.PLAYER else NowPlayingMode.QUEUE
                },
            )
        }

        if (showSleepTimerDialog) {
            PlayerSleepTimerDialog(
                onDismiss = { showSleepTimerDialog = false },
            )
        }

        if (castAnimProgress > 0.005f) {
            CastAudioDialog(
                animProgress = castAnimProgress,
                onDismiss = { showCastDialog = false },
            )
        }

        if (showLyricsOffsetDialog) {
            LyricsOffsetDialog(
                initialOffsetMs = lyricsOffset,
                onDismiss = { showLyricsOffsetDialog = false },
                onSaveOffset = { newOffset ->
                    currentSong?.song?.let { song ->
                        coroutineScope.launch(Dispatchers.IO) {
                            database.query {
                                update(song.copy(lyricsOffset = newOffset))
                            }
                        }
                    }
                },
            )
        }

        // Floating 3-dots Menu Popover with smooth scale/fade & screen blur
        if (menuAnimProgress > 0.005f && mediaMetadata != null) {
            val meta = mediaMetadata!!
            val isDownloaded = download?.state == Download.STATE_COMPLETED
            val isCached = currentSong?.song?.dateDownload != null || runCatching {
                playerConnection.service.playerCache.isCached(meta.id, 0, 1024)
            }.getOrDefault(false)
            val isLocal = currentSong?.song?.isLocal == true

            SongOptionsMenu(
                albumName = meta.album?.title ?: meta.title,
                artistName = meta.artists.joinToString { it.name }.takeIf { it.isNotBlank() } ?: "Unknown Artist",
                animProgress = menuAnimProgress,
                isLyricsOrQueueMode = !isPlayerMode,
                isCached = isCached,
                isDownloaded = isDownloaded,
                isLocal = isLocal,
                onDismiss = { showSongOptionsMenu = false },
                onAction = { action ->
                    showSongOptionsMenu = false
                    when (action) {
                        "share" -> {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "https://music.youtube.com/watch?v=${meta.id}")
                            }
                            context.startActivity(Intent.createChooser(intent, null))
                        }
                        "upgrade" -> {
                            bottomSheetPageState.show { ShowMediaInfo(meta.id) }
                        }
                        "info" -> {
                            bottomSheetPageState.show { ShowMediaInfo(meta.id) }
                        }
                        "playlist" -> {
                            showChoosePlaylistDialog = true
                        }
                        "video" -> {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/watch?v=${meta.id}"))
                            context.startActivity(intent)
                        }
                        "radio" -> {
                            Toast.makeText(context, context.getString(R.string.starting_radio), Toast.LENGTH_SHORT).show()
                            playerConnection.startRadioSeamlessly()
                        }
                        "play_next" -> {
                            playerConnection.playNext(meta.toMediaItem())
                            Toast.makeText(context, "Playing next", Toast.LENGTH_SHORT).show()
                        }
                        "queue" -> {
                            playerConnection.addToQueue(meta.toMediaItem())
                            Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                        }
                        "download" -> {
                            downloadUtil.download(meta)
                        }
                        "remove_download" -> {
                            DownloadService.sendRemoveDownload(
                                context,
                                ExoDownloadService::class.java,
                                meta.id,
                                false,
                            )
                        }
                        "remove_cache" -> {
                            runCatching {
                                playerConnection.service.playerCache.removeResource(meta.id)
                            }
                            coroutineScope.launch(Dispatchers.IO) {
                                database.query {
                                    getSongByIdBlocking(meta.id)?.let { update(it.song.copy(dateDownload = null)) }
                                }
                            }
                            Toast.makeText(context, "Removed from cache", Toast.LENGTH_SHORT).show()
                        }
                        "album" -> {
                            meta.album?.let { album ->
                                navController.navigate("album/${album.id}")
                                state.collapseSoft()
                            }
                        }
                        "artist" -> {
                            meta.artists.firstOrNull()?.let { artist ->
                                navController.navigate("artist/${artist.id}")
                                state.collapseSoft()
                            }
                        }
                        "sleep" -> {
                            showSleepTimerDialog = true
                        }
                        "lyrics_offset" -> {
                            showLyricsOffsetDialog = true
                        }
                        "copy_log" -> {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val log = "Title: ${meta.title}\nArtist: ${meta.artists.joinToString { it.name }}\nID: ${meta.id}\nDuration: ${durationMs}ms\nFormat: $audioInfo\nBitrate: ${currentFormat?.bitrate ?: 0}"
                            clipboard.setPrimaryClip(ClipData.newPlainText("Song Log", log))
                            Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            )
        }

        if (mediaMetadata != null) {
            AddToPlaylistDialog(
                isVisible = showChoosePlaylistDialog,
                onGetSong = {
                    database.withTransaction {
                        insert(mediaMetadata!!)
                    }
                    listOf(mediaMetadata!!.id)
                },
                onGetSongIds = { listOf(mediaMetadata!!.id) },
                onDismiss = {
                    showChoosePlaylistDialog = false
                },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Queue Content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun QueueContent(
    playerConnection: PlayerConnection,
    shuffleModeEnabled: Boolean,
    repeatMode: Int,
    modifier: Modifier = Modifier,
) {
    val queueWindows by playerConnection.queueWindows.collectAsStateWithLifecycle()
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsStateWithLifecycle(initialValue = -1)
    val listState = rememberLazyListState()

    LaunchedEffect(currentWindowIndex) {
        if (currentWindowIndex in queueWindows.indices) listState.animateScrollToItem(currentWindowIndex)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (shuffleModeEnabled) MaterialTheme.colorScheme.primary.copy(0.35f) else Color.White.copy(0.12f))
                    .clickable { playerConnection.player.shuffleModeEnabled = !shuffleModeEnabled }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(SongfyIcons.Shuffle, null, tint = if (shuffleModeEnabled) MaterialTheme.colorScheme.primary else Color.White, modifier = Modifier.size(22.dp))
            }

            val repeatActive = repeatMode != Player.REPEAT_MODE_OFF
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (repeatActive) MaterialTheme.colorScheme.primary.copy(0.35f) else Color.White.copy(0.12f))
                    .clickable {
                        playerConnection.player.repeatMode = when (repeatMode) {
                            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                            else -> Player.REPEAT_MODE_OFF
                        }
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (repeatMode == Player.REPEAT_MODE_ONE) SongfyIcons.RepeatOne else SongfyIcons.Repeat, null, tint = if (repeatActive) MaterialTheme.colorScheme.primary else Color.White, modifier = Modifier.size(22.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Continue Playing", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
        Spacer(modifier = Modifier.height(10.dp))

        if (queueWindows.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("Queue is empty", color = Color.White.copy(0.60f), fontSize = 16.sp)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(queueWindows, key = { i, w -> w.uid.hashCode() + i }) { index, window ->
                    val meta = window.mediaItem.mediaMetadata
                    val isCurrent = index == currentWindowIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isCurrent) Color.White.copy(0.15f) else Color.Transparent)
                            .clickable { playerConnection.player.seekToDefaultPosition(index) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(model = meta.artworkUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(meta.title?.toString().orEmpty(), color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.White, fontSize = 14.sp, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(meta.artist?.toString().orEmpty(), color = Color.White.copy(0.60f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (isCurrent) Icon(SongfyIcons.Play, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom Controls
// ─────────────────────────────────────────────────────────────────────────────

/**
 * instantTap — Custom pointer input modifier that guarantees immediate 1-tap responsiveness
 * for critical player actions inside draggable bottom sheets.
 *
 * Prevents parent drag gestures (like BottomSheet's detectVerticalDragGestures) from stealing
 * touches caused by natural thumb rolling during tap-down.
 */
private fun Modifier.instantTap(
    interactionSource: MutableInteractionSource? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = this.pointerInput(enabled, onClick) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        val press = PressInteraction.Press(down.position)
        interactionSource?.tryEmit(press)
        var movedFar = false
        val maxSlop = viewConfiguration.touchSlop * 2.5f

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null || !change.pressed) {
                change?.consume()
                interactionSource?.tryEmit(
                    if (!movedFar) PressInteraction.Release(press) else PressInteraction.Cancel(press)
                )
                if (!movedFar) {
                    onClick()
                }
                break
            }
            val dx = change.position.x - down.position.x
            val dy = change.position.y - down.position.y
            if (hypot(dx, dy) > maxSlop) {
                movedFar = true
            }
            change.consume()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BottomControls(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    audioInfo: String,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    mode: NowPlayingMode,
    onLyricsClick: () -> Unit,
    onCastClick: () -> Unit,
    onQueueClick: () -> Unit,
) {
    val context = LocalContext.current
    val prevInteraction = remember { MutableInteractionSource() }
    val playInteraction = remember { MutableInteractionSource() }
    val nextInteraction = remember { MutableInteractionSource() }
    val lyricsInteraction = remember { MutableInteractionSource() }
    val castInteraction = remember { MutableInteractionSource() }
    val queueInteraction = remember { MutableInteractionSource() }
    val playerConnection = LocalPlayerConnection.current
    val castHandler = remember(playerConnection) {
        try {
            playerConnection?.service?.castConnectionHandler
        } catch (_: Exception) {
            null
        }
    }
    val isCasting by castHandler?.isCasting?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat().coerceAtLeast(1f) }

    val audioOutput by rememberSongfyAudioOutputState()
    val isExternalOutputActive = audioOutput.kind != SongfyOutputKind.PHONE && !isCasting

    var volumeUi by remember {
        mutableFloatStateOf((audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol).coerceIn(0f, 1f))
    }

    DisposableEffect(context, audioManager, maxVol) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val current = (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol).coerceIn(0f, 1f)
                volumeUi = current
            }
        }
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
        } catch (_: Exception) {}
        onDispose {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }

    LaunchedEffect(audioManager, maxVol) {
        while (true) {
            delay(350)
            val current = (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol).coerceIn(0f, 1f)
            if (kotlin.math.abs(current - volumeUi) > 0.02f) volumeUi = current
        }
    }

    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }

    val currentSliderVal = if (dragging) dragPosition else positionMs.toFloat()
    val maxRange = durationMs.coerceAtLeast(1L).toFloat()
    val sliderRange = 0f..maxRange
    val sliderState = remember(sliderRange) { SliderState(value = currentSliderVal, trackRange = sliderRange) }
    sliderState.value = currentSliderVal.coerceIn(sliderRange)

    var volumeDragging by remember { mutableStateOf(false) }
    var volumeDragPosition by remember { mutableFloatStateOf(0f) }
    val currentVolumeVal = if (volumeDragging) volumeDragPosition else volumeUi * maxVol
    val volumeRange = 0f..maxVol
    val volumeSliderState = remember(maxVol) { SliderState(value = currentVolumeVal, trackRange = volumeRange) }
    volumeSliderState.value = currentVolumeVal.coerceIn(volumeRange)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom)),
    ) {
        Slider(
            state = sliderState,
            onValueChange = { dragging = true; dragPosition = it },
            onValueChangeFinished = { dragging = false; onSeek(dragPosition.toLong()) },
            thumb = { Spacer(modifier = Modifier.size(0.dp)) },
            track = { state ->
                PlayerSliderTrack(
                    sliderState = state,
                    trackHeight = 5.dp,
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val displayPos = if (dragging) dragPosition.toLong() else positionMs
            Text(formatTime(displayPos), color = Color.White.copy(0.80f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(audioInfo, color = Color.White.copy(0.65f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Text("-" + formatTime((durationMs - displayPos).coerceAtLeast(0L)), color = Color.White.copy(0.80f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Playback controls (Apple Music iOS bold solid icons, no circle background, as in screenshot)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Previous Button (◀◀ double arrows)
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clickable(
                        interactionSource = prevInteraction,
                        indication = ripple(bounded = false, radius = 32.dp),
                        onClick = onPrev,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_apple_backward),
                    contentDescription = "Previous",
                    tint = Color.White,
                    modifier = Modifier.size(38.dp),
                )
            }

            // Play / Pause Button (▶ / ❚❚ large solid white icon)
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clickable(
                        interactionSource = playInteraction,
                        indication = ripple(bounded = false, radius = 38.dp),
                        onClick = onPlayPause,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(if (isPlaying) R.drawable.ic_apple_pause else R.drawable.ic_apple_play),
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(52.dp),
                )
            }

            // Next Button (▶▶ double arrows)
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clickable(
                        interactionSource = nextInteraction,
                        indication = ripple(bounded = false, radius = 32.dp),
                        onClick = onNext,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_apple_forward),
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier.size(38.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // System volume slider
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = SongfyIcons.SpeakerLow, contentDescription = null, tint = Color.White.copy(0.65f), modifier = Modifier.size(18.dp))
            Slider(
                state = volumeSliderState,
                onValueChange = { newVal ->
                    volumeDragging = true
                    volumeDragPosition = newVal
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVal.roundToInt().coerceIn(0, maxVol.toInt()), 0)
                },
                onValueChangeFinished = {
                    volumeDragging = false
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeDragPosition.roundToInt().coerceIn(0, maxVol.toInt()), 0)
                },
                thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                track = { state ->
                    PlayerSliderTrack(
                        sliderState = state,
                        trackHeight = 5.dp,
                        colors = SliderDefaults.colors(
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                        ),
                    )
                },
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            )
            Icon(imageVector = SongfyIcons.SpeakerHigh, contentDescription = null, tint = Color.White.copy(0.65f), modifier = Modifier.size(18.dp))
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Mode switch bar: Lyrics | Audio Output (AirPods / Buds / Speaker / Cast) | Queue ("Continue Music")
        // Each button has an expanded 1/3-width touch container with instantTap to ensure 100% 1-tap responsiveness
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 1. Lyrics Toggle (1/3 of row) — 28dp SongfyIcons.LyricsQuote
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp)
                    .instantTap(
                        interactionSource = lyricsInteraction,
                        onClick = onLyricsClick,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(
                            if (mode == NowPlayingMode.LYRICS) Color.White.copy(alpha = 0.22f)
                            else Color.Transparent,
                        )
                        .let {
                            if (mode == NowPlayingMode.LYRICS) it.border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                            else it
                        }
                        .indication(lyricsInteraction, ripple(bounded = true, radius = 25.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    SongfyPlayerGlyph(
                        icon = SongfyIcons.LyricsQuote,
                        contentDescription = "Lyrics",
                        tint = if (mode == NowPlayingMode.LYRICS) Color.White else SongfyIconStyle.PlayerGlyphTint,
                    )
                }
            }

            // 2. Audio Output Glyph (1/3 of row) — 28dp dynamic glyph (AirPods/Buds, Headphones, Speaker, Car, TV, AirPlay)
            val outputKind = if (isCasting) SongfyOutputKind.OTHER else audioOutput.kind
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp)
                    .instantTap(
                        interactionSource = castInteraction,
                        onClick = onCastClick,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(
                            if (isCasting || isExternalOutputActive) Color.White.copy(alpha = 0.16f)
                            else Color.Transparent,
                        )
                        .indication(castInteraction, ripple(bounded = true, radius = 25.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    SongfyPlayerGlyph(
                        icon = SongfyIcons.outputGlyph(outputKind),
                        contentDescription = "Audio Output",
                        tint = SongfyIconStyle.PlayerGlyphTint,
                    )
                }
            }

            // 3. Queue Toggle ("Continue Music") (1/3 of row) — 28dp SongfyIcons.Queue
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp)
                    .instantTap(
                        interactionSource = queueInteraction,
                        onClick = onQueueClick,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(
                            if (mode == NowPlayingMode.QUEUE) Color.White.copy(alpha = 0.22f)
                            else Color.Transparent,
                        )
                        .let {
                            if (mode == NowPlayingMode.QUEUE) it.border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                            else it
                        }
                        .indication(queueInteraction, ripple(bounded = true, radius = 25.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    SongfyPlayerGlyph(
                        icon = SongfyIcons.Queue,
                        contentDescription = "Continue Playing",
                        tint = if (mode == NowPlayingMode.QUEUE) Color.White else SongfyIconStyle.PlayerGlyphTint,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        val currentDeviceName = when {
            isCasting -> "Google Cast"
            isExternalOutputActive -> audioOutput.deviceName
            else -> null
        }
        val displayDeviceText = currentDeviceName ?: stringResource(R.string.this_phone)

        val deviceInteraction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 6.dp)
                .height(32.dp)
                .instantTap(interactionSource = deviceInteraction, onClick = onCastClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = displayDeviceText,
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
