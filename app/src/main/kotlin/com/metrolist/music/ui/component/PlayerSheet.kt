/*
 * PlayerSheet.kt — layar Now Playing yang naik dari mini player (Jetpack Compose)
 *
 * Konsep (ditulis sendiri, terinspirasi cara kerja sheet player di app musik modern):
 *  - Posisi sheet dipegang MANUAL lewat satu nilai progress 0..1 (Animatable), bukan komponen bawaan
 *    Material 3. Saat di-drag, konten beneran ngikutin jari; saat dilepas, KECEPATAN jari dibawa ke
 *    spring (initialVelocity) dan menentukan lanjut naik atau balik turun. Tap, drag pelan, dan flick
 *    cepat jadi terasa konsisten.
 *  - Spring: dampingRatio NoBouncy (critically damped, nggak mantul) + stiffness 195 -> berangkat cepat,
 *    makin lama makin melambat pas mau sampai. Beda dari tween yang ujungnya ngebut.
 *  - Artwork "terbang" dari posisi cover di mini player ke posisi besar di layar Now Playing
 *    (posisi, ukuran, dan radius sudut di-lerp dari progress).
 *  - Semua animasi dibaca di fase graphicsLayer/layout -> nggak recompose per frame.
 *  - Predictive back: geser back = sheet mengecil mengikuti gestur.
 *
 * Cara nyambung ke LiquidGlassBottomBar:
 *  - miniPlayerModifier = Modifier.sheetMini(state)        -> drag dari mini player + ukur posisinya
 *  - slot art di bar    = Modifier.trackCollapsedArt(state) -> titik awal terbang artwork
 *  - modifier bar       = Modifier.hideWithSheet(state)     -> bar turun & memudar saat sheet naik
 */
package com.metrolist.music.ui.component

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Critically damped: nggak mantul, berangkat cepat lalu melambat halus. Respons ~0,45 dtk. */
val SheetSpring = PlayerSheetMotion.SheetSpring
const val FlingVelocity = PlayerSheetMotion.FlingVelocity

// ───────────────────────────────────────────────────────────────────────────────
// State
// ───────────────────────────────────────────────────────────────────────────────

@Stable
class PlayerSheetState internal constructor(private val scope: CoroutineScope) {

    private val anim = Animatable(0f)
    private var dragP = 0f

    /** 0 = tertutup (cuma mini player di bar), 1 = Now Playing penuh. Dibaca di lambda graphicsLayer/layout. */
    val progress: Float get() = anim.value
    val isVisible: Boolean get() = anim.value > 0.001f
    val isExpanded: Boolean get() = anim.value > 0.99f
    val isCollapsed: Boolean get() = anim.value < 0.01f

    /** bounds cover di mini player (koordinat root) = titik awal terbang artwork */
    var collapsedArt by mutableStateOf(Rect.Zero)
    /** y atas mini player (koordinat root) = jarak tempuh drag, supaya sheet pas ngikutin jari */
    var anchorTopPx by mutableFloatStateOf(0f)
    internal var screenHeightPx by mutableFloatStateOf(0f)

    internal val travelPx: Float
        get() = if (anchorTopPx > 1f) anchorTopPx else max(1f, screenHeightPx * 0.85f)

    fun expand() { scope.launch { anim.animateTo(1f, SheetSpring) } }
    fun collapse() { scope.launch { anim.animateTo(0f, SheetSpring) } }
    suspend fun snapTo(p: Float) { anim.snapTo(p.coerceIn(0f, 1f)) }

    internal fun onDragStart() {
        dragP = anim.value
        scope.launch { anim.stop() }
    }

    /** upPx > 0 = jari naik */
    internal fun dragBy(upPx: Float) {
        dragP = (dragP + upPx / travelPx).coerceIn(0f, 1f)
        val p = dragP
        scope.launch { anim.snapTo(p) }
    }

    /** Dilepas: flick cepat menentukan arah; kalau pelan, lihat posisinya. Kecepatan jari dibawa ke spring. */
    internal fun settle(velocityUpPxPerSec: Float) {
        val target = when {
            velocityUpPxPerSec > FlingVelocity -> 1f
            velocityUpPxPerSec < -FlingVelocity -> 0f
            dragP >= 0.5f -> 1f
            else -> 0f
        }
        scope.launch { anim.animateTo(target, SheetSpring, initialVelocity = velocityUpPxPerSec / travelPx) }
    }
}

@Composable
fun rememberPlayerSheetState(): PlayerSheetState {
    val scope = rememberCoroutineScope()
    return remember(scope) { PlayerSheetState(scope) }
}

// ───────────────────────────────────────────────────────────────────────────────
// Modifier penyambung
// ───────────────────────────────────────────────────────────────────────────────

/** Drag vertikal yang menggerakkan sheet. Pasang di mini player dan di container sheet. */
fun Modifier.sheetDrag(state: PlayerSheetState): Modifier = pointerInput(state) {
    val tracker = VelocityTracker()
    detectVerticalDragGestures(
        onDragStart = {
            tracker.resetTracking()
            state.onDragStart()
        },
        onVerticalDrag = { change, dy ->
            tracker.addPointerInputChange(change)
            change.consume()
            state.dragBy(-dy)
        },
        onDragEnd = { state.settle(-tracker.calculateVelocity().y) },
        onDragCancel = { state.settle(0f) },
    )
}

