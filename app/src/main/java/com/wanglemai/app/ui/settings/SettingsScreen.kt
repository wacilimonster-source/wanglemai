package com.wanglemai.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wanglemai.app.app
import com.wanglemai.app.data.update.ApkDownloadState
import com.wanglemai.app.domain.*
import com.wanglemai.app.ui.main.MainViewModel
import com.wanglemai.app.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    vm: MainViewModel,
    snack: SnackbarHostState,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onManageLists: (() -> Unit)? = null
) {
    val palette = LocalEditorialPalette.current
    val context = LocalContext.current
    val container = context.app.container
    val theme by vm.theme.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    var backupError by remember { mutableStateOf<String?>(null) }
    var importReport by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<Pair<String, BackupPreview>?>(null) }
    val currentVersion = remember { packageVersionName(context) }

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
                } catch (_: IOException) {
                    backupError = "读取文件失败"
                } catch (_: Exception) {
                    backupError = "导入失败，请重试"
                } finally {
                    busy = false
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.bg)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(8.dp))

        // Editorial Page Header
        if (onBack != null) {
            EditorialBackButton(
                text = "← 返回手账",
                onClick = onBack
            )
            Spacer(Modifier.height(10.dp))
        }
        Text(
            text = "留一点空间。",
            style = MaterialTheme.typography.headlineSmall,
            color = palette.text
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "简单、离线，由你掌握 · v$currentVersion",
            fontSize = 12.sp,
            color = palette.muted
        )

        // Section I: 外观与桌面
        EditorialSettingsSection("I · 外观与桌面") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "纸墨外观",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.text
                )
                Text(
                    text = "白天暖米宣纸，夜间低眩光炭墨。",
                    fontSize = 12.sp,
                    color = palette.muted
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ThemeMode.entries.forEach { mode ->
                        val label = when (mode) {
                            ThemeMode.SYSTEM -> "跟随系统"
                            ThemeMode.LIGHT -> "道林暖白 (日间)"
                            ThemeMode.DARK -> "深烘炭墨 (夜间)"
                        }
                        val compatDesc = when (mode) {
                            ThemeMode.SYSTEM -> "跟随系统"
                            ThemeMode.LIGHT -> "浅色"
                            ThemeMode.DARK -> "深色"
                        }
                        EditorialChoiceChip(
                            label = label,
                            selected = theme == mode,
                            onClick = { vm.setTheme(mode) },
                            modifier = Modifier.semantics { contentDescription = compatDesc }
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = palette.line)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "桌面微缩卡片",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.text
                        )
                        Text(
                            text = "小部件主题可在每个实例的配置中单独选择。",
                            fontSize = 12.sp,
                            color = palette.muted
                        )
                    }
                }
                WidgetPinControls(busy, scope, snack)
            }
        }

        // Section II: 分册与本地数据
        EditorialSettingsSection("II · 分册与本地数据") {
            if (onManageLists != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clickable(onClick = onManageLists)
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "书签分册管理",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.text
                        )
                        Text(
                            text = "新建、重命名或删除手账分册",
                            fontSize = 12.sp,
                            color = palette.muted
                        )
                    }
                    Text(
                        text = "进入 ›",
                        fontSize = EditorialMetrics.ControlTextSize,
                        fontWeight = FontWeight.Bold,
                        color = palette.primary
                    )
                }
                HorizontalDivider(thickness = 1.dp, color = palette.line)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "本地备份与合并恢复",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.text
                )
                Text(
                    text = "导出 UTF-8 JSON；恢复时先预览，只合并新增与较新项，绝不误删本地记录。",
                    fontSize = 12.sp,
                    color = palette.muted,
                    lineHeight = 18.sp
                )
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = palette.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("正在处理文件…", fontSize = 12.sp, color = palette.muted)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EditorialActionButton(
                        text = "↓ 导出 JSON 备份",
                        enabled = !busy,
                        onClick = {
                            backupError = null
                            exportLauncher.launch(suggestedBackupName())
                        },
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = "导出备份" }
                    )
                    EditorialActionButton(
                        text = "↑ 从文件恢复预览",
                        enabled = !busy,
                        onClick = {
                            backupError = null
                            importLauncher.launch(
                                arrayOf(
                                    "application/json",
                                    "text/plain",
                                    "application/octet-stream",
                                    "text/json"
                                )
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = "从文件恢复" }
                    )
                }
                backupError?.let {
                    Text(
                        text = it,
                        color = palette.danger,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
                importReport?.let {
                    Text(
                        text = it,
                        color = palette.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
            }
        }

        // Section III: 关于忘了没
        EditorialSettingsSection("III · 关于忘了没") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "当前版本 $currentVersion",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.text
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(palette.primarySoft)
                            .border(1.dp, palette.primary.copy(alpha = 0.35f), RoundedCornerShape(5.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "LOCAL FIRST",
                            fontFamily = EditorialMono,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.primary
                        )
                    }
                }
                Text(
                    text = "离线优先架构 · 仅在手动点击时检查 GitHub Release 并校验 SHA256。",
                    fontSize = 12.sp,
                    color = palette.muted,
                    lineHeight = 18.sp
                )
                EditorialActionButton(
                    text = if (updateBusy) "检查中…" else "检查更新",
                    enabled = !updateBusy,
                    onClick = { updateCenter.check(currentVersion) },
                    modifier = Modifier.fillMaxWidth()
                )

                updateError?.let {
                    Text(
                        text = it,
                        color = palette.danger,
                        fontSize = 12.sp,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }

                val result = updateResult
                val state = downloadState
                val hasApkSource = result?.let { it.apkMirrorUrl != null || it.apkUrl != null } == true
                result?.let { checked ->
                    Text(
                        text = if (checked.isNewer) "发现新版本：${checked.latestVersion}"
                        else "已是最新版本：${checked.latestVersion}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.text,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                    if (checked.viaAcceleratedSource) {
                        Text(
                            text = "已通过国内加速清单检查",
                            fontSize = 12.sp,
                            color = palette.muted
                        )
                    }
                    if (checked.isNewer) {
                        checked.sha256?.let {
                            Text(
                                text = "SHA256：$it",
                                fontFamily = EditorialMono,
                                fontSize = 11.sp,
                                color = palette.muted
                            )
                        }
                        if (!hasApkSource) {
                            Text(
                                text = "该版本未找到 APK 附件，请到发布页手动下载",
                                fontSize = 12.sp,
                                color = palette.muted
                            )
                        }
                    }
                }

                when (state) {
                    is ApkDownloadState.Downloading -> {
                        if (state.total > 0) {
                            LinearProgressIndicator(
                                progress = { (state.received.toFloat() / state.total).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                                color = palette.primary
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = palette.primary
                            )
                        }
                        Text(
                            text = "已下载 ${formatBytes(state.received)}" +
                                if (state.total > 0) " / ${formatBytes(state.total)}" else "",
                            fontSize = 12.sp,
                            color = palette.muted
                        )
                    }

                    is ApkDownloadState.Failed -> {
                        Text(
                            text = state.message,
                            color = palette.danger,
                            fontSize = 12.sp,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        )
                        state.previous?.takeIf { it.matches(result) }?.let { previous ->
                            Text(
                                text = "${previous.previousLabel()}仍可安装。",
                                fontSize = 12.sp,
                                color = palette.muted
                            )
                        }
                    }

                    is ApkDownloadState.Ready -> {
                        if (state.matches(result)) {
                            Text(
                                text = state.versionPrefix() + if (state.sha256Verified)
                                    "安装包已下载并通过 SHA256 校验"
                                else "安装包已下载（此版本未提供 SHA256，跳过校验）",
                                fontSize = 12.sp,
                                color = palette.muted,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                            )
                        } else {
                            Text(
                                text = "已下载的安装包对应 ${state.version}，与当前最新版本 ${result?.latestVersion.orEmpty()} 不一致，请重新下载。",
                                fontSize = 12.sp,
                                color = palette.danger
                            )
                        }
                    }

                    ApkDownloadState.Idle -> Unit
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (state) {
                        is ApkDownloadState.Downloading -> {
                            OutlinedButton(onClick = { updateCenter.cancelDownload() }) { Text("取消下载") }
                        }

                        is ApkDownloadState.Failed -> {
                            state.previous?.takeIf { it.matches(result) }?.let { previous ->
                                Button(onClick = { installApk(context, previous.file, scope, snack) }) {
                                    Text("安装已下载版本")
                                }
                            }
                            if (result != null && result.isNewer && hasApkSource) {
                                Button(
                                    onClick = {
                                        updateCenter.downloadApk(
                                            result.apkMirrorUrl,
                                            result.apkUrl,
                                            result.sha256,
                                            result.latestVersion
                                        )
                                    }
                                ) { Text("重新下载") }
                            }
                        }

                        is ApkDownloadState.Ready -> {
                            if (state.matches(result)) {
                                Button(onClick = { installApk(context, state.file, scope, snack) }) {
                                    Text("安装")
                                }
                            }
                            if (result != null && hasApkSource) {
                                TextButton(
                                    onClick = {
                                        updateCenter.downloadApk(
                                            result.apkMirrorUrl,
                                            result.apkUrl,
                                            result.sha256,
                                            result.latestVersion
                                        )
                                    }
                                ) { Text("重新下载") }
                            }
                        }

                        ApkDownloadState.Idle -> {
                            if (result != null && result.isNewer && hasApkSource) {
                                Button(
                                    onClick = {
                                        updateCenter.downloadApk(
                                            result.apkMirrorUrl,
                                            result.apkUrl,
                                            result.sha256,
                                            result.latestVersion
                                        )
                                    }
                                ) { Text("下载安装包") }
                            }
                        }
                    }
                    result?.let { checked ->
                        TextButton(onClick = { openUrl(context, checked.releaseUrl, scope, snack) }) {
                            Text("打开发布页")
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(36.dp))
    }

    pendingImport?.let { (json, preview) ->
        AlertDialog(
            onDismissRequest = { if (!busy) pendingImport = null },
            containerColor = palette.surface,
            titleContentColor = palette.text,
            textContentColor = palette.text,
            title = { Text("合并这份备份？", fontFamily = EditorialSerif, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("文件包含 ${preview.totalLists} 个清单、${preview.totalItems} 条记录。")
                    Text("预计新增 ${preview.newLists} 个清单、${preview.newItems} 条记录，跳过 ${preview.skipped} 项已有数据。")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "已有 ID 保留本机，不会覆盖本机记录。确认后在一个事务中合并。",
                        fontSize = 12.sp,
                        color = palette.muted
                    )
                    backupError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = it,
                            color = palette.danger,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
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
                    }
                ) { Text(if (busy) "合并中…" else "合并导入", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { pendingImport = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun EditorialSettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalEditorialPalette.current
    Spacer(Modifier.height(16.dp))
    Text(
        text = title,
        fontFamily = EditorialMono,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = palette.primary,
        modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
    )
    StackedPaperCard(content = content)
}

private fun packageVersionName(context: Context): String {
    @Suppress("DEPRECATION")
    return context.packageManager.getPackageInfo(context.packageName, 0).versionName
        ?: "未知版本"
}

private fun ApkDownloadState.Ready.matches(result: UpdateCheckResult?): Boolean =
    result == null || version.isEmpty() || version == result.latestVersion

private fun ApkDownloadState.Ready.versionPrefix(): String =
    if (version.isEmpty()) "" else "$version "

private fun ApkDownloadState.Ready.previousLabel(): String =
    if (version.isEmpty()) "之前下载的安装包" else "之前下载的 $version 安装包"

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
            String(output.toByteArray(), Charsets.UTF_8).removePrefix("\uFEFF")
        }
    }

private suspend fun writeText(context: Context, uri: Uri, text: String) =
    withContext(Dispatchers.IO) {
        val output = (runCatching { context.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: context.contentResolver.openOutputStream(uri))
            ?: throw IOException("无法写入文件")
        output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }
