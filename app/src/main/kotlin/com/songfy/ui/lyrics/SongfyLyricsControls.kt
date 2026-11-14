package com.songfy.ui.lyrics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val LYRICS_CONTROLS_IDLE_MS = 5_000L
private const val DECK_SLIDE_MS = 420

@Stable
class SongfyLyricsAutoHideState internal constructor() {
    var controlsOpen by mutableStateOf(true)
        private set

    internal var interactionVersion by mutableIntStateOf(0)
        private set

    fun userInteracted() {
        controlsOpen = true
        interactionVersion++
    }

    fun reveal() {
        if (!controlsOpen) {
            controlsOpen = true
            interactionVersion++
        }
    }

    internal fun hide() {
        controlsOpen = false
    }
}

@Composable
fun rememberSongfyLyricsAutoHide(
    isLyricsOpen: Boolean,
    isInteracting: Boolean,
): SongfyLyricsAutoHideState {
    val state = remember { SongfyLyricsAutoHideState() }

    LaunchedEffect(isLyricsOpen) {
        if (isLyricsOpen) state.reveal()
    }
    LaunchedEffect(isLyricsOpen, isInteracting, state.controlsOpen, state.interactionVersion) {
        if (isLyricsOpen && state.controlsOpen && !isInteracting) {
            delay(LYRICS_CONTROLS_IDLE_MS)
            state.hide()
        }
    }
    return state
}

@Composable
fun SongfySlidingPlayerDeck(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    var mounted by remember { mutableStateOf(visible) }
    val reveal = remember { Animatable(if (visible) 1f else 0f) }

    LaunchedEffect(visible) {
        if (visible) {
            mounted = true
            reveal.animateTo(1f, tween(DECK_SLIDE_MS, easing = FastOutSlowInEasing))
        } else {
            reveal.animateTo(0f, tween(DECK_SLIDE_MS, easing = FastOutSlowInEasing))
            mounted = false
        }
    }

    if (mounted) {
        Box(
            modifier = modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minHeight = 0))
                    val height = (placeable.height * reveal.value)
                        .roundToInt()
                        .coerceIn(constraints.minHeight, constraints.maxHeight)
                    layout(placeable.width, height) {
                        placeable.placeRelative(0, 0)
                    }
                }
                .graphicsLayer {
                    val v = reveal.value.coerceIn(0f, 1f)
                    alpha = v
                    translationY = (1f - v) * 24.dp.toPx()
                }
                .clipToBounds(),
            content = content,
        )
    }
}

fun Modifier.songfyTrackLyricsDeckInteractions(
    autoHide: SongfyLyricsAutoHideState,
): Modifier = pointerInput(autoHide) {
    awaitEachGesture {
        awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
        autoHide.userInteracted()
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.none { it.pressed }) {
                autoHide.userInteracted()
                break
            }
        } while (true)
    }
}

@Composable
fun Modifier.songfyTapToRevealControls(
    enabled: Boolean,
    onReveal: () -> Unit,
): Modifier {
    val currentOnReveal = rememberUpdatedState(onReveal)
    return pointerInput(enabled) {
        if (!enabled) return@pointerInput
        val tapSlop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(pass = PointerEventPass.Final, requireUnconsumed = false)
            if (down.type == PointerType.Mouse) {
                return@awaitEachGesture
            }

            var dragged = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if ((change.position - down.position).getDistance() > tapSlop || event.changes.size > 1) {
                    dragged = true
                }
                if (!change.pressed) {
                    if (!dragged && !change.isConsumed) {
                        currentOnReveal.value()
                    }
                    break
                }
            } while (true)
        }
    }
}
