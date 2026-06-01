package com.example.novel_r.data.repository

import android.content.Context
import com.example.novel_r.data.local.AudioFile
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class YouTubeRepository(private val context: Context) {

    suspend fun getStreamInfo(url: String): Result<AudioFile> = withContext(Dispatchers.IO) {
        try {
            val request = YoutubeDLRequest(url)
            request.addOption("-f", "bestaudio/best")
            
            val info = YoutubeDL.getInstance().getInfo(request)
            
            val audioFile = AudioFile(
                filePath = url, // Use original URL as the stable ID
                fileName = info.title ?: "Unknown Title",
                fileSize = 0, // Stream size unknown usually
                duration = (info.duration * 1000).toLong(),
                mimeType = "audio/mpeg", // Or derived from info.ext
                isStream = true,
                streamUrl = info.url, // The dynamic stream link
                originalUrl = url,
                thumbnailUrl = info.thumbnail
            )
            Result.success(audioFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPlaylistInfo(url: String): Result<Pair<String, List<AudioFile>>> = withContext(Dispatchers.IO) {
        try {
            val request = YoutubeDLRequest(url)
            request.addOption("--flat-playlist")
            request.addOption("--dump-single-json")
            
            val response = YoutubeDL.getInstance().execute(request)
            val jsonString = response.out
            
            val gson = com.google.gson.Gson()
            val jsonObject = gson.fromJson(jsonString, com.google.gson.JsonObject::class.java)
            
            // Extract playlist title
            val playlistTitle = if (jsonObject.has("title")) jsonObject.get("title").asString else "未命名播放清單"
            
            val entriesArray = jsonObject.getAsJsonArray("entries")
            val list = mutableListOf<AudioFile>()
            
            entriesArray?.forEach { element ->
                val entry = element.asJsonObject
                val entryUrl = if (entry.has("url")) entry.get("url").asString else ""
                val entryTitle = if (entry.has("title")) entry.get("title").asString else "Unknown"
                val entryDuration = if (entry.has("duration")) entry.get("duration").asLong else 0L
                
                // Construct full URL if it's just an ID
                val fullUrl = if (entryUrl.startsWith("http")) entryUrl else "https://www.youtube.com/watch?v=$entryUrl"
                
                list.add(AudioFile(
                    filePath = fullUrl,
                    fileName = entryTitle,
                    fileSize = 0,
                    duration = entryDuration * 1000,
                    mimeType = "audio/mpeg",
                    isStream = true,
                    streamUrl = null,
                    originalUrl = fullUrl,
                    thumbnailUrl = null 
                ))
            }

            Result.success(Pair(playlistTitle, list))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateYoutubeDL(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
