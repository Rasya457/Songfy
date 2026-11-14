/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * SyncedLyricsView.kt — Lirik per kata ala Apple Music.
 * - Draw-phase word sweep highlight (zero recomposition on playback ticks)
 * - Frame-clock subscribed draw phase for buttery 60/120 FPS continuous sweep
 * - Distance blur & dimming
 * - Smooth spring scroll to keep active line centered
 * - Interlude dots
 * - Tap to seek
 * - On-demand translation support (Google Translate, no API key)
 */

package com.metrolist.music.ui.component

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.R
import com.metrolist.music.lyrics.SyncLine
import com.metrolist.music.lyrics.SyncWord
import com.songfy.ui.icons.SongfyIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.roundToInt

// Lead time in ms before line start so highlight stays perfectly in sync with perceived vocal
private const val LINE_SWITCH_LEAD_MS = 60L

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SyncedLyricsView(
    lines: List<SyncLine>,
    positionProvider: () -> Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    enableBlur: Boolean = true,
    // Translation params (all optional for backward compat)
    translations: List<String>? = null,
    showTranslation: Boolean = false,
    isTranslating: Boolean = false,
    onTranslateClick: (() -> Unit)? = null,
    bottomContentPadding: Dp = 160.dp,
    onUserInteraction: (() -> Unit)? = null,
    onScrollUp: (() -> Unit)? = null,
) {
    if (isLoading) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(36.dp),
                strokeWidth = 3.dp,
            )
        }
        return
    }

    if (lines.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.lyrics_not_found),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 16.sp,
            )
        }
        return
    }

    // Frame-clock ticker for continuous 60/120 FPS draw phase updates without recomposition
    var animTick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { animTick = it }
        }
    }

    // Binary search for active line: triggers recomposition ONLY on line change
    var activeIndex by remember(lines) { mutableIntStateOf(-1) }
    LaunchedEffect(lines) {
        while (isActive) {
            val pos = positionProvider() + LINE_SWITCH_LEAD_MS
            val idx = findActiveLineIndex(lines, pos)
            if (idx != activeIndex) activeIndex = idx
            delay(20)
        }
    }

    val listState = rememberLazyListState()
    val lyricItemInteraction = remember { MutableInteractionSource() }
    val currentOnUserInteraction = rememberUpdatedState(onUserInteraction)
    val currentOnScrollUp = rememberUpdatedState(onScrollUp)
    var isAutoFollowing by remember(lines) { mutableStateOf(true) }
    var isProgrammaticScroll by remember { mutableStateOf(false) }
    var userTouching by remember { mutableStateOf(false) }

    val nestedScrollConnection = remember(listState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    currentOnScrollUp.value?.invoke()
                }
                return Offset.Zero
            }
        }
    }

    // Pause auto-follow on genuine user drag/fling, including drags that begin mid-programmatic scroll.
    LaunchedEffect(listState) {
        var wasUserScrolling = false
        snapshotFlow { listState.isScrollInProgress to userTouching }
            .distinctUntilChanged()
            .collect { (scrolling, touching) ->
                if (scrolling && (!isProgrammaticScroll || touching)) {
                    isAutoFollowing = false
                    wasUserScrolling = true
                    currentOnScrollUp.value?.invoke()
                } else if (!scrolling && wasUserScrolling) {
                    wasUserScrolling = false
                    currentOnUserInteraction.value?.invoke()
                }
            }
    }

    LaunchedEffect(lines, isAutoFollowing, listState.isScrollInProgress) {
        if (!isAutoFollowing && !listState.isScrollInProgress) {
            delay(2500L)
            isAutoFollowing = true
        }
    }

    // Center active lyric line on line changes, translation toggles, and when auto-follow resumes.
    LaunchedEffect(lines, activeIndex, isAutoFollowing, showTranslation, translations) {
        if (!isAutoFollowing || activeIndex !in lines.indices) return@LaunchedEffect
        if (listState.isScrollInProgress && !isProgrammaticScroll) return@LaunchedEffect
        isProgrammaticScroll = true
        try {
            val activeItem = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == activeIndex }
            if (activeItem != null) {
                val viewport = listState.layoutInfo
                val targetOffset = (viewport.viewportStartOffset + viewport.viewportEndOffset - activeItem.size) / 2f
                listState.animateScrollBy(
                    value = activeItem.offset - targetOffset,
                    animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
                )
            } else {
                val vpHeight = listState.layoutInfo.viewportSize.height
                val approxOffset = if (vpHeight > 0) -(vpHeight * 0.38f).roundToInt() else -140
                listState.animateScrollToItem(
                    index = activeIndex,
                    scrollOffset = approxOffset,
                )
                val settled = listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == activeIndex }
                if (settled != null) {
                    val viewport = listState.layoutInfo
                    val targetOffset = (viewport.viewportStartOffset + viewport.viewportEndOffset - settled.size) / 2f
                    listState.animateScrollBy(
                        value = settled.offset - targetOffset,
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                    )
                }
            }
        } finally {
            isProgrammaticScroll = false
            if (userTouching || listState.isScrollInProgress) {
                isAutoFollowing = false
            }
        }
    }

    // Keep the active lyric centered in real time as the controls deck slides open/closed.
    LaunchedEffect(lines) {
        var previousHeight = 0
        snapshotFlow { listState.layoutInfo.viewportSize.height }
            .distinctUntilChanged()
            .collect { height ->
                if (
                    previousHeight > 0 &&
                    height != previousHeight &&
                    isAutoFollowing &&
                    activeIndex in lines.indices &&
                    !listState.isScrollInProgress &&
                    !userTouching &&
                    !isProgrammaticScroll
                ) {
                    val activeItem = listState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.index == activeIndex }
                    if (activeItem != null) {
                        val viewport = listState.layoutInfo
                        val targetOffset = (viewport.viewportStartOffset + viewport.viewportEndOffset - activeItem.size) / 2f
                        val delta = activeItem.offset - targetOffset
                        if (abs(delta) > 1f) {
                            isProgrammaticScroll = true
                            try {
                                listState.scrollBy(delta)
                            } finally {
                                isProgrammaticScroll = false
                            }
                        }
                    }
                }
                previousHeight = height
            }
    }

    BoxWithConstraints(
        modifier = modifier
            .nestedScroll(nestedScrollConnection)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                    userTouching = true
                    currentOnUserInteraction.value?.invoke()
                    try {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.none { it.pressed }) {
                                currentOnUserInteraction.value?.invoke()
                                break
                            }
                        } while (true)
                    } finally {
                        userTouching = false
                    }
                }
            },
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 28.dp, bottom = bottomContentPadding, start = 20.dp, end = 20.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(
                items = lines,
                key = { i, line -> "${line.startMs}_$i" },
            ) { index, line ->
                val distance = if (activeIndex >= 0) abs(index - activeIndex) else 2
                val isCurrent = index == activeIndex

                // Interlude gap check
                val nextLine = lines.getOrNull(index + 1)
                val showInterludeAfter = nextLine != null && (nextLine.startMs - line.endMs) >= 4500L
                val isInterludeActive = isCurrent && showInterludeAfter && positionProvider() >= line.endMs

                val lineTransitionDelay = (distance.coerceAtMost(4) * 28)

                // Stagger nearby rows so the focus handoff reads as a wave, not a block.
                val targetAlpha = when {
                    isCurrent -> 1.0f
                    distance == 1 -> 0.55f
                    distance == 2 -> 0.35f
                    else -> 0.18f
                }
                val animAlpha by animateFloatAsState(
                    targetValue = targetAlpha,
                    animationSpec = tween(
                        durationMillis = 700,
                        delayMillis = lineTransitionDelay,
                        easing = FastOutSlowInEasing,
                    ),
                    label = "lyricsLineAlpha",
                )

                // Animated scale
                val targetScale = when {
                    isCurrent -> 1.02f
                    distance == 1 -> 0.98f
                    else -> 0.95f
                }
                val animScale by animateFloatAsState(
                    targetValue = targetScale,
                    animationSpec = tween(
                        durationMillis = 400,
                        delayMillis = lineTransitionDelay,
                        easing = FastOutSlowInEasing,
                    ),
                    label = "lyricsLineScale",
                )

                // Distance-based blur (draw phase only)
                val targetBlurRadius: Dp = if (enableBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    when {
                        isCurrent -> 0.dp
                        distance == 1 -> 1.5.dp
                        distance == 2 -> 3.dp
                        else -> 5.dp
                    }
                } else 0.dp
                val blurRadius by animateDpAsState(
                    targetValue = targetBlurRadius,
                    animationSpec = tween(
                        durationMillis = 700,
                        delayMillis = lineTransitionDelay,
                        easing = FastOutSlowInEasing,
                    ),
                    label = "lyricsLineBlur",
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = animAlpha
                            scaleX = animScale
                            scaleY = animScale
                        }
                        .let {
                            if (blurRadius > 0.dp && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                it.blur(blurRadius)
                            } else it
                        }
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(
                            enabled = line.startMs >= 0,
                            interactionSource = lyricItemInteraction,
                            indication = null,
                        ) {
                            isAutoFollowing = true
                            onSeek(line.startMs)
                        },
                    horizontalAlignment = Alignment.Start,
                ) {
                    if (isCurrent && line.isWordSynced) {
                        FlowRow(
                            horizontalArrangement = Arrangement.Start,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            line.words.forEachIndexed { wi, word ->
                                ActiveKaraokeWord(
                                    word = word,
                                    positionProvider = positionProvider,
                                    animTick = animTick,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontStyle = FontStyle.Italic,
                                )
                                if (wi < line.words.lastIndex) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                            }
                        }
                    } else {
                        Text(
                            text = line.text,
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic,
                            textAlign = TextAlign.Start,
                        )
                    }

                    // Translation line (shown below original when enabled)
                    val translationText = if (showTranslation) {
                        translations?.getOrNull(index)?.takeIf { it.isNotBlank() }
                    } else null
                    AnimatedVisibility(
                        visible = translationText != null,
                        enter = fadeIn(tween(250)),
                        exit = fadeOut(tween(200)),
                    ) {
                        if (translationText != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = translationText,
                                color = Color.White.copy(alpha = if (isCurrent) 0.75f else 0.40f),
                                fontSize = 16.sp,
                                fontStyle = FontStyle.Normal,
                                fontWeight = FontWeight.Normal,
                                textAlign = TextAlign.Start,
                            )
                        }
                    }

                    // SyncLine.translation field (from existing DB translation, legacy)
                    if (translationText == null) {
                        line.translation?.let { tr ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = tr,
                                color = Color.White.copy(alpha = if (isCurrent) 0.70f else 0.35f),
                                fontSize = 15.sp,
                                fontStyle = FontStyle.Italic,
                                textAlign = TextAlign.Start,
                            )
                        }
                    }

                    // Instrumental interlude dots
                    if (showInterludeAfter && (isCurrent || index + 1 == activeIndex)) {
                        Spacer(modifier = Modifier.height(10.dp))
                        InterludeDots(isActive = isInterludeActive)
                    }
                }
            }
        }

        // Translate button — bottom-right corner of the lyrics area
        if (onTranslateClick != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 20.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (showTranslation) 0.22f else 0.10f))
                    .border(
                        width = 1.dp,
                        color = if (showTranslation) Color.White.copy(alpha = 0.40f) else Color.White.copy(alpha = 0.18f),
                        shape = CircleShape,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTranslateClick,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isTranslating) {
                    CircularProgressIndicator(
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = SongfyIcons.Translate,
                        contentDescription = if (showTranslation)
                            stringResource(R.string.lyrics_hide_translation)
                        else
                            stringResource(R.string.lyrics_translate),
                        tint = if (showTranslation) Color.White else Color.White.copy(alpha = 0.70f),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Active Word Karaoke Sweep (Draw Phase Only — 60/120 FPS continuous sweep)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ActiveKaraokeWord(
    word: SyncWord,
    positionProvider: () -> Long,
    animTick: Long,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    fontStyle: FontStyle,
) {
    val durationMs = (word.endMs - word.startMs).coerceAtLeast(120L)
    val safeEndMs = word.startMs + durationMs

    Box {
        // Base dim word
        Text(
            text = word.text,
            color = Color.White.copy(alpha = 0.30f),
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            maxLines = 1,
            softWrap = false,
        )

        // Highlight overlay with frame-clock subscribed draw-phase sweep
        Text(
            text = word.text,
            color = Color.White,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.drawWithContent {
                // Reading animTick subscribes draw phase to every VSYNC frame
                @Suppress("UNUSED_VARIABLE")
                val tick = animTick
                val pos = positionProvider()
                val rawProgress = when {
                    pos <= word.startMs -> 0f
                    pos >= safeEndMs -> 1f
                    else -> ((pos - word.startMs).toFloat() / durationMs.toFloat())
                }
                // Cubic smoothstep for liquid word reveal
                val p = (rawProgress * rawProgress * (3f - 2f * rawProgress)).coerceIn(0f, 1f)

                if (p > 0f) {
                    val sweepX = size.width * p
                    clipRect(left = 0f, top = 0f, right = sweepX, bottom = size.height) {
                        this@drawWithContent.drawContent()
                    }
                }
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Interlude Indicator: 3 pulsing breathing dots ala Apple Music
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InterludeDots(
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "interludeDots")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.2831853f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    Row(
        modifier = modifier.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0..2) {
            val sine = kotlin.math.sin(phase + i * 0.85f).coerceIn(-1f, 1f)
            val dotScale = if (isActive) (0.85f + 0.35f * ((sine + 1f) / 2f)) else 0.85f
            val dotAlpha = if (isActive) (0.35f + 0.65f * ((sine + 1f) / 2f)) else 0.25f

            Box(
                modifier = Modifier
                    .size(7.dp)
                    .graphicsLayer {
                        scaleX = dotScale
                        scaleY = dotScale
                        alpha = dotAlpha
                    }
                    .background(Color.White, CircleShape),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Fast O(log N) Binary Search for Current Line
// ─────────────────────────────────────────────────────────────────────────────

private fun findActiveLineIndex(lines: List<SyncLine>, posMs: Long): Int {
    if (lines.isEmpty() || lines.first().startMs < 0) return -1
    if (posMs < lines.first().startMs) return -1
    if (posMs >= lines.last().startMs) return lines.lastIndex

    var low = 0
    var high = lines.lastIndex
    while (low <= high) {
        val mid = (low + high).ushr(1)
        val line = lines[mid]
        val nextStart = lines.getOrNull(mid + 1)?.startMs ?: Long.MAX_VALUE

        when {
            posMs < line.startMs -> high = mid - 1
            posMs >= nextStart -> low = mid + 1
            else -> return mid
        }
    }
    return low.coerceIn(0, lines.lastIndex)
}
