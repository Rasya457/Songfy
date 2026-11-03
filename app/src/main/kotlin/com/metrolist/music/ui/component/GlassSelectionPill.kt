package com.metrolist.music.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch

/**
 * GlassSelectionPill.kt — Active tab lens indicator with dynamic physics-based deformation,
 * ported from Flutter's `liquid_glass_easy`.
 *
 * Dinamika gerak:
 * 1. Saat ditekan/berpindah: pill naik dulu (lift) dengan overshoot halus.
 * 2. Selama meluncur (akselerasi):
 *    - Baru meluncur (akselerasi positif): melebar secara horizontal dan gepeng secara vertikal.
 *    - Kecepatan puncak / konstan (tengah perjalanan): proporsi kembali normal (scale 1.0).
 *    - Mengerem (deselerasi): menyempit secara horizontal dan meninggi secara vertikal.
 * 3. Sampai di tujuan: mendarat lembut dan kembali ke bentuk istirahat.
 *
 * Semua perhitungan distorsi dan posisi dievaluasi di draw phase (graphicsLayer)
 * tanpa memicu recomposition per frame -> 100% 60/120 FPS tanpa lag.
 */
@Composable
fun GlassSelectionPill(
    targetX: Float,
    targetY: Float,
    targetWidth: Float,
    targetHeight: Float,
    isDragging: Boolean,
    backdrop: GlassBackdrop?,
    modifier: Modifier = Modifier,
    alphaProgress: Float = 0f,
    indicatorX: Animatable<Float, AnimationVector1D> = remember { Animatable(targetX) },
) {
    val scope = rememberCoroutineScope()
    val lift = remember { Animatable(0f) }

    var travelDistance by remember { mutableFloatStateOf(1f) }

    // Respond to targetX changes (tab click / selection)
    LaunchedEffect(targetX) {
        if (!isDragging) {
            val dist = abs(targetX - indicatorX.value)
            if (dist > 1.5f) {
                travelDistance = max(1f, dist)

                // 1) Lift overshoot animation: naik dulu, overshoot dikit, mendarat halus
                scope.launch {
                    lift.snapTo(0f)
                    lift.animateTo(
                        targetValue = 1f,
                        animationSpec = PlayerSheetMotion.PillLiftSpring,
                    )
                    lift.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 320f),
                    )
                }

                // 2) Travel animation with fluid spring
                indicatorX.animateTo(
                    targetValue = targetX,
                    animationSpec = PlayerSheetMotion.PillTravelSpring,
                )
            }
        }
    }

    // Dragging overrides
    LaunchedEffect(isDragging) {
        if (isDragging) {
            lift.animateTo(0.6f, spring(dampingRatio = 0.7f, stiffness = 400f))
        } else {
            lift.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 350f))
        }
    }

    Box(
        modifier = modifier
            .offset {
                IntOffset(indicatorX.value.roundToInt(), targetY.roundToInt())
            }
            .fixedSize(w = { targetWidth }, h = { targetHeight })
            .graphicsLayer {
                val currentX = indicatorX.value
                val distRemaining = abs(targetX - currentX)
                val p = (1f - (distRemaining / travelDistance)).coerceIn(0f, 1f)

                // Normalized deformation intensity based on distance (subtle on 1 tab, pronounced on multiple)
                val maxDeform = (travelDistance / 340f).coerceIn(0.10f, 0.30f)

                // Sine wave across flight:
                // p < 0.5: wave > 0 -> scaleX stretches, scaleY squashes (melebar & gepeng)
                // p = 0.5: wave = 0 -> normal scale (kecepatan konstan)
                // p > 0.5: wave < 0 -> scaleX narrows, scaleY elongates (menyempit & meninggi)
                val wave = if (isDragging) 0f else sin(p * 2f * PI.toFloat())

                val deformX = wave * maxDeform
                val deformY = -wave * (maxDeform * 0.45f)

                val l = lift.value
                val liftScale = 1f + 0.10f * l

                scaleX = (1f + deformX) * liftScale
                scaleY = (1f + deformY) * liftScale
                alpha = (1f - alphaProgress * 3f).coerceIn(0f, 1f)
            }
            .liquidGlass(backdrop, GlassStyle.Lens)
    )
}
