package com.example.novel_r.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.novel_r.data.model.AudioFile

@Composable
fun PlaylistSelectionDialog(
    playlist: List<AudioFile>,
    onConfirm: (List<AudioFile>) -> Unit,
    onDismiss: () -> Unit
) {
    val selectedTracks = remember { mutableStateListOf<AudioFile>().apply { addAll(playlist) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("選擇要加入的曲目") },
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("共發現 ${playlist.size} 首歌曲")
                    TextButton(onClick = {
                        if (selectedTracks.size == playlist.size) selectedTracks.clear() else {
                            selectedTracks.clear()
                            selectedTracks.addAll(playlist)
                        }
                    }) {
                        Text(if (selectedTracks.size == playlist.size) "取消全選" else "全選")
                    }
                }
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(playlist) { track ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = selectedTracks.contains(track),
                                onCheckedChange = { checked ->
                                    if (checked) selectedTracks.add(track) else selectedTracks.remove(track)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = track.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedTracks.toList()) }, enabled = selectedTracks.isNotEmpty()) {
                Text("加入 (${selectedTracks.size})")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
