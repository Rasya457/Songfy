package com.songfy.ui

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.roundToInt

// ---------- Konstanta ala BitChord ----------
val PillShape = RoundedCornerShape(percent = 50)
private val BarGutter = 19.dp
private val BarLift = 6.dp
private val PillInset = 4.dp
private val TabVerticalPadding = 6.5.dp
private val IconSize = 28.dp
private val BarHeight = 62.dp

const val SPRING_DAMPING = 0.72f
const val SPRING_STIFFNESS = 320f
const val STRETCH = 0.16f          // lebar nambah 16% pas pill di tengah perjalanan
const val SQUASH = STRETCH / 2f    // tinggi nyusut setengahnya (8%)
const val FLING_PROJECTION = 0.12f // detik; seberapa jauh lemparan jari "diproyeksikan"

@Immutable
data class SongfyTab(
    val label: String,
    val icon: ImageVector,
)

/**
 * Optimasi:
 * - Di bawah Android 12 (API 31) blur nggak dipakai sama sekali, diganti warna solid.
 * - noiseFactor = 0 supaya nggak ada lapisan noise tambahan.
 * - blurRadius 8dp sudah kecil, jangan dinaikin kalau nggak perlu (biaya blur naik sama radius).
 */
fun Modifier.songfyLiquidGlass(
    dark: Boolean,
    shadowElevation: Dp = 16.dp,
    shape: Shape = PillShape,
): Modifier {
    val tint = if (dark) Color(0xFF121212) else Color(0xFFFAFAFA)
    val blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val base = this
        .shadow(
            elevation = shadowElevation,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = 0.30f),
            spotColor = Color.Black.copy(alpha = 0.30f),
        )
        .clip(shape)

    val glass = if (blurSupported) {
        base.background(tint.copy(alpha = if (dark) 0.65f else 0.78f))
    } else {
        base.background(tint.copy(alpha = 0.92f))
    }
    return glass.border(0.5.dp, Color.White.copy(alpha = if (dark) 0.10f else 0.20f), shape)
}

fun Modifier.songfyLiquidGlass(
    hazeState: Any?,
    dark: Boolean,
    shadowElevation: Dp = 16.dp,
    shape: Shape = PillShape,
): Modifier = songfyLiquidGlass(dark = dark, shadowElevation = shadowElevation, shape = shape)

@Composable
fun rememberSongfyLiquidGlass(
    dark: Boolean = isSystemInDarkTheme(),
    shadowElevation: Dp = 16.dp,
    shape: Shape = PillShape,
): Modifier = remember(dark, shadowElevation, shape) {
    Modifier.songfyLiquidGlass(dark = dark, shadowElevation = shadowElevation, shape = shape)
}

/**
 * SongfyLiquidBar
 *
 * Cara kerja pill:
 * - `position` = Animatable<Float> dalam satuan indeks tab (0f..count-1).
 * - Dibaca CUMA di dalam graphicsLayer {} (fase draw), jadi animasi & drag
 *   nggak memicu recomposition sama sekali. Ini bagian terpenting buat performa.
 * - Stretch/squash dihitung dari pecahan posisi: f = position - floor(position),
 *   bump = 4f(1-f). Nol pas di tab, puncak di tengah dua tab. Satu rumus yang sama
 *   dipakai buat animasi spring DAN pas lu lagi nyeret.
 * - Lepas drag: target = tab terdekat setelah kecepatan jari diproyeksikan (fling),
 *   spring jalan dengan initialVelocity dari jari, jadi terasa nyambung.
 * - Tap di tab tetap jalan lewat clickable biasa.
 */
@Composable
fun SongfyLiquidBar(
    tabs: List<SongfyTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: Any? = null,
    dark: Boolean = isSystemInDarkTheme(),
) {
    val count = tabs.size
    val lastIndex = (count - 1).coerceAtLeast(0).toFloat()
    val scope = rememberCoroutineScope()

    val position = remember { Animatable(selectedIndex.toFloat()) }
    var tabWidthPx by remember { mutableFloatStateOf(1f) }

    val spec = remember {
        spring<Float>(
            dampingRatio = SPRING_DAMPING,
            stiffness = SPRING_STIFFNESS,
        )
    }

    LaunchedEffect(selectedIndex) {
        if (position.targetValue.roundToInt() != selectedIndex) {
            position.animateTo(selectedIndex.toFloat(), spec)
        }
    }

    val dragState = rememberDraggableState { deltaPx ->
        if (tabWidthPx > 0f) {
            scope.launch {
                position.snapTo(
                    (position.value + deltaPx / tabWidthPx).coerceIn(0f, lastIndex)
                )
            }
        }
    }

    val pillFill = remember(dark) {
        if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
    }
    val content = if (dark) Color.White else Color(0xFF111111)

    val glassModifier = rememberSongfyLiquidGlass(dark = dark, shadowElevation = 16.dp, shape = PillShape)

    Box(
        modifier = modifier
            .padding(horizontal = BarGutter)
            .padding(bottom = BarLift)
            .fillMaxWidth()
            .height(BarHeight)
            .then(glassModifier)
            .padding(PillInset)
            .onSizeChanged { size ->
                if (count > 0) tabWidthPx = size.width.toFloat() / count
            }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                onDragStopped = { velocityPxPerSec ->
                    val vIdx = velocityPxPerSec / tabWidthPx
                    val target = (position.value + vIdx * FLING_PROJECTION)
                        .roundToInt()
                        .coerceIn(0, lastIndex.toInt())
                    if (target != selectedIndex) onSelect(target)
                    position.animateTo(target.toFloat(), spec, initialVelocity = vIdx)
                },
            ),
    ) {
        // Pill seleksi: satu graphicsLayer, tanpa layout pass dan tanpa recomposition.
        Box(
            Modifier
                .fillMaxWidth(1f / count.coerceAtLeast(1))
                .fillMaxHeight()
                .graphicsLayer {
                    val p = position.value
                    translationX = p * tabWidthPx
                    val f = p - floor(p)
                    val bump = 4f * f * (1f - f)
                    scaleX = 1f + STRETCH * bump
                    scaleY = 1f - SQUASH * bump
                }
                .clip(PillShape)
                .background(pillFill)
                .border(0.5.dp, Color.White.copy(alpha = 0.10f), PillShape),
        )

        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            tabs.forEachIndexed { i, tab ->
                val selected = i == selectedIndex
                val tabColor = if (dark) {
                    if (selected) Color.White else Color.White.copy(alpha = 0.65f)
                } else {
                    if (selected) Color(0xFF111111) else Color(0xFF111111).copy(alpha = 0.65f)
                }
                val interaction = remember { MutableInteractionSource() }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(PillShape)
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            role = Role.Tab,
                        ) { onSelect(i) }
                        .padding(vertical = TabVerticalPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Image(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        modifier = Modifier.size(IconSize),
                        colorFilter = ColorFilter.tint(tabColor),
                    )
                    Text(
                        text = tab.label,
                        color = tabColor,
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

