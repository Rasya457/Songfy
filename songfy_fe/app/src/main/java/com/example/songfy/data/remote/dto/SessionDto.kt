package com.lagu.songfy.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SessionStartRequestDto(
    @SerialName("device_name") val deviceName: String
)

@Serializable
data class SessionStartResponseDto(
    @SerialName("session_id") val sessionId: String,
    @SerialName("device_name") val deviceName: String,
    @SerialName("user_id") val userId: String,
    val message: String? = null
)
