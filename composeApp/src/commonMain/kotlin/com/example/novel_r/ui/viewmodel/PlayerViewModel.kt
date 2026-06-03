package com.example.novel_r.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.novel_r.data.model.AudioFile
import com.example.novel_r.data.repository.AudioRepository
import com.example.novel_r.util.PlatformAudioPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

enum class PlaybackScope { SINGLE, LIST }
enum class PlaybackAction { STOP, LOOP }
enum class PlaybackMode { SINGLE, LOOP, LIST }

/**
 * 播放器 ViewModel (跨平台版本)
 */
class PlayerViewModel(
    private val audioRepository: AudioRepository,
    private val player: PlatformAudioPlayer
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()
    
    data class PlayerUiState(
        val isPlaying: Boolean = false,
        val currentPosition: Long = 0L,
        val duration: Long = 0L,
        val playbackSpeed: Float = 1.0f,
        val currentTitle: String = "未選擇檔案",
        val currentFilePath: String? = null,
        val isLockMode: Boolean = false,
        val sleepTimer: Long? = null,
        val errorMessage: String? = null,
        val playbackScope: PlaybackScope = PlaybackScope.LIST,
        val playbackAction: PlaybackAction = PlaybackAction.STOP,
        val playbackMode: PlaybackMode = PlaybackMode.LIST,
        val debugLog: String = "",
        val downloadProgress: String? = null, // 目前歌曲的下載進度 (0-100)
        val downloadError: String? = null // 目前歌曲的下載錯誤訊息
    )

    init {
        // 啟動時自動在背景下載/檢查 ffmpeg 與 ffprobe 組件，確保本地大檔案播放時時長解析正確
        viewModelScope.launch(Dispatchers.IO) {
            try {
                audioRepository.ensureFfmpeg()
            } catch (e: Exception) {
                println("[PlayerVM] 背景初始化 ffmpeg/ffprobe 失敗: ${e.message}")
            }
        }

        // 監聽下載進度
        viewModelScope.launch {
            combine(audioRepository.downloadProgressFlow, _uiState) { progressMap, state ->
                val currentUrl = state.currentFilePath?.substringAfter("youtube:")?.trim() ?: ""
                progressMap[currentUrl]
            }.distinctUntilChanged().collect { progress ->
                _uiState.update { it.copy(downloadProgress = progress) }
            }
        }

        // 監聽下載錯誤
        viewModelScope.launch {
            combine(audioRepository.downloadErrorsFlow, _uiState) { errorsMap, state ->
                val currentUrl = state.currentFilePath?.substringAfter("youtube:")?.trim() ?: ""
                errorsMap[currentUrl]
            }.distinctUntilChanged().collect { error ->
                _uiState.update { it.copy(downloadError = error) }
            }
        }
        // 監聽播放狀態
        var lastStateWasPlaying = false
        viewModelScope.launch {
            combine(player.isPlaying, player.currentPosition, player.duration, player.debugLog) { playing, pos, dur, log ->
                arrayOf(playing, pos, dur, log)
            }.collect { arr ->
                val playing = arr[0] as Boolean
                val pos = arr[1] as Long
                val dur = arr[2] as Long
                val log = arr[3] as String
                
                _uiState.update { 
                    it.copy(
                        isPlaying = playing, 
                        currentPosition = pos, 
                        duration = dur,
                        debugLog = log
                    ) 
                }

                // 偵測自然播放結束：從正在播放變為停止播放，且目前播放進度已經到達或極度接近總長度
                if (lastStateWasPlaying && !playing) {
                    val isCompleted = dur > 0 && pos >= (dur - 2000L)
                    if (isCompleted) {
                        println("[PlayerVM] 偵測到音訊自然播放完畢，觸發自動下一首邏輯")
                        handlePlaybackCompletion()
                    }
                }
                lastStateWasPlaying = playing
            }
        }

        // 每 5 秒自動儲存進度
        viewModelScope.launch {
            while (true) {
                delay(5000)
                if (_uiState.value.isPlaying) {
                    saveCurrentProgress()
                }
            }
        }

        // 監聽下載完成通知，實現串流到本地 MP3 的「熱切換」
        viewModelScope.launch {
            audioRepository.downloadCompletedFlow.collect { downloadedFile ->
                val state = _uiState.value
                val currentPath = state.currentFilePath
                val currentUrl = currentPath?.substringAfter("youtube:")?.trim() ?: ""
                val originalUrl = downloadedFile.originalUrl?.trim() ?: ""
                
                val currentVideoId = com.example.novel_r.data.repository.extractYoutubeVideoId(currentUrl) ?: ""
                val downloadedVideoId = com.example.novel_r.data.repository.extractYoutubeVideoId(originalUrl) ?: ""
                
                // 如果目前正在播放的是「串流」且 Video ID 剛好就是剛下載完的這個檔案
                if (currentPath != null && currentPath.startsWith("youtube:") && 
                    currentVideoId.isNotEmpty() && 
                    currentVideoId == downloadedVideoId) {
                    
                    val logFile = java.io.File(com.example.novel_r.util.getAppDataPath(), "novel_r_debug.log")
                    logFile.appendText("Hot swap triggered! From $currentPath to ${downloadedFile.filePath} at ${System.currentTimeMillis()}\n")
                    
                    println("[PlayerVM] 偵測到串流下載完成，正在自動切換至本地 MP3: ${downloadedFile.fileName}")
                    
                    // 記錄目前精確位置並直接重新啟動播放（切換至本地路徑）
                    playMedia(
                        filePath = downloadedFile.filePath, 
                        title = downloadedFile.fileName, 
                        startTimeMs = state.currentPosition
                    )

                    // 切換完成後，異步刪除舊的串流與原始 URL 進度
                    viewModelScope.launch {
                        logFile.appendText("Cleaning up old stream progress after hot-swap...\n")
                        audioRepository.deleteProgress(currentPath)
                        logFile.appendText("Deleted progress for: $currentPath\n")
                        downloadedFile.originalUrl?.let {
                            audioRepository.deleteProgress(it)
                            logFile.appendText("Deleted progress for originalUrl: $it\n")
                        }
                    }
                }
            }
        }
    }

    /**
     * 儲存目前播放進度 (對外開放，供退出程式前呼叫)
     */
    fun saveProgress() {
        saveCurrentProgress()
    }

    /**
     * 儲存目前播放進度 (對外開放，供退出程式前呼叫，同步阻塞)
     */
    fun saveProgressBlocking() {
        runBlocking {
            saveCurrentProgressSuspend()
        }
    }
    private suspend fun saveCurrentProgressSuspend() {
        val path = _uiState.value.currentFilePath ?: return
        val pos = player.currentPosition.value
        val dur = player.duration.value
        if (dur <= 0) return

        // 同時儲存到目前路徑
        audioRepository.saveProgress(path, pos, dur)
        
        val allFiles = audioRepository.getAllAudioFiles().first()
        val audioFile = allFiles.find { it.filePath == path }
        // 只有當音訊檔案是「串流」狀態下，才同步存入原始網址
        if (audioFile?.isStream == true) {
            audioFile.originalUrl?.let { url ->
                if (url != path) {
                    audioRepository.saveProgress(url, pos, dur)
                }
            }
        }
    }

    fun playMedia(filePath: String, title: String? = null, startTimeMs: Long? = null) {
        viewModelScope.launch {
            // 確保存下目前這首的進度
            // 1. 儲存舊進度
            saveCurrentProgressSuspend()
            
            // 2. 尋找目標檔案資訊
            val allFiles = audioRepository.getAllAudioFiles().first()
            val audioFile = allFiles.find { it.filePath == filePath } 
                ?: allFiles.find { it.originalUrl != null && filePath.contains(it.originalUrl!!) }
            
            val finalPath = audioFile?.filePath ?: filePath
            val finalTitle = audioFile?.fileName ?: title ?: "未知檔案"
            var totalDur = audioFile?.duration ?: 0L
            
            // 如果是因為舊的 Bug 導致紀錄中的長度為 0，在這裡即時修復
            if (totalDur <= 0L && finalPath.startsWith("youtube:")) {
                val realUrl = finalPath.substringAfter("youtube:")
                val info = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.example.novel_r.util.createYoutubeDownloader().getStreamInfo(realUrl).getOrNull()
                }
                if (info != null && info.duration > 0) {
                    totalDur = info.duration
                }
            }
            
            _uiState.update { it.copy(currentFilePath = finalPath, currentTitle = finalTitle) }
            
            // 3. 獲取進度 (如果有指定 startTimeMs 則優先使用，否則嘗試從資料庫抓取)
            val finalStartPos = if (startTimeMs != null) {
                startTimeMs
            } else {
                val savedProgress = audioFile?.originalUrl?.let { audioRepository.getProgress(it) } 
                    ?: audioRepository.getProgress(finalPath)
                val savedPos = savedProgress?.lastPosition ?: 0L
                val savedDur = savedProgress?.duration ?: 0L
                val percentage = if (savedDur > 0) (savedPos.toFloat() / savedDur * 100).toInt().coerceIn(0, 100) else 0
                
                if (percentage >= 99) {
                    // 若播放進度已 >= 99%，則點選時歸0重新播放，並同步存檔
                    val targetDur = totalDur.coerceAtLeast(savedDur)
                    audioRepository.saveProgress(finalPath, 0L, targetDur)
                    audioFile?.originalUrl?.let { url ->
                        if (url != finalPath) {
                            audioRepository.saveProgress(url, 0L, targetDur)
                        }
                    }
                    0L
                } else {
                    savedPos
                }
            }
            
            println("[PlayerVM] 開始播放: $finalTitle, 跳轉至: ${finalStartPos}ms, 總長: ${totalDur}ms")
            player.play(finalPath, finalStartPos, totalDur)

            // 4. 若為串流播放且無本地快取，自動觸發背景下載
            if (finalPath.startsWith("youtube:")) {
                val realUrl = finalPath.substringAfter("youtube:").trim()
                viewModelScope.launch(Dispatchers.IO) {
                    audioRepository.processYoutubeUrl(realUrl)
                }
            }
        }
    }

    private fun saveCurrentProgress() {
        viewModelScope.launch {
            saveCurrentProgressSuspend()
        }
    }

    fun stop() {
        viewModelScope.launch {
            saveCurrentProgressSuspend()
            player.stop()
            _uiState.update { it.copy(isPlaying = false) }
        }
    }

    fun togglePlayPause() {
        val state = _uiState.value
        viewModelScope.launch {
            if (state.isPlaying) {
                player.pause()
                saveCurrentProgress() // 暫停時存檔
            } else {
                val path = state.currentFilePath ?: return@launch
                val audioFile = audioRepository.getAllAudioFiles().first().find { it.filePath == path }
                val playUrl = if (audioFile != null && audioFile.streamUrl != null) {
                    audioFile.streamUrl!!
                } else {
                    path
                }
                player.play(playUrl, state.currentPosition, state.duration)
            }
        }
    }

    fun seekTo(position: Long) {
        player.seekTo(position)
        // Seek 後立即存檔一次
        viewModelScope.launch { delay(500); saveCurrentProgress() }
    }

    fun setPlaybackSpeed(speed: Float) {
        player.setSpeed(speed)
        _uiState.update { it.copy(playbackSpeed = speed) }
    }

    fun rewind(seconds: Int) {
        val currentPos = player.currentPosition.value
        val newPos = (currentPos - seconds * 1000L).coerceAtLeast(0L)
        seekTo(newPos)
    }

    fun fastForward(seconds: Int) {
        val currentPos = player.currentPosition.value
        val dur = player.duration.value
        var newPos = currentPos + seconds * 1000L
        if (dur > 0) newPos = newPos.coerceAtMost(dur)
        seekTo(newPos)
    }

    fun skipToNext() {
        playNextSong()
    }

    fun skipToPrevious() {
        viewModelScope.launch {
            val allFiles = audioRepository.getAllAudioFiles().first()
            if (allFiles.isEmpty()) return@launch
            
            val currentPath = _uiState.value.currentFilePath
            val currentIndex = allFiles.indexOfFirst { it.filePath == currentPath }
            
            val prevIndex = if (currentIndex > 0) {
                currentIndex - 1
            } else {
                allFiles.size - 1
            }
            
            val prevFile = allFiles[prevIndex]
            playMedia(prevFile.filePath, prevFile.fileName, startTimeMs = 0L)
        }
    }

    fun playNextSong() {
        viewModelScope.launch {
            val allFiles = audioRepository.getAllAudioFiles().first()
            if (allFiles.isEmpty()) {
                stop()
                return@launch
            }
            
            val currentPath = _uiState.value.currentFilePath
            val currentIndex = allFiles.indexOfFirst { it.filePath == currentPath }
            
            val nextIndex = if (currentIndex != -1 && currentIndex < allFiles.size - 1) {
                currentIndex + 1
            } else {
                0
            }
            
            val nextFile = allFiles[nextIndex]
            println("[PlayerVM] 自動播放下一首: ${nextFile.fileName}")
            playMedia(nextFile.filePath, nextFile.fileName, startTimeMs = 0L)
        }
    }

    private fun handlePlaybackCompletion() {
        viewModelScope.launch {
            val mode = _uiState.value.playbackMode
            when (mode) {
                PlaybackMode.SINGLE -> {
                    stop()
                }
                PlaybackMode.LOOP -> {
                    val currentPath = _uiState.value.currentFilePath
                    val currentTitle = _uiState.value.currentTitle
                    if (currentPath != null) {
                        playMedia(currentPath, currentTitle, startTimeMs = 0L)
                    } else {
                        stop()
                    }
                }
                PlaybackMode.LIST -> {
                    playNextSong()
                }
            }
        }
    }

    fun togglePlaybackMode() {
        _uiState.update { 
            val nextMode = when (it.playbackMode) {
                PlaybackMode.LIST -> PlaybackMode.SINGLE
                PlaybackMode.SINGLE -> PlaybackMode.LOOP
                PlaybackMode.LOOP -> PlaybackMode.LIST
            }
            it.copy(playbackMode = nextMode)
        }
    }

    fun togglePlaybackScope() {
        _uiState.update { it.copy(playbackScope = if (it.playbackScope == PlaybackScope.LIST) PlaybackScope.SINGLE else PlaybackScope.LIST) }
    }
    fun togglePlaybackAction() {
        _uiState.update { it.copy(playbackAction = if (it.playbackAction == PlaybackAction.STOP) PlaybackAction.LOOP else PlaybackAction.STOP) }
    }
    fun toggleLockMode() {
        _uiState.update { it.copy(isLockMode = !it.isLockMode) }
    }
    fun setSleepTimer(seconds: Long) {
        _uiState.update { it.copy(sleepTimer = seconds) }
    }
    fun cancelSleepTimer() {
        _uiState.update { it.copy(sleepTimer = null) }
    }
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun retryCurrentDownload() {
        val currentPath = _uiState.value.currentFilePath ?: return
        if (currentPath.startsWith("youtube:")) {
            val realUrl = currentPath.substringAfter("youtube:").trim()
            audioRepository.retryDownload(realUrl)
        }
    }
    
    fun clearDebugLog() {
        _uiState.update { it.copy(debugLog = "") }
        player.clearDebugLog()
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val logFile = java.io.File(com.example.novel_r.util.getAppDataPath(), "novel_r_debug.log")
                if (logFile.exists()) {
                    logFile.writeText("")
                    println("[PlayerVM] novel_r_debug.log cleared successfully")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
