package com.lagu.songfy.presentation.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.lagu.songfy.data.remote.dto.ArtistSummaryDto
import com.lagu.songfy.data.remote.dto.SongDto
import com.lagu.songfy.presentation.theme.*
import com.lagu.songfy.util.collectSelector
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
    onSongClick: (SongDto) -> Unit = {},
    onAddToQueue: (SongDto) -> Unit = {},
    onArtistClick: (String) -> Unit = {}
) {
    // NOTE 2026-09-12: param homeViewModel/isActive/onBackClick REMOVED — tidak ada baris
    // yang baca (grep). hiltViewModel() tak terpakai = graph Hilt + VM di-init sia-sia
    // tiap tab Search dibuka. Kembalikan bila dibutuhkan riil.
    // Selector granular: ketikan query (tiap keystroke) HANYA recompose textfield,
    // hasil network HANYA recompose daftar — bukan seluruh discovery page.
    val isSearchActive by viewModel.uiState.collectSelector(selector = { it.isSearchActive })
    val query by viewModel.uiState.collectSelector(selector = { it.query })
    val selectedFilter by viewModel.uiState.collectSelector(selector = { it.selectedFilter })
    val isSearching by viewModel.uiState.collectSelector(selector = { it.isSearching })
    val searchResults by viewModel.uiState.collectSelector(selector = { it.searchResults })
    val artistResults by viewModel.uiState.collectSelector(selector = { it.artistResults })
    // albumResults TIDAK di-collect: UI tidak pernah render album (grep) — collect =
    // recompose tiap search selesai tanpa ada yang berubah di layar.
    val recentSearches by viewModel.uiState.collectSelector(selector = { it.recentSearches })
    val viralHits by viewModel.uiState.collectSelector(selector = { it.viralHits })
    val trendingNow by viewModel.uiState.collectSelector(selector = { it.trendingNow })
    val recentReleases by viewModel.uiState.collectSelector(selector = { it.recentReleases })
    val showBanner by viewModel.uiState.collectSelector(selector = { it.showBanner })
    val showClearConfirmDialog by viewModel.uiState.collectSelector(selector = { it.showClearConfirmDialog })
    val snackbarMessage by viewModel.uiState.collectSelector(selector = { it.snackbarMessage })
    // error TIDAK di-collect: tidak ada UI error state di layar ini (grep) — hanya
    // ViewModel yang set. Collect = recompose tiap gagal network tanpa efek visual.
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // Intercept back: if search mode is active, back exits search mode
    BackHandler(enabled = isSearchActive) {
        if (query.isNotEmpty()) {
            viewModel.onQueryChanged("")
        } else {
            keyboardController?.hide()
            viewModel.setSearchActive(false)
        }
    }

    // Auto-focus textfield ONLY when search mode becomes active
    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            delay(150)
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        } else {
            try {
                keyboardController?.hide()
            } catch (_: Exception) {}
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundNearBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .displayCutoutPadding()
        ) {
            // ==========================================
            // TOP SEARCH BAR LAYOUT
            // ==========================================
            if (!isSearchActive) {
                // Header in Browse Mode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Telusuri",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimaryWhite
                    )
                }

                // Clickable Search Box (Entry to Active Search)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceDarkNavy)
                        .clickable {
                            viewModel.setSearchActive(true)
                        }
                        .padding(horizontal = 14.dp, vertical = 13.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = TextSecondaryGray,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Artis, lagu, lirik, atau album...",
                            color = TextSecondaryGray,
                            fontSize = 15.sp
                        )
                    }
                }
            } else {
                // Active Search Input Header with Back Arrow
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            keyboardController?.hide()
                            viewModel.setSearchActive(false)
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimaryWhite,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    BasicTextField(
                        value = query,
                        onValueChange = { viewModel.onQueryChanged(it) },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .focusRequester(focusRequester),
                        textStyle = TextStyle(
                            color = TextPrimaryWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        cursorBrush = SolidColor(AccentSilver),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                        decorationBox = { innerTextField ->
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(SurfaceDarkNavy)
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = TextSecondaryGray,
                                    modifier = Modifier.size(18.dp)
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Box(modifier = Modifier.weight(1f)) {
                                    if (query.isEmpty()) {
                                        Text(
                                            text = "Cari di Songfy...",
                                            color = TextSecondaryGray,
                                            fontSize = 14.sp
                                        )
                                    }
                                    innerTextField()
                                }

                                if (query.isNotEmpty()) {
                                    IconButton(
                                        onClick = { viewModel.onQueryChanged("") },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear",
                                            tint = TextSecondaryGray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    )
                }

                // Filter Chips Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Semua", "Lagu", "Artis", "Album").forEach { filter ->
                        val isSelected = selectedFilter == filter
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (isSelected) AccentSilver else SurfaceDarkNavy)
                                .clickable { viewModel.setFilter(filter) }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = filter,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) BackgroundNearBlack else TextPrimaryWhite
                            )
                        }
                    }
                }
            }

            // ==========================================
            // MAIN CONTENT (Discovery vs Active Search)
            // Scope baca dipersempit: discovery hanya observe banner+list statis,
            // active-search hanya observe query+hasil. Pola lama: kedua branch baca
            // searchUiState penuh = ketikan query recompose discovery + sebaliknya.
            // ==========================================
            if (!isSearchActive) {
                // ==========================================
                // 1. APPLE MUSIC STYLE DISCOVERY PAGE
                // ==========================================
                DiscoveryContent(
                    showBanner = showBanner,
                    viralHits = viralHits,
                    trendingNow = trendingNow,
                    recentReleases = recentReleases,
                    onDismissBanner = { viewModel.dismissBanner() },
                    onSongClick = onSongClick,
                    onAddToQueue = {
                        onAddToQueue(it)    
                        viewModel.showSnackbar("Ditambahkan ke antrean")
                    },
                    onCategoryClick = { viewModel.onQueryChanged(it) }
                )
            } else {
                ActiveSearchContent(
                    query = query,
                    selectedFilter = selectedFilter,
                    isSearching = isSearching,
                    searchResults = searchResults,
                    artistResults = artistResults,
                    recentSearches = recentSearches,
                    onSongClick = {
                        viewModel.onSongSelected(it)
                        onSongClick(it)
                    },
                    onAddToQueue = {
                        onAddToQueue(it)
                        viewModel.showSnackbar("Ditambahkan ke antrean")
                    },
                    onRemoveRecent = { viewModel.removeRecentSearch(it) },
                    onClearAll = { viewModel.setClearConfirmDialogVisible(true) },
                    onArtistClick = onArtistClick
                )
            }
        }

        // Snackbar Host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 110.dp)
        )

        // Clear Confirmation Dialog
        if (showClearConfirmDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.setClearConfirmDialogVisible(false) },
                title = {
                    Text(
                        text = "Hapus Riwayat Pencarian?",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryWhite
                    )
                },
                text = {
                    Text(
                        text = "Tindakan ini akan menghapus semua riwayat pencarian terbaru kamu.",
                        color = TextSecondaryGray,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.clearAllRecentSearches() }
                    ) {
                        Text("Hapus", color = Color(0xFFF43F5E), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.setClearConfirmDialogVisible(false) }
                    ) {
                        Text("Batal", color = TextSecondaryGray)
                    }
                },
                containerColor = SurfaceDarkNavy,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@Composable
