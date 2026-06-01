package com.example.novel_r.data.model

import com.google.gson.annotations.SerializedName

/**
 * 音訊檔案 - 儲存檔案基本資訊
 */
data class AudioFile(
    val filePath: String = "",
    val fileName: String = "",
    val fileSize: Long = 0L,
    val duration: Long = 0L,
    val mimeType: String? = null,
    val addedTimestamp: Long = System.currentTimeMillis(),
    val isValid: Boolean = true,
    
    // YouTube 串流擴充欄位
    val isStream: Boolean = false,
    val streamUrl: String? = null,
    val originalUrl: String? = null,
    val thumbnailUrl: String? = null
)

/**
 * 播放進度 - 儲存播放進度
 */
data class AudioProgress(
    val filePath: String = "",
    val fileName: String = "",
    val fileSize: Long = 0L,
    val lastPosition: Long = 0L,
    val duration: Long = 0L,
    val lastPlayedTimestamp: Long = System.currentTimeMillis(),
    val playCount: Int = 0
)

/**
 * 播放清單書籤
 */
data class PlaylistBookmark(
    val id: Long = 0L,
    val url: String = "",
    val name: String = "",
    val thumbnailUrl: String? = null,
    val trackCount: Int = 0,
    val addedTimestamp: Long = System.currentTimeMillis()
)

/**
 * 整個應用的資料結構，用於 JSON 儲存
 */
data class AppData(
    @SerializedName("audio_files")
    val audioFiles: List<AudioFile> = emptyList(),
    @SerializedName("progress_list")
    val progressList: List<AudioProgress> = emptyList(),
    @SerializedName("bookmarks")
    val bookmarks: List<PlaylistBookmark> = emptyList(),
    @SerializedName("last_folder_path")
    val lastFolderPath: String? = null
)
