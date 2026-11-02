# Итерация 7 (досрочно): атрибут стартового вдохновения

**Дата:** 02.11.2026. Сделано раньше очереди — итерация 5 (скриптуемые карты) ещё
не начата, но требование заказчика №9 закрыто полностью.

## Цель

Убрать жёсткую константу 5 из формулы старта теории и дать штатный способ влиять на
стартовое вдохновение извне: предметы, эффекты, зелья, команды, KubeJS, другие моды.

## Что сделано

- `registry/ModAttributes.java`:
  - `DeferredRegister<Attribute>` + `RangedAttribute("attribute.libresearch.starting_inspiration",
    5.0, 0.0, 1024.0).setSyncable(true)`;
  - `EntityAttributeModificationEvent` → `event.add(EntityType.PLAYER, STARTING_INSPIRATION)`
    (**обязательно** в NeoForge 1.21.1: без этого `player.getAttribute(...)` возвращает `null`);
  - хелпер `startingInspiration(LivingEntity)` — итог с модификаторами.
- `LibResearch`: регистрация `ModAttributes.ATTRIBUTES` и слушателя события.
- `core/KnowledgeMath.startInspiration(double base, double spiky, double hidden)`:
  жёсткий потолок 15 убран, минимум 1, `MAX_INSPIRATION = 1024` (граница атрибута).
  Ядро осталось без Minecraft-импортов.
- `theory/TableContext.startInspiration()` / `baseInspiration()` — читают атрибут;
  `TheoryEngine.start` использует их вместо константы.
- `client/ResearchTableScreen.baseInspiration()` — читает синхронизированный атрибут,
  лампочки и проверка «хватает ли на бонусы» считаются от него.
- Lang: `attribute.libresearch.starting_inspiration` (`ru_ru` + `en_us`); `LangKeysTest`
  расширен на ключи `attribute.libresearch.*`.
- `SelfTest`: +3 проверки (атрибут есть у игрока и равен 5, изменение посреди теории не
  меняет `startInspiration`, модификатор снимается). Итого **29/29**.

## Как проверить

```mcfunction
attribute @s libresearch:starting_inspiration get                  # 5.0
attribute @s libresearch:starting_inspiration modifier add libresearch:test 3 add_value
attribute @s libresearch:starting_inspiration get                  # 8.0
# открыть стол → лампочек 8, старт теории даёт 8 вдохновения
attribute @s libresearch:starting_inspiration modifier remove libresearch:test
```

Дедик-сервер без игрока: `./gradlew --offline runServer`, затем
`libresearch selftest` (FakePlayer — тоже `ServerPlayer`, атрибут у него есть).

## Известные ограничения

- Лампочки в SETUP рассчитаны на `baseInspiration` до 49; при больших значениях нужен
  многорядный вывод или счётчик «N» (дизайн `notes/10` §4 — оставлено на итерацию 8/GUI).
- Тултип с разбивкой «база + модификаторы» не делали (опционально, итерация 8).
- `spiky`/`hidden` по-прежнему не подключены — параметры передаются нулями.

## Файлы

`registry/ModAttributes.java` (новый), `LibResearch.java`, `core/KnowledgeMath.java`,
`theory/TableContext.java`, `theory/TheoryEngine.java`, `theory/SelfTest.java`,
`client/ResearchTableScreen.java`, `test/.../KnowledgeMathTest.java`,
`test/.../LangKeysTest.java`, `assets/libresearch/lang/{ru_ru,en_us}.json`,
`notes/10`, `notes/11`.
