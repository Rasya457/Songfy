/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.MediaInfo
import com.metrolist.music.LocalArtistNameAliases
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.LoudnessLevel
import com.metrolist.music.constants.LoudnessLevelKey
import com.metrolist.music.db.entities.FormatEntity
import com.metrolist.music.db.entities.Song
import com.metrolist.music.ui.component.shimmer.ShimmerHost
import com.metrolist.music.ui.component.shimmer.TextPlaceholder
import com.metrolist.music.utils.ArtistNameAliases
import com.metrolist.music.utils.rememberEnumPreference
import com.songfy.ui.isSongfyDarkTheme
import androidx.compose.ui.platform.LocalLocale

@Composable
fun getLoudnessLevelLabel(loudnessLevel: LoudnessLevel): String {
    return when (loudnessLevel) {
        LoudnessLevel.AGGRESSIVE -> stringResource(R.string.loudness_level_aggressive)
        LoudnessLevel.LOUD -> stringResource(R.string.loudness_level_loud)
        LoudnessLevel.BALANCED -> stringResource(R.string.loudness_level_balanced)
        LoudnessLevel.QUIET -> stringResource(R.string.loudness_level_quiet)
    }
}

@Composable
fun ShowMediaInfo(videoId: String) {
    if (videoId.isBlank() || videoId.isEmpty()) return

    var info by remember {
        mutableStateOf<MediaInfo?>(null)
    }

    val database = LocalDatabase.current
    var song by remember { mutableStateOf<Song?>(null) }

    var currentFormat by remember { mutableStateOf<FormatEntity?>(null) }

    val playerConnection = LocalPlayerConnection.current
    val currentStreamClient by playerConnection?.currentStreamClient?.collectAsState() ?: remember { mutableStateOf(null) }
    val context = LocalContext.current
    val artistNameAliases = LocalArtistNameAliases.current

    val loudnessLevel by rememberEnumPreference(
        LoudnessLevelKey,
        defaultValue = LoudnessLevel.BALANCED
    )

    val targetLufs: Float = loudnessLevel.targetLufs

    LaunchedEffect(Unit, videoId) {
        info = YouTube.getMediaInfo(videoId).getOrNull()
    }

    LaunchedEffect(Unit, videoId) {
        database.song(videoId).collect {
            song = it
        }
    }

    LaunchedEffect(Unit, videoId) {
        database.format(videoId).collect {
            currentFormat = it
        }
    }

    val isDark = isSongfyDarkTheme()
    val primaryTextColor = if (isDark) Color.White else Color(0xFF111318)
    val secondaryTextColor = if (isDark) Color.White.copy(alpha = 0.65f) else Color(0xFF111318).copy(alpha = 0.62f)
    val cardColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
    val dividerColor = if (isDark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.07f)
    val navBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        state = rememberLazyListState(),
        contentPadding = PaddingValues(top = 4.dp, bottom = navBottomPadding + 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 560.dp),
    ) {
        item(key = "header") {
            Text(
                text = stringResource(R.string.details),
                color = primaryTextColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }

        if (info != null) {
            item(contentType = "MediaDetails") {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val unknownStr = stringResource(R.string.unknown)
                val copyToClipboard: (String) -> Unit = { text ->
                    cm.setPrimaryClip(ClipData.newPlainText("text", text))
                    Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
                }

                val baseList = listOf(
                    Triple(stringResource(R.string.song_title), info?.title ?: song?.title ?: unknownStr, R.drawable.music_note),
                    Triple(
                        stringResource(R.string.song_artists),
                        song?.artists?.joinToString {
                            ArtistNameAliases.resolve(artistNameAliases, it.id, it.name)
                        } ?: info?.author?.let {
                            ArtistNameAliases.resolve(artistNameAliases, null, it)
                        } ?: unknownStr,
                        R.drawable.person,
                    ),
                    Triple(stringResource(R.string.media_id), song?.id ?: info?.videoId ?: unknownStr, R.drawable.media3_icon_bookmark_filled),
                )

                val measuredLufs: Double? = currentFormat?.perceptualLoudnessDb ?: currentFormat?.loudnessDb?.let { it + LoudnessLevel.AGGRESSIVE.targetLufs }

                val extendedList = if (currentFormat != null) {
                    listOf(
                        Triple(stringResource(R.string.views), info?.viewCount?.let(::numberFormatter).orEmpty().ifEmpty { unknownStr }, R.drawable.media3_icon_feed),
                        Triple(stringResource(R.string.likes), info?.like?.let(::numberFormatter).orEmpty().ifEmpty { unknownStr }, R.drawable.media3_icon_thumb_up_unfilled),
                        Triple(stringResource(R.string.dislikes), info?.dislike?.let(::numberFormatter).orEmpty().ifEmpty { unknownStr }, R.drawable.media3_icon_thumb_down_unfilled),
                        Triple("Itag", currentFormat?.itag?.toString() ?: unknownStr, R.drawable.key),
                        Triple(stringResource(R.string.stream_client), currentStreamClient ?: unknownStr, R.drawable.play),
                        Triple(stringResource(R.string.mime_type), currentFormat?.mimeType ?: unknownStr, R.drawable.info),
                        Triple(stringResource(R.string.codecs), currentFormat?.codecs ?: unknownStr, R.drawable.radio),
                        Triple(stringResource(R.string.bitrate), currentFormat?.bitrate?.let { "${it / 1000} Kbps" } ?: unknownStr, R.drawable.gradient),
                        Triple(stringResource(R.string.sample_rate), currentFormat?.sampleRate?.let { "$it Hz" } ?: unknownStr, R.drawable.contrast),
                        Triple(
                            stringResource(R.string.loudness),
                            measuredLufs?.let { String.format(LocalLocale.current.platformLocale, "%.2f dB", it - targetLufs) } ?: unknownStr,
                            R.drawable.volume_up,
                        ),
                        Triple(stringResource(R.string.loudness_level), getLoudnessLevelLabel(loudnessLevel), R.drawable.volume_up),
                        Triple(stringResource(R.string.volume), if (playerConnection != null) "${(playerConnection.player.volume * 100).toInt()}%" else unknownStr, R.drawable.volume_mute),
                        Triple(
                            stringResource(R.string.file_size),
                            currentFormat?.contentLength?.let { Formatter.formatShortFileSize(context, it) } ?: unknownStr,
                            R.drawable.content_copy,
                        ),
                    )
                } else {
                    emptyList()
                }

                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    MediaInfoGlassSection(
                        title = stringResource(R.string.general),
                        items = baseList,
                        cardColor = cardColor,
                        borderColor = borderColor,
                        dividerColor = dividerColor,
                        primaryTextColor = primaryTextColor,
                        secondaryTextColor = secondaryTextColor,
                        onItemClick = copyToClipboard,
                    )

                    if (extendedList.isNotEmpty()) {
                        MediaInfoGlassSection(
                            title = stringResource(R.string.information),
                            items = extendedList,
                            cardColor = cardColor,
                            borderColor = borderColor,
                            dividerColor = dividerColor,
                            primaryTextColor = primaryTextColor,
                            secondaryTextColor = secondaryTextColor,
                            onItemClick = copyToClipboard,
                        )
                    }

                    val descriptionText = info?.description?.takeIf { it.isNotBlank() } ?: unknownStr
                    MediaInfoGlassSection(
                        title = stringResource(R.string.description),
                        items = listOf(Triple(stringResource(R.string.description), descriptionText, null)),
                        cardColor = cardColor,
                        borderColor = borderColor,
                        dividerColor = dividerColor,
                        primaryTextColor = primaryTextColor,
                        secondaryTextColor = secondaryTextColor,
                        onItemClick = copyToClipboard,
                    )
                }
            }
        } else {
            item(contentType = "MediaInfoLoader") {
                ShimmerHost {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(all = 16.dp),
                    ) {
                        TextPlaceholder()
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaInfoGlassSection(
    title: String,
    items: List<Triple<String, String, Int?>>,
    cardColor: Color,
    borderColor: Color,
    dividerColor: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    onItemClick: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            color = secondaryTextColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
        )
        val shape = RoundedCornerShape(18.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(cardColor)
                .border(0.5.dp, borderColor, shape),
        ) {
            items.forEachIndexed { index, (label, value, iconRes) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onItemClick(value) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (iconRes != null) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            tint = secondaryTextColor,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = label,
                            color = primaryTextColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = value,
                            color = secondaryTextColor,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }
                if (index < items.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = if (iconRes != null) 46.dp else 14.dp)
                            .height(0.5.dp)
                            .background(dividerColor),
                    )
                }
            }
        }
    }
}
