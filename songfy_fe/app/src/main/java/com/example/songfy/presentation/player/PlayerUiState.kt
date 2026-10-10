package com.lagu.songfy.presentation.player

import com.lagu.songfy.data.remote.dto.SongDto

data class LyricLine(
    val timeMs: Long = 0L,
    val text: String,
    val isInstrumental: Boolean = false
)

data class PlayerUiState(
    val currentSong: SongDto? = null,
    val isPlaying: Boolean = false,
    // NOTE: currentPositionMs/activeLyricIndex REMOVED 2026-09-12 — keduanya bergerak
    // tiap detik/baris; menaruhnya di state = emit penuh tiap tick = seluruh observer
    // recompose. Posisi baca dari playbackTick, baris lirik dari lyricActiveIndex.
    // (Sampah lanjutan dari isLyricsOpen/volume/eqPreset/waveAmplitudes/playCount.)
    val durationMs: Long = 262000L, // 04:22 fallback duration
    val isLiked: Boolean = false,
    val isShuffle: Boolean = false,
    val isRepeat: Boolean = false,
    val lyrics: List<LyricLine> = emptyList(),
    val isSyncedLyrics: Boolean = false,
    val queue: List<SongDto> = emptyList(),
    val currentIndex: Int = 0,
    // isLoadingAudio/isLoadingAutoQueue TIDAK dipakai UI mana pun (grep 2026-09-12) —
    // emit-nya dihapus dari ViewModel; field dibiarkan agar copy() lama tetap compile.
    // ponytail: hapus kedua field saat menyentuh file ini lagi.
    val isLoadingAudio: Boolean = false,
    val isLoadingLyrics: Boolean = false,
    val isLoadingAutoQueue: Boolean = false,
    val sleepTimerMinutes: Int? = null,
    val lyricOffsetMs: Long = 0L,
    val detectedLanguage: String? = null,
    val isTranslationActive: Boolean = false,
    val translatedLyrics: Map<Int, String> = emptyMap(),
    val isTranslating: Boolean = false,
    val translationError: String? = null,
    val error: String? = null
) {
    val canTranslate: Boolean
        get() = detectedLanguage != null &&
                detectedLanguage != "id" &&
                detectedLanguage != "in" &&
                detectedLanguage != "en" &&
                detectedLanguage != "und"
}