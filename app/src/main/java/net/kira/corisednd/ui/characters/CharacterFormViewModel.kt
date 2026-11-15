package net.kira.corisednd.ui.characters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.kira.corisednd.data.AppContainer
import net.kira.corisednd.data.AttributeValue
import net.kira.corisednd.data.CharacterUpdateRequest
import net.kira.corisednd.data.CharactersApi
import net.kira.corisednd.data.NamedRef
import net.kira.corisednd.data.PersonalityDto
import net.kira.corisednd.data.RaceItem
import net.kira.corisednd.data.SetValueRequest
import net.kira.corisednd.data.SkillValue
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
    val hpMax: Int? = null,
    val ml: String = "0",
    val mlMax: Int? = null,

    val races: List<RaceItem> = emptyList(),
    val cultures: List<NamedRef> = emptyList(),
    val religions: List<NamedRef> = emptyList(),
    val backstories: List<NamedRef> = emptyList(),

    val attributes: List<AttributeValue> = emptyList(),
    val skills: List<SkillValue> = emptyList(),
    val statBusy: Boolean = false,
    val statError: String? = null,
    val personality: PersonalityDto = PersonalityDto(),

    val errors: Map<String, String> = emptyMap(),
)

/** Правка существующего персонажа. Создание идёт через мастер (CharacterWizardViewModel). */
class CharacterFormViewModel(
    container: AppContainer,
    private val characterId: Long,
) : ViewModel() {

    private val api: CharactersApi = container.retrofit.create(CharactersApi::class.java)

    private val _state = MutableStateFlow(FormState())
    val state: StateFlow<FormState> = _state

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            val races = safeCall { api.races() }
            val cultures = safeCall { api.cultures() }
            val religions = safeCall { api.religions() }
            val backstories = safeCall { api.backstories() }
            val detail = safeCall { api.get(characterId) }

            val failure = listOf<Result<*>>(races, cultures, religions, backstories, detail)
                .firstOrNull { it.isFailure }
            if (failure != null) {
                _state.update { it.copy(loading = false, loadError = failure.exceptionOrNull()?.message) }
                return@launch
            }

            val c = detail.getOrThrow()
            _state.update { s ->
                s.copy(
                    loading = false,
                    races = races.getOrThrow(),
                    cultures = cultures.getOrThrow(),
                    religions = religions.getOrThrow(),
                    backstories = backstories.getOrThrow(),
                    name = c.name,
                    raceId = c.race?.id,
                    cultureId = c.culture?.id,
                    religionId = c.religion?.id,
                    backstoryId = c.backstory?.id,
                    level = c.level.toString(),
                    exp = c.exp.toString(),
                    hp = c.hpCur.toString(),
                    hpMax = c.hpMax,
                    ml = c.mlCur.toString(),
                    mlMax = c.mlMax,
                    personality = c.personality ?: s.personality,
                    attributes = c.attributes,
                    skills = c.skills,
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
    fun setPersonality(p: PersonalityDto) = _state.update { it.copy(personality = p) }

    // ---- атрибуты и навыки (применяются сразу) ----

    fun setAttribute(attributeId: Long, value: Int) {
        if (_state.value.statBusy || value !in 0..10) return
        _state.update { it.copy(statBusy = true, statError = null) }
        viewModelScope.launch {
            safeCall { api.setAttribute(characterId, attributeId, SetValueRequest(value)) }
                .onSuccess { reloadStats() }
                .onFailure { e -> _state.update { it.copy(statBusy = false, statError = e.message) } }
        }
    }

    fun setSkill(skillId: Long, level: Int) {
        if (_state.value.statBusy || level !in 0..10) return
        _state.update { it.copy(statBusy = true, statError = null) }
        viewModelScope.launch {
            safeCall { api.setSkill(characterId, skillId, SetValueRequest(level)) }
                .onSuccess { reloadStats() }
                .onFailure { e -> _state.update { it.copy(statBusy = false, statError = e.message) } }
        }
    }

    /** Обновляет только атрибуты и навыки: несохранённые правки в полях формы не затираются. */
    private suspend fun reloadStats() {
        safeCall { api.get(characterId) }
            .onSuccess { c ->
                _state.update {
                    it.copy(
                        statBusy = false, attributes = c.attributes, skills = c.skills,
                        hpMax = c.hpMax, mlMax = c.mlMax,
                    )
                }
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
            safeCall {
                api.update(
                    characterId,
                    CharacterUpdateRequest(
                        name = name, raceId = s.raceId!!,
                        cultureId = s.cultureId, religionId = s.religionId, backstoryId = s.backstoryId,
                        level = level!!, exp = exp!!, hpCur = hp!!, mlCur = ml!!,
                    )
                )
                api.setPersonality(characterId, s.personality)
            }
                .onSuccess { _state.update { it.copy(saving = false, saved = true) } }
                .onFailure { e -> _state.update { it.copy(saving = false, saveError = e.message) } }
        }
    }
}