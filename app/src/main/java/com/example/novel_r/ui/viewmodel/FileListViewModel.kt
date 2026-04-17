package com.example.novel_r.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.novel_r.data.local.AudioFile
import com.example.novel_r.data.local.AudioProgress
import com.example.novel_r.data.local.PlaylistBookmark
import com.example.novel_r.data.local.AppDatabase
import com.example.novel_r.data.repository.AudioRepository
import androidx.room.Room
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.novel_r.util.UrlUtils

/**
 * 檔案列表 ViewModel
 */
class FileListViewModel(application: Application) : AndroidViewModel(application) {
    
    private val audioRepository: AudioRepository
    private val youTubeRepository: com.example.novel_r.data.repository.YouTubeRepository
    
    private val _uiState = MutableStateFlow(FileListUiState())
    val uiState: StateFlow<FileListUiState> = _uiState.asStateFlow()
    
    data class FileListUiState(
        val audioFiles: List<AudioFile> = emptyList(),
        val progressMap: Map<String, AudioProgress> = emptyMap(),
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val selectedFolderUri: String? = null,
        val isUpdatingYtDlp: Boolean = false,
        // 播放清單選擇狀態
        val playlistCandidates: List<AudioFile> = emptyList(),
        val showPlaylistSelectionDialog: Boolean = false,
        // 我的最愛 (播放清單)
        val bookmarks: List<PlaylistBookmark> = emptyList(),
        val showBookmarkDialog: Boolean = false
    )
    
    private var pendingPlaylistUrl: String? = null
    private var pendingPlaylistTitle: String? = null
    
    init {
        val database = Room.databaseBuilder(
            application,
            AppDatabase::class.java,
            "audiobook_database"
        )
        .addMigrations(AppDatabase.MIGRATION_1_2)
        .build()
        
        audioRepository = AudioRepository(
            database.audioFileDao(),
            database.audioProgressDao(),
            database.playlistBookmarkDao(),
            application
        )
        
        youTubeRepository = com.example.novel_r.data.repository.YouTubeRepository(application)
        
        loadAudioFiles()
        loadProgress()
        loadBookmarks()
    }
    
