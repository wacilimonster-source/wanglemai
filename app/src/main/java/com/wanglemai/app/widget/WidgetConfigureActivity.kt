package com.wanglemai.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.room.withTransaction
import com.wanglemai.app.app
import com.wanglemai.app.data.db.WidgetConfig
import com.wanglemai.app.domain.*
import com.wanglemai.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class WidgetConfigureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID || info == null ||
            info.provider?.packageName != packageName
        ) {
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

    private fun returnToDesktop() {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun ConfigureScreen(widgetId: Int, providerClass: String) {
        val palette = LocalEditorialPalette.current
        val container = app.container
        var lists by remember { mutableStateOf<List<TaskList>>(emptyList()) }
        var selectedListId by remember { mutableStateOf("") }
        var theme by remember { mutableStateOf(ThemeMode.SYSTEM) }
        var density by remember { mutableStateOf(WidgetDensity.COMFORTABLE) }
        var loaded by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        BackHandler { if (!busy) returnToDesktop() }

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
                    ?: result.first.firstOrNull { it.isDefault }?.id
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
                            dao.saveConfig(
                                WidgetConfig(
                                    widgetId,
                                    providerClass,
                                    list.id,
                                    theme.name,
                                    density.name,
                                    now
                                )
                            )
                        }
                        container.widgets.refresh(widgetId)
                    }
                    setResult(
                        Activity.RESULT_OK,
                        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    )
                    returnToDesktop()
                } catch (_: Exception) {
                    busy = false
                    error = "配置没有保存成功，请重试"
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = palette.bg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Spacer(Modifier.height(4.dp))

                    // 1. Editorial Page Header
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        EditorialBackButton(
                            text = "← 返回手账",
                            onClick = { if (!busy) returnToDesktop() },
                            modifier = Modifier.semantics { contentDescription = "取消" }
                        )
                        Text(
                            text = "这张卡片，记什么？",
                            style = MaterialTheme.typography.headlineSmall,
                            color = palette.text
                        )
                        Text(
                            text = "每个桌面卡片可独立绑定分册、外观与排版密度。",
                            fontSize = 12.sp,
                            color = palette.muted
                        )
                    }

                    // 2. Framed Wallpaper Preview Stage
                    val previewDark = when (theme) {
                        ThemeMode.SYSTEM -> isSystemInDarkTheme()
                        ThemeMode.LIGHT -> false
                        ThemeMode.DARK -> true
                    }
                    WidgetMaterialPreview(
                        list = lists.firstOrNull { it.id == selectedListId }
                            ?: TaskList("preview", "随手待办"),
                        dark = previewDark,
                        density = density,
                        widgetId = widgetId,
                        large = providerClass == ListLargeProvider::class.java.name
                    )

                    // 3. Stacked Paper Card with Choice Chips
                    StackedPaperCard {
                        // Section A: 绑定分册
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "绑定分册",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.text
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                lists.forEach { list ->
                                    EditorialChoiceChip(
                                        label = list.name,
                                        selected = selectedListId == list.id,
                                        onClick = { if (!busy) selectedListId = list.id }
                                    )
                                }
                            }
                        }

                        HorizontalDivider(thickness = 1.dp, color = palette.line)

                        // Section B: 卡片材质外观
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "卡片材质外观",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.text
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                ThemeMode.entries.forEach { option ->
                                    val label = when (option) {
                                        ThemeMode.SYSTEM -> "跟随系统"
                                        ThemeMode.LIGHT -> "暖米宣纸"
                                        ThemeMode.DARK -> "深焙炭墨"
                                    }
                                    val compatDesc = when (option) {
                                        ThemeMode.SYSTEM -> "跟随系统"
                                        ThemeMode.LIGHT -> "浅色"
                                        ThemeMode.DARK -> "深色"
                                    }
                                    EditorialChoiceChip(
                                        label = label,
                                        selected = theme == option,
                                        onClick = { if (!busy) theme = option },
                                        modifier = Modifier.semantics { contentDescription = compatDesc }
                                    )
                                }
                            }
                        }

                        HorizontalDivider(thickness = 1.dp, color = palette.line)

                        // Section C: 排版密度
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "排版密度",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.text
                            )
                            Text(
                                text = "紧凑与超紧凑将「＋」收进标题栏，释放底栏空间",
                                fontSize = 11.sp,
                                color = palette.muted
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                WidgetDensity.entries.forEach { option ->
                                    val label = when (option) {
                                        WidgetDensity.COMFORTABLE -> "舒适 (56dp)"
                                        WidgetDensity.COMPACT -> "紧凑 (48dp)"
                                        WidgetDensity.ULTRA_COMPACT -> "超紧凑 (36dp)"
                                    }
                                    val compatDesc = when (option) {
                                        WidgetDensity.COMFORTABLE -> "舒适"
                                        WidgetDensity.COMPACT -> "紧凑"
                                        WidgetDensity.ULTRA_COMPACT -> "超紧凑"
                                    }
                                    EditorialChoiceChip(
                                        label = label,
                                        selected = density == option,
                                        onClick = { if (!busy) density = option },
                                        modifier = Modifier.semantics { contentDescription = compatDesc }
                                    )
                                }
                            }
                        }
                    }

                    error?.let {
                        Text(
                            text = it,
                            color = palette.danger,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                }

                // Bottom Save Bar
                Surface(
                    color = palette.bg,
                    border = BorderStroke(1.dp, palette.line)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val btnEnabled = loaded && !busy && selectedListId.isNotEmpty()
                        Surface(
                            onClick = { save() },
                            enabled = btnEnabled,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .shadow(
                                    elevation = if (btnEnabled) 4.dp else 0.dp,
                                    shape = RoundedCornerShape(13.dp),
                                    ambientColor = palette.primary.copy(alpha = 0.4f),
                                    spotColor = palette.primary.copy(alpha = 0.4f)
                                )
                                .semantics { contentDescription = "保存并添加" },
                            shape = RoundedCornerShape(13.dp),
                            color = if (btnEnabled) palette.primary else palette.soft
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (busy) "保存中…" else "保存并置入桌面",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (btnEnabled) palette.onPrimary else palette.faint
                                )
                            }
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
    density: WidgetDensity,
    widgetId: Int,
    large: Boolean
) {
    val palette = LocalEditorialPalette.current
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val sizes = WidgetSizes.forWidget(context, widgetId, large)
    val size = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
        sizes.maxBy { it.width } else sizes.minBy { it.width }
    val layout = remember(size, density, configuration) {
        WidgetLayout.resolve(context, size, density)
    }
    val samples = remember(list.id) {
        val titles = listOf(
            "给阳台的绿植浇水", "把周末的照片洗出来", "取回干洗的外套",
            "周五给妈妈打电话", "给猫补一袋猫粮", "整理书桌上的文件",
            "预约下周的牙齿检查", "把雨伞放进通勤包", "补充厨房纸", "整理周末行程", "寄出明信片", "给耳机充电"
        )
        titles.mapIndexed { index, title ->
            Item(
                "preview-$index", list.id, title, "",
                false, index == 0, index.toLong(), 0, 0, null, 1
            )
        }
    }
    val views = remember(list, dark, layout, samples) {
        WidgetRenderer(context).render(list, samples, samples.size, null, null, dark, layout)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val frameShape = RoundedCornerShape(20.dp)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .clip(frameShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(palette.soft, palette.bgDeep)
                    )
                )
                .border(1.dp, palette.lineStrong, frameShape)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            val scale = minOf(1f, maxWidth.value / size.width, 200f / size.height)
            AndroidView(
                factory = { previewContext ->
                    FrameLayout(previewContext).apply {
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    }
                },
                modifier = Modifier
                    .width((size.width * scale).dp)
                    .height((size.height * scale).dp),
                update = { frame ->
                    frame.removeAllViews()
                    val child = views.apply(context, frame)
                    child.pivotX = 0f
                    child.pivotY = 0f
                    child.scaleX = scale
                    child.scaleY = scale
                    val densityPx = context.resources.displayMetrics.density
                    frame.addView(
                        child,
                        FrameLayout.LayoutParams(
                            (size.width * densityPx).roundToInt(),
                            (size.height * densityPx).roundToInt()
                        )
                    )
                }
            )
        }
        Text(
            text = "SPEC · ${if (large) "4×4 大清单" else "4×2 标准清单"} / 预计显示 ${layout.capacity} 条 (${size.width.toInt()}×${size.height.toInt()}dp)",
            fontFamily = EditorialMono,
            fontSize = 10.sp,
            letterSpacing = 0.5.sp,
            color = palette.faint,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
