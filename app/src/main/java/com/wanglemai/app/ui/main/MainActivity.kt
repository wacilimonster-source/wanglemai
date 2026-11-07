package com.wanglemai.app.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wanglemai.app.R
import com.wanglemai.app.app
import com.wanglemai.app.data.update.UpdateEvent
import com.wanglemai.app.domain.*
import com.wanglemai.app.ui.editor.QuickEntryActivity
import com.wanglemai.app.ui.lists.ManageListsScreen
import com.wanglemai.app.ui.settings.SettingsScreen
import com.wanglemai.app.ui.settings.installApk
import com.wanglemai.app.ui.theme.WanglemaiTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val requestedList = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedList.value = intent.getStringExtra("list_id")
        setContent {
            val vm: MainViewModel = viewModel()
            val mode by vm.theme.collectAsStateWithLifecycle()
            WanglemaiTheme(mode) {
                MainRoot(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedList.value = intent.getStringExtra("list_id")
    }

    @Composable
    private fun MainRoot(vm: MainViewModel) {
        var screen by rememberSaveable { mutableStateOf(MainScreen.LIST) }
        val snack = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val container = remember { this@MainActivity.app.container }
        val kind by vm.kind.collectAsStateWithLifecycle()
        val listId by vm.listId.collectAsStateWithLifecycle()
        val requested by requestedList.collectAsStateWithLifecycle()

        LaunchedEffect(requested) {
            val id = requested ?: return@LaunchedEffect
            requestedList.value = null
            screen = MainScreen.LIST
            vm.openList(id)
        }
        // Downloads finish on the application scope; announce them from the app-wide host so the
        // user learns about completion even when another tab (or the installer) is in front.
        LaunchedEffect(container.updates) {
            container.updates.events.collect { event ->
                if (event is UpdateEvent.Downloaded) {
                    val result = snack.showSnackbar(
                        if (event.sha256Verified) "更新包已下载并通过 SHA256 校验" else "更新包已下载",
                        actionLabel = "安装",
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        installApk(this@MainActivity, event.file, scope, snack)
                    }
                }
            }
        }
        BackHandler(enabled = screen != MainScreen.LIST) {
            screen = MainScreen.LIST
        }
        Scaffold(
            snackbarHost = { SnackbarHost(snack) },
            bottomBar = {
                Column {
                    if (screen == MainScreen.LIST) {
                        Button(
                            onClick = {
                                startActivity(QuickEntryActivity.createIntent(this@MainActivity, listId))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            shape = MaterialTheme.shapes.medium,
                        ) { Text("＋ 记一条") }
                    }
                    NavigationBar {
                        val navColors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        NavigationBarItem(
                            selected = screen != MainScreen.SETTINGS && kind == ListKind.TODO,
                            onClick = { screen = MainScreen.LIST; vm.selectKind(ListKind.TODO) },
                            icon = { Icon(painterResource(R.drawable.ic_nav_todo), contentDescription = null) },
                            label = { Text("待办") },
                            colors = navColors)
                        NavigationBarItem(
                            selected = screen != MainScreen.SETTINGS && kind == ListKind.SHOPPING,
                            onClick = { screen = MainScreen.LIST; vm.selectKind(ListKind.SHOPPING) },
                            icon = { Icon(painterResource(R.drawable.ic_nav_shop), contentDescription = null) },
                            label = { Text("待买") },
                            colors = navColors)
                        NavigationBarItem(
                            selected = screen == MainScreen.SETTINGS,
                            onClick = { screen = MainScreen.SETTINGS },
                            icon = { Icon(painterResource(R.drawable.ic_nav_settings), contentDescription = null) },
                            label = { Text("设置") },
                            colors = navColors)
                    }
                }
            },
        ) { padding ->
            when (screen) {
                MainScreen.LIST -> ListScreen(vm, snack, scope,
                    Modifier.padding(padding), onManage = { screen = MainScreen.MANAGE })
                MainScreen.MANAGE -> ManageListsScreen(vm, snack, scope,
                    Modifier.padding(padding), onBack = { screen = MainScreen.LIST })
                MainScreen.SETTINGS -> SettingsScreen(vm, snack, scope, Modifier.padding(padding))
            }
        }
    }

    @Composable
    private fun ListScreen(
        vm: MainViewModel,
        snack: SnackbarHostState,
        scope: kotlinx.coroutines.CoroutineScope,
        modifier: Modifier,
        onManage: () -> Unit
    ) {
        val lists by vm.lists.collectAsStateWithLifecycle()
        val kind by vm.kind.collectAsStateWithLifecycle()
        val listId by vm.listId.collectAsStateWithLifecycle()
        val items by vm.items.collectAsStateWithLifecycle()
        val completed by vm.completed.collectAsStateWithLifecycle()
        val query by vm.search.collectAsStateWithLifecycle()
        val loading by vm.loading.collectAsStateWithLifecycle()
        val error by vm.error.collectAsStateWithLifecycle()
        val activeCount by vm.activeCount.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val shopping = kind == ListKind.SHOPPING
        val doneWord = if (shopping) "购买" else "完成"
        val currentList = lists.firstOrNull { it.id == listId }
        val listsOfKind = lists.filter { it.kind == kind }
        var pickerOpen by remember { mutableStateOf(false) }
        val busyItems = remember { mutableStateOf<Set<String>>(emptySet()) }

        suspend fun report(result: MutationResult, done: Boolean) {
            if (result is MutationResult.Success) {
                val token = result.undo
                if (token != null) {
                    val message = if (done) "已$doneWord" else "已恢复"
                    val action = snack.showUndo(message)
                    if (action == SnackbarResult.ActionPerformed) {
                        val undo = vm.undo(token)
                        snack.showSnackbar(if (undo is MutationResult.Success) "已撤销" else undo.message())
                    }
                }
            } else {
                snack.showSnackbar(result.message())
            }
        }

        Column(modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(8.dp))
            Text("忘了没 / ${if (shopping) "待买" else "待办"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (shopping) "想买的，都在这。" else "慢慢来，一件件。",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = onManage) { Text("管理清单") }
            }
            Text(
                if (activeCount > 0) "${activeCount} 件小事，等你完成" else "清单空空，心里轻轻。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Box {
                Button(
                    onClick = { pickerOpen = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    Text("${currentList?.name ?: if (shopping) "日常采购" else "随手待办"}  ▾")
                }
                DropdownMenu(expanded = pickerOpen, onDismissRequest = { pickerOpen = false }) {
                    listsOfKind.forEach { list ->
                        DropdownMenuItem(
                            text = { Text(list.name + if (list.isDefault) "（默认）" else "") },
                            onClick = {
                                pickerOpen = false
                                scope.launch { vm.selectList(list.id) }
                            })
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = vm::setSearch,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                placeholder = { Text("搜索这份清单…") },
                leadingIcon = {
                    Icon(painterResource(R.drawable.ic_search), contentDescription = null,
                        modifier = Modifier.size(20.dp))
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                ),
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .selectableGroup(),
                ) {
                    FilterSegment(selected = !completed, label = "未$doneWord",
                        onClick = { vm.setCompletedFilter(false) }, modifier = Modifier.weight(1f))
                    FilterSegment(selected = completed, label = "已$doneWord",
                        onClick = { vm.setCompletedFilter(true) }, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    if (shopping) "$activeCount 件待买" else "$activeCount 项待办",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            error?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                when {
                    loading && items.isEmpty() -> {
                        Text("正在读取…", Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items.isEmpty() -> {
                        Text(
                            when {
                                query.isNotEmpty() -> "没有找到相关记录"
                                completed -> "这里还没有已${doneWord}的记录"
                                else -> "还没有记录，点下方记一条。"
                            },
                            Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
                            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                                val quantity = listOfNotNull(item.quantityDecimal, item.unit).joinToString(" ")
                                val hasSubline = item.note.isNotBlank() || quantity.isNotEmpty()
                                val actionDescription = when {
                                    item.completed -> "恢复『${item.title}』为未$doneWord"
                                    shopping -> "标记『${item.title}』为已购买"
                                    else -> "标记『${item.title}』为已完成"
                                }
                                val titleDescription = buildString {
                                    append(item.title)
                                    if (item.pinned) append("，已置顶")
                                    if (item.note.isNotBlank()) append("，备注：${item.note}")
                                    if (quantity.isNotEmpty()) append("，数量 $quantity")
                                    if (item.completed) append("，已$doneWord")
                                }
                                Row(
                                    Modifier.fillMaxWidth()
                                        .heightIn(min = if (hasSubline) 68.dp else 56.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .clickable(enabled = item.id !in busyItems.value) {
                                                busyItems.value = busyItems.value + item.id
                                                scope.launch {
                                                    try {
                                                        val result = vm.setCompleted(item)
                                                        report(result, !item.completed)
                                                    } finally {
                                                        busyItems.value = busyItems.value - item.id
                                                    }
                                                }
                                            }
                                            .semantics {
                                                contentDescription = actionDescription
                                                role = Role.Checkbox
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (item.completed) {
                                            Box(
                                                Modifier
                                                    .size(20.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    painterResource(R.drawable.ic_check_small),
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(12.dp),
                                                )
                                            }
                                        } else {
                                            Box(
                                                Modifier
                                                    .size(20.dp)
                                                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .clickable {
                                                context.startActivity(QuickEntryActivity.editIntent(context, item.id))
                                            }
                                            .semantics(mergeDescendants = true) {
                                                contentDescription = titleDescription
                                            }
                                            .padding(vertical = 8.dp),
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (item.pinned) {
                                                Icon(
                                                    painterResource(R.drawable.ic_pin),
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp),
                                                )
                                                Spacer(Modifier.width(3.dp))
                                            }
                                            Text(
                                                item.title,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                textDecoration = if (item.completed) TextDecoration.LineThrough else null
                                            )
                                        }
                                        if (item.note.isNotBlank()) {
                                            Text(
                                                item.note,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    if (quantity.isNotEmpty()) {
                                        Text(
                                            quantity,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .widthIn(max = 64.dp)
                                                .clearAndSetSemantics {},
                                        )
                                    }
                                }
                                if (index < items.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** One half of the 40dp 未完成/已完成 segmented control. */
    @Composable
    private fun FilterSegment(
        selected: Boolean,
        label: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        Box(
            modifier = modifier
                .fillMaxHeight()
                .clip(MaterialTheme.shapes.extraSmall)
                .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    private enum class MainScreen { LIST, MANAGE, SETTINGS }
}

/** Shows an in-process undo action for exactly five seconds. */
private suspend fun SnackbarHostState.showUndo(message: String): SnackbarResult = coroutineScope {
    val timer = launch {
        delay(5_000)
        currentSnackbarData?.dismiss()
    }
    try {
        showSnackbar(message = message, actionLabel = "撤销", duration = SnackbarDuration.Indefinite)
    } finally {
        timer.cancel()
    }
}
