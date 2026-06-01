package com.example.novel_r.util

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberFolderPicker(onFolderSelected: (String) -> Unit): FolderPicker {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { onFolderSelected(it.toString()) }
    }
    
    return remember {
        object : FolderPicker {
            override fun launch() {
                launcher.launch(null)
            }
        }
    }
}

@Composable
actual fun rememberSaveFilePicker(initialPath: String?, onFileSelected: (String) -> Unit): SaveFilePicker {
    return remember {
        object : SaveFilePicker {
            override fun launch(defaultFileName: String) {
                // Stub
            }
        }
    }
}

