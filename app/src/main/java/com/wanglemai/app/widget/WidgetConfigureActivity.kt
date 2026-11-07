package com.wanglemai.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.withTransaction
import com.wanglemai.app.R
import com.wanglemai.app.app
import com.wanglemai.app.data.db.WidgetConfig
import com.wanglemai.app.domain.*
import com.wanglemai.app.ui.theme.WanglemaiTheme
import com.wanglemai.app.ui.theme.rememberAppThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WidgetConfigureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID)
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID || info == null ||
            info.provider?.packageName != packageName) {
            finish()
            return
        }
        enableEdgeToEdge()
        setContent {
            val appTheme = rememberAppThemeMode(app.container)
            WanglemaiTheme(appTheme) {
                ConfigureScreen(widgetId, info.provider.className)
            }
        }
    }

    @Composable
    private fun ConfigureScreen(widgetId: Int, providerClass: String) {
        val container = app.container
        var lists by remember { mutableStateOf<List<TaskList>>(emptyList()) }
        var selectedListId by remember { mutableStateOf("") }
        var theme by remember { mutableStateOf(ThemeMode.SYSTEM) }
        var density by remember { mutableStateOf(WidgetDensity.COMFORTABLE) }
        var loaded by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(widgetId) {
            try {
                val result = withContext(Dispatchers.IO) {
                    container.repository.initialize()
                    val all = container.repository.lists()
                    val config = container.db.dao().config(widgetId)
                    Triple(all, config, config?.listId)
                }
                lists = result.first
                val config = result.second
                selectedListId = result.third?.takeIf { id -> result.first.any { it.id == id } }
                    ?: result.first.firstOrNull { it.isDefault && it.kind == ListKind.TODO }?.id
                    ?: result.first.firstOrNull()?.id.orEmpty()
                if (config != null) {
                    theme = themeModeOf(config.theme)
                    density = widgetDensityOf(config.density)
                }
                loaded = true
            } catch (_: Exception) {
                error = "暂时无法读取清单，请返回后重试"
            }
        }

        fun save() {
            if (busy || !loaded || selectedListId.isEmpty()) return
            busy = true
            error = null
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val list = container.repository.list(selectedListId)
                            ?: throw IllegalStateException("清单已不存在")
                        val now = System.currentTimeMillis()
                        container.db.withTransaction {
                            val dao = container.db.dao()
                            dao.clearWidgetUndo(widgetId)
                            dao.saveConfig(WidgetConfig(widgetId, providerClass, list.id,
                                theme.name, density.name, now))
                        }
                        container.widgets.refresh(widgetId)
                    }
                    setResult(Activity.RESULT_OK,
                        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                    finish()
                } catch (_: Exception) {
                    busy = false
                    error = "配置没有保存成功，请重试"
                }
            }
        }

        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)) {
                    Spacer(Modifier.height(16.dp))
                    Text("这张卡片，记什么？", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(6.dp))
                    Text("每个小部件可以独立选择清单、主题和密度。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(20.dp))
                    val previewDark = when (theme) {
                        ThemeMode.SYSTEM -> isSystemInDarkTheme()
                        ThemeMode.LIGHT -> false
                        ThemeMode.DARK -> true
                    }
                    WidgetMaterialPreview(
                        listName = lists.firstOrNull { it.id == selectedListId }?.name ?: "清单",
                        dark = previewDark,
                        compact = density == WidgetDensity.COMPACT,
                    )
                    Spacer(Modifier.height(20.dp))
                    Text("选择清单", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    lists.forEach { list ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                .clickable(enabled = !busy) { selectedListId = list.id },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedListId == list.id,
                                onClick = { if (!busy) selectedListId = list.id })
                            Column(Modifier.weight(1f)) {
                                Text(list.name)
                                Text(
                                    (if (list.kind == ListKind.SHOPPING) "待买" else "待办") +
                                        if (list.isDefault) " · 默认清单" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Text("小部件主题", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { option ->
                            FilterChip(selected = theme == option, enabled = !busy,
                                onClick = { theme = option },
                                label = {
                                    Text(when (option) {
                                        ThemeMode.SYSTEM -> "跟随系统"
                                        ThemeMode.LIGHT -> "浅色"
                                        ThemeMode.DARK -> "深色"
                                    })
                                })
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("显示密度", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WidgetDensity.entries.forEach { option ->
                            FilterChip(selected = density == option, enabled = !busy,
                                onClick = { density = option },
                                label = {
                                    Text(if (option == WidgetDensity.COMPACT) "紧凑" else "舒适")
                                })
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("实际显示行数按桌面分配的空间和系统字体缩放调整，至少保留 1 行。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    error?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                    Spacer(Modifier.height(24.dp))
                }
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                        Button(onClick = { save() }, enabled = loaded && !busy && selectedListId.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium) {
                            Text(if (busy) "保存中…" else "保存并添加")
                        }
                        TextButton(onClick = { finish() }, enabled = !busy,
                            modifier = Modifier.fillMaxWidth()) {
                            Text("取消")
                        }
                    }
                }
            }
        }
    }
}

/** Static material preview of the widget glass, tracking the selected theme and density. */
@Composable
private fun WidgetMaterialPreview(listName: String, dark: Boolean, compact: Boolean) {
    val text = colorResource(if (dark) R.color.widget_dark_text else R.color.widget_light_text)
    val secondary = colorResource(if (dark) R.color.widget_dark_secondary else R.color.widget_light_secondary)
    val primary = colorResource(if (dark) R.color.widget_dark_primary else R.color.widget_light_primary)
    val ring = painterResource(if (dark) R.drawable.widget_check_ring_dark else R.drawable.widget_check_ring_light)
    val fill = Color(if (dark) 0xF0312920 else 0xEBFFF8EA)
    val stroke = Color(if (dark) 0x30FFE9C9 else 0xC2FFFFFF)
    val glass = RoundedCornerShape(24.dp)
    val rowHeight = if (compact) 48.dp else 52.dp
    Column(
        Modifier
            .fillMaxWidth()
            .clip(glass)
            .background(fill)
            .border(1.dp, stroke, glass)
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(listName, color = text, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("4 项", color = secondary, fontSize = 12.sp)
        }
        listOf("给阳台的绿植浇水", "把周末的照片洗出来").forEach { sample ->
            Row(Modifier.fillMaxWidth().heightIn(min = rowHeight), verticalAlignment = Alignment.CenterVertically) {
                Icon(ring, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                Text(sample, color = text, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("＋ 记一条", color = primary, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("查看全部 ›", color = primary, fontSize = 12.sp)
        }
    }
}
