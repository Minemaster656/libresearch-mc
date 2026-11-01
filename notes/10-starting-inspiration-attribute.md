# Атрибут стартового вдохновения

**Статус:** дизайн, не реализовано. Требование заказчика от 01.11.2026.

## 1. Зачем

Сейчас стартовое вдохновение жёстко считается в коде:

```
start = min(15, round(5 + 0.5 * spiky + 0.1 * hidden))
```

(см. `core/KnowledgeMath.startInspiration`; в текущей итерации вызывается как
`startInspiration(0, 0)`, т.е. всегда 5 — теги исследований ещё не подключены).

Нужен **штатный способ влиять на базу извне**: предметы, эффекты, зелья, команды,
KubeJS-скрипты, другие моды. Ванильный механизм для этого — **атрибут** (`Attribute`),
как `generic.max_health`, `generic.movement_speed`, `generic.armor` и т.п. Он уже умеет:

- базовое значение;
- модификаторы `ADD_VALUE` (+/−), `ADD_MULTIPLIED_BASE` (× базы), `ADD_MULTIPLIED_TOTAL`
  (× итога), с операцией и источником (предмет/эффект/скрипт);
- команду `/attribute <target> libresearch:starting_inspiration ...`;
- синхронизацию на клиент (нужно для отрисовки лампочек);
- сохранение в NBT игрока.

## 2. Регистрация

```java
public static final DeferredRegister<Attribute> ATTRIBUTES =
        DeferredRegister.create(Registries.ATTRIBUTE, LibResearch.MODID);

public static final Supplier<Attribute> STARTING_INSPIRATION = ATTRIBUTES.register(
        "starting_inspiration",
        () -> new RangedAttribute("attribute.libresearch.starting_inspiration",
                5.0D, 0.0D, 1024.0D).setSyncable(true));
```

- id: `libresearch:starting_inspiration`;
- ключ локализации: `attribute.libresearch.starting_inspiration`
  («Стартовое вдохновение» / «Starting Inspiration»);
- дефолт **5.0**, диапазон **0…1024** (верхняя граница нужна, чтобы `/attribute` не создавал
  абсурд; реальный «потолок» теории задаёт сам атрибут, а не хардкод 15);
- `setSyncable(true)` — иначе клиент не увидит значение для полосок/лампочек;
- атрибут регистрируется на **player**-сущности автоматически (все атрибуты есть у
  `LivingEntity`; для игрока ничего дополнительно делать не нужно).

## 3. Формула старта

```
base      = player.getAttributeValue(STARTING_INSPIRATION)   // с модификаторами
raw       = base + 0.5 * spiky + 0.1 * hidden                // теги исследований (позже)
start     = max(1, round(raw))                                // минимум 1, иначе теория мертва
```

Решения:

1. **Атрибут заменяет константу 5**, а не добавляется к ней (дефолт атрибута = 5, так что
   поведение по умолчанию не меняется).
2. **Потолок 15 убираем.** Верхняя граница — диапазон атрибута (1024). Обоснование: смысл
   атрибута в том, чтобы можно было сделать «очень вдохновляющий» предмет; если нужен
   баланс, контент-мод сам ограничит модификаторы.
3. **Минимум 1**: если модификаторы увели атрибут в 0 или ниже, теорию всё равно можно
   начать с 1 вдохновения (иначе игрок застревает без объяснения). Альтернатива — запрет
   старта с сообщением; выбрано «минимум 1», чтобы не ломать игру дебаффами.
4. `KnowledgeMath.startInspiration` меняет сигнатуру на `startInspiration(double base,
   double spiky, double hidden)` — ядро остаётся чистым (никакого `Player`), значение
   атрибута передаёт вызывающий код. Округление — `Math.round`, как в оригинале.
5. Значение фиксируется в `TheoryState.startInspiration` на момент старта: изменение
   атрибута **посреди теории** не влияет на текущую теорию (иначе возвраты вдохновения
   ломаются).

## 4. Отображение в GUI

- Лампочки в фазах SETUP/PLAY считаются от `startInspiration` (уже так).
- В SETUP показывать «Стартовое вдохновение: N» и, если N > 15, рисовать лампочки в
  несколько рядов (сейчас 7×7 = 49, хватит до 49; дальше — счётчик «N» без лампочек).
- Тултип: «База 5, модификаторы: +2 (предмет), ×1.5 (эффект)» — опционально, итерация 8.

## 5. Команда `/attribute` — примеры

```mcfunction
# добавить +3
attribute @s libresearch:starting_inspiration modifier add libresearch:test_bonus 3 add_value
# умножить базу на 1.5
attribute @s libresearch:starting_inspiration modifier add libresearch:test_mult 0.5 add_multiplied_base
# посмотреть итог
attribute @s libresearch:starting_inspiration get
# снять модификатор
attribute @s libresearch:starting_inspiration modifier remove libresearch:test_bonus
```

Эти же команды можно вешать на карты (см. `notes/09` §2.4) — например, карта-награда
«+1 к стартовому вдохновению навсегда».

## 6. KubeJS

- Чтение/запись через ванильный API: у сущности есть атрибуты, KubeJS даёт доступ к
  `AttributeInstance` (проверить точный синтаксис в итерации 6, задокументировать).
- Наш API дополнительно даёт хелперы:
  `LibResearchApi.getStartingInspiration(Player)` и
  `LibResearchApi.setStartingInspiration(Player, double)` (последний — только для тестов/дебага).

## 7. Что проверить при реализации

- [ ] Атрибут виден в `/attribute` и в списке атрибутов мода.
- [ ] Модификаторы `add_value`, `add_multiplied_base`, `add_multiplied_total` дают ожидаемый
      результат (сверить с ванильным поведением на `generic.max_health`).
- [ ] Отрицательный итог → старт с 1, в чате/тултипе понятное объяснение.
- [ ] Клиент видит значение без перезахода (sync).
- [ ] Значение фиксируется на старте теории и не меняет текущую теорию.
- [ ] Юнит-тест на `KnowledgeMath.startInspiration(base, spiky, hidden)` для
      base = 0, 5, 12.6, 100.
