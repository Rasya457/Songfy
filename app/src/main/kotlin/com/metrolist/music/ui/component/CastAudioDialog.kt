/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * CastAudioDialog.kt — Apple Music & Material 3 style Audio Output sheet.
 * Supports:
 * - This Phone / Built-in Speaker
 * - Bluetooth Audio (detects connected Bluetooth headphones/speakers, opens Bluetooth settings)
 * - Google Cast (Chromecast / Nest streaming, device volume, disconnect)
 */

package com.metrolist.music.ui.component

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.songfy.ui.icons.SongfyIcons
import com.songfy.ui.icons.SongfyOutputKind
import com.songfy.ui.icons.rememberSongfyAudioOutputState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastAudioDialog(
    animProgress: Float = 1f,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val castHandler = remember(playerConnection) {
        try {
            playerConnection?.service?.castConnectionHandler
        } catch (_: Exception) {
            null
        }
    }

    val isCasting by castHandler?.isCasting?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }
    val castDeviceName by castHandler?.castDeviceName?.collectAsStateWithLifecycle() ?: remember { mutableStateOf<String?>(null) }
    val castVolume by castHandler?.castVolume?.collectAsStateWithLifecycle() ?: remember { mutableFloatStateOf(1f) }

    val audioOutput by rememberSongfyAudioOutputState()
    val isBluetoothActive = audioOutput.kind != SongfyOutputKind.PHONE && !isCasting
    val bluetoothName = audioOutput.deviceName ?: "Bluetooth Audio"

    val scale = androidx.compose.ui.util.lerp(0.85f, 1f, animProgress)
    val alpha = (animProgress * 1.5f).coerceIn(0f, 1f)
    val scrimAlpha = (animProgress * 0.45f).coerceIn(0f, 0.45f)
    val scrimInteractionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .clickable(
                interactionSource = scrimInteractionSource,
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(28.dp),
                    ambientColor = Color.Black.copy(alpha = 0.40f),
                    spotColor = Color.Black.copy(alpha = 0.40f),
                )
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF1C1C1E))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(28.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = true,
                    onClick = {},
                )
                .padding(22.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = SongfyIcons.AirPlay,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Audio Output",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = when {
                                    isCasting -> "Streaming via Google Cast"
                                    isBluetoothActive -> "Connected to $bluetoothName"
                                    else -> "Playing on Phone Speaker"
                                },
                                color = Color.White.copy(alpha = 0.60f),
                                fontSize = 12.sp,
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = SongfyIcons.Close,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.65f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // ── Option 1: Phone Speaker ──
                val isPhoneSpeakerActive = !isCasting && !isBluetoothActive
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isPhoneSpeakerActive) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f))
                        .let {
                            if (isPhoneSpeakerActive) it.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                            else it
                        }
                        .padding(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = SongfyIcons.SpeakerHigh,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "This Phone",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Internal Speaker",
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 12.sp,
                            )
                        }
                        if (isPhoneSpeakerActive) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ── Option 2: Bluetooth / External Audio ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isBluetoothActive) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f))
                        .let {
                            if (isBluetoothActive) it.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                            else it
                        }
                        .clickable {
                            try {
                                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "Open Bluetooth settings from system panel", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isBluetoothActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (isBluetoothActive) SongfyIcons.outputGlyph(audioOutput.kind) else SongfyIcons.Buds,
                                contentDescription = null,
                                tint = if (isBluetoothActive) MaterialTheme.colorScheme.primary else Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (audioOutput.deviceName != null) bluetoothName else "Bluetooth Devices",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = if (isBluetoothActive) "Connected (Active Output)" else "Tap to pair or connect headphones/speakers",
                                color = if (isBluetoothActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.55f),
                                fontSize = 12.sp,
                            )
                        }
                        if (isBluetoothActive) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.navigate_next),
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.40f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ── Option 3: Google Cast ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isCasting) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f))
                        .let {
                            if (isCasting) it.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                            else it
                        }
                        .clickable {
                            if (!isCasting) {
                                try {
                                    val intent = Intent(Settings.ACTION_CAST_SETTINGS)
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Open Cast settings from system panel", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        .padding(14.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isCasting) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.10f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(if (isCasting) R.drawable.cast_connected else R.drawable.cast),
                                    contentDescription = null,
                                    tint = if (isCasting) MaterialTheme.colorScheme.primary else Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = castDeviceName ?: "Google Cast / Chromecast",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = if (isCasting) "Streaming Active" else "Chromecast, Nest, Smart TV",
                                    color = if (isCasting) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.55f),
                                    fontSize = 12.sp,
                                )
                            }

                            if (isCasting) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(Color.Red.copy(alpha = 0.18f))
                                        .clickable {
                                            castHandler?.disconnect()
                                            Toast.makeText(context, "Disconnected from Cast", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                ) {
                                    Text(
                                        text = "Disconnect",
                                        color = Color(0xFFFF6B6B),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            } else {
                                Icon(
                                    painter = painterResource(R.drawable.navigate_next),
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.40f),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        // Cast volume slider if currently streaming
                        if (isCasting) {
                            Spacer(modifier = Modifier.height(12.dp))
                            var draggingVol by remember { mutableStateOf(false) }
                            var dragVolVal by remember { mutableFloatStateOf(castVolume) }
                            val currentVol = if (draggingVol) dragVolVal else castVolume

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = SongfyIcons.SpeakerLow,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.65f),
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))

                                val sliderState = remember { SliderState(value = currentVol, trackRange = 0f..1f) }
                                sliderState.value = currentVol.coerceIn(0f, 1f)

                                Slider(
                                    state = sliderState,
                                    onValueChange = { newVal ->
                                        draggingVol = true
                                        dragVolVal = newVal
                                        castHandler?.setVolume(newVal)
                                    },
                                    onValueChangeFinished = {
                                        draggingVol = false
                                        castHandler?.setVolume(dragVolVal)
                                    },
                                    thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                                    track = { state ->
                                        PlayerSliderTrack(
                                            sliderState = state,
                                            trackHeight = 5.dp,
                                            colors = SliderDefaults.colors(
                                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                                inactiveTrackColor = Color.White.copy(alpha = 0.20f),
                                            ),
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                )

                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = SongfyIcons.SpeakerHigh,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.65f),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
