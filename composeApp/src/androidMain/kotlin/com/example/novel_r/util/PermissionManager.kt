package com.example.novel_r.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat

/**
 * 權限管理器 - 處理不同 Android 版本的權限申請
 */
class PermissionManager(private val context: Context) {

    /**
     * 檢查是否有音訊讀取權限
     */
    fun hasAudioPermission(): Boolean {
        return when {
            // Android 13+ (API 33+)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            }
            // Android 10-12 (API 29-32)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                // Android 10+ 建議使用 SAF，不需要 READ_EXTERNAL_STORAGE
                true
            }
            // Android 6-9 (API 23-28)
            else -> {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    /**
     * 取得需要申請的權限列表
     */
    fun getRequiredPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                // Android 10+ 使用 SAF，不需要申請權限
                emptyArray()
            }
            else -> {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    /**
     * 檢查是否需要申請權限
     */
    fun shouldRequestPermissions(): Boolean {
        val permissions = getRequiredPermissions()
        if (permissions.isEmpty()) return false

        return permissions.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 檢查是否應該顯示權限說明
     */
    fun shouldShowPermissionRationale(
        activity: android.app.Activity,
        permission: String
    ): Boolean {
        return activity.shouldShowRequestPermissionRationale(permission)
    }

    /**
     * 開啟應用程式設定頁面
     */
    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * 建立 SAF 資料夾選擇 Intent
     */
    fun createFolderPickerIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        }
    }

    /**
     * 保存資料夾存取權限
     */
    fun takePersistableUriPermission(uri: Uri) {
        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    /**
     * 取得已授權的資料夾 URI 列表
     */
    fun getPersistedUriPermissions(): List<Uri> {
        return try {
            context.contentResolver.persistedUriPermissions
                .filter { it.isReadPermission }
                .map { it.uri }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 釋放資料夾存取權限
     */
    fun releasePersistableUriPermission(uri: Uri) {
        val releaseFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        try {
            context.contentResolver.releasePersistableUriPermission(uri, releaseFlags)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}
