# Итерация 1 — реестры контента и валидация

**Статус:** готово, проверено на dev-сервере.

## Цель

Описать карты, бонусные блоки, типы столов и чернильницы JSON-данными, загружать их
датапаком, переживать `/reload` и уметь проверять целостность командой
`/libresearch validate`.

## Решения

1. **Бонусные блоки — отдельный глобальный реестр.**
   `data/<ns>/libresearch/table_bonus/<id>.json`. Тип стола больше не хранит описания
   бонусов, а ссылается на них списком id: `"bonus_blocks": ["libresearch:crafting_table"]`.
   Причина: один и тот же бонус (верстак, печь) используется несколькими столами, а в
   будущем — модами и KubeJS; дублировать объекты в каждом типе стола нельзя.
2. **Типы карт — sealed-иерархии с dispatch по `"type"`.** `CardRequirement` (`item`,
   `any_item`, `xp_levels`, `table_resources`, `progress`, `never`) и `CardEffect`
   (`progress`, `progress_drain`, `inspiration`, `bonus_draws`, `full_value_slots`,
   `block_discipline`, `give_item`, `consume_xp`, `paper`, `ink_charges`).
   Перед Codec-парсингом `CardJson` проверяет «сырой» JSON на неизвестные/отсутствующие
   `type` и `target`, чтобы в лог попадала понятная ошибка с id файла, а не исключение
   dispatch-кодека.
3. **Диапазоны.** `IntRange` принимает либо число (`5`), либо `{"min": 3, "max": 7}`;
   `normalized()` разворачивает перепутанные границы, валидация сообщает о них.
4. **Случайность в данных не фиксируется.** В JSON задаётся диапазон/цель (`random`,
   `highest`, `lowest`), конкретные значения выберет движок в момент создания карты
   (итерация 2) — так данные остаются декларативными.
5. **Валидация.** `DataValidation` проверяет: дубликаты id, существование предметов
   иконок/наград, ссылки `bonus_blocks` → `table_bonus`, ссылки бонусов → карты,
   порядок границ диапазонов, `target: self` только с `discipline`, `item` xor `tag`,
   положительные `capacity`/`charges_per_draw`. Команда печатает до 30 проблем.
6. **stdin для run-тасков.** В `build.gradle` добавлен `standardInput = System.in`, иначе
   команды серверу из консоли не доходят (Gradle по умолчанию подсовывает пустой поток).
7. **Ошибка парсинга = запись отбрасывается.** `resultOrPartial` у record-кодеков с
   необязательными полями возвращает «частичный» результат, и битый файл попадал в реестр
   со значениями по умолчанию. Общий хелпер `data/JsonLoad` логирует ошибку с id файла и
   возвращает пустой результат; используется всеми четырьмя загрузчиками.

## Схема данных

```jsonc
// data/<ns>/libresearch/card/<id>.json
{
  "display": "Base study",                // строка; в итерации 6 станет объектом с описанием
  "cost": 1,
  "weight": 3,
  "discipline": "libresearch:copper",   // null = любая, "self" — цель выбирается эффектом
  "bonus_only": false,                   // только из бонусной колоды
  "spawn": { "requirements": [ ... ] },  // условия появления в руке
  "requires": [ ... ],                   // условия активации
  "effects": [ ... ]
}

// data/<ns>/libresearch/table_bonus/<id>.json
{
  "display": "Crafting table",
  "match": { "blocks": ["minecraft:crafting_table"], "tags": [], "entities": [] },
  "cost": 1,
  "cards": [ { "card": "libresearch:study", "count": 2 } ]
}

// data/<ns>/libresearch/table_type/<id>.json
{
  "display": "Research table",
  "disciplines": [ ... ],
  "bonus_blocks": ["libresearch:crafting_table", "..."]
}
```

## Затронутые файлы

- Java: `content/card/{IntRange,CardRequirement,CardEffect,Card,CardJson,Cards}.java`,
  `content/bonus/{BonusBlockDefinition,TableBonuses}.java`, `content/table/TableType.java`,
  `data/{DataValidation,LibCommands}.java`, `DataEvents.java`, `build.gradle`.
- Данные: `data/libresearch/card/*.json` (10 карт), `.../table_bonus/*.json` (8 бонусов),
  `.../table_type/{research,debug}.json` (переведены на ссылки).
- Заметки: `notes/03-mvp-design.md`, `notes/07-multi-table.md`.

## Как проверить

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
./gradlew --offline build
mkfifo /tmp/opencode/srv.in          # держим писателя открытым, иначе сервер получит EOF
./gradlew --offline runServer < /tmp/opencode/srv.in
# после строки "Done":
#   libresearch validate
#   stop
```

Ожидаемо: `LibResearch: OK — типов столов: 2, карт: 10, бонусных блоков: 8, чернильниц: 1`.
Для проверки ветки ошибок достаточно подсунуть датапак с неизвестным `type` и ссылкой на
несуществующую карту — команда печатает список проблем.

## Известные проблемы

- `/reload` перезагружает файлы, но ещё не проверяет, что уже открытые теории не ссылаются
  на удалённые карты (появится вместе с логикой стола в итерации 2).
- `weight` пока только читается и валидируется; взвешенный добор включается в итерации 2.
- Схема `table_type` ещё не содержит `inkwell`-ссылки: чернильница ищется по предмету,
  положенному в слот. Если понадобится несколько типов чернильниц на один стол — решим
  в итерации 5.

## Что осталось (за пределами итерации)

- Реальная логика теории (итерация 2) и GUI (итерация 3).
- Скан бонусных блоков вокруг стола (итерация 4): сейчас есть только определения.
