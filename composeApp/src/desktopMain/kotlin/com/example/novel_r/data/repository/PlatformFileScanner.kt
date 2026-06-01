package com.example.novel_r.data.repository

import com.example.novel_r.data.model.AudioFile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class PlatformFileScanner actual constructor(context: Any?) {
    private val supportedExtensions = setOf("mp3", "m4a", "wav", "aac", "flac")

    actual suspend fun scanDirectory(path: String): List<AudioFile> = withContext(Dispatchers.IO) {
        val root = File(path)
        if (!root.exists() || !root.isDirectory) return@withContext emptyList()
        
        val result = mutableListOf<AudioFile>()
        root.walk().filter { it.isFile && it.extension.lowercase() in supportedExtensions }.forEach { file ->
            result.add(
                AudioFile(
                    filePath = file.absolutePath,
                    fileName = file.name,
                    fileSize = file.length(),
                    duration = 0, // PC 端暫時不解析時長
                    mimeType = "audio/${file.extension.lowercase()}"
                )
            )
        }
        result
    }

    actual suspend fun parseAudioMetadata(path: String): AudioFile? = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) return@withContext null
        AudioFile(
            filePath = file.absolutePath,
            fileName = file.name,
            fileSize = file.length(),
            duration = 0,
            mimeType = "audio/${file.extension.lowercase()}"
        )
    }

    actual suspend fun deleteFile(path: String): Boolean = withContext(Dispatchers.IO) {
        File(path).delete()
    }
}
