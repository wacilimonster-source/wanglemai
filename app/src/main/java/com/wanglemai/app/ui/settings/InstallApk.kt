package com.wanglemai.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.SnackbarHostState
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

/**
 * Hands a downloaded update APK to the system installer through FileProvider. Without the
 * "install unknown apps" grant it routes to the system settings page first and asks the
 * user to come back; installation itself is always confirmed in the system dialog.
 */
internal fun installApk(context: Context, file: File, scope: CoroutineScope, snack: SnackbarHostState) {
    if (!context.packageManager.canRequestPackageInstalls()) {
        val settingsIntent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val opened = runCatching { context.startActivity(settingsIntent) }.isSuccess
        scope.launch {
            snack.showSnackbar(
                if (opened) "请先允许「安装未知应用」，再回来点安装"
                else "请在系统设置中允许本应用安装未知应用"
            )
        }
        return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    // PackageInstaller sessions are the modern API, but the system-installer intent stays the
    // simplest path for a side-loaded app and is still honored through current Android.
    @Suppress("DEPRECATION")
    val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
        data = uri
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }.onFailure {
        scope.launch { snack.showSnackbar("无法启动系统安装器") }
    }
}
