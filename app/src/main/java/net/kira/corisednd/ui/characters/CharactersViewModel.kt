package net.kira.corisednd.ui.characters

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.kira.corisednd.data.*

private const val PAGE_SIZE = 20

class CharactersViewModel(container: AppContainer) : ViewModel() {
    private val api = container.retrofit.create(CharactersApi::class.java)

    var items by mutableStateOf<List<CharacterListItem>>(emptyList()); private set
    var total by mutableStateOf(0); private set
    var hasMore by mutableStateOf(false); private set
    var loading by mutableStateOf(false); private set
    var loadingMore by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var races by mutableStateOf<List<RaceItem>>(emptyList()); private set

    var search by mutableStateOf(""); private set
    var raceId by mutableStateOf<Long?>(null); private set
    var sort by mutableStateOf("name"); private set
    var desc by mutableStateOf(false); private set

    val hasFilters get() = search.isNotBlank() || raceId != null

    private var reloadJob: Job? = null
    private var moreJob: Job? = null

    init {
        viewModelScope.launch { safeCall { api.races() }.onSuccess { races = it } }
        reload()
    }

    fun onSearch(value: String) { search = value; reload(delayMs = 400) }
    fun onRace(id: Long?) { raceId = id; reload() }
    fun onSort(value: String) { sort = value; reload() }
    fun toggleOrder() { desc = !desc; reload() }

    private suspend fun fetch(offset: Int) = api.list(
        search = search.trim().ifBlank { null },
        raceId = raceId,
        sort = sort,
        order = if (desc) "desc" else "asc",
        limit = PAGE_SIZE,
        offset = offset,
    )

    fun reload(delayMs: Long = 0) {
        reloadJob?.cancel()
        moreJob?.cancel()
        loadingMore = false
        reloadJob = viewModelScope.launch {
            if (delayMs > 0) delay(delayMs)
            loading = true
            error = null
            try {
                safeCall { fetch(0) }
                    .onSuccess { items = it.items; total = it.total; hasMore = it.hasMore }
                    .onFailure { items = emptyList(); hasMore = false; error = it.message }
            } finally {
                loading = false
            }
        }
    }

    fun loadMore() {
        if (loading || loadingMore || !hasMore) return
        loadingMore = true
        error = null
        moreJob = viewModelScope.launch {
            try {
                safeCall { fetch(items.size) }
                    .onSuccess { items = items + it.items; total = it.total; hasMore = it.hasMore }
                    .onFailure { error = it.message }
            } finally {
                loadingMore = false
            }
        }
    }
}