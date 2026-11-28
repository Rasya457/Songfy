/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.playlist

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import com.metrolist.music.ui.component.PlaylistActionRow
import com.metrolist.music.ui.component.PlaylistDynamicBackground
import com.metrolist.music.ui.component.PlaylistFloatingTopBar
import com.metrolist.music.ui.component.formatPlaylistUpdatedText
import com.metrolist.music.ui.component.rememberPlaylistCoverColors
import com.songfy.ui.LocalSongfyBackdrop
import com.songfy.ui.backdrop.backdrops.layerBackdrop
import com.songfy.ui.backdrop.backdrops.rememberLayerBackdrop
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastForEachReversed
import androidx.compose.ui.util.fastSumBy
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalDownloadUtil
import com.metrolist.music.LocalNavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.LocalSyncUtils
import com.metrolist.music.R
import com.metrolist.music.constants.DarkModeKey
import com.metrolist.music.constants.PlaylistEditLockKey
import com.metrolist.music.constants.PlaylistSongSortDescendingKey
import com.metrolist.music.constants.PlaylistSongSortType
import com.metrolist.music.constants.PlaylistSongSortTypeKey
import com.metrolist.music.constants.SwipeToRemoveSongKey
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.db.entities.PlaylistSong
import com.metrolist.music.db.entities.PlaylistSongMap
import com.metrolist.music.extensions.move
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.ExoDownloadService
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.component.ActionPromptDialog
import com.metrolist.music.ui.component.DefaultDialog
import com.metrolist.music.ui.component.DraggableScrollbar
import com.metrolist.music.ui.component.EmptyPlaceholder
import com.metrolist.music.ui.component.ExpandableText
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.OverlayEditButton
import com.metrolist.music.ui.component.SongListItem
import com.metrolist.music.ui.component.SortHeader
import com.metrolist.music.ui.component.TextFieldDialog
import com.metrolist.music.ui.component.YouTubeListItem
import com.metrolist.music.ui.menu.CustomThumbnailMenu
import com.metrolist.music.ui.menu.LocalPlaylistMenu
import com.metrolist.music.ui.menu.SelectionSongMenu
import com.metrolist.music.ui.menu.SongMenu
import com.metrolist.music.ui.screens.settings.DarkMode
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.makeTimeString
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.utils.reportException
import com.metrolist.music.viewmodels.LocalPlaylistViewModel
import com.yalantis.ucrop.UCrop
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalDateTime

@SuppressLint("RememberReturnType")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LocalPlaylistScreen(
    navController: NavController,
    viewModel: LocalPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val songs by viewModel.playlistSongs.collectAsStateWithLifecycle()
    val onlinePlaylist by viewModel.onlinePlaylist.collectAsStateWithLifecycle()
    val mutableSongs = remember { mutableStateListOf<PlaylistSong>() }
    val playlistLength =
        remember(songs) {
            songs.fastSumBy { it.song.song.duration }
        }
    val (sortType, onSortTypeChange) =
        rememberEnumPreference(
            PlaylistSongSortTypeKey,
            PlaylistSongSortType.CUSTOM,
        )
    val (sortDescending, onSortDescendingChange) =
        rememberPreference(
            PlaylistSongSortDescendingKey,
            true,
        )
    var locked by rememberPreference(PlaylistEditLockKey, defaultValue = true)

    val coroutineScope = rememberCoroutineScope()
    val syncUtils = LocalSyncUtils.current
    val snackbarHostState = remember { SnackbarHostState() }

    var isSearching by rememberSaveable { mutableStateOf(false) }

    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }

    val filteredSongs =
        remember(songs, query) {
            if (query.text.isEmpty()) {
                songs
            } else {
                songs.filter { song ->
                    song.song.song.title
                        .contains(query.text, ignoreCase = true) ||
                        song.song.artists
                            .fastAny { it.name.contains(query.text, ignoreCase = true) }
                }
            }
        }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    var inSelectMode by remember { mutableStateOf(false) }
    val selection =
        remember {
            mutableStateListOf<Int>()
        }
    var selectionAnchorMapId by remember { mutableStateOf<Int?>(null) }
    val onExitSelectionMode = {
        inSelectMode = false
        selection.clear()
        selectionAnchorMapId = null
    }

    if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    } else if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    val downloadUtil = LocalDownloadUtil.current
    var downloadState by remember {
        mutableIntStateOf(Download.STATE_STOPPED)
    }

    val editable: Boolean = playlist?.playlist?.isEditable == true

    var onlineSearchResults by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    var isSearchingOnline by remember { mutableStateOf(false) }
    val recentlyAddedIds = remember { mutableStateListOf<String>() }
    val existingSongIds = remember(songs) { songs.mapTo(HashSet()) { it.song.id } }

    LaunchedEffect(isSearching, query.text, editable) {
        val trimmed = query.text.trim()
        if (!isSearching || !editable || trimmed.length < 2) {
            onlineSearchResults = emptyList()
            isSearchingOnline = false
            return@LaunchedEffect
        }
        isSearchingOnline = true
        kotlinx.coroutines.delay(350L)
        val results = withContext(Dispatchers.IO) {
            YouTube.search(trimmed, YouTube.SearchFilter.FILTER_SONG)
                .getOrNull()
                ?.items
                ?.filterIsInstance<SongItem>()
                ?.take(15)
                .orEmpty()
        }
        onlineSearchResults = results
        isSearchingOnline = false
    }

    LaunchedEffect(songs) {
        selection.fastForEachReversed { mapId ->
            if (songs.find { it.map.id == mapId } == null) {
                selection.remove(Integer.valueOf(mapId))
            }
        }

        if (selectionAnchorMapId != null && songs.none { it.map.id == selectionAnchorMapId }) {
            selectionAnchorMapId = songs.firstOrNull { it.map.id in selection }?.map?.id
        }
    }

    LaunchedEffect(songs) {
        mutableSongs.apply {
            clear()
            addAll(songs)
        }
        if (songs.isEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (songs.all { downloads[it.song.id]?.state == Download.STATE_COMPLETED }) {
                    Download.STATE_COMPLETED
                } else if (songs.all {
                        downloads[it.song.id]?.state == Download.STATE_QUEUED ||
                            downloads[it.song.id]?.state == Download.STATE_DOWNLOADING ||
                            downloads[it.song.id]?.state == Download.STATE_COMPLETED
                    }
                ) {
                    Download.STATE_DOWNLOADING
                } else {
                    Download.STATE_STOPPED
                }
        }
    }

    var showEditDialog by remember {
        mutableStateOf(false)
    }

    if (showEditDialog) {
        playlist?.playlist?.let { playlistEntity ->
            TextFieldDialog(
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.edit),
                        contentDescription = null,
                    )
                },
                title = { Text(text = stringResource(R.string.edit_playlist)) },
                onDismiss = { showEditDialog = false },
                initialTextFieldValue =
                    TextFieldValue(
                        playlistEntity.name,
                        TextRange(playlistEntity.name.length),
                    ),
                onDone = { name ->
                    database.query {
                        update(
                            playlistEntity.copy(
                                name = name,
                                lastUpdateTime = LocalDateTime.now(),
                            ),
                        )
                    }
                    viewModel.viewModelScope.launch(Dispatchers.IO) {
                        playlistEntity.browseId?.let { YouTube.renamePlaylist(it, name) }
                    }
                },
            )
        }
    }

    var showRemoveDownloadDialog by remember {
        mutableStateOf(false)
    }

    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            content = {
                Text(
                    text =
                        stringResource(
                            R.string.remove_download_playlist_confirm,
                            playlist?.playlist!!.name,
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(
                    onClick = { showRemoveDownloadDialog = false },
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                        if (!editable) {
                            database.transaction {
                                playlist?.id?.let { clearPlaylist(it) }
                            }
                        }
                        songs.forEach { song ->
                            DownloadService.sendRemoveDownload(
                                context,
                                ExoDownloadService::class.java,
                                song.song.id,
                                false,
                            )
                        }
                    },
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    var showDeletePlaylistDialog by remember {
        mutableStateOf(false)
    }
    if (showDeletePlaylistDialog) {
        DefaultDialog(
            onDismiss = { showDeletePlaylistDialog = false },
            content = {
                Text(
                    text =
                        stringResource(
                            R.string.delete_playlist_confirm,
                            playlist?.playlist!!.name,
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(
                    onClick = {
                        showDeletePlaylistDialog = false
                    },
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        showDeletePlaylistDialog = false
                        database.query {
                            playlist?.let { delete(it.playlist) }
                        }
                        viewModel.viewModelScope.launch(Dispatchers.IO) {
                            playlist?.playlist?.browseId?.let { YouTube.deletePlaylist(it) }
                        }
                        navController.popBackStack()
                    },
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    val headerItems = 2
    val lazyListState = rememberLazyListState()
    var dragInfo by remember {
        mutableStateOf<Pair<Int, Int>?>(null)
    }
    val reorderableState =
        rememberReorderableLazyListState(
            lazyListState = lazyListState,
            scrollThresholdPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) { from, to ->
            if (to.index >= headerItems && from.index >= headerItems) {
                val currentDragInfo = dragInfo
                dragInfo =
                    if (currentDragInfo == null) {
                        (from.index - headerItems) to (to.index - headerItems)
                    } else {
                        currentDragInfo.first to (to.index - headerItems)
                    }

                mutableSongs.move(from.index - headerItems, to.index - headerItems)
            }
        }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            dragInfo?.let { (from, to) ->
                viewModel.viewModelScope.launch(Dispatchers.IO) {
                    database.withTransaction {
                        move(viewModel.playlistId, from, to)
                    }

                    // Sync order with YT Music
                    val browseId = viewModel.playlist.value?.playlist?.browseId
                    if (browseId != null) {
                        val playlistSongMap = database.playlistSongMaps(viewModel.playlistId, 0)
                        val setVideoId = playlistSongMap.getOrNull(to)?.setVideoId
                        val successorSetVideoId = playlistSongMap.getOrNull(to + 1)?.setVideoId

                        if (setVideoId != null) {
                            YouTube.moveSongPlaylist(
                                browseId,
                                setVideoId,
                                successorSetVideoId,
                            )
                        }
                    }
                }

                dragInfo = null
            }
        }
    }

    val showTopBarTitle by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0
        }
    }

    val coverUrl = playlist?.playlist?.thumbnailUrl ?: playlist?.thumbnails?.firstOrNull()
    val coverPalette = rememberPlaylistCoverColors(coverUrl)
    val playlistBackdrop = rememberLayerBackdrop {
        drawRect(coverPalette.bottomColor)
        drawContent()
    }
    val playlistDownloadUtil = LocalDownloadUtil.current
    var playlistDownloadState by remember { mutableIntStateOf(Download.STATE_STOPPED) }
    LaunchedEffect(songs) {
        if (songs.isEmpty()) {
            playlistDownloadState = Download.STATE_STOPPED
            return@LaunchedEffect
        }
        playlistDownloadUtil.downloads.collect { downloads ->
            playlistDownloadState =
                if (songs.all { downloads[it.song.id]?.state == Download.STATE_COMPLETED }) {
                    Download.STATE_COMPLETED
                } else if (songs.all {
                        downloads[it.song.id]?.state == Download.STATE_QUEUED ||
                            downloads[it.song.id]?.state == Download.STATE_DOWNLOADING ||
                            downloads[it.song.id]?.state == Download.STATE_COMPLETED
                    }
                ) {
                    Download.STATE_DOWNLOADING
                } else {
                    Download.STATE_STOPPED
                }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(playlistBackdrop),
            ) {
                PlaylistDynamicBackground(
                    coverUrl = coverUrl,
                    palette = coverPalette,
                )
                LazyColumn(
                    state = lazyListState,
                    contentPadding = LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime).asPaddingValues(),
                ) {
            playlist?.let { playlist ->
                if (!isSearching && playlist.songCount == 0 && playlist.playlist.remoteSongCount == 0) {
                    item(key = "empty_placeholder") {
                        EmptyPlaceholder(
                            icon = R.drawable.music_note,
                            text = stringResource(R.string.playlist_is_empty),
                            modifier = Modifier.animateItem(),
                        )
                    }
                } else if (!isSearching) {
                    item(key = "playlist_header") {
                        LocalPlaylistHeader(
                            playlist = playlist,
                            songs = songs,
                            onlinePlaylist = onlinePlaylist,
                            onShowEditDialog = { showEditDialog = true },
                            onShowRemoveDownloadDialog = { showRemoveDownloadDialog = true },
                            onshowDeletePlaylistDialog = { showDeletePlaylistDialog = true },
                            onStartSearch = { isSearching = true },
                            snackbarHostState = snackbarHostState,
                            modifier = Modifier.animateItem(),
                        )
                    }

                    item(key = "controls_row") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .animateItem(),
                        ) {
                            SortHeader(
                                sortType = sortType,
                                sortDescending = sortDescending,
                                onSortTypeChange = onSortTypeChange,
                                onSortDescendingChange = onSortDescendingChange,
                                sortTypeText = { sortType ->
                                    when (sortType) {
                                        PlaylistSongSortType.CUSTOM -> R.string.sort_by_custom
                                        PlaylistSongSortType.CREATE_DATE -> R.string.sort_by_create_date
                                        PlaylistSongSortType.NAME -> R.string.sort_by_name
                                        PlaylistSongSortType.ARTIST -> R.string.sort_by_artist
                                        PlaylistSongSortType.PLAY_TIME -> R.string.sort_by_play_time
                                    }
                                },
                            )

                            if (editable) {
                                val unlocked = !locked
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            if (unlocked) {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                                            } else {
                                                Color.White.copy(alpha = 0.10f)
                                            },
                                        )
                                        .clickable { locked = !locked }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(if (locked) R.drawable.lock else R.drawable.lock_open),
                                        contentDescription = null,
                                        tint = if (unlocked) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.88f),
                                        modifier = Modifier.size(15.dp),
                                    )
                                    Text(
                                        text = if (locked) "Terkunci" else "Atur",
                                        color = if (unlocked) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.88f),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val displayedSongs = if (isSearching) filteredSongs else mutableSongs

            itemsIndexed(
                items = displayedSongs,
                key = { _, song -> song.map.id },
            ) { index, song ->
                ReorderableItem(
                    state = reorderableState,
                    key = song.map.id,
                ) {
                    val currentItem by rememberUpdatedState(song)

                    fun deleteFromPlaylist() {
                        // Capture values before deletion — DB entry will be gone afterwards
                        val browseId = playlist?.playlist?.browseId
                        val setVideoId = currentItem.map.setVideoId
                        val songId = currentItem.map.songId
                        val playlistId = currentItem.map.playlistId

                        database.transaction {
                            move(playlistId, currentItem.map.position, Int.MAX_VALUE)
                            delete(currentItem.map.copy(position = Int.MAX_VALUE))
                        }

                        if (browseId != null) {
                            syncUtils.scheduleRemoveFromPlaylist(
                                browseId,
                                songId,
                                playlistId
                            ) {
                                setVideoId
                            }
                        }
                    }

                    val swipeRemoveEnabled by rememberPreference(SwipeToRemoveSongKey, defaultValue = false)
                    val dismissBoxState =
                        rememberSwipeToDismissBoxState(
                            positionalThreshold = { totalDistance -> totalDistance },
                        )
                    var processedDismiss by remember { mutableStateOf(false) }
                    LaunchedEffect(dismissBoxState.currentValue) {
                        val dv = dismissBoxState.currentValue
                        if (swipeRemoveEnabled && !processedDismiss && (
                                dv == SwipeToDismissBoxValue.StartToEnd ||
                                    dv == SwipeToDismissBoxValue.EndToStart
                            )
                        ) {
                            processedDismiss = true
                            deleteFromPlaylist()
                        }
                        if (dv == SwipeToDismissBoxValue.Settled) {
                            processedDismiss = false
                        }
                    }

                    val onCheckedChange: (Boolean) -> Unit = {
                        if (it) {
                            selection.add(song.map.id)
                        } else {
                            selection.remove(Integer.valueOf(song.map.id))
                        }
                    }

                    val content: @Composable () -> Unit = {
                        SongListItem(
                            song = song.song,
                            isActive = song.song.id == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            showInLibraryIcon = true,
                            trailingContent = {
                                if (inSelectMode) {
                                    Checkbox(
                                        checked = selection.contains(song.map.id),
                                        onCheckedChange = onCheckedChange,
                                    )
                                } else {
                                    IconButton(
                                        onClick = {
                                            menuState.show {
                                                SongMenu(
                                                    originalSong = song.song,
                                                    playlistSong = song,
                                                    playlistBrowseId = playlist?.playlist?.browseId,
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

                                    if (sortType == PlaylistSongSortType.CUSTOM && !locked && !inSelectMode && !isSearching && editable) {
                                        IconButton(
                                            onClick = { },
                                            modifier = Modifier.draggableHandle(),
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.drag_handle),
                                                contentDescription = null,
                                            )
                                        }
                                    }
                                }
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            if (inSelectMode) {
                                                onCheckedChange(!selection.contains(song.map.id))
                                            } else if (song.song.id == mediaMetadata?.id) {
                                                playerConnection.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(
                                                    ListQueue(
                                                        title = playlist!!.playlist.name,
                                                        items = songs.map { it.song.toMediaItem() },
                                                        startIndex = songs.indexOfFirst { it.map.id == song.map.id },
                                                    ),
                                                )
                                            }
                                        },
                                        onLongClick = {
                                            if (!inSelectMode) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                inSelectMode = true
                                                onCheckedChange(true)
                                                selectionAnchorMapId = song.map.id
                                            } else {
                                                val anchorIndex =
                                                    selectionAnchorMapId?.let { anchorMapId ->
                                                        displayedSongs.indexOfFirst { it.map.id == anchorMapId }
                                                    } ?: -1

                                                if (anchorIndex == -1) {
                                                    onCheckedChange(true)
                                                    selectionAnchorMapId = song.map.id
                                                } else {
                                                    val range = if (anchorIndex <= index) anchorIndex..index else index..anchorIndex
                                                    for (rangeIndex in range) {
                                                        val rangeMapId = displayedSongs[rangeIndex].map.id
                                                        if (rangeMapId !in selection) {
                                                            selection.add(rangeMapId)
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                    ),
                        )
                    }

                    if (locked || inSelectMode || !swipeRemoveEnabled) {
                        Box(modifier = Modifier.animateItem()) {
                            content()
                        }
                    } else {
                        SwipeToDismissBox(
                            state = dismissBoxState,
                            backgroundContent = {},
                            modifier = Modifier.animateItem(),
                        ) {
                            Column {
                                content()
                                HorizontalDivider(
                                    color = Color.White.copy(alpha = 0.09f),
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(start = 72.dp, end = 16.dp),
                                )
                            }
                        }
                    }
                }
            }

            if (isSearching && editable && query.text.trim().length >= 2) {
                item(key = "online_add_header") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp),
                    ) {
                        Text(
                            text = "Tambahkan ke playlist",
                            color = Color.White.copy(alpha = 0.92f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (isSearchingOnline) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White.copy(alpha = 0.78f),
                            )
                        }
                    }
                }

                items(
                    count = onlineSearchResults.size,
                    key = { idx -> "online_add_${onlineSearchResults[idx].id}" },
                ) { idx ->
                    val ytSong = onlineSearchResults[idx]
                    val alreadyAdded = ytSong.id in existingSongIds || ytSong.id in recentlyAddedIds

                    YouTubeListItem(
                        item = ytSong,
                        isActive = ytSong.id == mediaMetadata?.id,
                        isPlaying = isPlaying,
                        trailingContent = {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (alreadyAdded) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.26f)
                                        } else {
                                            Color.White.copy(alpha = 0.14f)
                                        },
                                    )
                                    .clickable(enabled = !alreadyAdded) {
                                        val targetPlaylist = playlist ?: return@clickable
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        recentlyAddedIds.add(ytSong.id)
                                        coroutineScope.launch(Dispatchers.IO) {
                                            database.insert(ytSong.toMediaMetadata())
                                            database.addSongsToPlaylist(
                                                targetPlaylist,
                                                listOf(ytSong.id to null),
                                            )
                                            targetPlaylist.playlist.browseId?.let { browseId ->
                                                syncUtils.scheduleAddToPlaylist(
                                                    browseId,
                                                    targetPlaylist.id,
                                                    listOf(ytSong.id),
                                                )
                                            }
                                            withContext(Dispatchers.Main) {
                                                android.widget.Toast.makeText(
                                                    context,
                                                    "Ditambahkan ke \"${targetPlaylist.playlist.name}\"",
                                                    android.widget.Toast.LENGTH_SHORT,
                                                ).show()
                                            }
                                        }
                                    },
                            ) {
                                Icon(
                                    painter = painterResource(if (alreadyAdded) R.drawable.check else R.drawable.add),
                                    contentDescription = if (alreadyAdded) "Sudah ditambahkan" else "Tambahkan",
                                    tint = if (alreadyAdded) MaterialTheme.colorScheme.primary else Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                playerConnection.playQueue(
                                    YouTubeQueue(
                                        WatchEndpoint(videoId = ytSong.id),
                                        ytSong.toMediaMetadata(),
                                    ),
                                )
                            },
                    )
                }
            }
        }
            }
        }

        DraggableScrollbar(
            modifier =
                Modifier
                    .padding(
                        LocalPlayerAwareWindowInsets.current
                            .union(WindowInsets.ime)
                            .asPaddingValues(),
                    ).align(Alignment.CenterEnd),
            scrollState = lazyListState,
            headerItems = 2,
        )

        CompositionLocalProvider(LocalSongfyBackdrop provides playlistBackdrop) {
            PlaylistFloatingTopBar(
                modifier = Modifier.align(Alignment.TopCenter),
                title = playlist?.playlist?.name.orEmpty(),
                showTitle = showTopBarTitle,
                inSelectMode = inSelectMode,
                selectedCount = selection.size,
                allSelected = selection.size == songs.size && songs.isNotEmpty(),
                isSearching = isSearching,
                query = query,
                onQueryChange = { query = it },
                focusRequester = focusRequester,
                onBackClick = {
                    when {
                        inSelectMode -> onExitSelectionMode()
                        isSearching -> {
                            isSearching = false
                            query = TextFieldValue()
                        }
                        else -> navController.navigateUp()
                    }
                },
                onSelectAllChange = {
                    if (selection.size == songs.size) {
                        selection.clear()
                    } else {
                        selection.clear()
                        selection.addAll(songs.map { it.map.id })
                    }
                },
                onSelectionMoreClick = {
                    menuState.show {
                        SelectionSongMenu(
                            songSelection =
                                selection.mapNotNull { mapId ->
                                    songs.find { it.map.id == mapId }?.song
                                },
                            songPosition =
                                selection.mapNotNull { mapId ->
                                    songs.find { it.map.id == mapId }?.map
                                },
                            onDismiss = menuState::dismiss,
                            clearAction = onExitSelectionMode,
                        )
                    }
                },
                onSearchClick = { isSearching = true },
                onShareClick = playlist?.let { pl ->
                    {
                        val shareText = pl.playlist.browseId?.let {
                            "https://music.youtube.com/playlist?list=$it"
                        } ?: pl.playlist.name
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                    }
                },
                onMoreClick = {
                    playlist?.let { pl ->
                        menuState.show {
                            LocalPlaylistMenu(
                                playlist = pl,
                                songs = songs,
                                context = context,
                                downloadState = playlistDownloadState,
                                onEdit = { showEditDialog = true },
                                onSync = {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        syncUtils.syncPlaylistSuspend(
                                            pl.playlist.browseId!!,
                                            pl.id,
                                        )
                                        withContext(Dispatchers.Main) {
                                            snackbarHostState.showSnackbar(context.getString(R.string.playlist_synced))
                                        }
                                    }
                                },
                                onDelete = { showDeletePlaylistDialog = true },
                                onDownload = {
                                    when (playlistDownloadState) {
                                        Download.STATE_COMPLETED -> showRemoveDownloadDialog = true
                                        Download.STATE_DOWNLOADING -> {
                                            songs.forEach { song ->
                                                DownloadService.sendRemoveDownload(
                                                    context,
                                                    ExoDownloadService::class.java,
                                                    song.song.id,
                                                    false,
                                                )
                                            }
                                        }
                                        else -> {
                                            songs.forEach { song ->
                                                playlistDownloadUtil.download(song.song)
                                            }
                                        }
                                    }
                                },
                                onQueue = {
                                    playerConnection.addToQueue(
                                        items = songs.map { it.song.toMediaItem() },
                                    )
                                },
                                onDismiss = { menuState.dismiss() },
                            )
                        }
                    }
                },
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime))
                    .align(Alignment.BottomCenter),
        )
    }
}

