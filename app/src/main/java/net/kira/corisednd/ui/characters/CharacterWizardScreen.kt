package net.kira.corisednd.ui.characters

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val STEP_TITLES = listOf("Основное", "Характер и биография", "Атрибуты", "Навыки", "Заклинания")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterWizardScreen(
    vm: CharacterWizardViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val state by vm.state.collectAsState()
    var showSpells by remember { mutableStateOf(false) }

    LaunchedEffect(state.created) { if (state.created) onSaved() }
    BackHandler(enabled = state.step > 0) { vm.back() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "${state.step + 1}/$WIZARD_STEPS: ${STEP_TITLES[state.step]}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = { if (state.step > 0) vm.back() else onBack() }) { Text("← Назад") }
                },
            )
        },
        bottomBar = {
            if (!state.loading && state.loadError == null) {
                Column(Modifier.navigationBarsPadding()) {
                    LinearProgressIndicator(
                        progress = { (state.step + 1) / WIZARD_STEPS.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    state.saveError?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (state.step > 0) {
                            OutlinedButton(onClick = vm::back, modifier = Modifier.weight(1f)) { Text("Назад") }
                        }
                        if (state.step < WIZARD_STEPS - 1) {
                            Button(
                                onClick = vm::next,
                                enabled = state.canGoNext,
                                modifier = Modifier.weight(1f),
                            ) { Text("Далее") }
                        } else {
                            Button(
                                onClick = vm::create,
                                enabled = !state.saving,
                                modifier = Modifier.weight(1f),
                            ) {
                                if (state.saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Text("Создать")
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.loadError != null -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(state.loadError ?: "", textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = vm::load) { Text("Повторить") }
                }
                else -> when (state.step) {
                    0 -> BasicStep(state, vm)
                    1 -> PersonalityStep(state, vm)
                    2 -> AttributesStep(state, vm)
                    3 -> SkillsStep(state, vm)
                    else -> SpellsStep(state, vm) { showSpells = true; vm.openSpells() }
                }
            }
        }
    }

    if (showSpells) SpellDialog(state, vm) { showSpells = false }
}

@Composable
private fun BasicStep(s: WizardState, vm: CharacterWizardViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = s.name,
            onValueChange = vm::setName,
            label = { Text("Имя") },
            singleLine = true,
            isError = "name" in s.errors,
            supportingText = s.errors["name"]?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        Picker(
            label = "Раса", items = s.races, selectedId = s.raceId,
            idOf = { it.id }, nameOf = { it.outName },
            optional = false, error = s.errors["race"], onSelect = vm::setRace,
        )
        Picker(
            label = "Культура", items = s.cultures, selectedId = s.cultureId,
            idOf = { it.id }, nameOf = { it.name },
            optional = true, onSelect = vm::setCulture,
        )
        Text(
            "Язык культуры персонаж знает по умолчанию",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Picker(
            label = "Религия", items = s.religions, selectedId = s.religionId,
            idOf = { it.id }, nameOf = { it.name },
            optional = true, onSelect = vm::setReligion,
        )
        Picker(
            label = "Предыстория", items = s.backstories, selectedId = s.backstoryId,
            idOf = { it.id }, nameOf = { it.name },
            optional = true, onSelect = vm::setBackstory,
        )
        if (s.classes.isNotEmpty()) {
            Picker(
                label = "Класс", items = s.classes, selectedId = s.classId,
                idOf = { it.id }, nameOf = { it.name },
                optional = false, error = s.errors["class"], onSelect = vm::setClass,
            )
        }
    }
}

@Composable
private fun PersonalityStep(s: WizardState, vm: CharacterWizardViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Всё необязательно, можно заполнить позже",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PersonalityFields(s.personality, vm::setPersonality)
    }
}

@Composable
private fun AttributesStep(s: WizardState, vm: CharacterWizardViewModel) {
    Column(Modifier.fillMaxSize()) {
        PointsHeader(
            left = s.attributePointsLeft,
            total = ATTRIBUTE_POINTS,
            note = "Максимальный уровень $MAX_START_STAT. Уровень стоит свой номер: 1, 2, 3, 4, 5 очков",
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            s.attributeDefs.forEach { a ->
                val v = s.attributes[a.id] ?: 0
                StepperRow(
                    title = a.name,
                    subtitle = if (v < MAX_START_STAT) "Следующий уровень: ${v + 1} оч." else "Максимум",
                    value = v,
                    canDec = v > 0,
                    canInc = v < MAX_START_STAT && s.attributePointsLeft >= v + 1,
                    onDec = { vm.changeAttribute(a.id, -1) },
                    onInc = { vm.changeAttribute(a.id, 1) },
                )
            }
        }
    }
}

@Composable
private fun SkillsStep(s: WizardState, vm: CharacterWizardViewModel) {
    var opened by remember { mutableStateOf(setOf<Long>()) }
    val grouped = s.skillDefs.groupBy { it.attributeId }

    Column(Modifier.fillMaxSize()) {
        PointsHeader(
            left = s.skillPointsLeft,
            total = SKILL_POINTS,
            note = "Максимальный уровень $MAX_START_STAT, каждый уровень стоит 1 очко",
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            s.attributeDefs.forEach { a ->
                val list = grouped[a.id] ?: return@forEach
                val open = a.id in opened
                val spent = list.sumOf { s.skills[it.id] ?: 0 }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { opened = if (open) opened - a.id else opened + a.id }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(a.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text("$spent  " + if (open) "▲" else "▼")
                }
                if (open) {
                    list.forEach { sk ->
                        val v = s.skills[sk.id] ?: 0
                        StepperRow(
                            title = sk.name,
                            subtitle = null,
                            value = v,
                            canDec = v > 0,
                            canInc = v < MAX_START_STAT && s.skillPointsLeft >= 1,
                            onDec = { vm.changeSkill(sk.id, -1) },
                            onInc = { vm.changeSkill(sk.id, 1) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpellsStep(s: WizardState, vm: CharacterWizardViewModel, onPick: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Заклинания (необязательно)", style = MaterialTheme.typography.titleMedium)
        if (s.selectedSpells.isEmpty()) {
            Text("Не выбраны", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            s.selectedSpells.forEach { (id, name) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("• $name", Modifier.weight(1f))
                    TextButton(onClick = { vm.removeSpell(id) }) { Text("Убрать") }
                }
            }
        }
        OutlinedButton(onClick = onPick, modifier = Modifier.fillMaxWidth()) { Text("Выбрать заклинания") }
        Text(
            "Здоровье при создании равно максимальному, магическая нагрузка равна нулю",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SpellDialog(s: WizardState, vm: CharacterWizardViewModel, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Заклинания") },
        text = {
            Column {
                OutlinedTextField(
                    value = s.spellQuery,
                    onValueChange = vm::onSpellQuery,
                    label = { Text("Поиск") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                when {
                    s.spellsLoading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    s.spellsError != null -> Column {
                        Text(s.spellsError ?: "", color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = vm::openSpells) { Text("Повторить") }
                    }
                    s.spellResults.isEmpty() -> Text("Ничего не найдено")
                    else -> LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(s.spellResults, key = { it.id }) { spell ->
                            Row(
                                Modifier.fillMaxWidth().clickable { vm.toggleSpell(spell) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = spell.id in s.selectedSpells,
                                    onCheckedChange = { vm.toggleSpell(spell) },
                                )
                                Column {
                                    Text(spell.name)
                                    spell.type?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                        }
                    }
                }
                Text(
                    "Показаны первые 50, уточните поиск",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Готово") } },
    )
}

@Composable
private fun PointsHeader(left: Int, total: Int, note: String) {
    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (left == 0) "Все очки потрачены" else "Осталось очков: $left из $total",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (left > 0) {
                Text(
                    "Чтобы продолжить, потратьте все очки",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun StepperRow(
    title: String,
    subtitle: String?,
    value: Int,
    canDec: Boolean,
    canInc: Boolean,
    onDec: () -> Unit,
    onInc: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = onDec, enabled = canDec) { Text("−") }
        Text("$value", Modifier.widthIn(min = 24.dp), textAlign = TextAlign.Center)
        TextButton(onClick = onInc, enabled = canInc) { Text("+") }
    }
}