/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * CrossfadeController.kt — Level 0 (Gapless) + Level 1 (Standard Crossfade).
 *
 * Arsitektur:
 *   - Dua ExoPlayer sejawat (playerA / playerB), pointer active / standby.
 *   - Anti-double audio: standby SELALU diisi lagu BARU, bukan lagu lama.
 *   - Kurva equal-power sin/cos: tidak ada dip volume di tengah transisi.
 *   - Midpoint handoff: MediaSession + UI state pindah di 50% durasi blend.
 *   - Trigger 500ms ticker di service, bukan tiap frame UI.
 *   - cancelTransition() dipanggil saat Pause / Seek / Next / manual seek.
 *
 * Di Songfy, crossfade sudah diimplementasikan langsung di MusicService.
 * File ini menyediakan controller mandiri yang dapat digunakan secara
 * standalone atau diintegrasikan sebagai drop-in ke service.
 */

package com.metrolist.music.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
// Settings
// ─────────────────────────────────────────────────────────────────────────────

enum class CrossfadeMode { OFF, STANDARD }

data class TransitionSettings(
    val mode: CrossfadeMode = CrossfadeMode.STANDARD,
    /** 0f = gapless (tanpa overlap). 1f–12f = detik crossfade. */
    val crossfadeSeconds: Float = 6f,
)

// ─────────────────────────────────────────────────────────────────────────────
// Level 0 — Gapless helper (dokumentasi pola yang benar)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Gapless playback yang benar di ExoPlayer:
 * muat seluruh antrean sekaligus dengan setMediaItems() agar ExoPlayer
 * dapat pre-buffer lagu berikutnya sebelum lagu sekarang habis.
 *
 * Yang merusak gapless:
 *   - setMediaItem() satu per satu saat lagu habis → loading ulang = jeda
 *   - format berbeda (sample rate / channel) → decoder di-reset
 *   - stop() / seek() di antara lagu
 *   - URL lagu berikut belum ter-resolve saat dibutuhkan
 */
