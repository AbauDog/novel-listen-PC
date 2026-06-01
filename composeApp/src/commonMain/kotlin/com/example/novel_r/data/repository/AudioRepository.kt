package com.example.novel_r.data.repository

import com.example.novel_r.data.model.*
import com.example.novel_r.util.YoutubeDownloader
import com.example.novel_r.util.getAppDataPath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * 跨平台的音訊儲存庫 - 使用 JSON 檔案儲存
 */
class AudioRepository(
    private val jsonStorage: JsonStorageRepository,
    private val fileScanner: PlatformFileScanner,
    private val youtubeDownloader: YoutubeDownloader? = null // 新增下載器
) {
    private val downloadJobs = mutableMapOf<String, kotlinx.coroutines.Job>()
    
    // 用於通知下載完成
    private val _downloadCompletedFlow = kotlinx.coroutines.flow.MutableSharedFlow<AudioFile>()
    val downloadCompletedFlow: kotlinx.coroutines.flow.SharedFlow<AudioFile> = _downloadCompletedFlow

    // 用於通知下載進度 (URL -> 0~100)
    private val _downloadProgressFlow = kotlinx.coroutines.flow.MutableStateFlow<Map<String, String>>(emptyMap())
    val downloadProgressFlow: kotlinx.coroutines.flow.StateFlow<Map<String, String>> = _downloadProgressFlow


    /**
     * 取得所有音訊檔案
     */
    fun getAllAudioFiles(): Flow<List<AudioFile>> {
        return jsonStorage.appData.map { it.audioFiles }
    }

    /**
     * 取得所有播放進度
     */
    fun getAllProgress(): Flow<List<AudioProgress>> {
        return jsonStorage.appData.map { it.progressList }
    }

    /**
     * 取得上一次開啟的資料夾
     */
    fun getLastFolderPath(): Flow<String?> {
        return jsonStorage.appData.map { it.lastFolderPath }
    }

    /**
     * 取得特定檔案的播放進度
     */
    suspend fun getProgress(filePath: String): AudioProgress? {
        return jsonStorage.appData.value.progressList.find { it.filePath == filePath }
    }

    /**
     * 保存播放進度
     */
    suspend fun saveProgress(
        filePath: String,
        fileName: String,
        fileSize: Long,
        position: Long,
        duration: Long
    ) {
        val currentProgress = getProgress(filePath)
        val playCount = (currentProgress?.playCount ?: 0) + 1
        
        val progress = AudioProgress(
            filePath = filePath,
            fileName = fileName,
            fileSize = fileSize,
            lastPosition = position,
            duration = duration,
            lastPlayedTimestamp = System.currentTimeMillis(),
            playCount = playCount
        )
        jsonStorage.updateProgress(filePath, progress)
    }

    /**
     * 掃描資料夾
     */
    suspend fun scanAudioFiles(path: String): Result<Int> {
        return try {
            val scannedFiles = fileScanner.scanDirectory(path)
            jsonStorage.updateAudioFiles(scannedFiles)
            jsonStorage.updateLastFolderPath(path)
            Result.success(scannedFiles.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 確認 yt-dlp 已就緒（找不到則自動下載）
     */
    suspend fun ensureYtDlp(onProgress: (String) -> Unit): Boolean {
        if (youtubeDownloader == null) return false
        val path = youtubeDownloader.ensureYtDlp(getAppDataPath(), onProgress)
        return path != null
    }

    /**
     * 處理 YouTube 網址 (快速啟動版本)
     */
    suspend fun processYoutubeUrl(inputUrl: String, onProgress: (String) -> Unit = {}): Result<AudioFile> {
        if (youtubeDownloader == null) return Result.failure(Exception("此平台不支援網路下載"))
        val url = inputUrl.trim()
        
        // 1. 確保播放工具就緒
        val path = youtubeDownloader.ensureYtDlp(getAppDataPath(), onProgress)
        if (path == null) return Result.failure(Exception("無法取得下載組件"))
        youtubeDownloader.ensureFfmpeg(onProgress)

        // 2. 快速獲取串流資訊 (約 2-5 秒)
        println("[AudioRepo] 處理網址: $url (長度: ${url.length})")
        onProgress("正在獲取串流資訊...")
        val streamResult = youtubeDownloader.getStreamInfo(url)
        
        if (streamResult.isSuccess) {
            val streamFile = streamResult.getOrThrow()
            insertFile(streamFile) // 先以串流網址存入
            
            // 3. 只有當還是「串流」時才需要在背景開始下載存檔 (不阻塞播放)
            if (streamFile.isStream) {
                // 檢查是否已有相同 URL 的下載任務正在執行中，避免重複下載造成檔案鎖定損壞
                if (downloadJobs.containsKey(url)) {
                    println("[AudioRepo] 該網址已有下載任務在執行中，跳過重複下載: $url")
                    return Result.success(streamFile)
                }
                
                // 使用 viewModelScope 或特定的下載 Scope，這裡先維持 GlobalScope 但加強狀態管理
                val job = GlobalScope.launch(Dispatchers.IO) {
                    println("[AudioRepo] 開始背景下載: $url")
                    val downloadResult = youtubeDownloader.downloadAudio(url, getAppDataPath()) { percent ->
                        _downloadProgressFlow.update { it + (url to percent) }
                    }
                    downloadResult.onSuccess { downloadedFile ->
                        println("[AudioRepo] 下載成功，切換至本地路徑: ${downloadedFile.filePath}")
                        insertFile(downloadedFile) // 只有成功才替換為本地路徑
                        _downloadCompletedFlow.emit(downloadedFile) // 發送通知
                    }
                    downloadResult.onFailure {
                        println("[AudioRepo] 下載失敗: ${it.message}")
                    }
                    _downloadProgressFlow.update { it - url } // 移除進度紀錄
                    downloadJobs.remove(url)
                }
                downloadJobs[url] = job
            }
            
            return Result.success(streamFile)
        } else {
            return streamResult
        }
    }

    /**
     * 儲存播放進度
     */
    suspend fun saveProgress(filePath: String, position: Long, duration: Long) {
        val fileName = filePath.substringAfterLast("/").substringAfterLast("\\")
        jsonStorage.updateProgress(filePath, AudioProgress(
            filePath = filePath,
            fileName = fileName,
            fileSize = 0L,
            lastPosition = position,
            duration = duration
        ))
    }

    suspend fun deleteProgress(filePath: String) {
        jsonStorage.deleteProgress(filePath)
    }

    /**
     * 取得所有書籤
     */
    fun getAllBookmarks(): Flow<List<PlaylistBookmark>> {
        return jsonStorage.appData.map { it.bookmarks }
    }

    /**
     * 新增書籤
     */
    suspend fun addBookmark(bookmark: PlaylistBookmark) {
        jsonStorage.addBookmark(bookmark)
    }

    /**
     * 刪除書籤
     */
    suspend fun deleteBookmark(bookmark: PlaylistBookmark) {
        jsonStorage.deleteBookmark(bookmark.url)
    }

    /**
     * 檢查是否已收藏
     */
    suspend fun isBookmarked(url: String): Boolean {
        return jsonStorage.appData.value.bookmarks.any { it.url == url }
    }

    /**
     * 從列表移除檔案
     */
    suspend fun deleteFile(filePath: String) {
        val currentFiles = jsonStorage.appData.value.audioFiles.toMutableList()
        val fileToDelete = currentFiles.find { it.filePath == filePath }
        
        // 如果正在下載中，取消任務
        fileToDelete?.originalUrl?.let { url ->
            downloadJobs[url]?.cancel()
            downloadJobs.remove(url)
            
            // 同步刪除原始網址的進度 (重要：YouTube 歌曲進度通常存在這)
            jsonStorage.deleteProgress(url)
        }
        
        currentFiles.removeAll { it.filePath == filePath }
        jsonStorage.updateAudioFiles(currentFiles)
        jsonStorage.deleteProgress(filePath) // 同步刪除目前路徑的進度
    }

    /**
     * 實體刪除檔案
     */
    suspend fun deletePhysicalFile(filePath: String): Boolean {
        val logFile = java.io.File(getAppDataPath(), "novel_r_debug.log")
        logFile.appendText("Attempting physical delete of file: $filePath at ${System.currentTimeMillis()}\n")
        return try {
            val file = java.io.File(filePath)
            if (file.exists()) {
                val deleted = file.delete()
                logFile.appendText("File.delete() result for $filePath: $deleted\n")
                if (deleted) {
                    deleteFile(filePath)
                } else {
                    logFile.appendText("Failed to delete physical file (possibly locked by another process or media player).\n")
                }
                deleted
            } else {
                logFile.appendText("Physical file does not exist: $filePath\n")
                false
            }
        } catch (e: Exception) {
            val sw = java.io.StringWriter()
            e.printStackTrace(java.io.PrintWriter(sw))
            logFile.appendText("EXCEPTION during physical delete: ${sw.toString()}\n")
            false
        }
    }

    /**
     * 插入/更新單一檔案 (用於 YouTube 串流與下載)
     */
    suspend fun insertFile(audioFile: AudioFile) {
        println("[AudioRepo] 正在存入檔案: ${audioFile.filePath} (長度: ${audioFile.filePath.length})")
        val currentFiles = jsonStorage.appData.value.audioFiles.toMutableList()
        
        // 簡化去重邏輯：只要 ID 相同，一律視為重複項目
        val toRemove = mutableListOf<AudioFile>()
        val url = audioFile.originalUrl ?: ""
        val currentVideoId = extractYoutubeVideoId(url)

        currentFiles.forEach { 
            val itVideoId = it.originalUrl?.let { oUrl -> extractYoutubeVideoId(oUrl) } ?: extractYoutubeVideoId(it.filePath)
            val isMatch = it.filePath == audioFile.filePath || 
                (currentVideoId != null && currentVideoId == itVideoId)
            if (isMatch) toRemove.add(it)
        }
        
        if (toRemove.isNotEmpty()) {
            currentFiles.removeAll(toRemove)
        }
        
        currentFiles.add(0, audioFile) 
        jsonStorage.updateAudioFiles(currentFiles)

        // 如果是下載完成的本地檔案，進行進度移轉與清理多餘串流進度
        if (!audioFile.isStream && currentVideoId != null) {
            migrateAndCleanStreamProgress(currentVideoId, audioFile.filePath)
        }
    }

    private suspend fun migrateAndCleanStreamProgress(videoId: String, localFilePath: String) {
        val logFile = java.io.File(getAppDataPath(), "novel_r_debug.log")
        logFile.appendText("migrateAndCleanStreamProgress started for videoId: $videoId, localFilePath: $localFilePath at ${System.currentTimeMillis()}\n")
        val currentProgressList = jsonStorage.appData.value.progressList
        
        // 找出所有與此 videoId 相關的進度紀錄 (包括帶有 "youtube:" 前綴、原始網址、或已經是本地路徑的)
        val matchingProgress = currentProgressList.filter { progress ->
            val pVideoId = extractYoutubeVideoId(progress.filePath)
            progress.filePath.contains(videoId) || (pVideoId != null && pVideoId == videoId)
        }
        
        logFile.appendText("Matching progress entries count: ${matchingProgress.size}\n")
        matchingProgress.forEach {
            logFile.appendText("Matching entry: path=${it.filePath}, pos=${it.lastPosition}\n")
        }
        
        if (matchingProgress.isNotEmpty()) {
            // 找出其中位置最前 (進度最多) 或最新的紀錄作為要繼承的進度
            val bestProgress = matchingProgress.maxByOrNull { it.lastPosition } ?: matchingProgress.first()
            logFile.appendText("Selected best progress to inherit: path=${bestProgress.filePath}, pos=${bestProgress.lastPosition}\n")
            
            // 建立或更新本地 MP3 的進度
            val localFileName = localFilePath.substringAfterLast("/").substringAfterLast("\\")
            val newLocalProgress = AudioProgress(
                filePath = localFilePath,
                fileName = localFileName,
                fileSize = bestProgress.fileSize,
                lastPosition = bestProgress.lastPosition,
                duration = bestProgress.duration,
                lastPlayedTimestamp = System.currentTimeMillis(),
                playCount = bestProgress.playCount
            )
            
            // 從進度清單中移除所有與該 videoId 相關的紀錄 (包括 youtube: 前綴與原始 URL)
            // 但保留我們剛剛為本地 MP3 建立的進度
            val cleanedList = currentProgressList.filterNot { progress ->
                val pVideoId = extractYoutubeVideoId(progress.filePath)
                (progress.filePath.contains(videoId) || (pVideoId != null && pVideoId == videoId)) && progress.filePath != localFilePath
            }.toMutableList()
            
            logFile.appendText("Cleaned other matching stream entries. Remaining entries count: ${cleanedList.size}\n")
            
            // 將移轉後的新進度加入 (如果列表已有則取代)
            val existingIndex = cleanedList.indexOfFirst { it.filePath == localFilePath }
            if (existingIndex != -1) {
                cleanedList[existingIndex] = newLocalProgress
            } else {
                cleanedList.add(newLocalProgress)
            }
            
            // 更新並存入 JSON 儲存庫
            jsonStorage.updateProgressList(cleanedList)
            logFile.appendText("Successfully saved cleaned progress list to json.\n")
        } else {
            logFile.appendText("No matching progress entries found to migrate.\n")
        }
    }

    /**
     * 清除所有清單
     */
    suspend fun clearAllFiles() {
        // 取消所有正在下載的任務
        downloadJobs.clear()
        
        jsonStorage.clearAll() // 徹底清除所有清單與進度
    }

    /**
     * 新增多個檔案
     */
    suspend fun addFiles(filePaths: List<String>) {
        val currentFiles = jsonStorage.appData.value.audioFiles.toMutableList()
        filePaths.forEach { path ->
            if (currentFiles.none { it.filePath == path }) {
                val file = java.io.File(path)
                if (file.exists()) {
                    currentFiles.add(0, AudioFile(
                        filePath = path,
                        fileName = file.name,
                        fileSize = file.length(),
                        duration = 0,
                        mimeType = "audio/mpeg"
                    ))
                }
            }
        }
        jsonStorage.updateAudioFiles(currentFiles)
        
        // 儲存最後開啟的目錄
        if (filePaths.isNotEmpty()) {
            val lastDir = java.io.File(filePaths[0]).parent
            if (lastDir != null) {
                jsonStorage.updateLastFolderPath(lastDir)
            }
        }
    }

    /**
     * 切割音訊檔案為 (上) 與 (下) 兩半 (僅物理切割，不加入播放清單)
     */
    suspend fun splitAudioFile(audioFile: AudioFile): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val sourceFile = java.io.File(audioFile.filePath)
                if (!sourceFile.exists()) {
                    return@withContext Result.failure(Exception("實體檔案不存在"))
                }
                
                val resourceDir = java.io.File(getAppDataPath())
                val ffmpegPath = java.io.File(resourceDir, "ffmpeg.exe").absolutePath
                val ffprobePath = java.io.File(resourceDir, "ffprobe.exe").absolutePath
                
                if (!java.io.File(ffmpegPath).exists()) {
                    return@withContext Result.failure(Exception("找不到 ffmpeg 組件，請先播放任意網路歌曲以自動下載"))
                }

                // 1. 取得音訊時長 (秒)
                val durationSec = getAudioDuration(ffprobePath, ffmpegPath, sourceFile.absolutePath)
                if (durationSec <= 0.0) {
                    return@withContext Result.failure(Exception("無法解析檔案長度，且找不到 ffprobe.exe 或 ffmpeg.exe"))
                }
                
                val halfDuration = durationSec / 2.0
                
                val parent = sourceFile.parentFile
                val baseName = sourceFile.nameWithoutExtension
                val ext = sourceFile.extension
                
                val file1 = java.io.File(parent, "(上)$baseName.$ext")
                val file2 = java.io.File(parent, "(下)$baseName.$ext")
                
                // 2. 使用 ffmpeg 切割前半段
                val pb1 = ProcessBuilder(
                    ffmpegPath, "-y", "-nostdin", "-i", sourceFile.absolutePath,
                    "-t", halfDuration.toString(), "-acodec", "copy",
                    file1.absolutePath
                )
                pb1.redirectOutput(ProcessBuilder.Redirect.DISCARD)
                pb1.redirectError(ProcessBuilder.Redirect.DISCARD)
                val proc1 = pb1.start()
                proc1.outputStream.close() // 關閉 stdin 避免互動式掛起
                proc1.waitFor()
                
                // 3. 使用 ffmpeg 切割後半段
                val pb2 = ProcessBuilder(
                    ffmpegPath, "-y", "-nostdin", "-i", sourceFile.absolutePath,
                    "-ss", halfDuration.toString(), "-acodec", "copy",
                    file2.absolutePath
                )
                pb2.redirectOutput(ProcessBuilder.Redirect.DISCARD)
                pb2.redirectError(ProcessBuilder.Redirect.DISCARD)
                val proc2 = pb2.start()
                proc2.outputStream.close() // 關閉 stdin 避免互動式掛起
                proc2.waitFor()
                
                if (!file1.exists() || !file2.exists()) {
                    return@withContext Result.failure(Exception("切割失敗，請檢查檔案是否損壞"))
                }
                
                // 自動將切割後的 (上) 與 (下) 檔案加入資料庫，讓 UI 立即更新顯示
                addFiles(listOf(file1.absolutePath, file2.absolutePath))
                
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    private fun getAudioDuration(ffprobePath: String, ffmpegPath: String, filePath: String): Double {
        // 1. 嘗試使用 ffprobe
        try {
            val ffprobeFile = java.io.File(ffprobePath)
            if (ffprobeFile.exists()) {
                val pb = ProcessBuilder(ffprobePath, "-v", "error", "-show_entries", "format=duration", "-of", "default=noprint_wrappers=1:nokey=1", filePath)
                val proc = pb.start()
                val durationStr = proc.inputStream.bufferedReader().readText().trim()
                proc.waitFor()
                val d = durationStr.toDoubleOrNull()
                if (d != null && d > 0.0) return d
            }
        } catch (e: Exception) {
            // 忽略，下方的 ffmpeg 會作為備用方案
        }
        
        // 2. 嘗試使用 ffmpeg 備用方案 (解析 Duration: HH:MM:SS.xx)
        try {
            val ffmpegFile = java.io.File(ffmpegPath)
            if (ffmpegFile.exists()) {
                val pb = ProcessBuilder(ffmpegPath, "-i", filePath)
                val proc = pb.start()
                // ffmpeg 的資訊輸出在 errorStream
                val output = proc.errorStream.bufferedReader().readText()
                proc.waitFor()
                
                val match = Regex("""Duration:\s*(\d+):(\d+):(\d+\.\d+)""").find(output)
                if (match != null) {
                    val hours = match.groupValues[1].toDoubleOrNull() ?: 0.0
                    val minutes = match.groupValues[2].toDoubleOrNull() ?: 0.0
                    val seconds = match.groupValues[3].toDoubleOrNull() ?: 0.0
                    return hours * 3600.0 + minutes * 60.0 + seconds
                }
            }
        } catch (e: Exception) {
            // 忽略
        }
        
        return 0.0
    }
}

/**
 * 擷取網址中的影片/音訊 ID (支援 YouTube、FB 等)
 */
fun extractYoutubeVideoId(url: String): String? {
    var cleanUrl = url.trim()
    if (cleanUrl.startsWith("youtube:")) {
        cleanUrl = cleanUrl.substringAfter("youtube:").trim()
    }
    if (cleanUrl.startsWith("pending_")) {
        cleanUrl = cleanUrl.substringAfter("pending_").trim()
    }
    return when {
        cleanUrl.contains("v=") -> {
            cleanUrl.substringAfter("v=").substringBefore("&")
        }
        cleanUrl.contains("youtu.be/") -> {
            cleanUrl.substringAfter("youtu.be/").substringBefore("?").substringBefore("/")
        }
        cleanUrl.contains("/live/") -> {
            cleanUrl.substringAfter("/live/").substringBefore("?").substringBefore("/")
        }
        cleanUrl.contains("/shorts/") -> {
            cleanUrl.substringAfter("/shorts/").substringBefore("?").substringBefore("/")
        }
        cleanUrl.contains("/embed/") -> {
            cleanUrl.substringAfter("/embed/").substringBefore("?").substringBefore("/")
        }
        else -> {
            // 針對 FB 等一般 URL，先去掉 query string，再移除尾端的 '/'，最後拿最後一節作為 ID
            val path = cleanUrl.substringBefore("?").substringBefore("&").trim()
            val cleanPath = if (path.endsWith("/")) path.dropLast(1) else path
            val lastSegment = cleanPath.substringAfterLast("/")
            if (lastSegment.isNotEmpty() && !lastSegment.contains(".")) {
                lastSegment
            } else {
                null
            }
        }
    }
}