/** Untuk `miniPlayerModifier` di LiquidGlassBottomBar: drag-up membuka sheet + ukur posisi mini player. */
fun Modifier.sheetMini(state: PlayerSheetState): Modifier = this
    .onGloballyPositioned { state.anchorTopPx = it.positionInRoot().y }
    .sheetDrag(state)

/** Pasang di cover album pada slot `art` bar: titik awal artwork terbang. */
fun Modifier.trackCollapsedArt(state: PlayerSheetState): Modifier =
    onGloballyPositioned { state.collapsedArt = it.boundsInRoot() }

/** Pasang di `modifier` LiquidGlassBottomBar: turun dan memudar saat sheet naik. */
fun Modifier.hideWithSheet(state: PlayerSheetState): Modifier = graphicsLayer {
    val p = state.progress
    translationY = p * 96.dp.toPx()
    alpha = (1f - p * 3f).coerceIn(0f, 1f)
}

fun Modifier.sizePx(w: () -> Float, h: () -> Float): Modifier = layout { m, _ ->
    val ww = w().roundToInt().coerceAtLeast(0)
    val hh = h().roundToInt().coerceAtLeast(0)
    val p = m.measure(Constraints.fixed(ww, hh))
    layout(ww, hh) { p.place(0, 0) }
}

// ───────────────────────────────────────────────────────────────────────────────
// Sheet
// ───────────────────────────────────────────────────────────────────────────────

/**
 * Taruh sebagai layer paling atas di Box layar penuh (di atas konten dan bar).
 *
 * @param art     cover album (dipakai untuk artwork yang terbang)
 * @param content isi Now Playing (judul, scrubber, kontrol, ...). `artBottom` = batas bawah artwork besar,
 *                pakai sebagai padding atas supaya konten nggak numpuk di artwork.
 */
@Composable
fun PlayerSheet(
    state: PlayerSheetState,
    modifier: Modifier = Modifier,
    background: @Composable BoxScope.() -> Unit = { Box(Modifier.fillMaxSize().background(Color(0xFF0A0A0B))) },
    art: @Composable () -> Unit,
    content: @Composable BoxScope.(artBottom: Dp) -> Unit,
) {
    // predictive back: gestur geser mengecilkan sheet; dibatalkan -> balik terbuka
    val backEnabled by remember { derivedStateOf { state.progress > 0.02f } }
    PredictiveBackHandler(enabled = backEnabled) { events ->
        val start = state.progress
        try {
            events.collect { e -> state.snapTo(start * (1f - e.progress)) }
            state.collapse()
        } catch (_: CancellationException) {
            state.expand()
        }
    }

    // tertutup total -> jangan ada layer yang menangkap sentuhan di atas bar
    val visible by remember { derivedStateOf { state.isVisible } }
    if (!visible) return

    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        SideEffect { state.screenHeightPx = h }

        val top = WindowInsets.statusBars.getTop(density).toFloat()
        val artSize = min(w - with(density) { 64.dp.toPx() }, h * 0.42f)
        val artX = (w - artSize) / 2f
        val artY = top + with(density) { 72.dp.toPx() }
        val artBottom = with(density) { (artY + artSize).toDp() }

        // 1) container: naik mengikuti jari, sudut atas membulat saat belum penuh
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = state.progress
                    translationY = (1f - p) * state.travelPx
                    val r = 28.dp.toPx() * (1f - p)
                    shape = RoundedCornerShape(topStart = r, topEnd = r)
                    clip = true
                }
                .sheetDrag(state)
        ) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = (state.progress * 3f).coerceIn(0f, 1f) }) {
                background()
            }
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val p = state.progress
                    alpha = ((p - 0.35f) / 0.5f).coerceIn(0f, 1f)   // konten muncul belakangan
                    translationY = (1f - p) * 48.dp.toPx()
                }
            ) { content(artBottom) }
        }

        // 2) artwork terbang: koordinat root, lerp dari cover mini player ke artwork besar
        fun from(): Rect = state.collapsedArt.takeIf { it.width > 1f }
            ?: Rect(w * 0.05f, h * 0.9f, w * 0.05f + 120f, h * 0.9f + 120f)

        Box(
            Modifier
                .offset {
                    val c = from()
                    val p = state.progress
                    IntOffset(lerp(c.left, artX, p).roundToInt(), lerp(c.top, artY, p).roundToInt())
                }
                .sizePx(
                    w = { lerp(from().width, artSize, state.progress) },
                    h = { lerp(from().width, artSize, state.progress) },   // selalu persegi
                )
                .graphicsLayer {
                    alpha = (state.progress * 20f).coerceIn(0f, 1f)
                    shape = RoundedCornerShape(lerp(10.dp.toPx(), 22.dp.toPx(), state.progress))
                    clip = true
                }
        ) { art() }
    }
}

