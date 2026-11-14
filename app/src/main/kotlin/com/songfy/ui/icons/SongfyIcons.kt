// Songfy — Ikon ala iPhone (Home, Search, Lirik, Queue, Output Bluetooth, Menu, Player, TopBar).
// Ditulis ulang untuk Songfy mengikuti arsitektur yang dijelaskan dari BitChord
// (bukan salinan file BitChord, yang berlisensi GPL-3.0).

package com.songfy.ui.icons

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metrolist.music.ui.screens.Screens

// Ukuran + warna ala BitChord / Apple Music:
// ikon tab 28dp (bukan 24dp), label 10sp, aktif putih penuh, mati putih 65%.
object SongfyIconStyle {
    val TabIconSize = 28.dp
    val PlayerGlyphSize = 28.dp
    val ActiveTint = Color.White
    val InactiveTint = Color.White.copy(alpha = 0.65f)
    val PlayerGlyphTint = Color.White.copy(alpha = 0.92f)
    const val Stroke = 2.2f
}

enum class SongfyOutputKind { PHONE, BUDS, HEADPHONES, SPEAKER, CAR, HDMI, OTHER }

data class SongfyAudioOutputState(
    val kind: SongfyOutputKind,
    val deviceName: String?,
)

// ---------------------------------------------------------------------------
// Jalur B (bawaan, aman): ikon gambar tangan, stroke 2.2, ujung membulat.
// Semua di viewport 24x24 yang sama, jadi tidak ada yang mengecil sendiri.
// ---------------------------------------------------------------------------

object SongfyIcons {

    private val stroke = SolidColor(Color.Black)

