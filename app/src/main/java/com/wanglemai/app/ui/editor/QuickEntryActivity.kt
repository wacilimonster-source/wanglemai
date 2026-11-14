package com.wanglemai.app.ui.editor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wanglemai.app.domain.DEFAULT_TODO
import com.wanglemai.app.domain.TaskList
import com.wanglemai.app.ui.theme.*
import kotlinx.coroutines.delay

private val WarmScrim = Color(0x521C1610)

class QuickEntryActivity : ComponentActivity() {
    private val editorVm: EditorViewModel by viewModels {
        val explicitMode = intent.getStringExtra("mode")
        val mode = if (explicitMode == "edit" || intent.hasExtra("item_id")) EditorMode.EDIT
        else EditorMode.CREATE
        EditorViewModel.Factory(
            application,
            mode,
            intent.getStringExtra("list_id"),
            intent.getStringExtra("item_id")
        )
    }
    private var initialFocusRequested = false

    companion object {
        fun createIntent(context: Context, listId: String, originWidgetId: Int? = null) =
            Intent(context, QuickEntryActivity::class.java)
                .putExtra("mode", "create")
                .putExtra("list_id", listId)
                .apply { originWidgetId?.let { putExtra("origin_widget_id", it) } }

        fun editIntent(context: Context, itemId: String, originWidgetId: Int? = null) =
            Intent(context, QuickEntryActivity::class.java)
                .putExtra("mode", "edit")
                .putExtra("item_id", itemId)
                .apply { originWidgetId?.let { putExtra("origin_widget_id", it) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this) {
            if (editorVm.state.value.busy) return@addCallback
            if (isImeVisible()) hideIme() else closeEditor()
        }
        setContent {
            val state by editorVm.state.collectAsStateWithLifecycle()
            WanglemaiTheme(state.theme) {
                EditorSheet(
                    state = state,
                    vm = editorVm,
                    onClose = ::closeEditor,
                    onFinish = ::finish
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !initialFocusRequested) {
            initialFocusRequested = true
            editorVm.requestFocus(showIme = editorVm.state.value.mode == EditorMode.CREATE)
        }
    }

    override fun onStop() {
        editorVm.flushDraft()
        super.onStop()
    }

    private fun closeEditor() {
        editorVm.flushDraft()
        finish()
    }

    private fun isImeVisible(): Boolean {
        val insets = window.decorView.rootWindowInsets ?: return false
        return WindowInsetsCompat.toWindowInsetsCompat(insets, window.decorView)
            .isVisible(WindowInsetsCompat.Type.ime())
    }

    private fun hideIme() {
        WindowInsetsControllerCompat(window, window.decorView)
            .hide(WindowInsetsCompat.Type.ime())
    }
}

@Composable
private fun EditorSheet(
    state: EditorUiState,
    vm: EditorViewModel,
    onClose: () -> Unit,
    onFinish: () -> Unit
) {
    LaunchedEffect(vm) {
        vm.events.collect { onFinish() }
    }
    val palette = LocalEditorialPalette.current
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var listMenu by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }

    val currentList = state.lists.firstOrNull { it.id == state.listId }
        ?: state.lists.firstOrNull()
        ?: TaskList(state.listId.ifBlank { DEFAULT_TODO }, "随手待办", true)

    LaunchedEffect(state.loaded, state.focusRequest, state.imeRequest, state.itemMissing) {
        if (state.loaded && !state.itemMissing) {
            delay(120)
            runCatching { focusRequester.requestFocus() }
            if (state.imeRequest > 0) {
                keyboard?.show()
                delay(220)
                keyboard?.show()
            } else {
                keyboard?.hide()
                delay(160)
                keyboard?.hide()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmScrim)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { if (!state.busy) onClose() }
        )

        val sheetShape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            shape = sheetShape,
            color = palette.surface,
            border = BorderStroke(1.dp, palette.lineStrong),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Perforated Memo Top Header
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "✦ TEAR-OFF MEMO · DOT GRID",
                            fontFamily = EditorialMono,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp,
                            color = palette.faint
                        )
                        Text(
                            text = if (state.mode == EditorMode.CREATE) "NO. 01" else "EDIT · REV",
                            fontFamily = EditorialMono,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp,
                            color = palette.faint
                        )
                    }
                    DashedHorizontalDivider(
                        color = palette.lineStrong,
                        strokeWidth = 1.5.dp
                    )
                }

