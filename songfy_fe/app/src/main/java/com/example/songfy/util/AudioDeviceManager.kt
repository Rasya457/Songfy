package com.lagu.songfy.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext

data class AudioOutputDeviceState(
    val isHeadphones: Boolean,
    val deviceName: String,
    val icon: ImageVector
)

/**
 * Real-time audio output device state observer.
 * Automatically detects when wired headphones, Bluetooth earbuds, or USB-C headsets
 * are connected or disconnected, updating Compose state immediately.
 */
@Composable
fun rememberAudioOutputDevice(): AudioOutputDeviceState {
    val context = LocalContext.current
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    fun queryCurrentDevice(): AudioOutputDeviceState {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

            // Look for any connected headphones / earphones / bluetooth / usb audio
            val headphoneDev = devices.firstOrNull { dev ->
                dev.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                dev.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                dev.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                dev.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (
                    dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    dev.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
                ))
            }

            if (headphoneDev != null) {
                val isBluetooth = headphoneDev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                                  headphoneDev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                val name = headphoneDev.productName?.toString().takeIf { !it.isNullOrBlank() }
                    ?: if (isBluetooth) "Perangkat Bluetooth" else "Headphone"
                return AudioOutputDeviceState(
                    isHeadphones = true,
                    deviceName = name,
                    icon = Icons.Default.Headphones // Always earphone/headphone icon per user request
                )
            }
        } else {
            @Suppress("DEPRECATION")
            if (audioManager.isWiredHeadsetOn || audioManager.isBluetoothA2dpOn) {
                return AudioOutputDeviceState(
                    isHeadphones = true,
                    deviceName = "Headphone",
                    icon = Icons.Default.Headphones
                )
            }
        }

        return AudioOutputDeviceState(
            isHeadphones = false,
            deviceName = "Speaker Ponsel",
            icon = Icons.Default.Smartphone
        )
    }

    var deviceState by remember { mutableStateOf(queryCurrentDevice()) }

    DisposableEffect(context, audioManager) {
        // 1. AudioDeviceCallback (API 23+)
        val audioDeviceCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                deviceState = queryCurrentDevice()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                deviceState = queryCurrentDevice()
            }
        }
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))

        // 2. BroadcastReceiver for ACTION_HEADSET_PLUG & Bluetooth connections (instant broadcast)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) {
                deviceState = queryCurrentDevice()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED")
            addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED")
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        }
        context.registerReceiver(receiver, filter)

        onDispose {
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }
    }

    return deviceState
}
