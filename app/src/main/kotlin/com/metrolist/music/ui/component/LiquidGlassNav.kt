/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * LiquidGlassNav.kt
 * Liquid Glass bottom navigation bar + morphing mini player for Jetpack Compose.
 */

package com.metrolist.music.ui.component

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.CropAlbumArtKey
import com.metrolist.music.constants.MiniPlayerBackgroundStyle
import com.metrolist.music.constants.MiniPlayerBackgroundStyleKey
import com.metrolist.music.constants.PureBlackMiniPlayerKey
import com.metrolist.music.constants.SwipeThumbnailKey
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.ui.screens.Screens
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import com.songfy.ui.FLING_PROJECTION
import com.songfy.ui.PillShape
import com.songfy.ui.SPRING_STIFFNESS
import com.songfy.ui.SQUASH
import com.songfy.ui.STRETCH
import com.songfy.ui.icons.SongfyIconStyle
import com.songfy.ui.icons.SongfyIcons
import com.songfy.ui.icons.SongfyTabIcon
import com.songfy.ui.icons.toSongfyIcon
import com.songfy.ui.backdrop.backdrops.layerBackdrop
import com.songfy.ui.backdrop.backdrops.rememberLayerBackdrop
import com.songfy.ui.songfyLiquidGlass
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// State & Models
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Stable
class LiquidNavState(
    initialTab: Int = 0,
    private val thresholdPx: Float = 120f,
) {
    var selectedTab by mutableIntStateOf(initialTab)
    var minimized by mutableStateOf(false)
        private set
    var hasMiniPlayer by mutableStateOf(false)

    fun expand() {
        minimized = false
    }

    fun minimize() {
        minimized = true
    }

    val nestedScrollConnection = object : NestedScrollConnection {
        private var acc = 0f
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val dy = available.y
            if (dy == 0f) return Offset.Zero
            if (dy * acc < 0f) acc = 0f
            acc += dy
            when {
                acc <= -thresholdPx -> minimized = true
                acc >= thresholdPx -> minimized = false
            }
            return Offset.Zero
        }
    }
}

@Composable
fun rememberLiquidNavState(initialTab: Int = 0): LiquidNavState =
    remember { LiquidNavState(initialTab) }

val LocalLiquidNavState = compositionLocalOf<LiquidNavState?> { null }

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// Glass Surface Modifiers
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

internal fun Modifier.fixedSize(w: () -> Float, h: () -> Float): Modifier =
    layout { measurable, _ ->
        val ww = w().roundToInt().coerceAtLeast(0)
        val hh = h().roundToInt().coerceAtLeast(0)
        val placeable = measurable.measure(Constraints.fixed(ww, hh))
        layout(ww, hh) { placeable.place(0, 0) }
    }

@Composable
private fun Modifier.tap(
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    // Feedback on touch-down, not release; spring re-targets from the live value so it can be interrupted
    val pressed by source.collectIsPressedAsState()
    val reducedMotion = rememberReducedMotion()
    val pressScale = animateFloatAsState(
        targetValue = if (pressed && enabled && !reducedMotion) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 1f, stiffness = 900f),
        label = "tapScale",
    )
    return this
        .graphicsLayer {
            scaleX = pressScale.value
            scaleY = pressScale.value
        }
        .let { base ->
            // Do not attach clickable when disabled so touches pass through to the expanded player
            if (enabled) {
                base.clickable(
                    interactionSource = source,
                    indication = null,
                    onClick = onClick,
                )
            } else {
                base
            }
        }
}


// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// Liquid Glass Bottom Bar
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

private val BarHeight = 62.dp
private val MiniHeight = 56.dp
private val CircleSize = 56.dp
private val Gap = 8.dp
private val InnerPad = 4.dp
private val TabVerticalPadding = 6.5.dp

