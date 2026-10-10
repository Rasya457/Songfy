package com.lagu.songfy.presentation.playlist

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.lagu.songfy.data.remote.dto.SongDto
import com.lagu.songfy.data.remote.dto.toHighResThumbnail
import com.lagu.songfy.presentation.player.PlayerViewModel
import com.lagu.songfy.presentation.theme.*
import com.lagu.songfy.util.DynamicColorExtractor
import com.lagu.songfy.util.rememberAppleMusicPalette
import com.lagu.songfy.util.rememberMiniPlayerColors
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    fallbackTitle: String = "Playlist",
    fallbackArtwork: String? = null,
    currentPlayingVideoId: String? = null,
    onBackClick: () -> Unit = {},
    onSongClick: (SongDto, List<SongDto>) -> Unit = { _, _ -> },
    onArtistClick: (String) -> Unit = {},
    onNavigateToPlayer: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val recommendedSongs by viewModel.recommendedSongs.collectAsStateWithLifecycle()
    val playerUiState by playerViewModel.uiState.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val hazeState = remember { HazeState() }

    LaunchedEffect(playlistId) {
        viewModel.loadPlaylist(playlistId, fallbackTitle, fallbackArtwork)
    }

    // Determine cover & titles
    val titleToDisplay = playlist?.title?.ifBlank { fallbackTitle } ?: fallbackTitle
    val rawArtwork = playlist?.artworkUrl ?: fallbackArtwork
    val artworkToDisplay = rawArtwork.toHighResThumbnail() ?: "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=1200&q=80"

    // Extract dynamic Apple Music palette (darkened page background ~70% darker + vibrant accent color)
    val palette = rememberAppleMusicPalette(
        imageUrl = artworkToDisplay,
        defaultBg = Color(0xFF121212),
        defaultAccent = Color(0xFF64D2FF)
    )

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    var showAddSongSheet by remember { mutableStateOf(false) }

    // Exclusion check: Like song and Lagu offline should not show recommended songs
    val isLikeSongPlaylist = remember(titleToDisplay, playlistId) {
        val t = titleToDisplay.trim().lowercase()
        val id = playlistId.trim().lowercase()
        t == "like song" || t == "liked songs" || t == "lagu yang disukai" || t.contains("like song") || id.contains("like_song") || id.contains("liked")
    }

    val isOfflinePlaylist = remember(titleToDisplay, playlistId) {
        val t = titleToDisplay.trim().lowercase()
        val id = playlistId.trim().lowercase()
        t == "lagu offline" || t == "offline" || t.contains("lagu offline") || t.contains("offline") || id.contains("offline")
    }

    val isExcludedFromRecommendations = remember(isLikeSongPlaylist, isOfflinePlaylist) {
        isLikeSongPlaylist || isOfflinePlaylist
    }

    // Check if this playlist is a user custom playlist
    val isUserCustomPlaylist = playlistId.startsWith("pl_") ||
            playlist?.description == "Playlist dibuat oleh kamu" ||
            (playlist != null && playlistId.contains("-"))

    // Songs data: use playlist tracks if populated, fallback to curated recommended songs for system/backend playlists
    val rawPlaylistSongs = playlist?.songs ?: emptyList()
    val displayedSongs = remember(rawPlaylistSongs, recommendedSongs, isUserCustomPlaylist, isExcludedFromRecommendations) {
        if (rawPlaylistSongs.isNotEmpty()) {
            rawPlaylistSongs
        } else if (isUserCustomPlaylist || isExcludedFromRecommendations) {
            emptyList()
        } else {
            recommendedSongs
        }
    }

    // Primary artist
    val primaryArtist = remember(displayedSongs) {
        displayedSongs.firstOrNull()?.artists?.firstOrNull()?.name ?: "SZA"
    }

    // Metadata line (Genre, Year, Badges)
    val metadataLine = remember(displayedSongs) {
        val year = "2022"
        val genre = "R&B/Soul"
        "$genre · $year · Dolby Atmos · Lossless"
    }

    // Description text (Real or editorial quote matching Apple Music screenshot)
    var isDescriptionExpanded by remember { mutableStateOf(false) }
    val descriptionText = remember(playlist?.description, primaryArtist, titleToDisplay) {
        val rawDesc = playlist?.description
        if (!rawDesc.isNullOrBlank() && rawDesc != "Playlist dibuat oleh kamu") {
            rawDesc
        } else {
            "100 Best Albums in 2022. $primaryArtist's 23-track project confronts life with songs about love, sex, self-doubt, and heartbreak."
        }
    }

    // Saved to library state
    var isSavedToLibrary by remember { mutableStateOf(false) }

    // Bottom sheets
    var showMoreMenuSheet by remember { mutableStateOf(false) }
    var selectedTrackForSheet by remember { mutableStateOf<SongDto?>(null) }

    // Screen height calculation for hero cover
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val heroHeight = screenHeight * 0.55f
    val density = LocalDensity.current

    // Scroll calculations for parallax and sticky header
    val scrollOffset by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0) {
                listState.firstVisibleItemScrollOffset.toFloat()
            } else {
                1500f + listState.firstVisibleItemScrollOffset.toFloat()
            }
        }
    }

    val headerAlpha by remember {
        derivedStateOf {
            val heroPx = with(density) { heroHeight.toPx() }
            ((scrollOffset - heroPx * 0.45f) / (heroPx * 0.25f)).coerceIn(0f, 1f)
        }
    }

    // Current playing state sync
    val isCurrentlyPlayingFromPlaylist = remember(playerUiState.isPlaying, playerUiState.currentSong, displayedSongs) {
        playerUiState.isPlaying && displayedSongs.any { it.videoId == playerUiState.currentSong?.videoId }
    }

    BackHandler {
        onBackClick()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundNearBlack)
    ) {
        // Main Scrollable Body
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .haze(hazeState),
            contentPadding = PaddingValues(bottom = 130.dp) // Clears floating Apple Music mini player
        ) {
            // ─────────────────────────────────────────────────────────────
            // 1. FULL-BLEED HERO COVER
            // ─────────────────────────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(heroHeight)
                        .graphicsLayer {
                            // Parallax: translate at 0.5x speed
                            translationY = scrollOffset * 0.5f
                            // Overscroll stretch on pull down
                            val overscroll = 1f + (maxOf(-scrollOffset, 0f) / 1000f)
                            scaleX = overscroll
                            scaleY = overscroll
                        }
                ) {
                    // Full-bleed edge-to-edge cover image
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(artworkToDisplay)
                            .crossfade(true)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .listener(
                                onSuccess = { _, result ->
                                    val bmp = (result as? SuccessResult)?.drawable?.intrinsicWidth ?: 0
                                    if (bmp in 1..1079) {
                                        Log.w("PlaylistDetail", "Cover image is under 1080px wide: ${bmp}px")
                                    }
                                }
                            )
                            .build(),
                        contentDescription = titleToDisplay,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Vertical Gradient Overlay: Fades seamless from transparent to page background
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.Transparent,
                                        0.30f to Color.Transparent,
                                        0.60f to BackgroundNearBlack.copy(alpha = 0.45f),
                                        0.82f to BackgroundNearBlack.copy(alpha = 0.88f),
                                        1.0f to BackgroundNearBlack
                                    )
                                )
                            )
                    )
                }
            }

            // ─────────────────────────────────────────────────────────────
            // 2. CENTERED TITLE BLOCK (Overlapping bottom of hero)
            // ─────────────────────────────────────────────────────────────
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .offset(y = (-16).dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Album / Playlist Title
                    Text(
                        text = titleToDisplay,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryWhite,
                        letterSpacing = (-0.5).sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Artist Name (Clickable with accent color)
                    Text(
                        text = primaryArtist,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.accentColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onArtistClick(primaryArtist) }
                            )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Metadata Line: Genre • Year • Dolby Atmos • Lossless
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "R&B/Soul · 2022",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color.White.copy(alpha = 0.55f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "·",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.55f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))

                        // Dolby Atmos Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.12f))
                                .border(0.5.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Dolby Atmos",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Lossless Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.12f))
                                .border(0.5.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Lossless",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            // ─────────────────────────────────────────────────────────────
            // 3. ACTION ROW (Centered: Shuffle, Play, Plus)
            // ─────────────────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle Button (44dp circular translucent)
                    val isShuffle = playerUiState.isShuffle
                    val shuffleBg = if (isShuffle) palette.accentColor.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.14f)
                    val shuffleTint = if (isShuffle) palette.accentColor else Color.White

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(shuffleBg)
                            .border(0.5.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                            .clickable {
                                playerViewModel.toggleShuffle()
                                Toast.makeText(
                                    context,
                                    if (!isShuffle) "Acak: Aktif" else "Acak: Nonaktif",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = shuffleTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Play / Pause White Pill Button (CircleShape, 44dp height)
                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .widthIn(min = 148.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable {
                                if (isCurrentlyPlayingFromPlaylist) {
                                    playerViewModel.togglePlayPause()
                                } else {
                                    val firstSong = displayedSongs.firstOrNull()
                                    if (firstSong != null) {
                                        onSongClick(firstSong, displayedSongs)
                                    }
                                }
                            }
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isCurrentlyPlayingFromPlaylist) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isCurrentlyPlayingFromPlaylist) "Pause" else "Play",
                                tint = Color.Black,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isCurrentlyPlayingFromPlaylist) "Pause" else "Play",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }

                    // Plus / Add Song Button (44dp circular translucent)
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                            .clickable {
                                showAddSongSheet = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Tambah Lagu",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // ─────────────────────────────────────────────────────────────
            // 4. EDITORIAL DESCRIPTION (2 lines with bold "MORE")
            // ─────────────────────────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .animateContentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { isDescriptionExpanded = !isDescriptionExpanded }
                        )
                ) {
                    Text(
                        text = if (!isDescriptionExpanded) {
                            "$descriptionText "
                        } else {
                            "$descriptionText (Ketuk untuk ciutkan)"
                        },
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!isDescriptionExpanded) {
                        Text(
                            text = "MORE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimaryWhite,
                            modifier = Modifier.align(Alignment.BottomEnd)
                        )
                    }
                }
            }

            // ── ACTION ROW: + Tambah Lagu ke Playlist ──────────────────
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showAddSongSheet = true },
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(palette.accentColor.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Tambah lagu",
                                tint = palette.accentColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Tambah lagu ke playlist ini",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimaryWhite
                            )
                            Text(
                                text = if (isExcludedFromRecommendations) "Cari lagu untuk ditambahkan" else "Cari lagu favorit atau pilih dari rekomendasi",
                                fontSize = 12.sp,
                                color = TextSecondaryGray
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Empty state if playlist has no songs
            if (rawPlaylistSongs.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp, horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Playlist ini masih kosong",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryWhite
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isOfflinePlaylist) {
                                "Unduh lagu untuk menyimpannya di sini dan dengarkan tanpa internet"
                            } else if (isLikeSongPlaylist) {
                                "Ketuk ikon hati pada lagu untuk menambahkannya ke playlist ini"
                            } else {
                                "Gunakan tombol + di atas atau pilih rekomendasi di bawah untuk mulai mengisi lagu"
                            },
                            fontSize = 13.sp,
                            color = TextSecondaryGray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // ─────────────────────────────────────────────────────────────
            // 5. TRACK LIST
            // ─────────────────────────────────────────────────────────────
            itemsIndexed(
                items = displayedSongs,
                key = { index, song -> "am_track_${song.videoId}_$index" }
            ) { index, song ->
                val isTrackPlaying = playerUiState.currentSong?.videoId == song.videoId
                val isExplicit = index % 2 == 0 || song.title.contains("explicit", ignoreCase = true)

                AppleMusicTrackRow(
                    index = index + 1,
                    song = song,
                    isPlaying = isTrackPlaying,
                    accentColor = palette.accentColor,
                    isExplicit = isExplicit,
                    onClick = {
                        onSongClick(song, displayedSongs)
                    },
                    onMoreClick = {
                        selectedTrackForSheet = song
                    }
                )
            }

            // ── Section: Lagu yang Disarankan (dengan tombol + langsung) ──
            if (recommendedSongs.isNotEmpty() && !isExcludedFromRecommendations) {
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Lagu yang Disarankan",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryWhite
                            )
                            Text(
                                text = "Ketuk icon + untuk menambahkan langsung",
                                fontSize = 12.sp,
                                color = TextSecondaryGray
                            )
                        }
                        IconButton(onClick = { viewModel.loadRecommendations() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Muat Ulang Rekomendasi",
                                tint = palette.accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                items(recommendedSongs.take(10), key = { "rec_${it.videoId}" }) { song ->
                    val isAlreadyIn = rawPlaylistSongs.any { it.videoId == song.videoId }
                    RecommendedTrackRow(
                        song = song,
                        isAdded = isAlreadyIn,
                        accentColor = palette.accentColor,
                        onAddClick = {
                            viewModel.addSong(song)
                            Toast.makeText(context, "'${song.title}' ditambahkan ke playlist", Toast.LENGTH_SHORT).show()
                        },
                        onPlayClick = {
                            onSongClick(song, recommendedSongs)
                        }
                    )

                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.06f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 74.dp, end = 20.dp)
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }

        // ─────────────────────────────────────────────────────────────
        // 6. STICKY TOP BAR (Overlaid with translucent circular buttons)
        // ─────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(BackgroundNearBlack.copy(alpha = headerAlpha * 0.96f))
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Left: Back button (40dp circular translucent)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.CenterStart)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                    .clickable { onBackClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimaryWhite,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Center: Title fading in after scrolling past hero
            Text(
                text = titleToDisplay,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryWhite.copy(alpha = headerAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 90.dp)
            )

            // Right: Add Song, Share & More Buttons
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Add Song Button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                        .clickable { showAddSongSheet = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Tambah Lagu",
                        tint = TextPrimaryWhite,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Share Button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                        .clickable {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, titleToDisplay)
                                putExtra(Intent.EXTRA_TEXT, "Dengarkan playlist '$titleToDisplay' oleh $primaryArtist di Songfy!")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Bagikan Playlist"))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = TextPrimaryWhite,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // More Options Button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                        .clickable { showMoreMenuSheet = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "More",
                        tint = TextPrimaryWhite,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // ─────────────────────────────────────────────────────────────
        // 7. FLOATING MINI PLAYER (Apple Music Style – Grid + Pill + Search)
        // ─────────────────────────────────────────────────────────────
        val currentSong = playerUiState.currentSong
        if (currentSong != null) {
            AppleMusicFloatingMiniPlayer(
                currentSong = currentSong,
                isPlaying = playerUiState.isPlaying,
                onTogglePlayPause = { playerViewModel.togglePlayPause() },
                onNavigateToPlayer = onNavigateToPlayer,
                onNavigateToLibrary = onBackClick,
                onNavigateToSearch = onNavigateToSearch,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }

        // ─────────────────────────────────────────────────────────────
        // 8. MODAL BOTTOM SHEETS
        // ─────────────────────────────────────────────────────────────
        if (showMoreMenuSheet) {
            ModalBottomSheet(
                onDismissRequest = { showMoreMenuSheet = false },
                containerColor = SurfaceCard,
                contentColor = TextPrimaryWhite
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = titleToDisplay,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryWhite
                    )
                    Text(
                        text = "$primaryArtist · ${displayedSongs.size} lagu",
                        fontSize = 13.sp,
                        color = TextSecondaryGray
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    BottomSheetActionItem(
                        icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                        title = "Tambah ke Playlist Lain",
                        onClick = {
                            showMoreMenuSheet = false
                            Toast.makeText(context, "Fitur ditambahkan!", Toast.LENGTH_SHORT).show()
                        }
                    )

                    BottomSheetActionItem(
                        icon = Icons.Default.Person,
                        title = "Buka Profil Artis ($primaryArtist)",
                        onClick = {
                            showMoreMenuSheet = false
                            onArtistClick(primaryArtist)
                        }
                    )

                    BottomSheetActionItem(
                        icon = Icons.Default.Download,
                        title = "Download Semua Lagu",
                        onClick = {
                            showMoreMenuSheet = false
                            Toast.makeText(context, "Mulai mengunduh playlist...", Toast.LENGTH_SHORT).show()
                        }
                    )

                    BottomSheetActionItem(
                        icon = Icons.Default.Share,
                        title = "Bagikan Playlist",
                        onClick = {
                            showMoreMenuSheet = false
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Dengarkan '$titleToDisplay' di Songfy!")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Bagikan"))
                        }
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        selectedTrackForSheet?.let { song ->
            ModalBottomSheet(
                onDismissRequest = { selectedTrackForSheet = null },
                containerColor = SurfaceCard,
                contentColor = TextPrimaryWhite
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = song.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artists.firstOrNull()?.name ?: primaryArtist,
                        fontSize = 13.sp,
                        color = TextSecondaryGray
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    BottomSheetActionItem(
                        icon = Icons.AutoMirrored.Filled.QueueMusic,
                        title = "Putar Berikutnya",
                        onClick = {
                            playerViewModel.playNext(song)
                            selectedTrackForSheet = null
                            Toast.makeText(context, "Akan diputar berikutnya", Toast.LENGTH_SHORT).show()
                        }
                    )

                    BottomSheetActionItem(
                        icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                        title = "Tambah ke Antrean",
                        onClick = {
                            playerViewModel.addToQueue(song)
                            selectedTrackForSheet = null
                            Toast.makeText(context, "Ditambahkan ke antrean", Toast.LENGTH_SHORT).show()
                        }
                    )

                    BottomSheetActionItem(
                        icon = Icons.Default.Favorite,
                        title = "Sukai Lagu",
                        onClick = {
                            playerViewModel.toggleLike()
                            selectedTrackForSheet = null
                        }
                    )

                    BottomSheetActionItem(
                        icon = Icons.Default.Person,
                        title = "Buka Artis",
                        onClick = {
                            selectedTrackForSheet = null
                            onArtistClick(song.artists.firstOrNull()?.name ?: primaryArtist)
                        }
                    )

                    BottomSheetActionItem(
                        icon = Icons.Default.Share,
                        title = "Bagikan Lagu",
                        onClick = {
                            selectedTrackForSheet = null
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Dengarkan '${song.title}' oleh ${song.artists.firstOrNull()?.name ?: primaryArtist} di Songfy!")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Bagikan Lagu"))
                        }
                    )

                    if (rawPlaylistSongs.any { it.videoId == song.videoId }) {
                        BottomSheetActionItem(
                            icon = Icons.Default.Delete,
                            title = "Hapus dari Playlist Ini",
                            onClick = {
                                viewModel.removeSong(song.videoId)
                                selectedTrackForSheet = null
                                Toast.makeText(context, "'${song.title}' dihapus dari playlist", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // ─────────────────────────────────────────────────────────────
        // 9. MODAL BOTTOM SHEET: TAMBAH LAGU KE PLAYLIST (+)
        // ─────────────────────────────────────────────────────────────
        if (showAddSongSheet) {
            val addSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = {
                    showAddSongSheet = false
                    viewModel.onSearchQueryChanged("")
                },
                sheetState = addSheetState,
                containerColor = SurfaceDarkNavy,
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(top = 10.dp, bottom = 4.dp)
                            .size(width = 38.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                    )
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 18.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Tambah Lagu ke Playlist",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryWhite,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Search input with debounce
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        placeholder = { Text("Cari judul lagu atau nama artis...", color = TextSecondaryGray, fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Cari",
                                tint = TextSecondaryGray
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Hapus",
                                        tint = TextSecondaryGray
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimaryWhite,
                            unfocusedTextColor = TextPrimaryWhite,
                            focusedBorderColor = palette.accentColor,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            cursorColor = palette.accentColor
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    val listToShow = if (searchQuery.isNotBlank()) {
                        searchResults
                    } else if (isExcludedFromRecommendations) {
                        emptyList()
                    } else {
                        recommendedSongs
                    }
                    val subtitle = if (searchQuery.isNotBlank()) {
                        if (isSearching) "Mencari lagu..." else "Hasil pencarian (${listToShow.size})"
                    } else if (isExcludedFromRecommendations) {
                        "Ketik judul atau nama artis untuk mencari"
                    } else {
                        "Rekomendasi lagu untukmu"
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = subtitle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = palette.accentColor,
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 440.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (listToShow.isEmpty() && !isSearching) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 36.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (searchQuery.isNotBlank()) {
                                            "Tidak ada hasil untuk \"$searchQuery\""
                                        } else if (isExcludedFromRecommendations) {
                                            "Cari lagu yang ingin kamu tambahkan"
                                        } else {
                                            "Tidak ada lagu yang disarankan"
                                        },
                                        color = TextSecondaryGray,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }

                        items(listToShow, key = { "sheet_${it.videoId}" }) { song ->
                            val isAdded = rawPlaylistSongs.any { it.videoId == song.videoId }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (!isAdded) {
                                            viewModel.addSong(song)
                                            Toast.makeText(context, "'${song.title}' ditambahkan ke playlist", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = song.thumbnailUrl,
                                    contentDescription = song.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimaryWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = song.artists.firstOrNull()?.name ?: "Unknown Artist",
                                        fontSize = 12.sp,
                                        color = TextSecondaryGray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                if (isAdded) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF1DB954).copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Sudah ditambahkan",
                                            tint = Color(0xFF1DB954),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                } else {
                                    IconButton(
                                        onClick = {
                                            viewModel.addSong(song)
                                            Toast.makeText(context, "'${song.title}' ditambahkan ke playlist", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(palette.accentColor.copy(alpha = 0.2f))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Tambah lagu",
                                            tint = palette.accentColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPOSABLE: RECOMMENDED TRACK ROW (WITH DIRECT + BUTTON)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RecommendedTrackRow(
    song: SongDto,
    isAdded: Boolean,
    accentColor: Color,
    onAddClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlayClick)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.thumbnailUrl,
            contentDescription = song.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimaryWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artists.firstOrNull()?.name ?: "Unknown Artist",
                fontSize = 12.sp,
                color = TextSecondaryGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (isAdded) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1DB954).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Sudah ditambahkan",
                    tint = Color(0xFF1DB954),
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            IconButton(
                onClick = onAddClick,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.2f))
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Tambah lagu",
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPOSABLE: TRACK ROW WITH EQUALIZER & EXPLICIT BADGE
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AppleMusicTrackRow(
    index: Int,
    song: SongDto,
    isPlaying: Boolean,
    accentColor: Color,
    isExplicit: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
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
        // Track Index Number OR Animated Equalizer
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (isPlaying) {
                AnimatedEqualizerBars(accentColor = accentColor)
            } else {
                Text(
                    text = "$index",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = TextSecondaryGray
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Title and Explicit badge
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = song.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = if (isPlaying) accentColor else TextPrimaryWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )

            if (isExplicit) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                        .size(15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "E",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // Three-dot Options Menu
        IconButton(
            onClick = onMoreClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreHoriz,
                contentDescription = "Options",
                tint = Color.White.copy(alpha = 0.60f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPOSABLE: ANIMATED EQUALIZER BARS (CANVAS + INFINITE TRANSITION)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AnimatedEqualizerBars(accentColor: Color) {
    val transition = rememberInfiniteTransition(label = "equalizer")

    val bar1Height by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )

    val bar2Height by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.20f,
        animationSpec = infiniteRepeatable(
            animation = tween(330, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )

    val bar3Height by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(480, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )

    Canvas(modifier = Modifier.size(15.dp)) {
        val totalWidth = size.width
        val totalHeight = size.height
        val barWidth = 3.dp.toPx()
        val spacing = 2.dp.toPx()

        val h1 = totalHeight * bar1Height
        val h2 = totalHeight * bar2Height
        val h3 = totalHeight * bar3Height

        // Bar 1
        drawRoundRect(
            color = accentColor,
            topLeft = Offset(0f, totalHeight - h1),
            size = Size(barWidth, h1),
            cornerRadius = CornerRadius(barWidth / 2)
        )

        // Bar 2
        drawRoundRect(
            color = accentColor,
            topLeft = Offset(barWidth + spacing, totalHeight - h2),
            size = Size(barWidth, h2),
            cornerRadius = CornerRadius(barWidth / 2)
        )

        // Bar 3
        drawRoundRect(
            color = accentColor,
            topLeft = Offset((barWidth + spacing) * 2, totalHeight - h3),
            size = Size(barWidth, h3),
            cornerRadius = CornerRadius(barWidth / 2)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPOSABLE: FLOATING MINI PLAYER (APPLE MUSIC STYLE)
// Left: grid/library circle | Center: pill (art + title + play) | Right: search circle
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AppleMusicFloatingMiniPlayer(
    currentSong: SongDto,
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dynamicColors = rememberMiniPlayerColors(currentSong.thumbnailUrl)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── Left: Library/Grid Circle ──────────────────────────────────────
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xCC1C1C1E))
                .border(0.5.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                .clickable { onNavigateToLibrary() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.GridView,
                contentDescription = "Library",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        // ── Center: Pill Capsule ───────────────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(30.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            dynamicColors.topColor.copy(alpha = 0.92f),
                            dynamicColors.bottomColor.copy(alpha = 0.92f)
                        )
                    )
                )
                .border(
                    0.5.dp,
                    dynamicColors.accentColor.copy(alpha = 0.35f),
                    RoundedCornerShape(30.dp)
                )
                .clickable { onNavigateToPlayer() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 6.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album art thumbnail
                AsyncImage(
                    model = currentSong.thumbnailUrl,
                    contentDescription = currentSong.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(6.dp))
                )

                Spacer(modifier = Modifier.width(10.dp))

                // Title + Artist
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = currentSong.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentSong.artists.firstOrNull()?.name ?: "Unknown Artist",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color.White.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Play / Pause button
                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // Thin progress line at bottom of pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.BottomCenter)
                    .background(Color.White.copy(alpha = 0.15f))
            )
        }

        // ── Right: Search Circle ───────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xCC1C1C1E))
                .border(0.5.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                .clickable { onNavigateToSearch() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// COMPOSABLE: BOTTOM SHEET ACTION ITEM
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BottomSheetActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = TextPrimaryWhite,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimaryWhite
        )
    }
}
