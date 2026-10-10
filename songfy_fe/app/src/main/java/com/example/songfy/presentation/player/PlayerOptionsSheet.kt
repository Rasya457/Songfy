package com.lagu.songfy.presentation.player

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lagu.songfy.data.remote.dto.SongDto
import com.lagu.songfy.presentation.theme.AccentSilver
import com.lagu.songfy.presentation.theme.GlassBorder
import com.lagu.songfy.presentation.theme.GlassSurface
import com.lagu.songfy.presentation.theme.TextPrimaryWhite
import com.lagu.songfy.presentation.theme.TextSecondaryGray

// ─────────────────────────────────────────────────────────
// Titik-3 sheet: Tambah antrean / Putar berikutnya / Bagikan / Sleep timer / Lihat antrean
// ─────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongOptionsSheet(
    song: SongDto?,
    sleepTimerMinutes: Int?,
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    onDownloadClick: () -> Unit = {},
    onDeleteOfflineClick: () -> Unit = {},
    onAddToQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onSetSleepTimer: (Int?) -> Unit,
    onOpenQueue: () -> Unit,
    onAddToPlaylist: () -> Unit = {},
    onArtistClick: ((String) -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var sleepExpanded by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Kaca-abu: GlassSurface + border GlassBorder (re-theme hitam-abu).
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GlassSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            // Header: cover + judul + artis
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = song?.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song?.title ?: "Now Playing",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimaryWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = song?.artists?.joinToString(", ") { it.name } ?: "Artist",
                        fontSize = 12.sp,
                        color = TextSecondaryGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup",
                        tint = TextSecondaryGray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // ── OPSI DOWNLOAD OFFLINE & AUTO KE PLAYLIST 'LAGU OFFLINE' ──
            if (isDownloading) {
                SheetRow(
                    icon = Icons.Default.Downloading,
                    title = "Mengunduh...",
                    subtitle = "Menyimpan ke playlist Lagu Offline",
                    iconTint = Color(0xFF60A5FA),
                    onClick = {}
                )
            } else if (isDownloaded) {
                SheetRow(
                    icon = Icons.Default.CheckCircle,
                    title = "Tersimpan Offline",
                    subtitle = "Ada di playlist Lagu Offline • Ketuk untuk hapus",
                    iconTint = Color(0xFF4ADE80),
                    trailing = "Offline",
                    onClick = {
                        onDeleteOfflineClick()
                        onDismiss()
                    }
                )
            } else {
                SheetRow(
                    icon = Icons.Default.Download,
                    title = "Download offline",
                    subtitle = "Simpan ke folder playlist Lagu Offline",
                    onClick = {
                        onDownloadClick()
                            onDismiss()
                    }
                )
            }

            val firstArtist = song?.artists?.firstOrNull()
            val artistIdOrName = firstArtist?.id?.takeIf { it.isNotBlank() } ?: firstArtist?.name.orEmpty()
            if (onArtistClick != null && artistIdOrName.isNotBlank()) {
                SheetRow(
                    icon = Icons.Default.Person,
                    title = "Lihat artis",
                    subtitle = firstArtist?.name ?: "Buka profil artis",
                    onClick = {
                        onArtistClick(artistIdOrName)
                        onDismiss()
                    }
                )
            }
            if (!song?.album.isNullOrBlank()) {
                SheetRow(
                    icon = Icons.Default.MusicNote,
                    title = "Lihat album",
                    subtitle = song.album,
                    onClick = onDismiss
                )
            }

            SheetRow(
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                title = "Tambahkan ke playlist",
                subtitle = "Simpan lagu ini ke koleksi playlist kamu",
                onClick = onAddToPlaylist
            )
            SheetRow(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                title = "Tambah ke antrean",
                subtitle = "Putar setelah antrean saat ini",
                onClick = onAddToQueue
            )
            SheetRow(
                icon = Icons.Default.SkipNext,
                title = "Putar berikutnya",
                subtitle = "Sisipkan tepat setelah lagu ini",
                onClick = onPlayNext
            )
            SheetRow(
                icon = Icons.Default.Share,
                title = "Bagikan",
                subtitle = "Kirim link lagu ke aplikasi lain",
                onClick = {
                    if (onShare != null) {
                        onShare()
                        onDismiss()
                    } else {
                        val s = song
                        if (s != null) {
                            val artist = s.artists.joinToString(", ") { it.name }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Dengarkan \"${s.title}\" - $artist di Songfy\n" +
                                        "https://music.youtube.com/watch?v=${s.videoId}"
                                )
                            }
                            context.startActivity(Intent.createChooser(intent, "Bagikan lagu"))
                        }
                        onDismiss()
                    }
                }
            )
            SheetRow(
                icon = Icons.Default.Timer,
                title = "Sleep timer",
                subtitle = if (sleepTimerMinutes != null) "Aktif • berhenti dalam $sleepTimerMinutes mnt"
                    else "Matikan musik otomatis",
                trailing = if (sleepTimerMinutes != null) "ON" else null,
                onClick = { sleepExpanded = !sleepExpanded }
            )
            if (sleepExpanded) {
                SleepTimerPicker(
                    selected = sleepTimerMinutes,
                    onSelect = {
                        onSetSleepTimer(it)
                        sleepExpanded = false
                    }
                )
            }
            SheetRow(
                icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                title = "Lihat antrean",
                subtitle = "Lagu yang akan diputar selanjutnya",
                onClick = onOpenQueue
            )
        }
    }
}

