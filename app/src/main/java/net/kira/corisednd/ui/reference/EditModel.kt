package net.kira.corisednd.ui.reference

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class FieldKind { TEXT, LONG_TEXT, INT, GRIP }

class EditField(
    val key: String,
    val label: String,
    val kind: FieldKind = FieldKind.TEXT,
    val required: Boolean = false,
)

val GRIPS = listOf("one_handed", "two_handed", "polearm")

private val NAMED = listOf(
    EditField("name", "Название", required = true),
    EditField("description", "Описание", FieldKind.LONG_TEXT),
)

/** Поля формы раздела; null: раздел нельзя создавать (лор). */
fun Section.editFields(): List<EditField>? = when (this) {
    Section.RACES -> listOf(
        EditField("outName", "Название (для игроков)", required = true),
        EditField("inName", "Название внутри мира", required = true),
        EditField("shortDesc", "Кратко", FieldKind.LONG_TEXT),
        EditField("appearance", "Внешность", FieldKind.LONG_TEXT),
        EditField("lore", "История", FieldKind.LONG_TEXT),
        EditField("relationships", "Отношения с другими", FieldKind.LONG_TEXT),
        EditField("status", "Статус"),
        EditField("archetype", "Архетип"),
        EditField("lifespan", "Продолжительность жизни (лет)", FieldKind.INT),
    )
    Section.CULTURES, Section.RELIGIONS, Section.BACKSTORIES -> NAMED
    Section.CLASSES -> listOf(
        EditField("name", "Название", required = true),
        EditField("socialStatus", "Социальный статус", FieldKind.INT),
        EditField("description", "Описание", FieldKind.LONG_TEXT),
        EditField("primarySkills", "Основные навыки", FieldKind.LONG_TEXT),
    )
    Section.SPELLS -> listOf(
        EditField("name", "Название", required = true),
        EditField("time", "Время сотворения"),
        EditField("type", "Тип"),
        EditField("distance", "Дистанция", FieldKind.INT),
        EditField("cost", "Стоимость", FieldKind.INT),
        EditField("effect", "Эффект", FieldKind.LONG_TEXT),
        EditField("description", "Описание", FieldKind.LONG_TEXT),
    )
    Section.WEAPONS -> listOf(
        EditField("name", "Название", required = true),
        EditField("damage", "Урон (например, 1д8)", required = true),
        EditField("damageType", "Тип урона", required = true),
        EditField("grip", "Хват", FieldKind.GRIP),
        EditField("weight", "Вес", FieldKind.INT),
        EditField("value", "Стоимость", FieldKind.INT),
        EditField("description", "Описание", FieldKind.LONG_TEXT),
    )
    Section.ARMOURS -> listOf(
        EditField("name", "Название", required = true),
        EditField("armourType", "Тип (для щита: shield или щит)", required = true),
        EditField("damageMitigation", "Защита", FieldKind.INT),
        EditField("weight", "Вес", FieldKind.INT),
        EditField("value", "Стоимость", FieldKind.INT),
        EditField("requirements", "Требования", FieldKind.LONG_TEXT),
        EditField("disadvantages", "Недостатки", FieldKind.LONG_TEXT),
        EditField("resistance", "Сопротивления", FieldKind.LONG_TEXT),
        EditField("vulnerability", "Уязвимости", FieldKind.LONG_TEXT),
        EditField("description", "Описание", FieldKind.LONG_TEXT),
    )
    Section.LORE -> null
}

/**
 * Тело запроса. При правке за основу берётся загруженная запись: поля, которых нет в форме
 * (например, навык оружия), сохраняют прежнее значение. Лишние ключи сервер игнорирует.
 */
fun buildContentBody(
    section: Section,
    fields: List<EditField>,
    values: Map<String, String>,
    base: JsonObject?,
): JsonObject = buildJsonObject {
    base?.forEach { (k, v) -> put(k, v) }
    if (base == null && section == Section.WEAPONS) put("weaponSkillId", JsonNull)
    fields.forEach { f ->
        val text = values[f.key].orEmpty().trim()
        if (f.kind == FieldKind.INT) {
            when {
                text.isNotEmpty() -> put(f.key, text.toInt())
                f.key == "lifespan" -> put(f.key, JsonNull)
                else -> put(f.key, 0)
            }
        } else {
            put(f.key, text)
        }
    }
}