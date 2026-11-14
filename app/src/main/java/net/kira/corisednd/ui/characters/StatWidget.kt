package net.kira.corisednd.ui.characters

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.kira.corisednd.data.AttributeValue
import net.kira.corisednd.data.SkillValue

internal const val MAX_STAT = 10

/** onChange == null: строка только для чтения. */
@Composable
internal fun StatRow(name: String, value: Int, enabled: Boolean, onChange: ((Int) -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.weight(1f))
        if (onChange == null) {
            Text("$value")
        } else {
            TextButton(onClick = { onChange(value - 1) }, enabled = enabled && value > 0) { Text("−") }
            Text("$value", Modifier.widthIn(min = 24.dp), textAlign = TextAlign.Center)
            TextButton(onClick = { onChange(value + 1) }, enabled = enabled && value < MAX_STAT) { Text("+") }
        }
    }
}

@Composable
internal fun AttributeList(
    attributes: List<AttributeValue>,
    enabled: Boolean,
    onChange: ((Long, Int) -> Unit)?,
) {
    attributes.forEach { a ->
        StatRow(a.name, a.value, enabled, onChange?.let { f -> { v: Int -> f(a.attributeId, v) } })
    }
}

/** Навыки сгруппированы по атрибутам; группа раскрывается по нажатию, в заголовке видно, сколько навыков развито. */
@Composable
internal fun SkillGroups(
    attributes: List<AttributeValue>,
    skills: List<SkillValue>,
    enabled: Boolean,
    onChange: ((Long, Int) -> Unit)?,
) {
    var opened by remember { mutableStateOf(setOf<Long>()) }
    val attributeNames = attributes.associate { it.attributeId to it.name }

    skills.groupBy { it.attributeId }.forEach { (attributeId, list) ->
        val open = attributeId in opened
        val trained = list.count { it.level > 0 }
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { opened = if (open) opened - attributeId else opened + attributeId }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                attributeNames[attributeId] ?: "Прочее",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text("$trained/${list.size}  " + if (open) "▲" else "▼")
        }
        if (open) {
            list.forEach { s ->
                StatRow(s.name, s.level, enabled, onChange?.let { f -> { v: Int -> f(s.skillId, v) } })
            }
        }
    }
}