    private fun strokePath(
        name: String,
        width: Float = SongfyIconStyle.Stroke,
        block: PathBuilder.() -> Unit,
    ): ImageVector = ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            stroke = stroke, strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) { block() }
    }.build()

    // ── 1. Bottom Navigation Tab Icons (28dp) ──────────────────────────────

    /** Home — FILLED, bukan outline. Tab bar iPhone itu solid (house.fill). */
    val Home: ImageVector by lazy {
        ImageVector.Builder(
            name = "songfy_home", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                fill = stroke, stroke = stroke, strokeLineWidth = 1.7f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 3.8f)
                lineTo(20f, 11f)
                lineTo(20f, 20f)
                lineTo(14.8f, 20f)
                lineTo(14.8f, 15.2f)
                lineTo(9.2f, 15.2f)
                lineTo(9.2f, 20f)
                lineTo(4f, 20f)
                lineTo(4f, 11f)
                close()
            }
        }.build()
    }

    /** Search — kaca pembesar: lingkaran + gagang, stroke 2.2 (magnifyingglass). */
    val Search: ImageVector by lazy {
        ImageVector.Builder(
            name = "songfy_search", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke, strokeLineWidth = SongfyIconStyle.Stroke,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(16.2f, 16.2f)
                lineTo(20.6f, 20.6f)
            }
            path(
                stroke = stroke, strokeLineWidth = SongfyIconStyle.Stroke,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(16.2f, 10.5f)
                arcToRelative(5.7f, 5.7f, 0f, true, true, -11.4f, 0f)
                arcToRelative(5.7f, 5.7f, 0f, true, true, 11.4f, 0f)
                close()
            }
        }.build()
    }

    /** Library — dua kartu bertumpuk (square.stack.fill). */
    val Library: ImageVector by lazy {
        ImageVector.Builder(
            name = "songfy_library", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke, strokeLineWidth = SongfyIconStyle.Stroke,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(8f, 8f)
                lineTo(8f, 5f)
                quadTo(8f, 3.5f, 9.5f, 3.5f)
                lineTo(18.5f, 3.5f)
                quadTo(20f, 3.5f, 20f, 5f)
                lineTo(20f, 14f)
                quadTo(20f, 15.5f, 18.5f, 15.5f)
                lineTo(16f, 15.5f)
            }
            path(
                fill = stroke, stroke = stroke, strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5.5f, 8.5f)
                lineTo(14.5f, 8.5f)
                lineTo(14.5f, 19f)
                quadTo(14.5f, 20.5f, 13f, 20.5f)
                lineTo(5.5f, 20.5f)
                quadTo(4f, 20.5f, 4f, 19f)
                lineTo(4f, 10f)
                quadTo(4f, 8.5f, 5.5f, 8.5f)
                close()
            }
        }.build()
    }

    /** ListenTogether — dua profil solid (person.2.fill) dengan proporsi penuh 24x24. */
    val ListenTogether: ImageVector by lazy {
        ImageVector.Builder(
            name = "songfy_listen_together", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            // Kepala kiri (utama)
            path(fill = stroke) {
                moveTo(12.5f, 8.2f)
                arcToRelative(3.3f, 3.3f, 0f, true, true, -6.6f, 0f)
                arcToRelative(3.3f, 3.3f, 0f, true, true, 6.6f, 0f)
                close()
            }
            // Badan kiri (utama)
            path(fill = stroke) {
                moveTo(3.2f, 19.2f)
                quadTo(3.2f, 13.6f, 9.2f, 13.6f)
                quadTo(15.2f, 13.6f, 15.2f, 19.2f)
                quadTo(15.2f, 20.2f, 14.2f, 20.2f)
                lineTo(4.2f, 20.2f)
                quadTo(3.2f, 20.2f, 3.2f, 19.2f)
                close()
            }
            // Kepala kanan
            path(fill = stroke) {
                moveTo(19.6f, 9.2f)
                arcToRelative(2.6f, 2.6f, 0f, true, true, -5.2f, 0f)
                arcToRelative(2.6f, 2.6f, 0f, true, true, 5.2f, 0f)
                close()
            }
            // Badan kanan
            path(fill = stroke) {
                moveTo(15.5f, 14.2f)
                quadTo(16.3f, 14.0f, 17.2f, 14.0f)
                quadTo(21.4f, 14.0f, 21.4f, 18.6f)
                quadTo(21.4f, 19.6f, 20.4f, 19.6f)
                lineTo(16.8f, 19.6f)
                quadTo(17.0f, 16.5f, 15.5f, 14.2f)
                close()
            }
        }.build()
    }

    // ── 2. Now Playing Bottom Bar Glyphs (28dp) ────────────────────────────

    /** Lirik — bubble kutip (quote.bubble.fill), kiri bawah Now Playing. */
    val LyricsQuote: ImageVector by lazy {
        ImageVector.Builder(
            name = "songfy_lyrics_quote", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke, strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6f, 3.5f)
                lineTo(18f, 3.5f)
                quadTo(21f, 3.5f, 21f, 6.5f)
                lineTo(21f, 15f)
                quadTo(21f, 18f, 18f, 18f)
                lineTo(10f, 18f)
                lineTo(6f, 21f)
                lineTo(6f, 18f)
                quadTo(3f, 18f, 3f, 15f)
                lineTo(3f, 6.5f)
                quadTo(3f, 3.5f, 6f, 3.5f)
                close()
            }
            // Dua tanda kutip di dalam bubble.
            path(fill = stroke) {
                moveTo(8f, 8.5f); lineTo(11f, 8.5f); lineTo(11f, 11.5f)
                quadTo(11f, 13.5f, 8.8f, 14f); lineTo(8.8f, 12.6f)
                quadTo(9.8f, 12.3f, 9.8f, 11.5f); lineTo(8f, 11.5f); close()
                moveTo(13f, 8.5f); lineTo(16f, 8.5f); lineTo(16f, 11.5f)
                quadTo(16f, 13.5f, 13.8f, 14f); lineTo(13.8f, 12.6f)
                quadTo(14.8f, 12.3f, 14.8f, 11.5f); lineTo(13f, 11.5f); close()
            }
        }.build()
    }

    /** Queue — 3 titik + 3 garis (list.bullet), kanan bawah Now Playing. */
    val Queue: ImageVector by lazy {
        ImageVector.Builder(
            name = "songfy_queue", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = stroke, strokeLineWidth = 2.2f, strokeLineCap = StrokeCap.Round) {
                for (y in listOf(6f, 12f, 18f)) {
                    moveTo(8.2f, y); lineTo(20.8f, y)
                }
            }
            path(fill = stroke) {
                for (y in listOf(6f, 12f, 18f)) {
                    moveTo(4.6f, y)
                    arcToRelative(1.35f, 1.35f, 0f, true, true, -2.7f, 0f)
                    arcToRelative(1.35f, 1.35f, 0f, true, true, 2.7f, 0f)
                    close()
                }
            }
        }.build()
    }

    // ── 3. Audio Output Glyphs (Tengah bawah Now Playing) ──────────────────

    /** Buds / TWS — AirPods Pro (airpodspro): sepasang earbud kiri & kanan lengkap dengan eartip & tangkai. */
    val Buds: ImageVector by lazy {
        ImageVector.Builder(
            name = "songfy_buds", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            // Earbud kiri: housing + tangkai bawah
            path(
                fill = stroke, stroke = stroke, strokeLineWidth = 1.2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            ) {
                // Kepala earbud kiri menghadap ke kiri-luar
                moveTo(6.2f, 5.2f)
                quadTo(9.8f, 5.2f, 9.8f, 8.6f)
                quadTo(9.8f, 11.2f, 8.2f, 11.8f)
                lineTo(8.2f, 19.2f)
                quadTo(8.2f, 20.3f, 7.1f, 20.3f)
                quadTo(6.0f, 20.3f, 6.0f, 19.2f)
                lineTo(6.0f, 12.0f)
                quadTo(3.8f, 11.5f, 3.8f, 8.6f)
                quadTo(3.8f, 5.2f, 6.2f, 5.2f)
                close()
            }
            // Eartip silikon kiri
            path(fill = stroke) {
                moveTo(3.0f, 7.0f)
                quadTo(1.5f, 7.2f, 1.5f, 8.8f)
                quadTo(1.5f, 10.4f, 3.0f, 10.6f)
                close()
            }
            // Earbud kanan: housing + tangkai bawah
            path(
                fill = stroke, stroke = stroke, strokeLineWidth = 1.2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(17.8f, 5.2f)
                quadTo(14.2f, 5.2f, 14.2f, 8.6f)
                quadTo(14.2f, 11.2f, 15.8f, 11.8f)
                lineTo(15.8f, 19.2f)
                quadTo(15.8f, 20.3f, 16.9f, 20.3f)
                quadTo(18.0f, 20.3f, 18.0f, 19.2f)
                lineTo(18.0f, 12.0f)
                quadTo(20.2f, 11.5f, 20.2f, 8.6f)
                quadTo(20.2f, 5.2f, 17.8f, 5.2f)
                close()
            }
            // Eartip silikon kanan
            path(fill = stroke) {
                moveTo(21.0f, 7.0f)
                quadTo(22.5f, 7.2f, 22.5f, 8.8f)
                quadTo(22.5f, 10.4f, 21.0f, 10.6f)
                close()
            }
        }.build()
    }

    /** Headphones — Headphone kabel / over-ear (headphones). */
    val Headphones: ImageVector by lazy {
        ImageVector.Builder(
            name = "songfy_headphones", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke, strokeLineWidth = SongfyIconStyle.Stroke,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4f, 14f)
                lineTo(4f, 11.5f)
                arcToRelative(8f, 8f, 0f, false, true, 16f, 0f)
                lineTo(20f, 14f)
            }
            path(
                fill = stroke, stroke = stroke, strokeLineWidth = 1.4f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            ) {
                // Earcup kiri
                moveTo(4f, 12.5f)
                lineTo(6.8f, 12.5f)
                quadTo(7.8f, 12.5f, 7.8f, 13.5f)
                lineTo(7.8f, 18.5f)
                quadTo(7.8f, 19.5f, 6.8f, 19.5f)
                lineTo(5.2f, 19.5f)
                quadTo(3.5f, 19.5f, 3.5f, 17.8f)
                lineTo(3.5f, 13.2f)
                quadTo(3.5f, 12.5f, 4f, 12.5f)
                close()
                // Earcup kanan
                moveTo(20f, 12.5f)
                lineTo(17.2f, 12.5f)
                quadTo(16.2f, 12.5f, 16.2f, 13.5f)
                lineTo(16.2f, 18.5f)
                quadTo(16.2f, 19.5f, 17.2f, 19.5f)
                lineTo(18.8f, 19.5f)
                quadTo(20.5f, 19.5f, 20.5f, 17.8f)
                lineTo(20.5f, 13.2f)
                quadTo(20.5f, 12.5f, 20f, 12.5f)
                close()
            }
        }.build()
    }

    /** Speaker — Speaker Bluetooth / Hi-Fi (hifispeaker). */
    val Speaker: ImageVector by lazy {
        ImageVector.Builder(
            name = "songfy_speaker", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke, strokeLineWidth = SongfyIconStyle.Stroke,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(7.5f, 3.5f)
                lineTo(16.5f, 3.5f)
                quadTo(18.5f, 3.5f, 18.5f, 5.5f)
                lineTo(18.5f, 18.5f)
                quadTo(18.5f, 20.5f, 16.5f, 20.5f)
                lineTo(7.5f, 20.5f)
                quadTo(5.5f, 20.5f, 5.5f, 18.5f)
                lineTo(5.5f, 5.5f)
                quadTo(5.5f, 3.5f, 7.5f, 3.5f)
                close()
            }
            // Tweeter atas
            path(fill = stroke) {
                moveTo(13.3f, 8.0f)
                arcToRelative(1.3f, 1.3f, 0f, true, true, -2.6f, 0f)
                arcToRelative(1.3f, 1.3f, 0f, true, true, 2.6f, 0f)
                close()
            }
            // Woofer bawah
            path(stroke = stroke, strokeLineWidth = 2f) {
                moveTo(15.0f, 14.5f)
                arcToRelative(3.0f, 3.0f, 0f, true, true, -6.0f, 0f)
                arcToRelative(3.0f, 3.0f, 0f, true, true, 6.0f, 0f)
                close()
            }
        }.build()
    }

    /** Car — CarPlay / Audio Mobil (car.fill). */
    val Car: ImageVector by lazy {
        ImageVector.Builder(
            name = "songfy_car", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke, strokeLineWidth = SongfyIconStyle.Stroke,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5.5f, 11f)
                lineTo(7.3f, 6.2f)
                quadTo(7.8f, 5f, 9.2f, 5f)
                lineTo(14.8f, 5f)
                quadTo(16.2f, 5f, 16.7f, 6.2f)
                lineTo(18.5f, 11f)
                lineTo(19.5f, 11.8f)
                quadTo(20.5f, 12.5f, 20.5f, 13.8f)
                lineTo(20.5f, 18.5f)
                lineTo(18.0f, 18.5f)
                lineTo(18.0f, 16.5f)
                lineTo(6.0f, 16.5f)
                lineTo(6.0f, 18.5f)
                lineTo(3.5f, 18.5f)
                lineTo(3.5f, 13.8f)
                quadTo(3.5f, 12.5f, 4.5f, 11.8f)
                close()
            }
            path(fill = stroke) {
                moveTo(8.2f, 13.8f)
                arcToRelative(1.2f, 1.2f, 0f, true, true, -2.4f, 0f)
                arcToRelative(1.2f, 1.2f, 0f, true, true, 2.4f, 0f)
                close()
                moveTo(18.2f, 13.8f)
                arcToRelative(1.2f, 1.2f, 0f, true, true, -2.4f, 0f)
                arcToRelative(1.2f, 1.2f, 0f, true, true, 2.4f, 0f)
                close()
            }
        }.build()
    }

    /** Tv — TV / HDMI (tv). */
    val Tv: ImageVector by lazy {
        ImageVector.Builder(
            name = "songfy_tv", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke, strokeLineWidth = SongfyIconStyle.Stroke,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5f, 5.5f)
                lineTo(19f, 5.5f)
                quadTo(20.8f, 5.5f, 20.8f, 7.3f)
                lineTo(20.8f, 15.2f)
                quadTo(20.8f, 17f, 19f, 17f)
                lineTo(5f, 17f)
                quadTo(3.2f, 17f, 3.2f, 15.2f)
                lineTo(3.2f, 7.3f)
                quadTo(3.2f, 5.5f, 5f, 5.5f)
                close()
                moveTo(8.5f, 20f)
                lineTo(15.5f, 20f)
            }
        }.build()
    }

    /** AirPlay — Ikon output audio bawaan iPhone (airplayaudio). */
    val AirPlay: ImageVector by lazy {
        ImageVector.Builder(
            name = "songfy_airplay", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            // Segitiga solid bawah
            path(
                fill = stroke, stroke = stroke, strokeLineWidth = 1.4f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 13.8f)
                lineTo(16.6f, 19.8f)
                lineTo(7.4f, 19.8f)
                close()
            }
            // Gelombang konsentris AirPlay
            path(
                stroke = stroke, strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
            ) {
                // Titik tengah
                moveTo(12.2f, 10.5f)
                arcToRelative(0.2f, 0.2f, 0f, true, true, -0.4f, 0f)
                arcToRelative(0.2f, 0.2f, 0f, true, true, 0.4f, 0f)
                // Cincin dalam
                moveTo(8.8f, 13.0f)
                arcToRelative(4.2f, 4.2f, 0f, true, true, 6.4f, 0f)
                // Cincin luar
                moveTo(6.0f, 15.5f)
                arcToRelative(7.8f, 7.8f, 0f, true, true, 12.0f, 0f)
            }
        }.build()
    }

    fun outputGlyph(kind: SongfyOutputKind): ImageVector = when (kind) {
        SongfyOutputKind.BUDS -> Buds
        SongfyOutputKind.HEADPHONES -> Headphones
        SongfyOutputKind.SPEAKER -> Speaker
        SongfyOutputKind.CAR -> Car
        SongfyOutputKind.HDMI -> Tv
        SongfyOutputKind.PHONE, SongfyOutputKind.OTHER -> AirPlay
    }

    // ── 4. Player Controls & Volume Icons (iPhone / Apple Music) ───────────

    /** Play — segitiga solid membulat (play.fill). */
    val Play: ImageVector by lazy {
        ImageVector.Builder(
            name = "songfy_play", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                fill = stroke, stroke = stroke, strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6.8f, 4.8f)
                lineTo(19.0f, 12.0f)
                lineTo(6.8f, 19.2f)
                close()
            }
        }.build()
    }

    /** Pause — dua batang vertikal membulat (pause.fill). */
    val Pause: ImageVector by lazy {
        ImageVector.Builder(
            name = "songfy_pause", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                fill = stroke, stroke = stroke, strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6.5f, 5.0f); lineTo(9.8f, 5.0f); lineTo(9.8f, 19.0f); lineTo(6.5f, 19.0f); close()
                moveTo(14.2f, 5.0f); lineTo(17.5f, 5.0f); lineTo(17.5f, 19.0f); lineTo(14.2f, 19.0f); close()
            }
        }.build()
    }

    /** Forward — dua segitiga kanan solid membulat (forward.fill). */
    val Forward: ImageVector by lazy {
        ImageVector.Builder(
            name = "songfy_forward", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                fill = stroke, stroke = stroke, strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(3.5f, 6.2f)
                lineTo(11.8f, 12.0f)
                lineTo(3.5f, 17.8f)
                close()
                moveTo(12.5f, 6.2f)
                lineTo(20.8f, 12.0f)
                lineTo(12.5f, 17.8f)
                close()
            }
        }.build()
    }


    /** SpeakerLow — speaker kecil kiri slider volume (speaker.fill). */
    val SpeakerLow: ImageVector by lazy {
        ImageVector.Builder(
            name = "songfy_speaker_low", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                fill = stroke, stroke = stroke, strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5.5f, 9.5f)
                lineTo(9.2f, 9.5f)
                lineTo(14.5f, 5.2f)
                lineTo(14.5f, 18.8f)
                lineTo(9.2f, 14.5f)
                lineTo(5.5f, 14.5f)
                close()
            }
        }.build()
    }

    /** SpeakerHigh — speaker + gelombang kanan slider volume (speaker.wave.3.fill). */
    val SpeakerHigh: ImageVector by lazy {
        ImageVector.Builder(
            name = "songfy_speaker_high", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                fill = stroke, stroke = stroke, strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(3.2f, 9.5f)
                lineTo(6.6f, 9.5f)
                lineTo(11.5f, 5.2f)
                lineTo(11.5f, 18.8f)
                lineTo(6.6f, 14.5f)
                lineTo(3.2f, 14.5f)
                close()
            }
            path(
                stroke = stroke, strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(14.8f, 9.2f)
                quadTo(16.8f, 12.0f, 14.8f, 14.8f)
                moveTo(17.8f, 6.8f)
                quadTo(21.2f, 12.0f, 17.8f, 17.2f)
            }
        }.build()
    }

    /** Ellipsis — tiga titik horizontal iOS (ellipsis). */
    val Ellipsis: ImageVector by lazy {
        ImageVector.Builder(
            name = "songfy_ellipsis", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = stroke) {
                for (x in listOf(6.0f, 12.0f, 18.0f)) {
                    moveTo(x + 1.8f, 12f)
                    arcToRelative(1.8f, 1.8f, 0f, true, true, -3.6f, 0f)
                    arcToRelative(1.8f, 1.8f, 0f, true, true, 3.6f, 0f)
                    close()
                }
            }
        }.build()
    }

    /** Shuffle — panah silang iOS (shuffle). */
    val Shuffle: ImageVector by lazy {
        strokePath("songfy_shuffle", 2.1f) {
            moveTo(16.5f, 4.5f); lineTo(20.5f, 4.5f); lineTo(20.5f, 8.5f)
            moveTo(3.8f, 18.5f); lineTo(20.2f, 4.8f)
            moveTo(20.5f, 15.5f); lineTo(20.5f, 19.5f); lineTo(16.5f, 19.5f)
            moveTo(14.5f, 14.5f); lineTo(20.2f, 19.2f)
            moveTo(3.8f, 5.5f); lineTo(9.2f, 10.0f)
        }
    }

    /** Repeat — panah putar iOS (repeat). */
    val Repeat: ImageVector by lazy {
        strokePath("songfy_repeat", 2.1f) {
            moveTo(17f, 3.5f); lineTo(20.5f, 7f); lineTo(17f, 10.5f)
            moveTo(3.8f, 11.5f); lineTo(3.8f, 9.5f); quadTo(3.8f, 7f, 6.5f, 7f); lineTo(20.2f, 7f)
            moveTo(7f, 20.5f); lineTo(3.5f, 17f); lineTo(7f, 13.5f)
            moveTo(20.2f, 12.5f); lineTo(20.2f, 14.5f); quadTo(20.2f, 17f, 17.5f, 17f); lineTo(3.8f, 17f)
        }
    }

    /** RepeatOne — panah putar + angka 1 iOS (repeat.1). */
    val RepeatOne: ImageVector by lazy {
        strokePath("songfy_repeat_one", 2.1f) {
            moveTo(17f, 3.5f); lineTo(20.5f, 7f); lineTo(17f, 10.5f)
            moveTo(3.8f, 11.5f); lineTo(3.8f, 9.5f); quadTo(3.8f, 7f, 6.5f, 7f); lineTo(20.2f, 7f)
            moveTo(7f, 20.5f); lineTo(3.5f, 17f); lineTo(7f, 13.5f)
            moveTo(20.2f, 12.5f); lineTo(20.2f, 14.5f); quadTo(20.2f, 17f, 17.5f, 17f); lineTo(3.8f, 17f)
            moveTo(11.0f, 10.5f); lineTo(12.4f, 9.5f); lineTo(12.4f, 14.5f)
        }
    }

    // ── 5. Menu & TopBar iOS Glyphs ────────────────────────────────────────

    /** Share — kotak + panah ke atas khas iPhone (square.and.arrow.up). */
    val Share: ImageVector by lazy {
        strokePath("songfy_share", 2.1f) {
            moveTo(12f, 14.5f); lineTo(12f, 3.5f)
            moveTo(8.2f, 7.2f); lineTo(12f, 3.4f); lineTo(15.8f, 7.2f)
            moveTo(7.5f, 10.5f)
            lineTo(6.0f, 10.5f)
            quadTo(4.5f, 10.5f, 4.5f, 12.0f)
            lineTo(4.5f, 19.0f)
            quadTo(4.5f, 20.5f, 6.0f, 20.5f)
            lineTo(18.0f, 20.5f)
            quadTo(19.5f, 20.5f, 19.5f, 19.0f)
            lineTo(19.5f, 12.0f)
            quadTo(19.5f, 10.5f, 18.0f, 10.5f)
            lineTo(16.5f, 10.5f)
        }
    }

    /** Sliders / Equalizer — 3 garis slider horizontal iOS (slider.horizontal.3). */
    val Sliders: ImageVector by lazy {
        ImageVector.Builder(
            name = "songfy_sliders", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = stroke, strokeLineWidth = 2.0f, strokeLineCap = StrokeCap.Round) {
                moveTo(4f, 6.5f); lineTo(20f, 6.5f)
                moveTo(4f, 12f); lineTo(20f, 12f)
                moveTo(4f, 17.5f); lineTo(20f, 17.5f)
            }
            path(fill = stroke) {
                moveTo(10.5f, 6.5f)
                arcToRelative(2.2f, 2.2f, 0f, true, true, -4.4f, 0f)
                arcToRelative(2.2f, 2.2f, 0f, true, true, 4.4f, 0f)
                close()
                moveTo(17.8f, 12f)
                arcToRelative(2.2f, 2.2f, 0f, true, true, -4.4f, 0f)
                arcToRelative(2.2f, 2.2f, 0f, true, true, 4.4f, 0f)
                close()
                moveTo(11.8f, 17.5f)
                arcToRelative(2.2f, 2.2f, 0f, true, true, -4.4f, 0f)
                arcToRelative(2.2f, 2.2f, 0f, true, true, 4.4f, 0f)
                close()
            }
        }.build()
    }

    /** Info — lingkaran + huruf i iOS (info.circle). */
    val Info: ImageVector by lazy {
        ImageVector.Builder(
            name = "songfy_info", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = stroke, strokeLineWidth = 2.1f, strokeLineCap = StrokeCap.Round) {
                moveTo(20.5f, 12f)
                arcToRelative(8.5f, 8.5f, 0f, true, true, -17f, 0f)
                arcToRelative(8.5f, 8.5f, 0f, true, true, 17f, 0f)
                close()
                moveTo(12f, 11.2f); lineTo(12f, 16.2f)
            }
            path(fill = stroke) {
                moveTo(13.2f, 8.0f)
                arcToRelative(1.2f, 1.2f, 0f, true, true, -2.4f, 0f)
                arcToRelative(1.2f, 1.2f, 0f, true, true, 2.4f, 0f)
                close()
            }
        }.build()
    }

    /** PlayNext — baris teks + segitiga play di atas (text.insert). */
    val PlayNext: ImageVector by lazy {
        ImageVector.Builder(
            name = "songfy_play_next", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = stroke, strokeLineWidth = 2.1f, strokeLineCap = StrokeCap.Round) {
                moveTo(11f, 7f); lineTo(20f, 7f)
                moveTo(4f, 12.5f); lineTo(20f, 12.5f)
                moveTo(4f, 18f); lineTo(20f, 18f)
            }
            path(fill = stroke, stroke = stroke, strokeLineWidth = 1.2f, strokeLineJoin = StrokeJoin.Round) {
                moveTo(4f, 4.8f); lineTo(8.2f, 7f); lineTo(4f, 9.2f); close()
            }
        }.build()
    }

    /** AddToPlaylist — lingkaran + tanda tambah (plus.circle). */
    val PlusCircle: ImageVector by lazy {
        strokePath("songfy_plus_circle", 2.1f) {
            moveTo(20.5f, 12f)
            arcToRelative(8.5f, 8.5f, 0f, true, true, -17f, 0f)
            arcToRelative(8.5f, 8.5f, 0f, true, true, 17f, 0f)
            close()
            moveTo(12f, 8f); lineTo(12f, 16f)
            moveTo(8f, 12f); lineTo(16f, 12f)
        }
    }

    /** Radio — titik + gelombang radio kiri kanan (dot.radiowaves.left.and.right). */
    val Radio: ImageVector by lazy {
        ImageVector.Builder(
            name = "songfy_radio", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = stroke) {
                moveTo(13.8f, 12f)
                arcToRelative(1.8f, 1.8f, 0f, true, true, -3.6f, 0f)
                arcToRelative(1.8f, 1.8f, 0f, true, true, 3.6f, 0f)
                close()
            }
            path(stroke = stroke, strokeLineWidth = 2.1f, strokeLineCap = StrokeCap.Round) {
                moveTo(8.2f, 8.5f); quadTo(5.5f, 12f, 8.2f, 15.5f)
                moveTo(15.8f, 8.5f); quadTo(18.5f, 12f, 15.8f, 15.5f)
                moveTo(5.2f, 5.8f); quadTo(1.2f, 12f, 5.2f, 18.2f)
                moveTo(18.8f, 5.8f); quadTo(22.8f, 12f, 18.8f, 18.2f)
            }
        }.build()
    }

    /** Video — layar video play (play.rectangle). */
    val Video: ImageVector by lazy {
        ImageVector.Builder(
            name = "songfy_video", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = stroke, strokeLineWidth = 2.1f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(5f, 5.5f)
                lineTo(19f, 5.5f)
                quadTo(20.8f, 5.5f, 20.8f, 7.3f)
                lineTo(20.8f, 16.7f)
                quadTo(20.8f, 18.5f, 19f, 18.5f)
                lineTo(5f, 18.5f)
                quadTo(3.2f, 18.5f, 3.2f, 16.7f)
                lineTo(3.2f, 7.3f)
                quadTo(3.2f, 5.5f, 5f, 5.5f)
                close()
            }
            path(fill = stroke, stroke = stroke, strokeLineWidth = 1.2f, strokeLineJoin = StrokeJoin.Round) {
                moveTo(10.2f, 9.2f); lineTo(15.2f, 12f); lineTo(10.2f, 14.8f); close()
            }
        }.build()
    }

    /** Download — lingkaran + panah bawah iOS (arrow.down.circle). */
    val Download: ImageVector by lazy {
        strokePath("songfy_download", 2.1f) {
            moveTo(20.5f, 12f)
            arcToRelative(8.5f, 8.5f, 0f, true, true, -17f, 0f)
            arcToRelative(8.5f, 8.5f, 0f, true, true, 17f, 0f)
            close()
            moveTo(12f, 7.8f); lineTo(12f, 15.8f)
            moveTo(8.8f, 12.8f); lineTo(12f, 16.0f); lineTo(15.2f, 12.8f)
        }
    }

    /** Trash — tempat sampah iOS (trash). */
    val Trash: ImageVector by lazy {
        strokePath("songfy_trash", 2.1f) {
            moveTo(4.5f, 6.5f); lineTo(19.5f, 6.5f)
            moveTo(9.2f, 6.5f); lineTo(9.2f, 4.8f); quadTo(9.2f, 3.8f, 10.2f, 3.8f); lineTo(13.8f, 3.8f); quadTo(14.8f, 3.8f, 14.8f, 4.8f); lineTo(14.8f, 6.5f)
            moveTo(6.5f, 6.5f); lineTo(7.2f, 18.5f); quadTo(7.3f, 20.2f, 9.0f, 20.2f); lineTo(15.0f, 20.2f); quadTo(16.7f, 20.2f, 16.8f, 18.5f); lineTo(17.5f, 6.5f)
            moveTo(10.2f, 10.5f); lineTo(10.2f, 16.5f)
            moveTo(13.8f, 10.5f); lineTo(13.8f, 16.5f)
        }
    }

    /** Refresh / Cache — panah putar iOS (arrow.clockwise). */
    val Refresh: ImageVector by lazy {
        strokePath("songfy_refresh", 2.1f) {
            moveTo(19.5f, 5.2f); lineTo(19.5f, 9.5f); lineTo(15.2f, 9.5f)
            moveTo(19.2f, 9.2f)
            arcToRelative(7.8f, 7.8f, 0f, true, false, 1.0f, 5.2f)
        }
    }

    /** Album — piringan hitam / optical disc iOS (opticaldisc). */
    val Album: ImageVector by lazy {
        ImageVector.Builder(
            name = "songfy_album", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = stroke, strokeLineWidth = 2.1f) {
                moveTo(20.5f, 12f)
                arcToRelative(8.5f, 8.5f, 0f, true, true, -17f, 0f)
                arcToRelative(8.5f, 8.5f, 0f, true, true, 17f, 0f)
                close()
                moveTo(14.6f, 12f)
                arcToRelative(2.6f, 2.6f, 0f, true, true, -5.2f, 0f)
                arcToRelative(2.6f, 2.6f, 0f, true, true, 5.2f, 0f)
                close()
            }
        }.build()
    }

    /** Artist — mikrofon musik iOS (music.mic). */
    val Artist: ImageVector by lazy {
        ImageVector.Builder(
            name = "songfy_artist", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = stroke) {
                moveTo(12f, 3.5f)
                quadTo(15.2f, 3.5f, 15.2f, 6.7f)
                lineTo(15.2f, 11.2f)
                quadTo(15.2f, 14.4f, 12f, 14.4f)
                quadTo(8.8f, 14.4f, 8.8f, 11.2f)
                lineTo(8.8f, 6.7f)
                quadTo(8.8f, 3.5f, 12f, 3.5f)
                close()
            }
            path(stroke = stroke, strokeLineWidth = 2.1f, strokeLineCap = StrokeCap.Round) {
                moveTo(6.2f, 10.8f)
                arcToRelative(5.8f, 5.8f, 0f, false, false, 11.6f, 0f)
                moveTo(12f, 16.8f); lineTo(12f, 20.5f)
                moveTo(8.8f, 20.5f); lineTo(15.2f, 20.5f)
            }
        }.build()
    }

    /** Moon — bulan sabit iOS untuk Sleep Timer (moon.fill). */
    val Moon: ImageVector by lazy {
        ImageVector.Builder(
            name = "songfy_moon", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                fill = stroke, stroke = stroke, strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(19.5f, 14.8f)
                arcToRelative(7.8f, 7.8f, 0f, false, true, -10.3f, -10.3f)
                arcToRelative(7.8f, 7.8f, 0f, true, false, 10.3f, 10.3f)
                close()
            }
        }.build()
    }

    /** Sun — ikon matahari iOS untuk Keep Screen On (sun.max). */
    val Sun: ImageVector by lazy {
        strokePath("songfy_sun", 1.8f) {
            moveTo(15.5f, 12f); arcToRelative(3.5f, 3.5f, 0f, true, true, -7f, 0f); arcToRelative(3.5f, 3.5f, 0f, true, true, 7f, 0f)
            moveTo(12f, 3.2f); lineTo(12f, 5.4f)
            moveTo(12f, 18.6f); lineTo(12f, 20.8f)
            moveTo(3.2f, 12f); lineTo(5.4f, 12f)
            moveTo(18.6f, 12f); lineTo(20.8f, 12f)
            moveTo(5.8f, 5.8f); lineTo(7.3f, 7.3f)
            moveTo(16.7f, 16.7f); lineTo(18.2f, 18.2f)
            moveTo(18.2f, 5.8f); lineTo(16.7f, 7.3f)
            moveTo(7.3f, 16.7f); lineTo(5.8f, 18.2f)
        }
    }

    /** History — jam dengan panah putar iOS (clock.arrow.circlepath). */
    val History: ImageVector by lazy {
        strokePath("songfy_history", 2.1f) {
            moveTo(4.2f, 7.5f); lineTo(4.2f, 11.5f); lineTo(8.2f, 11.5f)
            moveTo(4.5f, 11.2f)
            arcToRelative(8.0f, 8.0f, 0f, true, true, 1.2f, 5.2f)
            moveTo(12f, 7.8f); lineTo(12f, 12.2f); lineTo(15.2f, 14.0f)
        }
    }

    /** Stats — grafik batang solid iOS (chart.bar.fill). */
    val Stats: ImageVector by lazy {
        ImageVector.Builder(
            name = "songfy_stats", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                fill = stroke, stroke = stroke, strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5.0f, 13.5f); lineTo(7.8f, 13.5f); lineTo(7.8f, 19.5f); lineTo(5.0f, 19.5f); close()
                moveTo(10.6f, 8.5f); lineTo(13.4f, 8.5f); lineTo(13.4f, 19.5f); lineTo(10.6f, 19.5f); close()
                moveTo(16.2f, 4.5f); lineTo(19.0f, 4.5f); lineTo(19.0f, 19.5f); lineTo(16.2f, 19.5f); close()
            }
        }.build()
    }

    /** Account — lingkaran profil iOS (person.crop.circle). */
    val Account: ImageVector by lazy {
        ImageVector.Builder(
            name = "songfy_account", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = stroke, strokeLineWidth = 2.1f) {
                moveTo(20.5f, 12f)
                arcToRelative(8.5f, 8.5f, 0f, true, true, -17f, 0f)
                arcToRelative(8.5f, 8.5f, 0f, true, true, 17f, 0f)
                close()
            }
            path(fill = stroke) {
                moveTo(14.8f, 9.5f)
                arcToRelative(2.8f, 2.8f, 0f, true, true, -5.6f, 0f)
                arcToRelative(2.8f, 2.8f, 0f, true, true, 5.6f, 0f)
                close()
                moveTo(6.8f, 18.2f)
                quadTo(8.5f, 14.2f, 12f, 14.2f)
                quadTo(15.5f, 14.2f, 17.2f, 18.2f)
                close()
            }
        }.build()
    }

    /** Close — tanda silang membulat iOS (xmark). */
    val Close: ImageVector by lazy {
        strokePath("songfy_close", 2.2f) {
            moveTo(6.5f, 6.5f); lineTo(17.5f, 17.5f)
            moveTo(17.5f, 6.5f); lineTo(6.5f, 17.5f)
        }
    }

    /** Translate — karakter A kecil + aksara (character.bubble, translate). */
    val Translate: ImageVector by lazy {
        ImageVector.Builder(
            name = "songfy_translate",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            // Bubble kiri (sumber)
            path(
                stroke = stroke, strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent),
            ) {
                moveTo(3f, 5f)
                curveTo(3f, 3.9f, 3.9f, 3f, 5f, 3f)
                lineTo(14f, 3f)
                curveTo(15.1f, 3f, 16f, 3.9f, 16f, 5f)
                lineTo(16f, 12f)
                curveTo(16f, 13.1f, 15.1f, 14f, 14f, 14f)
                lineTo(8f, 14f)
                lineTo(5f, 17f)
                lineTo(5f, 14f)
                lineTo(5f, 14f)
                curveTo(3.9f, 14f, 3f, 13.1f, 3f, 12f)
                close()
            }
            // Huruf "A" kecil di dalam bubble
            path(
                stroke = stroke, strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(7.5f, 11f); lineTo(9.5f, 6.5f); lineTo(11.5f, 11f)
                moveTo(8.2f, 9.5f); lineTo(10.8f, 9.5f)
            }
            // Bubble kanan (terjemahan)
            path(
                stroke = stroke, strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                fill = SolidColor(Color.Transparent),
            ) {
                moveTo(16f, 10f)
                lineTo(19f, 10f)
                curveTo(20.1f, 10f, 21f, 10.9f, 21f, 12f)
                lineTo(21f, 19f)
                curveTo(21f, 20.1f, 20.1f, 21f, 19f, 21f)
                lineTo(13f, 21f)
                curveTo(11.9f, 21f, 11f, 20.1f, 11f, 19f)
                lineTo(11f, 17f)
            }
            // Huruf "文" (garis horizontal + vertikal sederhana) di bubble kanan
            path(
                stroke = stroke, strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(13.5f, 13.5f); lineTo(18.5f, 13.5f)
                moveTo(16f, 13.5f); lineTo(16f, 18.5f)
                moveTo(13.5f, 18.5f); lineTo(18.5f, 18.5f)
            }
        }.build()
    }
}

