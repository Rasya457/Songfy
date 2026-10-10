package com.lagu.songfy.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LyricsResponseDto(
    @SerialName("video_id") val videoId: String,
    @SerialName("plain_lyrics") val plainLyrics: String? = null,
    @SerialName("synced_lyrics") val syncedLyrics: String? = null,
    val found: Boolean = false
)