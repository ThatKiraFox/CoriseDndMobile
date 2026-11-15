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
import net.kira.corisednd.data.AttributeRef
import net.kira.corisednd.data.CharacterCreateRequest
import net.kira.corisednd.data.CharactersApi
import net.kira.corisednd.data.NamedRef
import net.kira.corisednd.data.PersonalityDto
import net.kira.corisednd.data.RaceItem
import net.kira.corisednd.data.SkillRef
import net.kira.corisednd.data.SpellItem
import net.kira.corisednd.data.StatInput
import net.kira.corisednd.data.safeCall

// Правила создания персонажа 1 уровня. Проверяет их сервер; здесь они нужны только для счётчика очков.
internal const val WIZARD_STEPS = 5
internal const val ATTRIBUTE_POINTS = 27
internal const val SKILL_POINTS = 35
internal const val MAX_START_STAT = 5

/** Повышение атрибута с уровня n на n+1 стоит n+1, итого v * (v + 1) / 2. */
internal fun attributeCost(value: Int): Int = value * (value + 1) / 2

data class WizardState(
    val step: Int = 0,
    val loading: Boolean = true,
    val loadError: String? = null,
    val saving: Boolean = false,
    val saveError: String? = null,
    val created: Boolean = false,

    val races: List<RaceItem> = emptyList(),
    val cultures: List<NamedRef> = emptyList(),
    val religions: List<NamedRef> = emptyList(),
    val backstories: List<NamedRef> = emptyList(),
    val classes: List<NamedRef> = emptyList(),
    val attributeDefs: List<AttributeRef> = emptyList(),
    val skillDefs: List<SkillRef> = emptyList(),

    val name: String = "",
    val raceId: Long? = null,
    val cultureId: Long? = null,
    val religionId: Long? = null,
    val backstoryId: Long? = null,
    val classId: Long? = null,
    val personality: PersonalityDto = PersonalityDto(),
    val attributes: Map<Long, Int> = emptyMap(),
    val skills: Map<Long, Int> = emptyMap(),

    val selectedSpells: Map<Long, String> = emptyMap(),
    val spellQuery: String = "",
    val spellResults: List<SpellItem> = emptyList(),
    val spellsLoading: Boolean = false,
    val spellsError: String? = null,

    val errors: Map<String, String> = emptyMap(),


) {
    val attributePointsLeft: Int
        get() = ATTRIBUTE_POINTS - attributes.values.sumOf { attributeCost(it) }
    val skillPointsLeft: Int
        get() = SKILL_POINTS - skills.values.sum()

    val canGoNext: Boolean
        get() = when (step) {
            2 -> attributePointsLeft == 0
            3 -> skillPointsLeft == 0
            else -> true
        }
}

class CharacterWizardViewModel(container: AppContainer) : ViewModel() {

    private val api: CharactersApi = container.retrofit.create(CharactersApi::class.java)

    private val _state = MutableStateFlow(WizardState())
    val state: StateFlow<WizardState> = _state

    private var spellJob: Job? = null

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            val races = safeCall { api.races() }
            val cultures = safeCall { api.cultures() }
            val religions = safeCall { api.religions() }
            val backstories = safeCall { api.backstories() }
            val classes = safeCall { api.classes() }
            val attributes = safeCall { api.attributes() }
            val skills = safeCall { api.skills() }

            val failure = listOf<Result<*>>(races, cultures, religions, backstories, classes, attributes, skills)
                .firstOrNull { it.isFailure }
            if (failure != null) {
                _state.update { it.copy(loading = false, loadError = failure.exceptionOrNull()?.message) }
                return@launch
            }
            _state.update {
                it.copy(
                    loading = false,
                    races = races.getOrThrow(),
                    cultures = cultures.getOrThrow(),
                    religions = religions.getOrThrow(),
                    backstories = backstories.getOrThrow(),
                    classes = classes.getOrThrow(),
                    attributeDefs = attributes.getOrThrow(),
                    skillDefs = skills.getOrThrow(),
                )
            }
        }
    }

    // ---- шаг 1: основное ----

    fun setName(v: String) = _state.update { it.copy(name = v.take(100), errors = it.errors - "name") }
    fun setRace(id: Long?) = _state.update { it.copy(raceId = id, errors = it.errors - "race") }
    fun setCulture(id: Long?) = _state.update { it.copy(cultureId = id) }
    fun setReligion(id: Long?) = _state.update { it.copy(religionId = id) }
    fun setBackstory(id: Long?) = _state.update { it.copy(backstoryId = id) }
    fun setClass(id: Long?) = _state.update { it.copy(classId = id, errors = it.errors - "class") }

    // ---- шаг 2: характер ----

    fun setPersonality(p: PersonalityDto) = _state.update { it.copy(personality = p) }

    // ---- шаги 3 и 4: очки ----

    fun changeAttribute(id: Long, delta: Int) = _state.update { s ->
        val next = (s.attributes[id] ?: 0) + delta
        // повышение до уровня next стоит next очков
        val allowed = next in 0..MAX_START_STAT && (delta <= 0 || s.attributePointsLeft >= next)
        if (allowed) s.copy(attributes = s.attributes + (id to next)) else s
    }

    fun changeSkill(id: Long, delta: Int) = _state.update { s ->
        val next = (s.skills[id] ?: 0) + delta
        val allowed = next in 0..MAX_START_STAT && (delta <= 0 || s.skillPointsLeft >= 1)
        if (allowed) s.copy(skills = s.skills + (id to next)) else s
    }

    // ---- шаг 5: заклинания ----

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

    // ---- навигация по шагам ----

    fun next() {
        val s = _state.value
        if (!s.canGoNext) return
        if (s.step == 0) {
            val errors = mutableMapOf<String, String>()
            if (s.name.trim().isEmpty()) errors["name"] = "Введите имя"
            if (s.raceId == null) errors["race"] = "Выберите расу"
            if (s.classes.isNotEmpty() && s.classId == null) errors["class"] = "Выберите класс"
            if (errors.isNotEmpty()) {
                _state.update { it.copy(errors = errors) }
                return
            }
        }
        if (s.step < WIZARD_STEPS - 1) _state.update { it.copy(step = it.step + 1, saveError = null) }
    }

    fun back() {
        _state.update { if (it.step > 0) it.copy(step = it.step - 1, saveError = null) else it }
    }

    // ---- создание ----

    fun create() {
        val s = _state.value
        if (s.saving) return
        val raceId = s.raceId ?: return
        _state.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            safeCall {
                api.create(
                    CharacterCreateRequest(
                        name = s.name.trim(), raceId = raceId,
                        cultureId = s.cultureId, religionId = s.religionId, backstoryId = s.backstoryId,
                        level = 1, exp = 0, hpCur = 0, mlCur = 0,
                        spellIds = s.selectedSpells.keys.toList(),
                        classId = s.classId,
                        personality = s.personality,
                        attributes = s.attributes.filter { it.value > 0 }.map { StatInput(it.key, it.value) },
                        skills = s.skills.filter { it.value > 0 }.map { StatInput(it.key, it.value) },
                    )
                )
            }
                .onSuccess { _state.update { it.copy(saving = false, created = true) } }
                .onFailure { e -> _state.update { it.copy(saving = false, saveError = e.message) } }
        }
    }
}