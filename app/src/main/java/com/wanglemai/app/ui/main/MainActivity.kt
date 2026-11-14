package com.wanglemai.app.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.wanglemai.app.domain.*
import com.wanglemai.app.ui.editor.QuickEntryActivity
import com.wanglemai.app.ui.lists.ManageListsScreen
import com.wanglemai.app.ui.settings.SettingsScreen
import com.wanglemai.app.ui.theme.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val vm by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            val theme by vm.theme.collectAsStateWithLifecycle()
            WanglemaiTheme(theme) {
                MainScreen(
                    vm = vm,
                    onAdd = { listId ->
                        startActivity(QuickEntryActivity.createIntent(this, listId))
                    },
                    onEdit = { item ->
                        startActivity(QuickEntryActivity.editIntent(this, item.id))
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val id = intent?.getStringExtra("list_id") ?: return
        lifecycleScope.launch { vm.openList(id) }
    }
}

@Composable
private fun MainScreen(
    vm: MainViewModel,
    onAdd: (String) -> Unit,
    onEdit: (Item) -> Unit
) {
    val palette = LocalEditorialPalette.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var managingLists by rememberSaveable { mutableStateOf(false) }
    var inSettings by rememberSaveable { mutableStateOf(false) }
    val lists by vm.lists.collectAsStateWithLifecycle()
    val listCounts by vm.listCounts.collectAsStateWithLifecycle()
    val activeListCounts by vm.activeListCounts.collectAsStateWithLifecycle()
    val listId by vm.listId.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val completed by vm.completed.collectAsStateWithLifecycle()
    val activeCount by vm.activeCount.collectAsStateWithLifecycle()
    val completedCount by vm.completedCount.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    BackHandler(enabled = managingLists || inSettings) {
        when {
            managingLists -> managingLists = false
            inSettings -> inSettings = false
        }
    }

    LaunchedEffect(error) {
        val message = error ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        vm.clearError()
    }

    Scaffold(
        containerColor = palette.bg,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.bg)
                .padding(padding)
        ) {
            when {
                managingLists -> {
                    ManageListsScreen(
                        lists = lists,
                        counts = listCounts,
                        activeCounts = activeListCounts,
                        onBack = { managingLists = false },
                        onCreate = { name -> vm.createList(name) },
                        onRename = { list, name -> vm.renameList(list, name) },
                        onDelete = { list -> vm.deleteList(list) },
                        onMessage = { message -> scope.launch { snackbar.showSnackbar(message) } }
                    )
                }

                inSettings -> {
                    SettingsScreen(
                        vm = vm,
                        snack = snackbar,
                        scope = scope,
                        onBack = { inSettings = false },
                        onManageLists = {
                            inSettings = false
                            managingLists = true
                        }
                    )
                }

                else -> {
                    ListScreen(
                        lists = lists,
                        activeListCounts = activeListCounts,
                        listId = listId,
                        items = items,
                        completed = completed,
                        activeCount = activeCount,
                        completedCount = completedCount,
                        search = search,
                        loading = loading,
                        onSelectList = { id -> scope.launch { vm.selectList(id) } },
                        onManageLists = { managingLists = true },
                        onOpenSettings = { inSettings = true },
                        onSearch = vm::setSearch,
                        onCompleted = vm::setCompletedFilter,
                        onAdd = { onAdd(listId) },
                        onEdit = onEdit,
                        onTogglePin = { item ->
                            scope.launch {
                                val res = vm.togglePin(item)
                                if (res !is MutationResult.Success) {
                                    snackbar.showSnackbar(res.message())
                                }
                            }
                        },
                        onToggle = { item ->
                            scope.launch {
                                when (val result = vm.setCompleted(item)) {
                                    is MutationResult.Success -> {
                                        val undo = result.undo
                                        if (undo != null) {
                                            val action = snackbar.showSnackbar(
                                                message = "已归档",
                                                actionLabel = "撤销",
                                                duration = SnackbarDuration.Short
                                            )
                                            if (action == SnackbarResult.ActionPerformed) {
                                                val undoResult = vm.undo(undo)
                                                if (undoResult !is MutationResult.Success) {
                                                    snackbar.showSnackbar(undoResult.message())
                                                }
                                            }
                                        }
                                    }

                                    else -> snackbar.showSnackbar(result.message())
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ListScreen(
    lists: List<TaskList>,
    activeListCounts: Map<String, Int>,
    listId: String,
    items: List<Item>,
    completed: Boolean,
    activeCount: Int,
    completedCount: Int,
    search: String,
    loading: Boolean,
    onSelectList: (String) -> Unit,
    onManageLists: () -> Unit,
    onOpenSettings: () -> Unit,
    onSearch: (String) -> Unit,
    onCompleted: (Boolean) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Item) -> Unit,
    onTogglePin: (Item) -> Unit,
    onToggle: (Item) -> Unit
) {
    val palette = LocalEditorialPalette.current
    val current = lists.firstOrNull { it.id == listId }
        ?: TaskList(DEFAULT_TODO, "随手待办", true)
    val totalActiveCount = remember(activeListCounts, activeCount, lists) {
        if (activeListCounts.isEmpty()) activeCount else activeListCounts.values.sum()
    }
    val firstTabActive = lists.firstOrNull()?.id == current.id
    val sheetShape = RoundedCornerShape(
        topStart = if (firstTabActive) 0.dp else 18.dp,
        topEnd = 18.dp,
        bottomEnd = 18.dp,
        bottomStart = 18.dp
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
        ) {
            // 1. Editorial Almanac Header
            item(key = "almanac_header") {
                AlmanacHeader(
                    activeCount = activeCount,
                    completedCount = completedCount,
                    onManageLists = onManageLists,
                    onOpenSettings = onOpenSettings
                )
                Spacer(Modifier.height(14.dp))
            }

            // 2. Connected Folder Index Tabs + Stacked Notebook Sheet
            item(key = "notebook_sheet_container") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    IndexTabsBar(
                        lists = lists,
                        currentListId = current.id,
                        activeListCounts = activeListCounts,
                        onSelectList = onSelectList,
                        onManageLists = onManageLists
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .stackedPaperEffect()
                            .shadow(
                                elevation = 4.dp,
                                shape = sheetShape,
                                ambientColor = Color(0x222A231D),
                                spotColor = Color(0x222A231D)
                            ),
                        shape = sheetShape,
                        color = palette.surface,
                        border = BorderStroke(1.dp, palette.lineStrong)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Top Running Header (Ledger filter + Inline search)
                            SheetRunningHeader(
                                completed = completed,
                                activeCount = activeCount,
                                completedCount = completedCount,
                                search = search,
                                onCompleted = onCompleted,
                                onSearch = onSearch
                            )

                            // Paper Rows Area with Continuous Cinnabar Margin Line at 44dp
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 240.dp)
                                    .drawBehind {
                                        val marginX = 44.dp.toPx()
                                        drawLine(
                                            color = palette.marginLine,
                                            start = Offset(marginX, 0f),
                                            end = Offset(marginX, size.height),
                                            strokeWidth = 1.dp.toPx()
                                        )
                                    }
                            ) {
                                when {
                                    loading && items.isEmpty() -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(220.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                color = palette.primary,
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }

                                    items.isEmpty() -> {
                                        NotebookEmptyState(
                                            completed = completed,
                                            searching = search.isNotBlank()
                                        )
                                    }

                                    else -> {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            items.forEachIndexed { index, item ->
                                                NotebookPaperRow(
                                                    index = index + 1,
                                                    item = item,
                                                    onToggle = { onToggle(item) },
                                                    onEdit = { onEdit(item) },
                                                    onTogglePin = { onTogglePin(item) }
                                                )
                                            }

                                            // Ghost Ruled Lines when active items < 6 and not searching
                                            if (!completed && search.isBlank() && items.size < 6) {
                                                GhostRuledRow(
                                                    index = items.size + 1,
                                                    text = "在空白格落笔，再记一件小事…",
                                                    onClick = onAdd
                                                )
                                                GhostRuledRow(
                                                    index = items.size + 2,
                                                    text = "日日是好日 · 宜轻装慢行",
                                                    faint = true,
                                                    onClick = null
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Bottom Sheet Footer
                            SheetFooter(
                                currentListName = current.name,
                                totalActiveCount = totalActiveCount
                            )
                        }
                    }
                }
            }
        }

        // 3. Floating Asymmetric Espresso-Gold Action Island
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            palette.bg.copy(alpha = 0.88f),
                            palette.bg
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            InkActionIsland(
                currentListName = current.name,
                onClick = onAdd
            )
        }
    }
}

@Composable
private fun AlmanacHeader(
    activeCount: Int,
    completedCount: Int,
    onManageLists: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val palette = LocalEditorialPalette.current
    val today = remember { LocalDate.now() }
    val monthAbbr = remember(today) {
        today.month.getDisplayName(JavaTextStyle.SHORT, Locale.ENGLISH).uppercase(Locale.ENGLISH)
    }
    val dayStr = remember(today) { String.format(Locale.US, "%02d", today.dayOfMonth) }
    val weekdayCn = remember(today) {
        when (today.dayOfWeek.value) {
            1 -> "周一"
            2 -> "周二"
            3 -> "周三"
            4 -> "周四"
            5 -> "周五"
            6 -> "周六"
            else -> "周日"
        }
    }
    val subtitle = when {
        activeCount > 0 -> "${padCount(activeCount)} 件小事待办 · ${weekdayCn}宜轻装"
        completedCount > 0 -> "今日事毕 · 心里轻轻"
        else -> "纸页空空 · 随时落笔"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Double-bordered Date Stamp Box
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(palette.surface)
                    .border(1.5.dp, palette.primary.copy(alpha = 0.65f), RoundedCornerShape(11.dp))
                    .padding(2.5.dp)
                    .border(1.dp, palette.line, RoundedCornerShape(8.dp))
                    .padding(vertical = 3.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = monthAbbr,
                        fontFamily = EditorialMono,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = palette.primary,
                        lineHeight = 11.sp
                    )
                    HorizontalDivider(
                        modifier = Modifier
                            .padding(vertical = 1.5.dp)
                            .width(24.dp),
                        thickness = 0.8.dp,
                        color = palette.line
                    )
                    Text(
                        text = dayStr,
                        fontFamily = EditorialSerif,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.text,
                        lineHeight = 19.sp
                    )
                }
            }

            Column {
                Text(
                    text = "慢慢来，一件件。",
                    style = MaterialTheme.typography.headlineSmall,
                    color = palette.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = palette.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Top-Right Tactile Actions: [≡ 目录] + [⚙ 设置]
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 2.dp)
        ) {
            Surface(
                onClick = onManageLists,
                modifier = Modifier
                    .height(36.dp)
                    .semantics { contentDescription = "管理清单" },
                shape = RoundedCornerShape(11.dp),
                color = palette.surface,
                contentColor = palette.text,
                border = BorderStroke(1.dp, palette.lineStrong),
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Canvas(modifier = Modifier.size(13.dp)) {
                        val stroke = 1.5.dp.toPx()
                        val y1 = size.height * 0.22f
                        val y2 = size.height * 0.50f
                        val y3 = size.height * 0.78f
                        drawLine(palette.text, Offset(0f, y1), Offset(size.width, y1), stroke, StrokeCap.Round)
                        drawLine(palette.text, Offset(0f, y2), Offset(size.width * 0.75f, y2), stroke, StrokeCap.Round)
                        drawLine(palette.text, Offset(0f, y3), Offset(size.width * 0.90f, y3), stroke, StrokeCap.Round)
                        drawCircle(palette.primary, radius = 1.6.dp.toPx(), center = Offset(size.width * 0.85f, y2))
                    }
                    Text(
                        text = "目录",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.text
                    )
                }
            }

            Surface(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(36.dp)
                    .semantics { contentDescription = "设置" },
                shape = RoundedCornerShape(11.dp),
                color = palette.surface,
                contentColor = palette.text,
                border = BorderStroke(1.dp, palette.lineStrong),
                shadowElevation = 1.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(15.dp)) {
                        val stroke = 1.4.dp.toPx()
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val rInner = size.minDimension * 0.19f
                        val rMid = size.minDimension * 0.34f
                        val rOuter = size.minDimension * 0.46f
                        drawCircle(
                            color = palette.text,
                            radius = rInner,
                            center = center,
                            style = Stroke(width = stroke)
                        )
                        drawCircle(
                            color = palette.text,
                            radius = rMid,
                            center = center,
                            style = Stroke(width = stroke)
                        )
                        for (i in 0 until 8) {
                            val angle = Math.toRadians(i * 45.0)
                            val cos = kotlin.math.cos(angle).toFloat()
                            val sin = kotlin.math.sin(angle).toFloat()
                            drawLine(
                                color = palette.text,
                                start = Offset(center.x + cos * rMid, center.y + sin * rMid),
                                end = Offset(center.x + cos * rOuter, center.y + sin * rOuter),
                                strokeWidth = 1.8.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IndexTabsBar(
    lists: List<TaskList>,
    currentListId: String,
    activeListCounts: Map<String, Int>,
    onSelectList: (String) -> Unit,
    onManageLists: () -> Unit
) {
    val palette = LocalEditorialPalette.current
    val tabShape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 0.dp, bottomEnd = 0.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(6f)
            .horizontalScroll(rememberScrollState())
            .padding(end = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        lists.forEach { list ->
            val isActive = list.id == currentListId
            val count = activeListCounts[list.id] ?: 0
            Box(
                modifier = Modifier
                    .offset(y = if (isActive) 1.5.dp else 0.dp)
                    .clip(tabShape)
                    .background(if (isActive) palette.surfaceWarm else palette.soft.copy(alpha = 0.88f))
                    .drawBehind {
                        val s = 1.dp.toPx()
                        val r = 12.dp.toPx()
                        // Draw rounded top-left, top, and top-right border
                        drawRoundRect(
                            color = palette.lineStrong,
                            topLeft = Offset(0f, 0f),
                            size = Size(size.width, size.height + r),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                            style = Stroke(width = s)
                        )
                        if (isActive) {
                            // Erase the bottom border line so the active tab fuses seamlessly with the sheet
                            drawRect(
                                color = palette.surfaceWarm,
                                topLeft = Offset(s, size.height - 2.dp.toPx()),
                                size = Size(size.width - s * 2, 3.dp.toPx())
                            )
                        } else {
                            drawLine(
                                color = palette.lineStrong,
                                start = Offset(0f, size.height),
                                end = Offset(size.width, size.height),
                                strokeWidth = s
                            )
                        }
                    }
                    .clickable(role = Role.Tab) { onSelectList(list.id) }
                    .semantics { selected = isActive }
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        top = 7.dp,
                        bottom = if (isActive) 9.dp else 8.dp
                    )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (isActive) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(palette.primary)
                        )
                    }
                    Text(
                        text = list.name,
                        fontSize = 12.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isActive) palette.text else palette.muted,
                        maxLines = 1
                    )
                    Text(
                        text = padCount(count),
                        fontFamily = EditorialMono,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) palette.primary else palette.faint
                    )
                }
            }
        }

        // "+ 新册" Dashed Add Tab
        val addShape = RoundedCornerShape(9.dp)
        Box(
            modifier = Modifier
                .padding(bottom = 3.dp, start = 2.dp)
                .clip(addShape)
                .background(palette.surfaceWarm.copy(alpha = 0.5f))
                .clickable(role = Role.Button, onClick = onManageLists)
                .drawBehind {
                    val r = 9.dp.toPx()
                    drawRoundRect(
                        color = palette.primary.copy(alpha = 0.45f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                        style = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                        )
                    )
                }
                .padding(horizontal = 10.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "＋ 新册",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = palette.primary
            )
        }
    }
}

@Composable
private fun SheetRunningHeader(
    completed: Boolean,
    activeCount: Int,
    completedCount: Int,
    search: String,
    onCompleted: (Boolean) -> Unit,
    onSearch: (String) -> Unit
) {
    val palette = LocalEditorialPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.surfaceWarm)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Ledger Filter Buttons (待办 04 / 已归档 01)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LedgerFilterButton(
                    label = "待办",
                    count = activeCount,
                    selected = !completed,
                    compatDesc = "未完成 $activeCount",
                    onClick = { onCompleted(false) }
                )
                LedgerFilterButton(
                    label = "已归档",
                    count = completedCount,
                    selected = completed,
                    compatDesc = "已完成 $completedCount",
                    onClick = { onCompleted(true) }
                )
            }

            // Right: Compact Inline Search Box
            Surface(
                modifier = Modifier
                    .widthIn(min = 126.dp, max = 168.dp)
                    .height(30.dp),
                shape = RoundedCornerShape(999.dp),
                color = palette.surface,
                border = BorderStroke(1.dp, palette.lineStrong)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "⌕",
                        fontSize = 12.sp,
                        color = palette.faint
                    )
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = search,
                            onValueChange = onSearch,
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 12.sp,
                                color = palette.text
                            ),
                            cursorBrush = SolidColor(palette.primary),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (search.isEmpty()) {
                                        Text(
                                            text = "检索本页…",
                                            fontSize = 12.sp,
                                            color = palette.faint,
                                            maxLines = 1
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = "检索本页…" }
                        )
                    }
                    if (search.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(palette.soft)
                                .clickable { onSearch("") }
                                .semantics { contentDescription = "清空搜索" },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "×",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.muted,
                                lineHeight = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // Double horizontal border under running header
        HorizontalDivider(thickness = 1.dp, color = palette.lineStrong)
        Spacer(Modifier.height(2.dp))
        HorizontalDivider(thickness = 1.dp, color = palette.line)
    }
}

