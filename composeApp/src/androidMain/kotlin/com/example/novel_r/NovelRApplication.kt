package com.example.novel_r

import android.app.Application
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException

class NovelRApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        com.example.novel_r.util.androidAppPath = filesDir.absolutePath
        initYtDlp()
    }

    private fun initYtDlp() {
        try {
            YoutubeDL.getInstance().init(applicationContext)
            FFmpeg.getInstance().init(applicationContext)
            Log.d("NovelRApplication", "yt-dlp and ffmpeg initialized successfully")
        } catch (e: YoutubeDLException) {
            Log.e("NovelRApplication", "Failed to initialize yt-dlp", e)
        } catch (e: Exception) {
            Log.e("NovelRApplication", "Failed to initialize ffmpeg", e)
        }
    }
}