private fun DiscoveryContent(
    showBanner: Boolean,
    viralHits: List<SongDto>,
    trendingNow: List<SongDto>,
    recentReleases: List<SongDto>,
    onDismissBanner: () -> Unit,
    onSongClick: (SongDto) -> Unit,
    onAddToQueue: (SongDto) -> Unit,
    onCategoryClick: (String) -> Unit
) {
    // NOTE: indentasi badan di bawah berlebih (sisa pecah fungsi) — rapikan dengan
    // Ctrl+Alt+L di Android Studio, jangan edit manual per baris.
    LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 10.dp, bottom = 140.dp)
                ) {
                    // Promo banner dihapus sesuai preferensi pengguna (clean minimal search)


                    // Section 1: Favourite These Viral Hits (Reference)
                    item {
                        SectionHeader(
                            title = "Favourite These Viral Hits",
                            icon = Icons.Outlined.Star,
                            onClick = {}
                        )
                    }

                    item {
                        // 2 Columns of 4 songs each (Horizontal Paging Scroll).
                        // remember: take/drop dialokasi sekali per data baru, bukan tiap recompose.
                        val viralCols = remember(viralHits) {
                            Pair(viralHits.take(4), viralHits.drop(4).take(4))
                        }
                        val col1 = viralCols.first
                        val col2 = viralCols.second

                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 18.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
                                ViralHitsColumn(
                                    songs = col1,
                                    onSongClick = onSongClick,
                                    onAddToQueue = onAddToQueue
                                )
                            }
                            if (col2.isNotEmpty()) {
                                item {
                                    ViralHitsColumn(
                                        songs = col2,
                                        onSongClick = onSongClick,
                                        onAddToQueue = onAddToQueue
                                    )
                                }
                            }
                        }
                    }

                    // Section 2: Trending Now (Reference: Horizontal Large Cards)
                    item {
                        Spacer(modifier = Modifier.height(22.dp))
                        SectionHeader(
                            title = "Trending Now",
                            onClick = {}
                        )
                    }

                    item {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 18.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            itemsIndexed(trendingNow, key = { index, song -> "trend_${index}_${song.videoId}" }) { _, song ->
                                TrendingCard(
                                    song = song,
                                    onClick = { onSongClick(song) }
                                )
                            }
                        }
                    }

                    // Section 3: Recent Releases (Reference: Horizontal Album Cards)
                    item {
                        Spacer(modifier = Modifier.height(22.dp))
                        SectionHeader(
                            title = "Recent Releases",
                            onClick = {}
                        )
                    }

                    item {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 18.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            itemsIndexed(recentReleases, key = { index, song -> "rel_${index}_${song.videoId}" }) { _, song ->
                                RecentReleaseCard(
                                    song = song,
                                    onClick = { onSongClick(song) }
                                )
                            }
                        }
                    }

                    // Section 4: Browse Categories / Genres
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Jelajahi Kategori",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryWhite,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                        )
                    }

                    item {
                        CategoryGrid(onCategoryClick = onCategoryClick)
                    }
                }
} // tutup DiscoveryContent

