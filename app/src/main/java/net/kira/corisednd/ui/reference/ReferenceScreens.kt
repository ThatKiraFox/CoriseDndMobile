package net.kira.corisednd.ui.reference

import com.mikepenz.markdown.m3.Markdown
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferenceHomeScreen(onBack: () -> Unit, onOpen: (Section) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Справочник") },
                navigationIcon = { TextButton(onClick = onBack) { Text("← Назад") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(Section.entries) { s ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(s) }) {
                    Text(s.title, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferenceListScreen(vm: ReferenceListViewModel, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val section = vm.section
    val list = vm.visible

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(section.title) },
                navigationIcon = { TextButton(onClick = onBack) { Text("← Назад") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = vm.search,
                onValueChange = vm::onSearch,
                label = { Text("Поиск по названию") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            if (vm.loading && vm.items.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    vm.loading && vm.items.isEmpty() ->
                        CircularProgressIndicator(Modifier.align(Alignment.Center))

                    vm.error != null -> Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(vm.error ?: "", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { vm.load() }) { Text("Повторить") }
                    }

                    list.isEmpty() -> Text(
                        if (vm.search.isBlank()) "Пока пусто" else "Ничего не найдено",
                        Modifier.align(Alignment.Center),
                    )

                    else -> LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        var prevGroup = "\u0000"
                        list.forEach { e ->
                            val group = e.subtitle ?: ""
                            if (section.grouped && group != prevGroup) {
                                prevGroup = group
                                item(key = "h-$group") {
                                    Text(
                                        group.ifEmpty { "Без категории" },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                }
                            }
                            item(key = e.id) {
                                EntryCard(e, showSubtitle = !section.grouped) { onOpen(e.id) }
                            }
                        }
                        if (vm.hasMore) {
                            item {
                                Text(
                                    "Показаны первые 100, уточните поиск",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryCard(e: RefEntry, showSubtitle: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Text(e.title, style = MaterialTheme.typography.titleMedium)
            if (showSubtitle) {
                e.subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferenceDetailScreen(vm: ReferenceDetailViewModel, onBack: () -> Unit) {
    val section = vm.section
    val obj = vm.entry

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        obj?.text(section.titleKey) ?: section.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { TextButton(onClick = onBack) { Text("← Назад") } },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                vm.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                obj == null -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(vm.error ?: "Не удалось загрузить", textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = vm::load) { Text("Повторить") }
                }

                else -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    section.fields.forEach { f ->
                        val value = obj.text(f.key) ?: return@forEach
                        Column {
                            if (f.label.isNotEmpty()) {
                                Text(
                                    f.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (f.markdown) Markdown(value) else Text(displayValue(f.key, value))
                        }
                    }
                }
            }
        }
    }
}