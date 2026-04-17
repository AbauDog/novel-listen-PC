package com.example.novel_r.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaNotification
import androidx.media3.session.CommandButton
import com.google.common.collect.ImmutableList
import com.example.novel_r.MainActivity
import com.example.novel_r.R
import com.example.novel_r.data.local.AppDatabase
import com.example.novel_r.data.repository.AudioRepository
import androidx.room.Room
import android.media.AudioManager
import android.content.Context
import androidx.media3.session.DefaultMediaNotificationProvider
import com.example.novel_r.data.repository.YouTubeRepository
import com.example.novel_r.util.UrlUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 播放器服務 - 提供背景播放功能
 */
class PlayerService : MediaSessionService() {
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "audiobook_playback"
        private const val CHANNEL_NAME = "有聲書播放"
        
        const val ACTION_REWIND_60 = "com.example.novel_r.action.REWIND_60"
        const val ACTION_REWIND_30 = "com.example.novel_r.action.REWIND_30"
        const val ACTION_FORWARD_30 = "com.example.novel_r.action.FORWARD_30"
        const val ACTION_FORWARD_60 = "com.example.novel_r.action.FORWARD_60"
        const val ACTION_PLAY_PAUSE = "com.example.novel_r.action.PLAY_PAUSE"
        const val ACTION_STOP = "com.example.novel_r.action.STOP"
    }

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private var audioFocusManager: AudioFocusManager? = null
    private var audioRepository: AudioRepository? = null
    private var youtubeRepository: YouTubeRepository? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        
        // 建立通知頻道
        createNotificationChannel()
        
        // 設定自訂通知提供者 (Media3 方式)
        setMediaNotificationProvider(CustomNotificationProvider())

        // 初始化資料庫和 Repository
        val database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "audiobook_database"
        )
        .addMigrations(AppDatabase.MIGRATION_1_2)
        .build()
        
        audioRepository = AudioRepository(
            database.audioFileDao(),
            database.audioProgressDao(),
            database.playlistBookmarkDao(),
            applicationContext
        )
        
        youtubeRepository = YouTubeRepository(applicationContext)

        // 設定快取資料來源
        val cache = com.example.novel_r.util.AudioPlayerCache.getInstance(this)
        
        // 使用 DefaultDataSource.Factory 以支援 content://, file://, http:// 等所有協定
        val upstreamFactory = androidx.media3.datasource.DefaultDataSource.Factory(this)
            
        val cacheDataSourceFactory = androidx.media3.datasource.cache.CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        
        // 自定義負載控制 (Load Control)，增大緩衝以支援長時間預放
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                120000,          // minBufferMs (2 min)
                1200000,         // maxBufferMs (20 min)
                1000,            // bufferForPlaybackMs (1 sec)
                2000             // bufferForPlaybackAfterRebufferMs (2 sec)
            )
            .setBackBuffer(
                300000,          // backBufferDurationMs (5 min)
                true             // retainBackBufferFromKeyframe
            )
            .build()

        // 初始化 ExoPlayer
        exoPlayer = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(this)
                    .setDataSourceFactory(cacheDataSourceFactory)
            )
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .build(),
                true // handleAudioFocus
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { player ->
                player.addListener(object : Player.Listener {
                    // 不需要手動 startForeground，MediaSessionService 會透過 NotificationProvider 處理
                    override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                         // 強制更新通知
                         mediaSession?.let {
                             // 觸發 MediaSessionService 更新通知的機制有點隱晦，通常它會監聽狀態
                             // 若需要強制刷新，可以重新 setMediaItem 但這不好
                             // 基本上 Media3 會自動呼叫 Provider
                         }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        android.util.Log.e("PlayerService", "Playback error: ${error.errorCodeName} (${error.errorCode})")
                        
                        // 處理 403 錯誤且是網路串流的情境
                        if (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) {
                            val currentMediaItem = player.currentMediaItem
                            val mediaId = currentMediaItem?.mediaId
                            val currentUri = currentMediaItem?.localConfiguration?.uri?.toString()
                            
                            if (mediaId != null && mediaId.startsWith("http")) {
                                android.util.Log.d("PlayerService", "Detected 403 error for stream, attempting background refresh...")
                                refreshAndResume(mediaId, player.currentPosition)
                            }
                        }
                    }
                })
            }
        
        // 初始化播放管理器
        playbackManager = PlaybackManager(
            context = this,
            exoPlayer = exoPlayer!!,
            audioRepository = audioRepository!!
        )
        
        // 初始化音訊焦點管理器
        audioFocusManager = AudioFocusManager(this) { focusChange ->
            handleAudioFocusChange(focusChange)
        }
        
        // 建立 MediaSession
        // 使用 ForwardingPlayer 攔截上一首/下一首指令
        val forwardingPlayer = VolumeControlPlayer(exoPlayer!!)
        
        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setCallback(MediaSessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "顯示正在播放的有聲書"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun refreshAndResume(mediaId: String, position: Long) {
        serviceScope.launch {
            try {
                val result = youtubeRepository?.getStreamInfo(mediaId)
                val info = result?.getOrNull()
                
                if (info?.streamUrl != null) {
                    android.util.Log.d("PlayerService", "Successfully refreshed stream URL in background")
                    
                    // 更新資料庫
                    withContext(Dispatchers.IO) {
                        audioRepository?.insertFile(info)
                    }
                    
                    exoPlayer?.let { player ->
                        val currentItem = player.currentMediaItem
                        val newMediaItem = androidx.media3.common.MediaItem.Builder()
                            .setMediaId(mediaId)
                            .setUri(info.streamUrl)
                            .setMediaMetadata(currentItem?.mediaMetadata ?: androidx.media3.common.MediaMetadata.EMPTY)
                            .build()
                        
                        player.setMediaItem(newMediaItem, position)
                        player.prepare()
                        player.play()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PlayerService", "Failed to refresh stream URL in background", e)
            }
        }
    }

    private fun handleAudioFocusChange(focusChange: AudioFocusManager.FocusChange) {
        when (focusChange) {
            AudioFocusManager.FocusChange.GAIN -> {
                exoPlayer?.volume = 1.0f
                if (playbackManager?.playbackState?.value?.isPlaying == true) {
                    exoPlayer?.play()
                }
            }
            AudioFocusManager.FocusChange.LOSS -> {
                exoPlayer?.pause()
            }
            AudioFocusManager.FocusChange.LOSS_TRANSIENT -> {
                exoPlayer?.pause()
            }
            AudioFocusManager.FocusChange.LOSS_DUCK -> {
                exoPlayer?.volume = 0.5f
            }
        }
    }

    private fun adjustVolume(direction: Int) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            direction,
            AudioManager.FLAG_SHOW_UI
        )
    }

    private inner class CustomNotificationProvider : DefaultMediaNotificationProvider(this@PlayerService) {
        override fun getMediaButtons(
            session: MediaSession,
            playerCommands: Player.Commands,
            customLayout: ImmutableList<CommandButton>,
            showPauseButton: Boolean
        ): ImmutableList<CommandButton> {
            val builder = ImmutableList.builder<CommandButton>()

            // Rewind 60s
            builder.add(
                CommandButton.Builder()
                    .setSessionCommand(androidx.media3.session.SessionCommand(ACTION_REWIND_60, Bundle.EMPTY))
                    .setIconResId(R.drawable.ic_replay_60)
                    .setDisplayName("-1m")
                    .build()
            )

            // Rewind 30s
            builder.add(
                CommandButton.Builder()
                    .setSessionCommand(androidx.media3.session.SessionCommand(ACTION_REWIND_30, Bundle.EMPTY))
                    .setIconResId(R.drawable.ic_replay_30)
                    .setDisplayName("-30s")
                    .build()
            )

            // Play/Pause
            if (showPauseButton) {
                builder.add(
                    CommandButton.Builder()
                        .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                        .setIconResId(android.R.drawable.ic_media_pause)
                        .setDisplayName("暫停")
                        .build()
                )
            } else {
                builder.add(
                    CommandButton.Builder()
                        .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                        .setIconResId(android.R.drawable.ic_media_play)
                        .setDisplayName("播放")
                        .build()
                )
            }

            // Forward 30s
            builder.add(
                 CommandButton.Builder()
                    .setSessionCommand(androidx.media3.session.SessionCommand(ACTION_FORWARD_30, Bundle.EMPTY))
                    .setIconResId(R.drawable.ic_forward_30)
                    .setDisplayName("+30s")
                    .build()
            )

            // Forward 60s
            builder.add(
                CommandButton.Builder()
                    .setSessionCommand(androidx.media3.session.SessionCommand(ACTION_FORWARD_60, Bundle.EMPTY))
                    .setIconResId(R.drawable.ic_forward_60)
                    .setDisplayName("+1m")
                    .build()
            )

            return builder.build()
        }
    }

    // ... (rest of the file until MediaSessionCallback)

    /**
     * 自訂 Player，用於攔截上一首/下一首指令並轉為音量控制
     */
    private inner class VolumeControlPlayer(player: Player) : androidx.media3.common.ForwardingPlayer(player) {

        override fun getAvailableCommands(): Player.Commands {
            return super.getAvailableCommands().buildUpon()
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .build()
        }

        override fun isCommandAvailable(command: Int): Boolean {
            return getAvailableCommands().contains(command)
        }

        override fun seekToNext() {
            android.util.Log.d("PlayerService", "VolumeControlPlayer: seekToNext called - Volume UP")
            // 攔截下一首 -> 音量 +1
            adjustVolume(android.media.AudioManager.ADJUST_RAISE)
        }

        override fun seekToPrevious() {
            android.util.Log.d("PlayerService", "VolumeControlPlayer: seekToPrevious called - Volume DOWN")
            // 攔截上一首 -> 音量 -1
            adjustVolume(android.media.AudioManager.ADJUST_LOWER)
        }
        
        override fun seekToNextMediaItem() {
             android.util.Log.d("PlayerService", "VolumeControlPlayer: seekToNextMediaItem called - Volume UP")
             adjustVolume(android.media.AudioManager.ADJUST_RAISE)
        }
        
        override fun seekToPreviousMediaItem() {
             android.util.Log.d("PlayerService", "VolumeControlPlayer: seekToPreviousMediaItem called - Volume DOWN")
             adjustVolume(android.media.AudioManager.ADJUST_LOWER)
        }
    }

    /**
     * MediaSession 回調
     */
    private inner class MediaSessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = androidx.media3.session.SessionCommands.Builder()
                .add(androidx.media3.session.SessionCommand(ACTION_REWIND_60, Bundle.EMPTY))
                .add(androidx.media3.session.SessionCommand(ACTION_REWIND_30, Bundle.EMPTY))
                .add(androidx.media3.session.SessionCommand(ACTION_FORWARD_30, Bundle.EMPTY))
                .add(androidx.media3.session.SessionCommand(ACTION_FORWARD_60, Bundle.EMPTY))
                .build()

            // 允許所有預設指令 + 自訂指令
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: androidx.media3.session.SessionCommand,
            args: Bundle
        ): com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.SessionResult> {
            when (customCommand.customAction) {
                ACTION_REWIND_60 -> seek(-60000)
                ACTION_REWIND_30 -> seek(-30000)
                ACTION_FORWARD_30 -> seek(30000)
                ACTION_FORWARD_60 -> seek(60000)
            }
            return com.google.common.util.concurrent.Futures.immediateFuture(
                androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS)
            )
        }

        private fun seek(offset: Long) {
            exoPlayer?.let { player ->
                var newPosition = player.currentPosition + offset
                if (newPosition < 0) newPosition = 0
                if (newPosition > player.duration) newPosition = player.duration
                player.seekTo(newPosition)
            }
        }
    }

    override fun onDestroy() {
        serviceScope.launch {
            SupervisorJob().cancel() // 取消所有背景任務
        }
        super.onDestroy()
    }
}


