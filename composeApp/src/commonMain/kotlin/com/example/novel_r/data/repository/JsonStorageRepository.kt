package com.example.novel_r.data.repository

import com.example.novel_r.data.model.*
import com.example.novel_r.util.getAppDataPath
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 基於 JSON 檔案的資料儲存庫
 */
class JsonStorageRepository {
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping() // 確保 = 不會被轉成 \u003d
        .create()
    private val fileName = "novel_r_data.json"
    
    private val _appData = MutableStateFlow(AppData())
    val appData: StateFlow<AppData> = _appData.asStateFlow()

    private fun getFile(): File {
        val path = getAppDataPath()
        val dir = File(path)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, fileName)
    }

    suspend fun loadData() = withContext(Dispatchers.IO) {
        try {
            val file = getFile()
            if (file.exists()) {
                val json = file.readText()
                val data = gson.fromJson(json, AppData::class.java)
                if (data != null) {
                    _appData.value = data
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun saveData() = withContext(Dispatchers.IO) {
        try {
            val file = getFile()
            val json = gson.toJson(_appData.value)
            file.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- Helper Methods to update data ---

    suspend fun updateAudioFiles(files: List<AudioFile>) {
        _appData.value = _appData.value.copy(audioFiles = files)
        saveData()
    }

    suspend fun updateProgress(filePath: String, progress: AudioProgress) {
        val currentList = _appData.value.progressList.toMutableList()
        val index = currentList.indexOfFirst { it.filePath == filePath }
        if (index != -1) {
            currentList[index] = progress
        } else {
            currentList.add(progress)
        }
        _appData.value = _appData.value.copy(progressList = currentList)
        saveData()
    }

    suspend fun deleteProgress(filePath: String) {
        val currentList = _appData.value.progressList.filter { it.filePath != filePath }
        _appData.value = _appData.value.copy(progressList = currentList)
        saveData()
    }

    suspend fun updateProgressList(progressList: List<AudioProgress>) {
        _appData.value = _appData.value.copy(progressList = progressList)
        saveData()
    }

    suspend fun addBookmark(bookmark: PlaylistBookmark) {
        val currentList = _appData.value.bookmarks.toMutableList()
        if (currentList.none { it.url == bookmark.url }) {
            currentList.add(bookmark)
            _appData.value = _appData.value.copy(bookmarks = currentList)
            saveData()
        }
    }
    
    suspend fun deleteBookmark(url: String) {
        val currentList = _appData.value.bookmarks.filter { it.url != url }
        _appData.value = _appData.value.copy(bookmarks = currentList)
        saveData()
    }

    suspend fun updateLastFolderPath(path: String) {
        _appData.value = _appData.value.copy(lastFolderPath = path)
        saveData()
    }

    suspend fun clearAll() {
        _appData.value = AppData(lastFolderPath = _appData.value.lastFolderPath)
        saveData()
    }
}
