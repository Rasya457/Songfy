/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * SONGFY PATCH — 12 Nov 2026 (bug: swipe ke bawah, mini player hilang)
 * ---------------------------------------------------------------
 * File ini = BottomSheet Metrolist yang dikirim Rasya, dengan 3 perbaikan
 * di BottomSheetState.performFling / collapse / drag. API pemanggil TIDAK
 * berubah, kecuali collapse() tidak lagi memakai initialVelocity (lihat
 * catatan di collapse() — kalau fork-mu memanggil
 * state.collapse(spec, velocity), hapus argumen keduanya).
 *
 * PENYEBAB BUG (dianalisis dari kode ini + pemakaian upstream):
 * 1. collapse(flickSpring, velocityDp) meneruskan velocity fling ke bawah
 *    sebagai initialVelocity spring. Spring critically damped dengan
 *    velocity awal akan OVERSHOOT melewati collapsedBound: fling cepat
 *    (~8000 px/s) bisa mendorong value sampai menyentuh dismissedBound
 *    (0.dp). Saat itu isDismissed = true -> collapsedContent (mini
 *    player) disembunyikan DAN sheet terdorong penuh keluar layar.
 *    Kalau di saat itu user menyentuh layar lagi, animasi baliknya
 *    dibatalkan snapTo dan sheet nyangkut di dismissed = mini player
 *    hilang permanen. Lebih parah lagi: onDismiss di BottomSheetPlayer
 *    upstream memanggil player.stop() + clearMediaItems() — jadi kalau
 *    sampai masuk jalur dismiss, bukan cuma mini player yang hilang,
 *    lagunya ikut berhenti dan queue terhapus.
 * 2. Lantai drag (minBound) dibaca dari previousAnchor, yang diubah
 *    onAnchorChanged di AWAL collapse() — padahal animasinya masih jalan
 *    0.4 detik. Swipe kedua selama animasi collapse berjalan bisa
 *    menyeret sheet sampai dismissed, lalu performFling mengambil jalur
 *    dismiss (projected < collapsedBound / 2).
 * 3. wasExpanded di performFling bergantung pada anchorState
 *    (rememberUpdatedState) yang baru update setelah recomposition —
 *    rapuh terhadap timing gesture. Diganti penilaian dari lantai
 *    gesture + posisi value, yang selalu current.
 *
 * PERBAIKAN (revisi 1):
 * A. collapse() SELALU tanpa initialVelocity -> pendekatan ke
 *    collapsedBound monoton, dijamin tidak pernah overshoot / menyentuh
 *    dismissedBound. expand() tetap pakai velocity (overshoot ke atas
 *    dijepit upperBound, aman, dan memberi rasa flick).
 * B. Lantai drag disnapshot SEKALI per gesture (DragFloorHolder): gesture
 *    yang mulai dari atas mini player (value > collapsedBound) lantainya
 *    collapsedBound sampai gesture selesai — tidak bisa dismiss, apa pun
 *    yang terjadi dengan anchor di tengah jalan. Dismiss hanya bisa dari
 *    gesture yang memang mulai di mini player.
 * C. Jalur dismiss di performFling dikunci: hanya kalau gesture mulai
 *    dari mini player (!startedAboveMini) DAN projected < setengah
 *    collapsedBound. Swipe dari expanded tidak pernah bisa dismiss.
 * D. Gerakan tetap sama seperti sebelumnya (smooth ala BitChord):
 *    settle critically damped response 0.4 detik, flick 0.32 detik —
 *    yang dihilangkan cuma overshoot/glitch-nya, bukan kecepatannya.
 *
 * REVISI 2 — 12 Nov 2026 (gejala sisa: mini player hilang BENTAR lalu
 * muncul lagi + animasi terasa terlalu cepat):
 * Penyebab sisa: performFling memutuskan dari `progress`/`value` yang
 * bisa STALE saat jari dilepas — delta drag diterapkan lewat coroutine
 * per event, jadi pada swipe cepat value di onDragEnd masih tertinggal
 * dari posisi jari. Akibatnya (1 - progress) masih kecil dan performFling
 * memutuskan EXPAND: sheet melompat balik ke atas (mini player
 * menghilang), lalu delta drag yang tertinggal menyusul dan menariknya
 * turun lagi (mini player muncul lagi). Kelihatannya seperti
 * hilang-bentar-muncul-lagi.
 * E. Guard velocity di jalur wasExpanded: fling cepat KE BAWAH
 * (velocity < -250) SELALU collapse, tidak pernah expand — apa pun
 * kata progress yang stale. Fling cepat ke bawah = niat turun.
 * F. Animasi collapse diperlambat sesuai permintaan: settle response
 * 0.4 -> 0.55 detik (critically damped, tetap tanpa overshoot).
 * Flick ke atas tetap 0.32 detik supaya expand tetap responsif.
 * G. Mini player mulai fade-in lebih awal (alpha dari progress * 3,
 * bukan * 4): muncul sejak sepertiga terakhir slide, bukan cuma di
 * ujung — jadi tidak terasa "pop" muncul tiba-tiba.
 */

