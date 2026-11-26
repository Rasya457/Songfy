package com.songfy.ui

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
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
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import com.songfy.ui.backdrop.Backdrop
import com.songfy.ui.backdrop.backdrops.LayerBackdrop
import com.songfy.ui.backdrop.drawBackdrop
import com.songfy.ui.backdrop.effects.blur
import com.songfy.ui.backdrop.effects.colorControls
import com.songfy.ui.backdrop.effects.lens
import com.songfy.ui.backdrop.highlight.Highlight
import com.songfy.ui.backdrop.shadow.Shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.staticCompositionLocalOf
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.roundToInt

// ---------- Konstanta ala BitChord ----------
val PillShape = RoundedCornerShape(percent = 50)
val LocalSongfyHazeState = staticCompositionLocalOf<HazeState?> { null }
private val BarGutter = 19.dp
private val BarLift = 6.dp
private val PillInset = 4.dp
private val TabVerticalPadding = 6.5.dp
private val IconSize = 28.dp
private val BarHeight = 62.dp

const val SPRING_STIFFNESS = 320f
const val STRETCH = 0.16f          // lebar nambah 16% pas pill di tengah perjalanan
const val SQUASH = STRETCH / 2f    // tinggi nyusut setengahnya (8%)
const val FLING_PROJECTION = 0.12f // detik; seberapa jauh lemparan jari "diproyeksikan"

/**
 * True when the system accessibility "Reduce transparency" or "Disable window blurs" is active.
 * Apple Design: "make translucent surfaces frostier/solid: raise background opacity, drop the blur."
 */
@Composable
fun rememberReducedTransparency(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val cr = context.contentResolver
        val settingReduce = runCatching {
            Settings.Secure.getInt(cr, "accessibility_reduce_transparency", 0) == 1
        }.getOrDefault(false)
        settingReduce
    }
}

/**
 * True when the system accessibility "High text contrast" or high contrast mode is active.
 * Apple Design: "near-solid backgrounds with a defined, contrasting border."
 */
@Composable
fun rememberHighContrast(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val cr = context.contentResolver
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val highText = runCatching {
            val method = am?.javaClass?.getMethod("isHighTextContrastEnabled")
            method?.invoke(am) as? Boolean ?: false
        }.getOrDefault(false)
        val settingContrast = runCatching {
            Settings.Secure.getInt(cr, "high_text_contrast_enabled", 0) == 1
        }.getOrDefault(false)
        highText || settingContrast
    }
}

/**
 * Optimasi:
 * - Di bawah Android 12 (API 31) blur nggak dipakai sama sekali, diganti warna solid.
 * - Sesuai prinsip Apple Design (§14):
 *   - prefers-reduced-transparency: naikkan opacity jadi lebih solid (0.96f), matikan blur.
 *   - prefers-contrast: latar belakang solid (1.0f) dengan border tegas berbobot 1.5dp.
 */
val LocalSongfyBackdrop = staticCompositionLocalOf<Backdrop?> { null }
val LocalSongfyLiquidGlassEnabled = staticCompositionLocalOf { true }
private val DefaultGlassHighlight: () -> Highlight = { Highlight.Default }
private val DefaultGlassShadow: () -> Shadow = { Shadow.Default }

@Composable
fun Modifier.songfyLiquidGlass(
    dark: Boolean,
    shadowElevation: Dp = 16.dp,
    shape: Shape = PillShape,
    reducedTransparency: Boolean = rememberReducedTransparency(),
    highContrast: Boolean = rememberHighContrast(),
    exportedBackdrop: LayerBackdrop? = null,
): Modifier {
    val tint = if (dark) Color(0xFF121212) else Color(0xFFFAFAFA)
    val liquidGlassEnabled = LocalSongfyLiquidGlassEnabled.current
    val blurSupported = liquidGlassEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !reducedTransparency
    val backdrop = LocalSongfyBackdrop.current

    if (blurSupported && !highContrast && backdrop != null && shape is CornerBasedShape) {
        val density = LocalDensity.current
        val resolutionScale = 0.20f
        val blurPx = with(density) { 8.dp.toPx() } * resolutionScale
        val lensHeightPx = with(density) { (0.5f * 48f).dp.toPx() } * resolutionScale
        val lensAmountPx = with(density) { (0.5f * 48f).dp.toPx() } * resolutionScale
        val surfaceTintColor = tint.copy(alpha = 0.40f)
        val shapeLambda = remember(shape) { { shape } }
        val effectsLambda: com.songfy.ui.backdrop.BackdropEffectScope.() -> Unit =
            remember(blurPx, lensHeightPx, lensAmountPx) {
                {
                    colorControls(saturation = 1.35f)
                    blur(blurPx)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        lens(
                            refractionHeight = lensHeightPx,
                            refractionAmount = lensAmountPx,
                            depthEffect = false,
                            chromaticAberration = false,
                        )
                    }
                }
            }
        val onDrawSurfaceLambda: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit =
            remember(surfaceTintColor) {
                { drawRect(color = surfaceTintColor, size = size) }
            }
        return this.drawBackdrop(
            backdrop = backdrop,
            shape = shapeLambda,
            effects = effectsLambda,
            highlight = DefaultGlassHighlight,
            shadow = DefaultGlassShadow,
            onDrawSurface = onDrawSurfaceLambda,
            exportedBackdrop = exportedBackdrop,
            backdropScale = resolutionScale,
        )
    }

    val base = this
        .shadow(
            elevation = if (highContrast) 0.dp else shadowElevation,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = 0.30f),
            spotColor = Color.Black.copy(alpha = 0.30f),
        )
        .clip(shape)

    val backgroundAlpha = when {
        highContrast -> 1.0f
        !liquidGlassEnabled -> 0.96f
        reducedTransparency -> 0.96f
        blurSupported -> if (dark) 0.65f else 0.78f
        else -> 0.92f
    }

    val hazeState = LocalSongfyHazeState.current
    val blurStyle = remember { HazeBlurStyle { blurRadius(18.dp) } }
    val glass = if (blurSupported && hazeState != null) {
        base.hazeBlur(input = HazeInput.Sources(hazeState), style = blurStyle)
            .background(tint.copy(alpha = backgroundAlpha))
    } else {
        base.background(tint.copy(alpha = backgroundAlpha))
    }

    val borderColor = when {
        highContrast -> if (dark) Color.White.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.85f)
        dark -> Color.White.copy(alpha = 0.10f)
        else -> Color.White.copy(alpha = 0.20f)
    }
    val borderWidth = if (highContrast) 1.5.dp else 0.5.dp

    return glass.border(borderWidth, borderColor, shape)
}

@Composable
fun isSongfyDarkTheme(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

@Composable
fun Modifier.songfyGlassContainer(
    dark: Boolean = isSongfyDarkTheme(),
    shape: Shape = RoundedCornerShape(28.dp),
    shadowElevation: Dp = 24.dp,
): Modifier = this
    .clip(shape)
    .songfyLiquidGlass(
        dark = dark,
        shadowElevation = shadowElevation,
        shape = shape,
    )
    .background(if (dark) Color(0xC8141418) else Color(0xD8F6F7FA))
    .border(
        width = 0.75.dp,
        color = if (dark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.08f),
        shape = shape,
    )

