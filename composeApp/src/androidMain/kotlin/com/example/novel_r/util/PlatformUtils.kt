package com.example.novel_r.util

import android.app.Application
import java.io.File

// 注意：這需要一個全域的 Context 或是由 NovelRApplication 提供
// 為了簡單起見，我們可以在 Application 啟動時初始化
lateinit var androidAppPath: String

actual fun getAppDataPath(): String {
    return androidAppPath
}
