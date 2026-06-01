package com.example.novel_r.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.novel_r.util.TimeFormatter

/**
 * 進度條組件
 */
@Composable
fun ProgressSeekBar(
    currentPosition: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // 進度條
        Slider(
            value = if (duration > 0) currentPosition.toFloat() else 0f,
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
            modifier = Modifier.fillMaxWidth()
        )
        
        // 時間顯示
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = TimeFormatter.formatTime(currentPosition),
                style = MaterialTheme.typography.bodySmall
            )
            
            Text(
                text = TimeFormatter.formatTime(duration),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * 帶百分比顯示的進度條
 */
@Composable
fun ProgressSeekBarWithPercentage(
    currentPosition: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // 進度條
        Slider(
            value = if (duration > 0) currentPosition.toFloat() else 0f,
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
            modifier = Modifier.fillMaxWidth()
        )
        
        // 時間和百分比顯示
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 當前時間
            Text(
                text = TimeFormatter.formatTime(currentPosition),
                style = MaterialTheme.typography.bodySmall
            )
            
            // 百分比
            Text(
                text = TimeFormatter.formatProgress(currentPosition, duration),
                style = MaterialTheme.typography.bodySmall
            )
            
            // 總時間
            Text(
                text = TimeFormatter.formatTime(duration),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