@Composable
private fun LedgerFilterButton(
    label: String,
    count: Int,
    selected: Boolean,
    compatDesc: String,
    onClick: () -> Unit
) {
    val palette = LocalEditorialPalette.current
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier.semantics {
            role = Role.Tab
            this.selected = selected
            contentDescription = compatDesc
        },
        shape = shape,
        color = if (selected) palette.surface else Color.Transparent,
        border = if (selected) BorderStroke(1.dp, palette.lineStrong) else null,
        shadowElevation = if (selected) 1.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) palette.text else palette.muted
            )
            Text(
                text = padCount(count),
                fontFamily = EditorialMono,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) palette.primary else palette.faint
            )
        }
    }
}

@Composable
private fun NotebookPaperRow(
    index: Int,
    item: Item,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onTogglePin: () -> Unit
) {
    val palette = LocalEditorialPalette.current
    val isPinnedActive = item.pinned && !item.completed
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isPinnedActive) {
                    Modifier.background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                palette.primary.copy(alpha = if (palette.isDark) 0.14f else 0.08f),
                                palette.primary.copy(alpha = 0.01f)
                            )
                        )
                    )
                } else {
                    Modifier
                }
            )
            .drawBehind {
                if (isPinnedActive) {
                    drawRect(
                        color = palette.primary,
                        topLeft = Offset(0f, 0f),
                        size = Size(3.dp.toPx(), size.height)
                    )
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .padding(end = 6.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Left of 44dp margin line: Diamond Checkbox
            DiamondCheckbox(
                checked = item.completed,
                onToggle = onToggle,
                contentDesc = if (item.completed) "标为未完成" else "完成"
            )

            // Center of row: Index + Title + Stamp Badges + Note
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onEdit)
                    .semantics {
                        contentDescription = if (item.note.isNotBlank()) {
                            "${item.title}，${item.note}"
                        } else {
                            item.title
                        }
                    }
                    .padding(start = 10.dp, top = 11.dp, bottom = 11.dp, end = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = padCount(index),
                        fontFamily = EditorialMono,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (item.pinned) palette.primary else palette.faint,
                        modifier = Modifier.width(18.dp)
                    )
                    Text(
                        text = item.title,
                        fontSize = 15.sp,
                        fontWeight = if (item.pinned) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (item.completed) palette.muted else palette.text,
                        textDecoration = if (item.completed) TextDecoration.LineThrough else TextDecoration.None,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.pinned) {
                        StampBadge(text = "置顶")
                    }
                    if (item.completed) {
                        StampBadge(text = "已归档", muted = true)
                    }
                }
                if (item.note.isNotBlank()) {
                    Text(
                        text = item.note,
                        fontSize = 12.sp,
                        color = palette.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 24.dp, top = 3.dp)
                    )
                }
            }

            // Right: Direct Pin Toggle Button (Diagonal Pushpin)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clickable(role = Role.Button, onClick = onTogglePin)
                    .semantics {
                        contentDescription = if (item.pinned) "取消置顶" else "置顶"
                    },
                contentAlignment = Alignment.Center
            ) {
                val pinColor = if (item.pinned) palette.primary else palette.faint.copy(alpha = 0.55f)
                Canvas(
                    modifier = Modifier
                        .size(14.dp)
                        .rotate(38f)
                ) {
                    val w = size.width
                    val h = size.height
                    val stroke = 1.35.dp.toPx()
                    // Top cap bar
                    drawLine(
                        color = pinColor,
                        start = Offset(w * 0.25f, h * 0.14f),
                        end = Offset(w * 0.75f, h * 0.14f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                    // Body trapezoid lines
                    drawLine(
                        color = pinColor,
                        start = Offset(w * 0.34f, h * 0.14f),
                        end = Offset(w * 0.24f, h * 0.56f),
                        strokeWidth = stroke
                    )
                    drawLine(
                        color = pinColor,
                        start = Offset(w * 0.66f, h * 0.14f),
                        end = Offset(w * 0.76f, h * 0.56f),
                        strokeWidth = stroke
                    )
                    // Lower flange bar
                    drawLine(
                        color = pinColor,
                        start = Offset(w * 0.18f, h * 0.56f),
                        end = Offset(w * 0.82f, h * 0.56f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                    // Needle point
                    drawLine(
                        color = pinColor,
                        start = Offset(w * 0.50f, h * 0.56f),
                        end = Offset(w * 0.50f, h * 0.92f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        HorizontalDivider(
            thickness = 1.dp,
            color = palette.line.copy(alpha = 0.85f)
        )
    }
}

@Composable
private fun GhostRuledRow(
    index: Int,
    text: String,
    faint: Boolean = false,
    onClick: (() -> Unit)?
) {
    val palette = LocalEditorialPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.width(44.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(2.5.dp)
                        .clip(CircleShape)
                        .background(palette.lineStrong.copy(alpha = if (faint) 0.5f else 0.8f))
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = padCount(index),
                    fontFamily = EditorialMono,
                    fontSize = 11.sp,
                    color = palette.faint.copy(alpha = if (faint) 0.5f else 0.75f),
                    modifier = Modifier.width(18.dp)
                )
                Text(
                    text = text,
                    fontSize = 12.sp,
                    color = palette.faint.copy(alpha = if (faint) 0.6f else 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        DashedHorizontalDivider(color = palette.line.copy(alpha = 0.7f))
    }
}

@Composable
private fun NotebookEmptyState(
    completed: Boolean,
    searching: Boolean
) {
    val palette = LocalEditorialPalette.current
    val (title, subtitle) = when {
        searching -> "没有匹配的记录" to "试试更短的关键词，或清空上方检索框。"
        completed -> "还没有已完成记录" to "勾掉的小事会安静收进这里，随时可查。"
        else -> "纸页空空，随手记一笔" to "点下方「提笔记一笔」，把刚想到的事落到纸上。"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 54.dp, end = 24.dp, top = 40.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Dashed Circular "空" Seal Stamp
        Box(
            modifier = Modifier
                .size(44.dp)
                .drawBehind {
                    drawCircle(
                        color = palette.primary.copy(alpha = 0.45f),
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                        )
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "空",
                fontFamily = EditorialSerif,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = palette.primary.copy(alpha = 0.75f)
            )
        }
        Text(
            text = title,
            fontFamily = EditorialSerif,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = palette.text
        )
        Text(
            text = subtitle,
            fontSize = 12.sp,
            color = palette.muted
        )
    }
}

@Composable
private fun SheetFooter(
    currentListName: String,
    totalActiveCount: Int
) {
    val palette = LocalEditorialPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.surfaceWarm)
    ) {
        HorizontalDivider(thickness = 1.dp, color = palette.line)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "FOLIO · $currentListName",
                fontFamily = EditorialMono,
                fontSize = 10.sp,
                letterSpacing = 0.8.sp,
                color = palette.faint
            )
            Text(
                text = "✦",
                fontSize = 10.sp,
                color = palette.faint
            )
            Text(
                text = "ALL PENDING · ${padCount(totalActiveCount)}",
                fontFamily = EditorialMono,
                fontSize = 10.sp,
                letterSpacing = 0.8.sp,
                color = palette.faint
            )
        }
    }
}

@Composable
private fun InkActionIsland(
    currentListName: String,
    onClick: () -> Unit
) {
    val palette = LocalEditorialPalette.current
    val pillShape = RoundedCornerShape(999.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 10.dp,
                shape = pillShape,
                ambientColor = Color(0x4028211B),
                spotColor = Color(0x4028211B)
            )
            .semantics { contentDescription = "＋ 记一条" },
        shape = pillShape,
        color = palette.inkIsland,
        contentColor = palette.inkIslandText,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(palette.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "＋",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.onPrimary
                    )
                }
                Text(
                    text = "提笔记一笔",
                    fontFamily = EditorialSerif,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.inkIslandText
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (palette.isDark) Color.Black.copy(alpha = 0.12f)
                        else Color.White.copy(alpha = 0.10f)
                    )
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "入册「$currentListName」 ↗",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.inkIslandText.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
