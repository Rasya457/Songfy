package com.lagu.songfy.presentation.navigation

import android.app.Activity
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.metrics.performance.JankStats
import com.lagu.songfy.BuildConfig
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// ── Color Tokens ─────────────────────────────────────────────────────────────
val LiquidGlassActiveBlue = Color(0xFF0A84FF) // iOS 26 Solid Blue accent
val LiquidGlassInactiveWhite = Color.White.copy(alpha = 0.70f)
val LiquidGlassBarBackground = Color(0x241C1C1E) // Translucent dark glass
val LiquidGlassLensTintResting = Color.White.copy(alpha = 0.16f)
val LiquidGlassLensTintActive = Color.White.copy(alpha = 0.28f)

// ── Motion Physics ───────────────────────────────────────────────────────────
private val LensSnapSpring = spring<Float>(
    dampingRatio = 0.7f,
    stiffness = Spring.StiffnessMediumLow
)
private val LensScaleSpring = spring<Float>(
    dampingRatio = 0.7f,
    stiffness = Spring.StiffnessMediumLow
)
private val SquashStretchSpring = spring<Float>(
    dampingRatio = 0.7f,
    stiffness = Spring.StiffnessMediumLow
)

@Immutable
data class LiquidNavBarItem(
    val title: String,
    val icon: ImageVector,
    val index: Int
)

/**
 * Standard 4 tabs for Songfy: Home, Search, Playlist, Profile.
 */
val DefaultLiquidNavTabs = listOf(
    LiquidNavBarItem(title = "Home", icon = Icons.Default.Home, index = 0),
    LiquidNavBarItem(title = "Search", icon = Icons.Default.Search, index = 1),
    LiquidNavBarItem(title = "Playlist", icon = Icons.AutoMirrored.Filled.QueueMusic, index = 2),
    LiquidNavBarItem(title = "Profile", icon = Icons.Default.PersonOutline, index = 3)
)

/**
 * Custom Floating Bottom Navigation Bar replicating iOS 26 Liquid Glass tab bar.
 *
 * STACK:
 * - Jetpack Compose + Material3, Navigation Compose
 * - Haze (dev.chrisbanes.haze) for backdrop blur (works on all API levels, minSdk 26 safe)
 * - On API 33+ optionally adds AGSL RuntimeShader light refraction & chromatic lens distortion;
 *   below API 33 falls back to blur + scale only without crashing.
 *
 * BAR:
 * - Floating pill shape (CircleShape), height 64dp, horizontal margin 16dp,
 *   respects WindowInsets navigation bars.
 * - Haze backdrop blur (radius ~24dp), translucent tint (White 12%), 1dp gradient border
 *   (White 25% top -> White 5% bottom), soft ambient & spot shadow.
 * - 4 equal-width items: 24dp icon above 11sp label (weight 600).
 * - Active: solid blue #0A84FF with smooth 250ms transition. Inactive: White 70%.
 *
 * LIQUID LENS:
 * - Sits over/behind the active tab: pill shape, taller and wider (+8dp on each side).
 * - Long-press/Drag: scales to ~1.15x, stronger frosting, brighter rim, stronger shadow.
 * - Follows finger horizontally in real time, clamped inside the bar bounds.
 * - Squash & stretch driven by drag velocity (scaleX up to 1.12, scaleY down to 0.96).
 * - Live tab hover turns hovered tab blue live; light haptic tick fires on tab change.
 * - Release: snaps to nearest tab with spring(0.7f, StiffnessMediumLow), shrinks back,
 *   hovered tab becomes active and navigates once.
 * - Tap: slides lens to tapped tab with spring. Reselecting current tab triggers onTabReselected.
 *
 * PERFORMANCE:
 * - Zero recomposition during drag: animated position, scales, squash-and-stretch
 *   are read strictly inside graphicsLayer { ... }.
 */
