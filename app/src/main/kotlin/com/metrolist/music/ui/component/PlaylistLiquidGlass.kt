package com.metrolist.music.ui.component

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.graphics.ColorUtils
import androidx.media3.exoplayer.offline.Download
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.metrolist.music.R
import com.songfy.ui.songfyLiquidGlass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDateTime

@Immutable
data class PlaylistCoverPalette(
    val topColor: Color,
    val midColor: Color,
    val bottomColor: Color,
)

private val playlistPaletteCache = android.util.LruCache<String, PlaylistCoverPalette>(32)

@Composable
fun rememberPlaylistCoverColors(coverUrl: String?): PlaylistCoverPalette {
    val context = LocalContext.current
    val defaultPalette = remember {
        PlaylistCoverPalette(
            topColor = Color(0xFF4A535C),
            midColor = Color(0xFF2B3137),
            bottomColor = Color(0xFF101215),
        )
    }
    var rawPalette by remember(coverUrl) {
        mutableStateOf(coverUrl?.let { playlistPaletteCache.get(it) } ?: defaultPalette)
    }

    LaunchedEffect(coverUrl) {
        if (coverUrl.isNullOrBlank()) return@LaunchedEffect
        playlistPaletteCache.get(coverUrl)?.let { cached ->
            rawPalette = cached
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            runCatching {
                val req = ImageRequest.Builder(context)
                    .data(coverUrl)
                    .allowHardware(false)
                    .size(128)
                    .build()
                val bmp = context.imageLoader.execute(req).image?.toBitmap() ?: return@runCatching
                val palette = Palette.from(bmp).maximumColorCount(12).generate()
                val baseRgb = palette.dominantSwatch?.rgb
                    ?: palette.mutedSwatch?.rgb
                    ?: palette.vibrantSwatch?.rgb
                    ?: 0xFF4A535C.toInt()
                val darkRgb = palette.darkMutedSwatch?.rgb
                    ?: palette.darkVibrantSwatch?.rgb
                    ?: ColorUtils.blendARGB(baseRgb, android.graphics.Color.BLACK, 0.55f)
                val top = Color(ColorUtils.blendARGB(baseRgb, android.graphics.Color.BLACK, 0.25f))
                val mid = Color(ColorUtils.blendARGB(darkRgb, android.graphics.Color.BLACK, 0.35f))
                val bottom = Color(ColorUtils.blendARGB(darkRgb, android.graphics.Color.BLACK, 0.78f))
                val computed = PlaylistCoverPalette(top, mid, bottom)
                playlistPaletteCache.put(coverUrl, computed)
                withContext(Dispatchers.Main) {
                    rawPalette = computed
                }
            }
        }
    }

    val topAnim by animateColorAsState(rawPalette.topColor, tween(450), label = "plTopColor")
    val midAnim by animateColorAsState(rawPalette.midColor, tween(450), label = "plMidColor")
    val bottomAnim by animateColorAsState(rawPalette.bottomColor, tween(450), label = "plBottomColor")
    return PlaylistCoverPalette(topAnim, midAnim, bottomAnim)
}

@Composable
fun PlaylistDynamicBackground(
    coverUrl: String?,
    palette: PlaylistCoverPalette,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0.0f to palette.topColor,
                    0.45f to palette.midColor,
                    1.0f to palette.bottomColor,
                ),
            ),
    ) {
        if (!coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .alpha(0.32f)
                    .let { m ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) m.blur(48.dp) else m
                    },
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            palette.topColor.copy(alpha = 0.25f),
                            Color.Transparent,
                        ),
                        center = Offset(500f, 380f),
                        radius = 900f,
                    ),
                )
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.14f),
                        0.45f to Color.Black.copy(alpha = 0.28f),
                        1.0f to Color.Black.copy(alpha = 0.62f),
                    ),
                ),
        )
    }
}

