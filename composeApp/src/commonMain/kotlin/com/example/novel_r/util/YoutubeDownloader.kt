package com.example.novel_r.util

import com.example.novel_r.data.model.AudioFile

/**
 * YouTube 下載器介面 (跨平台)
 */
interface YoutubeDownloader {
    /** 獲取 YouTube 即時串流資訊 (快速) */
    suspend fun getStreamInfo(url: String): Result<AudioFile>

    /** 下載 YouTube 音訊並傳回檔案資訊 */
    suspend fun downloadAudio(url: String, outputDir: String, onProgress: (String) -> Unit = {}): Result<AudioFile>

    /** 確認 yt-dlp 存在，找不到則自動下載，回傳可用路徑 */
    suspend fun ensureYtDlp(appDir: String, onProgress: (String) -> Unit = {}): String?

    /** 確認 ffmpeg 存在，找不到則自動下載 */
    suspend fun ensureFfmpeg(onProgress: (String) -> Unit = {}): Boolean
}

/**
 * 建立平台對應的下載器
 */
expect fun createYoutubeDownloader(): YoutubeDownloader

/**
 * 修正過實際的名稱 = 實際的名稱裡面特殊字元皆用 '_' 取代
 */
fun sanitizeFileName(fileName: String, extension: String): String {
    val originalTitle = fileName
        .removePrefix("[串流] ")
        .removePrefix("[雲端] ")
        .trim()
    
    // 將所有非英數字與非中日韓文字的特殊字元，皆以 '_' 取代
    val cleanedTitle = originalTitle.replace(
        Regex("[^a-zA-Z0-9_\\u4e00-\\u9fa5\\u3040-\\u309f\\u30a0-\\u30ff\\uac00-\\ud7a3]"),
        "_"
    )
    
    // 合併連續底線，並去除首尾底線
    val singleUnderscores = cleanedTitle.replace(Regex("_+"), "_")
    val trimmed = singleUnderscores.trim('_')
    
    val baseName = if (trimmed.isEmpty()) "audio" else trimmed
    return if (extension.isNotEmpty()) "$baseName.$extension" else baseName
}
