package net.kira.corisednd.ui.characters

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val SORTS = listOf("name" to "Имя", "level" to "Уровень", "id" to "Дата создания")

@Composable
private fun <T> Picker(
    current: String,
    options: List<Pair<T, String>>,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(current, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { open = false; onPick(value) })
            }
        }
    }
}

@Composable
fun CharactersScreen(vm: CharactersViewModel,
                     onLogout: () -> Unit,
                     onOpen: (Long) -> Unit,
                     onCreate: () -> Unit,
                     onReference: () -> Unit,
                     ) {
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, vm.items.size) {
        if (nearEnd && vm.items.isNotEmpty()) vm.loadMore()
    }
    LaunchedEffect(vm.search, vm.raceId, vm.sort, vm.desc) { listState.scrollToItem(0) }

    val raceOptions = listOf<Pair<Long?, String>>(null to "Все расы") + vm.races.map { it.id to it.outName }
    val raceLabel = vm.races.firstOrNull { it.id == vm.raceId }?.outName ?: "Все расы"
    val sortLabel = SORTS.first { it.first == vm.sort }.second
    val err = vm.error
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Персонажи", style = MaterialTheme.typography.headlineSmall)
                Row {
                    TextButton(onClick = onReference) { Text("Справочник") }
                    TextButton(onClick = onLogout) { Text("Выйти") }
                }
            }

            OutlinedTextField(
                value = vm.search,
                onValueChange = vm::onSearch,
                label = { Text("Поиск по имени") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Picker(raceLabel, raceOptions, vm::onRace, Modifier.weight(1f))
                Picker(sortLabel, SORTS, vm::onSort, Modifier.weight(1f))
                TextButton(onClick = vm::toggleOrder) { Text(if (vm.desc) "▼" else "▲") }
            }
            if (vm.items.isNotEmpty()) {
                Text("Найдено: ${vm.total}", style = MaterialTheme.typography.bodySmall)
            }
            if (vm.loading && vm.items.isNotEmpty()) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    vm.loading && vm.items.isEmpty() ->
                        CircularProgressIndicator(Modifier.align(Alignment.Center))

                    err != null && vm.items.isEmpty() ->
                        Column(
                            Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                err,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { vm.reload() }) { Text("Повторить") }
                        }

                    vm.items.isEmpty() ->
                        Text(
                            if (vm.hasFilters) "Ничего не найдено" else "У вас пока нет персонажей",
                            modifier = Modifier.align(Alignment.Center),
                        )

                    else -> LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(vm.items, key = { it.id }) { c ->
                            Card(Modifier.fillMaxWidth().clickable { onOpen(c.id) }) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(c.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${c.race?.name ?: "—"} · уровень ${c.level}",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                        item {
                            when {
                                vm.loadingMore -> Box(
                                    Modifier.fillMaxWidth().padding(8.dp),
                                    Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                }

                                err != null -> Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        err,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = vm::loadMore) { Text("Повторить") }
                                }
                            }
                        }
                    }
                }
            }

        }
        FloatingActionButton(
            onClick = onCreate,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Text("+") }
    }
}