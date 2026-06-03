package com.example.novel_r.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    secondary = Color(0xFFCCC2DC),
    tertiary = Color(0xFFEFB8C8)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6650a4),
    secondary = Color(0xFF625b71),
    tertiary = Color(0xFF7D5260),
    background = Color(0xFFE2E2E2), // 微暗的淺灰色背景
    surface = Color(0xFFECECEC)     // 表面/對話框等底色
)

@Composable
fun NovelRTheme(
    darkTheme: Boolean = false, // 暫時關閉自動偵測以避免系統崩潰
    isLockMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        isLockMode -> darkColorScheme(
            background = Color.Black,
            onBackground = Color.DarkGray,
            primary = Color.DarkGray,
            onPrimary = Color.Black
        )
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
