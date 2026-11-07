package com.wanglemai.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wanglemai.app.app
import com.wanglemai.app.data.update.ApkDownloadState
import com.wanglemai.app.domain.*
import com.wanglemai.app.ui.main.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    vm: MainViewModel,
    snack: SnackbarHostState,
    scope: CoroutineScope,
    modifier: Modifier
) {
    val context = LocalContext.current
    val container = context.app.container
    val theme by vm.theme.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    var backupError by remember { mutableStateOf<String?>(null) }
    var importReport by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<Pair<String, BackupPreview>?>(null) }
    val currentVersion = remember { packageVersionName(context) }
    // Check/download state lives in the application-scoped UpdateCenter so a tab switch or
    // rotation neither cancels a running download nor discards its progress.
    val updateCenter = remember { context.app.container.updates }
    val updateBusy by updateCenter.checking.collectAsStateWithLifecycle()
    val updateResult by updateCenter.result.collectAsStateWithLifecycle()
    val updateError by updateCenter.checkError.collectAsStateWithLifecycle()
    val downloadState by updateCenter.download.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                backupError = null
                try {
                    withContext(Dispatchers.IO) {
                        val json = container.backup.exportJson()
                        writeText(context, uri, json)
                    }
                    snack.showSnackbar("备份已导出")
                } catch (_: Exception) {
                    backupError = "导出失败，请重试"
                } finally {
                    busy = false
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                backupError = null
                importReport = null
                try {
                    val payload = withContext(Dispatchers.IO) {
                        val json = readBoundedText(context, uri, BACKUP_MAX_BYTES)
                        json to container.backup.validateAndPreview(json)
                    }
                    pendingImport = payload
                } catch (e: BackupException) {
                    backupError = e.message
                } catch (e: IOException) {
                    backupError = if (e.message == "TOO_LARGE") "备份文件超过 5MB" else "读取文件失败"
                } catch (_: Exception) {
                    backupError = "导入失败，请重试"
                } finally {
                    busy = false
                }
            }
        }
    }

    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)
        .verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(8.dp))
        Text("忘了没 / 设置", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("留一点空间。", style = MaterialTheme.typography.headlineSmall)
        Text("简单、离线，由你掌握。", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        SettingsGroup("外观与桌面") {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("应用主题", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(selected = theme == mode, onClick = { vm.setTheme(mode) },
                            label = {
                                Text(when (mode) {
                                    ThemeMode.SYSTEM -> "跟随系统"
                                    ThemeMode.LIGHT -> "浅色"
                                    ThemeMode.DARK -> "深色"
                                })
                            })
                    }
                }
            }
            Text("小部件主题可在每个实例的配置中单独选择。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            WidgetPinControls(busy, scope, snack)
            Text("也可以从系统桌面的小部件列表添加。每个实例可独立选择清单、主题和密度；写入后小部件会主动刷新。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("澎湃 OS 3：本版本使用标准 Android Widget。普通桌面可直接添加；负一屏是否接纳取决于系统入口，需在真机单独验证，本版本不作保证。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SettingsGroup("清单与数据") {
            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("正在处理文件…", style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    enabled = !busy,
                    onClick = {
                        backupError = null
                        exportLauncher.launch(suggestedBackupName())
                    },
                    shape = MaterialTheme.shapes.medium
                ) { Text("导出备份") }
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        backupError = null
                        importLauncher.launch(arrayOf(
                            "application/json", "text/plain", "application/octet-stream", "text/json"
                        ))
                    }
                ) { Text("从文件恢复") }
            }
            Text("导出未删除的清单和记录（含已完成），不含草稿、小部件 ID 和设备设置。导入会先校验并预览，确认后按 ID 合并；同 ID 保留本机，不覆盖本机记录。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("备份文件包含你的记录，请妥善保存。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            backupError?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            importReport?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
        }

        SettingsGroup("关于忘了没") {
            Text("检查更新", style = MaterialTheme.typography.titleSmall)
            Text("当前版本 $currentVersion", style = MaterialTheme.typography.bodyMedium)
            Text("通过 GitHub Releases 检查新版本，需要联网；更新包在客户端内下载并自动校验 SHA256，由你确认后才会安装。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    enabled = !updateBusy,
                    onClick = { updateCenter.check(currentVersion) }
                ) { Text(if (updateBusy) "检查中…" else "检查更新") }
            }
        updateError?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        updateResult?.let { result ->
            Spacer(Modifier.height(8.dp))
            Text(
                if (result.isNewer) "发现新版本：${result.latestVersion}"
                else "已是最新版本：${result.latestVersion}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            if (result.viaAcceleratedSource) {
                Text("已通过国内加速清单检查", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (result.isNewer) {
                result.sha256?.let {
                    Text("SHA256：$it", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("下载优先走国内加速（jsDelivr），失败自动改用 GitHub；下载完成后自动校验 SHA256。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val hasApkSource = result.apkMirrorUrl != null || result.apkUrl != null
            if (result.isNewer && !hasApkSource) {
                Text("该版本未找到 APK 附件，请到发布页手动下载",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val state = downloadState
            when (state) {
                is ApkDownloadState.Downloading -> {
                    if (state.total > 0) {
                        LinearProgressIndicator(
                            progress = { (state.received.toFloat() / state.total).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(
                        "已下载 ${formatBytes(state.received)}" +
                            if (state.total > 0) " / ${formatBytes(state.total)}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        when {
                            state.viaAcceleratedSource -> "正在通过国内加速源下载"
                            result.apkMirrorUrl != null -> "加速源不可用，已改用 GitHub 重新下载"
                            else -> "正在从 GitHub 下载"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is ApkDownloadState.Failed -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                is ApkDownloadState.Ready -> {
                    Text(
                        if (state.sha256Verified) "安装包已下载并通过 SHA256 校验"
                        else "安装包已下载（此版本未提供 SHA256，跳过校验）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                    if (state.viaAcceleratedSource) {
                        Text("已通过国内加速源下载", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                ApkDownloadState.Idle -> {}
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state) {
                    is ApkDownloadState.Downloading -> {
                        OutlinedButton(onClick = { updateCenter.cancelDownload() }) { Text("取消下载") }
                    }
                    is ApkDownloadState.Failed -> {
                        if (result.isNewer && hasApkSource) {
                            Button(onClick = {
                                updateCenter.downloadApk(result.apkMirrorUrl, result.apkUrl, result.sha256)
                            }) { Text("重新下载") }
                        }
                    }
                    is ApkDownloadState.Ready -> {
                        Button(onClick = { installApk(context, state.file, scope, snack) }) {
                            Text("安装")
                        }
                        TextButton(onClick = {
                            updateCenter.downloadApk(result.apkMirrorUrl, result.apkUrl, result.sha256)
                        }) { Text("重新下载") }
                    }
                    ApkDownloadState.Idle -> {
                        if (result.isNewer && hasApkSource) {
                            Button(onClick = {
                                updateCenter.downloadApk(result.apkMirrorUrl, result.apkUrl, result.sha256)
                            }) { Text("下载安装包") }
                        }
                    }
                }
                TextButton(onClick = { openUrl(context, result.releaseUrl, scope, snack) }) {
                    Text("打开发布页")
                }
            }
        }

            Text("忘了没 $currentVersion", style = MaterialTheme.typography.bodyMedium)
            Text("本地优先，无登录、无云同步、无网络后台服务；仅在你手动检查更新和下载时联网，安装始终由你确认。卸载或清除应用数据会删除本机记录，请定期导出备份。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(40.dp))
    }

    pendingImport?.let { (json, preview) ->
        AlertDialog(
            onDismissRequest = { if (!busy) pendingImport = null },
            title = { Text("合并这份备份？") },
            text = {
                Column {
                    Text("文件包含 ${preview.totalLists} 个清单、${preview.totalItems} 条记录。")
                    Text("预计新增 ${preview.newLists} 个清单、${preview.newItems} 条记录，跳过 ${preview.skipped} 项已有数据。")
                    Spacer(Modifier.height(8.dp))
                    Text("已有 ID 保留本机，不会覆盖本机记录。确认后在一个事务中合并。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    backupError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    if (busy) return@TextButton
                    busy = true
                    backupError = null
                    scope.launch {
                        try {
                            val report = withContext(Dispatchers.IO) {
                                val document = BackupCodec.decode(json)
                                val merged = container.backup.merge(document)
                                runCatching { container.widgets.refreshAll() }
                                merged
                            }
                            pendingImport = null
                            importReport = "已新增 ${report.addedLists} 个清单、${report.addedItems} 条记录；跳过 ${report.skipped} 项。"
                            snack.showSnackbar("备份已合并")
                        } catch (e: BackupException) {
                            backupError = e.message
                        } catch (_: Exception) {
                            backupError = "导入失败，数据库未改变"
                        } finally {
                            busy = false
                        }
                    }
                }) { Text(if (busy) "合并中…" else "合并导入") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { pendingImport = null }) { Text("取消") }
            }
        )
    }
}

/** Section header + one 18dp-radius surface group card (development.md §2.3). */
@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(24.dp))
    Text(title, style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(8.dp))
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

private fun packageVersionName(context: Context): String {
    @Suppress("DEPRECATION")
    return context.packageManager.getPackageInfo(context.packageName, 0).versionName
        ?: "未知版本"
}

private fun openUrl(context: Context, url: String?, scope: CoroutineScope, snack: SnackbarHostState) {
    if (url.isNullOrBlank()) return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure {
        scope.launch { snack.showSnackbar("没有可打开链接的应用") }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", bytes / 1024f / 1024f / 1024f)
    bytes >= 1024L * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024f / 1024f)
    bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024f)
    else -> "$bytes B"
}

private fun suggestedBackupName(): String {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
    return "wanglemai-backup-$stamp.json"
}

private suspend fun readBoundedText(context: Context, uri: Uri, maxBytes: Int): String =
    withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("无法打开文件")
        input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) throw BackupException("备份文件超过 5MB")
                output.write(buffer, 0, read)
            }
            String(output.toByteArray(), Charsets.UTF_8)
        }
    }

private suspend fun writeText(context: Context, uri: Uri, text: String) =
    withContext(Dispatchers.IO) {
        val output = context.contentResolver.openOutputStream(uri)
            ?: throw IOException("无法写入文件")
        output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }
