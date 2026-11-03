package com.metrolist.music.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

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

    /** Spring lift untuk GlassSelectionPill: overshoot sedikit saat ditekan */
    val PillLiftSpring = spring<Float>(
        dampingRatio = 0.55f,
        stiffness = 450f,
    )

    /** Spring gerak posisi GlassSelectionPill */
    val PillTravelSpring = spring<Float>(
        dampingRatio = 0.78f,
        stiffness = 300f,
    )
}

