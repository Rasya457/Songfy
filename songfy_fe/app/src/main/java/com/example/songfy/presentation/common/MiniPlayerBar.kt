package com.lagu.songfy.presentation.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.outlined.AddCircleOutline
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lagu.songfy.presentation.player.PlayerUiState
import com.lagu.songfy.data.remote.dto.highResThumbnailUrl
import com.lagu.songfy.presentation.theme.AccentSilver
import com.lagu.songfy.presentation.theme.AccentSilverDim
import com.lagu.songfy.presentation.theme.BackgroundNearBlack
import com.lagu.songfy.presentation.theme.SurfaceDarkNavy
import com.lagu.songfy.util.rememberAudioOutputDevice
import com.lagu.songfy.util.rememberMiniPlayerColors
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayerBar(
    uiState: PlayerUiState,
    onTogglePlayPause: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    playbackTickFlow: StateFlow<Triple<Long, Long, Boolean>>? = null,
    playbackTick: Triple<Long, Long, Boolean> = Triple(0L, 0L, false)
) {
    val currentSong = uiState.currentSong ?: return
    val audioDevice = rememberAudioOutputDevice()
    val dynamicColors = rememberMiniPlayerColors(currentSong.highResThumbnailUrl)
    val artistName = remember(currentSong.artists) {
        currentSong.artists.joinToString(", ") { it.name }.ifBlank { "Unknown Artist" }
    }

    // Lightweight tactile press animation (0.976 scale on tap via graphicsLayer, zero layout cost)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.976f else 1f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "miniPlayerPressScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .height(56.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(8.dp),
                ambientColor = dynamicColors.topColor.copy(alpha = 0.65f),
                spotColor = Color.Black.copy(alpha = 0.5f)
            )
            .clip(RoundedCornerShape(8.dp))
            // Dynamic gradient otomatis menyesuaikan warna cover lagu (merah -> merah, biru -> biru, dsb)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        dynamicColors.topColor,
                        dynamicColors.bottomColor
                    )
                )
            )
            .border(
                width = 0.75.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        dynamicColors.accentColor.copy(alpha = 0.35f),
                        Color.White.copy(alpha = 0.05f)
                    )
                ),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null, // Tactile scale provides instant feedback without heavy ripple delay
                onClick = onNavigateToPlayer
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album Artwork (40dp with 6dp rounded corner) — fast AsyncImage
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF282828)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(currentSong.highResThumbnailUrl)
                        .crossfade(200)
                        .build(),
                    contentDescription = currentSong.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Title & Artist with basicMarquee for long titles
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = currentSong.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Connected/Active indicator in dynamic accent color
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dynamicColors.accentColor.copy(alpha = 0.9f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .clip(CircleShape)
                                .background(BackgroundNearBlack)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = artistName,
                        fontSize = 11.sp,
                        color = Color(0xFFD1D5DB),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // Granular duration text: only this sub-composable re-renders on playback tick
                    MiniPlayerDurationText(
                        playbackTickFlow = playbackTickFlow,
                        fallbackTick = playbackTick,
                        fallbackDurationMs = uiState.durationMs
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Dynamic Audio Output: Earphone / Smartphone icon
            IconButton(
                onClick = onNavigateToPlayer,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = if (audioDevice.isHeadphones) Icons.Default.Headphones else Icons.Default.Smartphone,
                    contentDescription = if (audioDevice.isHeadphones) "Earphone Terhubung" else "Speaker Ponsel",
                    tint = if (audioDevice.isHeadphones) Color(0xFF1ED760) else Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Add button (plus circle)
            IconButton(
                onClick = { /* Add to library / playlist */ },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.AddCircleOutline,
                    contentDescription = "Add to Playlist",
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Play / Pause Toggle Button with isolated tick observer
            IconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier.size(36.dp)
            ) {
                MiniPlayerPlayPauseIcon(
                    uiStateIsPlaying = uiState.isPlaying,
                    playbackTickFlow = playbackTickFlow,
                    fallbackTick = playbackTick
                )
            }
        }

        // Bottom Edge 3dp Playback Progress Bar isolated in child composable
        MiniPlayerProgressBar(
            playbackTickFlow = playbackTickFlow,
            fallbackTick = playbackTick,
            fallbackDurationMs = uiState.durationMs,
            accentColor = dynamicColors.accentColor,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun MiniPlayerDurationText(
    playbackTickFlow: StateFlow<Triple<Long, Long, Boolean>>?,
    fallbackTick: Triple<Long, Long, Boolean>,
    fallbackDurationMs: Long
) {
    val tick = playbackTickFlow?.collectAsStateWithLifecycle()?.value ?: fallbackTick
    val (currentPosMs, durationMs, _) = tick
    val effectiveDuration = if (durationMs > 0) durationMs else fallbackDurationMs
    val effectivePosition = if (currentPosMs > 0) currentPosMs else 0L

    if (effectiveDuration > 0) {
        val formattedCurrentTime = formatMiniDuration(effectivePosition)
        val formattedTotalTime = formatMiniDuration(effectiveDuration)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "• $formattedCurrentTime / $formattedTotalTime",
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = AccentSilver.copy(alpha = 0.85f),
            maxLines = 1
        )
    }
}

@Composable
private fun MiniPlayerPlayPauseIcon(
    uiStateIsPlaying: Boolean,
    playbackTickFlow: StateFlow<Triple<Long, Long, Boolean>>?,
    fallbackTick: Triple<Long, Long, Boolean>
) {
    val tick = playbackTickFlow?.collectAsStateWithLifecycle()?.value ?: fallbackTick
    val isPlaying = tick.third || uiStateIsPlaying
    Icon(
        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
        contentDescription = if (isPlaying) "Pause" else "Play",
        tint = Color.White,
        modifier = Modifier.size(26.dp)
    )
}

@Composable
private fun MiniPlayerProgressBar(
    playbackTickFlow: StateFlow<Triple<Long, Long, Boolean>>?,
    fallbackTick: Triple<Long, Long, Boolean>,
    fallbackDurationMs: Long,
    accentColor: Color = Color(0xFFC7C7CC),
    modifier: Modifier = Modifier
) {
    val tick = playbackTickFlow?.collectAsStateWithLifecycle()?.value ?: fallbackTick
    val (currentPosMs, durationMs, _) = tick
    val effectiveDuration = if (durationMs > 0) durationMs else fallbackDurationMs
    val effectivePosition = if (currentPosMs > 0) currentPosMs else 0L

    val progress = if (effectiveDuration > 0) {
        (effectivePosition.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(Color.White.copy(alpha = 0.12f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.85f),
                            Color(0xFFFFFFFF)  // Crisp bright white head
                        )
                    )
                )
        )
    }
}

private fun formatMiniDuration(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSec = ms / 1000
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return "%02d:%02d".format(minutes, seconds)
}