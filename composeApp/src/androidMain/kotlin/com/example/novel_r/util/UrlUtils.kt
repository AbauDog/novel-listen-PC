package com.example.novel_r.util

import android.net.Uri

object UrlUtils {
    /**
     * 檢查網址是否過期 (針對 YouTube 串流網址)
     */
    fun isUrlExpired(url: String?): Boolean {
        if (url == null) return false
        try {
            val uri = Uri.parse(url)
            val expireStr = uri.getQueryParameter("expire") ?: return false
            val expireTime = expireStr.toLongOrNull() ?: return false
            
            // 取得目前時間 (秒)
            val currentTime = System.currentTimeMillis() / 1000
            
            // 如果剩餘時間少於 5 分鐘 (300秒) 則視為過期，以確保播放順利
            return currentTime > (expireTime - 300)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
