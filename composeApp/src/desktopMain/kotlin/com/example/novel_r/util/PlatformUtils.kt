package com.example.novel_r.util

import java.io.File
import java.net.URI

private class PathLocator

actual fun getAppDataPath(): String {
    // 1. 嘗試從 compose.application.resources.dir 取得
    // 在打包環境中，此屬性通常指向 [AppDir]/app/resources/
    val resourcesDirProp = System.getProperty("compose.application.resources.dir")
    if (!resourcesDirProp.isNullOrBlank()) {
        val resDir = File(resourcesDirProp)
        var current: File? = resDir
        while (current != null) {
            if (current.name == "app") {
                val appDir = current.parentFile
                if (appDir != null && appDir.exists()) {
                    val resourceDir = File(appDir, "resource")
                    if (!resourceDir.exists()) resourceDir.mkdirs()
                    return resourceDir.absolutePath
                }
            }
            current = current.parentFile
        }
    }

    // 2. 嘗試從 Class 所在的 ProtectionDomain (JAR 包) 取得
    try {
        val uri: URI = PathLocator::class.java.protectionDomain.codeSource.location.toURI()
        val file = File(uri)
        val path = file.absolutePath
        // 判斷是否為開發環境的 class 目錄 (包含 build/classes 或 build/resources)
        if (!path.contains("build${File.separator}classes") && !path.contains("build${File.separator}resources")) {
            // 打包環境：例如 E:\tools\Novel_R_PC\app\composeApp.jar
            // 向上搜尋，尋找包含 "app" 的父資料夾，以定位到安裝根目錄
            var current: File? = file
            while (current != null) {
                if (current.name == "app") {
                    val appDir = current.parentFile
                    if (appDir != null && appDir.exists()) {
                        val resourceDir = File(appDir, "resource")
                        if (!resourceDir.exists()) resourceDir.mkdirs()
                        return resourceDir.absolutePath
                    }
                }
                current = current.parentFile
            }
        }
    } catch (e: Exception) {
        // 忽略異常，使用備用方案
    }

    // 3. 備用方案：使用當前執行路徑 (若在打包環境的 app 子目錄下，自動回退到父目錄)
    val userDir = System.getProperty("user.dir")
    val userDirFile = File(userDir)
    val baseDir = if (userDirFile.name == "app") {
        userDirFile.parentFile?.absolutePath ?: userDir
    } else {
        userDir
    }
    val resourceDir = File(baseDir, "resource")
    if (!resourceDir.exists()) {
        resourceDir.mkdirs()
    }
    return resourceDir.absolutePath
}
