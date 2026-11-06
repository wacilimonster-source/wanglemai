package com.wanglemai.app.domain

import kotlinx.coroutines.flow.Flow

interface ItemRepository {
    fun observeLists(): Flow<List<TaskList>>
    fun observeListCounts(): Flow<Map<String, Int>>
    fun observeActiveCount(listId: String): Flow<Int>
    fun observeItems(listId: String, completed: Boolean, search: String): Flow<List<Item>>
    suspend fun initialize()
    suspend fun lists(): List<TaskList>
    suspend fun list(id: String): TaskList?
    suspend fun getItem(id: String): Item?
    suspend fun create(input: ItemInput, commandId: String): MutationResult
    suspend fun createBatch(input: BatchInput, commandId: String): MutationResult
    suspend fun update(id: String, expectedVersion: Long, input: ItemInput): MutationResult
    suspend fun setCompleted(id: String, expectedVersion: Long, completed: Boolean, originWidgetId: Int? = null): MutationResult
    suspend fun undoCompletion(token: UndoToken): MutationResult
    suspend fun undoWidgetCompletion(widgetId: Int, commandId: String): MutationResult
    suspend fun delete(id: String, expectedVersion: Long): MutationResult

    suspend fun createList(kind: ListKind, name: String): MutationResult
    suspend fun renameList(id: String, expectedVersion: Long, name: String): MutationResult
    suspend fun deleteListAndMoveToDefault(id: String, expectedVersion: Long): MutationResult

    suspend fun getDraft(draftKey: String): Draft?
    suspend fun saveDraft(draft: Draft)
    suspend fun deleteDraft(draftKey: String)
    suspend fun getPreference(key: String): String?
    suspend fun setPreference(key: String, value: String)
}

interface BackupRepository {
    suspend fun exportJson(): String
    suspend fun validateAndPreview(json: String): BackupPreview
    suspend fun merge(document: BackupDocument): BackupMergeReport
}
