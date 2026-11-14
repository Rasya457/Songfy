/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.artist

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.songfy.ui.icons.SongfyIcons
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalArtistNameAliases
import com.metrolist.music.LocalListenTogetherManager
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.AppBarHeight
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.ShowArtistDescriptionKey
import com.metrolist.music.constants.ShowArtistSubscriberCountKey
import com.metrolist.music.constants.ShowMonthlyListenersKey
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.component.AlbumGridItem
import com.metrolist.music.ui.component.ExpandableText
import com.metrolist.music.ui.component.HideOnScrollFAB
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.LinkSegment
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.NavigationTitle
import com.metrolist.music.ui.component.SongListItem
import com.metrolist.music.ui.component.YouTubeGridItem
import com.metrolist.music.ui.component.YouTubeListItem
import com.metrolist.music.ui.component.shimmer.ButtonPlaceholder
import com.metrolist.music.ui.component.shimmer.ListItemPlaceHolder
import com.metrolist.music.ui.component.shimmer.ShimmerHost
import com.metrolist.music.ui.component.shimmer.TextPlaceholder
import com.metrolist.music.ui.menu.AlbumMenu
import com.metrolist.music.ui.menu.SongMenu
import com.metrolist.music.ui.menu.YouTubeAlbumMenu
import com.metrolist.music.ui.menu.YouTubeArtistMenu
import com.metrolist.music.ui.menu.YouTubePlaylistMenu
import com.metrolist.music.ui.menu.YouTubeSongMenu
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.ui.utils.fadingEdge
import com.metrolist.music.ui.utils.isScrollingUp
import com.metrolist.music.ui.utils.resize
import com.metrolist.music.utils.ArtistNameAliases
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.ArtistViewModel
import com.valentinilk.shimmer.shimmer
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    navController: NavController,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current ?: return
    val listenTogetherManager = LocalListenTogetherManager.current
    val isGuest = listenTogetherManager?.isInRoom == true && !listenTogetherManager.isHost
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val artistPage = viewModel.artistPage
    val libraryArtist by viewModel.libraryArtist.collectAsStateWithLifecycle()
    val artistNameAliases = LocalArtistNameAliases.current
    val displayArtistName =
        ArtistNameAliases.resolve(
            artistNameAliases,
            viewModel.artistId,
            artistPage?.artist?.title ?: libraryArtist?.artist?.name.orEmpty(),
        ).ifEmpty { null }
    val librarySongs by viewModel.librarySongs.collectAsStateWithLifecycle()
    val libraryAlbums by viewModel.libraryAlbums.collectAsStateWithLifecycle()
    val isChannelSubscribed by viewModel.isChannelSubscribed.collectAsStateWithLifecycle()
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)
    val showArtistDescription by rememberPreference(key = ShowArtistDescriptionKey, defaultValue = true)
    val showArtistSubscriberCount by rememberPreference(key = ShowArtistSubscriberCountKey, defaultValue = true)
    val showMonthlyListeners by rememberPreference(key = ShowMonthlyListenersKey, defaultValue = true)

    val lazyListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showLocal by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current

    // Calculate the offset value outside of the offset lambda
    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val headerOffset =
        with(density) {
            -(systemBarsTopPadding + AppBarHeight).roundToPx()
        }

    val transparentAppBar by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset < 100
        }
    }

    val distinctItemsBySection = remember(artistPage?.sections) {
        artistPage?.sections?.map { section ->
            section.items.distinctBy { it.id }
        } ?: emptyList()
    }

    LaunchedEffect(libraryArtist) {
        // always show local page for local artists. Show local page remote artist when offline
        showLocal = libraryArtist?.artist?.isLocal == true
    }

    val onSurface = MaterialTheme.colorScheme.onSurface
    val featuredRelease =
        remember(artistPage?.sections) {
            artistPage?.sections
                ?.flatMap { section -> section.items.filterIsInstance<AlbumItem>().map { it to section.title } }
                ?.maxByOrNull { it.first.year ?: 0 }
        }
    val firstSongSectionIndex =
        remember(artistPage?.sections) {
            artistPage?.sections?.indexOfFirst { (it.items.firstOrNull() as? SongItem)?.album != null } ?: -1
        }
    val canPlayAll =
        !isGuest && (
            (showLocal && librarySongs.isNotEmpty()) ||
                (
                    !showLocal && artistPage?.sections?.any {
                        (it.items.firstOrNull() as? SongItem)?.album != null
                    } == true
                )
        )
    val onPlayAllClick: () -> Unit = {
        if (!isGuest) {
            if (showLocal) {
                if (librarySongs.isNotEmpty()) {
                    playerConnection.playQueue(
                        ListQueue(
                            title = displayArtistName ?: "Unknown Artist",
                            items = librarySongs.map { it.toMediaItem() },
                        ),
                    )
                }
            } else if (artistPage != null) {
                val songSection =
                    artistPage.sections.find { section ->
                        (section.items.firstOrNull() as? SongItem)?.album != null
                    }

                val moreEndpoint = songSection?.moreEndpoint
                if (moreEndpoint != null) {
                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        val result = YouTube.artistItems(moreEndpoint).getOrNull()
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            if (result != null && result.items.isNotEmpty()) {
                                val songs = result.items.filterIsInstance<SongItem>().map { it.toMediaItem() }
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = displayArtistName ?: artistPage.artist.title,
                                        items = songs,
                                    ),
                                )
                            } else {
                                // Fallback to loaded items
                                val songs = songSection.items.filterIsInstance<SongItem>().map { it.toMediaItem() }
                                if (songs.isNotEmpty()) {
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = displayArtistName ?: artistPage.artist.title,
                                            items = songs,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                } else if (songSection != null) {
                    // Use loaded items if no more endpoint
                    val songs = songSection.items.filterIsInstance<SongItem>().map { it.toMediaItem() }
                    playerConnection.playQueue(
                        ListQueue(
                            title = displayArtistName ?: artistPage.artist.title,
                            items = songs,
                        ),
                    )
                } else {
                    // Fallback to shuffle endpoint (stripped) if no song section found
                    val shuffleEndpoint = artistPage.artist.shuffleEndpoint
                    if (shuffleEndpoint != null) {
                        val endpoint =
                            if (shuffleEndpoint.playlistId != null) {
                                WatchEndpoint(
                                    playlistId = shuffleEndpoint.playlistId,
                                    params = null, // Remove shuffle params to play in order
                                    videoId = null, // Ensure videoId is null to start from beginning of playlist
                                )
                            } else {
                                shuffleEndpoint
                            }
                        playerConnection.playQueue(YouTubeQueue(endpoint))
                    }
                }
            }
        }
    }
    val onShuffleClick: () -> Unit = {
        if (!isGuest) {
            if (showLocal) {
                if (librarySongs.isNotEmpty()) {
                    playerConnection.playQueue(
                        ListQueue(
                            title = displayArtistName ?: "Unknown Artist",
                            items = librarySongs.shuffled().map { it.toMediaItem() },
                        ),
                    )
                }
            } else {
                artistPage?.artist?.shuffleEndpoint?.let { playerConnection.playQueue(YouTubeQueue(it)) }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) {
            if (artistPage == null && !showLocal) {
                item(key = "shimmer") {
                    ShimmerHost(
                        modifier =
                            Modifier
                                .offset {
                                    IntOffset(x = 0, y = headerOffset)
                                },
                    ) {
                        // Artist Image Placeholder
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1.1f),
                        ) {
                            Spacer(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .shimmer()
                                        .background(MaterialTheme.colorScheme.onSurface)
                                        .fadingEdge(
                                            top = systemBarsTopPadding + AppBarHeight,
                                            bottom = 200.dp,
                                        ),
                            )
                        }
                        // Artist Name and Controls Section
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                        ) {
                            // Artist Name Placeholder
                            TextPlaceholder(
                                height = 36.dp,
                                modifier =
                                    Modifier
                                        .fillMaxWidth(0.7f)
                                        .padding(bottom = 16.dp),
                            )

                            // Buttons Row Placeholder
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Subscribe Button Placeholder
                                ButtonPlaceholder(
                                    modifier =
                                        Modifier
                                            .width(120.dp)
                                            .height(40.dp),
                                )

                                Spacer(modifier = Modifier.weight(1f))

                                // Right side buttons
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // Radio Button Placeholder
                                    ButtonPlaceholder(
                                        modifier =
                                            Modifier
                                                .width(100.dp)
                                                .height(40.dp),
                                    )

                                    // Shuffle Button Placeholder
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(48.dp)
                                                .shimmer()
                                                .background(
                                                    MaterialTheme.colorScheme.onSurface,
                                                    RoundedCornerShape(24.dp),
                                                ),
                                    )
                                }
                            }
                        }
                        // Songs List Placeholder
                        repeat(6) {
                            ListItemPlaceHolder()
                        }
                    }
                }
            } else {
                item(key = "header") {
                    val thumbnail = artistPage?.artist?.thumbnail ?: libraryArtist?.artist?.thumbnailUrl

                    Box {
                        // Artist Image with offset
                        if (thumbnail != null) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .offset {
                                            IntOffset(x = 0, y = headerOffset)
                                        },
                            ) {
                                AsyncImage(
                                    model = thumbnail.resize(1200, 1200),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.TopCenter)
                                            .fadingEdge(
                                                bottom = 200.dp,
                                            ),
                                )
                            }
                        }

                        // Artist Name and Controls Section - positioned at bottom of image
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        top =
                                            if (thumbnail != null) {
                                                LocalResources.current.displayMetrics.widthPixels.let { screenWidth ->
                                                    with(density) {
                                                        ((screenWidth / 1.2f) - 144).toDp()
                                                    }
                                                }
                                            } else {
                                                16.dp
                                            },
                                    ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = displayArtistName ?: "Unknown",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 34.sp,
                                textAlign = TextAlign.Center,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .padding(bottom = 16.dp),
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ArtistControlButton(
                                    size = 52.dp,
                                    background = onSurface.copy(alpha = 0.12f),
                                    onClick = onShuffleClick,
                                ) {
                                    Icon(
                                        SongfyIcons.Shuffle,
                                        contentDescription = "Shuffle",
                                        tint = onSurface,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                                ArtistControlButton(
                                    size = 76.dp,
                                    background = onSurface,
                                    onClick = onPlayAllClick,
                                ) {
                                    Icon(
                                        SongfyIcons.Play,
                                        contentDescription = "Play",
                                        tint = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.size(36.dp),
                                    )
                                }
                                ArtistControlButton(
                                    size = 52.dp,
                                    background = onSurface.copy(alpha = 0.12f),
                                    onClick = { viewModel.toggleChannelSubscription() },
                                ) {
                                    Icon(
                                        painter = painterResource(if (isChannelSubscribed) R.drawable.star else R.drawable.star_border),
                                        contentDescription = stringResource(if (isChannelSubscribed) R.string.subscribed else R.string.subscribe),
                                        tint = if (isChannelSubscribed) MaterialTheme.colorScheme.primary else onSurface,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }

                // Featured Release card
                if (!showLocal) {
                    featuredRelease?.let { (album, sectionTitle) ->
                        item(key = "featured_release") {
                            val shape = RoundedCornerShape(28.dp)
                            val type =
                                stringResource(
                                    if (sectionTitle.contains("single", ignoreCase = true)) {
                                        R.string.artist_type_single
                                    } else {
                                        R.string.artist_type_album
                                    },
                                )
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .padding(bottom = 24.dp)
                                        .clip(shape)
                                        .background(onSurface.copy(alpha = 0.08f))
                                        .border(0.5.dp, onSurface.copy(alpha = 0.10f), shape)
                                        .clickable { navController.navigate("album/${album.id}") }
                                        .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AsyncImage(
                                    model = album.thumbnail.resize(300, 300),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier =
                                        Modifier
                                            .size(100.dp)
                                            .clip(RoundedCornerShape(16.dp)),
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = listOfNotNull(type, album.year?.toString()).joinToString(" \u2022 "),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                    Text(
                                        text = album.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = stringResource(R.string.artist_latest_release),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }

                // About Artist Section
                if (!showLocal && (showArtistDescription || showArtistSubscriberCount || showMonthlyListeners)) {
                    val description = artistPage?.description
                    val descriptionRuns = artistPage?.descriptionRuns
                    val subscriberCount = artistPage?.subscriberCountText
                    val monthlyListeners = artistPage?.monthlyListenerCount

                    if ((showArtistDescription && !description.isNullOrEmpty()) ||
                        (showArtistSubscriberCount && !subscriberCount.isNullOrEmpty()) ||
                        (showMonthlyListeners && !monthlyListeners.isNullOrEmpty())
                    ) {
                        item(key = "about_artist") {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .padding(bottom = 16.dp)
                                        .animateItem(),
                            ) {
                                if (showArtistDescription && (!description.isNullOrEmpty() || !descriptionRuns.isNullOrEmpty())) {
                                    Text(
                                        text = stringResource(R.string.about_artist),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 8.dp),
                                    )
                                }

                                if (showArtistSubscriberCount && !subscriberCount.isNullOrEmpty()) {
                                    Text(
                                        text = subscriberCount,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 4.dp),
                                    )
                                }

                                if (showMonthlyListeners && !monthlyListeners.isNullOrEmpty()) {
                                    Text(
                                        text = monthlyListeners,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier =
                                            Modifier.padding(
                                                bottom =
                                                    if (showArtistDescription &&
                                                        !description.isNullOrEmpty()
                                                    ) {
                                                        8.dp
                                                    } else {
                                                        0.dp
                                                    },
                                            ),
                                    )
                                }

                                if (showArtistDescription && (!description.isNullOrEmpty() || !descriptionRuns.isNullOrEmpty())) {
                                    ExpandableText(
                                        text = description.orEmpty(),
                                        runs =
                                            descriptionRuns?.map {
                                                LinkSegment(
                                                    text = it.text,
                                                    url = it.navigationEndpoint?.urlEndpoint?.url,
                                                )
                                            },
                                        collapsedMaxLines = 3,
                                    )
                                }
                            }
                        }
                    }
                }

                // Show loading shimmer for sections when API hasn't returned yet
                if (artistPage == null && !showLocal) {
                    item(key = "section_shimmer") {
                        ShimmerHost {
                            repeat(4) { ListItemPlaceHolder() }
                        }
                    }
                }

                if (showLocal) {
                    if (librarySongs.isNotEmpty()) {
                        item(key = "local_songs_title") {
                            NavigationTitle(
                                title = stringResource(R.string.songs),
                                modifier = Modifier.animateItem(),
                                onClick = {
                                    navController.navigate("artist/${viewModel.artistId}/songs")
                                },
                            )
                        }

                        val filteredLibrarySongs =
                            if (hideExplicit) {
                                librarySongs.filter { !it.song.explicit }
                            } else {
                                librarySongs
                            }
                        itemsIndexed(
                            items = filteredLibrarySongs,
                            key = { index, item -> "local_song_${item.id}_$index" },
                        ) { index, song ->
                            SongListItem(
                                song = song,
                                showInLibraryIcon = true,
                                isActive = song.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                trailingContent = {
                                    IconButton(
                                        onClick = {
                                            menuState.show {
                                                SongMenu(
                                                    originalSong = song,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.more_vert),
                                            contentDescription = null,
                                        )
                                    }
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                if (!isGuest) {
                                                    if (song.id == mediaMetadata?.id) {
                                                        playerConnection.togglePlayPause()
                                                    } else {
                                                        playerConnection.playQueue(
                                                            ListQueue(
                                                                title = displayArtistName ?: "Unknown Artist",
                                                                items = librarySongs.map { it.toMediaItem() },
                                                                startIndex = index,
                                                            ),
                                                        )
                                                    }
                                                }
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.show {
                                                    SongMenu(
                                                        originalSong = song,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            },
                                        ).animateItem(),
                            )
                        }
                    }

                    if (libraryAlbums.isNotEmpty()) {
                        item(key = "local_albums_title") {
                            NavigationTitle(
                                title = stringResource(R.string.albums),
                                modifier = Modifier.animateItem(),
                                onClick = {
                                    navController.navigate("artist/${viewModel.artistId}/albums")
                                },
                            )
                        }

                        item(key = "local_albums_list") {
                            val filteredLibraryAlbums =
                                if (hideExplicit) {
                                    libraryAlbums.filter { !it.album.explicit }
                                } else {
                                    libraryAlbums
                                }
                            LazyRow(
                                contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues(),
                            ) {
                                items(
                                    items = filteredLibraryAlbums,
                                    key = { "local_album_${it.id}_${filteredLibraryAlbums.indexOf(it)}" },
                                ) { album ->
                                    AlbumGridItem(
                                        album = album,
                                        isActive = mediaMetadata?.album?.id == album.id,
                                        isPlaying = isPlaying,
                                        coroutineScope = coroutineScope,
                                        modifier =
                                            Modifier
                                                .combinedClickable(
                                                    onClick = {
                                                        navController.navigate("album/${album.id}")
                                                    },
                                                    onLongClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        menuState.show {
                                                            AlbumMenu(
                                                                originalAlbum = album,
                                                                onDismiss = menuState::dismiss,
                                                            )
                                                        }
                                                    },
                                                ).animateItem(),
                                    )
                                }
                            }
                        }
                    }
                } else {
                    artistPage?.sections?.forEachIndexed { index, section ->
                        if (section.items.isNotEmpty()) {
                            item(key = "section_${section.title}") {
                                NavigationTitle(
                                    title = if (index == firstSongSectionIndex) stringResource(R.string.artist_top_songs) else section.title,
                                    modifier = Modifier.animateItem(),
                                    onClick =
                                        section.moreEndpoint?.let {
                                            {
                                                navController.navigate(
                                                    "artist/${viewModel.artistId}/items?browseId=${it.browseId}?params=${it.params}",
                                                )
                                            }
                                        },
                                )
                            }
                        }

                        if ((section.items.firstOrNull() as? SongItem)?.album != null) {
                            items(
                                items = distinctItemsBySection.getOrNull(index) ?: section.items,
                                key = { "youtube_song_${it.id}" },
                            ) { song ->
                                YouTubeListItem(
                                    item = song as SongItem,
                                    isActive = mediaMetadata?.id == song.id,
                                    isPlaying = isPlaying,
                                    trailingContent = {
                                        IconButton(
                                            onClick = {
                                                menuState.show {
                                                    YouTubeSongMenu(
                                                        song = song,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            },
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.more_vert),
                                                contentDescription = null,
                                            )
                                        }
                                    },
                                    modifier =
                                        Modifier
                                            .combinedClickable(
                                                onClick = {
                                                    if (!isGuest) {
                                                        if (song.id == mediaMetadata?.id) {
                                                            playerConnection.togglePlayPause()
                                                        } else {
                                                            playerConnection.playQueue(
                                                                YouTubeQueue(
                                                                    WatchEndpoint(videoId = song.id),
                                                                    song.toMediaMetadata(),
                                                                ),
                                                            )
                                                        }
                                                    }
                                                },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    menuState.show {
                                                        YouTubeSongMenu(
                                                            song = song,
                                                            onDismiss = menuState::dismiss,
                                                        )
                                                    }
                                                },
                                            ).animateItem(),
                                )
                            }
                        } else {
                            item(key = "section_list_${section.title}") {
                                LazyRow(
                                    contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues(),
                                ) {
                                    items(
                                        items = distinctItemsBySection.getOrNull(index) ?: section.items,
                                        key = { "youtube_album_${it.id}" },
                                    ) { item ->
                                        YouTubeGridItem(
                                            item = item,
                                            isActive =
                                                when (item) {
                                                    is SongItem -> mediaMetadata?.id == item.id
                                                    is AlbumItem -> mediaMetadata?.album?.id == item.id
                                                    else -> false
                                                },
                                            isPlaying = isPlaying,
                                            coroutineScope = coroutineScope,
                                            thumbnailRatio = 1f, // Use square thumbnails for all items in horizontal scroll
                                            modifier =
                                                Modifier
                                                    .combinedClickable(
                                                        onClick = {
                                                            when (item) {
                                                                is SongItem -> {
                                                                    if (!isGuest) {
                                                                        playerConnection.playQueue(
                                                                            YouTubeQueue(
                                                                                WatchEndpoint(videoId = item.id),
                                                                                item.toMediaMetadata(),
                                                                            ),
                                                                        )
                                                                    }
                                                                }

                                                                is AlbumItem -> {
                                                                    navController.navigate("album/${item.id}")
                                                                }

                                                                is ArtistItem -> {
                                                                    navController.navigate("artist/${item.id}")
                                                                }

                                                                is PlaylistItem -> {
                                                                    navController.navigate("online_playlist/${item.id}")
                                                                }

                                                                is PodcastItem -> {
                                                                    navController.navigate("online_podcast/${item.id}")
                                                                }

                                                                is EpisodeItem -> {
                                                                    if (!isGuest) {
                                                                        playerConnection.playQueue(
                                                                            YouTubeQueue(
                                                                                WatchEndpoint(videoId = item.id),
                                                                                item.toMediaMetadata(),
                                                                            ),
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        },
                                                        onLongClick = {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            menuState.show {
                                                                when (item) {
                                                                    is SongItem -> {
                                                                        YouTubeSongMenu(
                                                                            song = item,
                                                                            onDismiss = menuState::dismiss,
                                                                        )
                                                                    }

                                                                    is AlbumItem -> {
                                                                        YouTubeAlbumMenu(
                                                                            albumItem = item,
                                                                            onDismiss = menuState::dismiss,
                                                                        )
                                                                    }

                                                                    is ArtistItem -> {
                                                                        YouTubeArtistMenu(
                                                                            artist = item,
                                                                            onDismiss = menuState::dismiss,
                                                                        )
                                                                    }

                                                                    is PlaylistItem -> {
                                                                        YouTubePlaylistMenu(
                                                                            playlist = item,
                                                                            coroutineScope = coroutineScope,
                                                                            onDismiss = menuState::dismiss,
                                                                        )
                                                                    }

                                                                    is PodcastItem -> {
                                                                        YouTubePlaylistMenu(
                                                                            playlist = item.asPlaylistItem(),
                                                                            coroutineScope = coroutineScope,
                                                                            onDismiss = menuState::dismiss,
                                                                        )
                                                                    }

                                                                    is EpisodeItem -> {
                                                                        YouTubeSongMenu(
                                                                            song = item.asSongItem(),
                                                                            onDismiss = menuState::dismiss,
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        },
                                                    ).animateItem(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val showLocalFab = librarySongs.isNotEmpty() && libraryArtist?.artist?.isLocal != true

        // Library/Local Toggle FAB
        HideOnScrollFAB(
            visible = showLocalFab,
            lazyListState = lazyListState,
            icon = if (showLocal) R.drawable.language else R.drawable.library_music,
            onClick = {
                showLocal = showLocal.not()
                if (!showLocal && artistPage == null) viewModel.fetchArtistsFromYTM()
            },
        )


        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                    .align(Alignment.BottomCenter),
        )
    }

    TopAppBar(
        title = { if (!transparentAppBar) Text(displayArtistName.orEmpty()) },
        navigationIcon = {
            ArtistControlButton(
                size = 40.dp,
                background = onSurface.copy(alpha = 0.12f),
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                    tint = onSurface,
                )
            }
        },
        actions = {
            Row(
                modifier =
                    Modifier
                        .padding(end = 8.dp)
                        .clip(CircleShape)
                        .background(onSurface.copy(alpha = 0.12f)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        viewModel.artistPage?.artist?.shareLink?.let { link ->
                            val songfyLink = com.metrolist.music.utils.SongfyLinks.fromYouTubeUrl(link)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Artist Link", songfyLink)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, R.string.link_copied, Toast.LENGTH_SHORT).show()
                        }
                    },
                ) {
                    Icon(
                        SongfyIcons.Share,
                        contentDescription = null,
                        tint = onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
                artistPage?.artist?.let { artist ->
                    IconButton(
                        onClick = {
                            menuState.show {
                                YouTubeArtistMenu(
                                    artist = artist,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    ) {
                        Icon(
                            SongfyIcons.Account,
                            contentDescription = null,
                            tint = onSurface,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        },
        colors =
            if (transparentAppBar) {
                TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            } else {
                TopAppBarDefaults.topAppBarColors()
            },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistControlButton(
    size: Dp,
    background: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(background)
                .border(0.5.dp, Color.White.copy(alpha = 0.10f), CircleShape)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
