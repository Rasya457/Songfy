package com.lagu.songfy.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ArtistDetailDto(
    @SerialName("artist_id") val artistId: String,
    val name: String,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("banner_url") val bannerUrl: String? = null,
    val description: String? = null,
    val subscribers: String? = null,
    @SerialName("monthly_listeners") val monthlyListeners: String? = null,
    val verified: Boolean = true,
    @SerialName("top_songs") val topSongs: List<SongDto> = emptyList(),
    @SerialName("all_songs") val allSongs: List<SongDto> = emptyList(),
    val albums: List<JsonElement> = emptyList(),
    val singles: List<JsonElement> = emptyList(),
    @SerialName("related_artists") val relatedArtists: List<JsonElement> = emptyList()
)