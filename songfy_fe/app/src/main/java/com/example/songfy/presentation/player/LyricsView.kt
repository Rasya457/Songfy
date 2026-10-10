package com.lagu.songfy.presentation.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagu.songfy.presentation.theme.*

// NOTE 2026-09-12: LyricsView TIDAK dipakai di mana pun (dead code) —
// Player memakai FullLyricsScreen di PlayerScreen.kt. File ini dipertahankan
// sebagai referensi desain, tapi signature dimigrasikan dari field state yang
// sudah dihapus (currentPositionMs/activeLyricIndex) agar tetap compile.
// Jangan panggil tanpa pasok activeLyricIndex + positionMs dari flow.
@Composable
fun LyricsView(
    uiState: PlayerUiState,
    activeLyricIndex: Int = 0,
    positionMs: Long = 0L,
    onCloseLyrics: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val song = uiState.currentSong
    val artistName = song?.artists?.joinToString(", ") { it.name } ?: "Unknown Artist"

    // Auto-scroll to active lyric line — snap, same rationale as FullLyricsScreen:
    // animated scrolls pile up during playback and risk OOM on long sessions.
    LaunchedEffect(activeLyricIndex) {
        if (uiState.isSyncedLyrics && uiState.lyrics.isNotEmpty()) {
            val target = (activeLyricIndex - 2).coerceAtLeast(0)
            try {
                listState.scrollToItem(target)
            } catch (_: Exception) { /* list changed mid-scroll — next tick retries */ }
        }
    }

    val gradientBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF1E293B),
                Color(0xFF0F172A),
                BackgroundNearBlack
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(gradientBrush)
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount > 35) {
                        onCloseLyrics()
                    }
                }
            }
            .systemBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ==========================================
            // 1. TOP HEADER
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCloseLyrics,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Close Lyrics",
                        tint = TextPrimaryWhite,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = song?.title ?: "Songfy",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = song?.album ?: artistName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = TextSecondaryGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.size(40.dp))
            }

            // ==========================================
            // 2. MAIN LYRICS SCROLL AREA / EMPTY STATE
            // ==========================================
            if (uiState.isLoadingLyrics && uiState.lyrics.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = AccentSilver,
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Memuat lirik...",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondaryGray
                        )
                    }
                }
            } else if (uiState.lyrics.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = TextSecondaryGray.copy(alpha = 0.4f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Lirik belum tersedia untuk lagu ini",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimaryWhite.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Nikmati alunan musikmu",
                            fontSize = 14.sp,
                            color = TextSecondaryGray.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    contentPadding = PaddingValues(top = 28.dp, bottom = 120.dp)
                ) {
                    itemsIndexed(
                        uiState.lyrics,
                        key = { index, line -> "${index}_${line.timeMs}" }
                    ) { index, line ->
                        val isActive = index == activeLyricIndex && uiState.isSyncedLyrics
                        val textColor = if (isActive) TextPrimaryWhite else TextPrimaryWhite.copy(alpha = 0.35f)
                        val fontSize = if (isActive) 25.sp else 22.sp

                        Text(
                            text = line.text,
                            fontSize = fontSize,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            lineHeight = 34.sp,
                            letterSpacing = (-0.3).sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp)
                                .clickable {
                                    if (uiState.isSyncedLyrics) {
                                        onSeekTo(line.timeMs)
                                    }
                                }
                        )
                    }
                }
            }

            // ==========================================
            // 3. BOTTOM CONTROLS & TIMELINE
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BackgroundNearBlack.copy(alpha = 0.92f))
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { /* share lyrics */ },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = TextPrimaryWhite.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = { /* more options */ },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More",
                            tint = TextPrimaryWhite.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                val progressFraction = if (uiState.durationMs > 0) {
                    (positionMs.toFloat() / uiState.durationMs.toFloat()).coerceIn(0f, 1f)
                } else 0f

                Slider(
                    value = progressFraction,
                    onValueChange = { frac ->
                        onSeekTo((frac * uiState.durationMs).toLong())
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = TextPrimaryWhite,
                        activeTrackColor = TextPrimaryWhite,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatMillis(positionMs),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = TextSecondaryGray
                    )
                    Text(
                        text = formatMillis(uiState.durationMs),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = TextSecondaryGray
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(TextPrimaryWhite)
                            .clickable(onClick = onTogglePlayPause),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = BackgroundNearBlack,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatMillis(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
