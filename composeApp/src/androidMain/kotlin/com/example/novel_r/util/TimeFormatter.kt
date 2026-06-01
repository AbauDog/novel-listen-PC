package com.example.novel_r.util

import java.util.concurrent.TimeUnit

/**
 * 時間格式化工具
 */
object TimeFormatter {

    /**
     * 將毫秒轉換為 HH:MM:SS 或 MM:SS 格式
     * @param milliseconds 毫秒數
     * @param alwaysShowHours 是否總是顯示小時（即使為 0）
     * @return 格式化的時間字串
     */
    fun formatTime(milliseconds: Long, alwaysShowHours: Boolean = false): String {
        val hours = TimeUnit.MILLISECONDS.toHours(milliseconds)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60

        return when {
            hours > 0 || alwaysShowHours -> {
                String.format("%02d:%02d:%02d", hours, minutes, seconds)
            }
            else -> {
                String.format("%02d:%02d", minutes, seconds)
            }
        }
    }

    /**
     * 將毫秒轉換為詳細的時間描述
     * 例如：1小時 23分 45秒
     */
    fun formatTimeDetailed(milliseconds: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(milliseconds)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60

        return buildString {
            if (hours > 0) {
                append("${hours}小時 ")
            }
            if (minutes > 0) {
                append("${minutes}分 ")
            }
            if (seconds > 0 || (hours == 0L && minutes == 0L)) {
                append("${seconds}秒")
            }
        }.trim()
    }

    /**
     * 計算進度百分比
     */
    fun calculateProgress(current: Long, total: Long): Float {
        if (total <= 0) return 0f
        return (current.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * 格式化進度（例如：45.5%）
     */
    fun formatProgress(current: Long, total: Long): String {
        val percentage = calculateProgress(current, total) * 100
        return String.format("%.1f%%", percentage)
    }

    /**
     * 格式化剩餘時間
     */
    fun formatRemainingTime(currentPosition: Long, duration: Long): String {
        val remaining = duration - currentPosition
        if (remaining <= 0) return "00:00"
        return formatTime(remaining)
    }
}
