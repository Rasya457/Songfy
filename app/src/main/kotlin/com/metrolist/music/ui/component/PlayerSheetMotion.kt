package com.metrolist.music.ui.component

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.songfy.ui.rememberHighContrast
import com.songfy.ui.rememberReducedTransparency

/** True when the system "Remove animations" / animator scale 0 setting is on. */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    var reducedMotion by remember(context) {
        mutableStateOf(
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f,
        )
    }

    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reducedMotion =
                    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            }
        }
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        context.contentResolver.registerContentObserver(uri, false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    return reducedMotion
}

/**
 * PlayerSheetMotion.kt — Fisika gerak Apple Music untuk Player Sheet dan Liquid Glass UI.
 *
 * Menggunakan spring fisika bawaan Jetpack Compose yang diracik khusus:
 * - Critically damped (dampingRatio = NoBouncy, nggak mantul-mantul)
 * - Stiffness = 195f -> respon ~0,45 detik
 * - Berangkat cepat, makin lama makin melambat pas mau sampai (critically damped decay).
 * - Animasi selalu dibaca di draw/layout phase (graphicsLayer) -> 0 recomposition per frame.
 */
object PlayerSheetMotion {
    /** Spring utama sheet Now Playing: critically damped, tanpa bouncing */
    val SheetSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 195f,
    )

    /** Ambang kecepatan flick (px/detik) untuk membedakan swipe cepat vs drag santai */
    const val FlingVelocity = 700f

    /** Spring untuk transisi cover dan judul Now Playing ke compact mode */
    val CoverMorphSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 220f,
    )
}