package com.metrolist.music.ui.component

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.metrolist.music.constants.NavigationBarAnimationSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.pow

/**
 * Bottom Sheet
 * Modified from [ViMusic](https://github.com/vfsfitvnm/ViMusic)
 */
@Composable
fun BottomSheet(
    state: BottomSheetState,
    modifier: Modifier = Modifier,
    background: @Composable (BoxScope.() -> Unit) = { },
    onDismiss: (() -> Unit)? = null,
    collapsedContent: @Composable BoxScope.() -> Unit,
    isExpandable: Boolean = true,
    morphToMiniPlayer: Boolean = false,
    miniPlayerHeight: Dp = 56.dp,
    miniPlayerHorizontalPadding: Dp = 16.dp,
    miniPlayerBottomMargin: Dp = 78.dp,
    miniPlayerCornerRadius: Dp = 28.dp,
    miniPlayerMorphOverlay: (@Composable BoxScope.() -> Unit)? = null,
    miniPlayerArtworkUrl: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    if (!morphToMiniPlayer && !state.isCollapsedOrDismissed) {
        Box(
            modifier = modifier
                .graphicsLayer {
                    // background fades during about 10%-61% progress
                    alpha = (1.4f * (state.progress.coerceAtLeast(0.1f) - 0.1f).pow(0.5f)).coerceIn(0f, 1f)
                }
                .fillMaxSize(),
            content = background
        )
    }
    SideEffect {
        state.allowOverscroll = !morphToMiniPlayer
    }

    val isSheetInteractive by remember(state, morphToMiniPlayer, isExpandable) {
        // Detach pointer capture once a collapse swipe passes the visible backdrop fade (progress <= 0.40f)
        // so immediate page scrolls don't drag the hidden sheet back up, while still allowing mid-collapse re-grab above 0.40f.
        derivedStateOf {
            isExpandable && (!morphToMiniPlayer || (!state.isCollapsedOrDismissed && (!state.isClosingOrCollapsed || state.progress > 0.40f)))
        }
    }
    val isBackInterceptEnabled by remember(state, morphToMiniPlayer, isExpandable) {
        derivedStateOf { isExpandable && (!morphToMiniPlayer || !state.isClosingOrCollapsed) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Use graphicsLayer for offset to ensure hardware acceleration and 120Hz support
            .graphicsLayer {
                val y = if (morphToMiniPlayer) {
                    // Keep the container stationary during morph collapse so only inner artwork moves;
                    // park offscreen once settled so collapsed/dismissed sheet cannot intercept touches.
                    if (state.isCollapsedOrDismissed) state.expandedBound.toPx() else 0f
                } else {
                    (state.expandedBound - state.value)
                        .toPx()
                        .coerceAtLeast(0f)
                }
                translationY = if (morphToMiniPlayer) y else y + state.overscrollPx
            }
            .then(
                if (isSheetInteractive) {
                    Modifier.pointerInput(state) {
                        val velocityTracker = VelocityTracker()
                        // Track cumulative screen-space drag delta because this Box translates with the finger;
                        // local change.position cancels out sheet movement and corrupts VelocityTracker.
                        var cumulativeDragY = 0f

                        detectVerticalDragGestures(
                            onDragStart = {
                                velocityTracker.resetTracking()
                                cumulativeDragY = 0f
                                // Cancel any ongoing settle animation at the current position before dragging.
                                state.snapTo(state.value)
                            },
                            onVerticalDrag = { change, dragAmount ->
                                cumulativeDragY += dragAmount
                                velocityTracker.addPosition(change.uptimeMillis, Offset(0f, cumulativeDragY))
                                state.dragBy(dragAmount)
                            },
                            onDragCancel = {
                                velocityTracker.resetTracking()
                                cumulativeDragY = 0f
                                state.releaseOverscroll()
                                // A cancelled gesture (e.g. stolen by the system's gesture bar)
                                // must settle to the NEAREST anchor, not teleport to mini.
                                if (state.value > (state.expandedBound + state.collapsedBound) / 2f) {
                                    state.expandSoft()
                                } else {
                                    state.collapseSoft()
                                }
                                state.endDrag()
                            },
                            onDragEnd = {
                                val velocity = -velocityTracker.calculateVelocity().y
                                velocityTracker.resetTracking()
                                cumulativeDragY = 0f
                                state.releaseOverscroll()
                                state.performFling(velocity, onDismiss)
                                state.endDrag()
                            }
                        )
                    }
                } else {
                    Modifier
                }
            )
            .graphicsLayer {
                if (!morphToMiniPlayer) {
                    val cornerRadius = (1f - state.progress.coerceIn(0f, 1f)) * 28.dp.toPx()
                    shape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius)
                    clip = true
                }
            }
    ) {
        if (!state.isCollapsedOrDismissed) {
            PredictiveBackHandler(enabled = isBackInterceptEnabled) { progress ->
                val initialValue = state.value
                try {
                    val range = initialValue - state.collapsedBound
                    progress.collect { event ->
                        state.snapToAndWait(
                            initialValue - range * event.progress.coerceIn(0f, 1f)
                        )
                    }
                    state.collapseSoft()
                } catch (_: CancellationException) {
                    state.expandSoft()
                }
            }

            // main content
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (!morphToMiniPlayer) {
                            alpha = ((state.progress - 0.08f) * 3.5f).coerceIn(0f, 1f)
                        }
                    },
                content = content
            )
        }

        if (!morphToMiniPlayer && !state.isExpanded && (onDismiss == null || !state.isDismissed)) {
            Box(
                modifier =
                Modifier
                    .graphicsLayer {
                        // Revisi 2: fade-in mulai dari sepertiga terakhir
                        // slide (progress * 3), bukan cuma di ujung (* 4) —
                        // mini player muncul bertahap, tidak "pop".
                        alpha = 1f - (state.progress * 3).coerceAtMost(1f)
                    }.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { if (isExpandable) state.expandSoft() },
                    )
                    .focusable(false)
                    .fillMaxWidth()
                    .height(state.collapsedBound),
                content = collapsedContent,
            )
        }
    }
}