fun Screens.toSongfyIcon(): ImageVector = when (this) {
    Screens.Home -> SongfyIcons.Home
    Screens.Search -> SongfyIcons.Search
    Screens.Library -> SongfyIcons.Library
    Screens.ListenTogether -> SongfyIcons.ListenTogether
}

// ---------------------------------------------------------------------------
// Ikon tab + glyph player — pemakaian yang benar.
// Jangan pakai Modifier.size(24.dp) + tint abu-abu: itu yang bikin
// ikonmu kelihatan kecil / tipis / bukan iPhone.
// ---------------------------------------------------------------------------

@Composable
fun SongfyTabIcon(
    icon: ImageVector,
    selected: Boolean,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = SongfyIconStyle.TabIconSize,
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = if (selected) SongfyIconStyle.ActiveTint else SongfyIconStyle.InactiveTint,
        modifier = modifier.size(size),
    )
}

@Composable
fun SongfyPlayerGlyph(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = SongfyIconStyle.PlayerGlyphTint,
    size: Dp = SongfyIconStyle.PlayerGlyphSize,
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(size),
    )
}

// ---------------------------------------------------------------------------
// Deteksi Real-Time Perangkat Output Audio (Buds / TWS, Speaker, Headphones, Car, TV, Phone)
// ---------------------------------------------------------------------------

