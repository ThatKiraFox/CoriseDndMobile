package net.kira.corisednd.ui.reference

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kira.corisednd.ui.characters.Picker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentFormScreen(vm: ContentFormViewModel, onBack: () -> Unit, onSaved: () -> Unit) {
    val state by vm.state.collectAsState()

    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        (if (vm.isEdit) "Изменить: " else "Новое: ") + vm.section.title,
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
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    vm.fields.forEach { f ->
                        val value = state.values[f.key].orEmpty()
                        val error = state.errors[f.key]
                        if (f.kind == FieldKind.GRIP) {
                            Picker(
                                label = f.label,
                                items = GRIPS,
                                selectedId = GRIPS.indexOf(value).takeIf { it >= 0 }?.toLong(),
                                idOf = { GRIPS.indexOf(it).toLong() },
                                nameOf = { displayValue("grip", it) },
                                optional = false,
                                onSelect = { i -> if (i != null) vm.setValue(f.key, GRIPS[i.toInt()]) },
                            )
                        } else {
                            val long = f.kind == FieldKind.LONG_TEXT
                            OutlinedTextField(
                                value = value,
                                onValueChange = { vm.setValue(f.key, it) },
                                label = { Text(f.label + if (f.required) " *" else "") },
                                singleLine = !long,
                                minLines = if (long) 3 else 1,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = if (f.kind == FieldKind.INT) KeyboardType.Number else KeyboardType.Text,
                                ),
                                isError = error != null,
                                supportingText = error?.let { { Text(it) } },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

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