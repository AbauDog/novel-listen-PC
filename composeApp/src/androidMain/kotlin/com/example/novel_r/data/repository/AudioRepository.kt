package com.example.novel_r.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.example.novel_r.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 音訊檔案儲存庫 - 負責檔案掃描、進度管理和資料同步
 */
class AudioRepository(
    private val audioFileDao: AudioFileDao,
    private val audioProgressDao: AudioProgressDao,
    private val playlistBookmarkDao: PlaylistBookmarkDao,
    private val context: Context
) {
    // ... existing companion object ...

    // ... existing methods ...

    /**
     * 取得所有播放清單書籤
     */
    fun getAllBookmarks(): Flow<List<PlaylistBookmark>> {
        return playlistBookmarkDao.getAllBookmarks()
    }

    /**
     * 新增播放清單書籤
     */
    suspend fun addBookmark(bookmark: PlaylistBookmark) {
        playlistBookmarkDao.insertBookmark(bookmark)
    }

    /**
     * 刪除播放清單書籤
     */
    suspend fun deleteBookmark(bookmark: PlaylistBookmark) {
        playlistBookmarkDao.deleteBookmark(bookmark)
    }

    /**
     * 檢查是否已收藏
     */
    suspend fun isBookmarked(url: String): Boolean {
        return playlistBookmarkDao.getBookmarkByUrl(url) != null
    }

    // ... existing scan method ...

    companion object {
        // 支援的音訊格式
        private val SUPPORTED_AUDIO_FORMATS = setOf(
            "mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "wma"
        )
    }

    /**
     * 取得所有有效的音訊檔案
     */
    fun getAllAudioFiles(): Flow<List<AudioFile>> {
        return audioFileDao.getAllFiles()
    }

    /**
     * 取得所有播放進度
     */
    fun getAllProgress(): Flow<List<AudioProgress>> {
        return audioProgressDao.getAllProgress()
    }

    /**
     * 取得特定檔案的播放進度
     */
    suspend fun getProgress(filePath: String): AudioProgress? {
        return audioProgressDao.getProgress(filePath)
    }

    /**
     * 取得最後播放的檔案
     */
    suspend fun getLastPlayedAudio(): AudioProgress? {
        return audioProgressDao.getLastPlayed()
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
        val existingProgress = audioProgressDao.getProgress(filePath)
        val playCount = (existingProgress?.playCount ?: 0) + 1
        
        val progress = AudioProgress(
            filePath = filePath,
            fileName = fileName,
            fileSize = fileSize,
            lastPosition = position,
            duration = duration,
            lastPlayedTimestamp = System.currentTimeMillis(),
            playCount = playCount
        )
        audioProgressDao.saveProgress(progress)
    }

    /**
     * 掃描資料夾中的音訊檔案（使用 SAF）
     */
    suspend fun scanAudioFiles(folderUri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        // ... (existing code)
        try {
            val documentFile = DocumentFile.fromTreeUri(context, folderUri)
                ?: return@withContext Result.failure(Exception("無法存取資料夾"))

            val audioFiles = mutableListOf<AudioFile>()
            scanDirectory(documentFile, audioFiles)

            // 批次插入檔案
            audioFileDao.insertFiles(audioFiles)

            // 清理無效的進度記錄
            cleanupInvalidProgress()

            Result.success(audioFiles.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 插入單一音訊檔案
     */
    suspend fun insertFile(audioFile: AudioFile) {
        audioFileDao.insertFile(audioFile)
    }

    /**
     * 遞迴掃描目錄
     */
    private suspend fun scanDirectory(
        directory: DocumentFile,
        audioFiles: MutableList<AudioFile>
    ) {
        directory.listFiles().forEach { file ->
            when {
                file.isDirectory -> {
                    // 遞迴掃描子目錄
                    scanDirectory(file, audioFiles)
                }
                file.isFile && isAudioFile(file.name) -> {
                    // 解析音訊檔案
                    parseAudioFile(file)?.let { audioFiles.add(it) }
                }
            }
        }
    }

    /**
     * 檢查是否為支援的音訊檔案
     */
    private fun isAudioFile(fileName: String?): Boolean {
        if (fileName == null) return false
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in SUPPORTED_AUDIO_FORMATS
    }

    /**
     * 解析音訊檔案資訊
     */
    private suspend fun parseAudioFile(file: DocumentFile): AudioFile? = withContext(Dispatchers.IO) {
        try {
            val uri = file.uri
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)

            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)

            retriever.release()

            AudioFile(
                filePath = uri.toString(),
                fileName = file.name ?: "未知檔案",
                fileSize = file.length(),
                duration = duration,
                mimeType = mimeType,
                addedTimestamp = System.currentTimeMillis(),
                isValid = true
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 驗證檔案是否存在
     */
    suspend fun validateFile(filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(filePath)
            val documentFile = DocumentFile.fromSingleUri(context, uri)
            val exists = documentFile?.exists() == true

            if (!exists) {
                // 標記檔案為無效
                audioFileDao.markFileAsInvalid(filePath)
                // 刪除相關進度
                audioProgressDao.deleteProgressByPath(filePath)
            }

            exists
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 清理無效的進度記錄
     */
    suspend fun cleanupInvalidProgress() = withContext(Dispatchers.IO) {
        // 刪除孤立的進度記錄（對應的檔案已不存在）
        audioProgressDao.cleanupOrphanedProgress()
        // 刪除標記為無效的檔案
        audioFileDao.deleteInvalidFiles()
    }

    /**
     * 刪除特定檔案的進度
     */
    suspend fun deleteProgress(filePath: String) {
        audioProgressDao.deleteProgressByPath(filePath)
    }

    /**
     * 清除所有資料（僅資料庫）
     */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        audioFileDao.deleteAll()
        audioProgressDao.deleteAll()
    }

    /**
     * 從列表移除檔案（不刪除實體檔案）
     */
    suspend fun removeFileFromList(filePath: String) = withContext(Dispatchers.IO) {
        audioFileDao.deleteFileByPath(filePath)
        audioProgressDao.deleteProgressByPath(filePath)
    }
    /**
     * 刪除音訊檔案
     */
    suspend fun deleteAudioFile(filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(filePath)
            val documentFile = DocumentFile.fromSingleUri(context, uri)
            
            val deleted = documentFile?.delete() == true
            
            if (deleted) {
                // 從資料庫移除
                audioFileDao.deleteFileByPath(filePath)
                audioProgressDao.deleteProgressByPath(filePath)
            }
            
            deleted
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