@Composable
fun LiquidGlassBottomBar(
    state: LiquidNavState,
    navigationItems: List<Screens>,
    currentRoute: String?,
    onNavItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    playerConnection: PlayerConnection? = LocalPlayerConnection.current,
    showMiniPlayer: Boolean = true,
    showTabBar: Boolean = true,
    playerExpandProgress: () -> Float = { 0f },
    onPlayerClick: () -> Unit = {},
    onSearchLongClick: (() -> Unit)? = null,
    onHomeLongHold: (() -> Unit)? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val density = LocalDensity.current
    val d = density.density
    val scope = rememberCoroutineScope()
    val reducedMotion = rememberReducedMotion()

    // 0f = fully expanded pills, 1f = minimized morphing dock
    val progress = animateFloatAsState(
        targetValue = if (state.minimized) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 1f, stiffness = 260f),
        label = "liquidMinimize",
    )

    // Synchronize selected tab index with currentRoute
    LaunchedEffect(currentRoute, navigationItems) {
        val idx = navigationItems.indexOfFirst { screen ->
            currentRoute == screen.route ||
                    (currentRoute != null && currentRoute.startsWith("${screen.route}/")) ||
                    (screen.route == "search_input" && currentRoute?.startsWith("search/") == true)
        }
        if (idx >= 0 && idx != state.selectedTab) {
            state.selectedTab = idx
        }
    }

    val isPlayingState = playerConnection?.isPlaying?.collectAsState()
    val isPlaying = isPlayingState?.value == true
    val mediaMetadataState = playerConnection?.mediaMetadata?.collectAsState()
    val mediaMetadata = mediaMetadataState?.value
    val hasTrack = mediaMetadata != null
    val effectiveShowMiniPlayer = showMiniPlayer && hasTrack
    LaunchedEffect(effectiveShowMiniPlayer) {
        state.hasMiniPlayer = effectiveShowMiniPlayer
    }
    if (!showTabBar && !effectiveShowMiniPlayer) return

    val miniProgress = if (showTabBar) progress else remember { mutableFloatStateOf(0f) }
    val isBarInteractive by remember(playerExpandProgress) {
        derivedStateOf { playerExpandProgress().coerceIn(0f, 1f) < 0.02f }
    }
    val isBarFullyHidden by remember(playerExpandProgress) {
        derivedStateOf { playerExpandProgress().coerceIn(0f, 1f) >= 0.45f }
    }
    if (isBarFullyHidden) return

    val miniPlayerBackground by rememberEnumPreference(
        MiniPlayerBackgroundStyleKey,
        defaultValue = MiniPlayerBackgroundStyle.DEFAULT,
    )
    val pureBlackMiniPlayer by rememberPreference(PureBlackMiniPlayerKey, defaultValue = false)
    val cropAlbumArt by rememberPreference(CropAlbumArtKey, defaultValue = false)
    val swipeThumbnail by rememberPreference(SwipeThumbnailKey, defaultValue = true)
    val layoutDirection = LocalLayoutDirection.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        val wc = constraints.maxWidth.toFloat()
        val circle = CircleSize.value * d
        val gap = Gap.value * d
        val miniH = MiniHeight.value * d
        val barH = BarHeight.value * d
        val pad = InnerPad.value * d
        val total = when {
            showTabBar && effectiveShowMiniPlayer -> miniH + gap + barH
            showTabBar -> barH
            else -> miniH
        }

        val n = navigationItems.size
        val itemW = if (n > 0) (wc - 2f * pad) / n else 0f
        val lastIndex = (n - 1).coerceAtLeast(0).toFloat()

        // ── BitChord travelling glass pill motion & exported backdrops ──
        val pillMotion = remember(scope) { GlassPillMotion(scope, state.selectedTab) }
        val barGlass = rememberLayerBackdrop()
        val tabRowBackdrop = rememberLayerBackdrop()
        var tabWidthPx by remember { mutableFloatStateOf(itemW) }
        var dragPos by remember { mutableFloatStateOf(state.selectedTab.toFloat()) }
        LaunchedEffect(itemW, d, reducedMotion) {
            if (itemW > 0f) {
                tabWidthPx = itemW
                pillMotion.stepDp = itemW / d
            }
            pillMotion.reduceMotion = reducedMotion
        }

        LaunchedEffect(state.selectedTab) {
            if (pillMotion.index != state.selectedTab) {
                pillMotion.animateTo(state.selectedTab)
            }
        }

        val dragState = rememberDraggableState { deltaPx ->
            if (tabWidthPx > 0f) {
                dragPos = (dragPos + deltaPx / tabWidthPx).coerceIn(0f, lastIndex)
                pillMotion.dragTo(dragPos)
            }
        }

        val dark = isSystemInDarkTheme()
        val pillFill = remember(dark) {
            if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
        }

        val containerHeight = when {
            showTabBar && effectiveShowMiniPlayer -> MiniHeight + Gap + BarHeight
            showTabBar -> BarHeight
            else -> MiniHeight
        }

        if (!isBarFullyHidden) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(containerHeight)
        ) {
            // ── 1) Mini Player Pill ──────────────────────────
            if (effectiveShowMiniPlayer) {
                var dragAccumulated by remember { mutableFloatStateOf(0f) }
                val effectiveMiniStyle =
                    if (miniPlayerBackground == MiniPlayerBackgroundStyle.DEFAULT && pureBlackMiniPlayer) {
                        MiniPlayerBackgroundStyle.PURE_BLACK
                    } else {
                        miniPlayerBackground
                    }
                val miniPillModifier = Modifier
                    .offset {
                        val p = miniProgress.value
                        IntOffset(
                            lerp(0f, circle + gap, p).roundToInt(),
                            lerp(0f, total - miniH, p).roundToInt(),
                        )
                    }
                    .fixedSize(
                        w = { lerp(wc, wc - 2f * (circle + gap), miniProgress.value) },
                        h = { miniH },
                    )
                    .graphicsLayer {
                        val ep = playerExpandProgress().coerceIn(0f, 1f)
                        alpha = (1f - ep * 2.4f).coerceIn(0f, 1f)
                        translationY = ep * 20.dp.toPx()
                    }
                    .let { base ->
                        if (swipeThumbnail && isBarInteractive) {
                            base.pointerInput(layoutDirection, playerConnection) {
                                detectHorizontalDragGestures(
                                    onDragStart = { dragAccumulated = 0f },
                                    onDragEnd = {
                                        val threshold = 80f
                                        val directed = if (layoutDirection == LayoutDirection.Rtl) -dragAccumulated else dragAccumulated
                                        if (directed < -threshold) {
                                            playerConnection.player.seekToNext()
                                        } else if (directed > threshold) {
                                            playerConnection.player.seekToPrevious()
                                        }
                                        dragAccumulated = 0f
                                    },
                                    onDragCancel = { dragAccumulated = 0f },
                                    onHorizontalDrag = { _, dragAmount ->
                                        dragAccumulated += dragAmount
                                    },
                                )
                            }
                        } else base
                    }
                    .let { base ->
                        when (effectiveMiniStyle) {
                            MiniPlayerBackgroundStyle.DEFAULT ->
                                base.songfyLiquidGlass(dark = dark, shadowElevation = 16.dp, shape = PillShape)
                            MiniPlayerBackgroundStyle.TRANSPARENT ->
                                base
                                    .shadow(8.dp, PillShape)
                                    .clip(PillShape)
                                    .background(Color.Black.copy(alpha = 0.25f))
                                    .border(1.dp, Color.White.copy(alpha = 0.14f), PillShape)
                            MiniPlayerBackgroundStyle.BLUR ->
                                base
                                    .shadow(16.dp, PillShape)
                                    .clip(PillShape)
                                    .background(Color.Black.copy(alpha = 0.65f))
                                    .border(1.dp, Color.White.copy(alpha = 0.12f), PillShape)
                            MiniPlayerBackgroundStyle.GRADIENT ->
                                base
                                    .shadow(16.dp, PillShape)
                                    .clip(PillShape)
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.80f),
                                                Color.Black.copy(alpha = 0.88f),
                                            ),
                                        ),
                                    )
                                    .border(1.dp, Color.White.copy(alpha = 0.12f), PillShape)
                            MiniPlayerBackgroundStyle.PURE_BLACK ->
                                base
                                    .shadow(16.dp, PillShape)
                                    .clip(PillShape)
                                    .background(Color.Black)
                                    .border(1.dp, Color.White.copy(alpha = 0.10f), PillShape)
                        }
                    }

                Box(modifier = miniPillModifier) {
                    if (effectiveMiniStyle == MiniPlayerBackgroundStyle.BLUR && mediaMetadata.thumbnailUrl != null) {
                        AsyncImage(
                            model = mediaMetadata.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .let { m -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) m.blur(20.dp) else m },
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f)),
                        )
                    }
                    MiniPlayerContent(
                        title = mediaMetadata.title,
                        artist = mediaMetadata.artists.joinToString { it.name },
                        thumbnailUrl = mediaMetadata.thumbnailUrl,
                        isPlaying = isPlaying,
                        progress = miniProgress,
                        density = d,
                        enabled = isBarInteractive,
                        cropAlbumArt = cropAlbumArt,
                        onPlayPause = {
                            if (isPlaying) {
                                playerConnection.player.pause()
                            } else {
                                playerConnection.player.play()
                            }
                        },
                        onNext = {
                            playerConnection.player.seekToNext()
                        },
                        onClick = {
                            onPlayerClick()
                        },
                    )
                }
            }

            if (showTabBar) {
            // 2) Tab Bar (Pill -> circle on scroll down)
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(0, (total - lerp(barH, circle, progress.value)).roundToInt())
                    }
                    .fixedSize(
                        w = { lerp(wc, circle, progress.value) },
                        h = { lerp(barH, circle, progress.value) },
                    )
                    .graphicsLayer {
                        val ep = playerExpandProgress().coerceIn(0f, 1f)
                        alpha = (1f - ep * 2.8f).coerceIn(0f, 1f)
                        translationY = ep * 72.dp.toPx()
                    }
                    .songfyLiquidGlass(
                        dark = dark,
                        shadowElevation = 16.dp,
                        shape = PillShape,
                        exportedBackdrop = barGlass,
                    )
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Horizontal,
                        // Also dead while the player sheet is expanded: an invisible bar must not
                        // fling-switch tabs (onNavItemClick collapses the player)
                        enabled = progress.value < 0.5f &&
                                n > 1 &&
                                isBarInteractive,
                        onDragStarted = {
                            dragPos = pillMotion.position
                            pillMotion.startDrag()
                        },
                        onDragStopped = { velocityPxPerSec ->
                            if (tabWidthPx > 0f) {
                                val vIdx = velocityPxPerSec / tabWidthPx
                                val target = (dragPos + vIdx * FLING_PROJECTION)
                                    .roundToInt()
                                    .coerceIn(0, lastIndex.toInt())
                                if (target != state.selectedTab) {
                                    val screen = navigationItems[target]
                                    val isSelected = currentRoute == screen.route
                                    onNavItemClick(screen, isSelected)
                                    state.selectedTab = target
                                }
                                pillMotion.release(target)
                            }
                        },
                    )
            ) {
                val pillCenter = {
                    val x = pad + (pillMotion.position + 0.5f) * itemW
                    val y = barH * 0.5f
                    androidx.compose.ui.geometry.Offset(x, y)
                }
                val restW = itemW
                val restH = barH - 2f * pad
                val growW = 14f * d
                val growH = PILL_GROW_HEIGHT.value * d
                val pillSize = {
                    val baseW = lerp(restW, restW + growW, pillMotion.liftX)
                    val baseH = lerp(restH, restH + growH, pillMotion.liftY)
                    val dev = pillMotion.deviation
                    androidx.compose.ui.geometry.Size(
                        width = (baseW * (1f + dev)).coerceAtLeast(1f),
                        height = (baseH * (1f - dev)).coerceAtLeast(1f),
                    )
                }

                // Flat resting pill behind tabs
                if (n > 0 && pillMotion.isFlat && progress.value < 0.4f) {
                    FlatSelectionPill(
                        color = pillFill,
                        modifier = Modifier
                            .pillPlacement(center = pillCenter, size = pillSize)
                            .graphicsLayer {
                                alpha = (1f - progress.value * 2.5f).coerceIn(0f, 1f)
                            },
                    )
                }

                // Tab items recorded into tabRowBackdrop so the lifted GlassSelectionPill can refract them
                Box(modifier = Modifier.fillMaxSize().layerBackdrop(tabRowBackdrop)) {
                navigationItems.forEachIndexed { i, screen ->
                    val selected = state.selectedTab == i
                    val isSearch = screen == Screens.Search
                    val isHome = screen == Screens.Home

                    NavItem(
                        screen = screen,
                        selected = selected,
                        accent = accent,
                        progress = progress,
                        density = d,
                        modifier = Modifier
                            .offset {
                                val p = progress.value
                                val expX = pad + i * itemW
                                val x = if (selected) lerp(expX, 0f, p) else expX
                                IntOffset(x.roundToInt(), 0)
                            }
                            .fixedSize(
                                w = { if (selected) lerp(itemW, circle, progress.value) else itemW },
                                h = { lerp(barH, circle, progress.value) },
                            )
                            .graphicsLayer {
                                alpha = if (selected) 1f else (1f - progress.value * 2.5f).coerceIn(0f, 1f)
                            }
                            .tap(
                                // Nav bar is invisible while the player is expanded but still hit-testable;
                                // a stray tap there navigated home. Ignore taps until the sheet is collapsed.
                                enabled = isBarInteractive,
                                onLongClick = {
                                    if (isSearch) onSearchLongClick?.invoke()
                                    if (isHome) onHomeLongHold?.invoke()
                                },
                                onClick = {
                                    if (state.minimized) {
                                        if (selected) state.expand()
                                    } else {
                                        state.selectedTab = i
                                        onNavItemClick(screen, selected)
                                    }
                                }
                            ),
                    )
                }
                }

                // Lifted travelling GlassSelectionPill above tabs (BitChord style)
                if (n > 0 && !pillMotion.isFlat && progress.value < 0.4f) {
                    GlassSelectionPill(
                        motion = pillMotion,
                        fillColor = pillFill,
                        barGlass = barGlass,
                        tabRow = tabRowBackdrop,
                        modifier = Modifier
                            .pillPlacement(center = pillCenter, size = pillSize)
                            .graphicsLayer {
                                alpha = (1f - progress.value * 2.5f).coerceIn(0f, 1f)
                            },
                    )
                }
            }

            // 3) Detached Search Circle (appears on right when minimized)
            val searchScreen = navigationItems.firstOrNull { it == Screens.Search } ?: navigationItems.lastOrNull()
            if (searchScreen != null) {
                val searchIdx = navigationItems.indexOf(searchScreen)
                val searchSelected = state.selectedTab == searchIdx

                Box(
                    modifier = Modifier
                        .offset { IntOffset((wc - circle).roundToInt(), (total - circle).roundToInt()) }
                        .size(CircleSize)
                        .graphicsLayer {
                            val p = progress.value
                            val ep = playerExpandProgress().coerceIn(0f, 1f)
                            alpha = p * (1f - ep * 2.8f).coerceIn(0f, 1f)
                            translationY = ep * 72.dp.toPx()
                            scaleX = 0.6f + 0.4f * p
                            scaleY = 0.6f + 0.4f * p
                        }
                        .songfyLiquidGlass(dark = dark, shadowElevation = 16.dp, shape = CircleShape)
                        .tap(
                            enabled = isBarInteractive,
                            onLongClick = onSearchLongClick,
                            onClick = {
                                if (state.minimized) {
                                    if (searchIdx >= 0) {
                                        state.selectedTab = searchIdx
                                        onNavItemClick(searchScreen, searchSelected)
                                    }
                                    state.expand()
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    SongfyTabIcon(
                        icon = SongfyIcons.Search,
                        selected = true,
                        contentDescription = stringResource(searchScreen.titleId),
                    )
                }
            }
            }
        }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// NavItem Component
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NavItem(
    screen: Screens,
    selected: Boolean,
    accent: Color,
    progress: State<Float>,
    density: Float,
    modifier: Modifier,
) {
    val color = if (selected) SongfyIconStyle.ActiveTint else SongfyIconStyle.InactiveTint

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.graphicsLayer {
                // When minimized, label disappears -> icon centers vertically
                translationY = 7.5f * density * progress.value
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            SongfyTabIcon(
                icon = screen.toSongfyIcon(),
                selected = selected,
                contentDescription = stringResource(screen.titleId),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(screen.titleId),
                color = color,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer {
                    alpha = (1f - progress.value * 2.2f).coerceIn(0f, 1f)
                },
            )
        }
    }
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// Mini Player Content
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
internal fun MiniPlayerContent(
    title: String,
    artist: String,
    thumbnailUrl: String?,
    isPlaying: Boolean,
    progress: State<Float>,
    density: Float,
    enabled: Boolean = true,
    cropAlbumArt: Boolean = false,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .tap(enabled = enabled, onClick = onClick)
            .padding(start = 8.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Album art thumbnail
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
        ) {
            if (thumbnailUrl != null) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    contentScale = if (cropAlbumArt) ContentScale.Crop else ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Title and artist
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .basicMarquee(
                        iterations = Int.MAX_VALUE,
                        initialDelayMillis = 1200,
                        spacing = MarqueeSpacing(32.dp),
                        velocity = 36.dp,
                    ),
            )
            Text(
                text = artist,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Play / Pause button
        Box(
            modifier = Modifier
                .size(42.dp)
                .tap(enabled = enabled, onClick = onPlayPause),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPlaying) SongfyIcons.Pause else SongfyIcons.Play,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
        }

        // Next button (shrinks width + fades when minimized so Play button glides to the right)
        Box(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val p = progress.value
                    val factor = (1f - p * 1.35f).coerceIn(0f, 1f)
                    val targetWidth = (42.dp.toPx() * factor).roundToInt()
                    val placeable = measurable.measure(
                        constraints.copy(
                            minWidth = 0,
                            maxWidth = 42.dp.roundToPx(),
                        )
                    )
                    layout(targetWidth, placeable.height) {
                        placeable.place(0, 0)
                    }
                }
                .graphicsLayer {
                    val p = progress.value
                    alpha = (1f - p * 1.8f).coerceIn(0f, 1f)
                    val s = (1f - p * 1.4f).coerceIn(0f, 1f)
                    scaleX = s
                    scaleY = s
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .tap(
                        enabled = enabled,
                        onClick = {
                            if (progress.value < 0.3f) onNext()
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = SongfyIcons.Forward,
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

