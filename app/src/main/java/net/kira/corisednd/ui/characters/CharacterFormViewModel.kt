package net.kira.corisednd.ui.characters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.kira.corisednd.data.AppContainer
import net.kira.corisednd.data.AttributeValue
import net.kira.corisednd.data.CharacterCreateRequest
import net.kira.corisednd.data.CharacterUpdateRequest
import net.kira.corisednd.data.CharactersApi
import net.kira.corisednd.data.NamedRef
import net.kira.corisednd.data.RaceItem
import net.kira.corisednd.data.SetValueRequest
import net.kira.corisednd.data.SkillValue
import net.kira.corisednd.data.SpellItem
import net.kira.corisednd.data.safeCall

data class FormState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val saving: Boolean = false,
    val saveError: String? = null,
    val saved: Boolean = false,

    val name: String = "",
    val raceId: Long? = null,
    val cultureId: Long? = null,
    val religionId: Long? = null,
    val backstoryId: Long? = null,
    val level: String = "1",
    val exp: String = "0",
    val hp: String = "0",
    val ml: String = "0",

    val races: List<RaceItem> = emptyList(),
    val cultures: List<NamedRef> = emptyList(),
    val religions: List<NamedRef> = emptyList(),
    val backstories: List<NamedRef> = emptyList(),

    val selectedSpells: Map<Long, String> = emptyMap(),
    val spellQuery: String = "",
    val spellResults: List<SpellItem> = emptyList(),
    val spellsLoading: Boolean = false,
    val spellsError: String? = null,
    val attributes: List<AttributeValue> = emptyList(),
    val skills: List<SkillValue> = emptyList(),
    val statBusy: Boolean = false,
    val statError: String? = null,

    val errors: Map<String, String> = emptyMap(),
)

