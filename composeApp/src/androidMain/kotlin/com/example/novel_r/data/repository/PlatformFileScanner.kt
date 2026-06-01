package com.example.novel_r.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.novel_r.data.model.AudioFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class PlatformFileScanner actual constructor(context: Any?) {
    private val ctx = context as Context
    private val supportedExtensions = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "wma")

    actual suspend fun scanDirectory(path: String): List<AudioFile> = withContext(Dispatchers.IO) {
        val folderUri = Uri.parse(path)
        val documentFile = DocumentFile.fromTreeUri(ctx, folderUri) ?: return@withContext emptyList()
        
        val audioFiles = mutableListOf<AudioFile>()
        scanRecursively(documentFile, audioFiles)
        audioFiles
    }

    private suspend fun scanRecursively(directory: DocumentFile, results: MutableList<AudioFile>) {
        directory.listFiles().forEach { file ->
            when {
                file.isDirectory -> scanRecursively(file, results)
                file.isFile && isAudioFile(file.name) -> {
                    parseAudioMetadata(file.uri.toString())?.let { results.add(it) }
                }
            }
        }
    }

    private fun isAudioFile(fileName: String?): Boolean {
        if (fileName == null) return false
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in supportedExtensions
    }

    actual suspend fun parseAudioMetadata(path: String): AudioFile? = withContext(Dispatchers.IO) {
        // ... (preserving original logic)
        try {
            val uri = Uri.parse(path)
            val file = DocumentFile.fromSingleUri(ctx, uri) ?: return@withContext null
            
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(ctx, uri)
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

    actual suspend fun deleteFile(path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(path)
            val documentFile = DocumentFile.fromSingleUri(ctx, uri)
            documentFile?.delete() == true
        } catch (e: Exception) {
            false
        }
    }
}
