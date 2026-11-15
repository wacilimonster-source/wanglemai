package com.wanglemai.app.ui.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wanglemai.app.WanglemaiApplication
import com.wanglemai.app.app
import com.wanglemai.app.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

enum class EditorMode { CREATE, EDIT }

data class EditorUiState(
    val mode: EditorMode,
    val loaded: Boolean = false,
    val lists: List<TaskList> = emptyList(),
    val listId: String = DEFAULT_TODO,
    val itemId: String? = null,
    val title: String = "",
    val note: String = "",
    val pinned: Boolean = false,
    val batch: Boolean = false,
    val expectedVersion: Long = 1,
    val draftBaseVersion: Long? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val draftRestored: Boolean = false,
    val conflict: Boolean = false,
    val itemMissing: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val focusRequest: Int = 0,
    val imeRequest: Int = 0,
    val savedCount: Int = 0,
    val dirty: Boolean = false
) {
    val canSave: Boolean get() = loaded && !busy && !itemMissing && !conflict
}

sealed interface EditorEvent {
    data object Finished : EditorEvent
}

class EditorViewModel(
    application: Application,
    private val mode: EditorMode,
    private val initialListId: String?,
    private val itemId: String?
) : AndroidViewModel(application) {
    private val container = application.app.container
    private val appScope = (application as WanglemaiApplication).scope
    private val repo = container.repository
    private val _state = MutableStateFlow(EditorUiState(mode = mode, itemId = itemId))
    val state = _state.asStateFlow()
    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    @Volatile private var commandId = UUID.randomUUID().toString()
    private var draftJob: Job? = null
    private val draftMutex = Mutex()
    @Volatile private var draftEpoch = 0L
    @Volatile private var pendingLatestVersion: Long? = null
    @Volatile private var lastSavedDraftKey: String? = null

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val loadedState = withContext(Dispatchers.IO) {
                    repo.initialize()
                    val allLists = repo.lists()
                    val theme = themeModeOf(repo.getPreference(PREF_THEME))
                    if (mode == EditorMode.CREATE) {
                        val listId = initialListId?.takeIf { id -> allLists.any { it.id == id } }
                            ?: DEFAULT_TODO
                        val key = "create:$listId"
                        val draft = repo.getDraft(key)
                        var state = _state.value.copy(
                            lists = allLists, theme = theme, loaded = true, listId = listId
                        )
                        if (draft != null && allLists.any { it.id == draft.listId }) {
                            state = state.copy(
                                listId = draft.listId,
                                title = draft.title,
                                note = draft.note,
                                pinned = draft.pinned,
                                batch = draft.batch,
                                draftRestored = true
                            )
                            lastSavedDraftKey = key
                        } else if (draft != null) {
                            repo.deleteDraft(key)
                        }
                        state
                    } else {
                        val item = itemId?.let { repo.getItem(it) }
                        if (item == null) {
                            _state.value.copy(lists = allLists, theme = theme,
                                loaded = true, itemMissing = true, error = "记录已被删除")
                        } else {
                            val draft = repo.getDraft("edit:${item.id}")
                            val fallbackList = if (allLists.any { it.id == item.listId }) item.listId
                                else allLists.firstOrNull { it.isDefault }?.id ?: DEFAULT_TODO
                            var state = _state.value.copy(
                                lists = allLists, theme = theme, loaded = true, itemId = item.id,
                                listId = fallbackList, title = item.title, note = item.note,
                                pinned = item.pinned, expectedVersion = item.version,
                                draftBaseVersion = item.version
                            )
                            if (draft != null) {
                                state = state.copy(
                                    listId = draft.listId.takeIf { id ->
                                        allLists.any { it.id == id }
                                    } ?: fallbackList,
                                    title = draft.title,
                                    note = draft.note,
                                    pinned = draft.pinned,
                                    draftRestored = true,
                                    draftBaseVersion = draft.baseItemVersion ?: item.version
                                )
                                lastSavedDraftKey = "edit:${item.id}"
                                if (draft.baseItemVersion != item.version) {
                                    pendingLatestVersion = item.version
                                    state = state.copy(conflict = true)
                                }
                            }
                            state
                        }
                    }
                }
                _state.value = loadedState.copy(
                    focusRequest = loadedState.focusRequest + 1,
                    imeRequest = if (mode == EditorMode.CREATE) loadedState.imeRequest + 1
                    else loadedState.imeRequest
                )
            } catch (_: Exception) {
                _state.value = _state.value.copy(loaded = false,
                    error = "暂时无法读取记录，请返回后重试")
            }
        }
    }

    fun setTitle(value: String) = touch { it.copy(title = value) }
    fun setNote(value: String) = touch { it.copy(note = value) }
    fun setPinned(value: Boolean) = touch { it.copy(pinned = value) }
    fun setBatch(value: Boolean) {
        if (mode != EditorMode.CREATE) return
        touch { it.copy(batch = value, focusRequest = it.focusRequest + 1,
            imeRequest = it.imeRequest + 1) }
    }

    fun requestFocus(showIme: Boolean = false) {
        _state.value = _state.value.copy(
            focusRequest = _state.value.focusRequest + 1,
            imeRequest = if (showIme) _state.value.imeRequest + 1 else _state.value.imeRequest
        )
    }

    fun setListId(value: String) {
        val current = _state.value
        if (value == current.listId || current.lists.none { it.id == value }) return
        val oldKey = draftKey(current)
        draftEpoch++
        draftJob?.cancel()
        if (mode == EditorMode.CREATE) {
            appScope.launch(Dispatchers.IO) {
                draftMutex.withLock {
                    if (lastSavedDraftKey == oldKey) lastSavedDraftKey = null
                    runCatching { repo.deleteDraft(oldKey) }
                }
            }
        }
        _state.value = current.copy(listId = value, error = null, info = null, dirty = true)
        scheduleDraft()
    }

    private inline fun touch(transform: (EditorUiState) -> EditorUiState) {
        _state.value = transform(_state.value).copy(error = null, info = null, dirty = true)
        scheduleDraft()
    }

    private fun scheduleDraft() {
        if (!_state.value.loaded || _state.value.itemMissing) return
        draftJob?.cancel()
        val epoch = draftEpoch
        draftJob = viewModelScope.launch {
            delay(300)
            writeDraft(_state.value, epoch)
        }
    }

    private suspend fun loadItemSafe(id: String): Result<Item?> = try {
        Result.success(withContext(Dispatchers.IO) { repo.getItem(id) })
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private suspend fun writeDraft(snapshot: EditorUiState, epoch: Long) {
        if (!snapshot.loaded || snapshot.itemMissing || !snapshot.dirty) return
        draftMutex.withLock {
            if (epoch != draftEpoch) return
            val key = draftKey(snapshot)
            val previous = lastSavedDraftKey
            val draft = Draft(
                draftKey = key,
                listId = snapshot.listId,
                title = snapshot.title,
                note = snapshot.note,
                pinned = snapshot.pinned,
                batch = snapshot.batch,
                baseItemVersion = snapshot.draftBaseVersion
            )
            val saved = try {
                withContext(Dispatchers.IO) {
                    repo.saveDraft(draft)
                    if (previous != null && previous != key) runCatching { repo.deleteDraft(previous) }
                }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            if (saved) lastSavedDraftKey = key
        }
    }

    /** Best-effort onStop write on the application scope so Activity teardown cannot cancel it. */
    fun flushDraft() {
        draftJob?.cancel()
        val snapshot = _state.value
        if (!snapshot.loaded || snapshot.itemMissing || !snapshot.dirty) return
        val epoch = draftEpoch
        appScope.launch(Dispatchers.IO) { runCatching { writeDraft(snapshot, epoch) } }
    }

    fun save(continueAfter: Boolean = false) {
        performSave(continueAfter)
    }

    private fun performSave(continueAfter: Boolean) {
        val snapshot = _state.value
        if (!snapshot.canSave) {
            if (snapshot.conflict) _state.value = snapshot.copy(error = "请先处理记录冲突")
            return
        }
        if (snapshot.lists.none { it.id == snapshot.listId }) {
            _state.value = snapshot.copy(error = "清单已不存在")
            return
        }
        _state.value = snapshot.copy(busy = true, error = null, info = null)
        viewModelScope.launch {
            val input = ItemInput(
                listId = snapshot.listId,
                title = snapshot.title,
                note = snapshot.note,
                pinned = snapshot.pinned
            )
            val result = withContext(Dispatchers.IO) {
                container.mutate {
                    when {
                        mode == EditorMode.EDIT -> repo.update(itemId.orEmpty(),
                            snapshot.expectedVersion, input)
                        snapshot.batch -> repo.createBatch(
                            BatchInput(snapshot.listId, snapshot.title, snapshot.note, snapshot.pinned),
                            commandId)
                        else -> repo.create(input, commandId)
                    }
                }
            }
            if (result is MutationResult.Success) {
                clearDraftAfterSave(snapshot)
                if (mode == EditorMode.CREATE && continueAfter) {
                    commandId = UUID.randomUUID().toString()
                    _state.value = _state.value.copy(
                        title = "", note = "", pinned = false,
                        busy = false, error = null, draftRestored = false,
                        savedCount = result.itemIds.size,
                        info = "已保存 ${result.itemIds.size} 条",
                        focusRequest = _state.value.focusRequest + 1,
                        imeRequest = _state.value.imeRequest + 1
                    )
                } else {
                    _events.send(EditorEvent.Finished)
                }
            } else {
                val latestResult = if (mode == EditorMode.EDIT) loadItemSafe(itemId.orEmpty()) else Result.success(null)
                val latest = latestResult.getOrNull()
                val missing = mode == EditorMode.EDIT && latestResult.isSuccess && latest == null
                if (latest != null && result is MutationResult.Conflict) {
                    pendingLatestVersion = latest.version
                }
                _state.value = _state.value.copy(
                    busy = false,
                    error = result.message(),
                    itemMissing = missing,
                    conflict = latest != null && result is MutationResult.Conflict,
                    draftRestored = _state.value.draftRestored
                )
                val current = _state.value
                val epoch = draftEpoch
                appScope.launch(Dispatchers.IO) { runCatching { writeDraft(current, epoch) } }
            }
        }
    }

    fun delete() {
        if (mode != EditorMode.EDIT || itemId == null || !_state.value.canSave) return
        val snapshot = _state.value
        _state.value = snapshot.copy(busy = true, error = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                container.mutate { repo.delete(itemId, snapshot.expectedVersion) }
            }
            if (result is MutationResult.Success) {
                clearDraftAfterSave(snapshot)
                _events.send(EditorEvent.Finished)
            } else {
                val latestResult = loadItemSafe(itemId)
                val latest = latestResult.getOrNull()
                if (latest != null && result is MutationResult.Conflict) {
                    pendingLatestVersion = latest.version
                }
                _state.value = _state.value.copy(
                    busy = false,
                    error = result.message(),
                    itemMissing = latestResult.isSuccess && latest == null,
                    conflict = latest != null && result is MutationResult.Conflict
                )
            }
        }
    }

    fun reloadLatest() {
        if (mode != EditorMode.EDIT || itemId == null) return
        viewModelScope.launch {
            val itemResult = loadItemSafe(itemId)
            if (itemResult.isFailure) {
                _state.value = _state.value.copy(error = "读取最新记录失败，请重试")
                return@launch
            }
            val item = itemResult.getOrNull()
            if (item == null) {
                _state.value = _state.value.copy(itemMissing = true, conflict = false,
                    error = "记录已被删除")
                return@launch
            }
            val key = "edit:${item.id}"
            draftEpoch++
            draftJob?.cancel()
            draftMutex.withLock {
                withContext(Dispatchers.IO) { runCatching { repo.deleteDraft(key) } }
                if (lastSavedDraftKey == key) lastSavedDraftKey = null
            }
            _state.value = _state.value.copy(
                title = item.title,
                note = item.note,
                pinned = item.pinned,
                listId = item.listId,
                expectedVersion = item.version,
                draftBaseVersion = item.version,
                draftRestored = false,
                conflict = false,
                itemMissing = false,
                error = null,
                info = null,
                dirty = false,
                focusRequest = _state.value.focusRequest + 1
            )
        }
    }

    fun keepDraftOverride() {
        val latest = pendingLatestVersion ?: return
        draftEpoch++
        _state.value = _state.value.copy(
            expectedVersion = latest,
            draftBaseVersion = latest,
            conflict = false,
            draftRestored = true,
            error = null,
            info = "保存时将覆盖最新版本",
            dirty = true
        )
        scheduleDraft()
    }

    fun discardDraft() {
        val snapshot = _state.value
        val key = draftKey(snapshot)
        draftEpoch++
        draftJob?.cancel()
        viewModelScope.launch {
            draftMutex.withLock {
                withContext(Dispatchers.IO) { runCatching { repo.deleteDraft(key) } }
                lastSavedDraftKey = null
            }
            if (mode == EditorMode.EDIT && itemId != null) {
                val itemResult = loadItemSafe(itemId)
                if (itemResult.isFailure) {
                    _state.value = _state.value.copy(draftRestored = false, error = "读取最新记录失败，请重试")
                    return@launch
                }
                val item = itemResult.getOrNull()
                if (item == null) {
                    _state.value = _state.value.copy(itemMissing = true, draftRestored = false,
                        conflict = false, dirty = false, error = "记录已被删除")
                } else {
                    _state.value = _state.value.copy(
                        title = item.title, note = item.note,
                        pinned = item.pinned, listId = item.listId,
                        expectedVersion = item.version, draftBaseVersion = item.version,
                        draftRestored = false, conflict = false, error = null, info = null,
                        dirty = false, focusRequest = _state.value.focusRequest + 1
                    )
                }
            } else {
                _state.value = _state.value.copy(
                    title = "", note = "", pinned = false,
                    batch = false, draftRestored = false, conflict = false,
                    error = null, info = null, dirty = false,
                    focusRequest = _state.value.focusRequest + 1,
                    imeRequest = _state.value.imeRequest + 1
                )
            }
        }
    }

    private suspend fun clearDraftAfterSave(snapshot: EditorUiState) {
        draftJob?.cancel()
        draftEpoch++
        draftMutex.withLock {
            val key = draftKey(snapshot)
            val previous = lastSavedDraftKey
            withContext(Dispatchers.IO) {
                runCatching { repo.deleteDraft(key) }
                if (previous != null && previous != key) runCatching { repo.deleteDraft(previous) }
            }
            lastSavedDraftKey = null
        }
        _state.value = _state.value.copy(dirty = false)
    }

    private fun draftKey(snapshot: EditorUiState): String =
        if (mode == EditorMode.EDIT) "edit:${itemId.orEmpty()}" else "create:${snapshot.listId}"

    class Factory(
        private val application: Application,
        private val mode: EditorMode,
        private val listId: String?,
        private val itemId: String?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            EditorViewModel(application, mode, listId, itemId) as T
    }
}
