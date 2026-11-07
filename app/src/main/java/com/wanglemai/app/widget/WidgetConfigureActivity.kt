package com.wanglemai.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.room.withTransaction
import com.wanglemai.app.app
import com.wanglemai.app.data.db.WidgetConfig
import com.wanglemai.app.domain.*
import com.wanglemai.app.ui.theme.WanglemaiTheme
import com.wanglemai.app.ui.theme.rememberAppThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

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
                        list = lists.firstOrNull { it.id == selectedListId }
                            ?: TaskList("preview", "清单", ListKind.TODO),
                        dark = previewDark,
                        compact = density == WidgetDensity.COMPACT,
                        widgetId = widgetId,
                        large = providerClass == ListLargeProvider::class.java.name
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
                    Text("显示条数随桌面尺寸和系统字号调整。",
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

/** Render the same RemoteViews at the host's size, scaled only to fit the preview stage. */
@Composable
private fun WidgetMaterialPreview(
    list: TaskList,
    dark: Boolean,
    compact: Boolean,
    widgetId: Int,
    large: Boolean
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val sizes = WidgetSizes.forWidget(context, widgetId, large)
    val size = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
        sizes.maxBy { it.width } else sizes.minBy { it.width }
    val layout = remember(size, compact, configuration) {
        WidgetLayout.resolve(context, size, compact)
    }
    val samples = remember(list.id, list.kind) {
        val titles = listOf("给阳台的绿植浇水", "把周末的照片洗出来", "取回干洗的外套",
            "周五给妈妈打电话", "给猫补一袋猫粮", "整理书桌上的文件",
            "预约下周的牙齿检查", "把雨伞放进通勤包", "补充厨房纸", "整理周末行程", "寄出明信片", "给耳机充电")
        titles.mapIndexed { index, title ->
            Item("preview-$index", list.id, title, "",
                if (list.kind == ListKind.SHOPPING) "1" else null,
                if (list.kind == ListKind.SHOPPING) "件" else null,
                false, index == 0, index.toLong(), 0, 0, null, 1)
        }
    }
    val views = remember(list, dark, layout, samples) {
        WidgetRenderer(context).render(list, samples, samples.size, null, null, dark, layout)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (large) "大清单" else "标准清单",
            style = MaterialTheme.typography.labelMedium)
        BoxWithConstraints(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center) {
            val scale = minOf(1f, maxWidth.value / size.width, 220f / size.height)
            AndroidView(
                factory = { previewContext ->
                    FrameLayout(previewContext).apply {
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    }
                },
                modifier = Modifier.width((size.width * scale).dp).height((size.height * scale).dp),
                update = { frame ->
                    frame.removeAllViews()
                    val child = views.apply(context, frame)
                    child.pivotX = 0f
                    child.pivotY = 0f
                    child.scaleX = scale
                    child.scaleY = scale
                    val density = context.resources.displayMetrics.density
                    frame.addView(child, FrameLayout.LayoutParams(
                        (size.width * density).roundToInt(),
                        (size.height * density).roundToInt()))
                }
            )
        }
        Text("此尺寸预计显示 ${layout.capacity} 条 · ${size.width.toInt()} × ${size.height.toInt()}dp",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