/**
 * Pemegang lantai drag untuk SATU gesture. Semua delta drag (dari
 * pointerInput sheet maupun dari nested scroll list di dalamnya) mengalir
 * lewat satu DraggableState, jadi lantai disnapshot sekali di event drag
 * pertama dan dipakai sampai gesture selesai (endDrag / settle).
 */
internal class DragFloorHolder {
    val floor: MutableState<Dp?> = mutableStateOf(null)
}

@Stable
class BottomSheetState internal constructor(
    draggableState: DraggableState,
    private val coroutineScope: CoroutineScope,
    private val animatable: Animatable<Dp, AnimationVector1D>,
    private val onAnchorChanged: (Int) -> Unit,
    val collapsedBound: Dp,
    private val dragFloorHolder: DragFloorHolder,
    private val density: Density,
    private val reducedMotion: State<Boolean> = mutableStateOf(false),
    initialAnchor: Int = collapsedAnchor,
) : DraggableState by draggableState {
    private companion object {
        // d / (1 - d) / 1000 with d = 0.99 (Apple's paging deceleration rate)
        const val MOMENTUM_PROJECTION = 0.099f
    }

    var targetAnchor by mutableIntStateOf(initialAnchor)
        private set

    val isClosingOrCollapsed: Boolean
        get() = targetAnchor != expandedAnchor && dragFloorHolder.floor.value == null

    val dismissedBound: Dp
        get() = animatable.lowerBound!!

    val expandedBound: Dp
        get() = animatable.upperBound!!

    val value by animatable.asState()

    val isDismissed by derivedStateOf {
        value == animatable.lowerBound!!
    }

    val isCollapsed by derivedStateOf {
        value == collapsedBound
    }

    val isCollapsedOrDismissed by derivedStateOf {
        value <= collapsedBound
    }

    val isExpanded by derivedStateOf {
        value == animatable.upperBound
    }

    val progress by derivedStateOf {
        1f - (animatable.upperBound!! - animatable.value) / (animatable.upperBound!! - collapsedBound)
    }

    // Overscroll state for pull-down bounce effect (disabled in morphToMiniPlayer mode).
    internal var allowOverscroll: Boolean = true
    var rawOverscrollPx by mutableFloatStateOf(0f)
        private set
    var overscrollPx by mutableFloatStateOf(0f)
        private set

    /**
     * True kalau gesture yang sedang berjalan MULAI dari atas mini player
     * (value > collapsedBound saat drag pertama). Gesture seperti ini
     * tidak boleh dismiss — lantainya collapsedBound.
     */
    internal val dragStartedAboveMini: Boolean
        get() = dragFloorHolder.floor.value == collapsedBound

    /** Dipanggil saat gesture drag berakhir (onDragEnd / onDragCancel / onPostFling). */
    internal fun endDrag() {
        dragFloorHolder.floor.value = null
    }

    fun dragBy(delta: Float) {
        if (!allowOverscroll) {
            if (rawOverscrollPx != 0f || overscrollPx != 0f) {
                rawOverscrollPx = 0f
                overscrollPx = 0f
            }
            dispatchRawDelta(delta)
            return
        }
        val atTop = value >= expandedBound
        if (!reducedMotion.value && ((atTop && delta < 0f) || rawOverscrollPx > 0f)) {
            val nextOverscroll = rawOverscrollPx - delta
            if (nextOverscroll <= 0f) {
                rawOverscrollPx = 0f
                overscrollPx = 0f
                dispatchRawDelta(-nextOverscroll)
            } else {
                rawOverscrollPx = nextOverscroll
                val dim = with(density) { (expandedBound - collapsedBound).toPx() }
                overscrollPx = -(rawOverscrollPx * dim * 0.55f) / (dim + 0.55f * rawOverscrollPx)
            }
        } else {
            dispatchRawDelta(delta)
        }
    }

    fun releaseOverscroll() {
        rawOverscrollPx = 0f
        if (overscrollPx != 0f) {
            val start = overscrollPx
            if (reducedMotion.value) {
                overscrollPx = 0f
                return
            }
            coroutineScope.launch {
                animate(
                    initialValue = start,
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = 1f, stiffness = 439f),
                ) { value, _ ->
                    overscrollPx = value
                }
            }
        }
    }

    fun collapse(animationSpec: AnimationSpec<Dp>, initialVelocity: Dp = 0.dp) {
        targetAnchor = collapsedAnchor
        onAnchorChanged(collapsedAnchor)
        dragFloorHolder.floor.value = null
        coroutineScope.launch {
            // SENGAJA tanpa initialVelocity (argumennya diterima demi
            // kompatibilitas pemanggil lama, tapi diabaikan): spring
            // critically damped dengan velocity awal akan OVERSHOOT melewati
            // collapsedBound dan bisa menyentuh dismissedBound — itulah yang
            // bikin mini player hilang. Tanpa velocity, pendekatan ke
            // collapsedBound monoton: tidak pernah melewati target, jadi
            // dijamin tidak pernah menyentuh dismissedBound.
            animatable.animateTo(collapsedBound, animationSpec)
        }
    }

    fun expand(animationSpec: AnimationSpec<Dp>, initialVelocity: Dp = 0.dp) {
        targetAnchor = expandedAnchor
        onAnchorChanged(expandedAnchor)
        dragFloorHolder.floor.value = null
        coroutineScope.launch {
            // Expand BOLEH pakai velocity: overshoot ke atas dijepit oleh
            // upperBound (= expandedBound), jadi aman dan terasa flicky.
            animatable.animateTo(animatable.upperBound!!, animationSpec, initialVelocity)
        }
    }

    // Apple-style spring: damping 1.0 (no overshoot), tuned by response (seconds) instead of raw stiffness.
    private fun settleSpring(response: Float) = spring<Dp>(
        dampingRatio = 1f,
        stiffness = (2f * PI.toFloat() / response).pow(2),
        visibilityThreshold = 0.5.dp,
    )

    // Revisi 2: settle collapse diperlambat 0.4 -> 0.55 detik (permintaan
    // Rasya: jangan terlalu cepat). Flick ke atas tetap 0.32 detik supaya
    // expand tetap responsif.
    private val criticallyDampedSpring = settleSpring(response = 0.55f)
    private val flickSpring = settleSpring(response = 0.42f)

    private fun collapse() {
        collapse(criticallyDampedSpring)
    }

    private fun expand() {
        expand(criticallyDampedSpring)
    }

    fun collapseSoft() {
        collapse(settleSpring(response = 0.52f))
    }

    fun expandSoft() {
        expand(settleSpring(response = 0.68f))
    }

    fun dismiss() {
        targetAnchor = dismissedAnchor
        onAnchorChanged(dismissedAnchor)
        dragFloorHolder.floor.value = null
        coroutineScope.launch {
            animatable.animateTo(animatable.lowerBound!!)
        }
    }

    fun snapTo(value: Dp) {
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            animatable.snapTo(value)
        }
    }

    suspend fun snapToAndWait(value: Dp) {
        animatable.snapTo(value)
    }

    fun performFling(velocity: Float, onDismiss: (() -> Unit)?) {
        val velocityDp = with(density) { velocity.toDp() }
        // Snapshot lantai gesture SEBELUM settle menghapusnya: inilah yang
        // menentukan apakah dismiss boleh terjadi. Tidak pakai anchorState
        // (rememberUpdatedState) lagi — nilainya baru update setelah
        // recomposition, jadi rapuh terhadap timing gesture.
        val startedAboveMini = dragStartedAboveMini
        val wasExpanded = startedAboveMini || value > collapsedBound + 20.dp
        if (wasExpanded) {
            val dragDownFraction = (1f - progress).coerceIn(0f, 1f)
            if (velocity > 300) {
                expand(flickSpring, velocityDp)
            } else if (velocity < -180 || dragDownFraction > 0.20f || (velocity < -40 && dragDownFraction > 0.10f)) {
                collapse(criticallyDampedSpring)
            } else {
                expand(criticallyDampedSpring)
            }
            return
        }

        val belowCollapsed = value < collapsedBound
        val projected = value + velocityDp * MOMENTUM_PROJECTION

        if (velocity > 250) {
            expand(flickSpring, velocityDp)
        } else if (velocity < -PlayerSheetMotion.FlingVelocity) {
            if (belowCollapsed) {
                // Sudah di bawah mini: kembali ke mini TANPA velocity.
                collapse(criticallyDampedSpring)
            } else if (value > expandedBound - (expandedBound - collapsedBound) * 0.06f) {
                expand(flickSpring, velocityDp)
            } else {
                collapse(criticallyDampedSpring)
            }
        } else if (belowCollapsed) {
            // Jalur dismiss gesture ditutup supaya tarikan pelan pada mini player tidak pernah
            // menghilangkan mini player saat lagu masih diputar; dismiss hanya lewat dismiss() programatik.
            collapse(criticallyDampedSpring)
        } else if (projected >= collapsedBound + (expandedBound - collapsedBound) / 2f) {
            expand(flickSpring, velocityDp)
        } else {
            collapse(criticallyDampedSpring)
        }
    }

    val preUpPostDownNestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        var isTopReached = false

        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (isExpanded && available.y < 0) {
                isTopReached = false
            }

            if (isTopReached && available.y < 0 && source == NestedScrollSource.UserInput) {
                val headroomPx = with(density) { (expandedBound - value).toPx() }.coerceAtLeast(0f)
                if (headroomPx <= 0f) {
                    isTopReached = false
                    return Offset.Zero
                }
                val toConsume = available.y.coerceAtLeast(-headroomPx)
                dragBy(toConsume)
                if (-available.y >= headroomPx) {
                    isTopReached = false
                }
                return Offset(0f, toConsume)
            }
            return Offset.Zero
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            if (!isTopReached) {
                isTopReached = consumed.y == 0f && available.y > 0
            }

            return if (isTopReached && source == NestedScrollSource.UserInput) {
                dragBy(available.y)
                available
            } else {
                Offset.Zero
            }
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            return if (isTopReached) {
                val velocity = -available.y
                performFling(velocity, null)

                available
            } else {
                Velocity.Zero
            }
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            isTopReached = false
            endDrag()
            return Velocity.Zero
        }
    }
}

