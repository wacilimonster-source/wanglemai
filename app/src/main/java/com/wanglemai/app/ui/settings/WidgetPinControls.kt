package com.wanglemai.app.ui.settings

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wanglemai.app.app
import com.wanglemai.app.widget.ListLargeProvider
import com.wanglemai.app.widget.ListSmallProvider
import com.wanglemai.app.widget.WidgetConfigureActivity
import com.wanglemai.app.widget.WidgetPinStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun WidgetPinControls(busy: Boolean, scope: CoroutineScope, snack: SnackbarHostState) {
    val context = LocalContext.current
    val pinning = context.app.container.pinning
    val state by pinning.state.collectAsStateWithLifecycle()
    val xiaomi = remember { listOf(Build.MANUFACTURER, Build.BRAND).any {
        it.lowercase() in setOf("xiaomi", "redmi", "poco")
    } }
    var guideProvider by rememberSaveable { mutableStateOf<String?>(null) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var showManual by rememberSaveable { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { scope.launch { pinning.refresh() } }

    fun add(provider: Class<out AppWidgetProvider>) {
        if (xiaomi) guideProvider = provider.name else pinning.request(provider)
    }
    fun openPermissions() {
        val candidates = listOf(
            Intent("miui.intent.action.APP_PERM_EDITOR").setPackage("com.miui.securitycenter")
                .putExtra("extra_pkgname", context.packageName),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
        )
        val opened = candidates.any { intent -> runCatching { context.startActivity(intent); true }.getOrDefault(false) }
        scope.launch { snack.showSnackbar(if (opened) "请将“桌面快捷方式”或“创建桌面快捷方式”设为允许，返回后继续添加。"
            else "请在系统设置的应用管理中打开“忘了没”，检查其他权限。") }
    }

    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = !busy && state.status != WidgetPinStatus.REQUESTING,
            onClick = { add(ListSmallProvider::class.java) }) { Text("添加标准清单") }
        OutlinedButton(enabled = !busy && state.status != WidgetPinStatus.REQUESTING,
            onClick = { add(ListLargeProvider::class.java) }) { Text("添加大清单") }
    }
    if (xiaomi) {
        Text("小米、Redmi、POCO手机请先允许“创建桌面快捷方式”权限。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (state.status != WidgetPinStatus.IDLE) {
        val message = when (state.status) {
            WidgetPinStatus.REQUESTING -> "已发出请求，请在桌面弹窗中确认添加。"
            WidgetPinStatus.UNCONFIRMED -> "尚未确认添加结果。若没有弹窗，请检查桌面权限后重试。"
            WidgetPinStatus.ADDED -> "已添加到桌面，可继续选择清单、主题和密度。"
            WidgetPinStatus.UNSUPPORTED -> "当前桌面未接受应用内添加，请从系统桌面添加。"
            WidgetPinStatus.ERROR -> "添加请求未能发出，请检查权限或从系统桌面添加。"
            WidgetPinStatus.IDLE -> ""
        }
        Text(message, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyMedium)
        if (state.status == WidgetPinStatus.ADDED) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val id = state.widgetId
                    if (id != null && runCatching { AppWidgetManager.getInstance(context).getAppWidgetInfo(id) }.getOrNull() != null) {
                        context.startActivity(Intent(context, WidgetConfigureActivity::class.java)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                    } else {
                        pinning.dismiss()
                        scope.launch { snack.showSnackbar("暂时无法打开该小部件，请从桌面长按配置或重新添加。") }
                    }
                }) { Text("配置这个小部件") }
                TextButton(onClick = pinning::dismiss) { Text("知道了") }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { showHelp = true }) { Text("添加无响应？") }
        TextButton(onClick = { showManual = true }) { Text("从系统桌面添加") }
    }

    if (guideProvider != null) {
        AlertDialog(onDismissRequest = { guideProvider = null }, title = { Text("先允许添加到桌面") },
            text = { Text("请在“忘了没”的其他权限中，将“桌面快捷方式”或“创建桌面快捷方式”设为允许。\n\n设置完成后返回这里，点击“已开启，继续添加”。") },
            confirmButton = { TextButton(onClick = {
                val provider = if (guideProvider == ListLargeProvider::class.java.name)
                    ListLargeProvider::class.java else ListSmallProvider::class.java
                guideProvider = null
                pinning.request(provider)
            }) { Text("已开启，继续添加") } },
            dismissButton = { TextButton(onClick = ::openPermissions) { Text("去开启权限") } })
    }
    if (showHelp) {
        AlertDialog(onDismissRequest = { showHelp = false }, title = { Text("添加小部件没有反应") },
            text = { Text("小米、Redmi、POCO手机请到系统设置 → 应用管理 → 忘了没 → 其他权限，允许“桌面快捷方式”或“创建桌面快捷方式”。\n\n开启后返回App，再点击添加。桌面没有确认时，也可以使用系统桌面的小部件入口。") },
            confirmButton = { TextButton(onClick = ::openPermissions) { Text("打开权限设置") } },
            dismissButton = { TextButton(onClick = { showHelp = false }) { Text("知道了") } })
    }
    if (showManual) {
        AlertDialog(onDismissRequest = { showManual = false }, title = { Text("从系统桌面添加") },
            text = { Text("长按桌面空白处，进入“小部件”或“添加工具”，找到“忘了没”，选择标准清单或大清单。部分小米桌面需要先进入“Android小部件”入口。") },
            confirmButton = { TextButton(onClick = {
                showManual = false
                context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text("返回桌面") } },
            dismissButton = { TextButton(onClick = { showManual = false }) { Text("知道了") } })
    }
}
