package com.metrolist.music.ui.screens

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.constants.LiquidGlassEnabledKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.lyrics.LrcParser
import com.metrolist.music.lyrics.LyricsHelper
import com.metrolist.music.lyrics.SyncLine
import com.metrolist.music.playback.MusicService
import com.metrolist.music.utils.rememberPreference
import com.songfy.ui.LocalSongfyBackdrop
import com.songfy.ui.LocalSongfyLiquidGlassEnabled
import com.songfy.ui.backdrop.backdrops.layerBackdrop
import com.songfy.ui.backdrop.backdrops.rememberLayerBackdrop
import com.songfy.ui.songfyLiquidGlass
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class LockScreenLyricsActivity : ComponentActivity() {
    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var lyricsHelper: LyricsHelper

    private val musicServiceState = mutableStateOf<MusicService?>(null)
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            musicServiceState.value = (binder as? MusicService.MusicBinder)?.service
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicServiceState.value = null
        }
    }

    private fun navigateToPhoneHomeAndFinish() {
        runCatching {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
        }
        finish()
    }

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_USER_PRESENT) {
                navigateToPhoneHomeAndFinish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(false)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )

        val bindIntent = Intent(this, MusicService::class.java)
        isBound = bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        ContextCompat.registerReceiver(
            this,
            unlockReceiver,
            IntentFilter(Intent.ACTION_USER_PRESENT),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        setContent {
            // Back button/gesture must never exit to system lockscreen
            BackHandler(enabled = true) { /* Stay on lock screen lyrics */ }

            val (liquidGlassEnabled) = rememberPreference(LiquidGlassEnabledKey, defaultValue = true)
            val service = musicServiceState.value

            CompositionLocalProvider(
                LocalSongfyLiquidGlassEnabled provides liquidGlassEnabled,
            ) {
                LockScreenLyricsContent(
                    service = service,
                    database = database,
                    lyricsHelper = lyricsHelper,
                    onSwipeUpToUnlock = {
                        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                        if (km == null || !km.isKeyguardLocked) {
                            navigateToPhoneHomeAndFinish()
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            if (!km.isKeyguardSecure) {
                                km.requestDismissKeyguard(this, null)
                                navigateToPhoneHomeAndFinish()
                            } else {
                                km.requestDismissKeyguard(
                                    this,
                                    object : KeyguardManager.KeyguardDismissCallback() {
                                        override fun onDismissSucceeded() {
                                            navigateToPhoneHomeAndFinish()
                                        }
                                    },
                                )
                            }
                        } else {
                            navigateToPhoneHomeAndFinish()
                        }
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(unlockReceiver) }
        if (isBound) {
            runCatching { unbindService(serviceConnection) }
            isBound = false
        }
        super.onDestroy()
    }
}

private val SmoothLyricEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

@Composable
private fun LockScreenLyricsContent(
    service: MusicService?,
    database: MusicDatabase,
    lyricsHelper: LyricsHelper,
    onSwipeUpToUnlock: () -> Unit,
) {
    val mediaMetadata = service?.currentMediaMetadata?.collectAsStateWithLifecycle()?.value
    val player = service?.player
    val coroutineScope = rememberCoroutineScope()

    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(true) }
    var parsedLyrics by remember { mutableStateOf<List<SyncLine>>(emptyList()) }
    var currentDateText by remember { mutableStateOf("") }
    var currentTimeText by remember { mutableStateOf("") }

    // Seek bar drag state
    var isSeeking by remember { mutableStateOf(false) }
    var seekFraction by remember { mutableFloatStateOf(0f) }

    val listState = rememberLazyListState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Update clock & date only while lock screen is visible/resumed (zero battery drain when screen is off)
    LaunchedEffect(lifecycleOwner) {
        val dateFmt = SimpleDateFormat("EEEE d MMMM", Locale.forLanguageTag("id-ID"))
        val timeFmt = SimpleDateFormat("h.mma", Locale.US)
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val now = Date()
                currentDateText = dateFmt.format(now).replaceFirstChar { it.uppercase() }
                currentTimeText = timeFmt.format(now).uppercase()
                delay(1000L)
            }
        }
    }

    // Observe lyrics continuously from Room (parsed off Main thread) and fetch in parallel if not yet cached
    LaunchedEffect(mediaMetadata?.id) {
        val meta = mediaMetadata ?: return@LaunchedEffect
        parsedLyrics = emptyList()
        currentPositionMs = 0L
        runCatching { listState.scrollToItem(0) }

        var fetchTriggered = false
        var lastRawLyrics: String? = null
        database.lyrics(meta.id).collect { entity ->
            val raw = entity?.lyrics
            if (!raw.isNullOrBlank() && raw != LyricsEntity.LYRICS_NOT_FOUND) {
                if (raw != lastRawLyrics) {
                    lastRawLyrics = raw
                    parsedLyrics = withContext(Dispatchers.Default) { parseLockScreenLyrics(raw) }
                }
            } else if (!fetchTriggered) {
                fetchTriggered = true
                launch(Dispatchers.IO) {
                    val fetched = runCatching { lyricsHelper.getLyrics(meta) }.getOrNull()
                    if (fetched != null && fetched.lyrics.isNotBlank() && fetched.lyrics != LyricsEntity.LYRICS_NOT_FOUND) {
                        val parsed = withContext(Dispatchers.Default) { parseLockScreenLyrics(fetched.lyrics) }
                        withContext(Dispatchers.Main) {
                            lastRawLyrics = fetched.lyrics
                            parsedLyrics = parsed
                        }
                        runCatching {
                            database.query {
                                upsert(
                                    LyricsEntity(
                                        id = meta.id,
                                        lyrics = fetched.lyrics,
                                        provider = fetched.provider,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Poll playback position & state only while resumed (zero CPU work when screen turns off)
    LaunchedEffect(player, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                if (player != null) {
                    if (!isSeeking) {
                        currentPositionMs = player.currentPosition.coerceAtLeast(0L)
                    }
                    durationMs = player.duration.coerceAtLeast(0L)
                    isPlaying = player.isPlaying
                }
                delay(if (isPlaying) 100L else 500L)
            }
        }
    }

    val effectivePosMs = if (isSeeking && durationMs > 0L) {
        (seekFraction * durationMs).toLong()
    } else {
        currentPositionMs
    }

    val isUnsynced = parsedLyrics.firstOrNull()?.startMs?.let { it < 0L } == true
    val activeIndex = remember(parsedLyrics, effectivePosMs, isUnsynced) {
        if (parsedLyrics.isEmpty() || isUnsynced) -1
        else parsedLyrics.indexOfLast { effectivePosMs + 80L >= it.startMs }
    }

    // Smoothly glide active lyric line to vertical center
    LaunchedEffect(activeIndex, parsedLyrics) {
        if (activeIndex !in parsedLyrics.indices) return@LaunchedEffect
        val activeItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == activeIndex }
        if (activeItem != null) {
            val vp = listState.layoutInfo
            val targetOffset = (vp.viewportStartOffset + vp.viewportEndOffset - activeItem.size) / 2f
            listState.animateScrollBy(
                value = activeItem.offset - targetOffset,
                animationSpec = spring(dampingRatio = 0.92f, stiffness = 125f),
            )
        } else {
            val vpHeight = listState.layoutInfo.viewportSize.height
            val approxOffset = if (vpHeight > 0) -(vpHeight * 0.34f).toInt() else -160
            listState.animateScrollToItem(index = activeIndex, scrollOffset = approxOffset)
            val settled = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == activeIndex }
            if (settled != null) {
                val vp = listState.layoutInfo
                val targetOffset = (vp.viewportStartOffset + vp.viewportEndOffset - settled.size) / 2f
                listState.animateScrollBy(
                    value = settled.offset - targetOffset,
                    animationSpec = spring(dampingRatio = 0.92f, stiffness = 125f),
                )
            }
        }
    }

    val swipeOffsetY = remember { Animatable(0f) }

    val unlockSwipeModifier = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragStart = {
                coroutineScope.launch { swipeOffsetY.stop() }
            },
            onVerticalDrag = { _, dragAmount ->
                coroutineScope.launch {
                    val next = (swipeOffsetY.value + dragAmount).coerceIn(-360f, 0f)
                    swipeOffsetY.snapTo(next)
                }
            },
            onDragEnd = {
                val pulled = swipeOffsetY.value
                coroutineScope.launch {
                    if (pulled < -100f) {
                        onSwipeUpToUnlock()
                    }
                    swipeOffsetY.animateTo(0f, spring(dampingRatio = 0.84f, stiffness = 400f))
                }
            },
            onDragCancel = {
                coroutineScope.launch {
                    swipeOffsetY.animateTo(0f, spring(dampingRatio = 0.84f, stiffness = 400f))
                }
            },
        )
    }

    val lockBackdrop = rememberLayerBackdrop {
        drawRect(Color(0xFF1A1B1E))
        drawContent()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1B1E))
            .then(unlockSwipeModifier),
    ) {
        // Layer 1: Enlarged, deeply blurred album artwork with smooth crossfade on track change
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(lockBackdrop),
        ) {
            Crossfade(
                targetState = mediaMetadata?.thumbnailUrl,
                animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
                label = "lockScreenArtworkBg",
            ) { artUrl ->
                if (artUrl != null) {
                    AsyncImage(
                        model = artUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = 1.28f
                                scaleY = 1.28f
                            }
                            .let { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) it.blur(68.dp) else it },
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF222428)),
                    )
                }
            }
            // Soft atmospheric dark translucent scrim matching reference tone
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color(0x5E1A1A1C),
                            0.48f to Color(0x7018181A),
                            1.0f to Color(0x94111114),
                        ),
                    ),
            )
        }

        // Layer 2: Foreground Lock Screen Layout
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = swipeOffsetY.value * 0.42f
                    alpha = (1f - (abs(swipeOffsetY.value) / 520f)).coerceIn(0.65f, 1f)
                }
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom))
                .padding(horizontal = 20.dp),
        ) {
            val clockFontSize = (maxWidth.value * 0.205f).coerceIn(56f, 80f).sp
            val lyricFontSize = (maxWidth.value * 0.068f).coerceIn(22f, 27f).sp
            val lyricLineHeight = (lyricFontSize.value * 1.26f).sp

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Small padlock icon + Date + Ultra-bold large Clock
                Icon(
                    painter = painterResource(R.drawable.lock),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.height(7.dp))
                Text(
                    text = currentDateText,
                    color = Color.White.copy(alpha = 0.96f),
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp,
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = currentTimeText,
                    color = Color.White,
                    fontSize = clockFontSize,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-2.8).sp,
                    lineHeight = (clockFontSize.value * 1.02f).sp,
                    maxLines = 1,
                    softWrap = false,
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Center Real-Time Synced Lyrics with vertical fade mask & depth-of-field blur
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0.0f to Color.Transparent,
                                    0.12f to Color.Black,
                                    0.86f to Color.Black,
                                    1.0f to Color.Transparent,
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (parsedLyrics.isEmpty()) {
                        LockScreenTransitionDots(progress = 0.36f)
                    } else {
                        LazyColumn(
                            state = listState,
                            userScrollEnabled = false,
                            contentPadding = PaddingValues(vertical = 76.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            // Intro dots before first lyric line starts
                            if (!isUnsynced && activeIndex < 0) {
                                item(key = "intro_dots") {
                                    val firstStart = parsedLyrics.first().startMs.coerceAtLeast(1L)
                                    val introProgress = (effectivePosMs.toFloat() / firstStart.toFloat()).coerceIn(0f, 1f)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        LockScreenTransitionDots(progress = introProgress)
                                    }
                                }
                            }

                            itemsIndexed(
                                items = parsedLyrics,
                                key = { idx, line -> "${idx}_${line.startMs}" },
                            ) { index, line ->
                                val isCurrent = index == activeIndex
                                val isPrevious = activeIndex > 0 && index == activeIndex - 1
                                val distance = if (activeIndex >= 0) abs(index - activeIndex) else index

                                val targetAlpha = when {
                                    isUnsynced -> 0.90f
                                    isCurrent -> 0.96f
                                    distance == 1 -> 0.56f
                                    distance == 2 -> 0.42f
                                    else -> 0.24f
                                }
                                val animatedAlpha by animateFloatAsState(
                                    targetValue = targetAlpha,
                                    animationSpec = tween(480, easing = SmoothLyricEasing),
                                    label = "lockLyricAlpha",
                                )

                                val targetScale = when {
                                    isUnsynced -> 1f
                                    isCurrent -> 1.03f
                                    distance == 1 -> 0.98f
                                    else -> 0.95f
                                }
                                val animatedScale by animateFloatAsState(
                                    targetValue = targetScale,
                                    animationSpec = spring(dampingRatio = 0.86f, stiffness = 200f),
                                    label = "lockLyricScale",
                                )

                                val targetBlur = when {
                                    isUnsynced || isCurrent -> 0.dp
                                    distance == 1 -> 0.8.dp
                                    distance == 2 -> 1.7.dp
                                    else -> 2.6.dp
                                }
                                val animatedBlur by animateDpAsState(
                                    targetValue = targetBlur,
                                    animationSpec = tween(480, easing = FastOutSlowInEasing),
                                    label = "lockLyricBlur",
                                )

                                // Show 3-dot indicator between previous line and active line (matches screenshot)
                                val showTransitionDots = !isUnsynced && isPrevious
                                val transitionProgress = if (showTransitionDots && activeIndex in parsedLyrics.indices) {
                                    val activeLine = parsedLyrics[activeIndex]
                                    val lineSpan = (activeLine.endMs - activeLine.startMs).coerceAtLeast(1500L)
                                    ((effectivePosMs - activeLine.startMs).toFloat() / lineSpan.toFloat()).coerceIn(0.15f, 1f)
                                } else {
                                    0.35f
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = line.text,
                                        color = Color.White,
                                        fontSize = lyricFontSize,
                                        lineHeight = lyricLineHeight,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.4).sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp)
                                            .graphicsLayer {
                                                alpha = animatedAlpha
                                                scaleX = animatedScale
                                                scaleY = animatedScale
                                            }
                                            .let { m ->
                                                val quantizedBlur = ((animatedBlur.value * 2f).toInt() / 2f).dp
                                                if (quantizedBlur > 0.dp && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                    m.blur(quantizedBlur)
                                                } else {
                                                    m
                                                }
                                            },
                                    )

                                    AnimatedVisibility(
                                        visible = showTransitionDots,
                                        enter = fadeIn(tween(340)) + expandVertically(tween(340, easing = SmoothLyricEasing)),
                                        exit = fadeOut(tween(260)) + shrinkVertically(tween(260, easing = SmoothLyricEasing)),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 22.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            LockScreenTransitionDots(progress = transitionProgress)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Glassmorphism Music Player Card
                val cardShape = RoundedCornerShape(28.dp)
                val borderBrush = remember {
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.40f),
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.30f),
                        ),
                    )
                }

                CompositionLocalProvider(LocalSongfyBackdrop provides lockBackdrop) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .songfyLiquidGlass(
                                dark = true,
                                shadowElevation = 18.dp,
                                shape = cardShape,
                            )
                            .clip(cardShape)
                            .background(Color(0x2E1A1B1F))
                            .border(0.9.dp, borderBrush, cardShape)
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Crossfade(
                                targetState = mediaMetadata?.thumbnailUrl,
                                animationSpec = tween(420),
                                label = "lockCardArt",
                            ) { thumbUrl ->
                                AsyncImage(
                                    model = thumbUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.12f)),
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mediaMetadata?.title ?: "Songfy",
                                    color = Color.White,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = mediaMetadata?.artists?.joinToString(", ") { it.name }.orEmpty(),
                                    color = Color.White.copy(alpha = 0.74f),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Interactive seekable progress bar
                        val displayFraction = if (isSeeking) {
                            seekFraction
                        } else if (durationMs > 0L) {
                            (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }

                        var barWidthPx by remember { mutableFloatStateOf(1f) }
                        val barHeight by animateDpAsState(
                            targetValue = if (isSeeking) 8.dp else 5.dp,
                            animationSpec = spring(dampingRatio = 0.8f, stiffness = 450f),
                            label = "lockSeekBarHeight",
                        )

                        Box(
                            contentAlignment = Alignment.CenterStart,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp)
                                .onSizeChanged { barWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                                .pointerInput(durationMs, player) {
                                    detectTapGestures { offset ->
                                        if (durationMs > 0L && player != null) {
                                            val frac = (offset.x / barWidthPx).coerceIn(0f, 1f)
                                            val targetMs = (frac * durationMs).toLong()
                                            currentPositionMs = targetMs
                                            player.seekTo(targetMs)
                                        }
                                    }
                                }
                                .pointerInput(durationMs, player) {
                                    detectHorizontalDragGestures(
                                        onDragStart = { offset ->
                                            if (durationMs > 0L) {
                                                isSeeking = true
                                                seekFraction = (offset.x / barWidthPx).coerceIn(0f, 1f)
                                            }
                                        },
                                        onHorizontalDrag = { change, _ ->
                                            if (isSeeking && durationMs > 0L) {
                                                change.consume()
                                                seekFraction = (change.position.x / barWidthPx).coerceIn(0f, 1f)
                                            }
                                        },
                                        onDragEnd = {
                                            if (isSeeking && durationMs > 0L && player != null) {
                                                val targetMs = (seekFraction * durationMs).toLong()
                                                currentPositionMs = targetMs
                                                player.seekTo(targetMs)
                                            }
                                            isSeeking = false
                                        },
                                        onDragCancel = {
                                            isSeeking = false
                                        },
                                    )
                                },
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(barHeight)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.24f)),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(displayFraction)
                                        .fillMaxHeight()
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.94f)),
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = formatTimeMs(effectivePosMs),
                                color = Color.White.copy(alpha = 0.74f),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            val remaining = (durationMs - effectivePosMs).coerceAtLeast(0L)
                            Text(
                                text = "-${formatTimeMs(remaining)}",
                                color = Color.White.copy(alpha = 0.74f),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = { player?.seekToPrevious() },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.skip_previous),
                                    contentDescription = "Previous",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (player != null) {
                                        if (player.isPlaying) player.pause() else player.play()
                                    }
                                },
                                modifier = Modifier.size(54.dp),
                            ) {
                                Icon(
                                    painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                                    contentDescription = "Play/Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(40.dp),
                                )
                            }
                            IconButton(
                                onClick = { player?.seekToNext() },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.skip_next),
                                    contentDescription = "Next",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Geser ke atas untuk membuka",
                    color = Color.White.copy(alpha = 0.76f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Bottom home pill indicator matching screenshot
                Box(
                    modifier = Modifier
                        .width(136.dp)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.92f)),
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun LockScreenTransitionDots(progress: Float) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until 3) {
            val targetAlpha = when {
                progress >= (i + 1) / 3f -> 0.96f
                progress >= i / 3f -> 0.36f + 0.60f * ((progress - i / 3f) * 3f)
                else -> 0.28f
            }.coerceIn(0.28f, 0.96f)

            val dotAlpha by animateFloatAsState(
                targetValue = targetAlpha,
                animationSpec = tween(260, easing = FastOutSlowInEasing),
                label = "lockDotAlpha_$i",
            )
            val dotScale by animateFloatAsState(
                targetValue = if (targetAlpha > 0.75f) 1.08f else 0.95f,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
                label = "lockDotScale_$i",
            )

            Box(
                modifier = Modifier
                    .size(9.dp)
                    .graphicsLayer {
                        scaleX = dotScale
                        scaleY = dotScale
                    }
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = dotAlpha)),
            )
        }
    }
}

private fun parseLockScreenLyrics(raw: String): List<SyncLine> {
    val synced = LrcParser.parse(raw)
    if (synced.isNotEmpty()) return synced
    return raw.lineSequence()
        .map { it.replace(Regex("""\[[^\]]*]|\{[^}]*}"""), "").trim() }
        .filter { it.isNotEmpty() }
        .map { SyncLine(startMs = -1L, endMs = -1L, text = it) }
        .toList()
}

private fun formatTimeMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return "$min:${sec.toString().padStart(2, '0')}"
}
