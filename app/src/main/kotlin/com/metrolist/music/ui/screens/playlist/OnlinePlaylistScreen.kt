/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.playlist

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import com.metrolist.music.LocalDownloadUtil
import com.metrolist.music.ui.component.PlaylistActionRow
import com.metrolist.music.ui.component.PlaylistDynamicBackground
import com.metrolist.music.ui.component.PlaylistFloatingTopBar
import com.metrolist.music.ui.component.rememberPlaylistCoverColors
import com.songfy.ui.LocalSongfyBackdrop
import com.songfy.ui.backdrop.backdrops.layerBackdrop
import com.songfy.ui.backdrop.backdrops.rememberLayerBackdrop
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEachReversed
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalListenTogetherManager
import com.metrolist.music.LocalNavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.LocalSyncUtils
import com.metrolist.music.R
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.db.entities.PlaylistSongMap
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.YouTubePlaylistQueue
import com.metrolist.music.ui.component.ExpandableText
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.YouTubeListItem
import com.metrolist.music.ui.menu.YouTubePlaylistMenu
import com.metrolist.music.ui.menu.YouTubeSelectionSongMenu
import com.metrolist.music.ui.menu.YouTubeSongMenu
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.ui.utils.resize
import com.metrolist.music.utils.makeTimeString
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.OnlinePlaylistViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnlinePlaylistScreen(
    navController: NavController,
    viewModel: OnlinePlaylistViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val listenTogetherManager = LocalListenTogetherManager.current
    val isListenTogetherGuest = listenTogetherManager?.let { it.isInRoom && !it.isHost } ?: false
    val coroutineScope = rememberCoroutineScope()

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val songs by viewModel.playlistSongs.collectAsStateWithLifecycle()
    val dbPlaylist by viewModel.dbPlaylist.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val isPodcastPlaylist = viewModel.isPodcastPlaylist

    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)

    val lazyListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var sortType by rememberSaveable { mutableStateOf(com.metrolist.music.constants.PlaylistSongSortType.CUSTOM) }
    var sortDescending by rememberSaveable { mutableStateOf(false) }

    val filteredSongs =
        remember(songs, query, sortType, sortDescending) {
            val base = if (query.text.isEmpty()) {
                songs.mapIndexed { i, s -> i to s }
            } else {
                songs.mapIndexed { i, s -> i to s }.filter {
                    it.second.title.contains(query.text, true) ||
                        it.second.artists.fastAny { a -> a.name.contains(query.text, true) }
                }
            }
            val sorted = when (sortType) {
                com.metrolist.music.constants.PlaylistSongSortType.CUSTOM,
                com.metrolist.music.constants.PlaylistSongSortType.CREATE_DATE,
                com.metrolist.music.constants.PlaylistSongSortType.PLAY_TIME -> base
                com.metrolist.music.constants.PlaylistSongSortType.NAME -> base.sortedBy { it.second.title.lowercase() }
                com.metrolist.music.constants.PlaylistSongSortType.ARTIST -> base.sortedBy {
                    it.second.artists.joinToString(", ") { a -> a.name }.lowercase()
                }
            }
            if (sortDescending) sorted.reversed() else sorted
        }

    var inSelectMode by remember { mutableStateOf(false) }
    val selection =
        remember {
            mutableStateListOf<String>()
        }
    var selectionAnchorSongId by remember { mutableStateOf<String?>(null) }
    val onExitSelectionMode = {
        inSelectMode = false
        selection.clear()
        selectionAnchorSongId = null
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearching) { if (isSearching) focusRequester.requestFocus() }

    LaunchedEffect(filteredSongs) {
        selection.fastForEachReversed { songId ->
            if (filteredSongs.find { it.second.id == songId } == null) {
                selection.remove(songId)
            }
        }

        if (selectionAnchorSongId != null && filteredSongs.none { it.second.id == selectionAnchorSongId }) {
            selectionAnchorSongId = filteredSongs.firstOrNull { it.second.id in selection }?.second?.id
        }
    }

    if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    } else if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    val onlineCoverUrl = playlist?.thumbnail?.resize(1080, 1080)
    val coverPalette = rememberPlaylistCoverColors(onlineCoverUrl)
    val playlistBackdrop = rememberLayerBackdrop {
        drawRect(coverPalette.bottomColor)
        drawContent()
    }
    val context = LocalContext.current

    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(playlistBackdrop),
            ) {
                PlaylistDynamicBackground(
                    coverUrl = onlineCoverUrl,
                    palette = coverPalette,
                )
                LazyColumn(
                    state = lazyListState,
                    contentPadding = LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime).asPaddingValues(),
                ) {
            if (playlist == null || songs.isEmpty()) {
                if (isLoading) {
                    item(key = "loading_placeholder") {
                        Box(
                            modifier =
                                Modifier
                                    .fillParentMaxSize()
                                    .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            ContainedLoadingIndicator()
                        }
                    }
                } else if (error != null) {
                    item(key = "error_placeholder") {
                        Column(
                            modifier =
                                Modifier
                                    .fillParentMaxSize()
                                    .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = error ?: stringResource(R.string.error_unknown),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            androidx.compose.material3.TextButton(onClick = { viewModel.retry() }) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                } else if (!isLoading && songs.isEmpty()) {
                    item(key = "empty_placeholder") {
                        Box(
                            modifier =
                                Modifier
                                    .fillParentMaxSize()
                                    .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.playlist_is_empty),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            } else {
                playlist?.let { playlist ->
                    if (!isSearching) {
                        item(key = "playlist_header") {
                            OnlinePlaylistHeader(
                                playlist = playlist,
                                songs = songs,
                                dbPlaylist = dbPlaylist,
                                coroutineScope = coroutineScope,
                                continuation = viewModel.continuation,
                                isPodcastPlaylist = isPodcastPlaylist,
                                modifier = Modifier.animateItem(),
                            )
                        }
                        if (songs.isNotEmpty()) {
                            item(key = "controls_row") {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                        .animateItem(),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(Color.White.copy(alpha = 0.08f))
                                            .border(0.75.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
                                            .padding(horizontal = 10.dp),
                                    ) {
                                        com.metrolist.music.ui.component.SortHeader(
                                            sortType = sortType,
                                            sortDescending = sortDescending,
                                            onSortTypeChange = { sortType = it },
                                            onSortDescendingChange = { sortDescending = it },
                                            sortTypeText = { type ->
                                                when (type) {
                                                    com.metrolist.music.constants.PlaylistSongSortType.CUSTOM -> R.string.sort_by_custom
                                                    com.metrolist.music.constants.PlaylistSongSortType.CREATE_DATE -> R.string.sort_by_create_date
                                                    com.metrolist.music.constants.PlaylistSongSortType.NAME -> R.string.sort_by_name
                                                    com.metrolist.music.constants.PlaylistSongSortType.ARTIST -> R.string.sort_by_artist
                                                    com.metrolist.music.constants.PlaylistSongSortType.PLAY_TIME -> R.string.sort_by_play_time
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    itemsIndexed(filteredSongs) { index, (_, songItem) ->
                        val onCheckedChange: (Boolean) -> Unit = {
                            if (it) {
                                selection.add(songItem.id)
                            } else {
                                selection.remove(songItem.id)
                            }
                        }

                        Column {
                            YouTubeListItem(
                                item = songItem,
                                isActive = mediaMetadata?.id == songItem.id,
                                isPlaying = isPlaying,
                                isSelected = inSelectMode && songItem.id in selection,
                                modifier =
                                    Modifier
                                        .combinedClickable(
                                            enabled = !hideExplicit || !songItem.explicit,
                                            onClick = {
                                                if (inSelectMode) {
                                                    onCheckedChange(songItem.id !in selection)
                                                } else if (songItem.id == mediaMetadata?.id) {
                                                    playerConnection.togglePlayPause()
                                                } else {
                                                    playerConnection.playQueue(
                                                        YouTubePlaylistQueue(
                                                            playlistId = playlist.id,
                                                            playlistTitle = playlist.title,
                                                            initialSongs = filteredSongs.map { it.second },
                                                            initialContinuation = viewModel.continuation,
                                                            startIndex = index,
                                                        ),
                                                    )
                                                }
                                            },
                                            onLongClick = {
                                                if (!inSelectMode) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    inSelectMode = true
                                                    onCheckedChange(true)
                                                    selectionAnchorSongId = songItem.id
                                                } else {
                                                    val anchorIndex =
                                                        selectionAnchorSongId?.let { anchorSongId ->
                                                            filteredSongs.indexOfFirst { it.second.id == anchorSongId }
                                                        } ?: -1

                                                    if (anchorIndex == -1) {
                                                        onCheckedChange(true)
                                                        selectionAnchorSongId = songItem.id
                                                    } else {
                                                        val range = if (anchorIndex <= index) anchorIndex..index else index..anchorIndex
                                                        for (rangeIndex in range) {
                                                            val rangeSongId = filteredSongs[rangeIndex].second.id
                                                            if (rangeSongId !in selection) {
                                                                selection.add(rangeSongId)
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                        ).animateItem(),
                                trailingContent = {
                                    if (inSelectMode) {
                                        Checkbox(
                                            checked = songItem.id in selection,
                                            onCheckedChange = onCheckedChange,
                                        )
                                    } else {
                                        IconButton(onClick = {
                                            menuState.show {
                                                YouTubeSongMenu(songItem, menuState::dismiss)
                                            }
                                        }) {
                                            Icon(painterResource(R.drawable.more_vert), null)
                                        }
                                    }
                                },
                            )
                            HorizontalDivider(
                                color = Color.White.copy(alpha = 0.09f),
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(start = 72.dp, end = 16.dp),
                            )
                        }
                    }

                    if (isLoadingMore) {
                        item(key = "loading_more") {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                ContainedLoadingIndicator()
                            }
                        }
                    }
                }
            }
        }
            }
        }

        CompositionLocalProvider(LocalSongfyBackdrop provides playlistBackdrop) {
            PlaylistFloatingTopBar(
                modifier = Modifier.align(Alignment.TopCenter),
                title = playlist?.title ?: "",
                showTitle = lazyListState.firstVisibleItemIndex > 0,
                inSelectMode = inSelectMode,
                selectedCount = selection.size,
                allSelected = selection.size == filteredSongs.size && selection.isNotEmpty(),
                isSearching = isSearching,
                query = query,
                onQueryChange = { query = it },
                focusRequester = focusRequester,
                onBackClick = {
                    when {
                        isSearching -> {
                            isSearching = false
                            query = TextFieldValue()
                        }
                        inSelectMode -> onExitSelectionMode()
                        else -> navController.navigateUp()
                    }
                },
                onSelectAllChange = {
                    if (selection.size == filteredSongs.size) {
                        selection.clear()
                    } else {
                        selection.clear()
                        selection.addAll(filteredSongs.map { it.second.id })
                    }
                },
                onSelectionMoreClick = {
                    menuState.show {
                        YouTubeSelectionSongMenu(
                            songSelection =
                                filteredSongs
                                    .filter { it.second.id in selection }
                                    .map { it.second },
                            onDismiss = menuState::dismiss,
                            clearAction = onExitSelectionMode,
                        )
                    }
                },
                onSearchClick = { isSearching = true },
                onShareClick = playlist?.let { pl ->
                    {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, pl.shareLink)
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                    }
                },
                onMoreClick = {
                    playlist?.let { pl ->
                        menuState.show {
                            YouTubePlaylistMenu(
                                playlist = pl,
                                songs = songs,
                                coroutineScope = coroutineScope,
                                onDismiss = menuState::dismiss,
                            )
                        }
                    }
                },
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun OnlinePlaylistHeader(
    playlist: PlaylistItem,
    songs: List<SongItem>,
    dbPlaylist: Playlist?,
    coroutineScope: CoroutineScope,
    continuation: String?,
    isPodcastPlaylist: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val navController = LocalNavController.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val listenTogetherManager = LocalListenTogetherManager.current
    val isListenTogetherGuest = listenTogetherManager?.let { it.isInRoom && !it.isHost } ?: false
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val syncUtils = LocalSyncUtils.current

        Column(
        modifier =
            modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 56.dp, bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier =
                Modifier
                    .size(256.dp)
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(22.dp),
                        spotColor = Color.Black.copy(alpha = 0.45f),
                    ),
            shape = RoundedCornerShape(22.dp),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(playlist.thumbnail?.resize(1080, 1080)).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = playlist.title,
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 32.dp),
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Creator row - channel avatar + name centered
        val author = playlist.author
        if (author != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier =
                    Modifier.combinedClickable(
                        onClick = {
                            if (author.id != null) {
                                navController.navigate("artist/${author.id}")
                            }
                        },
                    ),
            ) {
                if (playlist.authorAvatarUrl != null) {
                    AsyncImage(
                        model = playlist.authorAvatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .size(20.dp)
                                .clip(CircleShape),
                    )
                }
                Text(
                    text = author.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.88f),
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
        }

        // Metadata row - song count, duration
        val totalDuration = songs.sumOf { it.duration ?: 0 }
        val nSongs = pluralStringResource(
            if (isPodcastPlaylist) R.plurals.n_episode else R.plurals.n_song,
            songs.size,
            songs.size,
        )
        val durationText = if (totalDuration > 0) makeTimeString(totalDuration * 1000L) else null
        val metadataText = buildString {
            append(nSongs)
            if (durationText != null) {
                append(" ")
                append(durationText)
            }
        }
        Text(
            text = metadataText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Description
        val description = playlist.description
        if (!description.isNullOrBlank()) {
            ExpandableText(
                text = description,
                modifier = Modifier.padding(horizontal = 32.dp),
                collapsedMaxLines = 3,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action Buttons Row (Shuffle / Like, white Play pill, Menu)
        Spacer(modifier = Modifier.height(16.dp))

        val downloadUtil = LocalDownloadUtil.current
        val context = LocalContext.current
        var onlineDownloadState by remember { mutableIntStateOf(androidx.media3.exoplayer.offline.Download.STATE_STOPPED) }
        LaunchedEffect(songs) {
            if (songs.isEmpty()) return@LaunchedEffect
            downloadUtil.downloads.collect { downloads ->
                onlineDownloadState =
                    if (songs.all { downloads[it.id]?.state == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED }) {
                        androidx.media3.exoplayer.offline.Download.STATE_COMPLETED
                    } else if (songs.all {
                            downloads[it.id]?.state == androidx.media3.exoplayer.offline.Download.STATE_QUEUED ||
                                downloads[it.id]?.state == androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING ||
                                downloads[it.id]?.state == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED
                        }
                    ) {
                        androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING
                    } else {
                        androidx.media3.exoplayer.offline.Download.STATE_STOPPED
                    }
            }
        }

        PlaylistActionRow(
            onShuffleClick = {
                if (!isListenTogetherGuest && songs.isNotEmpty()) {
                    playerConnection.playQueue(
                        YouTubePlaylistQueue(
                            playlistId = playlist.id,
                            playlistTitle = playlist.title,
                            initialSongs = songs.shuffled(),
                            initialContinuation = continuation,
                        ),
                    )
                }
            },
            onPlayClick = {
                if (!isListenTogetherGuest && songs.isNotEmpty()) {
                    playerConnection.playQueue(
                        YouTubePlaylistQueue(
                            playlistId = playlist.id,
                            playlistTitle = playlist.title,
                            initialSongs = songs,
                            initialContinuation = continuation,
                        ),
                    )
                }
            },
            downloadState = onlineDownloadState,
            onDownloadClick = {
                if (onlineDownloadState == androidx.media3.exoplayer.offline.Download.STATE_STOPPED) {
                    songs.forEach { songItem ->
                        downloadUtil.download(songItem.toMediaMetadata())
                    }
                } else {
                    songs.forEach { songItem ->
                        androidx.media3.exoplayer.offline.DownloadService.sendRemoveDownload(
                            context,
                            com.metrolist.music.playback.ExoDownloadService::class.java,
                            songItem.id,
                            false,
                        )
                    }
                }
            },
        )
    }
}
