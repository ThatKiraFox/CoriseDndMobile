package net.kira.corisednd.ui.characters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.kira.corisednd.data.AddSpellRequest
import net.kira.corisednd.data.AppContainer
import net.kira.corisednd.data.ArmourItem
import net.kira.corisednd.data.CharacterDetail
import net.kira.corisednd.data.CharactersApi
import net.kira.corisednd.data.EquipmentRequest
import net.kira.corisednd.data.InventoryItemRequest
import net.kira.corisednd.data.SetValueRequest
import net.kira.corisednd.data.SpellItem
import net.kira.corisednd.data.WeaponItem
import net.kira.corisednd.data.safeCall

enum class Slot { WEAPON, ARMOUR, SHIELD }

data class DetailState(
    val loading: Boolean = true,
    val character: CharacterDetail? = null,
    val error: String? = null,
    val deleting: Boolean = false,
    val deleted: Boolean = false,

    val busy: Boolean = false, // идёт любое изменение (заклинание, предмет, экипировка)
    val dialogError: String? = null,

    val spellQuery: String = "",
    val spellResults: List<SpellItem> = emptyList(),
    val spellsLoading: Boolean = false,
    val spellsError: String? = null,

    val weapons: List<WeaponItem> = emptyList(),
    val armours: List<ArmourItem> = emptyList(),
    val catalogLoading: Boolean = false,
    val catalogError: String? = null,
)

class CharacterDetailViewModel(
    container: AppContainer,
    private val id: Long,
) : ViewModel() {

    private val api: CharactersApi = container.retrofit.create(CharactersApi::class.java)

    private val _state = MutableStateFlow(DetailState())
    val state: StateFlow<DetailState> = _state

    private var spellJob: Job? = null

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            safeCall { api.get(id) }
                .onSuccess { c -> _state.update { it.copy(loading = false, character = c) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }

    /** Обновление без индикатора на весь экран. */
    private suspend fun refresh() {
        safeCall { api.get(id) }
            .onSuccess { c -> _state.update { it.copy(busy = false, character = c) } }
            .onFailure { e -> _state.update { it.copy(busy = false, error = e.message) } }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun delete() {
        if (_state.value.deleting) return
        _state.update { it.copy(deleting = true, error = null) }
        viewModelScope.launch {
            safeCall { api.delete(id) }
                .onSuccess { _state.update { it.copy(deleting = false, deleted = true) } }
                .onFailure { e -> _state.update { it.copy(deleting = false, error = e.message) } }
        }
    }

    // ---- заклинания ----

    fun removeSpell(spellId: Long) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            safeCall { api.removeSpell(id, spellId) }
                .onSuccess { refresh() }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message) } }
        }
    }

    fun addSpell(spell: SpellItem) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, dialogError = null) }
        viewModelScope.launch {
            safeCall { api.addSpell(id, AddSpellRequest(spell.id)) }
                .onSuccess { refresh() }
                .onFailure { e -> _state.update { it.copy(busy = false, dialogError = e.message) } }
        }
    }

    fun openSpellPicker() {
        _state.update { it.copy(dialogError = null) }
        spellJob?.cancel()
        spellJob = viewModelScope.launch { fetchSpells() }
    }

    fun onSpellQuery(q: String) {
        _state.update { it.copy(spellQuery = q) }
        spellJob?.cancel()
        spellJob = viewModelScope.launch {
            delay(400)
            fetchSpells()
        }
    }

    private suspend fun fetchSpells() {
        _state.update { it.copy(spellsLoading = true, spellsError = null) }
        val q = _state.value.spellQuery.trim().ifEmpty { null }
        safeCall { api.spells(search = q) }
            .onSuccess { p -> _state.update { it.copy(spellsLoading = false, spellResults = p.items) } }
            .onFailure { e -> _state.update { it.copy(spellsLoading = false, spellsError = e.message) } }
    }

    // ---- каталог оружия и брони ----

    fun loadCatalog() {
        _state.update { it.copy(catalogLoading = true, catalogError = null, dialogError = null) }
        viewModelScope.launch {
            val w = safeCall { api.weapons() }
            val a = safeCall { api.armours() }
            val failure = listOf(w, a).firstOrNull { it.isFailure }
            if (failure != null) {
                _state.update {
                    it.copy(catalogLoading = false, catalogError = failure.exceptionOrNull()?.message)
                }
            } else {
                _state.update {
                    it.copy(catalogLoading = false, weapons = w.getOrThrow(), armours = a.getOrThrow())
                }
            }
        }
    }

    // ---- инвентарь ----

    /** Добавление из диалога: ошибка показывается в диалоге. */
    fun addToInventory(itemId: Long) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, dialogError = null) }
        viewModelScope.launch {
            safeCall { api.putItem(id, itemId, InventoryItemRequest(1)) }
                .onSuccess { refresh() }
                .onFailure { e -> _state.update { it.copy(busy = false, dialogError = e.message) } }
        }
    }

    fun setAmount(itemId: Long, amount: Int) {
        if (_state.value.busy || amount < 1) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            safeCall { api.putItem(id, itemId, InventoryItemRequest(amount)) }
                .onSuccess { refresh() }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message) } }
        }
    }

    fun removeItem(itemId: Long) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            safeCall { api.removeItem(id, itemId) }
                .onSuccess { refresh() } // сервер сам снимает убранный предмет с персонажа
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message) } }
        }
    }

    // ---- экипировка ----

    /** newId == null: снять предмет из слота. Остальные слоты остаются как есть. */
    fun equip(slot: Slot, newId: Long?) {
        val c = _state.value.character ?: return
        if (_state.value.busy) return
        val e = c.equipment
        val request = EquipmentRequest(
            weaponId = if (slot == Slot.WEAPON) newId else e.weapon?.id,
            armourId = if (slot == Slot.ARMOUR) newId else e.armour?.id,
            shieldId = if (slot == Slot.SHIELD) newId else e.shield?.id,
        )
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            safeCall { api.setEquipment(id, request) }
                .onSuccess { refresh() }
                .onFailure { err -> _state.update { it.copy(busy = false, error = err.message) } }
        }
    }
}