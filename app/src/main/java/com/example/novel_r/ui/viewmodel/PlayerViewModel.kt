package com.example.novel_r.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.novel_r.service.PlayerService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import com.example.novel_r.util.UrlUtils

enum class PlaybackScope { SINGLE, LIST }
enum class PlaybackAction { STOP, LOOP }

/**
 * 播放器 ViewModel
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()
    
    data class PlayerUiState(
        val isPlaying: Boolean = false,
        val currentPosition: Long = 0L,
        val duration: Long = 0L,
        val playbackSpeed: Float = 1.0f,
        val currentTitle: String = "未選擇檔案",
        val currentFilePath: String? = null, // 目前正在播放的檔案路徑
        val isLockMode: Boolean = false,
        val sleepTimer: Long? = null, // 剩餘秒數
        val errorMessage: String? = null, // 錯誤訊息
        val playbackScope: PlaybackScope = PlaybackScope.LIST,
        val playbackAction: PlaybackAction = PlaybackAction.STOP
    )
    
    private var pendingMediaId: String? = null
    private var pendingTitle: String? = null
    private var pendingArtist: String? = null

    init {
        initializeMediaController()
    }

    private var playlist: List<com.example.novel_r.data.local.AudioFile> = emptyList()
    private var playlistLoaded = false

    private suspend fun loadPlaylistIfNeeded() {
        if (!playlistLoaded) {
            try {
                withContext(Dispatchers.IO) {
                    playlist = audioRepository.getAllAudioFiles().first()
                    playlistLoaded = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // 如果載入失敗, 使用空清單
                playlist = emptyList()
            }
        }
    }
    
    private fun initializeMediaController() {
        val sessionToken = SessionToken(
            getApplication(),
            ComponentName(getApplication(), PlayerService::class.java)
        )
        
        controllerFuture = MediaController.Builder(getApplication(), sessionToken).buildAsync()
        controllerFuture?.addListener(
            {
                try {
                    mediaController = controllerFuture?.get()
                    
                    // 添加監聽器以同步狀態
                    mediaController?.addListener(object : androidx.media3.common.Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
                            if (isPlaying) {
                                startProgressMonitoring()
                            } else {
                                saveCurrentProgress() // 暫停時儲存進度
                                stopProgressMonitoring()
                            }
                        }
                        
                        override fun onEvents(player: androidx.media3.common.Player, events: androidx.media3.common.Player.Events) {
                             if (events.containsAny(
                                 androidx.media3.common.Player.EVENT_PLAYBACK_STATE_CHANGED,
                                 androidx.media3.common.Player.EVENT_IS_PLAYING_CHANGED,
                                 androidx.media3.common.Player.EVENT_POSITION_DISCONTINUITY
                             )) {
                                 updatePlaybackState()
                             }
                             
                             // 處理自動播放邏輯
                             if (events.contains(androidx.media3.common.Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                                 if (player.playbackState == androidx.media3.common.Player.STATE_ENDED) {
                                     onTrackEnded()
                                 }
                             }
                        }

                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            val errorMsg = "撥放發生錯誤: ${error.message ?: "未知原因"}"
                            val mediaId = _uiState.value.currentFilePath
                            
                            // 如果是 403 錯誤且是網路串流，嘗試自動刷新網址
                            if (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS || 
                                error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED) {
                                
                                if (mediaId != null && mediaId.startsWith("http")) {
                                    android.util.Log.d("PlayerViewModel", "Detected playback error (403 or similar), attempting auto-refresh...")
                                    playMedia(mediaId, _uiState.value.currentTitle)
                                    return
                                }
                            }
                            
                            _uiState.value = _uiState.value.copy(errorMessage = errorMsg, isPlaying = false)
                        }
                    })

                    // 如果有等待播放的媒體，則開始播放
                    pendingMediaId?.let { mediaId ->
                        playMedia(mediaId, pendingTitle, pendingArtist)
                        pendingMediaId = null
                        pendingTitle = null
                        pendingArtist = null
                    }
                    updatePlaybackState()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            MoreExecutors.directExecutor()
        )
    }
    
    private var progressJob: kotlinx.coroutines.Job? = null

    private fun startProgressMonitoring() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                updatePlaybackState()
                // 定期儲存進度 (每 10 秒)
                if (System.currentTimeMillis() % 10000 < 1000) {
                    saveCurrentProgress()
                }
                kotlinx.coroutines.delay(1000) // 每秒更新一次
            }
        }
    }
    
    private fun saveCurrentProgress() {
        mediaController?.let { controller ->
            val currentMediaId = controller.currentMediaItem?.mediaId ?: return
            val position = controller.currentPosition
            val duration = controller.duration
            val title = _uiState.value.currentTitle
            
            if (duration > 0 && position > 0) {
                viewModelScope.launch(Dispatchers.IO) {
                    audioRepository.saveProgress(
                        filePath = currentMediaId,
                        fileName = title,
                        fileSize = 0, // 無法獲取，暫為 0
                        position = position,
                        duration = duration
                    )
                }
            }
        }
    }

    private fun stopProgressMonitoring() {
        progressJob?.cancel()
    }
    
    /**
     * 播放/暫停
     */
    fun togglePlayPause() {
        mediaController?.let { controller ->
            if (controller.isPlaying) {
                controller.pause()
            } else {
                // 檢查是否網址已過期 (針對 YouTube 等串流)
                val currentMediaItem = controller.currentMediaItem
                val mediaId = currentMediaItem?.mediaId
                val currentUri = currentMediaItem?.localConfiguration?.uri?.toString()
                
                if (mediaId != null && currentUri != null && mediaId.startsWith("http") && UrlUtils.isUrlExpired(currentUri)) {
                    android.util.Log.d("PlayerViewModel", "Stream URL expired before resume, refreshing...")
                    playMedia(mediaId, _uiState.value.currentTitle)
                } else {
                    controller.play()
                }
            }
        }
    }
    
    /**
     * 快進
     */
    fun fastForward(seconds: Int) {
        mediaController?.let { controller ->
            val duration = controller.duration
            if (duration != androidx.media3.common.C.TIME_UNSET) {
                val newPosition = (controller.currentPosition + seconds * 1000)
                    .coerceAtMost(duration)
                controller.seekTo(newPosition)
                updatePlaybackState()
            }
        }
    }
    
    /**
     * 快退
     */
    fun rewind(seconds: Int) {
        mediaController?.let { controller ->
            val newPosition = (controller.currentPosition - seconds * 1000)
                .coerceAtLeast(0)
            controller.seekTo(newPosition)
            updatePlaybackState()
        }
    }
    
    /**
     * 跳轉到指定位置
     */
    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        updatePlaybackState()
    }

    /**
     * 下一首
     */
    fun skipToNext() {
        viewModelScope.launch {
            loadPlaylistIfNeeded()
            
            if (playlist.isEmpty()) return@launch

            mediaController?.let { controller ->
                val currentMediaId = controller.currentMediaItem?.mediaId
                val currentIndex = playlist.indexOfFirst { it.filePath == currentMediaId }
                
                val nextIndex = if (currentIndex == -1 || currentIndex == playlist.lastIndex) {
                     0 // 循環播放，回到第一首
                } else {
                    currentIndex + 1
                }
                
                val nextFile = playlist[nextIndex]
                playMedia(nextFile.filePath, nextFile.fileName)
            }
        }
    }

    /**
     * 上一首
     */
    fun skipToPrevious() {
        viewModelScope.launch {
            loadPlaylistIfNeeded()
            
            if (playlist.isEmpty()) return@launch

            mediaController?.let { controller ->
                val currentMediaId = controller.currentMediaItem?.mediaId
                val currentIndex = playlist.indexOfFirst { it.filePath == currentMediaId }
                
                // 上一首通常不受自動播放停止與否的限制，永遠可以手動切換
                val prevIndex = if (currentIndex <= 0) {
                     playlist.lastIndex // 循環播放，回到最後一首
                } else {
                    currentIndex - 1
                }
                
                val prevFile = playlist[prevIndex]
                playMedia(prevFile.filePath, prevFile.fileName)
            }
        }
    }

    private fun onTrackEnded() {
        val scope = _uiState.value.playbackScope
        val action = _uiState.value.playbackAction

        // Case 1: Single + Stop -> 自然停止 (Player已進入ENDED)，無需額外操作
        if (scope == PlaybackScope.SINGLE && action == PlaybackAction.STOP) {
            // Do nothing, let it stop.
            return
        }

        // Case 2: Single + Loop -> 重播
        if (scope == PlaybackScope.SINGLE && action == PlaybackAction.LOOP) {
             mediaController?.seekTo(0)
             mediaController?.play()
             return
        }

        // Case 3 & 4: List
        if (scope == PlaybackScope.LIST) {
            viewModelScope.launch {
                loadPlaylistIfNeeded()
                if (playlist.isEmpty()) return@launch

                mediaController?.let { controller ->
                    val currentMediaId = controller.currentMediaItem?.mediaId
                    val currentIndex = playlist.indexOfFirst { it.filePath == currentMediaId }
                    
                    if (currentIndex == -1) return@let

                    if (currentIndex < playlist.lastIndex) {
                        // 還有下一首，直接播放
                        val nextFile = playlist[currentIndex + 1]
                        playMedia(nextFile.filePath, nextFile.fileName)
                    } else {
                        // 已經是最後一首
                        if (action == PlaybackAction.LOOP) {
                            // Loop -> 回到第一首
                            val firstFile = playlist[0]
                            playMedia(firstFile.filePath, firstFile.fileName)
                        } else {
                            // Stop -> 停止，無需操作
                        }
                    }
                }
            }
        }
    }

    fun togglePlaybackScope() {
        val newScope = if (_uiState.value.playbackScope == PlaybackScope.LIST) PlaybackScope.SINGLE else PlaybackScope.LIST
        _uiState.value = _uiState.value.copy(playbackScope = newScope)
    }

    fun togglePlaybackAction() {
        val newAction = if (_uiState.value.playbackAction == PlaybackAction.STOP) PlaybackAction.LOOP else PlaybackAction.STOP
        _uiState.value = _uiState.value.copy(playbackAction = newAction)
    }

    /**
     * 播放指定媒體檔案
     */
    // Lazy initialization of Repository to avoid context issues in init block if not ready
    private val database by lazy {
        androidx.room.Room.databaseBuilder(
            getApplication(),
            com.example.novel_r.data.local.AppDatabase::class.java,
            "audiobook_database"
        ).build()
    }

    private val audioRepository by lazy {
        com.example.novel_r.data.repository.AudioRepository(
            database.audioFileDao(),
            database.audioProgressDao(),
            database.playlistBookmarkDao(),
            getApplication()
        )
    }

    private val youTubeRepository by lazy {
        com.example.novel_r.data.repository.YouTubeRepository(getApplication())
    }

    /**
     * 播放指定媒體檔案
     */
    /**
     * 播放指定媒體檔案
     */
    fun playMedia(mediaId: String, title: String? = null, artist: String? = null) {
        // 1. 立即更新 UI 標題，避免等待解析時標題空白
        _uiState.value = _uiState.value.copy(
            currentTitle = title ?: "載入中...",
            currentFilePath = mediaId,
            isPlaying = true
        )

        if (mediaController == null) {
            // 控制器尚未準備好，暫存請求
            pendingMediaId = mediaId
            pendingTitle = title
            pendingArtist = artist
            return
        }

        mediaController?.let { controller ->
            val currentMediaId = controller.currentMediaItem?.mediaId
            if (currentMediaId == mediaId) {
                // 如果是同一首歌曲，直接播放（Resume）
                if (!controller.isPlaying) {
                     controller.play()
                }
                return
            }
            
            // 儲存上一首的進度
            saveCurrentProgress()

            // 啟動 Coroutine 來讀取讀取進度並播放
            viewModelScope.launch {
                try {
                    val file = try {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                           // 試圖從資料庫獲取檔案資訊，包含快取的 streamUrl
                           audioRepository.getAllAudioFiles().first().find { it.filePath == mediaId }
                        }
                    } catch (e: Exception) { null }

                    val savedProgress = try {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                           audioRepository.getProgress(mediaId)
                        }
                    } catch (e: Exception) { null }
                    
                    // 檢查是否為串流並解析真實 URL
                    val realUri = try {
                        if (mediaId.startsWith("http")) {
                            // 優先使用快取的 streamUrl 以加快速度
                            val cachedUrl = file?.streamUrl
                            val isExpired = UrlUtils.isUrlExpired(cachedUrl)
                            
                            if (cachedUrl != null && !isExpired) {
                                android.net.Uri.parse(cachedUrl)
                            } else {
                                if (isExpired) {
                                    android.util.Log.d("PlayerViewModel", "Stream URL expired, refreshing...")
                                }
                                // 沒有快取或已過期，嘗試解析
                                val result = youTubeRepository.getStreamInfo(mediaId)
                                val info = result.getOrNull()
                                if (info?.streamUrl != null) {
                                    // 更新資料庫快取
                                    audioRepository.insertFile(info)
                                    android.net.Uri.parse(info.streamUrl)
                                } else {
                                    android.net.Uri.parse(mediaId)
                                }
                            }
                        } else {
                            if (mediaId.startsWith("content://") || mediaId.startsWith("file://")) {
                                android.net.Uri.parse(mediaId)
                            } else {
                                android.net.Uri.fromFile(java.io.File(mediaId))
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                         if (mediaId.startsWith("http")) android.net.Uri.parse(mediaId) else android.net.Uri.fromFile(java.io.File(mediaId))
                    }

                    val mediaItem = androidx.media3.common.MediaItem.Builder()
                        .setMediaId(mediaId)
                        .setUri(realUri)
                        .setMediaMetadata(
                            androidx.media3.common.MediaMetadata.Builder()
                                .setTitle(title ?: file?.fileName ?: "未知標題")
                                .setArtist(artist ?: "未知作者")
                                .build()
                        )
                        .build()

                    // 計算起始位置：如果有儲存的進度，則跳轉 (且稍微倒退一點點，例如 2 秒)
                    val startPosition = if (savedProgress != null && savedProgress.lastPosition > 0) {
                        (savedProgress.lastPosition - 2000).coerceAtLeast(0)
                    } else {
                        0L
                    }

                    controller.setMediaItem(mediaItem, startPosition)
                    controller.prepare()
                    controller.play()
                    
                    // 再次確認更新 UI 標題 (如果原本傳入 null，現在可以用 DB 查到的)
                    if (title == null && file != null) {
                         _uiState.value = _uiState.value.copy(currentTitle = file.fileName)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    _uiState.value = _uiState.value.copy(errorMessage = "無法播放檔案: ${e.localizedMessage}")
                }
            }
        }
    }
    
    /**
     * 設定播放速度
     */
    fun setPlaybackSpeed(speed: Float) {
        mediaController?.setPlaybackSpeed(speed)
        _uiState.value = _uiState.value.copy(playbackSpeed = speed)
    }
    
    /**
     * 切換鎖定模式
     */
    fun toggleLockMode() {
        _uiState.value = _uiState.value.copy(isLockMode = !_uiState.value.isLockMode)
    }
    
    /**
     * 設定睡眠計時器（秒）
     */
    fun setSleepTimer(seconds: Long) {
        if (seconds <= 0) {
            _uiState.value = _uiState.value.copy(sleepTimer = null)
            return
        }
        
        _uiState.value = _uiState.value.copy(sleepTimer = seconds)
        
        // 啟動倒數計時
        viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                kotlinx.coroutines.delay(1000)
                remaining--
                _uiState.value = _uiState.value.copy(sleepTimer = remaining)
            }
            
            // 時間到，暫停播放
            mediaController?.pause()
            _uiState.value = _uiState.value.copy(sleepTimer = null)
        }
    }
    
    /**
     * 取消睡眠計時器
     */
    fun cancelSleepTimer() {
        _uiState.value = _uiState.value.copy(sleepTimer = null)
    }
    
    /**
     * 更新播放狀態
     */
    private fun updatePlaybackState() {
        mediaController?.let { controller ->
            val hasDuration = controller.duration > 0
            _uiState.value = _uiState.value.copy(
                isPlaying = controller.isPlaying,
                currentPosition = controller.currentPosition,
                duration = if (hasDuration) controller.duration else 0L
            )
        }
    }

    /**
     * 清除錯誤訊息
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
    
    override fun onCleared() {
        stopProgressMonitoring()
        mediaController?.release()
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        super.onCleared()
    }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