object GaplessPlayback {
    fun prepareQueue(player: ExoPlayer, queue: List<MediaItem>, startIndex: Int = 0) {
        player.setMediaItems(queue, startIndex, 0L)
        player.prepare()
        player.play()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Level 1 — Crossfade Controller (dual-player equal-power blend)
// ─────────────────────────────────────────────────────────────────────────────

class CrossfadeController(
    private val scope: CoroutineScope,
    /** Factory untuk membuat ExoPlayer baru (lengkap: audio processor, cache, dsb). */
    private val buildPlayer: () -> ExoPlayer,
    /**
     * Callback yang dipanggil di midpoint (50% durasi blend).
     * Di sinilah MediaSession.player, audio focus, dan notifikasi
     * dipindahkan dari [outgoing] ke [incoming].
     */
    private val onHandoff: (outgoing: ExoPlayer, incoming: ExoPlayer) -> Unit,
    /**
     * Callback saat sisa antrean perlu disinkronisasi ke player yang baru
     * menjadi active setelah handoff selesai.
     */
    private val onQueueSync: ((active: ExoPlayer) -> Unit)? = null,
) {
    private var playerA: ExoPlayer = buildPlayer()
    private var playerB: ExoPlayer = buildPlayer()

    /** Player yang sedang aktif memutar dan memegang MediaSession. */
    var active: ExoPlayer = playerA
        private set

    private val standby: ExoPlayer
        get() = if (active === playerA) playerB else playerA

    private var fadeJob: Job? = null

    var settings: TransitionSettings = TransitionSettings()

    /** True selama blend berlangsung. */
    val isCrossfading: Boolean
        get() = fadeJob?.isActive == true

    // ── Queue ────────────────────────────────────────────────────────────────

    /**
     * Muat antrean ke player aktif (gapless). Gunakan ini saat pertama kali
     * memuat antrean atau saat mode OFF / gapless.
     */
    fun playQueue(queue: List<MediaItem>, startIndex: Int = 0) {
        active.setMediaItems(queue, startIndex, 0L)
        active.prepare()
        active.play()
    }

    // ── Transition ───────────────────────────────────────────────────────────

    /**
     * Mulai crossfade ke [nextItem].
     *
     * Panggil dari service ticker (tiap 500ms, bukan tiap frame), saat:
     *   duration - currentPosition <= crossfadeSeconds * 1000
     * dan belum di-arm untuk lagu yang sedang aktif.
     *
     * Jika [settings.mode] == OFF atau [settings.crossfadeSeconds] <= 0,
     * fungsi ini tidak melakukan apapun (gapless ditangani ExoPlayer sendiri).
     */
    fun beginTransition(nextItem: MediaItem) {
        if (settings.mode == CrossfadeMode.OFF || settings.crossfadeSeconds <= 0f) return
        if (fadeJob?.isActive == true) return

        val incoming = standby
        val outgoing = active

        // 1. Muat lagu BARU di standby — JANGAN lagu lama.
        //    Ini mencegah double-audio (lagu lama main di dua player sekaligus).
        incoming.setMediaItem(nextItem)
        incoming.prepare()
        incoming.volume = 0f
        incoming.play()

        val fadeMs = (settings.crossfadeSeconds * 1000f).toLong().coerceAtLeast(300L)
        val halfMs = fadeMs / 2L

        fadeJob = scope.launch {
            val start = System.currentTimeMillis()
            var handedOff = false

            // 2. Loop 16ms (≈60 fps) — equal-power sin/cos curve.
            //    sin²(t) + cos²(t) = 1 → total daya konstan, tidak ada dip.
            while (isActive) {
                val elapsed = System.currentTimeMillis() - start
                if (elapsed >= fadeMs) break

                val t = (elapsed.toFloat() / fadeMs).coerceIn(0f, 1f)
                val angle = t * PI.toFloat() / 2f

                try {
                    outgoing.volume = cos(angle)   // 1 → 0
                    incoming.volume = sin(angle)   // 0 → 1
                } catch (_: Exception) {
                    break
                }

                // 3. Midpoint handoff — tepat di 50%, saat kedua lagu sama keras.
                if (!handedOff && elapsed >= halfMs) {
                    handedOff = true
                    // Tukar pointer sebelum callback agar onHandoff menerima
                    // state yang sudah benar.
                    active = incoming
                    onHandoff(outgoing, incoming)
                    onQueueSync?.invoke(active)
                }

                delay(16L)
            }

            // 4. Selesai: pastikan volume final bersih.
            try {
                outgoing.volume = 0f
                incoming.volume = 1f
                outgoing.stop()
            } catch (_: Exception) {}

            if (!handedOff) {
                active = incoming
                onHandoff(outgoing, incoming)
                onQueueSync?.invoke(active)
            }

            fadeJob = null
        }
    }

    // ── Cancel ───────────────────────────────────────────────────────────────

    /**
     * Batalkan blend yang sedang berjalan.
     *
     * Panggil setiap kali user melakukan aksi manual:
     * Pause, Seek, Next, Prev, atau pilih lagu manual.
     * Ini mencegah dua lagu berbunyi bersamaan setelah aksi manual.
     */
    fun cancelTransition() {
        fadeJob?.cancel()
        fadeJob = null
        try {
            standby.stop()
            standby.volume = 0f
            active.volume = 1f
        } catch (_: Exception) {}
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    fun release() {
        fadeJob?.cancel()
        try { playerA.release() } catch (_: Exception) {}
        try { playerB.release() } catch (_: Exception) {}
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Panduan integrasi ke MusicService (komentar, bukan kode aktif):
//
// val crossfadeController = CrossfadeController(
//     scope = serviceScope,
//     buildPlayer = { createExoPlayer() },
//     onHandoff = { _, incoming ->
//         player = incoming
//         mediaSession?.let { (it as MediaSession).player = incoming }
//         sleepTimer?.player = incoming
//     },
//     onQueueSync = { active ->
//         // Sinkronisasi repeatMode, shuffleMode, dan sisa queue ke player baru
//         active.repeatMode = player.repeatMode
//         active.shuffleModeEnabled = player.shuffleModeEnabled
//     },
// )
//
// // Ticker 500ms di service (bukan tiap frame UI):
// scope.launch {
//     while (isActive) {
//         delay(500)
//         if (!crossfadeController.isCrossfading) {
//             val remaining = player.duration - player.currentPosition
//             val threshold = (settings.crossfadeSeconds * 1000).toLong()
//             if (remaining in 0..threshold && player.hasNextMediaItem()) {
//                 val next = player.getMediaItemAt(player.nextMediaItemIndex)
//                 crossfadeController.beginTransition(next)
//             }
//         }
//     }
// }
//
// // Batalkan saat aksi manual:
// fun onUserSeek() { crossfadeController.cancelTransition() }
// fun onUserNext() { crossfadeController.cancelTransition(); player.seekToNext() }
// fun onUserPause() { crossfadeController.cancelTransition(); player.pause() }
// ─────────────────────────────────────────────────────────────────────────────

