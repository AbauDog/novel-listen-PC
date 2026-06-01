package com.example.novel_r.util

/**
 * 時間格式化工具 (純 Kotlin 版本)
 */
object TimeFormatter {

    fun formatTime(milliseconds: Long, alwaysShowHours: Boolean = false): String {
        val totalSeconds = milliseconds / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0 || alwaysShowHours) {
            "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        } else {
            "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
        }
    }

    fun calculateProgress(current: Long, total: Long): Float {
        if (total <= 0) return 0f
        return (current.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }

    fun formatProgress(current: Long, total: Long): String {
        val percentage = calculateProgress(current, total) * 100
        return "${((percentage * 10).toInt() / 10.0)}%"
    }

    fun formatRemainingTime(currentPosition: Long, duration: Long): String {
        val remaining = duration - currentPosition
        if (remaining <= 0) return "00:00"
        return formatTime(remaining)
    }
}
