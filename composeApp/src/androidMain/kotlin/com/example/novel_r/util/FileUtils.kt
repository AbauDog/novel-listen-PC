package com.example.novel_r.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.text.DecimalFormat

/**
 * 檔案工具類別
 */
object FileUtils {

    /**
     * 格式化檔案大小
     * @param bytes 檔案大小（位元組）
     * @return 格式化的檔案大小字串（例如：1.5 GB、256 MB）
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"

        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()

        val size = bytes / Math.pow(1024.0, digitGroups.toDouble())
        val df = DecimalFormat("#,##0.#")

        return "${df.format(size)} ${units[digitGroups]}"
    }

    /**
     * 檢查檔案是否存在（SAF）
     */
    fun exists(context: Context, uriString: String): Boolean {
        return try {
            val uri = Uri.parse(uriString)
            val documentFile = DocumentFile.fromSingleUri(context, uri)
            documentFile?.exists() == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 取得檔案名稱（不含副檔名）
     */
    fun getFileNameWithoutExtension(fileName: String): String {
        val lastDotIndex = fileName.lastIndexOf('.')
        return if (lastDotIndex > 0) {
            fileName.substring(0, lastDotIndex)
        } else {
            fileName
        }
    }

    /**
     * 取得檔案副檔名
     */
    fun getFileExtension(fileName: String): String {
        val lastDotIndex = fileName.lastIndexOf('.')
        return if (lastDotIndex > 0 && lastDotIndex < fileName.length - 1) {
            fileName.substring(lastDotIndex + 1).lowercase()
        } else {
            ""
        }
    }

    /**
     * 檢查是否為音訊檔案
     */
    fun isAudioFile(fileName: String): Boolean {
        val supportedFormats = setOf(
            "mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "wma"
        )
        val extension = getFileExtension(fileName)
        return extension in supportedFormats
    }

    /**
     * 取得檔案的 MIME 類型
     */
    fun getMimeType(fileName: String): String {
        return when (getFileExtension(fileName)) {
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "opus" -> "audio/opus"
            "wma" -> "audio/x-ms-wma"
            else -> "audio/*"
        }
    }

    /**
     * 驗證 URI 是否有效
     */
    fun isValidUri(uriString: String?): Boolean {
        if (uriString.isNullOrBlank()) return false
        return try {
            val uri = Uri.parse(uriString)
            uri != null && uri.scheme != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 取得檔案資訊摘要
     */
    fun getFileInfo(fileName: String, fileSize: Long, duration: Long): String {
        return buildString {
            append(fileName)
            append(" • ")
            append(formatFileSize(fileSize))
            append(" • ")
            append(TimeFormatter.formatTime(duration))
        }
    }
}
