package net.kira.corisednd.ui.reference

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.kira.corisednd.data.AppContainer
import net.kira.corisednd.data.ReferenceApi
import net.kira.corisednd.data.safeCall

private const val LIMIT = 100

class ReferenceListViewModel(container: AppContainer, val section: Section) : ViewModel() {
    private val api = container.retrofit.create(ReferenceApi::class.java)

    var items by mutableStateOf<List<RefEntry>>(emptyList()); private set
    var loading by mutableStateOf(true); private set
    var error by mutableStateOf<String?>(null); private set
    var hasMore by mutableStateOf(false); private set
    var search by mutableStateOf(""); private set

    private var job: Job? = null

    init { load() }

    /** Разделы со страницами ищут на сервере, остальные фильтруются на месте. */
    fun onSearch(value: String) {
        search = value
        if (section.paged) load(delayMs = 400)
    }

    val visible: List<RefEntry>
        get() {
            val q = search.trim()
            return if (section.paged || q.isEmpty()) items
            else items.filter { it.title.contains(q, ignoreCase = true) }
        }

    fun load(delayMs: Long = 0) {
        job?.cancel()
        job = viewModelScope.launch {
            if (delayMs > 0) delay(delayMs)
            loading = true
            error = null
            try {
                val q = if (section.paged) search.trim().ifEmpty { null } else null
                safeCall { api.list(section.path, q, LIMIT) }
                    .onSuccess { el ->
                        val obj = el as? JsonObject
                        val arr = if (el is JsonArray) el else (obj?.get("items") as? JsonArray) ?: JsonArray(emptyList())
                        items = arr.map { it.jsonObject.toEntry(section) }
                        hasMore = obj?.get("hasMore")?.jsonPrimitive?.booleanOrNull ?: false
                    }
                    .onFailure { items = emptyList(); hasMore = false; error = it.message }
            } finally {
                loading = false
            }
        }
    }
}

class ReferenceDetailViewModel(
    container: AppContainer,
    val section: Section,
    private val id: Long,
) : ViewModel() {
    private val api = container.retrofit.create(ReferenceApi::class.java)

    var entry by mutableStateOf<JsonObject?>(null); private set
    var loading by mutableStateOf(true); private set
    var error by mutableStateOf<String?>(null); private set

    var deleting by mutableStateOf(false); private set
    var deleted by mutableStateOf(false); private set
    var actionError by mutableStateOf<String?>(null); private set

    /** Править и удалять можно только свою запись раздела, который поддерживает создание. */
    val canEdit: Boolean
        get() = section.editFields() != null &&
                entry?.get("isCustom")?.jsonPrimitive?.booleanOrNull == true

    fun delete() {
        if (deleting) return
        deleting = true
        actionError = null
        viewModelScope.launch {
            safeCall { api.delete(section.path, id) }
                .onSuccess { deleted = true }
                .onFailure { actionError = it.message }
            deleting = false
        }
    }

    init { load() }

    fun load() {
        loading = true
        error = null
        viewModelScope.launch {
            safeCall { api.item(section.path, id) }
                .onSuccess { entry = it }
                .onFailure { error = it.message }
            loading = false
        }
    }
}