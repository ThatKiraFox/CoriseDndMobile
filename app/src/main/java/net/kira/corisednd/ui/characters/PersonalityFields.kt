package net.kira.corisednd.ui.characters

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.kira.corisednd.data.PersonalityDto

private const val MAX_TEXT = 5000

@Composable
internal fun PersonalityFields(p: PersonalityDto, onChange: (PersonalityDto) -> Unit) {
    PersonalityField("Цель", "Что преследует персонаж в приключении?", p.goal) {
        onChange(p.copy(goal = it))
    }
    PersonalityField("Особенности", "Особенности внешности, речи, взгляда на мир", p.uniqueTraits) {
        onChange(p.copy(uniqueTraits = it))
    }
    PersonalityField("Ценности", "Что персонаж ценит: вещи, отношения, принципы", p.personalValues) {
        onChange(p.copy(personalValues = it))
    }
    PersonalityField("Зло для персонажа", "Противоположность ценностям", p.personalEvils) {
        onChange(p.copy(personalEvils = it))
    }
    PersonalityField("Черты характера", "Сильнейшие положительные и отрицательные черты", p.personalityTraits) {
        onChange(p.copy(personalityTraits = it))
    }
    PersonalityField("Биография", "Жизнь персонажа до начала путешествия", p.biography, minLines = 4) {
        onChange(p.copy(biography = it))
    }
}

@Composable
private fun PersonalityField(
    label: String,
    hint: String,
    value: String,
    minLines: Int = 2,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(MAX_TEXT)) },
        label = { Text(label) },
        supportingText = { Text(hint) },
        minLines = minLines,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Только чтение: показывает заполненные поля. */
@Composable
internal fun PersonalityView(p: PersonalityDto) {
    val items = listOf(
        "Цель" to p.goal,
        "Особенности" to p.uniqueTraits,
        "Ценности" to p.personalValues,
        "Зло для персонажа" to p.personalEvils,
        "Черты характера" to p.personalityTraits,
        "Биография" to p.biography,
    ).filter { it.second.isNotBlank() }

    if (items.isEmpty()) {
        Text("Не заполнено")
    } else {
        items.forEach { (label, text) ->
            Column {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text)
            }
        }
    }
}