/** characterId == null: создание, иначе правка существующего персонажа. */
class CharacterFormViewModel(
    container: AppContainer,
    private val characterId: Long?,
) : ViewModel() {

    private val api: CharactersApi = container.retrofit.create(CharactersApi::class.java)

    private val _state = MutableStateFlow(FormState())
    val state: StateFlow<FormState> = _state

    private var spellJob: Job? = null

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            val races = safeCall { api.races() }
            val cultures = safeCall { api.cultures() }
            val religions = safeCall { api.religions() }
            val backstories = safeCall { api.backstories() }
            val detail = characterId?.let { safeCall { api.get(it) } }

            val failure = listOfNotNull(races, cultures, religions, backstories, detail)
                .firstOrNull { it.isFailure }
            if (failure != null) {
                _state.update { it.copy(loading = false, loadError = failure.exceptionOrNull()?.message) }
                return@launch
            }

            val c = detail?.getOrNull()
            _state.update { s ->
                s.copy(
                    loading = false,
                    races = races.getOrThrow(),
                    cultures = cultures.getOrThrow(),
                    religions = religions.getOrThrow(),
                    backstories = backstories.getOrThrow(),
                    name = c?.name ?: s.name,
                    raceId = if (c != null) c.race?.id else s.raceId,
                    cultureId = if (c != null) c.culture?.id else s.cultureId,
                    religionId = if (c != null) c.religion?.id else s.religionId,
                    backstoryId = if (c != null) c.backstory?.id else s.backstoryId,
                    level = c?.level?.toString() ?: s.level,
                    exp = c?.exp?.toString() ?: s.exp,
                    hp = c?.hpCur?.toString() ?: s.hp,
                    ml = c?.mlCur?.toString() ?: s.ml,
                    attributes = c?.attributes ?: s.attributes,
                    skills = c?.skills ?: s.skills,
                )
            }
        }
    }

    private fun digits(v: String) = v.filter { it.isDigit() }.take(9)

    fun setName(v: String) = _state.update { it.copy(name = v, errors = it.errors - "name") }
    fun setRace(id: Long?) = _state.update { it.copy(raceId = id, errors = it.errors - "race") }
    fun setCulture(id: Long?) = _state.update { it.copy(cultureId = id) }
    fun setReligion(id: Long?) = _state.update { it.copy(religionId = id) }
    fun setBackstory(id: Long?) = _state.update { it.copy(backstoryId = id) }
    fun setLevel(v: String) = _state.update { it.copy(level = digits(v), errors = it.errors - "level") }
    fun setExp(v: String) = _state.update { it.copy(exp = digits(v), errors = it.errors - "exp") }
    fun setHp(v: String) = _state.update { it.copy(hp = digits(v), errors = it.errors - "hp") }
    fun setMl(v: String) = _state.update { it.copy(ml = digits(v), errors = it.errors - "ml") }

    // ---- заклинания (только при создании) ----

    fun openSpells() {
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

    fun toggleSpell(spell: SpellItem) = _state.update {
        val m = it.selectedSpells.toMutableMap()
        if (m.remove(spell.id) == null) m[spell.id] = spell.name
        it.copy(selectedSpells = m)
    }

    fun removeSpell(id: Long) = _state.update { it.copy(selectedSpells = it.selectedSpells - id) }

    // ---- атрибуты и навыки (только при правке, применяются сразу) ----

    fun setAttribute(attributeId: Long, value: Int) {
        val id = characterId ?: return
        if (_state.value.statBusy || value !in 0..10) return
        _state.update { it.copy(statBusy = true, statError = null) }
        viewModelScope.launch {
            safeCall { api.setAttribute(id, attributeId, SetValueRequest(value)) }
                .onSuccess { reloadStats(id) }
                .onFailure { e -> _state.update { it.copy(statBusy = false, statError = e.message) } }
        }
    }

    fun setSkill(skillId: Long, level: Int) {
        val id = characterId ?: return
        if (_state.value.statBusy || level !in 0..10) return
        _state.update { it.copy(statBusy = true, statError = null) }
        viewModelScope.launch {
            safeCall { api.setSkill(id, skillId, SetValueRequest(level)) }
                .onSuccess { reloadStats(id) }
                .onFailure { e -> _state.update { it.copy(statBusy = false, statError = e.message) } }
        }
    }

    /** Обновляет только атрибуты и навыки: несохранённые правки в полях формы не затираются. */
    private suspend fun reloadStats(id: Long) {
        safeCall { api.get(id) }
            .onSuccess { c ->
                _state.update { it.copy(statBusy = false, attributes = c.attributes, skills = c.skills) }
            }
            .onFailure { e -> _state.update { it.copy(statBusy = false, statError = e.message) } }
    }

    // ---- сохранение ----

    fun save() {
        val s = _state.value
        if (s.saving) return

        val errors = mutableMapOf<String, String>()
        val name = s.name.trim()
        if (name.isEmpty()) errors["name"] = "Введите имя"
        if (s.raceId == null) errors["race"] = "Выберите расу"
        val level = s.level.toIntOrNull()
        if (level == null || level < 1) errors["level"] = "Уровень от 1"
        val exp = s.exp.toIntOrNull()
        if (exp == null) errors["exp"] = "Введите число"
        val hp = s.hp.toIntOrNull()
        if (hp == null) errors["hp"] = "Введите число"
        val ml = s.ml.toIntOrNull()
        if (ml == null) errors["ml"] = "Введите число"

        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors, saveError = null) }
            return
        }

        _state.update { it.copy(saving = true, saveError = null, errors = emptyMap()) }
        viewModelScope.launch {
            val result = if (characterId == null) {
                safeCall {
                    api.create(
                        CharacterCreateRequest(
                            name = name, raceId = s.raceId!!,
                            cultureId = s.cultureId, religionId = s.religionId, backstoryId = s.backstoryId,
                            level = level!!, exp = exp!!, hpCur = hp!!, mlCur = ml!!,
                            spellIds = s.selectedSpells.keys.toList(),
                        )
                    )
                }
            } else {
                safeCall {
                    api.update(
                        characterId,
                        CharacterUpdateRequest(
                            name = name, raceId = s.raceId!!,
                            cultureId = s.cultureId, religionId = s.religionId, backstoryId = s.backstoryId,
                            level = level!!, exp = exp!!, hpCur = hp!!, mlCur = ml!!,
                        )
                    )
                }
            }
            result
                .onSuccess { _state.update { it.copy(saving = false, saved = true) } }
                .onFailure { e -> _state.update { it.copy(saving = false, saveError = e.message) } }
        }
    }
}