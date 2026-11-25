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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.lyrics.LrcParser
import com.metrolist.music.lyrics.LyricsHelper
import com.metrolist.music.lyrics.SyncLine
import com.metrolist.music.playback.MusicService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
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
        enableEdgeToEdge()

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

            val service = musicServiceState.value
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
                                    // Cancelled/error: remain on lyrics instead of system lockscreen
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

    override fun onDestroy() {
        runCatching { unregisterReceiver(unlockReceiver) }
        if (isBound) {
            runCatching { unbindService(serviceConnection) }
            isBound = false
        }
        super.onDestroy()
    }
}

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

    // Update clock & date in Indonesian locale
    LaunchedEffect(Unit) {
        val dateFmt = SimpleDateFormat("EEEE d MMMM", Locale.forLanguageTag("id-ID"))
        val timeFmt = SimpleDateFormat("h.mma", Locale.US)
        while (true) {
            val now = Date()
            currentDateText = dateFmt.format(now).replaceFirstChar { it.uppercase() }
            currentTimeText = timeFmt.format(now).uppercase()
            delay(1000L)
        }
    }

    // Load lyrics fast: check DB first (0ms), otherwise parallel-fetch via LyricsHelper and cache
    LaunchedEffect(mediaMetadata?.id) {
        val meta = mediaMetadata ?: return@LaunchedEffect
        val localRaw = withContext(Dispatchers.IO) {
            database.lyrics(meta.id).firstOrNull()?.lyrics
        }
        if (!localRaw.isNullOrBlank() && localRaw != LyricsEntity.LYRICS_NOT_FOUND) {
            parsedLyrics = LrcParser.parse(localRaw)
        } else {
            parsedLyrics = emptyList()
            val fetched = withContext(Dispatchers.IO) {
                runCatching { lyricsHelper.getLyrics(meta) }.getOrNull()
            }
            if (fetched != null && fetched.lyrics.isNotBlank() && fetched.lyrics != LyricsEntity.LYRICS_NOT_FOUND) {
                parsedLyrics = LrcParser.parse(fetched.lyrics)
                withContext(Dispatchers.IO) {
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

    // Poll playback position & state smoothly (throttle when paused to save battery)
    LaunchedEffect(player) {
        while (true) {
            if (player != null) {
                if (!isSeeking) {
                    currentPositionMs = player.currentPosition.coerceAtLeast(0L)
                }
                durationMs = player.duration.coerceAtLeast(0L)
                isPlaying = player.isPlaying
            }
            delay(if (isPlaying) 65L else 400L)
        }
    }

    val effectivePosMs = if (isSeeking && durationMs > 0L) {
        (seekFraction * durationMs).toLong()
    } else {
        currentPositionMs
    }

    val activeIndex = remember(parsedLyrics, effectivePosMs) {
        if (parsedLyrics.isEmpty()) -1
        else parsedLyrics.indexOfLast { effectivePosMs + 60L >= it.startMs }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(activeIndex, parsedLyrics) {
        if (activeIndex !in parsedLyrics.indices) return@LaunchedEffect
        val activeItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == activeIndex }
        if (activeItem != null) {
            val vp = listState.layoutInfo
            val targetOffset = (vp.viewportStartOffset + vp.viewportEndOffset - activeItem.size) / 2f
            listState.animateScrollBy(
                value = activeItem.offset - targetOffset,
                animationSpec = spring(dampingRatio = 0.88f, stiffness = 175f),
            )
        } else {
            listState.animateScrollToItem(activeIndex)
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
                    if (pulled < -110f) {
                        onSwipeUpToUnlock()
                    }
                    swipeOffsetY.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = 420f))
                }
            },
            onDragCancel = {
                coroutineScope.launch {
                    swipeOffsetY.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = 420f))
                }
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141518))
            .then(unlockSwipeModifier),
    ) {
        // Blurred album artwork backdrop
        mediaMetadata?.thumbnailUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .let { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) it.blur(55.dp) else it },
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color(0x881A1A1E),
                        0.5f to Color(0x9416171B),
                        1.0f to Color(0xB40E0F13),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = swipeOffsetY.value * 0.45f
                    alpha = (1f - (abs(swipeOffsetY.value) / 520f)).coerceIn(0.65f, 1f)
                }
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom))
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Lock icon + Date + Huge Bold Clock matching reference photo
            Icon(
                painter = painterResource(R.drawable.lock),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = currentDateText,
                color = Color.White.copy(alpha = 0.94f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = currentTimeText,
                color = Color.White,
                fontSize = 74.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-2.5).sp,
                lineHeight = 76.sp,
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Center Synced Lyrics (non-scrollable so swiping up anywhere unlocks to phone Home)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                if (parsedLyrics.isEmpty()) {
                    LockScreenInterludeDots(progress = 0.35f)
                } else {
                    LazyColumn(
                        state = listState,
                        userScrollEnabled = false,
                        contentPadding = PaddingValues(vertical = 84.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(parsedLyrics) { index, line ->
                            val isCurrent = index == activeIndex
                            val distance = if (activeIndex >= 0) abs(index - activeIndex) else index
                            val nextStart = parsedLyrics.getOrNull(index + 1)?.startMs
                            val gapMs = if (nextStart != null) nextStart - line.endMs else 0L
                            val showInterlude = isCurrent && gapMs >= 4000L && effectivePosMs > line.endMs
                            val interludeProgress = if (showInterlude && gapMs > 0L) {
                                ((effectivePosMs - line.endMs).toFloat() / gapMs.toFloat()).coerceIn(0f, 1f)
                            } else 0f

                            val targetAlpha = when {
                                isCurrent -> 0.98f
                                distance == 1 -> 0.56f
                                distance == 2 -> 0.44f
                                else -> 0.26f
                            }
                            val alpha by animateFloatAsState(
                                targetValue = targetAlpha,
                                animationSpec = tween(420, easing = FastOutSlowInEasing),
                                label = "lockLyricAlpha",
                            )

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = line.text,
                                    color = Color.White,
                                    fontSize = 26.sp,
                                    lineHeight = 33.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp)
                                        .graphicsLayer { this.alpha = alpha }
                                        .let { m ->
                                            if (!isCurrent && distance >= 2 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                m.blur(1.2.dp)
                                            } else m
                                        },
                                )
                                if (showInterlude) {
                                    Spacer(modifier = Modifier.height(18.dp))
                                    LockScreenInterludeDots(progress = interludeProgress)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Liquid Glass Player Card with seekable progress bar
            val cardShape = RoundedCornerShape(26.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(Color(0x3E1A1C22))
                    .border(0.85.dp, Color.White.copy(alpha = 0.28f), cardShape)
                    .padding(horizontal = 18.dp, vertical = 15.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AsyncImage(
                        model = mediaMetadata?.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.1f)),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mediaMetadata?.title ?: "Songfy",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = mediaMetadata?.artists?.joinToString(", ") { it.name }.orEmpty(),
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Interactive seekable progress bar (tap or drag left/right to rewind/fast-forward)
                val displayFraction = if (isSeeking) {
                    seekFraction
                } else if (durationMs > 0L) {
                    (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                } else 0f

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
                        .height(24.dp)
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
                            .background(Color.White.copy(alpha = 0.26f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(displayFraction)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(Color.White),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatTimeMs(effectivePosMs),
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 11.sp,
                    )
                    val remaining = (durationMs - effectivePosMs).coerceAtLeast(0L)
                    Text(
                        text = "-${formatTimeMs(remaining)}",
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 11.sp,
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { player?.seekToPrevious() }) {
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
                    ) {
                        Icon(
                            painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                    IconButton(onClick = { player?.seekToNext() }) {
                        Icon(
                            painter = painterResource(R.drawable.skip_next),
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Geser ke atas untuk membuka",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LockScreenInterludeDots(progress: Float) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (i in 0 until 3) {
            val dotAlpha = when {
                progress >= (i + 1) / 3f -> 0.95f
                progress >= i / 3f -> 0.40f + 0.55f * ((progress - i / 3f) * 3f)
                else -> 0.30f
            }
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = dotAlpha.coerceIn(0.30f, 0.95f))),
            )
        }
    }
}

private fun formatTimeMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return "$min:${sec.toString().padStart(2, '0')}"
}
