package com.lagu.songfy.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StreamResponseDto(
    @SerialName("video_id") val videoId: String,
    @SerialName("audio_url") val audioUrl: String,
    @SerialName("expires_in") val expiresIn: Int,
    val bitrate: Double? = null
)