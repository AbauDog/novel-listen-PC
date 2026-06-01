package com.example.novel_r.service

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.novel_r.data.repository.AudioRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 播放管理器 - 封裝 ExoPlayer 播放邏輯
 */
class PlaybackManager(
    private val context: Context,
    private val exoPlayer: ExoPlayer,
    private val audioRepository: AudioRepository
) {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    
    // 播放狀態
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    // 進度保存任務
    private var progressSaveJob: Job? = null

    data class PlaybackState(
        val isPlaying: Boolean = false,
        val currentPosition: Long = 0L,
        val duration: Long = 0L,
        val playbackSpeed: Float = 1.0f,
        val currentMediaUri: String? = null,
        val currentMediaTitle: String? = null
    )

    init {
        setupPlayerListeners()
    }

    private fun setupPlayerListeners() {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlaybackState()
                if (isPlaying) {
                    startProgressTracking()
                } else {
                    stopProgressTracking()
                    saveCurrentProgress()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        updatePlaybackState()
                    }
                    Player.STATE_ENDED -> {
                        updatePlaybackState()
                        saveCurrentProgress()
                        // 確保將進度設為 duration 以標記完成
                         _playbackState.value = _playbackState.value.copy(
                            currentPosition = _playbackState.value.duration
                        )
                        saveCurrentProgress()
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateCurrentMediaInfo(mediaItem)
            }
        })
        
        // 初始化時立即更新一次，避免錯過狀態
        updateCurrentMediaInfo(exoPlayer.currentMediaItem)
    }

    private fun updateCurrentMediaInfo(mediaItem: MediaItem?) {
        if (mediaItem == null) return

        // 優先使用 mediaId，因為這是我們在 ViewModel 中設定的主要識別碼 (即檔案路徑)
        // 只有當 mediaId 為預設值時才嘗試使用 URI
        val mediaId = if (mediaItem.mediaId != MediaItem.DEFAULT_MEDIA_ID) {
            mediaItem.mediaId
        } else {
            mediaItem.localConfiguration?.uri?.toString()
        }
        
        val title = mediaItem.mediaMetadata.title?.toString()

        if (mediaId != null) {
            _playbackState.value = _playbackState.value.copy(
                currentMediaUri = mediaId,
                currentMediaTitle = title ?: "未知標題"
            )
        }
    }

    /**
     * 播放音訊
     */
    fun play(uri: Uri, title: String, startPosition: Long = 0L) {
        val mediaItem = MediaItem.fromUri(uri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        
        if (startPosition > 0) {
            exoPlayer.seekTo(startPosition)
        }
        
        exoPlayer.play()
        
        _playbackState.value = _playbackState.value.copy(
            currentMediaUri = uri.toString(),
            currentMediaTitle = title
        )
    }

    /**
     * 暫停播放
     */
    fun pause() {
        exoPlayer.pause()
        saveCurrentProgress()
    }

    /**
     * 恢復播放
     */
    fun resume() {
        exoPlayer.play()
    }

    /**
     * 停止播放
     */
    fun stop() {
        exoPlayer.stop()
        saveCurrentProgress()
    }

    /**
     * 跳轉到指定位置
     */
    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        updatePlaybackState()
    }

    /**
     * 快進
     */
    fun fastForward(seconds: Int) {
        val newPosition = (exoPlayer.currentPosition + seconds * 1000).coerceAtMost(exoPlayer.duration)
        seekTo(newPosition)
    }

    /**
     * 快退
     */
    fun rewind(seconds: Int) {
        val newPosition = (exoPlayer.currentPosition - seconds * 1000).coerceAtLeast(0)
        seekTo(newPosition)
    }

    /**
     * 設定播放速度
     */
    fun setPlaybackSpeed(speed: Float) {
        exoPlayer.setPlaybackSpeed(speed)
        _playbackState.value = _playbackState.value.copy(playbackSpeed = speed)
    }

    /**
     * 降低音量（Duck mode）
     */
    fun duck(enable: Boolean) {
        exoPlayer.volume = if (enable) 0.3f else 1.0f
    }

    /**
     * 開始進度追蹤
     */
    private fun startProgressTracking() {
        progressSaveJob?.cancel()
        progressSaveJob = scope.launch {
            while (isActive && exoPlayer.isPlaying) {
                updatePlaybackState()
                delay(1000) // 每秒更新一次
            }
        }
    }

    /**
     * 停止進度追蹤
     */
    private fun stopProgressTracking() {
        progressSaveJob?.cancel()
    }

    /**
     * 更新播放狀態
     */
    private fun updatePlaybackState() {
        _playbackState.value = _playbackState.value.copy(
            isPlaying = exoPlayer.isPlaying,
            currentPosition = exoPlayer.currentPosition,
            duration = exoPlayer.duration.coerceAtLeast(0)
        )
    }

    /**
     * 保存當前播放進度
     */
    private fun saveCurrentProgress() {
        val state = _playbackState.value
        val uri = state.currentMediaUri ?: return
        val title = state.currentMediaTitle ?: return

        scope.launch(Dispatchers.IO) {
            try {
                audioRepository.saveProgress(
                    filePath = uri,
                    fileName = title,
                    fileSize = 0L, // 需要從其他地方取得
                    position = state.currentPosition,
                    duration = state.duration
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 釋放資源
     */
    fun release() {
        stopProgressTracking()
        saveCurrentProgress()
        scope.launch {
            delay(500) // 等待保存完成
        }
    }
}
