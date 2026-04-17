package com.example.novel_r.util

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

object AudioPlayerCache {
    private var simpleCache: SimpleCache? = null
    // 1GB Cache Size (Increased to handle longer buffers)
    private const val MAX_CACHE_SIZE: Long = 1024 * 1024 * 1024

    @Synchronized
    fun getInstance(context: Context): SimpleCache {
        if (simpleCache == null) {
            val cacheDir = File(context.cacheDir, "media_cache")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            
            val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE)
            val databaseProvider = StandaloneDatabaseProvider(context)
            
            simpleCache = SimpleCache(cacheDir, evictor, databaseProvider)
        }
        return simpleCache!!
    }

    @Synchronized
    fun release() {
        try {
            simpleCache?.release()
            simpleCache = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