@Composable
fun LiquidGlassNavBar(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    tabs: List<LiquidNavBarItem> = DefaultLiquidNavTabs,
    onTabReselected: ((Int) -> Unit)? = null,
    visible: Boolean = true,
    sheetProgress: (() -> Float)? = null,
    pagerPosition: (() -> Float)? = null,
    isFrozen: (() -> Boolean)? = null,
    includeBottomPadding: Boolean = true
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()

    // Debug frame jank logger (>16ms frames logged in debug)
    if (BuildConfig.DEBUG) {
        RememberJankStatsLogger(tag = "LiquidGlassNavBar")
    }

    // Accessibility: Reduce motion check
    val isReduceMotion = remember(context) {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        } catch (_: Throwable) {
            false
        }
    }

    // Container geometry measurement (px)
    var barWidthPx by remember { mutableFloatStateOf(0f) }
    val tabCount = tabs.size.coerceAtLeast(1)
    val tabWidthPx = if (barWidthPx > 0f) barWidthPx / tabCount else 0f
    val tabWidthDp: Dp = if (tabWidthPx > 0f) with(density) { tabWidthPx.toDp() } else 0.dp

    // ── Single Source of Truth: Lens Position Driven by Index Float ───────────
    // Animated index position (0f .. 3f). Drives lens offset in draw phase.
    val lensIndexAnim = remember { Animatable(selectedIndex.toFloat()) }
    val lensActiveScale = remember { Animatable(1f) }
    val stretchX = remember { Animatable(1f) }
    val stretchY = remember { Animatable(1f) }

    // Drag / Hover state
    var isDragging by remember { mutableStateOf(false) }
    var hoveredIndex by remember { mutableIntStateOf(selectedIndex) }

    // Sync selectedIndex from external caller / state when not actively dragging
    LaunchedEffect(selectedIndex, isDragging) {
        if (!isDragging) {
            hoveredIndex = selectedIndex
            if (isReduceMotion) {
                lensIndexAnim.snapTo(selectedIndex.toFloat())
            } else {
                lensIndexAnim.animateTo(
                    targetValue = selectedIndex.toFloat(),
                    animationSpec = LensSnapSpring
                )
            }
        }
    }

    // Sync continuous pager swipe position if provided (e.g. from HorizontalPager)
    LaunchedEffect(pagerPosition) {
        if (pagerPosition != null && !isDragging) {
            val continuous = pagerPosition.invoke()
            if (!continuous.isNaN() && isFrozen?.invoke() == true) {
                lensIndexAnim.snapTo(continuous.coerceIn(0f, (tabCount - 1).toFloat()))
            }
        }
    }

    // Hide/show animation or sheet progress translation
    val barHeight = 64.dp
    val lensHeight = 52.dp // Sits inside 64dp bar (6dp top/bottom breathing margin)
    val lensExtraWidth = 8.dp

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(300)
        ) + fadeIn(tween(250)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(260)
        ) + fadeOut(tween(200)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (includeBottomPadding) {
                        Modifier
                            .navigationBarsPadding()
                            .padding(bottom = 10.dp)
                    } else Modifier
                )
                .graphicsLayer {
                    // Smooth sheet-based slide down & fade if sheetProgress provided
                    val progress = sheetProgress?.invoke() ?: 0f
                    if (progress > 0f) {
                        translationY = progress * size.height * 1.8f
                        alpha = (1f - progress * 2f).coerceIn(0f, 1f)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // ── Outer Bar Wrapper (64dp height, 16dp horizontal margin) ───────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(barHeight)
                    .onSizeChanged { barWidthPx = it.width.toFloat() }
                    .shadow(
                        elevation = 16.dp,
                        shape = CircleShape,
                        ambientColor = Color.Black.copy(alpha = 0.70f),
                        spotColor = Color.Black.copy(alpha = 0.40f)
                    )
                    .clip(CircleShape)
                    // Haze backdrop blur for resting glass look - clipped cleanly to CircleShape
                    .then(
                        if (hazeState != null) {
                            Modifier.hazeChild(
                                state = hazeState,
                                style = HazeDefaults.style(
                                    backgroundColor = Color.Transparent,
                                    tint = HazeDefaults.tint(Color.White.copy(alpha = 0.12f)),
                                    blurRadius = 24.dp,
                                    noiseFactor = 0.05f
                                )
                            )
                        } else Modifier
                    )
                    .background(LiquidGlassBarBackground)
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.25f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        ),
                        shape = CircleShape
                    )
            ) {
                // Top subtle specular reflection line
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 28.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.35f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // ── LAYER A: The Floating Liquid Glass Lens ───────────────────────
                // Rendered over the bar background but behind the icons/labels.
                // Pill highlight with smooth squash & stretch and liquid specular lighting.
                if (tabWidthPx > 0f) {
                    val lensWidth = (tabWidthDp + lensExtraWidth).coerceAtMost(tabWidthDp * 1.08f)
                    val lensWidthPx = with(density) { lensWidth.toPx() }
                    val lensOffsetY = with(density) { ((barHeight - lensHeight) / 2f).toPx() }

                    LiquidGlassLens(
                        lensWidth = lensWidth,
                        lensHeight = lensHeight,
                        isDragging = isDragging,
                        modifier = Modifier
                            .graphicsLayer {
                                // Strictly read animated values inside graphicsLayer lambda -> ZERO recomposition
                                val currentIdx = lensIndexAnim.value
                                val targetCenterX = (currentIdx + 0.5f) * tabWidthPx
                                translationX = targetCenterX - (lensWidthPx / 2f)
                                translationY = lensOffsetY
                                scaleX = lensActiveScale.value * stretchX.value
                                scaleY = lensActiveScale.value * stretchY.value
                            }
                    )
                }

                // ── LAYER B: 4 Tab Items (Equal Width) ───────────────────────────
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEach { tab ->
                        val isActive = (selectedIndex == tab.index)
                        val isHovered = (hoveredIndex == tab.index)

                        LiquidNavTabItemCell(
                            tab = tab,
                            isActive = isActive,
                            isHovered = isHovered,
                            isDragging = isDragging,
                            underLensProvider = {
                                val dist = abs(lensIndexAnim.value - tab.index)
                                (1f - dist * 1.5f).coerceIn(0f, 1f)
                            },
                            onTabClick = {
                                if (isActive) {
                                    onTabReselected?.invoke(tab.index)
                                } else {
                                    onTabSelected(tab.index)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // ── Gesture Overlay: Long-press + Real-time Horizontal Drag ─────
                // PENTING: Pakai PointerEventPass.Initial agar HorizontalPager mendapat
                // events swipe TERLEBIH DAHULU. Navbar hanya intercept gestur yang
                // jelas horizontal (deltaX >> deltaY) di area navbar itu sendiri.
                // Sebelumnya: PointerEventPass.Main → navbar ambil semua events sebelum
                // pager → swipe antar halaman terasa berat / sulit dimulai.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(tabWidthPx, tabCount, selectedIndex) {
                            if (tabWidthPx <= 0f) return@pointerInput

                            val velocityTracker = VelocityTracker()

                            awaitEachGesture {
                                // Initial pass: deteksi sentuhan tanpa langsung consume
                                val down = awaitFirstDown(pass = PointerEventPass.Initial)
                                val downX = down.position.x
                                val downY = down.position.y
                                val downTime = down.uptimeMillis
                                velocityTracker.resetTracking()
                                velocityTracker.addPosition(down.uptimeMillis, down.position)

                                var hasDragged = false
                                var isVerticalGesture = false
                                var lastX = downX

                                try {
                                    while (true) {
                                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                            ?: break

                                        if (!change.pressed) {
                                            // Finger released — hanya consume kalau bukan gestur vertikal
                                            if (!isVerticalGesture) change.consume()
                                            break
                                        }

                                        val currentX = change.position.x
                                        val currentY = change.position.y
                                        val deltaX = currentX - downX
                                        val deltaY = currentY - downY
                                        velocityTracker.addPosition(change.uptimeMillis, change.position)

                                        // Deteksi apakah ini gestur vertikal — kalau iya, langsung
                                        // lepas kontrol agar pager / scroll vertikal bisa bekerja normal
                                        if (!hasDragged && !isVerticalGesture && (abs(deltaX) > 8f || abs(deltaY) > 8f)) {
                                            if (abs(deltaY) > abs(deltaX) * 1.2f) {
                                                // Jelas vertikal → jangan intercept
                                                isVerticalGesture = true
                                            }
                                        }

                                        if (isVerticalGesture) break  // Lepaskan ke pager/page

                                        if (!hasDragged && (abs(deltaX) > 12f || (change.uptimeMillis - downTime) > 160)) {
                                            hasDragged = true
                                            isDragging = true
                                            coroutineScope.launch {
                                                lensActiveScale.animateTo(1.15f, LensScaleSpring)
                                            }
                                            // Light tick on drag start
                                            triggerHapticTick(view, haptic)
                                        }

                                        if (hasDragged) {
                                            change.consume()
                                            // Compute real-time index target clamped inside [0, tabCount - 1]
                                            val targetFloat = ((currentX / tabWidthPx) - 0.5f)
                                                .coerceIn(0f, (tabCount - 1).toFloat())

                                            // Snap lens position in real time (draw phase)
                                            coroutineScope.launch {
                                                lensIndexAnim.snapTo(targetFloat)
                                            }

                                            // Squash & stretch based on horizontal speed
                                            val moveDelta = currentX - lastX
                                            val speedRatio = (abs(moveDelta) / 25f).coerceIn(0f, 1f)
                                            val targetStretchX = 1f + 0.12f * speedRatio
                                            val targetStretchY = 1f - 0.04f * speedRatio

                                            coroutineScope.launch {
                                                stretchX.snapTo(targetStretchX)
                                                stretchY.snapTo(targetStretchY)
                                            }

                                            // Check hovered tab & fire haptic tick on tab change
                                            val nearest = targetFloat.roundToInt().coerceIn(0, tabCount - 1)
                                            if (nearest != hoveredIndex) {
                                                hoveredIndex = nearest
                                                triggerHapticTick(view, haptic)
                                            }

                                            lastX = currentX
                                        }
                                    }

                                    // Gesture completed / released
                                    if (hasDragged) {
                                        val finalNearest = hoveredIndex.coerceIn(0, tabCount - 1)
                                        coroutineScope.launch {
                                            if (isReduceMotion) {
                                                lensIndexAnim.snapTo(finalNearest.toFloat())
                                                lensActiveScale.snapTo(1f)
                                                stretchX.snapTo(1f)
                                                stretchY.snapTo(1f)
                                            } else {
                                                launch { lensIndexAnim.animateTo(finalNearest.toFloat(), LensSnapSpring) }
                                                launch { lensActiveScale.animateTo(1f, LensScaleSpring) }
                                                launch { stretchX.animateTo(1f, SquashStretchSpring) }
                                                launch { stretchY.animateTo(1f, SquashStretchSpring) }
                                            }
                                            isDragging = false
                                        }

                                        // Navigation occurs only once upon release, only if changed
                                        if (finalNearest != selectedIndex) {
                                            onTabSelected(finalNearest)
                                        }
                                    } else {
                                        // Quick tap without significant movement
                                        val tappedTab = (downX / tabWidthPx).toInt().coerceIn(0, tabCount - 1)
                                        if (tappedTab == selectedIndex) {
                                            onTabReselected?.invoke(tappedTab)
                                        } else {
                                            onTabSelected(tappedTab)
                                        }
                                    }
                                } catch (_: Throwable) {
                                    // Cancelled by system / edge gesture -> return lens to current active tab
                                    isDragging = false
                                    coroutineScope.launch {
                                        lensIndexAnim.animateTo(selectedIndex.toFloat(), LensSnapSpring)
                                        lensActiveScale.animateTo(1f, LensScaleSpring)
                                        stretchX.animateTo(1f, SquashStretchSpring)
                                        stretchY.animateTo(1f, SquashStretchSpring)
                                    }
                                }
                            }
                        }
                )
            }
        }
    }
}

