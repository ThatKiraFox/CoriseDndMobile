package net.kira.corisednd.ui.characters

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
import androidx.compose.ui.unit.dp
import net.kira.corisednd.data.ArmourItem
import net.kira.corisednd.data.CharacterDetail
import net.kira.corisednd.data.WeaponItem
import net.kira.corisednd.data.isShield

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterDetailScreen(
    vm: CharacterDetailViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    var showSpells by remember { mutableStateOf(false) }
    var showItems by remember { mutableStateOf(false) }
    var equipSlot by remember { mutableStateOf<Slot?>(null) }

    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }

    // ошибки действий над загруженным персонажем показываем снизу
    LaunchedEffect(state.error, state.character) {
        val e = state.error
        if (e != null && state.character != null) {
            snackbar.showSnackbar(e, duration = SnackbarDuration.Long)
            vm.dismissError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(state.character?.name ?: "Персонаж") },
                navigationIcon = { TextButton(onClick = onBack) { Text("← Назад") } },
                actions = {
                    if (state.character != null) {
                        TextButton(onClick = onEdit, enabled = !state.deleting) { Text("Изменить") }
                        TextButton(
                            onClick = { confirmDelete = true },
                            enabled = !state.deleting,
                        ) { Text("Удалить") }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val c = state.character
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                c == null -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(state.error ?: "Не удалось загрузить персонажа")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = vm::load) { Text("Повторить") }
                }
                else -> DetailContent(
                    c = c,
                    busy = state.busy,
                    onRemoveSpell = vm::removeSpell,
                    onAddSpell = { showSpells = true; vm.openSpellPicker() },
                    onChangeSlot = { slot -> equipSlot = slot; vm.loadCatalog() },
                    onAmount = vm::setAmount,
                    onRemoveItem = vm::removeItem,
                    onAddItem = { showItems = true; vm.loadCatalog() },
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить персонажа?") },
            text = { Text("Персонаж «${state.character?.name}» будет удалён без возможности восстановления.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Отмена") }
            },
        )
    }

    if (showSpells) {
        val owned = state.character?.spells?.map { it.id }?.toSet() ?: emptySet()
        AlertDialog(
            onDismissRequest = { showSpells = false },
            title = { Text("Добавить заклинание") },
            text = {
                Column {
                    OutlinedTextField(
                        value = state.spellQuery,
                        onValueChange = vm::onSpellQuery,
                        label = { Text("Поиск") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    if (state.busy || state.spellsLoading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    state.dialogError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    when {
                        state.spellsError != null -> Column {
                            Text(state.spellsError ?: "", color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = vm::openSpellPicker) { Text("Повторить") }
                        }
                        state.spellResults.isEmpty() && !state.spellsLoading -> Text("Ничего не найдено")
                        else -> LazyColumn(Modifier.heightIn(max = 320.dp)) {
                            items(state.spellResults, key = { it.id }) { spell ->
                                val has = spell.id in owned
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (has || state.busy) Modifier
                                            else Modifier.clickable { vm.addSpell(spell) }
                                        )
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(spell.name)
                                        spell.type?.let {
                                            Text(it, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    Text(
                                        if (has) "есть" else "+",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
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
            confirmButton = { TextButton(onClick = { showSpells = false }) { Text("Готово") } },
        )
    }

    if (showItems) {
        val owned = state.character?.inventory?.associate { it.itemId to it.amount } ?: emptyMap()
        AddItemDialog(
            state = state,
            owned = owned,
            onAdd = vm::addToInventory,
            onRetry = vm::loadCatalog,
            onClose = { showItems = false },
        )
    }

    equipSlot?.let { slot ->
        val eq = state.character?.equipment
        val current = when (slot) {
            Slot.WEAPON -> eq?.weapon?.id
            Slot.ARMOUR -> eq?.armour?.id
            Slot.SHIELD -> eq?.shield?.id
        }
        EquipDialog(
            slot = slot,
            state = state,
            current = current,
            onPick = { id -> equipSlot = null; vm.equip(slot, id) },
            onUnequip = { equipSlot = null; vm.equip(slot, null) },
            onRetry = vm::loadCatalog,
            onClose = { equipSlot = null },
        )
    }
}

@Composable
private fun DetailContent(
    c: CharacterDetail,
    busy: Boolean,
    onRemoveSpell: (Long) -> Unit,
    onAddSpell: () -> Unit,
    onChangeSlot: (Slot) -> Unit,
    onAmount: (Long, Int) -> Unit,
    onRemoveItem: (Long) -> Unit,
    onAddItem: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

        Section("Основное") {
            Row2("Раса", c.race?.name)
            Row2("Культура", c.culture?.name)
            Row2("Религия", c.religion?.name)
            Row2("Предыстория", c.backstory?.name)
            Row2("Класс", c.classes.joinToString { it.name }.ifEmpty { null })
            Row2("Языки", c.languages.joinToString { it.name }.ifEmpty { null })
        }

        Section("Параметры") {
            Row2("Уровень", c.level.toString())
            Row2("Опыт", c.exp.toString())
            Row2("Здоровье (HP)", c.hpMax?.let { "${c.hpCur} / $it" } ?: c.hpCur.toString())
            Row2("Магическая нагрузка (ML)", c.mlMax?.let { "${c.mlCur} / $it" } ?: c.mlCur.toString())
        }

        Section("Характер и биография") {
            val p = c.personality
            if (p == null) Text("Не заполнено") else PersonalityView(p)
        }

        Section("Атрибуты") {
            AttributeList(c.attributes, enabled = false, onChange = null)
        }

        Section("Навыки") {
            SkillGroups(c.attributes, c.skills, enabled = false, onChange = null)
        }

        Section("Заклинания") {
            if (c.spells.isEmpty()) {
                Text("Нет заклинаний")
            } else {
                c.spells.forEach { spell ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("• ${spell.name}", Modifier.weight(1f))
                        TextButton(onClick = { onRemoveSpell(spell.id) }, enabled = !busy) {
                            Text("Убрать")
                        }
                    }
                }
            }
            OutlinedButton(onClick = onAddSpell, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Добавить заклинание")
            }
        }

        Section("Экипировка") {
            SlotRow("Оружие", c.equipment.weapon?.name, !busy) { onChangeSlot(Slot.WEAPON) }
            SlotRow("Броня", c.equipment.armour?.name, !busy) { onChangeSlot(Slot.ARMOUR) }
            SlotRow("Щит", c.equipment.shield?.name, !busy) { onChangeSlot(Slot.SHIELD) }
        }

        Section("Инвентарь") {
            if (c.inventory.isEmpty()) {
                Text("Пусто")
            } else {
                c.inventory.forEach { item ->
                    Column {
                        Text(item.name + (item.category?.let { " ($it)" } ?: ""))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { onAmount(item.itemId, item.amount - 1) },
                                enabled = !busy && item.amount > 1,
                            ) { Text("−") }
                            Text("${item.amount}")
                            TextButton(
                                onClick = { onAmount(item.itemId, item.amount + 1) },
                                enabled = !busy,
                            ) { Text("+") }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { onRemoveItem(item.itemId) }, enabled = !busy) {
                                Text("Убрать")
                            }
                        }
                    }
                }
            }
            OutlinedButton(onClick = onAddItem, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Добавить предмет")
            }
        }
    }
}

@Composable
private fun SlotRow(label: String, value: String?, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value ?: "—")
        }
        TextButton(onClick = onClick, enabled = enabled) { Text("Сменить") }
    }
}

// ---- диалог выбора предмета для слота ----

private data class Cand(val id: Long, val name: String, val note: String?)

@Composable
private fun EquipDialog(
    slot: Slot,
    state: DetailState,
    current: Long?,
    onPick: (Long) -> Unit,
    onUnequip: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    val inv = state.character?.inventory?.map { it.itemId }?.toSet() ?: emptySet()
    val candidates: List<Cand> = when (slot) {
        Slot.WEAPON -> state.weapons.filter { it.itemId in inv }
            .map { Cand(it.id, it.name, weaponNote(it)) }
        Slot.ARMOUR -> state.armours.filter { it.itemId in inv && !it.isShield() }
            .map { Cand(it.id, it.name, armourNote(it)) }
        Slot.SHIELD -> state.armours.filter { it.itemId in inv && it.isShield() }
            .map { Cand(it.id, it.name, armourNote(it)) }
    }
    val title = when (slot) {
        Slot.WEAPON -> "Оружие"
        Slot.ARMOUR -> "Броня"
        Slot.SHIELD -> "Щит"
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            Column {
                when {
                    state.catalogLoading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    state.catalogError != null -> Column {
                        Text(state.catalogError ?: "", color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRetry) { Text("Повторить") }
                    }
                    candidates.isEmpty() ->
                        Text("В инвентаре нет подходящих предметов. Сначала добавьте их в инвентарь.")
                    else -> LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(candidates, key = { it.id }) { cand ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(cand.id) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(cand.name)
                                    cand.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                }
                                if (cand.id == current) {
                                    Text("надето", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Закрыть") } },
        dismissButton = if (current != null) {
            { TextButton(onClick = onUnequip) { Text("Снять") } }
        } else null,
    )
}

// ---- диалог добавления предмета в инвентарь ----

@Composable
private fun AddItemDialog(
    state: DetailState,
    owned: Map<Long, Int>,
    onAdd: (Long) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val weapons = state.weapons.filter { it.name.contains(q, ignoreCase = true) }
    val armours = state.armours.filter { it.name.contains(q, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Добавить предмет") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Поиск") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                if (state.busy || state.catalogLoading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                state.dialogError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                when {
                    state.catalogError != null -> Column {
                        Text(state.catalogError ?: "", color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRetry) { Text("Повторить") }
                    }
                    state.weapons.isEmpty() && state.armours.isEmpty() && !state.catalogLoading ->
                        Text("Каталог пуст. Создайте оружие или броню (например, через Swagger).")
                    weapons.isEmpty() && armours.isEmpty() && !state.catalogLoading ->
                        Text("Ничего не найдено")
                    else -> LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        if (weapons.isNotEmpty()) {
                            item {
                                Text(
                                    "Оружие",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                            items(weapons, key = { "w${it.itemId}" }) { w ->
                                ItemRow(w.name, weaponNote(w), owned[w.itemId], !state.busy) { onAdd(w.itemId) }
                            }
                        }
                        if (armours.isNotEmpty()) {
                            item {
                                Text(
                                    "Броня и щиты",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                            items(armours, key = { "a${it.itemId}" }) { a ->
                                ItemRow(a.name, armourNote(a), owned[a.itemId], !state.busy) { onAdd(a.itemId) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Готово") } },
    )
}

@Composable
private fun ItemRow(name: String, note: String?, amount: Int?, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (amount == null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name)
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        Text(
            if (amount != null) "есть ×$amount" else "+",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun gripText(grip: String?): String? = when (grip) {
    "one_handed" -> "одноручное"
    "two_handed" -> "двуручное"
    "polearm" -> "древковое"
    else -> grip
}

private fun weaponNote(w: WeaponItem): String? =
    listOfNotNull(gripText(w.grip), w.damage).joinToString(" · ").ifEmpty { null }

private fun armourNote(a: ArmourItem): String? =
    listOfNotNull(
        a.armourType,
        a.damageMitigation?.let { "защита ${it.toString().removeSuffix(".0")}" },
    ).joinToString(" · ").ifEmpty { null }

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Row2(label: String, value: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value ?: "—")
    }
}