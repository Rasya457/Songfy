package com.lagu.songfy.util

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import java.util.concurrent.ConcurrentHashMap

/**
 * Dynamic Album Color Extractor using Android Palette API.
 * Automatically extracts the dominant/vibrant color from the album artwork,
 * ensuring saturated, recognizable hues (green, blue, red, purple) while keeping
 * backgrounds subtle, dark, and highly readable.
 */
object DynamicColorExtractor {

    private val colorCache = ConcurrentHashMap<String, Color>()

    suspend fun extract(
        context: Context,
        imageUrl: String?
    ): Color? = withContext(Dispatchers.IO) {
        if (imageUrl.isNullOrBlank()) return@withContext null
        colorCache[imageUrl]?.let { return@withContext it }
        try {
            val loader = Coil.imageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .allowHardware(false)
                .size(200)
                .build()
            val result = loader.execute(request)
            val bitmap = (result as? SuccessResult)?.drawable?.let {
                (it as? BitmapDrawable)?.bitmap
            } ?: return@withContext null

            val palette = Palette.from(bitmap).generate()

            // Prioritize vibrant swatches for vivid album character
            val swatch = palette.vibrantSwatch
                ?: palette.dominantSwatch
                ?: palette.lightVibrantSwatch
                ?: palette.darkVibrantSwatch
                ?: palette.mutedSwatch

            val argb = swatch?.rgb ?: palette.getDominantColor(0xFF282038.toInt())

            // Tune HSV for vibrant presence and dark-mode compatibility
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(argb, hsv)
            // Ensure minimum saturation for clear color identity (green, blue, red, purple)
            if (hsv[1] < 0.45f) {
                hsv[1] = (hsv[1] * 1.6f).coerceIn(0.45f, 0.92f)
            }
            // Balance lightness for readable contrast & soft glowing accents
            val finalColor = Color(android.graphics.Color.HSVToColor(hsv))
            if (colorCache.size > 50) colorCache.clear()
            colorCache[imageUrl] = finalColor
            finalColor
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    suspend fun extractAppleMusicPalette(
        context: Context,
        imageUrl: String?
    ): AppleMusicPalette = withContext(Dispatchers.IO) {
        if (imageUrl.isNullOrBlank()) return@withContext AppleMusicPalette()
        try {
            val loader = Coil.imageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .allowHardware(false)
                .size(256)
                .build()
            val result = loader.execute(request)
            val bitmap = (result as? SuccessResult)?.drawable?.let {
                (it as? BitmapDrawable)?.bitmap
            } ?: return@withContext AppleMusicPalette()

            val palette = Palette.from(bitmap).generate()

            // 1. Dominant / Background Swatch
            val domSwatch = palette.darkVibrantSwatch
                ?: palette.vibrantSwatch
                ?: palette.dominantSwatch
                ?: palette.darkMutedSwatch
                ?: palette.mutedSwatch

            val domRgb = domSwatch?.rgb ?: palette.getDominantColor(0xFF121212.toInt())
            val bgHsv = FloatArray(3)
            android.graphics.Color.colorToHSV(domRgb, bgHsv)
            // ~70% darker for rich deep Apple Music style backdrop
            bgHsv[1] = (bgHsv[1] * 0.85f).coerceIn(0.20f, 0.75f)
            bgHsv[2] = (bgHsv[2] * 0.28f).coerceIn(0.06f, 0.16f)
            val pageBg = Color(android.graphics.Color.HSVToColor(bgHsv))

            // 2. Accent Swatch (Light vibrant / vibrant for Artist title and equalizer)
            val accSwatch = palette.lightVibrantSwatch
                ?: palette.vibrantSwatch
                ?: palette.lightMutedSwatch
                ?: domSwatch
            val accRgb = accSwatch?.rgb ?: 0xFF5AC8FA.toInt()
            val accHsv = FloatArray(3)
            android.graphics.Color.colorToHSV(accRgb, accHsv)
            accHsv[1] = (accHsv[1] * 1.2f).coerceIn(0.40f, 0.90f)
            accHsv[2] = accHsv[2].coerceAtLeast(0.78f)
            val accent = Color(android.graphics.Color.HSVToColor(accHsv))

            AppleMusicPalette(
                backgroundColor = pageBg,
                accentColor = accent,
                dominantColor = Color(domRgb)
            )
        } catch (_: Throwable) {
            AppleMusicPalette()
        }
    }

    /**
     * Extracts dynamic mini player gradient & accent colors from album artwork.
     * Preserves the exact hue (red -> red, blue -> blue, green -> green) while
     * crafting a rich, dark, moody gradient matching Spotify/Apple Music aesthetic.
     */
    suspend fun extractMiniPlayerColors(
        context: Context,
        imageUrl: String?
    ): MiniPlayerColors = withContext(Dispatchers.IO) {
        if (imageUrl.isNullOrBlank()) return@withContext MiniPlayerColors()
        try {
            val loader = Coil.imageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .allowHardware(false)
                .size(160)
                .build()
            val result = loader.execute(request)
            val bitmap = (result as? SuccessResult)?.drawable?.let {
                (it as? BitmapDrawable)?.bitmap
            } ?: return@withContext MiniPlayerColors()

            val palette = Palette.from(bitmap).generate()
            val swatch = palette.darkVibrantSwatch
                ?: palette.vibrantSwatch
                ?: palette.dominantSwatch
                ?: palette.lightVibrantSwatch
                ?: palette.mutedSwatch

            val rgb = swatch?.rgb ?: palette.getDominantColor(0xFF381522.toInt())
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(rgb, hsv)

            // Pertahankan Hue asli lagu, tingkatkan saturasi agar warna tegas & kaya
            val sat = (hsv[1] * 1.30f).coerceIn(0.55f, 0.95f)

            // Top gradient: warna gelap yang kaya, jelas bernuansa warna cover lagu (merah, biru, hijau, dsb)
            val topHsv = floatArrayOf(hsv[0], sat, (hsv[2] * 0.50f).coerceIn(0.28f, 0.42f))
            // Bottom gradient: versi lebih dalam dari rona yang sama
            val botHsv = floatArrayOf(hsv[0], (sat * 1.05f).coerceIn(0.60f, 0.95f), (hsv[2] * 0.30f).coerceIn(0.16f, 0.26f))

            val topColor = Color(android.graphics.Color.HSVToColor(topHsv))
            val bottomColor = Color(android.graphics.Color.HSVToColor(botHsv))

            val accSwatch = palette.lightVibrantSwatch ?: palette.vibrantSwatch ?: swatch
            val accRgb = accSwatch?.rgb ?: rgb
            val accHsv = FloatArray(3)
            android.graphics.Color.colorToHSV(accRgb, accHsv)
            accHsv[1] = (accHsv[1] * 1.25f).coerceIn(0.45f, 0.95f)
            accHsv[2] = accHsv[2].coerceAtLeast(0.85f)
            val accentColor = Color(android.graphics.Color.HSVToColor(accHsv))

            MiniPlayerColors(
                topColor = topColor,
                bottomColor = bottomColor,
                accentColor = accentColor
            )
        } catch (_: Throwable) {
            MiniPlayerColors()
        }
    }
}

/**
 * Palette colors for dynamic MiniPlayerBar:
 * - topColor: rich dark tone matching song artwork (e.g. crimson red, navy blue, emerald green)
 * - bottomColor: deeper shade of the same hue for a subtle gradient
 * - accentColor: bright vibrant accent for glowing border and equalizer
 */
data class MiniPlayerColors(
    val topColor: Color = Color(0xFF3E1522),
    val bottomColor: Color = Color(0xFF260D15),
    val accentColor: Color = Color(0xFFE53E3E)
)

/**
 * Remembers and smoothly animates dynamic MiniPlayerColors extracted from current song artwork.
 * Transitions smoothly over 450ms when song changes.
 */
@Composable
fun rememberMiniPlayerColors(
    imageUrl: String?,
    fallbackTop: Color = Color(0xFF3E1522),
    fallbackBottom: Color = Color(0xFF260D15)
): MiniPlayerColors {
    val context = LocalContext.current
    var rawColors by remember(imageUrl) {
        mutableStateOf(
            MiniPlayerColors(
                topColor = fallbackTop,
                bottomColor = fallbackBottom
            )
        )
    }

    LaunchedEffect(imageUrl) {
        if (!imageUrl.isNullOrBlank()) {
            rawColors = DynamicColorExtractor.extractMiniPlayerColors(context, imageUrl)
        }
    }

    val animatedTop by animateColorAsState(
        targetValue = rawColors.topColor,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "miniPlayerTopColor"
    )
    val animatedBottom by animateColorAsState(
        targetValue = rawColors.bottomColor,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "miniPlayerBottomColor"
    )
    val animatedAccent by animateColorAsState(
        targetValue = rawColors.accentColor,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "miniPlayerAccentColor"
    )

    return MiniPlayerColors(
        topColor = animatedTop,
        bottomColor = animatedBottom,
        accentColor = animatedAccent
    )
}

/**
 * Palette colors tailored for Apple Music style layout:
 * - backgroundColor: ~70% darkened dominant color
 * - accentColor: light vibrant color for artist text, equalizer, active states
 * - dominantColor: raw extracted dominant color
 */
data class AppleMusicPalette(
    val backgroundColor: Color = Color(0xFF09111A),
    val accentColor: Color = Color(0xFF64D2FF),
    val dominantColor: Color = Color(0xFF1B354F)
)

/**
 * Remembers and smoothly animates the Apple Music palette extracted from an album artwork URL.
 * Automatically animates color transitions when the song/cover changes over 500ms.
 */
@Composable
fun rememberAppleMusicPalette(
    imageUrl: String?,
    defaultBg: Color = Color(0xFF121212),
    defaultAccent: Color = Color(0xFF64D2FF)
): AppleMusicPalette {
    val context = LocalContext.current
    var rawPalette by remember(imageUrl) {
        mutableStateOf(
            AppleMusicPalette(
                backgroundColor = defaultBg,
                accentColor = defaultAccent,
                dominantColor = defaultBg
            )
        )
    }

    LaunchedEffect(imageUrl) {
        if (!imageUrl.isNullOrBlank()) {
            val extracted = DynamicColorExtractor.extractAppleMusicPalette(context, imageUrl)
            rawPalette = extracted
        }
    }

    val animatedBg by animateColorAsState(
        targetValue = rawPalette.backgroundColor,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "AppleMusicBgTransition"
    )
    val animatedAccent by animateColorAsState(
        targetValue = rawPalette.accentColor,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "AppleMusicAccentTransition"
    )

    return AppleMusicPalette(
        backgroundColor = animatedBg,
        accentColor = animatedAccent,
        dominantColor = rawPalette.dominantColor
    )
}

/**
 * Remembers and smoothly animates the dynamic color extracted from an album artwork URL.
 * Automatically animates color transitions when the song changes over 650ms.
 */
@Composable
fun rememberDynamicColor(
    imageUrl: String?,
    defaultColor: Color = Color(0xFF6B52AE)
): State<Color> {
    val context = LocalContext.current
    var dominantColor by remember(imageUrl) { mutableStateOf(defaultColor) }

    LaunchedEffect(imageUrl) {
        if (!imageUrl.isNullOrBlank()) {
            val extracted = DynamicColorExtractor.extract(context, imageUrl)
            if (extracted != null) {
                dominantColor = extracted
            }
        }
    }

    return animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "DynamicAlbumColorTransition"
    )
}