@Composable
fun PlaylistFloatingTopBar(
    title: String,
    showTitle: Boolean,
    inSelectMode: Boolean,
    selectedCount: Int,
    allSelected: Boolean,
    isSearching: Boolean,
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    focusRequester: FocusRequester,
    onBackClick: () -> Unit,
    onSelectAllChange: () -> Unit,
    onSelectionMoreClick: () -> Unit,
    onSearchClick: () -> Unit,
    onShareClick: (() -> Unit)?,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Row(
        modifier = modifier
            .zIndex(10f)
            .fillMaxWidth()
            .padding(top = topInset + 8.dp, start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Left circular Liquid Glass Back / Close button
        Box(
            modifier = Modifier
                .size(44.dp)
                .songfyLiquidGlass(
                    dark = true,
                    shadowElevation = 10.dp,
                    shape = CircleShape,
                )
                .clickable(onClick = onBackClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(
                    if (inSelectMode || isSearching) R.drawable.close else R.drawable.arrow_back,
                ),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }

        // Center title / search field / selection count
        if (isSearching) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        text = stringResource(R.string.search),
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 16.sp,
                    )
                },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = Color.White,
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .focusRequester(focusRequester),
            )
        } else if (inSelectMode) {
            Text(
                text = "$selectedCount selected",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            )
        } else if (showTitle) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        // Right Liquid Glass action pill
        if (inSelectMode) {
            Row(
                modifier = Modifier
                    .height(44.dp)
                    .songfyLiquidGlass(
                        dark = true,
                        shadowElevation = 10.dp,
                        shape = CircleShape,
                    )
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = allSelected,
                    onCheckedChange = { onSelectAllChange() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color.White,
                        uncheckedColor = Color.White.copy(alpha = 0.7f),
                        checkmarkColor = Color.Black,
                    ),
                )
                IconButton(
                    enabled = selectedCount > 0,
                    onClick = onSelectionMoreClick,
                    modifier = Modifier.size(38.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        } else if (!isSearching) {
            Row(
                modifier = Modifier
                    .height(44.dp)
                    .songfyLiquidGlass(
                        dark = true,
                        shadowElevation = 10.dp,
                        shape = CircleShape,
                    )
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onSearchClick,
                    modifier = Modifier.size(42.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.search),
                        contentDescription = stringResource(R.string.search),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                if (onShareClick != null) {
                    IconButton(
                        onClick = onShareClick,
                        modifier = Modifier.size(42.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.share),
                            contentDescription = stringResource(R.string.share),
                            tint = Color.White,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
                IconButton(
                    onClick = onMoreClick,
                    modifier = Modifier.size(42.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistActionRow(
    onShuffleClick: () -> Unit,
    onPlayClick: () -> Unit,
    downloadState: Int,
    onDownloadClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Shuffle circular translucent button
        Surface(
            onClick = onShuffleClick,
            enabled = enabled,
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.16f),
            modifier = Modifier
                .size(48.dp)
                .border(0.5.dp, Color.White.copy(alpha = 0.20f), CircleShape),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.shuffle),
                    contentDescription = stringResource(R.string.shuffle),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        // Play wide white pill button (Apple Music style)
        Surface(
            onClick = onPlayClick,
            enabled = enabled,
            shape = CircleShape,
            color = Color.White,
            modifier = Modifier
                .height(48.dp)
                .widthIn(min = 148.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.play),
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.play),
                    color = Color.Black,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Download circular translucent button
        Surface(
            onClick = onDownloadClick,
            enabled = enabled,
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.16f),
            modifier = Modifier
                .size(48.dp)
                .border(0.5.dp, Color.White.copy(alpha = 0.20f), CircleShape),
        ) {
            Box(contentAlignment = Alignment.Center) {
                when (downloadState) {
                    Download.STATE_DOWNLOADING -> {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Download.STATE_COMPLETED -> {
                        Icon(
                            painter = painterResource(R.drawable.offline),
                            contentDescription = stringResource(R.string.remove_download),
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = com.songfy.ui.icons.SongfyIcons.Download,
                            contentDescription = "Download",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

fun formatPlaylistUpdatedText(lastUpdateTime: LocalDateTime?): String? {
    lastUpdateTime ?: return null
    val duration = Duration.between(lastUpdateTime, LocalDateTime.now())
    val minutes = duration.toMinutes().coerceAtLeast(0L)
    val hours = duration.toHours()
    val days = duration.toDays()
    return when {
        minutes < 1 -> "Updated just now"
        minutes < 60 -> "Updated ${minutes}m ago"
        hours < 24 -> "Updated ${hours}hr ago"
        days < 30 -> "Updated ${days}d ago"
        else -> "Updated ${days / 30}mo ago"
    }
}