@Composable
fun LocalPlaylistHeader(
    playlist: Playlist,
    songs: List<PlaylistSong>,
    onlinePlaylist: PlaylistItem?,
    onShowEditDialog: () -> Unit,
    onShowRemoveDownloadDialog: () -> Unit,
    onshowDeletePlaylistDialog: () -> Unit,
    onStartSearch: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier,
) {
    val navController = LocalNavController.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val context = LocalContext.current
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val syncUtils = LocalSyncUtils.current
    val scope = rememberCoroutineScope()
    val editPlaylistCoverStr = stringResource(R.string.edit_playlist_cover)
    val playlistSyncedStr = stringResource(R.string.playlist_synced)

    val playlistLength =
        remember(songs) {
            songs.fastSumBy { it.song.song.duration }
        }

    val downloadUtil = LocalDownloadUtil.current
    var downloadState by remember {
        mutableIntStateOf(Download.STATE_STOPPED)
    }

    val liked = playlist.playlist.bookmarkedAt != null
    val editable: Boolean = playlist.playlist.isEditable

    val overrideThumbnail = remember(playlist.id) { mutableStateOf<String?>(null) }
    val activeCustomUrl = overrideThumbnail.value ?: playlist.playlist.thumbnailUrl
    var isCustomThumbnail by remember(playlist.id, activeCustomUrl) {
        mutableStateOf(
            activeCustomUrl?.let {
                it.contains("studio_square_thumbnail") ||
                    it.contains("content://${context.packageName}") ||
                    it.contains("playlist_covers")
            } ?: false
        )
    }

    val currentPlaylist by rememberUpdatedState(playlist)
    var pendingCropDestUri by remember { mutableStateOf<Uri?>(null) }
    var showEditNoteDialog by remember { mutableStateOf(false) }

    val cropLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == android.app.Activity.RESULT_OK) {
                val output = res.data?.let { UCrop.getOutput(it) } ?: pendingCropDestUri
                pendingCropDestUri = null
                if (output != null) {
                    val targetPlaylist = currentPlaylist
                    val outputStr = output.toString()
                    overrideThumbnail.value = outputStr
                    isCustomThumbnail = true
                    scope.launch(Dispatchers.IO) {
                        val coverDir = java.io.File(context.filesDir, "playlist_covers")
                        val currentFileName = output.lastPathSegment
                        coverDir.listFiles { f ->
                            f.name.startsWith("cover_${targetPlaylist.id}_") && f.name != currentFileName
                        }?.forEach { runCatching { it.delete() } }

                        when {
                            targetPlaylist.playlist.browseId == null -> {
                                database.query {
                                    update(targetPlaylist.playlist.copy(thumbnailUrl = outputStr))
                                }
                            }

                            else -> {
                                val bytes = uriToByteArray(context, output)
                                if (bytes != null) {
                                    YouTube
                                        .uploadCustomThumbnailLink(
                                            targetPlaylist.playlist.browseId,
                                            bytes,
                                        ).onSuccess { newThumbnailUrl ->
                                            withContext(Dispatchers.Main) {
                                                overrideThumbnail.value = newThumbnailUrl
                                                isCustomThumbnail = true
                                            }
                                            database.query {
                                                update(targetPlaylist.playlist.copy(thumbnailUrl = newThumbnailUrl))
                                            }
                                        }.onFailure {
                                            if (it is ClientRequestException) {
                                                snackbarHostState.showSnackbar("${it.response.status.value} ${it.response.status.description}")
                                            }
                                            reportException(it)
                                        }
                                }
                            }
                        }
                    }
                }
            } else {
                pendingCropDestUri = null
            }
        }

    val (darkMode, _) =
        rememberEnumPreference(
            DarkModeKey,
            defaultValue = DarkMode.AUTO,
        )

    val cropColor = MaterialTheme.colorScheme
    val darkTheme = darkMode == DarkMode.ON || (darkMode == DarkMode.AUTO && isSystemInDarkTheme())

    val pickLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            uri?.let { sourceUri ->
                val coverDir = java.io.File(context.filesDir, "playlist_covers").apply { mkdirs() }
                val destFile = java.io.File(coverDir, "cover_${currentPlaylist.id}_${System.currentTimeMillis()}.jpg")
                val destUri = Uri.fromFile(destFile)
                pendingCropDestUri = destUri

                val options =
                    UCrop.Options().apply {
                        setCompressionFormat(Bitmap.CompressFormat.JPEG)
                        setCompressionQuality(90)
                        setHideBottomControls(true)
                        setToolbarTitle(editPlaylistCoverStr)

                        setStatusBarLight(!darkTheme)

                        setToolbarColor(cropColor.surface.toArgb())
                        setToolbarWidgetColor(cropColor.inverseSurface.toArgb())
                        setRootViewBackgroundColor(cropColor.surface.toArgb())
                        setLogoColor(cropColor.surface.toArgb())
                    }

                val intent =
                    UCrop
                        .of(sourceUri, destUri)
                        .withAspectRatio(1f, 1f)
                        .withOptions(options)
                        .getIntent(context)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                cropLauncher.launch(intent)
            }
        }

    LaunchedEffect(songs) {
        if (songs.isEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (songs.all { downloads[it.song.id]?.state == Download.STATE_COMPLETED }) {
                    Download.STATE_COMPLETED
                } else if (songs.all {
                        downloads[it.song.id]?.state == Download.STATE_QUEUED ||
                            downloads[it.song.id]?.state == Download.STATE_DOWNLOADING ||
                            downloads[it.song.id]?.state == Download.STATE_COMPLETED
                    }
                ) {
                    Download.STATE_DOWNLOADING
                } else {
                    Download.STATE_STOPPED
                }
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 58.dp, bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showEditNoteDialog) {
            ActionPromptDialog(
                title = stringResource(R.string.edit_playlist_cover),
                onDismiss = { showEditNoteDialog = false },
                onConfirm = {
                    showEditNoteDialog = false
                    pickLauncher.launch(
                        PickVisualMediaRequest(mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onCancel = { showEditNoteDialog = false },
            ) {
                if (playlist.playlist.browseId != null) {
                    Text(
                        text = stringResource(R.string.edit_playlist_cover_note),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    text = stringResource(R.string.edit_playlist_cover_note_wait),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }
        // Playlist Thumbnail(s) - Large centered with shadow
        Box(
            modifier = Modifier.padding(top = 4.dp, bottom = 22.dp),
        ) {
            val coverShape = RoundedCornerShape(22.dp)
            val customUrl = overrideThumbnail.value ?: playlist.playlist.thumbnailUrl
            if (customUrl != null) {
                Surface(
                    modifier =
                        Modifier
                            .size(256.dp)
                            .shadow(
                                elevation = 24.dp,
                                shape = coverShape,
                                spotColor = Color.Black.copy(alpha = 0.45f),
                            ),
                    shape = coverShape,
                ) {
                    AsyncImage(
                        model = customUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                when (playlist.thumbnails.size) {
                    0 -> {
                        Surface(
                            modifier =
                                Modifier
                                    .size(256.dp)
                                    .shadow(
                                        elevation = 20.dp,
                                        shape = coverShape,
                                    ),
                            shape = coverShape,
                            color = Color.White.copy(alpha = 0.12f),
                        ) {
                            androidx.compose.foundation.Image(
                                painter = painterResource(R.drawable.ic_launcher_foreground),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }

                    1 -> {
                        Surface(
                            modifier =
                                Modifier
                                    .size(256.dp)
                                    .shadow(
                                        elevation = 24.dp,
                                        shape = coverShape,
                                        spotColor = Color.Black.copy(alpha = 0.45f),
                                    ),
                            shape = coverShape,
                        ) {
                            AsyncImage(
                                model = playlist.thumbnails[0],
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }

                    else -> {
                        Surface(
                            modifier =
                                Modifier
                                    .size(256.dp)
                                    .shadow(
                                        elevation = 24.dp,
                                        shape = coverShape,
                                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                    ),
                            shape = coverShape,
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                listOf(
                                    Alignment.TopStart,
                                    Alignment.TopEnd,
                                    Alignment.BottomStart,
                                    Alignment.BottomEnd,
                                ).fastForEachIndexed { index, alignment ->
                                    AsyncImage(
                                        model = playlist.thumbnails.getOrNull(index),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier =
                                            Modifier
                                                .align(alignment)
                                                .size(128.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (editable) {
                OverlayEditButton(
                    visible = true,
                    alignment = Alignment.BottomEnd,
                    onClick = {
                        if (isCustomThumbnail) {
                            menuState.show(
                                {
                                    CustomThumbnailMenu(
                                        onEdit = {
                                            pickLauncher.launch(
                                                PickVisualMediaRequest(
                                                    mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly,
                                                ),
                                            )
                                        },
                                        onRemove = {
                                            when {
                                                playlist.playlist.browseId == null -> {
                                                    overrideThumbnail.value = null
                                                    scope.launch(Dispatchers.IO) {
                                                        val coverDir = java.io.File(context.filesDir, "playlist_covers")
                                                        coverDir.listFiles { f ->
                                                            f.name.startsWith("cover_${playlist.id}_")
                                                        }?.forEach { runCatching { it.delete() } }
                                                        database.query {
                                                            update(playlist.playlist.copy(thumbnailUrl = null))
                                                        }
                                                    }
                                                }

                                                else -> {
                                                    scope.launch(Dispatchers.IO) {
                                                        YouTube.removeThumbnailPlaylist(playlist.playlist.browseId).onSuccess { newThumbnailUrl ->
                                                            withContext(Dispatchers.Main) {
                                                                overrideThumbnail.value = newThumbnailUrl
                                                            }
                                                            database.query {
                                                                update(playlist.playlist.copy(thumbnailUrl = newThumbnailUrl))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            isCustomThumbnail = false
                                        },
                                        onDismiss = menuState::dismiss,
                                    )
                                },
                            )
                        } else {
                            showEditNoteDialog = true
                        }
                    },
                )
            }
        }

        // Playlist Name
        Text(
            text = playlist.playlist.name,
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 32.dp),
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Creator name (Apple Music style)
        val onlineAuthor = onlinePlaylist?.author
        val creatorName = onlineAuthor?.name ?: "Songfy"
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.combinedClickable(
                onClick = {
                    onlineAuthor?.id?.let { navController.navigate("artist/$it") }
                },
            ),
        ) {
            if (onlinePlaylist?.authorAvatarUrl != null) {
                AsyncImage(
                    model = onlinePlaylist.authorAvatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape),
                )
            }
            Text(
                text = creatorName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.88f),
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Metadata - Updated time • Song Count • Duration
        val songCount =
            if (playlist.songCount == 0 && playlist.playlist.remoteSongCount != null) {
                playlist.playlist.remoteSongCount
            } else {
                playlist.songCount
            }
        val nSongs = pluralStringResource(R.plurals.n_song, songCount, songCount)
        val durationText = if (playlistLength > 0) makeTimeString(playlistLength * 1000L) else null
        val updatedText = formatPlaylistUpdatedText(playlist.playlist.lastUpdateTime)
        val metadataString = listOfNotNull(updatedText, nSongs, durationText).joinToString(" • ")
        Text(
            text = metadataString,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.70f),
        )

        val description = onlinePlaylist?.description
        if (!description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))

            ExpandableText(
                text = description,
                modifier = Modifier.padding(horizontal = 32.dp),
                collapsedMaxLines = 3,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Action Buttons Row (Shuffle, white Play pill, Download)
        PlaylistActionRow(
            onShuffleClick = {
                playerConnection.playQueue(
                    ListQueue(
                        title = playlist.playlist.name,
                        items = songs.shuffled().map { it.song.toMediaItem() },
                    ),
                )
            },
            onPlayClick = {
                playerConnection.playQueue(
                    ListQueue(
                        title = playlist.playlist.name,
                        items = songs.map { it.song.toMediaItem() },
                    ),
                )
            },
            downloadState = downloadState,
            onDownloadClick = {
                when (downloadState) {
                    Download.STATE_COMPLETED -> onShowRemoveDownloadDialog()
                    Download.STATE_DOWNLOADING -> {
                        songs.forEach { song ->
                            DownloadService.sendRemoveDownload(
                                context,
                                ExoDownloadService::class.java,
                                song.song.id,
                                false,
                            )
                        }
                    }
                    else -> {
                        songs.forEach { song ->
                            downloadUtil.download(song.song)
                        }
                    }
                }
            },
        )
    }
}

fun uriToByteArray(
    context: Context,
    uri: Uri,
): ByteArray? =
    try {
        if (uri.scheme == "file") {
            uri.path?.let { java.io.File(it).takeIf { f -> f.exists() }?.readBytes() }
        } else {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }
    } catch (_: Exception) {
        null
    }
