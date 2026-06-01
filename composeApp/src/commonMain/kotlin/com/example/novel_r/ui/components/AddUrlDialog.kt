package com.example.novel_r.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AddUrlDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var isValid by remember { mutableStateOf(false) }

    LaunchedEffect(url) {
        isValid = url.isNotBlank() // 可以加入更嚴格的 URL 驗證
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增 YouTube URL") },
        text = {
            Column {
                Text("請輸入影片連結：")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(url) },
                enabled = isValid
            ) {
                Text("新增")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
