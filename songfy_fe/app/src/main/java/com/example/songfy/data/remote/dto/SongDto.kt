package com.lagu.songfy.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ArtistShortDto(
    val id: String? = null,
    val name: String
)

@Serializable
data class SongDto(
    @SerialName("video_id") val videoId: String,
    val title: String,
    val artists: List<ArtistShortDto>,
    val album: String? = null,
    val duration: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("play_count") val playCount: String? = null
)

@Serializable
data class SearchResponseDto(
    val songs: List<SongDto> = emptyList(),
    val artists: List<ArtistSummaryDto> = emptyList(),
    val albums: List<AlbumSummaryDto> = emptyList(),
    val playlists: List<PlaylistSummaryDto> = emptyList()
)

@Serializable
data class ArtistSummaryDto(
    @SerialName("artist_id") val artistId: String? = null,
    val name: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null
)

@Serializable
data class AlbumSummaryDto(
    @SerialName("browse_id") val browseId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null
)

@Serializable
data class PlaylistSummaryDto(
    @SerialName("browse_id") val browseId: String? = null,
    val title: String? = null,
    val description: String? = null,
    val author: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("item_count") val itemCount: String? = null
)

@Serializable
data class PlaylistDetailResponseDto(
    @SerialName("playlist_id") val playlistId: String? = null,
    val title: String? = null,
    val description: String? = null,
    val author: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("track_count") val trackCount: Int? = null,
    val tracks: List<SongDto> = emptyList()
)

fun String?.toHighResThumbnail(): String? {
    if (this.isNullOrBlank()) return this
    if (this.contains("googleusercontent.com") || this.contains("ggpht.com")) {
        return this.replace(Regex("=w\\d+-h\\d+.*|=s\\d+.*"), "=w1200-h1200-l90-rj")
    }
    if (this.contains("ytimg.com")) {
        return this.replace(Regex("(hqdefault|mqdefault|default|sddefault)\\.jpg.*"), "maxresdefault.jpg")
    }
    return this
}

val SongDto.highResThumbnailUrl: String?
    get() = thumbnailUrl.toHighResThumbnail()