fun resolveAudioOutputState(context: Context, audioManager: AudioManager): SongfyAudioOutputState {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val outputs = runCatching { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) }.getOrNull().orEmpty()

        // Prioritas 1: Bluetooth (A2DP / SCO / BLE Headset / BLE Speaker / Hearing Aid)
        val btDevice = outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (it.type == AudioDeviceInfo.TYPE_BLE_HEADSET || it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER)) ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && it.type == AudioDeviceInfo.TYPE_HEARING_AID)
        }
        if (btDevice != null) {
            val rawName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                btDevice.productName?.toString()?.trim()?.takeIf { it.isNotBlank() && !it.equals(Build.MODEL, ignoreCase = true) }
            } else null
            val name = rawName ?: runCatching {
                @Suppress("MissingPermission")
                BluetoothAdapter.getDefaultAdapter()?.bondedDevices?.firstOrNull()?.name
            }.getOrNull() ?: "Bluetooth"

            val lower = name.lowercase()
            val kind = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && btDevice.type == AudioDeviceInfo.TYPE_BLE_SPEAKER -> SongfyOutputKind.SPEAKER
                lower.contains("car") || lower.contains("handsfree") || lower.contains("mycar") -> SongfyOutputKind.CAR
                lower.contains("speaker") || lower.contains("soundbar") || lower.contains("jbl") ||
                    lower.contains("boombox") || lower.contains("homepod") || lower.contains("sonos") ||
                    lower.contains("marshall") || lower.contains("bose soundlink") -> SongfyOutputKind.SPEAKER
                else -> SongfyOutputKind.BUDS
            }
            return SongfyAudioOutputState(kind = kind, deviceName = name)
        }

        // Prioritas 2: Headphone kabel / USB Headset
        val wiredDevice = outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && it.type == AudioDeviceInfo.TYPE_USB_HEADSET)
        }
        if (wiredDevice != null) {
            val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                wiredDevice.productName?.toString()?.trim()?.takeIf { it.isNotBlank() && !it.equals(Build.MODEL, ignoreCase = true) }
            } else null
            return SongfyAudioOutputState(kind = SongfyOutputKind.HEADPHONES, deviceName = name ?: "Headphones")
        }

        // Prioritas 3: HDMI / TV
        val hdmiDevice = outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_HDMI ||
                it.type == AudioDeviceInfo.TYPE_HDMI_ARC ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_HDMI_EARC)
        }
        if (hdmiDevice != null) {
            val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                hdmiDevice.productName?.toString()?.trim()?.takeIf { it.isNotBlank() }
            } else null
            return SongfyAudioOutputState(kind = SongfyOutputKind.HDMI, deviceName = name ?: "TV")
        }
    }
    return SongfyAudioOutputState(kind = SongfyOutputKind.PHONE, deviceName = null)
}

@Composable
fun rememberSongfyAudioOutputState(): State<SongfyAudioOutputState> {
    val context = LocalContext.current
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val state = remember { mutableStateOf(resolveAudioOutputState(context, audioManager)) }

    DisposableEffect(context, audioManager) {
        state.value = resolveAudioOutputState(context, audioManager)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val callback = object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    state.value = resolveAudioOutputState(context, audioManager)
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    state.value = resolveAudioOutputState(context, audioManager)
                }
            }
            audioManager.registerAudioDeviceCallback(callback, null)
            onDispose {
                audioManager.unregisterAudioDeviceCallback(callback)
            }
        } else {
            onDispose { }
        }
    }

    return state
}

