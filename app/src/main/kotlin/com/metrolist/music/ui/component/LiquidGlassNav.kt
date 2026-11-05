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
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.ui.screens.Screens
import com.songfy.ui.FLING_PROJECTION
import com.songfy.ui.PillShape
import com.songfy.ui.SPRING_DAMPING
import com.songfy.ui.SPRING_STIFFNESS
import com.songfy.ui.SQUASH
import com.songfy.ui.STRETCH
import com.songfy.ui.SongfyLiquidBar
import com.songfy.ui.SongfyTab
import com.songfy.ui.rememberSongfyLiquidGlass
import com.songfy.ui.songfyLiquidGlass
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// State & Models
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Stable
class LiquidNavState(
    initialTab: Int = 0,
    private val thresholdPx: Float = 64f,
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
// Backdrop & Glass Refraction Shader
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Stable
class GlassBackdrop(
    internal val layer: GraphicsLayer,
) {
    internal var sourceOrigin by mutableStateOf(Offset.Zero)
    internal var isRecorded by mutableStateOf(false)
}

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

fun Modifier.glassSource(backdrop: GlassBackdrop): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        this
            .onGloballyPositioned { backdrop.sourceOrigin = it.positionInRoot() }
            .drawWithContent {
                backdrop.layer.record(
                    density = this,
                    layoutDirection = layoutDirection,
                    size = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                ) {
                    this@drawWithContent.drawContent()
                }
                backdrop.isRecorded = true
                drawLayer(backdrop.layer)
            }
    } else {
        this
    }

@Immutable
data class GlassStyle(
    val blur: Dp,
    val bevel: Dp,
    val strength: Dp,
    val aberration: Float,
    val saturation: Float,
    val tint: Color,
    val sheen: Float = 0.12f,
) {
    companion object {
        val Bar = GlassStyle(
            blur = 0.dp,
            bevel = 20.dp,
            strength = 16.dp,
            aberration = 0.05f,
            saturation = 1.25f,
            tint = Color(0xFF16161A).copy(alpha = 0.88f),
            sheen = 0.12f,
        )

        val Lens = GlassStyle(
            blur = 1.dp,
            bevel = 26.dp,
            strength = 22.dp,
            aberration = 0.08f,
            saturation = 1.35f,
            tint = Color.White.copy(alpha = 0.09f),
            sheen = 0.18f,
        )
    }
}

private const val GLASS_AGSL = """
uniform shader content;
uniform float2 size;
uniform float radius;
uniform float bevel;
uniform float strength;
uniform float aberration;
uniform float saturation;

float sdRoundBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + float2(r);
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0))) - r;
}

float2 clampCoord(float2 c) {
    return clamp(c, float2(0.5), size - float2(0.5));
}

half4 main(float2 coord) {
    float2 hs = size * 0.5;
    float2 p = coord - hs;
    float d = sdRoundBox(p, hs, radius);

    float e = 1.0;
    float2 g = float2(
        sdRoundBox(p + float2(e, 0.0), hs, radius) - sdRoundBox(p - float2(e, 0.0), hs, radius),
        sdRoundBox(p + float2(0.0, e), hs, radius) - sdRoundBox(p - float2(0.0, e), hs, radius)
    );
    float gl = length(g);
    float2 n = gl > 0.0001 ? g / gl : float2(0.0);

    float depth = clamp(-d / bevel, 0.0, 1.0);
    float t = 1.0 - depth;
    float lens = t * t * (3.0 - 2.0 * t);
    lens = lens * lens;

    float2 off = -n * lens * strength;

    half4 c;
    c.r = content.eval(clampCoord(coord + off * (1.0 + aberration))).r;
    c.g = content.eval(clampCoord(coord + off)).g;
    c.b = content.eval(clampCoord(coord + off * (1.0 - aberration))).b;
    c.a = 1.0;

    half l = dot(c.rgb, half3(0.299, 0.587, 0.114));
    c.rgb = mix(half3(l), c.rgb, half(saturation));
    c.rgb += half(lens * 0.10);
    return c;
}
"""

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
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = source,
        indication = null,
        onClick = onClick,
    )
}

