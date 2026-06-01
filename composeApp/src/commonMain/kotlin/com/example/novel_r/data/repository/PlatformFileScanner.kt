package com.example.novel_r.data.repository

import com.example.novel_r.data.model.AudioFile

/**
 * 平台相關的檔案掃描器
 */
expect class PlatformFileScanner(context: Any?) {
    /**
     * 掃描目錄下的音訊檔案
     */
    suspend fun scanDirectory(path: String): List<AudioFile>

    /**
     * 解析單一檔案的元數據 (Metadata)
     */
    suspend fun parseAudioMetadata(path: String): AudioFile?
    /**
     * 刪除實體檔案
     */
    suspend fun deleteFile(path: String): Boolean
}
