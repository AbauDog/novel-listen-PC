import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.*
import com.example.novel_r.App
import com.example.novel_r.ui.viewmodel.PlayerViewModel
import com.example.novel_r.data.repository.AudioRepository
import com.example.novel_r.data.repository.JsonStorageRepository
import com.example.novel_r.data.repository.PlatformFileScanner
import com.example.novel_r.util.createPlatformAudioPlayer

import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.NativeHookException
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.runBlocking

fun main() = application {
    var isWindowVisible by remember { mutableStateOf(true) }
    
    // 初始化全域播放器與 ViewModel
    val jsonStorage = remember { JsonStorageRepository() }
    val fileScanner = remember { PlatformFileScanner(null) }
    val youtubeDownloader = remember { com.example.novel_r.util.createYoutubeDownloader() }
    val audioRepository = remember { AudioRepository(jsonStorage, fileScanner, youtubeDownloader) }
    val player = remember { createPlatformAudioPlayer(null) }
    val playerViewModel = remember { PlayerViewModel(audioRepository, player) }

    // 啟動時立即載入資料，確保清單與進度能顯示
    LaunchedEffect(Unit) {
        jsonStorage.loadData()
    }

    val icon = painterResource("icon.png")

    // 註冊全域快速鍵 (F12)
    DisposableEffect(Unit) {
        val logger = Logger.getLogger(GlobalScreen::class.java.`package`.name)
        logger.level = Level.OFF
        logger.useParentHandlers = false

        val listener = object : NativeKeyListener {
            override fun nativeKeyPressed(e: NativeKeyEvent) {
                if (e.keyCode == NativeKeyEvent.VC_F12) {
                    playerViewModel.togglePlayPause()
                }
            }
        }

        try {
            GlobalScreen.registerNativeHook()
            GlobalScreen.addNativeKeyListener(listener)
        } catch (ex: NativeHookException) {
            println("無法註冊全域快速鍵: ${ex.message}")
        }

        onDispose {
            try {
                GlobalScreen.removeNativeKeyListener(listener)
                GlobalScreen.unregisterNativeHook()
            } catch (ex: NativeHookException) {
                ex.printStackTrace()
            }
        }
    }

    if (isWindowVisible) {
        Window(
            onCloseRequest = { isWindowVisible = false },
            title = "Abau.聽讀 v1.0",
            icon = icon,
            state = rememberWindowState(
                position = WindowPosition(Alignment.BottomStart),
                width = 380.dp,
                height = 180.dp
            )
        ) {
            App(
                audioRepository = audioRepository,
                playerViewModel = playerViewModel,
                onExit = {
                    playerViewModel.saveProgressBlocking()
                    try { GlobalScreen.unregisterNativeHook() } catch (e: Exception) {}
                    exitApplication()
                }
            )
        }
    }

    Tray(
        icon = icon,
        tooltip = "Abau.聽讀 v1.0",
        onAction = { isWindowVisible = true },
        menu = {
            Item("Show", onClick = { isWindowVisible = true })
            Separator()
            Item("Exit", onClick = {
                // 退出前強制儲存一次進度 (使用 runBlocking 確保完成)
                playerViewModel.saveProgressBlocking()
                
                try { GlobalScreen.unregisterNativeHook() } catch (e: Exception) {}
                
                exitApplication()
            })
        }
    )
}
