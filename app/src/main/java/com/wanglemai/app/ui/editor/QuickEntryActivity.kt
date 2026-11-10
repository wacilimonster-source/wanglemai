package com.wanglemai.app.ui.editor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wanglemai.app.domain.ListKind
import com.wanglemai.app.domain.ThemeMode
import com.wanglemai.app.ui.theme.WanglemaiTheme
import kotlinx.coroutines.delay

/** 28% warm-brown scrim behind the editor sheet (development.md §2.2). */
private val WarmScrim = Color(0xFF383129).copy(alpha = 0.28f)

/** Warm input treatment: background fill, 1dp outlineVariant border, primary 2dp when focused. */
@Composable
private fun warmFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = MaterialTheme.colorScheme.background,
    focusedContainerColor = MaterialTheme.colorScheme.background,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
)

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
            // Let the theme own system-bar icon contrast: the sheet is light in light mode,
            // so the previous unconditional white icons were unreadable.
            WanglemaiTheme(state.theme) {
                EditorSheet(state, editorVm, onClose = ::closeEditor, onFinish = ::finish)
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
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var listMenu by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.loaded, state.focusRequest, state.imeRequest, state.itemMissing) {
        if (state.loaded && !state.itemMissing) {
            delay(120)
            runCatching { focusRequester.requestFocus() }
            // Create and save-and-continue need the keyboard immediately; edit/read mode
            // should show the full title and note instead of covering them.
            if (state.imeRequest > 0) {
                keyboard?.show()
                delay(220)
                keyboard?.show()
            } else {
                // Keep the title focused for hardware keyboards, but do not let the
                // platform's focus-triggered IME cover an existing record.
                keyboard?.hide()
                delay(160)
                keyboard?.hide()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(WarmScrim)) {
        Box(
            Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { if (!state.busy) onClose() })
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.94f),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(width = 36.dp, height = 4.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
                            RoundedCornerShape(2.dp)))
                }
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (state.mode == EditorMode.EDIT) "编辑这一条" else "想到，就记一条。",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onClose, enabled = !state.busy) { Text("取消") }
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (state.draftRestored && !state.conflict && !state.itemMissing) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(14.dp)) {
                            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("已恢复上次草稿", Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = vm::discardDraft) { Text("丢弃") }
                            }
                        }
                    }
                    if (state.conflict) {
                        Surface(color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(14.dp)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text("这条记录已在别处更新，你的草稿已保留。",
                                    style = MaterialTheme.typography.bodyMedium)
                                Text("请选择载入最新内容，或明确以草稿覆盖最新版本。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer)
                                Row {
                                    TextButton(onClick = vm::reloadLatest) { Text("载入最新记录") }
                                    TextButton(onClick = vm::keepDraftOverride) { Text("保留草稿并覆盖") }
                                }
                            }
                        }
                    }
                    if (state.itemMissing) {
                        Text("记录已被删除，无法保存。", color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }

                    OutlinedTextField(
                        value = state.title,
                        onValueChange = vm::setTitle,
                        label = { Text(if (state.batch) "每行一条" else "要记的内容") },
                        placeholder = {
                            Text(if (state.batch) "换行分隔，最多 50 条" else "有什么想记下来的？")
                        },
                        singleLine = !state.batch,
                        minLines = if (state.batch) 4 else 1,
                        maxLines = if (state.batch) 10 else 1,
                        enabled = state.loaded && !state.busy && !state.itemMissing,
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        textStyle = TextStyle(fontSize = 20.sp, lineHeight = 30.sp),
                        colors = warmFieldColors(),
                        keyboardOptions = KeyboardOptions(
                            imeAction = if (state.batch) ImeAction.Default else ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { vm.save() })
                    )
                    if (state.mode == EditorMode.CREATE) {
                        val batchLines = remember(state.title) {
                            Regex("\\r\\n|\\n|\\r").split(state.title)
                                .count { it.trim().isNotEmpty() }
                        }
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(
                            enabled = !state.busy
                        ) { vm.setBatch(!state.batch) },
                            verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = state.batch, onCheckedChange = vm::setBatch,
                                enabled = !state.busy)
                            Spacer(Modifier.width(8.dp))
                            Text("每行生成一条", style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f))
                            if (state.batch) {
                                Text(
                                    if (batchLines == 0) "最多 50 条" else "将生成 $batchLines 条",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            if (state.batch) "每行一条，超过 50 条不会保存"
                            else "单条最多 200 字；备注最多 2000 字",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text("放进哪份清单", style = MaterialTheme.typography.labelLarge)
                    Box {
                        Button(
                            onClick = { listMenu = true },
                            enabled = state.loaded && !state.busy && !state.itemMissing,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 0.dp, pressedElevation = 0.dp
                            ),
                        ) {
                            val target = state.lists.firstOrNull { it.id == state.listId }
                            Text(
                                (if (target?.kind == ListKind.SHOPPING) "待买 · " else "待办 · ") +
                                    (target?.name ?: "选择清单") + "  ▾",
                                Modifier.weight(1f)
                            )
                        }
                        DropdownMenu(expanded = listMenu, onDismissRequest = { listMenu = false }) {
                            state.lists.forEach { list ->
                                DropdownMenuItem(
                                    text = {
                                        Text((if (list.kind == ListKind.SHOPPING) "待买 · " else "待办 · ") + list.name)
                                    },
                                    onClick = {
                                        listMenu = false
                                        vm.setListId(list.id)
                                    })
                            }
                        }
                    }

                    if (state.targetKind == ListKind.SHOPPING && !state.batch) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = state.quantity,
                                onValueChange = vm::setQuantity,
                                label = { Text("数量（可选）") },
                                singleLine = true,
                                enabled = !state.busy && !state.itemMissing,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = warmFieldColors(),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = state.unit,
                                onValueChange = vm::setUnit,
                                label = { Text("单位") },
                                singleLine = true,
                                enabled = !state.busy && !state.itemMissing,
                                colors = warmFieldColors(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = state.note,
                        onValueChange = vm::setNote,
                        label = { Text("备注（可选）") },
                        enabled = !state.busy && !state.itemMissing,
                        minLines = 2,
                        maxLines = 5,
                        colors = warmFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(
                            enabled = !state.busy && !state.itemMissing
                        ) { vm.setPinned(!state.pinned) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = state.pinned,
                            onCheckedChange = if (state.busy || state.itemMissing) null
                            else { checked -> vm.setPinned(checked) }
                        )
                        Text("置顶，让它先被看见")
                    }

                    state.info?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                    if (state.mode == EditorMode.EDIT) {
                        TextButton(
                            onClick = { deleteDialog = true },
                            enabled = state.canSave,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("删除这条记录", color = MaterialTheme.colorScheme.error) }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(
                        Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        state.error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                            Spacer(Modifier.height(6.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (state.mode == EditorMode.CREATE) {
                                OutlinedButton(
                                    onClick = { vm.save(continueAfter = true) },
                                    enabled = state.canSave,
                                    modifier = Modifier.weight(1f)
                                ) { Text("保存并继续") }
                            }
                            Button(
                                onClick = { vm.save(continueAfter = false) },
                                enabled = state.canSave,
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Text(when {
                                    state.busy -> "保存中…"
                                    state.mode == EditorMode.EDIT -> "保存修改"
                                    else -> "保存"
                                })
                            }
                        }
                        Text("取消或返回会保留草稿；保存成功后才清除。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }

    if (state.crossTypeConfirm) {
        AlertDialog(
            onDismissRequest = vm::cancelCrossTypeMove,
            title = { Text("移到待办清单？") },
            text = { Text("移动后将清除数量和单位，标题和备注保留。") },
            confirmButton = {
                TextButton(onClick = vm::confirmCrossTypeMove) { Text("移动并保存") }
            },
            dismissButton = {
                TextButton(onClick = vm::cancelCrossTypeMove) { Text("取消") }
            }
        )
    }

    if (deleteDialog) {
        AlertDialog(
            onDismissRequest = { deleteDialog = false },
            title = { Text("删除这条记录？") },
            text = { Text("删除后不再显示，暂不提供回收站；这条记录的草稿也会清除。") },
            confirmButton = {
                TextButton(onClick = {
                    deleteDialog = false
                    vm.delete()
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialog = false }) { Text("取消") }
            }
        )
    }
}
