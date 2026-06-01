package com.example.novel_r.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import javax.swing.JFileChooser
import java.io.File
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
actual fun rememberFolderPicker(initialPath: String?, onFolderSelected: (String) -> Unit): FolderPicker {
    return remember(initialPath) {
        object : FolderPicker {
            override fun launch() {
                val chooser = JFileChooser()
                
                // 預設為程式執行路徑所在的根目錄 (即 resource 的上一層)
                val defaultDir = File(getAppDataPath()).parentFile ?: File(getAppDataPath())
                var targetDir = defaultDir
                
                if (initialPath != null) {
                    val file = File(initialPath)
                    if (file.exists()) {
                        targetDir = file
                    }
                }
                chooser.currentDirectory = targetDir
                chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                val result = chooser.showOpenDialog(null)
                if (result == JFileChooser.APPROVE_OPTION) {
                    onFolderSelected(chooser.selectedFile.absolutePath)
                }
            }
        }
    }
}

@Composable
actual fun rememberFilePicker(initialPath: String?, onFilesSelected: (List<String>) -> Unit): FolderPicker {
    return remember(initialPath) {
        object : FolderPicker {
            override fun launch() {
                val chooser = JFileChooser()
                
                // 預設為程式執行路徑所在的根目錄 (即 resource 的上一層)
                val defaultDir = File(getAppDataPath()).parentFile ?: File(getAppDataPath())
                var targetDir = defaultDir
                
                if (initialPath != null) {
                    val file = File(initialPath)
                    if (file.exists()) {
                        targetDir = file
                    }
                }
                chooser.currentDirectory = targetDir
                chooser.fileSelectionMode = JFileChooser.FILES_ONLY
                chooser.isMultiSelectionEnabled = true
                chooser.fileFilter = FileNameExtensionFilter("音訊檔案 (MP3, WAV)", "mp3", "wav", "m4a")
                
                val result = chooser.showOpenDialog(null)
                if (result == JFileChooser.APPROVE_OPTION) {
                    onFilesSelected(chooser.selectedFiles.map { it.absolutePath })
                }
            }
        }
    }
}

@Composable
actual fun rememberSaveFilePicker(initialPath: String?, onFileSelected: (String) -> Unit): SaveFilePicker {
    return remember(initialPath) {
        object : SaveFilePicker {
            override fun launch(defaultFileName: String) {
                val chooser = JFileChooser()
                
                // 預設為程式執行路徑所在的根目錄 (即 resource 的上一層)
                val defaultDir = File(getAppDataPath()).parentFile ?: File(getAppDataPath())
                var targetDir = defaultDir
                
                if (initialPath != null) {
                    val file = File(initialPath)
                    if (file.exists()) {
                        targetDir = file
                    }
                }
                chooser.currentDirectory = targetDir
                chooser.selectedFile = File(targetDir, defaultFileName)
                chooser.fileSelectionMode = JFileChooser.FILES_ONLY
                
                val ext = defaultFileName.substringAfterLast('.', "")
                if (ext.isNotEmpty()) {
                    chooser.fileFilter = FileNameExtensionFilter("音訊檔案 (*.$ext)", ext)
                }
                
                val result = chooser.showSaveDialog(null)
                if (result == JFileChooser.APPROVE_OPTION) {
                    onFileSelected(chooser.selectedFile.absolutePath)
                }
            }
        }
    }
}

