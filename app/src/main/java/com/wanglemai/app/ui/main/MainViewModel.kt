package com.wanglemai.app.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wanglemai.app.app
import com.wanglemai.app.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.app.container
    private val repo = container.repository

    val lists = repo.observeLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val listCounts = repo.observeListCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    val kind = MutableStateFlow(ListKind.TODO)
    private val selected = MutableStateFlow<Map<ListKind, String>>(emptyMap())
    val completed = MutableStateFlow(false)
    val search = MutableStateFlow("")
    val loading = MutableStateFlow(true)
    val error = MutableStateFlow<String?>(null)
    val theme = MutableStateFlow(ThemeMode.SYSTEM)

    val listId: StateFlow<String> = combine(kind, selected, lists) { kind, selected, lists ->
        val chosen = selected[kind]
        if (chosen != null && lists.any { it.id == chosen && it.kind == kind }) chosen
        else defaultListId(kind)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DEFAULT_TODO)

    val items = combine(listId, completed, search) { id, done, query -> Triple(id, done, query) }
        .flatMapLatest { (id, done, query) ->
            repo.observeItems(id, done, query)
                .onStart { loading.value = true }
                .onEach { loading.value = false }
                .catch {
                    loading.value = false
                    error.value = "暂时无法读取记录，请重试"
                    emit(emptyList())
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeCount = listId.flatMapLatest { id ->
        repo.observeActiveCount(id).catch { emit(0) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        viewModelScope.launch {
            listId.drop(1).distinctUntilChanged().collect {
                search.value = ""
                completed.value = false
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.initialize()
                container.widgets.syncInstances()
            } catch (_: Exception) {
                error.value = "暂时无法读取记录，请重试"
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val todo = repo.getPreference(PREF_LAST_LIST_TODO)
                val shopping = repo.getPreference(PREF_LAST_LIST_SHOPPING)
                selected.value = buildMap {
                    todo?.let { put(ListKind.TODO, it) }
                    shopping?.let { put(ListKind.SHOPPING, it) }
                }
                repo.getPreference(PREF_LAST_KIND)?.let { stored ->
                    ListKind.entries.firstOrNull { it.name == stored }?.let { kind.value = it }
                }
                theme.value = themeModeOf(repo.getPreference(PREF_THEME))
            } catch (_: Exception) {
                Unit
            }
        }
    }

    suspend fun openList(id: String) = withContext(Dispatchers.IO) {
        val list = repo.list(id)
        if (list == null) {
            error.value = "清单已不存在，已切换到默认清单"
            val fallback = defaultListId(kind.value)
            selected.value = selected.value + (kind.value to fallback)
            runCatching { repo.setPreference(prefKey(kind.value), fallback) }
            return@withContext
        }
        selected.value = selected.value + (list.kind to list.id)
        kind.value = list.kind
        repo.setPreference(PREF_LAST_KIND, list.kind.name)
        repo.setPreference(prefKey(list.kind), list.id)
        search.value = ""
        completed.value = false
        error.value = null
        Unit
    }

    fun selectKind(target: ListKind) {
        if (kind.value == target) return
        kind.value = target
        search.value = ""
        completed.value = false
        error.value = null
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repo.setPreference(PREF_LAST_KIND, target.name) }
        }
    }

    suspend fun selectList(id: String) = withContext(Dispatchers.IO) {
        val list = repo.list(id) ?: return@withContext
        selected.value = selected.value + (list.kind to list.id)
        kind.value = list.kind
        repo.setPreference(PREF_LAST_KIND, list.kind.name)
        repo.setPreference(prefKey(list.kind), list.id)
        search.value = ""
        completed.value = false
        error.value = null
    }

    fun setSearch(value: String) {
        search.value = value
        error.value = null
    }

    fun setCompletedFilter(done: Boolean) {
        completed.value = done
    }

    fun setTheme(mode: ThemeMode) {
        theme.value = mode
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repo.setPreference(PREF_THEME, mode.name) }
        }
    }

    fun clearError() {
        error.value = null
    }

    suspend fun setCompleted(item: Item): MutationResult = withContext(Dispatchers.IO) {
        container.mutate { repo.setCompleted(item.id, item.version, !item.completed) }
    }

    suspend fun undo(token: UndoToken): MutationResult = withContext(Dispatchers.IO) {
        container.mutate { repo.undoCompletion(token) }
    }

    suspend fun createList(name: String): MutationResult = withContext(Dispatchers.IO) {
        val result = container.mutate { repo.createList(kind.value, name) }
        val id = (result as? MutationResult.Success)?.itemIds?.firstOrNull()
        if (id != null) selectList(id)
        result
    }

    suspend fun renameList(list: TaskList, name: String): MutationResult = withContext(Dispatchers.IO) {
        container.mutate { repo.renameList(list.id, list.version, name) }
    }

    suspend fun deleteList(list: TaskList): MutationResult = withContext(Dispatchers.IO) {
        val result = container.mutate { repo.deleteListAndMoveToDefault(list.id, list.version) }
        if (result is MutationResult.Success && selected.value[list.kind] == list.id) {
            val fallback = defaultListId(list.kind)
            selected.value = selected.value + (list.kind to fallback)
            repo.setPreference(prefKey(list.kind), fallback)
        }
        result
    }

    private fun prefKey(kind: ListKind) =
        if (kind == ListKind.TODO) PREF_LAST_LIST_TODO else PREF_LAST_LIST_SHOPPING

    private fun defaultListId(kind: ListKind) =
        if (kind == ListKind.SHOPPING) DEFAULT_SHOPPING else DEFAULT_TODO
}
