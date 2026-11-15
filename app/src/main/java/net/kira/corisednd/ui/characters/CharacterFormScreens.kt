package net.kira.corisednd.ui.characters

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** Правка существующего персонажа. Создание идёт через мастер (CharacterWizardScreen). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterFormScreen(
    vm: CharacterFormViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val state by vm.state.collectAsState()

    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Редактирование") },
                navigationIcon = { TextButton(onClick = onBack) { Text("← Назад") } },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.loadError != null -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(state.loadError ?: "")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = vm::load) { Text("Повторить") }
                }
                else -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = vm::setName,
                        label = { Text("Имя") },
                        singleLine = true,
                        isError = "name" in state.errors,
                        supportingText = state.errors["name"]?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Picker(
                        label = "Раса", items = state.races, selectedId = state.raceId,
                        idOf = { it.id }, nameOf = { it.outName },
                        optional = false, error = state.errors["race"], onSelect = vm::setRace,
                    )
                    Picker(
                        label = "Культура", items = state.cultures, selectedId = state.cultureId,
                        idOf = { it.id }, nameOf = { it.name },
                        optional = true, onSelect = vm::setCulture,
                    )
                    Picker(
                        label = "Религия", items = state.religions, selectedId = state.religionId,
                        idOf = { it.id }, nameOf = { it.name },
                        optional = true, onSelect = vm::setReligion,
                    )
                    Picker(
                        label = "Предыстория", items = state.backstories, selectedId = state.backstoryId,
                        idOf = { it.id }, nameOf = { it.name },
                        optional = true, onSelect = vm::setBackstory,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumberField("Уровень", state.level, state.errors["level"], vm::setLevel, Modifier.weight(1f))
                        NumberField("Опыт", state.exp, state.errors["exp"], vm::setExp, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumberField(
                            "Здоровье (HP)", state.hp, state.errors["hp"], vm::setHp, Modifier.weight(1f),
                            hint = state.hpMax?.let { "Максимум: $it" },
                        )
                        NumberField(
                            "Нагрузка (ML)", state.ml, state.errors["ml"], vm::setMl, Modifier.weight(1f),
                            hint = state.mlMax?.let { "Максимум: $it" },
                        )
                    }

                    Text("Атрибуты", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Значения от 0 до 10, применяются сразу",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.statBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    AttributeList(state.attributes, !state.statBusy, vm::setAttribute)

                    Text("Навыки", style = MaterialTheme.typography.titleMedium)
                    SkillGroups(state.attributes, state.skills, !state.statBusy, vm::setSkill)

                    state.statError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                    Text("Характер и биография", style = MaterialTheme.typography.titleMedium)
                    PersonalityFields(state.personality, vm::setPersonality)

                    state.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                    Button(
                        onClick = vm::save,
                        enabled = !state.saving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("Сохранить")
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    error: String?,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    val supporting = error ?: hint
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = error != null,
        supportingText = supporting?.let { { Text(it) } },
        modifier = modifier,
    )
}

@Composable
internal fun <T> Picker(
    label: String,
    items: List<T>,
    selectedId: Long?,
    idOf: (T) -> Long,
    nameOf: (T) -> String,
    optional: Boolean,
    error: String? = null,
    onSelect: (Long?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = items.firstOrNull { idOf(it) == selectedId }?.let(nameOf)

    Column {
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text("$label: ${current ?: "не выбрано"}", modifier = Modifier.weight(1f))
                Text("▾")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (optional) {
                    DropdownMenuItem(
                        text = { Text("Не выбрано") },
                        onClick = { onSelect(null); expanded = false },
                    )
                }
                items.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(nameOf(item)) },
                        onClick = { onSelect(idOf(item)); expanded = false },
                    )
                }
            }
        }
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}