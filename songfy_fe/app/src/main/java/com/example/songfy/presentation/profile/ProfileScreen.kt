package com.lagu.songfy.presentation.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.lagu.songfy.data.remote.dto.SongDto
import com.lagu.songfy.data.repository.SongHistoryItem
import com.lagu.songfy.presentation.theme.*

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
    canGoBack: Boolean = false,
    onBackClick: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToLikedSongs: () -> Unit = {},
    onSongClickWithPos: ((SongDto, Long) -> Unit)? = null,
    onSongClick: (SongDto) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Format total listening time
    val listeningTimeText = remember(uiState.totalHoursListened) {
        val hrs = uiState.totalHoursListened.toDoubleOrNull() ?: 0.0
        if (hrs >= 1.0) {
            "${uiState.totalHoursListened} Jam"
        } else {
            val minutes = (hrs * 60).toInt().coerceAtLeast(0)
            if (minutes > 0) "$minutes Menit" else "0 Menit"
        }
    }

    // Avatar Photo Picker Launcher
    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.updateAvatarUri(it) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundNearBlack)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // ----------------------------------------------------
            // 1. IMMERSIVE HERO BANNER (Menyelimuti sampai layout following)
            // ----------------------------------------------------
            item {
                val bannerModel = uiState.bannerUrl ?: "https://yt3.googleusercontent.com/RYNqywBw8ubgXGJNSRbtd3y7wtkbAZym24vpA-nNZ153mDHTyEuGgo1L4ecEMyiI1ts2OdwI0Q=s1200"

                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Full-width Profile Banner Image (Menyelimuti seluruh bagian atas hingga layout Following)
                    AsyncImage(
                        model = bannerModel,
                        contentDescription = "Profile Banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )

                    // Gradient overlay for contrast and smooth blending into background (Draw phase, zero GPU offscreen overhead)
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.Black.copy(alpha = 0.40f),
                                        0.25f to Color.Transparent,
                                        0.60f to Color.Black.copy(alpha = 0.35f),
                                        0.85f to BackgroundNearBlack.copy(alpha = 0.88f),
                                        1.0f to BackgroundNearBlack
                                    )
                                )
                            )
                    )

                    // Foreground Content (Top Bar -> Avatar -> Name/Bio -> Stats Bar)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top Bar Actions (Back Arrow if canGoBack + Setting Button)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (canGoBack) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.5f))
                                        .clickable(onClick = onBackClick),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = TextPrimaryWhite,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.size(38.dp))
                            }

                            // Setting Button (Reference Layout)
                            Text(
                                text = "Setting",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimaryWhite,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .clickable(onClick = onNavigateToSettings)
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Centered Circular Avatar (PP)
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier.size(104.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(100.dp)
                                        .shadow(12.dp, CircleShape)
                                        .clip(CircleShape)
                                        .border(3.dp, BackgroundNearBlack, CircleShape)
                                        .background(SurfaceDarkNavy)
                                        .clickable { avatarPickerLauncher.launch("image/*") },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (uiState.avatarUrl != null) {
                                        AsyncImage(
                                            model = uiState.avatarUrl,
                                            contentDescription = "User Avatar",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        // Default Initial Badge
                                        Text(
                                            text = uiState.username.take(1).uppercase(),
                                            fontSize = 38.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AccentSilver
                                        )
                                    }

                                    if (uiState.isUploadingAvatar) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(36.dp),
                                            color = AccentSilver,
                                            strokeWidth = 3.dp
                                        )
                                    }
                                }

                                // Avatar Camera Edit Badge Overlay
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(PlayButtonWhite)
                                        .clickable { avatarPickerLauncher.launch("image/*") },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Edit Avatar",
                                        tint = BackgroundNearBlack,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Display Name
                        Text(
                            text = uiState.username,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryWhite,
                            letterSpacing = (-0.4).sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Music Stats Bar (Waktu Putar & History)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(SurfaceCard.copy(alpha = 0.82f))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                                .padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MusicStatItem(
                                value = listeningTimeText,
                                label = "Waktu Putar",
                                modifier = Modifier.weight(1f)
                            )
                            VerticalDivider(
                                modifier = Modifier.height(28.dp),
                                color = Color.White.copy(alpha = 0.12f)
                            )
                            MusicStatItem(
                                value = "${uiState.songHistory.size}",
                                label = "History",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // ----------------------------------------------------
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Riwayat Mendengarkan",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryWhite,
                        letterSpacing = (-0.3).sp
                    )

                    if (uiState.songHistory.isNotEmpty()) {
                        Text(
                            text = "${uiState.songHistory.size} Lagu",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = AccentSilver
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (uiState.songHistory.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 24.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(SurfaceCard)
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(SurfacePillInactive),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = TextSecondaryGray,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Belum ada riwayat lagu",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryWhite
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Mulai mendengarkan lagu favoritmu di Songfy untuk melihat riwayat pendengaran di sini.",
                                fontSize = 13.sp,
                                color = TextSecondaryGray,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            } else {
                // Key stabil tanpa index — history bertambah di depan tanpa re-layout total.
                itemsIndexed(uiState.songHistory, key = { _, historyItem -> "hist_${historyItem.id}" }) { _, historyItem ->
                    SongHistoryCard(
                        historyItem = historyItem,
                        username = uiState.username,
                        userAvatarUrl = uiState.avatarUrl,
                        onClick = {
                            val resumePos = if (!historyItem.isCompleted) historyItem.progressMs else 0L
                            if (onSongClickWithPos != null) {
                                onSongClickWithPos(historyItem.song, resumePos)
                            } else {
                                onSongClick(historyItem.song)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
fun MusicStatItem(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimaryWhite,
            letterSpacing = (-0.3).sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            color = TextSecondaryGray
        )
    }
}

private fun formatMinutesSeconds(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun parseDurationMs(durationStr: String?): Long {
    if (durationStr.isNullOrBlank()) return 0L
    val parts = durationStr.split(":").mapNotNull { it.trim().toLongOrNull() }
    return when (parts.size) {
        1 -> parts[0] * 1000L
        2 -> (parts[0] * 60L + parts[1]) * 1000L
        3 -> (parts[0] * 3600L + parts[1] * 60L + parts[2]) * 1000L
        else -> 0L
    }
}

@Composable
fun SongHistoryCard(
    historyItem: SongHistoryItem,
    username: String,
    userAvatarUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val song = historyItem.song
    val artistName = song.artists.joinToString(", ") { it.name }.ifBlank { "Various Artists" }

    val parsedDurMs = parseDurationMs(song.duration)
    val totalDurMs = if (historyItem.totalDurationMs > 0L) historyItem.totalDurationMs else if (parsedDurMs > 0L) parsedDurMs else 262000L
    val currentProgMs = if (historyItem.isCompleted) totalDurMs else historyItem.progressMs.coerceIn(0L, totalDurMs)
    val progressRatio = if (totalDurMs > 0L) (currentProgMs.toFloat() / totalDurMs.toFloat()).coerceIn(0f, 1f) else 0f
    val currentProgText = formatMinutesSeconds(currentProgMs)
    val totalDurText = if (!song.duration.isNullOrBlank()) song.duration else formatMinutesSeconds(totalDurMs)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceCard)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        // User Header Row (Reference layout: Avatar + Username + Time)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(AccentSilver),
                    contentAlignment = Alignment.Center
                ) {
                    if (userAvatarUrl != null) {
                        AsyncImage(
                            model = userAvatarUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = username.take(1).uppercase(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = BackgroundNearBlack
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = username,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimaryWhite
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Verified Badge
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF3B82F6)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(8.dp)
                    )
                }
            }

            Text(
                text = "â€¢ ${historyItem.relativeTimeText}",
                fontSize = 12.sp,
                color = TextSecondaryGray
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Song Content Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Track Thumbnail
            AsyncImage(
                model = song.thumbnailUrl,
                contentDescription = song.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDarkNavy)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = artistName,
                    fontSize = 12.sp,
                    color = TextSecondaryGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Playback Bar Progress (Reference layout)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = AccentSilver,
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Progress line
            LinearProgressIndicator(
                progress = { progressRatio },
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(CircleShape),
                color = TrackActive,
                trackColor = Color.White.copy(alpha = 0.15f)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = "$currentProgText / $totalDurText",
                fontSize = 11.sp,
                color = TextSecondaryGray
            )
        }
    }
}
