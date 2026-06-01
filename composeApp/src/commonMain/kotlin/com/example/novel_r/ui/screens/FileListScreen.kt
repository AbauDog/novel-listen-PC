package com.example.novel_r.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.ExperimentalComposeUiApi
import com.example.novel_r.data.model.AudioFile
import com.example.novel_r.ui.viewmodel.FileListViewModel
import com.example.novel_r.ui.viewmodel.PlayerViewModel
import com.example.novel_r.util.rememberFolderPicker
import com.example.novel_r.util.rememberFilePicker
import com.example.novel_r.util.rememberSaveFilePicker
import com.example.novel_r.util.sanitizeFileName
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun FileListScreen(
    viewModel: FileListViewModel,
    playerViewModel: PlayerViewModel,
    onFileSelected: (String, String) -> Unit,
    onNavigateToPlayer: () -> Unit,
    onExit: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val playerUiState by playerViewModel.uiState.collectAsState()
    
    var showYoutubeDialog by remember { mutableStateOf(false) }
    var youtubeUrl by remember { mutableStateOf("") }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) } // 新增排序選單狀態
    
    // 2. 開啟資料夾選取
    val folderPicker = rememberFolderPicker(initialPath = uiState.selectedFolderUri) { path ->
        viewModel.scanFolder(path)
    }
    
    // 2. 開啟檔案選取
    val filePicker = rememberFilePicker(initialPath = uiState.selectedFolderUri) { paths ->
        viewModel.addFiles(paths)
    }
    
    var pendingSaveSourceFile by remember { mutableStateOf<java.io.File?>(null) }
    
    val saveFilePicker = rememberSaveFilePicker(
        initialPath = uiState.selectedFolderUri,
        onFileSelected = { destPath ->
            pendingSaveSourceFile?.let { source ->
                try {
                    source.copyTo(java.io.File(destPath), overwrite = true)
                    println("[FileListScreen] 另存新檔成功: ${source.absolutePath} -> $destPath")
                } catch (e: Exception) {
                    e.printStackTrace()
                    println("[FileListScreen] 另存新檔失敗: ${e.message}")
                }
            }
            pendingSaveSourceFile = null
        }
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 2. 資料夾按鈕
                        IconButton(
                            onClick = { folderPicker.launch() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.FolderOpen, 
                                contentDescription = "Open Folder",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        
                        // 2. 檔案按鈕 (新增)
                        IconButton(
                            onClick = { filePicker.launch() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.PostAdd, 
                                contentDescription = "Add Files",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        
                        // 網路按鈕
                        IconButton(
                            onClick = { showYoutubeDialog = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.CloudDownload, 
                                contentDescription = "網路串流網址",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // 排序按鈕 (新增)
                        Box {
                            IconButton(
                                onClick = { showSortMenu = true },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Sort, 
                                    contentDescription = "排序方式",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("檔案時間", fontSize = 12.sp) },
                                    leadingIcon = { 
                                        Icon(
                                            Icons.Default.Schedule, 
                                            null, 
                                            Modifier.size(16.dp),
                                            tint = if (uiState.sortOrder == com.example.novel_r.ui.viewmodel.SortOrder.TIME) MaterialTheme.colorScheme.primary else Color.Gray
                                        ) 
                                    },
                                    onClick = {
                                        viewModel.setSortOrder(com.example.novel_r.ui.viewmodel.SortOrder.TIME)
                                        showSortMenu = false
                                    },
                                    modifier = Modifier.height(18.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                )
                                DropdownMenuItem(
                                    text = { Text("名稱", fontSize = 12.sp) },
                                    leadingIcon = { 
                                        Icon(
                                            Icons.Default.SortByAlpha, 
                                            null, 
                                            Modifier.size(16.dp),
                                            tint = if (uiState.sortOrder == com.example.novel_r.ui.viewmodel.SortOrder.NAME) MaterialTheme.colorScheme.primary else Color.Gray
                                        ) 
                                    },
                                    onClick = {
                                        viewModel.setSortOrder(com.example.novel_r.ui.viewmodel.SortOrder.NAME)
                                        showSortMenu = false
                                    },
                                    modifier = Modifier.height(18.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                )
                                DropdownMenuItem(
                                    text = { Text("檔案大小", fontSize = 12.sp) },
                                    leadingIcon = { 
                                        Icon(
                                            Icons.Default.Storage, 
                                            null, 
                                            Modifier.size(16.dp),
                                            tint = if (uiState.sortOrder == com.example.novel_r.ui.viewmodel.SortOrder.SIZE) MaterialTheme.colorScheme.primary else Color.Gray
                                        ) 
                                    },
                                    onClick = {
                                        viewModel.setSortOrder(com.example.novel_r.ui.viewmodel.SortOrder.SIZE)
                                        showSortMenu = false
                                    },
                                    modifier = Modifier.height(18.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                )
                            }
                        }

                        
                        Spacer(modifier = Modifier.width(6.dp))
                        
                        Text(
                            text = "音訊檔案列表", 
                            fontSize = 13.sp, 
                            color = Color(0xFF00008B),
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        
                        // 1. 清除所有清單按鈕
                        IconButton(
                            onClick = { showClearConfirmDialog = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.DeleteSweep, 
                                contentDescription = "Clear All",
                                modifier = Modifier.size(18.dp),
                                tint = Color.Gray
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToPlayer,
                        modifier = Modifier.size(32.dp)
                    ) { 
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp)) 
                    }
                    
                    IconButton(
                        onClick = onExit,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.PowerSettingsNew, 
                            contentDescription = "Exit App", 
                            tint = Color.Red,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                modifier = Modifier.height(40.dp)
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (uiState.audioFiles.isEmpty()) {
                Text("尚未選擇檔案或資料夾", modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.audioFiles) { audioFile ->
                        val isCurrentlyPlaying = playerUiState.currentFilePath == audioFile.filePath
                        val savedProgress = uiState.progressMap[audioFile.filePath]
                        
                        val currentPos = if (isCurrentlyPlaying) playerUiState.currentPosition else (savedProgress?.lastPosition ?: 0L)
                        val duration = if (isCurrentlyPlaying && playerUiState.duration > 0) playerUiState.duration else (savedProgress?.duration ?: 0L)
                        val percentage = if (duration > 0) (currentPos.toFloat() / duration * 100).toInt().coerceIn(0, 100) else 0

                        AudioFileItem(
                            audioFile = audioFile,
                            isCurrentlyPlaying = isCurrentlyPlaying,
                            isPlayingNow = isCurrentlyPlaying && playerUiState.isPlaying,
                            percentage = percentage,
                            onClick = { onFileSelected(audioFile.filePath, audioFile.fileName) },
                            onRemove = { viewModel.removeFile(audioFile) },
                            onDelete = { 
                                if (isCurrentlyPlaying) {
                                    playerViewModel.stop()
                                }
                                viewModel.deleteFile(audioFile) 
                            },
                            onSaveAs = { sourceFile, defaultName ->
                                pendingSaveSourceFile = sourceFile
                                saveFilePicker.launch(defaultName)
                            },
                            onSplit = { viewModel.splitFile(audioFile) }
                        )
                    }
                }
            }
            
            if (uiState.isUpdatingYtDlp || uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter))
            }
        }
    }
    
    if (showYoutubeDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showYoutubeDialog = false }
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF2B2B2B), // 深色背景
                modifier = Modifier
                    .width(300.dp)
                    .border(1.dp, Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("網路影片/串流網址 (支援 YouTube、FB 等):", fontSize = 12.sp, color = Color.LightGray)
                    BasicTextField(
                        value = youtubeUrl,
                        onValueChange = { youtubeUrl = it },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color.White),
                        cursorBrush = SolidColor(Color.White),
                        decorationBox = @Composable { innerTextField ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                    .border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                if (youtubeUrl.isEmpty()) {
                                    Text("貼上 YouTube、FB 等網址...", color = Color.Gray, fontSize = 13.sp)
                                }
                                innerTextField()
                            }
                        }
                    )
                    
                    if (uiState.errorMessage != null) {
                        Text(uiState.errorMessage!!, color = Color.Yellow, fontSize = 11.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { showYoutubeDialog = false },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) { Text("取消", fontSize = 12.sp) }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Button(
                            onClick = {
                                if (youtubeUrl.isNotBlank()) {
                                    val urlToDownload = youtubeUrl.trim()
                                    youtubeUrl = ""
                                    viewModel.processYouTubeUrl(urlToDownload) { downloadedFile ->
                                        showYoutubeDialog = false
                                        onFileSelected(downloadedFile.filePath, downloadedFile.fileName)
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            if (uiState.isUpdatingYtDlp) {
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = Color.White)
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text("確認", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("確認清除", fontSize = 16.sp) },
            text = { Text("確定要清除目前所有的檔案清單嗎？(不會刪除實際檔案)", fontSize = 14.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllFiles()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("確定清除", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun AudioFileItem(
    audioFile: AudioFile,
    isCurrentlyPlaying: Boolean,
    isPlayingNow: Boolean,
    percentage: Int,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onDelete: () -> Unit,
    onSaveAs: (java.io.File, String) -> Unit,
    onSplit: () -> Unit // 新增切割回呼
) {
    var showMenu by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val sourceFile = remember(audioFile.filePath) { java.io.File(audioFile.filePath) }
    val fileExists = remember(audioFile.filePath) { sourceFile.exists() }

    Box {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 1.dp)
                .onPointerEvent(PointerEventType.Press) {
                    if (it.button == PointerButton.Secondary) {
                        showMenu = true
                    }
                }
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                ),
            shape = MaterialTheme.shapes.extraSmall,
            color = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else Color.Transparent
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
            Icon(
                imageVector = when {
                    audioFile.filePath.startsWith("pending_") -> Icons.Default.Sync
                    isPlayingNow -> Icons.Default.VolumeUp
                    isCurrentlyPlaying -> Icons.Default.PauseCircle
                    audioFile.isStream -> Icons.Default.Cloud
                    else -> Icons.Default.AudioFile
                },
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (audioFile.filePath.startsWith("pending_")) Color.Gray 
                       else if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary 
                       else if (audioFile.isStream) Color(0xFF1E88E5) 
                       else Color.Gray
            )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = audioFile.fileName,
                    fontSize = 12.sp,
                    color = Color(0xFF00008B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = "$percentage%",
                    fontSize = 10.sp,
                    color = if (percentage > 0) MaterialTheme.colorScheme.primary else Color.Gray,
                    modifier = Modifier.width(34.dp)
                )
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            if (audioFile.originalUrl != null) {
                DropdownMenuItem(
                    text = { Text("複製原始網址", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)) },
                    onClick = {
                        clipboardManager.setText(AnnotatedString(audioFile.originalUrl))
                        showMenu = false
                    },
                    modifier = Modifier.height(18.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                )
            }
            if (fileExists) {
                DropdownMenuItem(
                    text = { Text("另存新檔", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Save, null, Modifier.size(16.dp)) },
                    onClick = {
                        val ext = sourceFile.extension
                        val defaultName = sanitizeFileName(audioFile.fileName, ext)
                        onSaveAs(sourceFile, defaultName)
                        showMenu = false
                    },
                    modifier = Modifier.height(18.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                )
                DropdownMenuItem(
                    text = { Text("切割檔案", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.ContentCut, null, Modifier.size(16.dp)) },
                    onClick = {
                        onSplit()
                        showMenu = false
                    },
                    modifier = Modifier.height(18.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                )
            }
            DropdownMenuItem(
                text = { Text("從列表移除", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.RemoveCircleOutline, null, Modifier.size(16.dp)) },
                onClick = {
                    onRemove()
                    showMenu = false
                },
                modifier = Modifier.height(18.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            )
            DropdownMenuItem(
                text = { Text("實體刪除檔案", fontSize = 12.sp, color = Color.Red) },
                leadingIcon = { Icon(Icons.Default.DeleteForever, null, Modifier.size(16.dp), tint = Color.Red) },
                onClick = {
                    onDelete()
                    showMenu = false
                },
                modifier = Modifier.height(18.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            )
        }
    }
}


