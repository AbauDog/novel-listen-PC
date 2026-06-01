package com.example.novel_r.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.novel_r.data.model.*
import com.example.novel_r.data.repository.AudioRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SortOrder {
    TIME, // 檔案時間
    NAME, // 名稱
    SIZE  // 檔案大小
}

/**
 * 檔案列表 ViewModel (跨平台版本)
 */
class FileListViewModel(
    private val audioRepository: AudioRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(FileListUiState())
    val uiState: StateFlow<FileListUiState> = _uiState.asStateFlow()
    
    data class FileListUiState(
        val audioFiles: List<AudioFile> = emptyList(),
        val progressMap: Map<String, AudioProgress> = emptyMap(),
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val selectedFolderUri: String? = null,
        val bookmarks: List<PlaylistBookmark> = emptyList(),
        val showBookmarkDialog: Boolean = false,
        val isUpdatingYtDlp: Boolean = false,
        val playlistCandidates: List<AudioFile> = emptyList(),
        val showPlaylistSelectionDialog: Boolean = false,
        val sortOrder: SortOrder = SortOrder.TIME // 預設依檔案時間
    )

    private var rawAudioFiles: List<AudioFile> = emptyList()

    init {
        loadAudioFiles()
        loadProgress()
        loadBookmarks()
        loadLastFolderPath()
    }

    private fun loadAudioFiles() {
        viewModelScope.launch {
            audioRepository.getAllAudioFiles().collect { files ->
                rawAudioFiles = files
                applySort()
            }
        }
    }

    private fun applySort() {
        val files = rawAudioFiles
        val order = _uiState.value.sortOrder
        val sortedFiles = when (order) {
            SortOrder.TIME -> files.sortedByDescending { it.addedTimestamp }
            SortOrder.NAME -> files.sortedBy { it.fileName }
            SortOrder.SIZE -> files.sortedByDescending { it.fileSize }
        }
        _uiState.update { it.copy(audioFiles = sortedFiles) }
    }

    fun setSortOrder(order: SortOrder) {
        _uiState.update { it.copy(sortOrder = order) }
        applySort()
    }

    private fun loadProgress() {
        viewModelScope.launch {
            audioRepository.getAllProgress().collect { list ->
                val map = list.associateBy { it.filePath }
                _uiState.update { it.copy(progressMap = map) }
            }
        }
    }


    private fun loadBookmarks() {
        viewModelScope.launch {
            audioRepository.getAllBookmarks().collect { list ->
                _uiState.update { it.copy(bookmarks = list) }
            }
        }
    }

    private fun loadLastFolderPath() {
        viewModelScope.launch {
            audioRepository.getLastFolderPath().collect { path ->
                _uiState.update { it.copy(selectedFolderUri = path) }
            }
        }
    }

    fun scanFolder(path: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = audioRepository.scanAudioFiles(path)
            result.fold(
                onSuccess = { count ->
                    _uiState.update { it.copy(isLoading = false, selectedFolderUri = path) }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message ?: "掃描失敗") }
                }
            )
        }
    }

    fun getProgress(filePath: String): AudioProgress? {
        return _uiState.value.progressMap[filePath]
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun removeFile(audioFile: AudioFile) {
        viewModelScope.launch {
            audioRepository.deleteFile(audioFile.filePath)
        }
    }

    fun splitFile(audioFile: AudioFile) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = audioRepository.splitAudioFile(audioFile)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isLoading = false) }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message ?: "切割失敗") }
                }
            )
        }
    }
    
    fun toggleBookmarkDialog(show: Boolean) {
        _uiState.update { it.copy(showBookmarkDialog = show) }
    }
    
    // YouTube 功能暫時留空，待後續實作 PC 版 yt-dlp
    fun processYouTubeUrl(url: String, onSuccess: (AudioFile) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdatingYtDlp = true, errorMessage = null) }
            
            // 先加一個臨時項到列表，讓使用者看到有在處理
            val pendingFile = AudioFile(
                filePath = "pending_$url",
                fileName = "正在獲取串流資訊...",
                fileSize = 0,
                duration = 0,
                mimeType = null,
                isStream = true,
                originalUrl = url
            )
            audioRepository.insertFile(pendingFile)

            val result = audioRepository.processYoutubeUrl(url) { msg ->
                _uiState.update { it.copy(errorMessage = msg) }
            }
            result.fold(
                onSuccess = { audioFile ->
                    audioRepository.deleteFile(pendingFile.filePath) // 移除臨時項
                    _uiState.update { it.copy(isUpdatingYtDlp = false) }
                    onSuccess(audioFile)
                },
                onFailure = { error ->
                    audioRepository.deleteFile(pendingFile.filePath) // 失敗也移除
                    _uiState.update { it.copy(isUpdatingYtDlp = false, errorMessage = error.message) }
                }
            )
        }
    }
    fun updateYtDlp() {}
    fun deleteBookmark(bookmark: PlaylistBookmark) {}
    fun confirmPlaylistSelection(tracks: List<AudioFile>) {}
    fun cancelPlaylistSelection() {}
    fun clearAllFiles() {
        viewModelScope.launch {
            audioRepository.clearAllFiles()
        }
    }

    fun addFiles(paths: List<String>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            audioRepository.addFiles(paths)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun deleteFile(audioFile: AudioFile) {
        viewModelScope.launch {
            audioRepository.deletePhysicalFile(audioFile.filePath)
        }
    }
}
