package com.wanglemai.app.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wanglemai.app.app
import com.wanglemai.app.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.app.container
    private val repo = container.repository

    // Pre-v0.4.0 stored the last list under this key; migrate it lazily to PREF_LAST_LIST.
    private val legacyPrefLastList = "last_list_TODO"

    val lists = repo.observeLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val listCounts = repo.observeListCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    val activeListCounts = repo.observeActiveListCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    private val selected = MutableStateFlow<String?>(null)
    val completed = MutableStateFlow(false)
    val search = MutableStateFlow("")
    val loading = MutableStateFlow(true)
    val error = MutableStateFlow<String?>(null)
    val theme = MutableStateFlow(ThemeMode.SYSTEM)

    val listId: StateFlow<String> = combine(selected, lists) { chosen, lists ->
        if (chosen != null && lists.any { it.id == chosen }) chosen
        else DEFAULT_TODO
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DEFAULT_TODO)

    // Empty queries (initial state, clearing) pass instantly; typing debounces the
    // unindexed full-scan query so every keystroke does not restart a Room query.
    private val debouncedSearch = search.debounce { query -> if (query.isBlank()) 0 else 250 }

    val items = combine(listId, completed, debouncedSearch) { id, done, query -> Triple(id, done, query) }
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

    val completedCount = listId.flatMapLatest { id ->
        repo.observeCompletedCount(id).catch { emit(0) }
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
                var stored = repo.getPreference(PREF_LAST_LIST)
                if (stored == null) {
                    stored = repo.getPreference(legacyPrefLastList)
                    if (stored != null) repo.setPreference(PREF_LAST_LIST, stored)
                }
                selected.value = stored
                theme.value = themeModeOf(repo.getPreference(PREF_THEME))
            } catch (_: Exception) {
                Unit
            }
        }
    }

    private suspend fun savePreferenceQuietly(key: String, value: String) {
        try {
            repo.setPreference(key, value)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    suspend fun openList(id: String) = withContext(Dispatchers.IO) {
        val list = try {
            repo.list(id)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            error.value = "暂时无法读取记录，请重试"
            return@withContext
        }
        if (list == null) {
            error.value = "清单已不存在，已切换到默认清单"
            selected.value = DEFAULT_TODO
            savePreferenceQuietly(PREF_LAST_LIST, DEFAULT_TODO)
            return@withContext
        }
        selectList(id)
    }

    suspend fun selectList(id: String) = withContext(Dispatchers.IO) {
        val list = try {
            repo.list(id)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            error.value = "暂时无法读取记录，请重试"
            return@withContext
        } ?: return@withContext
        selected.value = list.id
        savePreferenceQuietly(PREF_LAST_LIST, list.id)
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
            savePreferenceQuietly(PREF_THEME, mode.name)
        }
    }

    fun clearError() {
        error.value = null
    }

    // NonCancellable bodies: a committed intent must not roll back when the UI
    // scope dies mid-transaction (activity recreation, navigation away).
    suspend fun setCompleted(item: Item): MutationResult = withContext(Dispatchers.IO) {
        withContext(NonCancellable) {
            container.mutate { repo.setCompleted(item.id, item.version, !item.completed) }
        }
    }

    suspend fun togglePin(item: Item): MutationResult = withContext(Dispatchers.IO) {
        withContext(NonCancellable) {
            container.mutate {
                repo.update(item.id, item.version, ItemInput(item.listId, item.title, item.note, !item.pinned))
            }
        }
    }

    suspend fun undo(token: UndoToken): MutationResult = withContext(Dispatchers.IO) {
        withContext(NonCancellable) {
            container.mutate { repo.undoCompletion(token) }
        }
    }

    suspend fun createList(name: String): MutationResult = withContext(Dispatchers.IO) {
        withContext(NonCancellable) {
            val result = container.mutate { repo.createList(name) }
            val id = (result as? MutationResult.Success)?.itemIds?.firstOrNull()
            if (id != null) selectList(id)
            result
        }
    }

    suspend fun renameList(list: TaskList, name: String): MutationResult = withContext(Dispatchers.IO) {
        withContext(NonCancellable) {
            container.mutate { repo.renameList(list.id, list.version, name) }
        }
    }

    suspend fun deleteList(list: TaskList): MutationResult = withContext(Dispatchers.IO) {
        withContext(NonCancellable) {
            val result = container.mutate { repo.deleteListAndMoveToDefault(list.id, list.version) }
            if (result is MutationResult.Success && selected.value == list.id) {
                selected.value = DEFAULT_TODO
                savePreferenceQuietly(PREF_LAST_LIST, DEFAULT_TODO)
            }
            result
        }
    }
}
