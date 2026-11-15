package net.kira.corisednd.ui.reference

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

class RefField(val key: String, val label: String, val markdown: Boolean = false)

/** paged: сервер отдаёт страницы, поиск идёт на сервер. grouped: список с заголовками по подзаголовку. */
enum class Section(
    val title: String,
    val path: String,
    val titleKey: String,
    val subtitleKey: String?,
    val fields: List<RefField>,
    val paged: Boolean = false,
    val grouped: Boolean = false,
) {
    RACES(
        "Расы", "races", "outName", "inName",
        listOf(
            RefField("inName", "Название внутри мира"),
            RefField("shortDesc", "Кратко"),
            RefField("appearance", "Внешность"),
            RefField("lore", "История"),
            RefField("relationships", "Отношения с другими"),
            RefField("status", "Статус"),
            RefField("archetype", "Архетип"),
            RefField("lifespan", "Продолжительность жизни (лет)"),
        ),
    ),
    CULTURES("Культуры", "cultures", "name", null, listOf(RefField("description", "Описание"))),
    RELIGIONS("Религии", "religions", "name", null, listOf(RefField("description", "Описание"))),
    BACKSTORIES("Предыстории", "backstories", "name", null, listOf(RefField("description", "Описание"))),
    CLASSES(
        "Классы", "classes", "name", null,
        listOf(
            RefField("socialStatus", "Социальный статус"),
            RefField("primarySkills", "Основные навыки"),
            RefField("description", "Описание"),
        ),
    ),
    WEAPONS(
        "Оружие", "weapons", "name", "damage",
        listOf(
            RefField("damage", "Урон"),
            RefField("damageType", "Тип урона"),
            RefField("grip", "Хват"),
            RefField("weight", "Вес"),
            RefField("value", "Стоимость"),
            RefField("description", "Описание"),
        ),
    ),
    ARMOURS(
        "Броня и щиты", "armours", "name", "armourType",
        listOf(
            RefField("armourType", "Тип"),
            RefField("damageMitigation", "Защита"),
            RefField("weight", "Вес"),
            RefField("value", "Стоимость"),
            RefField("requirements", "Требования"),
            RefField("disadvantages", "Недостатки"),
            RefField("resistance", "Сопротивления"),
            RefField("vulnerability", "Уязвимости"),
            RefField("description", "Описание"),
        ),
    ),
    SPELLS(
        "Заклинания", "spells", "name", "type",
        listOf(
            RefField("type", "Тип"),
            RefField("time", "Время сотворения"),
            RefField("distance", "Дистанция"),
            RefField("cost", "Стоимость"),
            RefField("effect", "Эффект"),
            RefField("description", "Описание"),
        ),
        paged = true,
    ),
    LORE(
        "Лор", "lore", "title", "category",
        listOf(
            RefField("category", "Категория"),
            RefField("body", "", markdown = true),
        ),
        paged = true,
        grouped = true,
    ),
}

/** Строка списка: только то, что нужно для показа и перехода. */
class RefEntry(val id: Long, val title: String, val subtitle: String?, val isCustom: Boolean = false)

fun JsonObject.toEntry(section: Section) = RefEntry(
    id = getValue("id").jsonPrimitive.long,
    title = text(section.titleKey) ?: "Без названия",
    subtitle = section.subtitleKey?.let { text(it) },
    isCustom = this["isCustom"]?.jsonPrimitive?.booleanOrNull == true,
)

/** Текст поля или null, если поля нет, оно null или пустое. */
fun JsonObject.text(key: String): String? {
    val p = this[key] as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    return p.content.trim().ifEmpty { null }
}

fun displayValue(key: String, value: String): String =
    if (key == "grip") {
        when (value) {
            "one_handed" -> "одноручное"
            "two_handed" -> "двуручное"
            "polearm" -> "древковое"
            else -> value
        }
    } else value