const val expandedAnchor = 2
const val collapsedAnchor = 1
const val dismissedAnchor = 0

@Composable
fun rememberBottomSheetState(
    dismissedBound: Dp,
    expandedBound: Dp,
    collapsedBound: Dp = dismissedBound,
    initialAnchor: Int = dismissedAnchor,
): BottomSheetState {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val reducedMotion = rememberReducedMotion()
    val currentReducedMotion = rememberUpdatedState(reducedMotion)

    var previousAnchor by rememberSaveable {
        mutableIntStateOf(initialAnchor)
    }
    val animatable = remember {
        Animatable(0.dp, Dp.VectorConverter)
    }
    // Holder lantai drag: hidup di luar remember() ber-key di bawah, supaya
    // tidak ikut ter-reset kalau bounds berubah (rotasi / nav bar muncul).
    val dragFloorHolder = remember { DragFloorHolder() }

    return remember(dismissedBound, expandedBound, collapsedBound, coroutineScope) {
        val initialValue = when (previousAnchor) {
            expandedAnchor -> expandedBound
            collapsedAnchor -> collapsedBound
            dismissedAnchor -> dismissedBound
            else -> error("Unknown BottomSheet anchor")
        }

        animatable.updateBounds(dismissedBound.coerceAtMost(expandedBound), expandedBound)
        coroutineScope.launch {
            animatable.animateTo(initialValue, NavigationBarAnimationSpec)
        }

        BottomSheetState(
            draggableState = DraggableState { delta ->
                // UNDISPATCHED updates animatable.value synchronously on drag events
                // so value/progress stay current when onDragEnd evaluates the fling.
                coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    val minBound = dragFloorHolder.floor.value
                        ?: run {
                            val floor =
                                if (animatable.value > collapsedBound) collapsedBound
                                else dismissedBound
                            dragFloorHolder.floor.value = floor
                            floor
                        }
                    val target = (animatable.value - with(density) { delta.toDp() })
                        .coerceIn(minBound.coerceAtMost(expandedBound), expandedBound)
                    animatable.snapTo(target)
                }
            },
            onAnchorChanged = { previousAnchor = it },
            coroutineScope = coroutineScope,
            animatable = animatable,
            collapsedBound = collapsedBound,
            dragFloorHolder = dragFloorHolder,
            density = density,
            reducedMotion = currentReducedMotion,
            initialAnchor = previousAnchor,
        )
    }
}
