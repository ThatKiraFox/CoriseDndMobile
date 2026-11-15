package net.kira.corisednd.ui.reference

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import net.kira.corisednd.data.AppContainer
import net.kira.corisednd.data.ReferenceApi
import net.kira.corisednd.data.safeCall

data class ContentFormState(
    val loading: Boolean = false,
    val loadError: String? = null,
    val saving: Boolean = false,
    val saveError: String? = null,
    val saved: Boolean = false,
    val values: Map<String, String> = emptyMap(),
    val errors: Map<String, String> = emptyMap(),
)

/** id == null: создание, иначе правка собственной записи. */
class ContentFormViewModel(
    container: AppContainer,
    val section: Section,
    private val id: Long?,
) : ViewModel() {

    private val api = container.retrofit.create(ReferenceApi::class.java)
    val fields: List<EditField> = section.editFields().orEmpty()
    val isEdit: Boolean get() = id != null

    private var base: JsonObject? = null

    private val _state = MutableStateFlow(
        ContentFormState(
            loading = id != null,
            values = fields.associate { it.key to if (it.kind == FieldKind.GRIP) GRIPS.first() else "" },
        ),
    )
    val state: StateFlow<ContentFormState> = _state

    init { if (id != null) load() }

    fun load() {
        val itemId = id ?: return
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            safeCall { api.item(section.path, itemId) }
                .onSuccess { obj ->
                    base = obj
                    val values = fields.associate { it.key to (obj.text(it.key) ?: "") }
                    _state.update { it.copy(loading = false, values = values) }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, loadError = e.message) } }
        }
    }

    fun setValue(key: String, value: String) {
        val kind = fields.firstOrNull { it.key == key }?.kind ?: return
        val clean = when (kind) {
            FieldKind.INT -> value.filter { it.isDigit() }.take(9)
            FieldKind.LONG_TEXT -> value.take(5000)
            else -> value.take(100)
        }
        _state.update { it.copy(values = it.values + (key to clean), errors = it.errors - key) }
    }

    fun save() {
        val s = _state.value
        if (s.saving) return

        val errors = fields
            .filter { it.required && s.values[it.key].orEmpty().isBlank() }
            .associate { it.key to "Обязательное поле" }
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors, saveError = null) }
            return
        }

        val body = buildContentBody(section, fields, s.values, base)
        _state.update { it.copy(saving = true, saveError = null, errors = emptyMap()) }
        viewModelScope.launch {
            val result = if (id == null) {
                safeCall { api.create(section.path, body) }
            } else {
                safeCall { api.update(section.path, id, body) }
            }
            result
                .onSuccess { _state.update { it.copy(saving = false, saved = true) } }
                .onFailure { e -> _state.update { it.copy(saving = false, saveError = e.message) } }
        }
    }
}