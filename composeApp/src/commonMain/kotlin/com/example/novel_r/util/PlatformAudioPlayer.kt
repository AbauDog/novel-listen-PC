package com.example.novel_r.util

import kotlinx.coroutines.flow.StateFlow

/**
 * 跨平台音訊播放器介面
 */
interface PlatformAudioPlayer {
    val isPlaying: StateFlow<Boolean>
    val currentPosition: StateFlow<Long>
    val duration: StateFlow<Long>
    val debugLog: StateFlow<String>

    /**
     * 播放音訊
     * @param url 檔案路徑或網路網址
     * @param startPosition 起始播放位置 (毫秒)
     * @param totalDuration 總長度 (毫秒，選填，用於網路串流無法掃描長度時)
     */
    fun play(url: String, startPosition: Long = 0, totalDuration: Long = 0)
    
    fun pause()
    fun stop()
    fun seekTo(position: Long)
    fun setSpeed(speed: Float)
    fun release()
    fun clearDebugLog()
}

/**
 * 創建平台相關的播放器
 */
expect fun createPlatformAudioPlayer(context: Any?): PlatformAudioPlayer
