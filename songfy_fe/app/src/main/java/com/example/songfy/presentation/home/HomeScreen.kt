package com.lagu.songfy.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lagu.songfy.data.remote.dto.SongDto
import com.lagu.songfy.data.remote.dto.highResThumbnailUrl
import com.lagu.songfy.util.collectSelector

// ── Color Tokens matching Spotify Reference Image ─────────────────────────────
private val SpotifyDarkBackground = Color(0xFF121212)
private val SpotifyCardBackground = Color(0xFF282828)
private val SongfyBlue = Color(0xFF0A84FF)
private val SpotifyTextPrimary = Color(0xFFFFFFFF)
private val SpotifyTextSecondary = Color(0xFFA7A7A7)

private val DailyMixColors = listOf(
    Color(0xFFF9D949), // Yellow
    Color(0xFFFF7A00), // Orange
    Color(0xFF00D2C4), // Teal
    Color(0xFF1ED760), // Green
    Color(0xFF8B5CF6), // Purple
    Color(0xFFEC4899)  // Pink
)

data class PopularArtistItem(
    val name: String,
    val imageUrl: String
)

data class NostalgiaItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String,
    val badgeColor: Color = Color(0xFFF9D949)
)

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    onSongClick: (SongDto) -> Unit = {},
    onPlaylistClick: (PlaylistCardItem) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onLibraryClick: () -> Unit = {},
    onProfileClick: () -> Unit = {}
) {
    val headerName by viewModel.uiState.collectSelector(selector = { it.userName })
    val headerAvatar by viewModel.uiState.collectSelector(selector = { it.userAvatarUrl })
    val category by viewModel.uiState.collectSelector(selector = { it.selectedCategory })
    val playlists by viewModel.uiState.collectSelector(selector = { it.topPlaylists })
    val daily by viewModel.uiState.collectSelector(selector = { it.dailySongs })
    val recentlyPlayed by viewModel.uiState.collectSelector(selector = { it.recentlyPlayedSongs })
    val recommended by viewModel.uiState.collectSelector(selector = { it.recommendedSongs })
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        keyboardController?.hide()
    }

    // Combine daily and fallback songs to ensure 8 quick grid items are always present
    val quickSongs = remember(daily) {
        val combined = (daily + HomeViewModel.staticFallbackSongs()).distinctBy { it.videoId }
        combined.take(8)
    }

    // Popular songs list for Section 2
    val popularSongs = remember(daily, recommended) {
        val list = if (daily.size > 8) daily.drop(8) else daily
        if (list.isNotEmpty()) list else HomeViewModel.staticFallbackSongs()
    }

    // Curated high-res popular artists matching user screenshot (Raim Laode, Tulus, etc.)
    val popularArtists = remember {
        listOf(
            PopularArtistItem(
                name = "Raim Laode",
                imageUrl = "https://lh3.googleusercontent.com/WP-k6kFk2XdH_l41tw8zWFNe9M-TVTnmfPCuqHJLj5apoMbsfUz6-UR9ICwspp2chE6_ra2ZB-nccA=w500-h500-p-l90-rj"
            ),
            PopularArtistItem(
                name = "Tulus",
                imageUrl = "https://yt3.googleusercontent.com/h8P1jEIZLM8lkMxNA6Nbq98b43wcqllJSNmcZTCRPAB-F6rG_0Nqw5w7fwou0PN1QGwSW5viwWD5NV0=w500-h500-p-l90-rj"
            ),
            PopularArtistItem(
                name = "Bernadya",
                imageUrl = "https://lh3.googleusercontent.com/hxROE1fvLWSxUYAFV3IgMp5vvjN91Jx6oS6uwSVyYN9_fb3I6PLaRa3Ufb4A0awxq4J5UoPFQAQM-Q=w500-h500-p-l90-rj"
            ),
            PopularArtistItem(
                name = "Coldplay",
                imageUrl = "https://lh3.googleusercontent.com/IOKuXtp8PCQ_Fc-vaRKm3sKIXBxFV51gZheLTH5br-YGnWHFQf_Jywcuk7wbprYRoEbQyS_XZY6-nMJX=w500-h500-p-l90-rj"
            ),
            PopularArtistItem(
                name = "Sal Priadi",
                imageUrl = "https://lh3.googleusercontent.com/wmItRT4hTCJrmnlsh_JBgOeBXww9mquXhrNR0oW3_hPW9LsZ5Z2grMij01EaENdt6ensOJfKm-OCBKqJ=w500-h500-p-l90-rj"
            ),
            PopularArtistItem(
                name = "Sheila On 7",
                imageUrl = "https://lh3.googleusercontent.com/ZBI5XEhdUvkE59Kr7HKZYIjoopuA_kC0uPAWNsEw-EmLfTtRaXuDEv-qYWTv_lahkpA8Yuhe7P-sO2Y=w500-h500-p-l90-rj"
            ),
            PopularArtistItem(
                name = "Hindia",
                imageUrl = "https://yt3.googleusercontent.com/8ImMAMQSD4FA6-gdqCZWSFaB-drHvkdfiFcFAk7Mcyy56ctfWD-Xxno-CHfGC4L6Ql8aR61XT0vX0F4b=w500-h500-p-l90-rj"
            ),
            PopularArtistItem(
                name = "Billie Eilish",
                imageUrl = "https://lh3.googleusercontent.com/tQC4rOL6xz6FhmFr0ggQExxyGbYSOsyveXVSnPBh2WjEyIzQ9pMHablLJ-0GlMBrLBlBrbWQGmzrV6KN=w500-h500-p-l90-rj"
            )
        )
    }

    // Nostalgia playlists/albums matching user screenshot
    val nostalgiaItems = remember {
        listOf(
            NostalgiaItem(
                id = "nos_1",
                title = "Pop Rock 2000-an",
                subtitle = "Avril Lavigne, Linkin Park, Coldplay, Paramore, Green Day",
                artworkUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&q=80",
                badgeColor = Color(0xFFF9D949) // Yellow badge like Avril Lavigne card
            ),
            NostalgiaItem(
                id = "nos_2",
                title = "Lagu Cinta 2000-an",
                subtitle = "Bruno Mars, Coldplay, Maroon 5, Alicia Keys, Backstreet Boys",
                artworkUrl = "https://images.unsplash.com/photo-1518895949257-7621c3c786d7?w=500&q=80",
                badgeColor = Color(0xFFEF4444) // Red badge like reference screenshot
            ),
            NostalgiaItem(
                id = "nos_3",
                title = "Hits 2000-an Indonesia",
                subtitle = "Peterpan, Dewa 19, Sheila On 7, Padi, Ada Band, Ungu",
                artworkUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=500&q=80",
                badgeColor = Color(0xFF1ED760)
            ),
            NostalgiaItem(
                id = "nos_4",
                title = "Nostalgia 90-an",
                subtitle = "Westlife, Boyzone, Spice Girls, MLTR, Britney Spears",
                artworkUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=500&q=80",
                badgeColor = Color(0xFFF97316)
            ),
            NostalgiaItem(
                id = "nos_5",
                title = "Akustik Nostalgia Santai",
                subtitle = "Chrisye, Ebiet G. Ade, Iwan Fals, KLA Project",
                artworkUrl = "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=500&q=80",
                badgeColor = Color(0xFF8B5CF6)
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SpotifyDarkBackground)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                top = 10.dp,
                bottom = 140.dp // Clear floating mini player and bottom nav bar
            )
        ) {
            // ── 1. TOP SECTION (Avatar + Horizontal Filter Chips) ─────────────
            item {
                HomeTopBar(
                    avatarUrl = headerAvatar,
                    selectedCategory = category,
                    onCategorySelected = { viewModel.onCategorySelected(it) },
                    onProfileClick = onProfileClick,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── 2. QUICK MUSIC / ARTIST GRID (2 Columns × 4 Rows) ────────────
            item {
                QuickMusicGrid(
                    songs = quickSongs,
                    onSongClick = onSongClick,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // ── 3. FIRST CONTENT SECTION ("Lagu populer" - Popular Playlists) ─────
            item {
                Text(
                    text = "Lagu populer",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = SpotifyTextPrimary,
                    letterSpacing = (-0.4).sp,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 14.dp)
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (playlists.isNotEmpty()) {
                        itemsIndexed(playlists, key = { index, pl -> "pop_pl_${index}_${pl.id}" }) { index, playlist ->
                            DailyMixCard(
                                title = playlist.title,
                                subtitle = playlist.description,
                                artworkUrl = playlist.artworkUrl,
                                mixNumber = index + 1,
                                badgeColor = DailyMixColors[index % DailyMixColors.size],
                                badgeLabel = "Playlist",
                                onClick = { onPlaylistClick(playlist) }
                            )
                        }
                    } else {
                        // Fallback using recommended or daily songs
                        val mixSongs = (recommended.ifEmpty { daily }).take(6)
                        itemsIndexed(mixSongs, key = { index, song -> "pop_s_${index}_${song.videoId}" }) { index, song ->
                            val artistName = song.artists.joinToString(", ") { it.name }.ifBlank { "Various Artists" }
                            DailyMixCard(
                                title = song.title,
                                subtitle = "$artistName • Top Hits",
                                artworkUrl = song.thumbnailUrl,
                                mixNumber = index + 1,
                                badgeColor = DailyMixColors[index % DailyMixColors.size],
                                badgeLabel = "Top Hits",
                                onClick = { onSongClick(song) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // ── 4. SECOND CONTENT SECTION ("Lagu yang baru kamu putar") ───────────────────
            item {
                Text(
                    text = "Lagu yang baru kamu putar",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = SpotifyTextPrimary,
                    letterSpacing = (-0.4).sp,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 14.dp)
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val displayedRecentSongs = if (recentlyPlayed.isNotEmpty()) recentlyPlayed else popularSongs
                    itemsIndexed(displayedRecentSongs, key = { index, song -> "recent_${song.videoId}_$index" }) { _, song ->
                        PopularSongCard(
                            song = song,
                            onClick = { onSongClick(song) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // ── 5. THIRD CONTENT SECTION ("Artis populer") ───────────────────
            item {
                Text(
                    text = "Artis populer",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = SpotifyTextPrimary,
                    letterSpacing = (-0.4).sp,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 14.dp)
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(popularArtists, key = { index, artist -> "art_${index}_${artist.name}" }) { _, artist ->
                        PopularArtistCircleCard(
                            artist = artist,
                            onClick = { onArtistClick(artist.name) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // ── 6. FOURTH CONTENT SECTION ("Nostalgia") ──────────────────────
            item {
                Text(
                    text = "Nostalgia",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = SpotifyTextPrimary,
                    letterSpacing = (-0.4).sp,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 14.dp)
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    itemsIndexed(nostalgiaItems, key = { index, item -> "nos_${index}_${item.id}" }) { _, item ->
                        NostalgiaCard(
                            item = item,
                            onClick = {
                                val song = daily.firstOrNull { it.title.contains(item.title, ignoreCase = true) }
                                if (song != null) {
                                    onSongClick(song)
                                } else {
                                    onPlaylistClick(
                                        PlaylistCardItem(
                                            id = item.id,
                                            title = item.title,
                                            description = item.subtitle,
                                            artworkUrl = item.artworkUrl
                                        )
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

// ── 1. TOP BAR COMPONENT ──────────────────────────────────────────────────────
@Composable
private fun HomeTopBar(
    avatarUrl: String?,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filterCategories = listOf("All", "Wrapped", "Music")

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // User Profile Avatar on left
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(SpotifyCardBackground)
                .clickable(onClick = onProfileClick),
            contentAlignment = Alignment.Center
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = "Profile",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Profile",
                    tint = SpotifyTextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Horizontal Category Filter Pills beside avatar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            filterCategories.forEach { category ->
                val isSelected = (category == selectedCategory) || (selectedCategory !in filterCategories && category == "All")
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(if (isSelected) SongfyBlue else SpotifyCardBackground)
                        .clickable { onCategorySelected(category) }
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = category,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else SpotifyTextPrimary
                    )
                }
            }
        }
    }
}

// ── 2. QUICK MUSIC / ARTIST GRID (2 Columns × 4 Rows = 8 Cards) ───────────────
@Composable
private fun QuickMusicGrid(
    songs: List<SongDto>,
    onSongClick: (SongDto) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (rowIndex in 0 until 4) {
            val leftSong = songs.getOrNull(rowIndex * 2)
            val rightSong = songs.getOrNull(rowIndex * 2 + 1)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (leftSong != null) {
                    QuickGridCard(
                        song = leftSong,
                        isPlaying = (rowIndex == 1), // Optional playing equalizer accent
                        onClick = { onSongClick(leftSong) },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                if (rightSong != null) {
                    QuickGridCard(
                        song = rightSong,
                        isPlaying = false,
                        onClick = { onSongClick(rightSong) },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun QuickGridCard(
    song: SongDto,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val artistName = remember(song.artists) {
        song.artists.firstOrNull()?.name?.ifBlank { null }
    }
    val displayText = remember(song.title, artistName) {
        if (!artistName.isNullOrBlank() && !song.title.contains(artistName, ignoreCase = true)) {
            artistName
        } else {
            song.title
        }
    }

    Row(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(SpotifyCardBackground)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left square artwork
        Box(
            modifier = Modifier
                .size(54.dp)
                .background(Color(0xFF1E1E1E)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.2f),
                modifier = Modifier.size(22.dp)
            )
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(song.highResThumbnailUrl)
                    .crossfade(200)
                    .build(),
                contentDescription = displayText,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Middle title text
        Text(
            text = displayText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = SpotifyTextPrimary,
            maxLines = 2,
            lineHeight = 15.sp,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        )

        // Optional playing equalizer indicator
        if (isPlaying) {
            Icon(
                imageVector = Icons.Default.Equalizer,
                contentDescription = "Playing",
                tint = SongfyBlue,
                modifier = Modifier
                    .size(16.dp)
                    .padding(end = 6.dp)
            )
        }
    }
}

// ── 3. DAILY MIX CARD (Section 1: "Lagu yang sering kamu putar") ───────────────
@Composable
private fun DailyMixCard(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    mixNumber: Int,
    badgeColor: Color,
    badgeLabel: String = "Playlist",
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(145.dp)
            .clickable(onClick = onClick)
    ) {
        // Artwork Box with overlay badge
        Box(
            modifier = Modifier
                .size(145.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SpotifyCardBackground)
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.15f),
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.Center)
            )
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(artworkUrl)
                    .crossfade(300)
                    .build(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Playlist bottom badge overlay
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 6.dp, start = 6.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Badge Tag
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .background(badgeColor)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Number Tag ("01", "02", ...)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .background(badgeColor)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = String.format("%02d", mixNumber),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Title
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = SpotifyTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Subtitle / Description
        Text(
            text = subtitle,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            color = SpotifyTextSecondary,
            maxLines = 2,
            lineHeight = 14.sp,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── 4. POPULAR SONG CARD (Section 2: "Lagu populer") ───────────────────────────
@Composable
private fun PopularSongCard(
    song: SongDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val artistName = remember(song.artists) {
        song.artists.joinToString(", ") { it.name }.ifBlank { "Various Artists" }
    }

    Column(
        modifier = modifier
            .width(145.dp)
            .clickable(onClick = onClick)
    ) {
        // Artwork Box
        Box(
            modifier = Modifier
                .size(145.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SpotifyCardBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.15f),
                modifier = Modifier.size(40.dp)
            )
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(song.highResThumbnailUrl)
                    .crossfade(300)
                    .build(),
                contentDescription = song.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Song Title
        Text(
            text = song.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = SpotifyTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Artist Name
        Text(
            text = artistName,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            color = SpotifyTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── 5. POPULAR ARTIST CIRCLE CARD (Section 3: "Artis populer") ─────────────────
@Composable
private fun PopularArtistCircleCard(
    artist: PopularArtistItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(155.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Perfectly circular artist image (155dp matching reference screenshot)
        AsyncImage(
            model = artist.imageUrl,
            contentDescription = artist.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(155.dp)
                .clip(CircleShape)
                .background(SpotifyCardBackground)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Centered Artist Name
        Text(
            text = artist.name,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = SpotifyTextPrimary,
            maxLines = 1,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ── 6. NOSTALGIA CARD (Section 4: "Nostalgia") ──────────────────────────────────
@Composable
private fun NostalgiaCard(
    item: NostalgiaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(155.dp)
            .clickable(onClick = onClick)
    ) {
        // Large rectangular artwork with rounded corners (8dp)
        Box(
            modifier = Modifier
                .size(155.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SpotifyCardBackground)
        ) {
            AsyncImage(
                model = item.artworkUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Small circular Spotify logo badge on top-left (matching reference screenshot)
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(item.badgeColor)
                    .align(Alignment.TopStart),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(11.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Title
        Text(
            text = item.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = SpotifyTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Subtitle / artists list
        Text(
            text = item.subtitle,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            color = SpotifyTextSecondary,
            maxLines = 2,
            lineHeight = 14.sp,
            overflow = TextOverflow.Ellipsis
        )
    }
}
