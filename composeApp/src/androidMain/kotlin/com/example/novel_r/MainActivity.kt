package com.example.novel_r

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.novel_r.ui.screens.PlayerScreen
import com.example.novel_r.ui.theme.Novel_RTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.novel_r.ui.viewmodel.PlayerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Novel_RTheme {
                val navController = rememberNavController()
                // 建立共享的 PlayerViewModel，讓所有畫面都使用同一個實例
                val sharedPlayerViewModel: PlayerViewModel = viewModel()

                NavHost(
                    navController = navController,
                    startDestination = "file_list"
                ) {
                    // 1. 檔案列表畫面
                    composable("file_list") {
                        com.example.novel_r.ui.screens.FileListScreen(
                            playerViewModel = sharedPlayerViewModel,
                            onFileSelected = { filePath, fileName ->
                                // 對路徑進行編碼以避免特殊字符導致路由錯誤
                                val encodedPath = android.net.Uri.encode(filePath)
                                val encodedTitle = android.net.Uri.encode(fileName)
                                navController.navigate("player?filePath=$encodedPath&title=$encodedTitle")
                            },
                            onNavigateToPlayer = {
                                // 直接進入播放器（不帶新檔案），如果已有播放則繼續
                                navController.navigate("player")
                            }
                        )
                    }

                    // 2. 播放器畫面
                    composable(
                        route = "player?filePath={filePath}&title={title}",
                        arguments = listOf(
                            navArgument("filePath") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                            navArgument("title") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            }
                        )
                    ) { backStackEntry ->
                        val filePath = backStackEntry.arguments?.getString("filePath")
                        val title = backStackEntry.arguments?.getString("title")

                        com.example.novel_r.ui.screens.PlayerScreen(
                            filePath = filePath,
                            fileTitle = title,
                            viewModel = sharedPlayerViewModel,
                            onNavigateBack = {
                                if (navController.previousBackStackEntry != null) {
                                    navController.popBackStack()
                                } else {
                                    // 如果無處可退（例如直接開啟），則退到列表（雖然這裡結構上應該不會發生）
                                    navController.navigate("file_list") {
                                        popUpTo("player") { inclusive = true }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
