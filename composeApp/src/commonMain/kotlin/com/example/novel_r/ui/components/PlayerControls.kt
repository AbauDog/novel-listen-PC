package com.example.novel_r.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 播放控制組件
 */
@Composable
fun PlayerControls(
    isPlaying: Boolean,
    isLockMode: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRewind60: () -> Unit,
    onRewind120: () -> Unit,
    onForward60: () -> Unit,
    onForward120: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 快進快退按鈕列
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // -120s
            IconButton(onClick = onRewind120) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "快退 120 秒"
                    )
                    Text(text = "-120s", style = MaterialTheme.typography.labelSmall)
                }
            }
            
            // -60s
            IconButton(onClick = onRewind60) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Replay,
                        contentDescription = "快退 60 秒"
                    )
                    Text(text = "-60s", style = MaterialTheme.typography.labelSmall)
                }
            }
            
            // +60s
            IconButton(onClick = onForward60) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = "快進 60 秒"
                    )
                    Text(text = "+60s", style = MaterialTheme.typography.labelSmall)
                }
            }
            
            // +120s
            IconButton(onClick = onForward120) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "快進 120 秒"
                    )
                    Text(text = "+120s", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 主要控制按鈕列
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 上一首按鈕（鎖定模式下禁用）
            IconButton(
                onClick = onPrevious,
                enabled = !isLockMode
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "上一首",
                    modifier = Modifier.size(48.dp)
                )
            }
            
            // 播放/暫停按鈕（大按鈕）
            FilledIconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暫停" else "播放",
                    modifier = Modifier.size(48.dp)
                )
            }
            
            // 下一首按鈕（鎖定模式下禁用）
            IconButton(
                onClick = onNext,
                enabled = !isLockMode
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "下一首",
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}