private fun Modifier.fadingEdges(fade: Dp = 14.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val f = (fade.toPx() / size.width).coerceIn(0f, 0.5f)
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Transparent,
                f to Color.Black,
                1f - f to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

@Composable
internal fun Modifier.liquidGlass(
    backdrop: GlassBackdrop? = null,
    style: GlassStyle = GlassStyle.Bar,
    shape: Shape = PillShape,
): Modifier {
    val dark = isSystemInDarkTheme()
    val canUseAgsl = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            backdrop != null && backdrop.isRecorded

    return if (canUseAgsl) {
        agslGlass(backdrop, style)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = style.sheen),
                        Color.White.copy(alpha = style.sheen * 0.12f),
                    )
                )
            )
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = if (dark) 0.12f else 0.20f),
                shape = shape,
            )
    } else {
        this.songfyLiquidGlass(dark = dark, shadowElevation = 16.dp, shape = shape)
    }
}

@Composable
private fun Modifier.fallbackGlass(style: GlassStyle): Modifier {
    return this
        .clip(CircleShape)
        .background(style.tint)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun Modifier.agslGlass(backdrop: GlassBackdrop, style: GlassStyle): Modifier {
    val glassLayer = rememberGraphicsLayer()
    val shader = remember { RuntimeShader(GLASS_AGSL) }
    var origin by remember { mutableStateOf(Offset.Zero) }

    return this
        .onGloballyPositioned { origin = it.positionInRoot() }
        .clip(CircleShape)
        .drawWithContent {
            val w = size.width
            val h = size.height
            val rel = origin - backdrop.sourceOrigin

            shader.setFloatUniform("size", w, h)
            shader.setFloatUniform("radius", min(w, h) / 2f)
            shader.setFloatUniform("bevel", max(1f, min(style.bevel.toPx(), min(w, h) / 2f)))
            shader.setFloatUniform("strength", style.strength.toPx())
            shader.setFloatUniform("aberration", style.aberration)
            shader.setFloatUniform("saturation", style.saturation)

            val refract = RenderEffect.createRuntimeShaderEffect(shader, "content")
            val blurPx = style.blur.toPx()
            val effect = if (blurPx > 0.5f) {
                RenderEffect.createChainEffect(
                    RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP),
                    refract,
                )
            } else refract
            glassLayer.renderEffect = effect.asComposeRenderEffect()

            glassLayer.record(
                density = this,
                layoutDirection = layoutDirection,
                size = IntSize(w.roundToInt(), h.roundToInt()),
            ) {
                translate(left = -rel.x, top = -rel.y) { drawLayer(backdrop.layer) }
            }
            drawLayer(glassLayer)
            drawRect(style.tint)
            drawContent()
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
    backdrop: GlassBackdrop? = null,
    playerConnection: PlayerConnection? = LocalPlayerConnection.current,
    showMiniPlayer: Boolean = true,
    playerSheetState: PlayerSheetState? = null,
    onPlayerClick: () -> Unit = {},
    onSearchLongClick: (() -> Unit)? = null,
    onHomeLongHold: (() -> Unit)? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val density = LocalDensity.current
    val d = density.density
    val scope = rememberCoroutineScope()

    // 0f = fully expanded pills, 1f = minimized morphing dock
    val progress = animateFloatAsState(
        targetValue = if (state.minimized) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 260f),
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
        val total = if (effectiveShowMiniPlayer) (miniH + gap + barH) else barH

        val n = navigationItems.size
        val itemW = if (n > 0) (wc - 2f * pad) / n else 0f
        val lastIndex = (n - 1).coerceAtLeast(0).toFloat()

        // ── BitChord draw-phase pill Animatable & Spec ──
        val position = remember { Animatable(state.selectedTab.toFloat()) }
        var tabWidthPx by remember { mutableFloatStateOf(itemW) }
        LaunchedEffect(itemW) {
            if (itemW > 0f) tabWidthPx = itemW
        }

        val spec = remember {
            spring<Float>(
                dampingRatio = SPRING_DAMPING,
                stiffness = SPRING_STIFFNESS,
            )
        }

        LaunchedEffect(state.selectedTab) {
            if (position.targetValue.roundToInt() != state.selectedTab) {
                position.animateTo(state.selectedTab.toFloat(), spec)
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

        val dark = isSystemInDarkTheme()
        val pillFill = remember(dark) {
            if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (effectiveShowMiniPlayer) (MiniHeight + Gap + BarHeight) else BarHeight)
        ) {
            // ── 1) Mini Player Pill ──────────────────────────
            if (effectiveShowMiniPlayer) {
                Box(
                    modifier = Modifier
                        .offset {
                            val p = progress.value
                            IntOffset(
                                lerp(0f, circle + gap, p).roundToInt(),
                                lerp(0f, total - miniH, p).roundToInt(),
                            )
                        }
                        .fixedSize(
                            w = { lerp(wc, wc - 2f * (circle + gap), progress.value) },
                            h = { miniH },
                        )
                        .songfyLiquidGlass(dark = dark, shadowElevation = 16.dp, shape = PillShape)
                        .then(if (playerSheetState != null) Modifier.sheetMini(playerSheetState) else Modifier)
                ) {
                    MiniPlayerContent(
                        title = mediaMetadata.title,
                        artist = mediaMetadata.artists.joinToString { it.name },
                        thumbnailUrl = mediaMetadata.thumbnailUrl,
                        isPlaying = isPlaying,
                        progress = progress,
                        density = d,
                        playerSheetState = playerSheetState,
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
                            playerSheetState?.expand()
                            onPlayerClick()
                        },
                    )
                }
            }

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
                    .songfyLiquidGlass(dark = dark, shadowElevation = 16.dp, shape = PillShape)
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Horizontal,
                        enabled = progress.value < 0.5f && n > 1,
                        onDragStopped = { velocityPxPerSec ->
                            if (tabWidthPx > 0f) {
                                val vIdx = velocityPxPerSec / tabWidthPx
                                val target = (position.value + vIdx * FLING_PROJECTION)
                                    .roundToInt()
                                    .coerceIn(0, lastIndex.toInt())
                                if (target != state.selectedTab) {
                                    val screen = navigationItems[target]
                                    val isSelected = currentRoute == screen.route
                                    onNavItemClick(screen, isSelected)
                                    state.selectedTab = target
                                }
                                scope.launch {
                                    position.animateTo(target.toFloat(), spec, initialVelocity = vIdx)
                                }
                            }
                        },
                    )
            ) {
                // Lens indicator behind active tab:
                // Evaluated ONLY in graphicsLayer {} (draw phase) - 0 recomposition & 0 layout pass!
                if (n > 0) {
                    Box(
                        Modifier
                            .fixedSize(
                                w = { itemW },
                                h = { barH - 2f * pad },
                            )
                            .graphicsLayer {
                                val p = position.value
                                translationX = pad + p * itemW
                                translationY = pad
                                val f = p - floor(p)
                                val bump = 4f * f * (1f - f)
                                scaleX = 1f + STRETCH * bump
                                scaleY = 1f - SQUASH * bump
                                alpha = (1f - progress.value * 2.5f).coerceIn(0f, 1f)
                            }
                            .clip(PillShape)
                            .background(pillFill)
                            .border(0.5.dp, Color.White.copy(alpha = if (dark) 0.12f else 0.20f), PillShape),
                    )
                }

                // Tab items
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
                            alpha = p
                            scaleX = 0.6f + 0.4f * p
                            scaleY = 0.6f + 0.4f * p
                        }
                        .songfyLiquidGlass(dark = dark, shadowElevation = 16.dp, shape = CircleShape)
                        .tap(
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
                    val iconRes = if (searchSelected) searchScreen.iconIdActive else searchScreen.iconIdInactive
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = stringResource(searchScreen.titleId),
                        tint = if (searchSelected) accent else Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }
    }
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// NavItem Component
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun NavItem(
    screen: Screens,
    selected: Boolean,
    accent: Color,
    progress: State<Float>,
    density: Float,
    modifier: Modifier,
) {
    val color by animateColorAsState(if (selected) accent else Color.White, label = "tabTint")
    val iconRes = if (selected) screen.iconIdActive else screen.iconIdInactive

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.graphicsLayer {
                // When minimized, label disappears -> icon centers vertically
                translationY = 7.5f * density * progress.value
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = stringResource(screen.titleId),
                tint = color,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(screen.titleId),
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
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
private fun MiniPlayerContent(
    title: String,
    artist: String,
    thumbnailUrl: String?,
    isPlaying: Boolean,
    progress: State<Float>,
    density: Float,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onClick: () -> Unit,
    playerSheetState: PlayerSheetState? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .tap(onClick = onClick)
            .padding(start = 8.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Album art thumbnail
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .then(if (playerSheetState != null) Modifier.trackCollapsedArt(playerSheetState) else Modifier)
        ) {
            if (thumbnailUrl != null) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
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
                .tap(onClick = onPlayPause),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = if (isPlaying) R.drawable.pause else R.drawable.play),
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
        }

        // Next button (shrinks + fades when minimized to fit narrower pill)
        Box(
            modifier = Modifier
                .fixedSize(
                    w = { 42f * density * (1f - (progress.value * 1.4f).coerceIn(0f, 1f)) },
                    h = { 42f * density },
                )
                .graphicsLayer {
                    alpha = (1f - progress.value * 1.8f).coerceIn(0f, 1f)
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .tap(onClick = onNext),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.skip_next),
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