@Composable
private fun SheetRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailing: String? = null,
    iconTint: Color = TextPrimaryWhite,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SheetIconBubble(icon, iconTint)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimaryWhite
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                SheetSubtitle(subtitle)
            }
        }
        if (trailing != null) {
            Text(
                text = trailing,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(AccentSilver)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

// Ikon dalam lingkaran — dipisah biar SheetRow tetap ramping
@Composable
private fun SheetIconBubble(icon: ImageVector, iconTint: Color = TextPrimaryWhite) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SheetSubtitle(subtitle: String) {
    Text(
        text = subtitle,
        fontSize = 12.sp,
        color = TextSecondaryGray,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SleepTimerPicker(
    selected: Int?,
    onSelect: (Int?) -> Unit
) {
    val options = listOf(5, 10, 15, 30, 45, 60)
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { min ->
            val isSelected = selected == min
            Text(
                text = "$min mnt",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) Color.Black else TextPrimaryWhite,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (isSelected) AccentSilver else Color.White.copy(alpha = 0.10f)
                    )
                    .clickable { onSelect(min) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        Text(
            text = "Matikan",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondaryGray,
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f))
                .clickable { onSelect(null) }
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
    Spacer(Modifier.height(4.dp))
}

// ─────────────────────────────────────────────────────────
// Antrean sheet: sedang diputar + berikutnya (tap = putar, X = hapus)
// ─────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongQueueSheet(
    currentSong: SongDto?,
    currentIndex: Int,
    queue: List<SongDto>,
    onPlaySong: (SongDto) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val upcoming = remember(queue, currentIndex) {
        queue.drop(currentIndex + 1)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GlassSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Antrean",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryWhite
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (upcoming.isEmpty()) "Tidak ada lagu berikutnya"
                            else "${upcoming.size} lagu berikutnya",
                        fontSize = 12.sp,
                        color = TextSecondaryGray
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup",
                        tint = TextSecondaryGray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Sedang diputar
            val current = currentSong
            if (current != null) {
                Text(
                    text = "Sedang diputar",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondaryGray,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
                QueueRow(
                    song = current,
                    isCurrent = true,
                    onClick = {},
                    onRemove = null
                )
            }

            if (upcoming.isNotEmpty()) {
                Text(
                    text = "Berikutnya",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondaryGray,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
                LazyColumn(
                    // ModalBottomSheet = Column scope; cap tinggi biar sheet bisa collapse
                    // untuk antrean pendek tapi tetap scroll kalau panjang
                    modifier = Modifier.heightIn(max = 320.dp)
                ) {
                    itemsIndexed(upcoming, key = { index, song -> "${index}_${song.videoId}" }) { _, song ->
                        QueueRow(
                            song = song,
                            isCurrent = false,
                            onClick = { onPlaySong(song) },
                            onRemove = { onRemove(song.videoId) }
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = TextSecondaryGray.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Lagu habis — putar lagu lain atau tambah ke antrean",
                        fontSize = 13.sp,
                        color = TextSecondaryGray
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueRow(
    song: SongDto,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.07f))
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isCurrent) AccentSilver else TextPrimaryWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = song.artists.joinToString(", ") { it.name },
                fontSize = 12.sp,
                color = TextSecondaryGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isCurrent) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Sedang diputar",
                tint = AccentSilver,
                modifier = Modifier.size(20.dp)
            )
        } else {
            if (!song.duration.isNullOrBlank()) {
                Text(
                    text = song.duration.orEmpty(),
                    fontSize = 12.sp,
                    color = TextSecondaryGray
                )
                Spacer(Modifier.width(8.dp))
            }
            if (onRemove != null) {
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Hapus dari antrean",
                        tint = TextSecondaryGray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
