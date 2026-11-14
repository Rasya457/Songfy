package com.metrolist.music.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.songfy.ui.icons.SongfyIcons

// Spesifikasi panel asli BitChord (IosMenu.kt / SongActionsMenu.kt)
private val MenuFill = Color(0xFF1C1C1E).copy(alpha = 0.94f)
private val MenuRim = Color.White.copy(alpha = 0.12f)
private val MenuSeparator = Color.White.copy(alpha = 0.10f)
private val MenuSecondary = Color.White.copy(alpha = 0.65f)
private val MenuShape = RoundedCornerShape(28.dp)
private val MenuDefaultWidth = 268.dp

/**
 * SongOptionsMenu — Panel menu mengambang (floating popover) gaya iOS ala BitChord.
 * Dilengkapi animasi scale & fade yang mulus tersinkronisasi dengan efek blur seluruh layar.
 */
@Composable
fun SongOptionsMenu(
    albumName: String,
    artistName: String,
    animProgress: Float,
    isLyricsOrQueueMode: Boolean = false,
    isCached: Boolean = true,
    isDownloaded: Boolean = false,
    isLocal: Boolean = false,
    keepScreenOn: Boolean = false,
    width: Dp = MenuDefaultWidth,
    onDismiss: () -> Unit,
    onAction: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }

    val scale = androidx.compose.ui.util.lerp(0.82f, 1f, animProgress)
    val alpha = (animProgress * 1.5f).coerceIn(0f, 1f)
    val scrimAlpha = (animProgress * 0.42f).coerceIn(0f, 0.42f)

    val alignment = if (isLyricsOrQueueMode) Alignment.TopEnd else Alignment.BottomEnd
    val transformOrigin = if (isLyricsOrQueueMode) TransformOrigin(0.92f, 0.08f) else TransformOrigin(0.92f, 0.92f)
    val menuPadding = if (isLyricsOrQueueMode) {
        Modifier.padding(end = 16.dp, top = 68.dp)
    } else {
        Modifier.padding(end = 16.dp, bottom = 120.dp)
    }

    // Scrim gelap di belakang yang memudar sesuai animasi progress
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = alignment,
    ) {
        Column(
            modifier = menuPadding
                .width(width)
                .heightIn(max = 520.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                    this.transformOrigin = transformOrigin
                }
                .shadow(
                    elevation = 28.dp,
                    shape = MenuShape,
                    ambientColor = Color.Black.copy(alpha = 0.45f),
                    spotColor = Color.Black.copy(alpha = 0.45f),
                )
                .clip(MenuShape)
                .background(MenuFill)
                .border(0.5.dp, MenuRim, MenuShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = false,
                ) {} // Menahan tap agar tidak tembus ke scrim
                .padding(vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // Quick action: Share (ikon di atas, teks di tengah)
            if (!isLocal) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true),
                            onClick = { onAction("share") },
                        )
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = SongfyIcons.Share,
                        contentDescription = "Share",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Share",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // ── Grup 1: Kualitas Audio & Info Lagu ──
            HorizontalDivider(color = MenuSeparator, thickness = 0.5.dp)
            MenuRow(
                icon = SongfyIcons.Sliders,
                title = "Upgrade quality",
                subtitle = "Audio format & technical specs",
                onClick = { onAction("upgrade") },
            )
            MenuRow(
                icon = SongfyIcons.Info,
                title = "Song Info",
                subtitle = "Complete track metadata",
                onClick = { onAction("info") },
            )

            // ── Grup 2: Pemutaran, Antrean & Playlist ──
            HorizontalDivider(color = MenuSeparator, thickness = 0.5.dp)
            MenuRow(
                icon = SongfyIcons.PlayNext,
                title = "Play next",
                onClick = { onAction("play_next") },
            )
            MenuRow(
                icon = SongfyIcons.Queue,
                title = "Add to queue",
                onClick = { onAction("queue") },
            )
            MenuRow(
                icon = SongfyIcons.PlusCircle,
                title = "Add to playlist",
                onClick = { onAction("playlist") },
            )
            MenuRow(
                icon = SongfyIcons.Radio,
                title = "Start radio",
                onClick = { onAction("radio") },
            )
            MenuRow(
                icon = SongfyIcons.Video,
                title = "Convert to video",
                onClick = { onAction("video") },
            )

            // ── Grup 3: Unduhan & Cache ──
            HorizontalDivider(color = MenuSeparator, thickness = 0.5.dp)
            if (!isDownloaded) {
                MenuRow(
                    icon = SongfyIcons.Download,
                    title = "Download",
                    onClick = { onAction("download") },
                )
            } else {
                MenuRow(
                    icon = SongfyIcons.Trash,
                    title = "Remove download",
                    onClick = { onAction("remove_download") },
                )
            }
            if (isCached) {
                MenuRow(
                    icon = SongfyIcons.Refresh,
                    title = "Remove from cache",
                    onClick = { onAction("remove_cache") },
                )
            }

            // ── Grup 4: Album & Artis ──
            HorizontalDivider(color = MenuSeparator, thickness = 0.5.dp)
            MenuRow(
                icon = SongfyIcons.Album,
                title = "Open album",
                subtitle = albumName,
                onClick = { onAction("album") },
            )
            MenuRow(
                icon = SongfyIcons.Artist,
                title = "Open artist",
                subtitle = artistName,
                onClick = { onAction("artist") },
            )

            // ── Grup 5: Alat Pemutar ──
            HorizontalDivider(color = MenuSeparator, thickness = 0.5.dp)
            MenuRow(
                icon = SongfyIcons.Moon,
                title = "Sleep timer",
                onClick = { onAction("sleep") },
            )
            MenuRow(
                icon = SongfyIcons.Sun,
                title = "Keep screen on",
                subtitle = if (keepScreenOn) "On" else "Off",
                onClick = { onAction("keep_screen_on") },
            )
            MenuRow(
                icon = SongfyIcons.Sliders,
                title = "Lyrics offset",
                onClick = { onAction("lyrics_offset") },
            )
            MenuRow(
                icon = SongfyIcons.Info,
                title = "Copy log",
                onClick = { onAction("copy_log") },
            )
        }
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = it,
                    color = MenuSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
