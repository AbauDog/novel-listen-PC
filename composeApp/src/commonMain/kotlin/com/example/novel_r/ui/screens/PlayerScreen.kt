package com.example.novel_r.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import com.example.novel_r.ui.theme.NovelRTheme
import com.example.novel_r.ui.viewmodel.PlayerViewModel
import com.example.novel_r.util.TimeFormatter
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun PlayerScreen(
    filePath: String? = null,
    fileTitle: String? = null,
    viewModel: PlayerViewModel,
    onNavigateBack: () -> Unit,
    onExit: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    var sliderPosition by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.currentPosition, uiState.duration, isDragging) {
        if (!isDragging) {
            if (uiState.duration > 0) {
                sliderPosition = uiState.currentPosition.toFloat()
            } else {
                sliderPosition = 0f
            }
        }
    }

    LaunchedEffect(filePath) {
        if (filePath != null) {
            viewModel.playMedia(filePath, title = fileTitle)
        }
    }
    
    NovelRTheme(isLockMode = uiState.isLockMode) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TextButton(onClick = { viewModel.rewind(60) }, contentPadding = PaddingValues(0.dp)) {
                                Text("-60", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = { viewModel.rewind(30) }, contentPadding = PaddingValues(0.dp)) {
                                Text("-30", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            
                            FilledIconButton(
                                onClick = { viewModel.togglePlayPause() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, 
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            
                            TextButton(onClick = { viewModel.fastForward(30) }, contentPadding = PaddingValues(0.dp)) {
                                Text("+30", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = { viewModel.fastForward(60) }, contentPadding = PaddingValues(0.dp)) {
                                Text("+60", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.size(32.dp) // 縮小返回按鈕
                        ) {
                            Icon(Icons.Default.ArrowBack, null, modifier = Modifier.size(18.dp))
                        }
                    },
                    actions = {
                        // 1. 關閉按鈕縮小 1/2
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
                    modifier = Modifier.height(40.dp) // 頂部欄高度也縮小
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Slider(
                    value = sliderPosition,
                    onValueChange = { 
                        isDragging = true
                        sliderPosition = it
                    },
                    onValueChangeFinished = {
                        viewModel.seekTo(sliderPosition.toLong())
                        isDragging = false
                    },
                    valueRange = 0f..(uiState.duration.toFloat().coerceAtLeast(1f)),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), 
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(TimeFormatter.formatTime(sliderPosition.toLong()), fontSize = 11.sp)
                    Text(TimeFormatter.formatTime(uiState.duration), fontSize = 11.sp)
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = uiState.currentTitle, 
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )

                if (uiState.downloadProgress != null) {
                    Text(
                        text = "📥 正在背景下載中: ${uiState.downloadProgress}%",
                        fontSize = 12.sp,
                        color = Color(0xFF006400), // 深綠色
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                } else if (uiState.currentFilePath?.startsWith("youtube:") == true) {
                    Text(
                        text = "📥 背景下載準備中...",
                        fontSize = 12.sp,
                        color = Color(0xFF006400).copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                
                // Debug Log Area
                if (uiState.debugLog.isNotEmpty()) {
                    var showLogMenu by remember { mutableStateOf(false) }
                    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color.Black.copy(alpha = 0.8f))
                            .onPointerEvent(PointerEventType.Press) {
                                if (it.button == PointerButton.Secondary) {
                                    showLogMenu = true
                                }
                            }
                    ) {
                        val scrollState = rememberScrollState()
                        SelectionContainer {
                            Text(
                                text = uiState.debugLog,
                                color = Color.Green,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState)
                                    .padding(4.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showLogMenu,
                            onDismissRequest = { showLogMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("複製log", fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)) },
                                onClick = {
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(uiState.debugLog))
                                    showLogMenu = false
                                },
                                modifier = Modifier.height(18.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            )
                            DropdownMenuItem(
                                text = { Text("刪除log", fontSize = 12.sp, color = Color.Red) },
                                leadingIcon = { Icon(Icons.Default.DeleteForever, null, Modifier.size(16.dp), tint = Color.Red) },
                                onClick = {
                                    viewModel.clearDebugLog()
                                    showLogMenu = false
                                },
                                modifier = Modifier.height(18.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            )
                        }
                        
                        LaunchedEffect(uiState.debugLog) {
                            scrollState.scrollTo(scrollState.maxValue)
                        }
                    }
                }
            }
        }
    }
}
