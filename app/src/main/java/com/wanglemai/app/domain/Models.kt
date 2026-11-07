package com.wanglemai.app.domain

enum class ListKind { TODO, SHOPPING }
enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class WidgetDensity { COMFORTABLE, COMPACT, ULTRA_COMPACT }

const val DEFAULT_TODO = "list-todo-default"
const val DEFAULT_SHOPPING = "list-shopping-default"
const val PREF_THEME = "theme"
const val PREF_LAST_KIND = "last_kind"
const val PREF_LAST_LIST_TODO = "last_list_TODO"
const val PREF_LAST_LIST_SHOPPING = "last_list_SHOPPING"

fun themeModeOf(value: String?): ThemeMode =
    runCatching { ThemeMode.valueOf(value.orEmpty()) }.getOrDefault(ThemeMode.SYSTEM)

fun widgetDensityOf(value: String?): WidgetDensity =
    runCatching { WidgetDensity.valueOf(value.orEmpty()) }.getOrDefault(WidgetDensity.COMFORTABLE)

data class TaskList(
    val id: String,
    val name: String,
    val kind: ListKind,
    val isDefault: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val version: Long = 1
)

data class Item(
    val id: String, val listId: String, val title: String, val note: String,
    val quantityDecimal: String?, val unit: String?, val completed: Boolean,
    val pinned: Boolean, val sortKey: Long, val createdAt: Long, val updatedAt: Long,
    val completedAt: Long?, val version: Long
)

data class ItemInput(
    val listId: String, val title: String, val note: String = "",
    val quantity: String = "", val unit: String = "", val pinned: Boolean = false
)

data class BatchInput(
    val listId: String, val text: String, val note: String = "", val pinned: Boolean = false
)

data class Draft(
    val draftKey: String,
    val listId: String,
    val title: String,
    val note: String,
    val quantity: String,
    val unit: String,
    val pinned: Boolean,
    val batch: Boolean,
    val baseItemVersion: Long?
)

data class UndoToken(
    val itemId: String, val completed: Boolean,
    val completedAt: Long?, val expectedVersion: Long
)

sealed interface MutationResult {
    data class Success(
        val itemIds: List<String> = emptyList(),
        val undo: UndoToken? = null,
        val movedCount: Int = 0,
        val message: String? = null
    ) : MutationResult
    data class ValidationError(
        val message: String,
        val field: String? = null,
        val line: Int? = null
    ) : MutationResult
    data object NotFound : MutationResult
    data object Conflict : MutationResult
    data object StorageError : MutationResult
}

fun MutationResult.message(): String = when (this) {
    is MutationResult.Success -> message ?: "已保存"
    is MutationResult.ValidationError -> message
    MutationResult.NotFound -> "记录已不存在，请重新查看"
    MutationResult.Conflict -> "记录已变化，请重新加载后编辑"
    MutationResult.StorageError -> "没有保存成功，请重试"
}
