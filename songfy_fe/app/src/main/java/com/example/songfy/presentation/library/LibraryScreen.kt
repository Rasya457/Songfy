package com.lagu.songfy.presentation.library

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.io.File
import java.io.FileOutputStream
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.auth.FirebaseAuth
import com.lagu.songfy.data.remote.dto.ArtistShortDto
import com.lagu.songfy.data.remote.dto.SongDto
import coil.compose.AsyncImage
import com.lagu.songfy.presentation.home.HomeUiState
import com.lagu.songfy.presentation.home.HomeViewModel
import com.lagu.songfy.presentation.home.PlaylistCardItem
import com.lagu.songfy.presentation.library.components.LibraryItemRow
import com.lagu.songfy.presentation.theme.*
import com.lagu.songfy.util.collectSelector

data class LibraryDisplayItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val isArtist: Boolean,
    val isUserPlaylist: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    homeViewModel: HomeViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    onSongClick: (SongDto) -> Unit = {},
    onPlaylistClick: (PlaylistCardItem) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onAddArtistClick: () -> Unit = {}
) {
    val context = LocalContext.current
    var showCreateOptionSheet by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var selectedBannerUrl by remember { mutableStateOf<String?>(null) }
    var playlistToDelete by remember { mutableStateOf<LibraryDisplayItem?>(null) }

    val bannerPresets = remember {
        listOf(
            "Sunset Glow" to "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=800&q=80",
            "Neon Cyber" to "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=800&q=80",
            "Chill Lo-Fi" to "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=800&q=80",
            "Dark Velvet" to "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=800&q=80",
            "Indie Acoustic" to "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=800&q=80",
            "EDM Wave" to "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=800&q=80",
            "Retro Vinyl" to "https://images.unsplash.com/photo-1539375665275-f9de415ef9ac?w=800&q=80"
        )
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val dir = File(context.filesDir, "playlist_covers").apply { if (!exists()) mkdirs() }
                val file = File(dir, "banner_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(it)?.use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                selectedBannerUrl = Uri.fromFile(file).toString()
            } catch (_: Exception) {
                selectedBannerUrl = it.toString()
            }
        }
    }

    // PERF: Sebelumnya collect seluruh homeUiState → LibraryScreen recompose tiap ada
    // perubahan di history, recommendations, atau liked songs walau Library tidak menampilkannya.
    // Sekarang hanya collect 3 field yang benar-benar dipakai LibraryScreen.
    val topPlaylists by homeViewModel.uiState.collectSelector<HomeUiState, List<PlaylistCardItem>>(selector = { it.topPlaylists })
    val homeAvatarUrl by homeViewModel.uiState.collectSelector<HomeUiState, String?>(selector = { it.userAvatarUrl })
    val homeUserName by homeViewModel.uiState.collectSelector<HomeUiState, String>(selector = { it.userName })
    val savedArtists by libraryViewModel.savedArtists.collectAsStateWithLifecycle()
    val userPlaylists by libraryViewModel.userPlaylists.collectAsStateWithLifecycle()
    var selectedFilter by remember { mutableStateOf<String?>(null) } // null = All, "Playlist", "Artis"

    // If a filter chip is active, back clears the filter without navigating away from Library
    BackHandler(enabled = selectedFilter != null) {
        selectedFilter = null
    }

    // Extract user profile from centralized state
    val currentUser = remember { FirebaseAuth.getInstance().currentUser }
    val avatarToDisplay = homeAvatarUrl ?: currentUser?.photoUrl?.toString()
    val initialToDisplay = homeUserName.firstOrNull()?.uppercaseChar()?.toString()
        ?: currentUser?.displayName?.firstOrNull()?.uppercaseChar()?.toString()
        ?: "R"

    // Combine backend playlists, saved artists, and user playlists (no hardcoded placeholder data)
    val allItems = remember(topPlaylists, savedArtists, userPlaylists) {
        val userArtistItems = savedArtists.map { art ->
            LibraryDisplayItem(
                id = "art_${art.id}",
                title = art.name,
                subtitle = "Artis",
                imageUrl = art.imageUrl,
                isArtist = true,
                isUserPlaylist = false
            )
        }
        val userPlaylistItems = userPlaylists.map { pl ->
            LibraryDisplayItem(
                id = pl.id,
                title = pl.title,
                subtitle = "Playlist • ${pl.songs.size} lagu",
                imageUrl = pl.artworkUrl,
                isArtist = false,
                isUserPlaylist = true
            )
        }
        val backendItems = topPlaylists.map { pl ->
            LibraryDisplayItem(
                id = pl.id,
                title = pl.title,
                subtitle = "Playlist • YouTube Music",
                imageUrl = pl.artworkUrl,
                isArtist = false,
                isUserPlaylist = false
            )
        }
        userArtistItems + userPlaylistItems + backendItems
    }

    // Filter items according to selected chip
    val filteredItems = remember(selectedFilter, allItems) {
        when (selectedFilter) {
            "Playlist" -> allItems.filter { !it.isArtist }
            "Artis" -> allItems.filter { it.isArtist }
            else -> allItems
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundNearBlack)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .displayCutoutPadding(),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = 20.dp,
                bottom = 160.dp // Clear both MiniPlayer and Bottom Navbar
            )
        ) {
            // 1. Header matching Spotify reference
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Avatar with User Initial (e.g. "R" in circular lime-green badge)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!avatarToDisplay.isNullOrBlank()) {
                                AsyncImage(
                                    model = avatarToDisplay,
                                    contentDescription = "User Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = initialToDisplay,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }

                        Text(
                            text = "Koleksi Kamu",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            letterSpacing = (-0.5).sp
                        )
                    }

                    // Right: Search & Add Icons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(onClick = {
                            if (selectedFilter == "Artis") {
                                onAddArtistClick()
                            } else {
                                onSearchClick()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Cari",
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        IconButton(onClick = { showCreateOptionSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Tambah",
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }

            // 2. Horizontal Filter Chips ("Playlist", "Artis")
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val filterOptions = listOf("Playlist", "Artis")
                    filterOptions.forEach { filter ->
                        val isSelected = selectedFilter == filter
                        val chipBg = if (isSelected) {
                            MaterialTheme.colorScheme.onBackground // White/Bright active pill
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant // Dark gray inactive pill
                        }
                        val textColor = if (isSelected) {
                            MaterialTheme.colorScheme.background // Dark text on active
                        } else {
                            MaterialTheme.colorScheme.onBackground // White text on inactive
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(percent = 50))
                                .background(chipBg)
                                .clickable {
                                    selectedFilter = if (isSelected) null else filter
                                }
                                .padding(horizontal = 18.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = filter,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = textColor
                            )
                        }
                    }
                }
            }

            // Top shortcut row when "Artis" filter is active
            if (selectedFilter == "Artis") {
                item {
                    LibraryAddShortcutRow(
                        title = "Tambahkan artis",
                        onClick = onAddArtistClick,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }

            // 3. Collection List (LazyColumn items)
            items(filteredItems, key = { it.id }) { item ->
                LibraryItemRow(
                    title = item.title,
                    subtitle = item.subtitle,
                    imageUrl = item.imageUrl,
                    isArtist = item.isArtist,
                    onClick = {
                        if (item.isArtist) {
                            onArtistClick(item.title)
                        } else {
                            val playlist = PlaylistCardItem(
                                id = item.id,
                                title = item.title,
                                description = item.subtitle,
                                artworkUrl = item.imageUrl ?: ""
                            )
                            onPlaylistClick(playlist)
                        }
                    },
                    onLongClick = if (item.isUserPlaylist) {
                        {
                            val isSystem = item.title.equals("like song", ignoreCase = true) ||
                                    item.title.equals("Lagu Offline", ignoreCase = true)
                            if (isSystem) {
                                Toast.makeText(context, "Playlist sistem tidak dapat dihapus", Toast.LENGTH_SHORT).show()
                            } else {
                                playlistToDelete = item
                            }
                        }
                    } else null
                )
            }

            // 4. Section Tambahan di Bawah List (Spotify Reference Layout)
            if (selectedFilter != "Artis") {
                item {
                    Spacer(modifier = Modifier.height(12.dp))

                    LibraryAddShortcutRow(
                        title = "Tambahkan artis",
                        onClick = onAddArtistClick
                    )
                }
            }
        }

        // ModalBottomSheet: Pilih antara "Folder Playlist" dan "Artis"
        if (showCreateOptionSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showCreateOptionSheet = false },
                sheetState = sheetState,
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
                        .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 24.dp)
                ) {
                    Text(
                        text = "Buat",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        color = TextPrimaryWhite,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Option 1: Bikin folder playlist
                    CreateOptionRow(
                        icon = Icons.AutoMirrored.Filled.QueueMusic,
                        title = "Playlist",
                        subtitle = "Buat folder playlist dengan lagu-lagu pilihanmu",
                        onClick = {
                            showCreateOptionSheet = false
                            if (selectedBannerUrl == null) {
                                selectedBannerUrl = bannerPresets.first().second
                            }
                            showCreatePlaylistDialog = true
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Option 2: Milih artis
                    CreateOptionRow(
                        icon = Icons.Default.Person,
                        title = "Artis",
                        subtitle = "Pilih dan ikuti artis favorit kamu",
                        onClick = {
                            showCreateOptionSheet = false
                            onAddArtistClick()
                        }
                    )
                }
            }
        }

        // Modal Bottom Sheet: Buat Playlist Baru dengan Banner Image
        if (showCreatePlaylistDialog) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = {
                    showCreatePlaylistDialog = false
                    newPlaylistName = ""
                },
                sheetState = sheetState,
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
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Buat Playlist Baru",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        color = TextPrimaryWhite
                    )

                    Text(
                        text = "Pilih gambar banner dan beri nama untuk playlist kamu",
                        color = TextSecondaryGray,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                    )

                    // 1. BANNER PREVIEW (16:9 ratio with rounded corners)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                    ) {
                        val activeBanner = selectedBannerUrl ?: bannerPresets.first().second
                        AsyncImage(
                            model = activeBanner,
                            contentDescription = "Banner Preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Gradient overlay at bottom
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.65f)
                                        ),
                                        startY = 100f
                                    )
                                )
                        )

                        // Bottom row in preview: Pill badge & Gallery picker
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Preview Banner",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }

                            // Button to pick custom image from gallery
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { galleryLauncher.launch("image/*") },
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddPhotoAlternate,
                                        contentDescription = "Ganti Foto",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Galeri",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. BANNER PRESETS CAROUSEL
                    Text(
                        text = "Pilih Gambar Banner",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimaryWhite
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Custom Gallery tile
                        Box(
                            modifier = Modifier
                                .width(96.dp)
                                .height(64.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { galleryLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = "Upload Galeri",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Unggah",
                                    fontSize = 11.sp,
                                    color = TextPrimaryWhite,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Presets
                        bannerPresets.forEach { (name, url) ->
                            val isSelected = selectedBannerUrl == url
                            Box(
                                modifier = Modifier
                                    .width(96.dp)
                                    .height(64.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(
                                        width = if (isSelected) 2.dp else 0.5.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { selectedBannerUrl = url }
                            ) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.35f))
                                )
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Terpilih",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = name,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 3. PLAYLIST NAME INPUT
                    Text(
                        text = "Nama Playlist",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimaryWhite
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        placeholder = { Text("Contoh: Lagu Galau, Beat Semangat...", color = TextSecondaryGray, fontSize = 14.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimaryWhite,
                            unfocusedTextColor = TextPrimaryWhite,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // 4. ACTION BUTTONS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                showCreatePlaylistDialog = false
                                newPlaylistName = ""
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimaryWhite)
                        ) {
                            Text("Batal", fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = {
                                val name = newPlaylistName.trim().ifBlank { "Playlist Baru" }
                                val finalBanner = selectedBannerUrl ?: bannerPresets.first().second
                                libraryViewModel.createPlaylist(name, finalBanner) { createdPl ->
                                    onPlaylistClick(
                                        PlaylistCardItem(
                                            id = createdPl.id,
                                            title = createdPl.title,
                                            description = createdPl.description,
                                            artworkUrl = createdPl.artworkUrl ?: finalBanner
                                        )
                                    )
                                }
                                Toast.makeText(context, "Playlist \"$name\" berhasil dibuat!", Toast.LENGTH_SHORT).show()
                                showCreatePlaylistDialog = false
                                newPlaylistName = ""
                            },
                            modifier = Modifier
                                .weight(1.5f)
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Buat Playlist",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // Dialog Konfirmasi Hapus Playlist Permanen (Long-press)
        playlistToDelete?.let { target ->
            AlertDialog(
                onDismissRequest = { playlistToDelete = null },
                containerColor = SurfaceCard,
                shape = RoundedCornerShape(20.dp),
                title = {
                    Text(
                        text = "Hapus Playlist?",
                        color = TextPrimaryWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Apakah Anda yakin ingin menghapus playlist \"${target.title}\" secara permanen dari koleksi kamu?",
                        color = TextSecondaryGray,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            libraryViewModel.deletePlaylist(target.id)
                            Toast.makeText(context, "Playlist \"${target.title}\" berhasil dihapus", Toast.LENGTH_SHORT).show()
                            playlistToDelete = null
                        }
                    ) {
                        Text("Hapus", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { playlistToDelete = null }) {
                        Text("Batal", color = TextSecondaryGray)
                    }
                }
            )
        }
    }
}

@Composable
private fun CreateOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = TextPrimaryWhite,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimaryWhite
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = TextSecondaryGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun LibraryAddShortcutRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rounded square placeholder with "+" icon matching screenshot
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
