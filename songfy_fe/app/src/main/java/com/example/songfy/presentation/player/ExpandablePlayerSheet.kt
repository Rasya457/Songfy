package com.lagu.songfy.presentation.player

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

// Animasi tap expand: sangat halus dan gradual khas Apple Music / iOS sheet transition (~420ms)
private val SheetTapExpandSpec = tween<Float>(
    durationMillis = 420,
    easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
)
// Animasi collapse saat back ditekan: halus & presisi (~380ms)
private val SheetCollapseSpec = tween<Float>(
    durationMillis = 380,
    easing = CubicBezierEasing(0.25f, 1f, 0.35f, 1f)
)
// Spring responsif untuk drag & fling gesture release
private val SheetFlingSpring = spring<Float>(
    dampingRatio = 0.86f,
    stiffness = Spring.StiffnessLow
)
private val SheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

@Stable
class PlayerSheetState internal constructor() {
    internal val anim: Animatable<Float, AnimationVector1D> =
        Animatable(0f).apply { updateBounds(0f, 1f) }

    /** 0f = mini player, 1f = full player. */
    val progress: Float get() = anim.value

    val isExpanded: Boolean get() = anim.value >= 0.5f

    fun expand(velocity: Float = 0f) {
        scope?.launch {
            if (velocity != 0f) {
                anim.animateTo(1f, SheetFlingSpring, initialVelocity = velocity)
            } else {
                anim.animateTo(1f, SheetTapExpandSpec)
            }
        }
    }

    fun collapse(velocity: Float = 0f) {
        scope?.launch {
            if (velocity != 0f) {
                anim.animateTo(0f, SheetFlingSpring, initialVelocity = velocity)
            } else {
                anim.animateTo(0f, SheetCollapseSpec)
            }
        }
    }

    internal var scope: kotlinx.coroutines.CoroutineScope? = null
}

@Composable
fun rememberPlayerSheetState(): PlayerSheetState {
    val scope = rememberCoroutineScope()
    return remember { PlayerSheetState() }.also { it.scope = scope }
}

/**
 * Gesture-driven Expandable Player Sheet:
 * - 1:1 finger tracking dengan momentum fling physics
 * - Zero recomposition saat drag (progress dibaca di layout/graphicsLayer)
 * - Interpolasi tinggi: saat collapsed hanya menutupi area mini player sehingga
 *   navbar di bawahnya tetap 100% clickable dan interaktif
 * - Sinkronisasi navbar turun saat sheet ditarik ke atas
 * - Dukungan Predictive Back gesture Android 13+
 */
@Composable
fun ExpandablePlayerSheet(
    state: PlayerSheetState,
    modifier: Modifier = Modifier,
    miniHeight: Dp = 62.dp,
    navBarHeight: Dp = 0.dp,
    containerColor: Color = Color(0xFF121212),
    artworkContent: (@Composable () -> Unit)? = null,
    miniPlayerContent: @Composable () -> Unit,
    fullPlayerContent: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()

        val miniHeightPx = with(density) { miniHeight.toPx() }
        val navBarHeightPx = with(density) { navBarHeight.toPx() }
        val maxHeightPx = constraints.maxHeight.toFloat()

        // Jarak vertikal yang ditempuh dari posisi collapsed ke puncak layar
        val rangePx = (maxHeightPx - miniHeightPx - navBarHeightPx).coerceAtLeast(1f)

        val isCollapsed by remember { derivedStateOf { state.anim.value < 0.01f } }
        val isVisible by remember { derivedStateOf { state.anim.value > 0f } }

        val dragState = rememberDraggableState { delta ->
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                state.anim.snapTo((state.anim.value - delta / rangePx).coerceIn(0f, 1f))
            }
        }

        // Predictive back gesture: sheet mengecil mengikuti gestur swipe back
        PredictiveBackHandler(enabled = !isCollapsed) { events ->
            val startProgress = state.anim.value
            try {
                events.collect { e ->
                    state.anim.snapTo(startProgress * (1f - 0.35f * e.progress))
                }
                state.anim.animateTo(0f, SheetCollapseSpec)
            } catch (_: CancellationException) {
                scope.launch { state.anim.animateTo(startProgress, SheetTapExpandSpec) }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .layout { measurable, c ->
                    val p = state.anim.value
                    // Tinggi elemen menyesuaikan dari miniHeight ke maxHeight
                    val currentHeight = lerp(miniHeightPx, maxHeightPx, p).roundToInt()
                    val placeable = measurable.measure(
                        Constraints.fixed(c.maxWidth, currentHeight)
                    )
                    // Translasi vertikal: saat collapsed duduk di atas navbar (rangePx), saat full di 0
                    val currentTranslationY = ((1f - p) * rangePx).roundToInt()
                    layout(c.maxWidth, currentHeight) {
                        placeable.place(0, currentTranslationY)
                    }
                }
                .graphicsLayer {
                    shape = SheetShape
                    clip = state.anim.value > 0.02f
                }
                .drawBehind {
                    // Draw phase: background transparan saat collapsed dan memudar masuk saat ditarik
                    val bgAlpha = (state.anim.value * 2f).coerceIn(0f, 1f)
                    if (bgAlpha > 0f) {
                        drawRect(containerColor.copy(alpha = bgAlpha))
                    }
                }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    onDragStarted = { state.anim.stop() },
                    onDragStopped = { velocity ->
                        val p = state.anim.value
                        val target = when {
                            velocity < -600f -> 1f  // fling up
                            velocity > 600f  -> 0f  // fling down
                            p > 0.45f        -> 1f
                            else             -> 0f
                        }
                        state.anim.animateTo(
                            targetValue = target,
                            animationSpec = SheetFlingSpring,
                            initialVelocity = -velocity / rangePx,
                        )
                    }
                )
        ) {
            // ── FULL PLAYER: fade in seiring pembukaan sheet ──
            if (isVisible) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = ((state.anim.value - 0.25f) / 0.75f).coerceIn(0f, 1f)
                        }
                ) {
                    fullPlayerContent()
                }
            }

            // ── MINI PLAYER: fade out cepat saat sheet ditarik ──
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(miniHeight)
                    .graphicsLayer {
                        alpha = (1f - state.anim.value * 4f).coerceIn(0f, 1f)
                    }
            ) {
                miniPlayerContent()
            }
        }
    }
}
