package com.wanglemai.app.ui.lists

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wanglemai.app.domain.ListKind
import com.wanglemai.app.domain.MutationResult
import com.wanglemai.app.domain.TaskList
import com.wanglemai.app.domain.message
import com.wanglemai.app.ui.main.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ManageListsScreen(
    vm: MainViewModel,
    snack: SnackbarHostState,
    scope: CoroutineScope,
    modifier: Modifier,
    onBack: () -> Unit
) {
    val lists by vm.lists.collectAsStateWithLifecycle()
    val counts by vm.listCounts.collectAsStateWithLifecycle()
    val kind by vm.kind.collectAsStateWithLifecycle()
    val current = lists.filter { it.kind == kind }
    val kindWord = if (kind == ListKind.SHOPPING) "待买" else "待办"

    var showNameDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<TaskList?>(null) }
    var nameText by remember { mutableStateOf("") }
    var dialogError by remember { mutableStateOf<String?>(null) }
    var dialogBusy by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<TaskList?>(null) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var deleteBusy by remember { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }

    LaunchedEffect(showNameDialog) {
        if (showNameDialog) {
            delay(120)
            runCatching { nameFocus.requestFocus() }
        }
    }

    Column(modifier.fillMaxSize().padding(horizontal = 20.dp)
        .verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack) { Text("← 返回清单") }
        Text("管理$kindWord 清单", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text("默认清单可以改名，但不能删除。每个小部件只绑定一份清单。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        if (current.isEmpty()) {
            Text("正在读取清单…", Modifier.padding(vertical = 24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        current.forEach { list ->
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(list.name)
                    Text(
                        buildString {
                            if (list.isDefault) append("默认清单 · ")
                            append("${counts[list.id] ?: 0} 条记录")
                            if (list.isDefault) append(" · 不可删除")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = {
                    renameTarget = list
                    nameText = list.name
                    dialogError = null
                    showNameDialog = true
                }) { Text("改名") }
                TextButton(
                    enabled = !list.isDefault,
                    onClick = {
                        deleteTarget = list
                        deleteError = null
                    }
                ) { Text("删除") }
            }
            HorizontalDivider()
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                renameTarget = null
                nameText = ""
                dialogError = null
                showNameDialog = true
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("＋ 新建$kindWord 清单") }
        Spacer(Modifier.height(32.dp))
    }

    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { if (!dialogBusy) showNameDialog = false },
            title = { Text(if (renameTarget == null) "新建$kindWord 清单" else "重命名清单") },
            text = {
                Column {
                    OutlinedTextField(
                        value = nameText,
                        onValueChange = { nameText = it; dialogError = null },
                        singleLine = true,
                        label = { Text("清单名称") },
                        modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
                        enabled = !dialogBusy
                    )
                    dialogError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !dialogBusy, onClick = {
                    if (dialogBusy) return@TextButton
                    dialogBusy = true
                    scope.launch {
                        val target = renameTarget
                        val result = if (target == null) vm.createList(nameText)
                        else vm.renameList(target, nameText)
                        dialogBusy = false
                        if (result is MutationResult.Success) {
                            showNameDialog = false
                            snack.showSnackbar(if (target == null) "清单已创建" else "清单已改名")
                        } else {
                            dialogError = result.message()
                        }
                    }
                }) { Text(if (dialogBusy) "保存中…" else "保存") }
            },
            dismissButton = {
                TextButton(enabled = !dialogBusy, onClick = { showNameDialog = false }) { Text("取消") }
            }
        )
    }

    deleteTarget?.let { target ->
        val defaultName = lists.firstOrNull { it.kind == target.kind && it.isDefault }?.name
            ?: if (target.kind == ListKind.SHOPPING) "日常采购" else "随手待办"
        AlertDialog(
            onDismissRequest = { if (!deleteBusy) deleteTarget = null },
            title = { Text("删除「${target.name}」？") },
            text = {
                Column {
                    Text("其中 ${counts[target.id] ?: 0} 条记录将移到「$defaultName」，关联小部件也会切换。")
                    deleteError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !deleteBusy, onClick = {
                    if (deleteBusy) return@TextButton
                    deleteBusy = true
                    scope.launch {
                        val result = vm.deleteList(target)
                        deleteBusy = false
                        if (result is MutationResult.Success) {
                            deleteTarget = null
                            snack.showSnackbar(result.message())
                        } else {
                            deleteError = result.message()
                        }
                    }
                }) { Text(if (deleteBusy) "删除中…" else "删除并移动") }
            },
            dismissButton = {
                TextButton(enabled = !deleteBusy, onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}
