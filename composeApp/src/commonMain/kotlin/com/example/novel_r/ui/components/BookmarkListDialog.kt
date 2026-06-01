package com.example.novel_r.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.novel_r.data.model.PlaylistBookmark

@Composable
fun BookmarkListDialog(
    bookmarks: List<PlaylistBookmark>,
    onSelect: (PlaylistBookmark) -> Unit,
    onDelete: (PlaylistBookmark) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("我的最愛 (播放清單)") },
        text = {
            if (bookmarks.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    Text("暫無收藏")
                }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(bookmarks) { bookmark ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onSelect(bookmark) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PlaylistPlay, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = bookmark.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                Text(text = "共 ${bookmark.trackCount} 首歌曲", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { onDelete(bookmark) }) {
                                Icon(Icons.Default.Delete, contentDescription = "刪除", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("關閉") } }
    )
}
