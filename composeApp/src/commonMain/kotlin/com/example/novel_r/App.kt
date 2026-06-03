package com.example.novel_r

import androidx.compose.runtime.*
import androidx.navigation.compose.*
import com.example.novel_r.data.repository.AudioRepository
import com.example.novel_r.ui.screens.FileListScreen
import com.example.novel_r.ui.screens.PlayerScreen
import com.example.novel_r.ui.viewmodel.FileListViewModel
import com.example.novel_r.ui.viewmodel.PlayerViewModel

import com.example.novel_r.ui.theme.NovelRTheme

@Composable
fun App(
    audioRepository: AudioRepository,
    playerViewModel: PlayerViewModel,
    onExit: () -> Unit // 新增退出回呼
) {
    // 1. 初始化 FileListViewModel
    val fileListViewModel = remember { FileListViewModel(audioRepository) }

    // 2. 設定導覽
    val navController = rememberNavController()
    
    val playerUiState by playerViewModel.uiState.collectAsState()
    
    NovelRTheme(isLockMode = playerUiState.isLockMode) {
        NavHost(navController = navController, startDestination = "fileList") {
            composable("fileList") {
                FileListScreen(
                    viewModel = fileListViewModel,
                    playerViewModel = playerViewModel,
                    onFileSelected = { path, title ->
                        val encodedPath = encodeParam(path)
                        val encodedTitle = encodeParam(title)
                        navController.navigate("player?path=$encodedPath&title=$encodedTitle")
                    },
                    onNavigateToPlayer = {
                        navController.navigate("player")
                    },
                    onExit = onExit
                )
            }
            composable("player?path={path}&title={title}") { backStackEntry ->
                val path = backStackEntry.arguments?.getString("path")
                val title = backStackEntry.arguments?.getString("title")
                PlayerScreen(
                    filePath = path,
                    fileTitle = title,
                    viewModel = playerViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onExit = onExit
                )
            }
        }
    }
}

/**
 * 簡易的純 Kotlin URL 百分比編碼器，專為安全傳遞導航參數設計。
 */
private fun encodeParam(value: String): String {
    val bytes = value.encodeToByteArray()
    return bytes.map { b ->
        val c = b.toInt() and 0xFF
        if ((c >= 'a'.code && c <= 'z'.code) ||
            (c >= 'A'.code && c <= 'Z'.code) ||
            (c >= '0'.code && c <= '9'.code) ||
            c == '-'.code || c == '_'.code || c == '.'.code || c == '~'.code
        ) {
            c.toChar().toString()
        } else {
            "%" + c.toString(16).uppercase().padStart(2, '0')
        }
    }.joinToString("")
}

