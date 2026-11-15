package com.wanglemai.app.ui.lists

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wanglemai.app.domain.*
import com.wanglemai.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ManageListsScreen(
    lists: List<TaskList>,
    counts: Map<String, Int>,
    activeCounts: Map<String, Int> = emptyMap(),
    onBack: () -> Unit,
    onCreate: suspend (String) -> MutationResult,
    onRename: suspend (TaskList, String) -> MutationResult,
    onDelete: suspend (TaskList) -> MutationResult,
    onMessage: (String) -> Unit
) {
    val palette = LocalEditorialPalette.current
    val scope = rememberCoroutineScope()
    var creating by rememberSaveable { mutableStateOf(false) }
    var renamingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    var inlineName by rememberSaveable { mutableStateOf("") }
    var inlineError by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }

    val renaming = lists.firstOrNull { it.id == renamingId }
    val deleting = lists.firstOrNull { it.id == deletingId }
    val defaultName = lists.firstOrNull { it.isDefault }?.name ?: "随手待办"

    val totalActive = remember(activeCounts) { activeCounts.values.sum() }
    val totalAll = remember(counts) { counts.values.sum() }
    val totalArchived = (totalAll - totalActive).coerceAtLeast(0)

    LaunchedEffect(renamingId, lists, submitting) {
        if (!submitting && renamingId != null && renaming == null) {
            renamingId = null
            error = null
            onMessage(MutationResult.NotFound.message())
        }
    }
    LaunchedEffect(deletingId, lists, submitting) {
        if (!submitting && deletingId != null && deleting == null) {
            deletingId = null
            onMessage(MutationResult.NotFound.message())
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bg)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Editorial Page Header
        item(key = "manage_header") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EditorialBackButton(
                    text = "← 返回手账",
                    onClick = onBack
                )
                Text(
                    text = "书签与分册目录",
                    style = MaterialTheme.typography.headlineSmall,
                    color = palette.text
                )
                Text(
                    text = "默认分册可改名不可删；删册时其下记录自动迁回默认册。",
                    fontSize = 12.sp,
                    color = palette.muted,
                    lineHeight = 18.sp
                )
            }
        }

        // 2. Stacked Paper Card with Lists
        item(key = "manage_lists_card") {
            StackedPaperCard {
                lists.forEachIndexed { index, list ->
                    val totalCount = counts[list.id] ?: 0
                    val activeCount = activeCounts[list.id] ?: 0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = padCount(index + 1),
                                    fontFamily = EditorialMono,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.primary
                                )
                                Text(
                                    text = list.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.text
                                )
                                if (list.isDefault) {
                                    StampBadge(text = "默认常驻")
                                }
                            }
                            Text(
                                text = if (list.isDefault) {
                                    "${padCount(activeCount)} 项待办 · 共 ${padCount(totalCount)} 条记录 · 不可删除"
                                } else {
                                    "${padCount(activeCount)} 项待办 · 共 ${padCount(totalCount)} 条记录"
                                },
                                fontSize = 12.sp,
                                color = palette.muted
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ListRowActionButton(
                                text = "改名",
                                enabled = true,
                                destructive = false,
                                onClick = {
                                    name = list.name
                                    error = null
                                    renamingId = list.id
                                }
                            )
                            ListRowActionButton(
                                text = "删除",
                                enabled = !list.isDefault,
                                destructive = true,
                                onClick = { if (!list.isDefault) deletingId = list.id }
                            )
                        }
                    }
                    if (index < lists.lastIndex) {
                        HorizontalDivider(thickness = 1.dp, color = palette.line)
                    }
                }
            }
        }

        // 3. Inline Create Bar (+ Dialog Fallback when empty)
        item(key = "inline_create_bar") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = EditorialMetrics.InlineInputHeight),
                        shape = RoundedCornerShape(12.dp),
                        color = palette.surface,
                        border = BorderStroke(1.dp, palette.lineStrong)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = EditorialMetrics.InlineInputHeight)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (inlineName.isEmpty()) {
                                Text(
                                    text = "新分册名称，如：月度书单…",
                                    fontSize = 13.sp,
                                    color = palette.faint
                                )
                            }
                            BasicTextField(
                                value = inlineName,
                                onValueChange = {
                                    inlineName = it
                                    inlineError = null
                                },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 13.sp,
                                    color = palette.text
                                ),
                                cursorBrush = SolidColor(palette.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (inlineName.isNotBlank() && !submitting) {
                                            submitting = true
                                            scope.launch {
                                                try {
                                                    when (val res = onCreate(inlineName)) {
                                                        is MutationResult.Success -> {
                                                            inlineName = ""
                                                            inlineError = null
                                                            onMessage("已创建清单")
                                                        }

                                                        is MutationResult.ValidationError -> inlineError = res.message
                                                        else -> inlineError = res.message()
                                                    }
                                                } finally {
                                                    submitting = false
                                                }
                                            }
                                        }
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            if (inlineName.isBlank()) {
                                name = ""
                                error = null
                                creating = true
                            } else if (!submitting) {
                                submitting = true
                                scope.launch {
                                    try {
                                        when (val res = onCreate(inlineName)) {
                                            is MutationResult.Success -> {
                                                inlineName = ""
                                                inlineError = null
                                                onMessage("已创建清单")
                                            }

                                            is MutationResult.ValidationError -> inlineError = res.message
                                            else -> inlineError = res.message()
                                        }
                                    } finally {
                                        submitting = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .shadow(
                                elevation = 3.dp,
                                shape = RoundedCornerShape(12.dp),
                                ambientColor = palette.primary.copy(alpha = 0.35f),
                                spotColor = palette.primary.copy(alpha = 0.35f)
                            )
                            .semantics { contentDescription = "＋ 新建清单" },
                        shape = RoundedCornerShape(12.dp),
                        color = palette.primary
                    ) {
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minHeight = EditorialMetrics.InlineInputHeight)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "＋ 添一册",
                                fontSize = EditorialMetrics.ControlTextSize,
                                fontWeight = FontWeight.Bold,
                                color = palette.onPrimary
                            )
                        }
                    }
                }

                Text(
                    text = "分册名称全局唯一。添册后将作为顶部活页索引耳展示。",
                    fontSize = 11.sp,
                    color = palette.muted
                )
                if (inlineError != null) {
                    Text(
                        text = inlineError!!,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.danger
                    )
                }
            }
        }

        // 4. EX LIBRIS Archival Stamp Card
        item(key = "ex_libris_card") {
            val exShape = RoundedCornerShape(14.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(exShape)
                    .background(palette.surfaceWarm)
                    .drawBehind {
                        drawRoundRect(
                            color = palette.lineStrong,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                            style = Stroke(
                                width = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f), 0f)
                            )
                        )
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "EX LIBRIS · 藏书印记",
                            fontFamily = EditorialMono,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = palette.faint
                        )
                        Text(
                            text = "V0.4.0 ARCHIVE",
                            fontFamily = EditorialMono,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = palette.faint
                        )
                    }
                    HorizontalDivider(thickness = 1.dp, color = palette.line)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ExLibrisStatItem(
                            number = padCount(lists.size),
                            label = "立册总数",
                            modifier = Modifier.weight(1f)
                        )
                        ExLibrisStatItem(
                            number = padCount(totalActive),
                            label = "待办小事",
                            modifier = Modifier.weight(1f)
                        )
                        ExLibrisStatItem(
                            number = padCount(totalArchived),
                            label = "已归档页",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    if (creating || renaming != null) {
        val target = renaming
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(creating, target?.id) {
            focusRequester.requestFocus()
        }
        AlertDialog(
            onDismissRequest = {
                if (!submitting) {
                    creating = false
                    renamingId = null
                    error = null
                }
            },
            containerColor = palette.surface,
            titleContentColor = palette.text,
            textContentColor = palette.text,
            title = {
                Text(
                    text = if (target == null) "新建清单" else "重命名清单",
                    fontFamily = EditorialSerif,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            error = null
                        },
                        label = { Text("清单名称") },
                        singleLine = true,
                        isError = error != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                    if (error != null) {
                        Text(
                            text = error!!,
                            color = palette.danger,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !submitting,
                    onClick = {
                        submitting = true
                        scope.launch {
                            try {
                                val result = if (target == null) {
                                    onCreate(name)
                                } else {
                                    onRename(target, name)
                                }
                                when (result) {
                                    is MutationResult.Success -> {
                                        val created = target == null
                                        creating = false
                                        renamingId = null
                                        error = null
                                        onMessage(if (created) "已创建清单" else "已重命名")
                                    }

                                    is MutationResult.ValidationError -> error = result.message
                                    else -> error = result.message()
                                }
                            } finally {
                                submitting = false
                            }
                        }
                    }
                ) { Text("保存", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(
                    enabled = !submitting,
                    onClick = {
                        creating = false
                        renamingId = null
                        error = null
                    }
                ) { Text("取消") }
            }
        )
    }

    if (deleting != null) {
        val count = counts[deleting.id] ?: 0
        AlertDialog(
            onDismissRequest = { if (!submitting) deletingId = null },
            containerColor = palette.surface,
            titleContentColor = palette.text,
            textContentColor = palette.muted,
            title = {
                Text(
                    text = "删除「${deleting.name}」？",
                    fontFamily = EditorialSerif,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("删除「${deleting.name}」后，其中 $count 条记录将移动到「$defaultName」。")
            },
            confirmButton = {
                TextButton(
                    enabled = !submitting,
                    onClick = {
                        val target = deleting
                        submitting = true
                        scope.launch {
                            try {
                                when (val result = onDelete(target)) {
                                    is MutationResult.Success -> {
                                        deletingId = null
                                        onMessage("已删除清单，${result.movedCount} 条记录已移到「$defaultName」")
                                    }

                                    else -> {
                                        deletingId = null
                                        onMessage(result.message())
                                    }
                                }
                            } finally {
                                submitting = false
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = palette.danger)
                ) { Text("删除并移动", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(
                    enabled = !submitting,
                    onClick = { deletingId = null }
                ) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ExLibrisStatItem(
    number: String,
    label: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalEditorialPalette.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = number,
            fontFamily = EditorialSerif,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            color = palette.primary
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = palette.muted
        )
    }
}

@Composable
private fun ListRowActionButton(
    text: String,
    enabled: Boolean,
    destructive: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalEditorialPalette.current
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(EditorialMetrics.RadiusChip),
        color = if (!enabled) palette.soft.copy(alpha = 0.5f) else palette.soft,
        border = BorderStroke(
            1.dp,
            if (!enabled) palette.line else palette.lineStrong.copy(alpha = 0.65f)
        )
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minHeight = EditorialMetrics.CompactControlHeight)
                .padding(horizontal = 13.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = EditorialMetrics.ControlTextSize,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    !enabled -> palette.faint.copy(alpha = 0.55f)
                    destructive -> palette.danger
                    else -> palette.text
                }
            )
        }
    }
}