                // 2. Sheet Title Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (state.mode == EditorMode.EDIT) "修订这页便签" else "想到，就落笔。",
                        style = MaterialTheme.typography.titleLarge,
                        color = palette.text
                    )
                    Surface(
                        onClick = onClose,
                        enabled = !state.busy,
                        modifier = Modifier.semantics { contentDescription = "取消" },
                        shape = RoundedCornerShape(8.dp),
                        color = palette.surfaceWarm,
                        border = BorderStroke(1.dp, palette.lineStrong)
                    ) {
                        Text(
                            text = "收起",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.text,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                // 3. Banners (Draft Restored / Conflict / Item Missing)
                if (state.draftRestored && !state.conflict && !state.itemMissing) {
                    Surface(
                        color = palette.surfaceWarm,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, palette.lineStrong)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "已恢复上次未保存的草稿",
                                modifier = Modifier.weight(1f),
                                fontSize = 12.sp,
                                color = palette.muted
                            )
                            Text(
                                text = "丢弃草稿",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(enabled = !state.busy, onClick = vm::discardDraft)
                                    .semantics { contentDescription = "丢弃" }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                if (state.conflict) {
                    Surface(
                        color = palette.dangerSoft,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, palette.danger.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "这条记录已在别处更新，你的草稿已保留。",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.danger
                            )
                            Text(
                                text = "请选择载入最新内容，或明确以草稿覆盖最新版本。",
                                fontSize = 12.sp,
                                color = palette.muted
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = vm::reloadLatest) {
                                    Text("载入最新记录", fontWeight = FontWeight.Bold)
                                }
                                TextButton(onClick = vm::keepDraftOverride) {
                                    Text("保留草稿并覆盖", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (state.itemMissing) {
                    Text(
                        text = "记录已被删除，无法保存。",
                        color = palette.danger,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }

                // 4. Dot-Grid Stationery Pad (notepad-sheet)
                val padShape = RoundedCornerShape(14.dp)
                val dotColor = palette.lineStrong.copy(alpha = if (palette.isDark) 0.42f else 0.55f)
                val primaryBarColor = palette.primary
                val inputEnabled = state.loaded && !state.busy && !state.itemMissing
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(padShape)
                        .background(palette.surfaceWarm)
                        .border(1.dp, palette.lineStrong, padShape)
                        .drawBehind {
                            val step = 16.dp.toPx()
                            val dotRadius = 0.9.dp.toPx()
                            var x = step / 2f
                            while (x < size.width) {
                                var y = step / 2f
                                while (y < size.height) {
                                    drawCircle(
                                        color = dotColor,
                                        radius = dotRadius,
                                        center = Offset(x, y)
                                    )
                                    y += step
                                }
                                x += step
                            }
                            drawRect(
                                color = primaryBarColor,
                                topLeft = Offset(0f, 0f),
                                size = Size(3.dp.toPx(), size.height)
                            )
                        }
                        .padding(start = 15.dp, end = 14.dp, top = 12.dp, bottom = 10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val titlePlaceholder = if (state.batch) "每行一条，最多 50 条…" else "准备做什么？"
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = if (state.batch) 84.dp else 44.dp)
                        ) {
                            if (state.title.isEmpty()) {
                                Text(
                                    text = titlePlaceholder,
                                    fontFamily = EditorialSerif,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.faint
                                )
                            }
                            BasicTextField(
                                value = state.title,
                                onValueChange = vm::setTitle,
                                enabled = inputEnabled,
                                singleLine = !state.batch,
                                maxLines = if (state.batch) 10 else 3,
                                textStyle = TextStyle(
                                    fontFamily = EditorialSerif,
                                    fontSize = 18.sp,
                                    lineHeight = 25.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.text
                                ),
                                cursorBrush = SolidColor(palette.primary),
                                keyboardOptions = KeyboardOptions(
                                    imeAction = if (state.batch) ImeAction.Default else ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (!state.batch && state.canSave) {
                                            vm.save(continueAfter = false)
                                        }
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                                    .semantics {
                                        contentDescription = if (state.batch) "每行一条" else "要记的内容"
                                    }
                            )
                        }

                        DashedHorizontalDivider(color = palette.lineStrong)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                        ) {
                            if (state.note.isEmpty()) {
                                Text(
                                    text = "附注或细节（可选）…",
                                    fontSize = 13.sp,
                                    color = palette.faint
                                )
                            }
                            BasicTextField(
                                value = state.note,
                                onValueChange = vm::setNote,
                                enabled = inputEnabled,
                                maxLines = 4,
                                textStyle = TextStyle(
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp,
                                    color = palette.muted
                                ),
                                cursorBrush = SolidColor(palette.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp)
                                    .semantics { contentDescription = "备注（可选）" }
                            )
                            Text(
                                text = "${state.title.length + state.note.length} / 200",
                                fontFamily = EditorialMono,
                                fontSize = 10.sp,
                                color = palette.faint,
                                modifier = Modifier.align(Alignment.BottomEnd)
                            )
                        }
                    }
                }

                // Batch Preview Count / Info / Error
                val lineCount = remember(state.title) {
                    state.title.lines().count { it.trim().isNotEmpty() }
                }
                if (state.batch && lineCount > 0) {
                    Text(
                        text = "将生成 $lineCount 条",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.primary
                    )
                }
                state.info?.let {
                    Text(
                        text = it,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.primary,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
                state.error?.let {
                    Text(
                        text = it,
                        color = palette.danger,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }

                // 5. Compact Meta Pills Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pill 1: List Selector Dropdown
                    Box {
                        MetaPill(
                            text = "📒 ${currentList.name} ▾",
                            active = false,
                            enabled = inputEnabled,
                            contentDesc = "所属清单 ${currentList.name}",
                            onClick = { listMenu = true }
                        )
                        DropdownMenu(
                            expanded = listMenu,
                            onDismissRequest = { listMenu = false }
                        ) {
                            state.lists.forEach { list ->
                                DropdownMenuItem(
                                    text = { Text(list.name) },
                                    onClick = {
                                        listMenu = false
                                        vm.setListId(list.id)
                                    }
                                )
                            }
                        }
                    }

                    // Pill 2: Pin Toggle Pill
                    MetaPill(
                        text = if (state.pinned) "★ 已置顶" else "★ 置顶印记",
                        active = state.pinned,
                        enabled = inputEnabled,
                        contentDesc = "置顶，让它先被看见",
                        onClick = { vm.setPinned(!state.pinned) }
                    )

                    // Pill 3: Batch Toggle Pill (Create mode only)
                    if (state.mode == EditorMode.CREATE) {
                        MetaPill(
                            text = if (state.batch) "☰ 批量 ($lineCount/50条)" else "☰ 按行拆条",
                            active = state.batch,
                            enabled = inputEnabled,
                            contentDesc = "每行生成一条",
                            onClick = { vm.setBatch(!state.batch) }
                        )
                    }
                }

                // 6. Bottom Sheet Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (state.mode == EditorMode.CREATE) {
                        Surface(
                            onClick = { vm.save(continueAfter = true) },
                            enabled = state.canSave,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .semantics { contentDescription = "保存并继续" },
                            shape = RoundedCornerShape(13.dp),
                            color = palette.soft,
                            border = BorderStroke(1.dp, palette.lineStrong)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "入册并继续",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (state.canSave) palette.text else palette.faint
                                )
                            }
                        }
                    } else {
                        Surface(
                            onClick = { deleteDialog = true },
                            enabled = state.canSave,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .semantics { contentDescription = "删除这条记录" },
                            shape = RoundedCornerShape(13.dp),
                            color = palette.dangerSoft,
                            border = BorderStroke(1.dp, palette.danger.copy(alpha = 0.35f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "撕下删除",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.danger
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = { vm.save(continueAfter = false) },
                        enabled = state.canSave,
                        modifier = Modifier
                            .weight(1.3f)
                            .height(46.dp)
                            .shadow(
                                elevation = if (state.canSave) 4.dp else 0.dp,
                                shape = RoundedCornerShape(13.dp),
                                ambientColor = palette.primary.copy(alpha = 0.4f),
                                spotColor = palette.primary.copy(alpha = 0.4f)
                            )
                            .semantics {
                                contentDescription = if (state.mode == EditorMode.EDIT) "保存修改" else "保存"
                            },
                        shape = RoundedCornerShape(13.dp),
                        color = if (state.canSave) palette.primary else palette.soft
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = when {
                                    state.busy -> "保存中…"
                                    state.mode == EditorMode.EDIT -> "保存修订"
                                    else -> "入册保存"
                                },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (state.canSave) palette.onPrimary else palette.faint
                            )
                        }
                    }
                }

                // Helper Footnote
                Text(
                    text = "收起或返回会自动留存本地草稿；正式入册后清除。",
                    fontSize = 11.sp,
                    color = palette.muted,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (deleteDialog) {
        AlertDialog(
            onDismissRequest = { deleteDialog = false },
            containerColor = palette.surface,
            titleContentColor = palette.text,
            textContentColor = palette.muted,
            title = { Text("删除这条记录？", fontFamily = EditorialSerif, fontWeight = FontWeight.Bold) },
            text = { Text("删除后不再显示，暂不提供回收站；这条记录的草稿也会清除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteDialog = false
                        vm.delete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = palette.danger)
                ) { Text("删除", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun MetaPill(
    text: String,
    active: Boolean,
    enabled: Boolean,
    contentDesc: String,
    onClick: () -> Unit
) {
    val palette = LocalEditorialPalette.current
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.semantics {
            role = Role.Button
            contentDescription = contentDesc
        },
        shape = shape,
        color = if (active) palette.primarySoft else palette.soft,
        border = BorderStroke(
            width = if (active) 1.5.dp else 1.dp,
            color = if (active) palette.primary else palette.lineStrong.copy(alpha = 0.65f)
        )
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
            color = if (active) palette.primary else palette.text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}
