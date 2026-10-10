package com.lagu.songfy.presentation.artist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.lagu.songfy.data.remote.dto.SongDto
import com.lagu.songfy.presentation.common.MiniPlayerBar
import com.lagu.songfy.presentation.navigation.LiquidGlassBottomNavbar
import com.lagu.songfy.presentation.player.PlayerUiState
import com.lagu.songfy.presentation.theme.*

@Composable
fun ArtistScreen(
    artistIdOrName: String,
    modifier: Modifier = Modifier,
    viewModel: ArtistViewModel = hiltViewModel(),
    playerUiState: PlayerUiState? = null,
    playbackTick: Triple<Long, Long, Boolean>? = null,
    onSongClick: (SongDto, List<SongDto>) -> Unit = { _, _ -> },
    onBackClick: () -> Unit = {},
    onTogglePlayPause: () -> Unit = {},
    onNavigateToPlayer: () -> Unit = {},
    onTabSelected: (Int) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(artistIdOrName) {
        viewModel.loadArtist(artistIdOrName)
    }

    BackHandler {
        onBackClick()
    }

    val artist = uiState.artistDetail
    val songsToDisplay = remember(uiState.showAllSongs, artist) {
        if (artist == null) emptyList()
        else if (uiState.showAllSongs && artist.allSongs.isNotEmpty()) artist.allSongs
        else artist.topSongs
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundNearBlack)
    ) {
        if (uiState.isLoading && artist == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = AccentSilver,
                    modifier = Modifier.size(40.dp)
                )
            }
        } else if (artist != null) {
            // Scrollable LazyColumn containing all sections based on the reference layout
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 145.dp)
            ) {
                // â”€â”€ 1. ARTIST BANNER & HERO HEADER â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(330.dp)
                    ) {
                        // Banner background image
                        val bannerImage = artist.bannerUrl ?: artist.thumbnailUrl
                        if (!bannerImage.isNullOrBlank()) {
                            AsyncImage(
                                model = bannerImage,
                                    contentDescription = artist.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                        .fillMaxSize()
                                    .background(SurfaceDarkNavy)
                             )
                        }

                        // Gradient overlay (top vignette for status bar, bottom fade to dark)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0.0f to Color.Black.copy(alpha = 0.50f),
                                            0.40f to Color.Transparent,
                                            0.75f to BackgroundNearBlack.copy(alpha = 0.85f),
                                            1.0f to BackgroundNearBlack
                                        )
                                    )
                                )
                        )

                        // Circular Back button at top-left
                        Box(
                            modifier = Modifier
                                .statusBarsPadding()
                                .displayCutoutPadding()
                                .padding(start = 16.dp, top = 8.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable(onClick = onBackClick),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimaryWhite,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Artist Name & Verified Badge at bottom of banner
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = artist.name,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Verified",
                                    tint = AccentSilver,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Diverifikasi oleh Songfy",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimaryWhite.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                }

                // â”€â”€ 2. MONTHLY LISTENERS & SHORT BIO â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                item {
                    val listeners = artist.monthlyListeners ?: "2,2 jt pendengar bulanan"
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                        Text(
                            text = listeners,
                            fontSize = 13.sp,
                            color = TextSecondaryGray
                        )
                        if (!artist.description.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = artist.description.trim().take(90),
                                fontSize = 13.sp,
                                color = TextSecondaryGray,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // â”€â”€ 3. ACTION ROW: AVATAR, IKUTI, MORE, SHUFFLE, PLAY â”€â”€
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Mini artist avatar
                        AsyncImage(
                            model = artist.thumbnailUrl ?: artist.bannerUrl,
                            contentDescription = artist.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(SurfaceDarkNavy)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        // "Ikuti" / "Mengikuti" pill button
                        OutlinedButton(
                            onClick = { viewModel.toggleFollow() },
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, if (uiState.isFollowing) AccentSilver else Color(0xFF5A5A68)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (uiState.isFollowing) FollowActiveBg else Color.Transparent,
                                contentColor = if (uiState.isFollowing) AccentSilver else TextPrimaryWhite
                            ),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (uiState.isFollowing) "Mengikuti" else "Ikuti",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // NOTE 2026-09-12: tombol MoreVert no-op dihapus (lihat SongRow di bawah).

                        Spacer(modifier = Modifier.weight(1f))

                        // Shuffle button
                        IconButton(
                            onClick = {
                                if (songsToDisplay.isNotEmpty()) {
                                    val shuffled = songsToDisplay.shuffled()
                                    onSongClick(shuffled.first(), shuffled)
                                }
                            },
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                tint = TextSecondaryGray,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Big round Play button — putih solid + ikon hitam (re-theme).
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(PlayButtonWhite)
                                .clickable {
                                    if (songsToDisplay.isNotEmpty()) {
                                        onSongClick(songsToDisplay.first(), songsToDisplay)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play All",
                                tint = BackgroundNearBlack,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }

                // â”€â”€ 4. TABS (Musik, Klip) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.clickable { viewModel.selectTab("Musik") }
                        ) {
                            Text(
                                text = "Musik",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.activeTab == "Musik") TextPrimaryWhite else TextSecondaryGray
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .width(36.dp)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (uiState.activeTab == "Musik") AccentSilver else Color.Transparent)
                            )
                        }

                        Column(
                            modifier = Modifier.clickable { viewModel.selectTab("Klip") }
                        ) {
                            Text(
                                text = "Klip",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.activeTab == "Klip") TextPrimaryWhite else TextSecondaryGray
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .width(28.dp)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (uiState.activeTab == "Klip") AccentSilver else Color.Transparent)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // â”€â”€ 5. POPULAR SONGS SECTION â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                item {
                    Text(
                        text = "Populer",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryWhite,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }

                if (songsToDisplay.isNotEmpty()) {
                    // Key stabil TANPA index: index-suffixed key memaksa LazyColumn anggap
                    // semua baris baru tiap toggle showAll/shuffle = re-layout + re-fetch art.
                    itemsIndexed(songsToDisplay, key = { _, s -> s.videoId }) { index, song ->
                        ArtistSongRow(
                            rank = index + 1,
                            song = song,
                            onClick = { onSongClick(song, songsToDisplay) }
                        )
                    }

                    // "Lihat Semua" / Toggle button
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            TextButton(
                                onClick = { viewModel.toggleShowAllSongs() }
                            ) {
                                Text(
                                    text = if (uiState.showAllSongs) "Tampilkan lebih sedikit" else "Lihat semua",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextSecondaryGray
                                )
                            }
                        }
                    }
                } else {
                    item {
                        Text(
                            text = "Tidak ada lagu yang tersedia.",
                            fontSize = 14.sp,
                            color = TextSecondaryGray,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
                        )
                    }
                }
            }
        } else if (uiState.error != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = uiState.error ?: "Gagal memuat profil artis",
                        color = TextSecondaryGray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.loadArtist(artistIdOrName) },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentSilver)
                    ) {
                        Text(text = "Coba Lagi", color = BackgroundNearBlack)
                    }
                }
            }
        }

        // ── 9. BOTTOM NAVBAR (Mini player dihandle secara global oleh ExpandablePlayerSheet) ──
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 10.dp)
        ) {
            LiquidGlassBottomNavbar(
                selectedIndex = 0,
                onTabSelected = onTabSelected
            )
        }
    }
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// Explore Card Item Component
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
@Composable
private fun ExploreCardItem(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(125.dp)
            .height(185.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF28273A))
            .clickable(onClick = onClick)
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Dark gradient overlay for readable text at bottom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.50f to Color.Black.copy(alpha = 0.25f),
                            1.0f to Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        // Text at bottom
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimaryWhite,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
        )
    }
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// Credit Row Item Component
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
@Composable
private fun CreditRowItem(
    name: String,
    roles: String,
    isFollowing: Boolean,
    onFollowClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = roles,
                fontSize = 12.sp,
                color = TextSecondaryGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        OutlinedButton(
            onClick = onFollowClick,
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, if (isFollowing) AccentSilver else Color(0xFF5A5A68)),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (isFollowing) FollowActiveBg else Color(0x33FFFFFF),
                contentColor = if (isFollowing) AccentSilver else TextPrimaryWhite
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (isFollowing) "Mengikuti" else "Ikuti",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// Artist Song Row Component
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
@Composable
private fun ArtistSongRow(
    rank: Int,
    song: SongDto,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SongItemCardBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rank number
        Text(
            text = "$rank",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondaryGray,
            modifier = Modifier.width(28.dp)
        )

        // Song Thumbnail
        AsyncImage(
            model = song.thumbnailUrl,
            contentDescription = song.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceDarkNavy)
        )

        Spacer(modifier = Modifier.width(14.dp))

        // Title and artist
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = song.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimaryWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            val subtitle = if (!song.playCount.isNullOrBlank()) {
                song.playCount
            } else if (!song.duration.isNullOrBlank()) {
                song.duration
            } else {
                song.artists.joinToString(", ") { it.name }
            }
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = TextSecondaryGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // NOTE 2026-09-12: tombol MoreVert no-op dihapus (1 IconButton + vector per
        // baris lagu). Kembalikan saat menu opsi lagu beneran di-wire ke sheet.
    }
}
