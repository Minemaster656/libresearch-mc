# Итерация 0 — скелет сборки

## Цель

Собрать с нуля Gradle-проект NeoForge 1.21.1, зарегистрировать каркас мода:
два отладочных стола, чернильницу, BlockEntity со слотами и датапак-загрузчики
типов столов и определений чернильниц.

## Что сделано

- Gradle wrapper **9.6.1** (взят из системного Gradle 9.8.1, дистрибутив уже в кэше).
- ModDevGradle **2.0.140** через `resolutionStrategy` (plugin-marker не требуется).
- `build.gradle`: Java 21 toolchain, `runClient`/`runServer`/`runData`, подстановка
  версий в `neoforge.mods.toml`.
- `LibResearch`: регистрация компонентов, блоков, предметов, BlockEntity, creative tab,
  capability `ItemHandler` для стола.
- `ResearchTableBlock` / `DebugResearchTableBlock`: один класс + поле «тип по умолчанию»;
  при установке тип пишется в BlockEntity.
- `ResearchTableBlockEntity`: `table_type` + `ItemStackHandler` на 2 слота
  (чернильница / бумага), сохранение в NBT, sync-пакет.
- `InkwellItem` + `InkwellData` (компонент `charges`, `capacity`): полоска заряда,
  тултип, тинт по цвету из определения.
- Датапак-загрузчики (Codec + `SimpleJsonResourceReloadListener`):
  `libresearch/table_type/*.json`, `libresearch/inkwell/*.json`.
- Контент: `research.json` (5 слитков + звезда Незера), `debug.json`
  (5 красителей; бонусные блоки — камень, обсидиан, бедрок), `ink.json`.
- Ресурсы: blockstate/model/item-model на ванильных текстурах (плейсхолдеры до итерации 6),
  лут-таблицы, теги `mineable/axe|pickaxe`, lang `en_us`/`ru_ru`.

## Как проверить

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
./gradlew --offline build
```

Запуск игры (нужен Tor — тянется `moddev-config.json`):

```bash
./gradlew -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=9060 \
          -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=9060 runClient
```

В логе должно быть:

```
Loaded 2 table types   (в текущей локализации — «Загружено типов столов: 2»)
Loaded 1 inkwell definition
```

В игре: оба блока есть в креативной вкладке LibResearch, ставятся/ломаются,
BlockEntity сохраняет слоты после перезахода. `/reload` перезагружает JSON
(ошибки парсинга видны в логе).

## Известные проблемы

- `runClient`/`runServer` без Tor падают на `prepareServerRun`:
  нет `neoforge-21.1.228-moddev-config.json` в кэше. После первого онлайн-запуска
  артефакт кэшируется, дальше работает `--offline`.
- Тинт чернильницы на выделенном сервере может быть дефолтным (датапак-определения
  не синхронизируются клиенту).
- Ёмкость из `ink.json` пока не применяется к стартовому предмету (только 100);
  применение — при крафте/перезарядке в итерации 5.
- Рецептов нет: чернильница и столы пока только в креативной вкладке.
- GUI нет: ПКМ по столу ничего не открывает.

## Следующее

Итерация 1: карты и бонусные блоки в датапаке (Codec, реестр карт, `/reload`),
команда `/libresearch validate` с человекочитаемыми ошибками.