    /**
     * 處理 YouTube URL (單曲或播放清單)
     */
    fun processYouTubeUrl(url: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            
            if (url.contains("list=")) {
                pendingPlaylistUrl = url
                // 處理播放清單
                val result = youTubeRepository.getPlaylistInfo(url)
                result.fold(
                    onSuccess = { (title, tracks) -> // Destructure Pair
                        if (tracks.isNotEmpty()) {
                            pendingPlaylistTitle = title
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                playlistCandidates = tracks,
                                showPlaylistSelectionDialog = true
                            )
                        } else {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                errorMessage = "找不到播放清單內容"
                            )
                        }
                    },
                    onFailure = { e ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "播放清單解析失敗: ${e.message}"
                        )
                    }
                )
            } else {
                // ... existing single file logic
                val result = youTubeRepository.getStreamInfo(url)
                result.fold(
                    onSuccess = { audioFile ->
                        audioRepository.insertFile(audioFile)
                        _uiState.value = _uiState.value.copy(isLoading = false)
                    },
                    onFailure = { e ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "解析失敗: ${e.message}"
                        )
                    }
                )
            }
        }
    }

    /**
     * 確認加入播放清單中的選定曲目
     */
    fun confirmPlaylistSelection(selectedTracks: List<AudioFile>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, showPlaylistSelectionDialog = false)
            
            // 批次插入
            selectedTracks.forEach { track ->
                audioRepository.insertFile(track)
            }
            
            // 自動加入書籤
            pendingPlaylistUrl?.let { url ->
                if (!audioRepository.isBookmarked(url)) {
                    val firstTrack = selectedTracks.firstOrNull()
                    val bookmark = PlaylistBookmark(
                        url = url,
                        name = pendingPlaylistTitle ?: firstTrack?.fileName?.substringBeforeLast("-")?.trim() ?: "未命名清單",
                        thumbnailUrl = firstTrack?.thumbnailUrl,
                        trackCount = selectedTracks.size
                    )
                    audioRepository.addBookmark(bookmark)
                }
            }
            pendingPlaylistUrl = null
            pendingPlaylistTitle = null
            
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                playlistCandidates = emptyList()
            )
        }
    }
    
    private fun loadBookmarks() {
        viewModelScope.launch {
            audioRepository.getAllBookmarks().collect { list ->
                _uiState.value = _uiState.value.copy(bookmarks = list)
            }
        }
    }
    
    fun deleteBookmark(bookmark: PlaylistBookmark) {
        viewModelScope.launch {
            audioRepository.deleteBookmark(bookmark)
        }
    }
    
    fun toggleBookmarkDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showBookmarkDialog = show)
    }

    /**
     * 取消播放清單選擇
     */
    fun cancelPlaylistSelection() {
        _uiState.value = _uiState.value.copy(
            showPlaylistSelectionDialog = false,
            playlistCandidates = emptyList()
        )
        pendingPlaylistUrl = null
    }

    /**
     * 取得可播放的 URL (如果是串流，確保網址有效)
     */
    suspend fun getPlayableUrl(audioFile: AudioFile): String {
        if (!audioFile.isStream) {
            return audioFile.filePath
        }
        
        // 檢查快取的串流網址是否依然有效
        val cachedUrl = audioFile.streamUrl
        if (!UrlUtils.isUrlExpired(cachedUrl)) {
            android.util.Log.d("FileListViewModel", "Using cached stream URL for ${audioFile.fileName}")
            return cachedUrl!!
        }

        // 如果已過期或不存在，重新解析
        return try {
            android.util.Log.d("FileListViewModel", "Stream URL expired or missing, refreshing for ${audioFile.fileName}")
            val result = youTubeRepository.getStreamInfo(audioFile.originalUrl ?: audioFile.filePath)
            val newFile = result.getOrNull()
            if (newFile != null) {
                // 更新資料庫中的 streamUrl
                audioRepository.insertFile(audioFile.copy(
                    streamUrl = newFile.streamUrl, 
                    duration = newFile.duration
                ))
                newFile.streamUrl ?: audioFile.filePath
            } else {
                audioFile.filePath // Fallback
            }
        } catch (e: Exception) {
            e.printStackTrace()
             audioFile.filePath
        }
    }

    /**
     * 更新 yt-dlp元件
     */
    fun updateYtDlp() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUpdatingYtDlp = true)
            val result = youTubeRepository.updateYoutubeDL()
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isUpdatingYtDlp = false, errorMessage = "元件更新成功")
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isUpdatingYtDlp = false, errorMessage = "更新失敗: ${e.message}")
                }
            )
        }
    }

    /**
     * 載入音訊檔案列表
     */
    private fun loadAudioFiles() {
        viewModelScope.launch {
             // ... existing code

            audioRepository.getAllAudioFiles().collect { files ->
                // 再次確保依照路徑排序，解決插入順序問題
                _uiState.value = _uiState.value.copy(audioFiles = files.sortedBy { it.filePath })
            }
        }
    }
    
    /**
     * 載入播放進度
     */
    private fun loadProgress() {
        viewModelScope.launch {
            audioRepository.getAllProgress().collect { progressList ->
                val progressMap = progressList.associateBy { it.filePath }
                _uiState.value = _uiState.value.copy(progressMap = progressMap)
            }
        }
    }
    
    /**
     * 掃描資料夾
     */
    fun scanFolder(folderUri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            
            try {
                // 1. 取得 ContentResolver
                val contentResolver = getApplication<Application>().contentResolver
                
                // 2. 累加權限：不要釋放舊權限，這樣才能支援跨資料夾播放與管理
                // 只有在 "Clear All" 時才需要釋放權限

                // 3. 取得並持久化新資料夾的權限
                contentResolver.takePersistableUriPermission(
                    folderUri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or 
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                // 如果無法取得權限，可能已經有權限或是無法取得，繼續嘗試掃描
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(errorMessage = "權限錯誤：請嘗試重新選擇資料夾")
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            val result = audioRepository.scanAudioFiles(folderUri)
            
            result.fold(
                onSuccess = { count ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        selectedFolderUri = folderUri.toString(),
                        errorMessage = null
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "掃描失敗"
                    )
                }
            )
        }
    }
    
    /**
     * 取得檔案的播放進度
     */
    fun getProgress(filePath: String): AudioProgress? {
        return _uiState.value.progressMap[filePath]
    }
    
    /**
     * 清除錯誤訊息
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
    
    /**
     * 刪除檔案
     */
    /**
     * 刪除檔案 (手動觸發)
     */
    fun deleteFile(audioFile: AudioFile) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            val success = audioRepository.deleteAudioFile(audioFile.filePath)
            
            if (!success) {
                _uiState.value = _uiState.value.copy(errorMessage = "刪除失敗")
            }
            // 成功時 Flow 會自動更新列表
            
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    /**
     * 從列表移除檔案
     */
    fun removeFile(audioFile: AudioFile) {
        viewModelScope.launch {
            audioRepository.removeFileFromList(audioFile.filePath)
            // Flow 會自動更新 UI
        }
    }

    /**
     * 清除所有檔案與權限
     */
    fun clearAllFiles() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            // 1. 清除資料庫
            audioRepository.clearAll()
            
            // 2. 清除權限
            try {
                val contentResolver = getApplication<Application>().contentResolver
                contentResolver.persistedUriPermissions.forEach { permission ->
                    contentResolver.releasePersistableUriPermission(
                        permission.uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or 
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                audioFiles = emptyList(),
                progressMap = emptyMap(),
                selectedFolderUri = null, // 重置選擇的資料夾
                errorMessage = null
            )
        }
    }

    /**
     * 刪除檔案進度
     */
    fun deleteProgress(filePath: String) {
        viewModelScope.launch {
            audioRepository.deleteProgress(filePath)
        }
    }
}