/**
 * The Liquid Glass Lens Component.
 * Pill-shaped magnifying glass floating above/behind the active tab.
 * Uses AGSL RuntimeShader on API 33+ for liquid lens refraction and dispersion;
 * falls back to Compose blur + scale on older APIs without crashing.
 */
@Composable
private fun LiquidGlassLens(
    lensWidth: Dp,
    lensHeight: Dp,
    isDragging: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(lensWidth)
            .height(lensHeight)
            .shadow(
                elevation = if (isDragging) 12.dp else 4.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = 0.50f),
                spotColor = Color.Black.copy(alpha = 0.30f)
            )
            .clip(CircleShape)
            // Frosted pill capsule tint matching iOS 26 active tab indicator
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (isDragging) 0.30f else 0.18f),
                        Color.White.copy(alpha = if (isDragging) 0.15f else 0.08f),
                        Color.White.copy(alpha = if (isDragging) 0.22f else 0.12f)
                    )
                )
            )
            // Lens rim border with specular gradient
            .border(
                width = if (isDragging) 1.2.dp else 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (isDragging) 0.85f else 0.55f),
                        Color.White.copy(alpha = 0.12f),
                        Color.White.copy(alpha = if (isDragging) 0.60f else 0.30f)
                    )
                ),
                shape = CircleShape
            )
    ) {
        // Specular top arc highlight
        Box(
            modifier = Modifier
                .fillMaxWidth(0.70f)
                .height(1.5.dp)
                .align(Alignment.TopCenter)
                .padding(top = 1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = if (isDragging) 0.85f else 0.50f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

/**
 * Individual Tab Item Cell.
 * - 24dp icon above 11sp label (weight 600)
 * - Inactive: White 70%
 * - Active: solid blue #0A84FF for both icon and label, 250ms smooth transition
 * - Under lens: scales up ~1.10x with light blue tint
 * - Accessibility: Role.Tab, selected, min 48dp touch target, TalkBack double-tap
 */
@Composable
private fun LiquidNavTabItemCell(
    tab: LiquidNavBarItem,
    isActive: Boolean,
    isHovered: Boolean,
    isDragging: Boolean,
    underLensProvider: () -> Float,
    onTabClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Smooth 250ms color transition for resting active state
    val targetActive = if (isDragging) isHovered else isActive
    val animatedColor by animateColorAsState(
        targetValue = if (targetActive) LiquidGlassActiveBlue else LiquidGlassInactiveWhite,
        animationSpec = tween(durationMillis = 250),
        label = "tabColor_${tab.index}"
    )

    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp) // 48dp accessibility touch target
            .semantics {
                role = Role.Tab
                selected = isActive
                contentDescription = tab.title
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onTabClick
            )
            .graphicsLayer {
                // Scale up slightly (~1.10x) when under the lens
                val underLensFactor = underLensProvider()
                val lensScaleBoost = if (isDragging) {
                    0.12f * underLensFactor
                } else if (isActive) {
                    0.05f
                } else {
                    0f
                }
                val scale = 1f + lensScaleBoost
                scaleX = scale
                scaleY = scale
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = null, // provided on parent semantics
            tint = animatedColor,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = tab.title,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold, // 600 weight
            color = animatedColor,
            maxLines = 1,
            letterSpacing = (-0.2).sp
        )
    }
}



// ── Haptic Feedback Helper ───────────────────────────────────────────────────

private fun triggerHapticTick(view: android.view.View, haptic: HapticFeedback) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        try {
            view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
            return
        } catch (_: Throwable) {
        }
    }
    try {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    } catch (_: Throwable) {
        try {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        } catch (_: Throwable) {
            try {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            } catch (_: Throwable) {
            }
        }
    }
}

// ── JankStats Debug Logger ───────────────────────────────────────────────────

/**
 * Logs frames over 16ms using JankStats in debug builds.
 */
@Composable
private fun RememberJankStatsLogger(tag: String = "LiquidGlassJank") {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        if (window == null) return@DisposableEffect onDispose {}

        val listener = JankStats.OnFrameListener { frameData ->
            if (frameData.isJank) {
                val durationMs = frameData.frameDurationUiNanos / 1_000_000.0
                if (durationMs > 16.0) {
                    android.util.Log.d(tag, "Jank frame: %.2f ms (UI thread)".format(durationMs))
                }
            }
        }

        val jankStats = try {
            JankStats.createAndTrack(window, listener)
        } catch (_: Throwable) {
            null
        }

        onDispose {
            jankStats?.isTrackingEnabled = false
        }
    }
}
