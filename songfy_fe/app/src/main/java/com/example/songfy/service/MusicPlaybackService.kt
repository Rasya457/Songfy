package com.lagu.songfy.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.lagu.songfy.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MusicPlaybackService : MediaSessionService() {

    @Inject
    lateinit var player: ExoPlayer

    @Inject
    lateinit var playbackEventBus: PlaybackEventBus

    @Inject
    lateinit var playbackStateManager: PlaybackStateManager

    private var mediaSession: MediaSession? = null
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "songfy_playback_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP_PLAYBACK = "com.lagu.songfy.action.STOP_PLAYBACK"
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                acquireWakeLock()
            } else {
                releaseWakeLock()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // 1. Setup DefaultMediaNotificationProvider with custom channel and small icon
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(NOTIFICATION_CHANNEL_ID)
            .setNotificationId(NOTIFICATION_ID)
            .build()
        notificationProvider.setSmallIcon(R.drawable.ic_music_note)
        setMediaNotificationProvider(notificationProvider)

        // 2. Setup Session Activity (tap notification to open app)
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        val stopCommand = SessionCommand(ACTION_STOP_PLAYBACK, Bundle.EMPTY)
        val stopButton = CommandButton.Builder()
            .setDisplayName("Tutup")
            .setIconResId(android.R.drawable.ic_menu_close_clear_cancel)
            .setSessionCommand(stopCommand)
            .build()

        // 3. Wrap player in ForwardingPlayer so COMMAND_SEEK_TO_NEXT and COMMAND_SEEK_TO_PREVIOUS
        // are recognized as available by Media3, exposing both Prev and Next buttons in the notification!
        val forwardingPlayer = object : ForwardingPlayer(player) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
                    else -> super.isCommandAvailable(command)
                }
            }

            override fun hasNextMediaItem(): Boolean = true
            override fun hasPreviousMediaItem(): Boolean = true

            override fun seekToNext() {
                playbackStateManager.skipNext()
            }

            override fun seekToNextMediaItem() {
                playbackStateManager.skipNext()
            }

            override fun seekToPrevious() {
                playbackStateManager.skipPrevious()
            }

            override fun seekToPreviousMediaItem() {
                playbackStateManager.skipPrevious()
            }
        }

        // 4. Callback to handle Next, Previous, and Custom commands (like Stop 'X')
        val callback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val connectionResult = super.onConnect(session, controller)
                val sessionCommands = connectionResult.availableSessionCommands.buildUpon()
                    .add(stopCommand)
                    .build()
                val availablePlayerCommands = connectionResult.availablePlayerCommands.buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailablePlayerCommands(availablePlayerCommands)
                    .setAvailableSessionCommands(sessionCommands)
                    .setCustomLayout(listOf(stopButton))
                    .build()
            }

            override fun onPlayerCommandRequest(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                playerCommand: Int
            ): Int {
                // Return super so Media3 routes Player commands directly to forwardingPlayer methods
                return super.onPlayerCommandRequest(session, controller, playerCommand)
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                if (customCommand.customAction == ACTION_STOP_PLAYBACK) {
                    stopPlaybackCompletely()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                return super.onCustomCommand(session, controller, customCommand, args)
            }
        }

        val builder = MediaSession.Builder(this, forwardingPlayer)
            .setCallback(callback)
            .setCustomLayout(listOf(stopButton))
        if (pendingIntent != null) {
            builder.setSessionActivity(pendingIntent)
        }
        val session = builder.build()
        mediaSession = session
        addSession(session)

        player.addListener(playerListener)
        if (player.isPlaying) {
            acquireWakeLock()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Disarm Android 5-second startForegroundService timer immediately if no media yet.
        // Once media items are loaded, MediaSessionService automatically manages the rich MediaStyle notification.
        if (player.mediaItemCount == 0) {
            try {
                val notification = createInitialNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (e: Exception) {
                Log.w("MusicPlaybackService", "Immediate startForeground failed: ")
            }
        }
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        // Kunci Requirement 4: Pertahankan status foreground meski di-pause selama ada lagu (mediaItemCount > 0)
        // agar ActivityManager tidak mematikan service ("app idle") saat aplikasi di-swipe tutup dari Recent Apps,
        // dan notifikasi tetap standby dengan tombol Play.
        val keepForeground = startInForegroundRequired || (player.mediaItemCount > 0)
        super.onUpdateNotification(session, keepForeground)
    }

    private fun createInitialNotification(): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        val metadata = player.mediaMetadata
        val title = metadata.title?.toString()?.ifBlank { null } ?: "Songfy"
        val artist = metadata.artist?.toString()?.ifBlank { null } ?: "Memutar musik..."

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(R.drawable.ic_music_note)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        val artworkBytes = metadata.artworkData
        if (artworkBytes != null && artworkBytes.size > 0) {
            try {
                val bitmap = BitmapFactory.decodeByteArray(artworkBytes, 0, artworkBytes.size)
                if (bitmap != null) {
                    builder.setLargeIcon(bitmap)
                }
            } catch (_: Exception) {}
        }

        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent)
        }
        return builder.build()
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Songfy:PlaybackWakeLock"
                )?.apply {
                    setReferenceCounted(false)
                }
            }
            wakeLock?.acquire(60 * 60 * 1000L) // 60 mins maximum lock timeout
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
    }

    /** Stop total dari tombol X: matikan audio, cabut foreground, bunuh service. */
    private fun stopPlaybackCompletely() {
        playbackStateManager.stopPlayback()
        releaseWakeLock()
        try {
            player.stop()
            player.clearMediaItems()
        } catch (_: Exception) { /* player belum siap */ }
        try {
            mediaSession?.let { removeSession(it) }
        } catch (_: Exception) {}
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.cancel(NOTIFICATION_ID)
        } catch (_: Exception) { /* notification manager ignore */ }
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Songfy Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Songfy music playback controls"
                setShowBadge(false)
                setSound(null, null)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val activePlayer = mediaSession?.player
        if (activePlayer == null || activePlayer.mediaItemCount == 0) {
            releaseWakeLock()
            stopSelf()
        }
    }

    override fun onDestroy() {
        player.removeListener(playerListener)
        releaseWakeLock()
        mediaSession?.run {
            removeSession(this)
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
