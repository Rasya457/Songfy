package com.lagu.songfy.presentation.navigation

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * State untuk merekam layer latar belakang (glass source)
 * agar dapat dibaca, diblur, dan dibiaskan oleh elemen kaca.
 */
@Stable
class GlassState internal constructor(internal val layer: GraphicsLayer) {
    internal var origin by mutableStateOf(Offset.Zero)
    var isFrozen: Boolean by mutableStateOf(false)
}

@Composable
fun rememberGlassState(): GlassState {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassState(layer) }
}

/**
 * Tandai konten yang menjadi latar belakang di balik kaca.
 * Ketika isFrozen() bernilai true (misal saat swipe HorizontalPager), konten digambar
 * langsung via drawContent() secara native sehingga transisi swipe 100% mulus
 * dan tidak ada background hitam atau snapshot yang membeku.
 */
fun Modifier.glassSource(
    state: GlassState,
    isFrozen: () -> Boolean = { state.isFrozen }
): Modifier = this
    .onGloballyPositioned { state.origin = it.positionInRoot() }
    .drawWithContent {
        if (isFrozen()) {
            drawContent()
            return@drawWithContent
        }
        val w = size.width.roundToInt()
        val h = size.height.roundToInt()
        if (w > 0 && h > 0) {
            try {
                state.layer.record(
                    density = this,
                    layoutDirection = layoutDirection,
                    size = IntSize(w, h),
                ) {
                    this@drawWithContent.drawContent()
                }
                drawLayer(state.layer)
                return@drawWithContent
            } catch (_: Throwable) {
            }
        }
        drawContent()
    }

// ---------------------------------------------------------------------------
// AGSL Refraction Shader (API 33+)
// Menghitung SDF round rect, normal arah luar, dan mendistorsi koordinat
// tekstur di sepanjang lengkungan tepi kaca dengan dispersi kromatik.
// ---------------------------------------------------------------------------

private const val REFRACTION_AGSL = """
uniform shader content;
uniform float2 size;
uniform float radius;
uniform float band;      // lebar zona pembiasan dari tepi (px)
uniform float strength;  // maksimum pergeseran (px)

float sdRoundRect(float2 p, float2 halfSize, float r) {
    float2 q = abs(p) - halfSize + float2(r, r);
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0, 0.0))) - r;
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 p = coord - halfSize;
    float d = sdRoundRect(p, halfSize, radius);        // < 0 di dalam
    float t = clamp(-d / band, 0.0, 1.0);              // 0 di tepi, 1 di dalam
    float bend = (1.0 - t) * (1.0 - t);                // kuat di tepi, hilang ke tengah

    float2 ex = float2(1.0, 0.0);
    float2 ey = float2(0.0, 1.0);
    float2 n = float2(
        sdRoundRect(p + ex, halfSize, radius) - sdRoundRect(p - ex, halfSize, radius),
        sdRoundRect(p + ey, halfSize, radius) - sdRoundRect(p - ey, halfSize, radius)
    );
    n = n / max(length(n), 0.0001);                    // normal mengarah keluar

    float2 offset = n * bend * strength;
    float2 lo = float2(0.5, 0.5);
    float2 hi = size - float2(0.5, 0.5);

    // dispersi kromatik tipis (chromatic aberration) agar tepian kaca terlihat realistis
    half4 r = content.eval(clamp(coord - offset * 1.0, lo, hi));
    half4 g = content.eval(clamp(coord - offset * 1.06, lo, hi));
    half4 b = content.eval(clamp(coord - offset * 1.12, lo, hi));
    return half4(r.r, g.g, b.b, g.a);
}
"""

/** Cache RenderEffect agar tidak dialokasikan ulang setiap frame saat ukuran statis. */
private class EffectHolder {
    var shader: Any? = null
    private var lw = -1f
    private var lh = -1f
    private var lr = -1f
    private var lb = -1f
    private var ls = -1f
    private var lbl = -1f

    fun update(
        layer: GraphicsLayer,
        w: Float, h: Float, radius: Float,
        band: Float, strength: Float, blurPx: Float,
    ) {
        if (w == lw && h == lh && radius == lr && band == lb && strength == ls && blurPx == lbl) return
        lw = w; lh = h; lr = radius; lb = band; ls = strength; lbl = blurPx

        layer.renderEffect = when {
            Build.VERSION.SDK_INT >= 33 && strength > 0f ->
                agslEffect(w, h, radius, band, strength, blurPx)
            Build.VERSION.SDK_INT >= 31 && blurPx > 0f ->
                BlurEffect(blurPx, blurPx, TileMode.Clamp)
            else -> null
        }
    }
}