@Composable
private fun CategoryGrid(onCategoryClick: (String) -> Unit) {
    // remember: list + chunked dialokasi sekali. Pola lama: listOf + chunked(2)
    // dibangun ulang tiap recompose discovery (tiap ketikan / hasil network).
    val rows = remember {
        listOf(
            CategoryTileData("Pop", listOf(Color(0xFFE11D48), Color(0xFFBE123C))),
            CategoryTileData("Hip-Hop", listOf(Color(0xFFEA580C), Color(0xFFC2410C))),
            CategoryTileData("Indie Indonesia", listOf(Color(0xFF059669), Color(0xFF047857))),
            CategoryTileData("R&B & Soul", listOf(Color(0xFF2563EB), Color(0xFF1D4ED8))),
            CategoryTileData("Rock", listOf(Color(0xFF7C3AED), Color(0xFF6D28D9))),
            CategoryTileData("K-Pop", listOf(Color(0xFFDB2777), Color(0xFFBE185D)))
        ).chunked(2)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        rows.forEach { rowPairs ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowPairs.forEach { item ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(84.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(item.colors))
                            .clickable { onCategoryClick(item.title) }
                            .padding(14.dp)
                    ) {
                        Text(
                            text = item.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryWhite,
                            modifier = Modifier.align(Alignment.TopStart)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveSearchContent(
    query: String,
    selectedFilter: String,
    isSearching: Boolean,
    searchResults: List<SongDto>,
    artistResults: List<ArtistSummaryDto>,
    recentSearches: List<SongDto>,
    onSongClick: (SongDto) -> Unit,
    onAddToQueue: (SongDto) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearAll: () -> Unit,
    onArtistClick: (String) -> Unit
) {
    // Scope menyempit: composable ini hanya recompose saat query/hasil berubah —
    // discovery (banner, viral, trending) tidak ikut rebuild tiap keystroke.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 140.dp)
    ) {
        if (query.isBlank()) {
            // RECENT SEARCHES
            item {
                Text(
                    text = "Baru Dicari",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryWhite,
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 8.dp)
                )
            }

            if (recentSearches.isNotEmpty()) {
                itemsIndexed(recentSearches, key = { index, song -> "recent_${index}_${song.videoId}" }) { _, song ->
                    RecentSearchRow(
                        song = song,
                        onClick = { onSongClick(song) },
                        onAddToQueue = { onAddToQueue(song) },
                        onRemove = { onRemoveRecent(song.videoId) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        OutlinedButton(
                            onClick = onClearAll,
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, Color(0xFF383844)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = TextPrimaryWhite
                            ),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "Hapus semua pencarian",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 56.dp, horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = TextSecondaryGray.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Ketik untuk mencari musik",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimaryWhite
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Temukan lagu, artis, atau album favoritmu di Songfy.",
                            fontSize = 13.sp,
                            color = TextSecondaryGray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            // LIVE SEARCH RESULTS
            if (isSearching && searchResults.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 50.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = AccentSilver,
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp
                        )
                    }
                }
            } else if (searchResults.isNotEmpty() || artistResults.isNotEmpty()) {
                // ARTISTS
                if (artistResults.isNotEmpty() &&
                    (selectedFilter == "Semua" || selectedFilter == "Artis")
                ) {
                    item {
                        Text(
                            text = "Artis",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryWhite,
                            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 6.dp)
                        )
                    }
                    itemsIndexed(artistResults, key = { index, artist -> "art_${index}_${artist.artistId ?: artist.name.orEmpty()}" }) { _, artist ->
                        ArtistResultRow(
                            artist = artist,
                            onClick = {
                                val id = artist.artistId ?: artist.name ?: return@ArtistResultRow
                                onArtistClick(id)
                            }
                        )
                    }
                }

                // SONGS
                if (searchResults.isNotEmpty() &&
                    (selectedFilter == "Semua" || selectedFilter == "Lagu")
                ) {
                    item {
                        Text(
                            text = "Lagu",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryWhite,
                            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 6.dp)
                        )
                    }
                    itemsIndexed(searchResults, key = { index, song -> "res_${index}_${song.videoId}" }) { _, song ->
                        SearchResultRow(
                            song = song,
                            onClick = { onSongClick(song) },
                            onAddToQueue = { onAddToQueue(song) },
                            onArtistClick = onArtistClick
                        )
                    }
                }
            } else if (!isSearching) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = TextSecondaryGray.copy(alpha = 0.5f),
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Tidak ada hasil untuk \"$query\"",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimaryWhite,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Periksa ejaan kata kunci atau coba cari dengan kata lain.",
                            fontSize = 13.sp,
                            color = TextSecondaryGray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================================
// SUB-COMPONENTS (APPLE MUSIC STYLE DISCOVERY)
// =========================================================================================

data class CategoryTileData(val title: String, val colors: List<Color>)

@Composable
fun ExplicitBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(Color.White.copy(alpha = 0.28f))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(
            text = "E",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimaryWhite
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextPrimaryWhite,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimaryWhite
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = "More",
            tint = TextSecondaryGray,
            modifier = Modifier.size(13.dp)
        )
    }
}

@Composable
private fun ViralHitsColumn(
    songs: List<SongDto>,
    onSongClick: (SongDto) -> Unit,
    onAddToQueue: (SongDto) -> Unit
) {
    Column(
        modifier = Modifier.width(280.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        songs.forEach { song ->
            ViralHitItem(
                song = song,
                onClick = { onSongClick(song) },
                onMoreClick = { onAddToQueue(song) }
            )
        }
    }
}

@Composable
private fun ViralHitItem(
    song: SongDto,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val artistName = song.artists.firstOrNull()?.name ?: "Unknown Artist"
    val isExplicit = song.title.contains("explicit", ignoreCase = true)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SongItemCardBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.thumbnailUrl,
            contentDescription = song.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(SurfaceDarkNavy)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = song.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimaryWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isExplicit) {
                    Spacer(modifier = Modifier.width(5.dp))
                    ExplicitBadge()
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = artistName,
                fontSize = 12.sp,
                color = TextSecondaryGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onMoreClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreHoriz,
                contentDescription = "More",
                tint = TextSecondaryGray,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun TrendingCard(
    song: SongDto,
    onClick: () -> Unit
) {
    val artistName = song.artists.firstOrNull()?.name ?: "Unknown Artist"
    val isExplicit = song.title.contains("explicit", ignoreCase = true)

    Column(
        modifier = Modifier
            .width(148.dp)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = song.thumbnailUrl,
            contentDescription = song.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(148.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceDarkNavy)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = song.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimaryWhite,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (isExplicit) {
                Spacer(modifier = Modifier.width(4.dp))
                ExplicitBadge()
            }
        }

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

@Composable
private fun RecentReleaseCard(
    song: SongDto,
    onClick: () -> Unit
) {
    val artistName = song.artists.firstOrNull()?.name ?: "Unknown Artist"

    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = song.thumbnailUrl,
            contentDescription = song.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceDarkNavy)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = song.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
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

// =========================================================================================
// ACTIVE SEARCH RESULT ROWS
// =========================================================================================

@Composable
private fun RecentSearchRow(
    song: SongDto,
    onClick: () -> Unit,
    onAddToQueue: () -> Unit,
    onRemove: () -> Unit
) {
    val artistName = song.artists.firstOrNull()?.name ?: "Unknown Artist"

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

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimaryWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "Lagu â€¢ $artistName",
                fontSize = 12.sp,
                color = TextSecondaryGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onAddToQueue,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.AddCircleOutline,
                contentDescription = "Add to Queue",
                tint = TextSecondaryGray,
                modifier = Modifier.size(20.dp)
            )
        }

        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove",
                tint = TextSecondaryGray,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SearchResultRow(
    song: SongDto,
    onClick: () -> Unit,
    onAddToQueue: () -> Unit,
    onArtistClick: (String) -> Unit
) {
    val artistName = song.artists.firstOrNull()?.name ?: "Unknown Artist"
    val artistId = song.artists.firstOrNull()?.id ?: artistName

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

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimaryWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = artistName,
                fontSize = 12.sp,
                color = TextSecondaryGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { onArtistClick(artistId) }
            )
        }

        IconButton(
            onClick = onAddToQueue,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.AddCircleOutline,
                contentDescription = "Add to Queue",
                tint = TextSecondaryGray,
                modifier = Modifier.size(20.dp)
            )
        }

        // NOTE 2026-09-12: tombol MoreVert no-op (onClick = {}) dihapus — 1 IconButton
        // + vector per baris hasil. Kembalikan saat menu opsi lagu beneran ada.
    }
}

@Composable
private fun ArtistResultRow(
    artist: ArtistSummaryDto,
    onClick: () -> Unit
) {
    val name = artist.name ?: "Artist"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = artist.thumbnailUrl,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(SurfaceDarkNavy)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimaryWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "Artis",
                fontSize = 12.sp,
                color = TextSecondaryGray
            )
        }
    }
}
