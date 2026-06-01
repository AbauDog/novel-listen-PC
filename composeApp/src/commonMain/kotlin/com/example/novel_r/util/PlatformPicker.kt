package com.example.novel_r.util

import androidx.compose.runtime.Composable

/**
 * 平台文件/資料夾選擇器
 */
@Composable
expect fun rememberFolderPicker(initialPath: String? = null, onFolderSelected: (String) -> Unit): FolderPicker

@Composable
expect fun rememberFilePicker(initialPath: String? = null, onFilesSelected: (List<String>) -> Unit): FolderPicker

interface FolderPicker {
    fun launch()
}

interface SaveFilePicker {
    fun launch(defaultFileName: String)
}

@Composable
expect fun rememberSaveFilePicker(
    initialPath: String? = null,
    onFileSelected: (String) -> Unit
): SaveFilePicker