@RequiresApi(33)
private fun EffectHolder.agslEffect(
    w: Float, h: Float, radius: Float,
    band: Float, strength: Float, blurPx: Float,
): androidx.compose.ui.graphics.RenderEffect {
    val s = try {
        (shader as? RuntimeShader) ?: RuntimeShader(REFRACTION_AGSL).also { shader = it }
    } catch (_: Throwable) {
        null
    }
    if (s != null) {
        try {
            s.setFloatUniform("size", w, h)
            s.setFloatUniform("radius", radius)
            s.setFloatUniform("band", band)
            s.setFloatUniform("strength", strength)
            val refract = AndroidRenderEffect.createRuntimeShaderEffect(s, "content")
            val effect = if (blurPx > 0f) {
                val blur = AndroidRenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
                AndroidRenderEffect.createChainEffect(refract, blur) // Blur dulu, lalu dibiaskan
            } else refract
            return effect.asComposeRenderEffect()
        } catch (_: Throwable) {
        }
    }
    return if (blurPx > 0f) {
        BlurEffect(blurPx, blurPx, TileMode.Clamp)
    } else {
        AndroidRenderEffect.createBlurEffect(1f, 1f, Shader.TileMode.CLAMP).asComposeRenderEffect()
    }
}

// ---------------------------------------------------------------------------
// Modifier Liquid Glass
// ---------------------------------------------------------------------------

/**
 * Modifier efek dark liquid glass khas iOS:
 * - API 33+ : Blur + Refraksi tepi (AGSL) + Dark Tint tipis + Rim Highlight
 * - API 31-32: Blur + Dark Tint + Rim Highlight
 * - API < 31 : Dark Tint semi-transparan + Rim Highlight
 */
@Composable
fun Modifier.liquidGlass(
    state: GlassState? = null,
    cornerRadius: Dp = 34.dp,
    blur: Dp = 5.dp,
    refraction: Dp = 12.dp,
    edgeWidth: Dp = 18.dp,
    tint: Color = Color.Black.copy(alpha = 0.32f),
    rimAlpha: Float = 1f,
    isFrozen: () -> Boolean = { state?.isFrozen ?: false }
): Modifier {
    val glassLayer = rememberGraphicsLayer()
    val holder = remember { EffectHolder() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val tintColor = if (Build.VERSION.SDK_INT < 31) tint.copy(alpha = maxOf(tint.alpha, 0.78f)) else tint

    return this
        .onGloballyPositioned { origin = it.positionInRoot() }
        .drawWithContent {
            val w = size.width.roundToInt()
            val h = size.height.roundToInt()
            if (w > 0 && h > 0) {
                val r = min(cornerRadius.toPx(), min(size.width, size.height) / 2f)

                // 1) Salin latar tepat di belakang elemen ini dan terapkan refraksi + blur
                if (state != null) {
                    try {
                        if (!isFrozen()) {
                            val offset = origin - state.origin
                            glassLayer.record(
                                density = this,
                                layoutDirection = layoutDirection,
                                size = IntSize(w, h),
                            ) {
                                translate(-offset.x, -offset.y) {
                                    drawLayer(state.layer)
                                }
                            }

                            holder.update(
                                layer = glassLayer,
                                w = size.width, h = size.height, radius = r,
                                band = edgeWidth.toPx(),
                                strength = refraction.toPx(),
                                blurPx = blur.toPx(),
                            )
                            glassLayer.setRoundRectOutline(
                                topLeft = Offset.Zero,
                                size = Size(size.width, size.height),
                                cornerRadius = r
                            )
                            glassLayer.clip = true
                        }
                        drawLayer(glassLayer)
                    } catch (_: Throwable) {
                    }
                }

                // 2) Tint gelap tipis agar isi di belakangnya tetap terbaca
                drawRoundRect(
                    color = tintColor,
                    cornerRadius = CornerRadius(r)
                )

                // 3) Rim highlight: specular terang di kiri atas, redup di tengah, sedikit pantulan di kanan bawah
                if (rimAlpha > 0f) {
                    val rim = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.55f * rimAlpha),
                            Color.White.copy(alpha = 0.08f * rimAlpha),
                            Color.White.copy(alpha = 0.28f * rimAlpha),
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    )
                    val stroke = 1.2.dp.toPx()
                    drawRoundRect(
                        brush = rim,
                        topLeft = Offset(stroke / 2f, stroke / 2f),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(r - stroke / 2f),
                        style = Stroke(stroke),
                    )
                }
            }
            drawContent()
        }
}
