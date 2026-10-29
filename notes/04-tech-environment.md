# Окружение и сборка

## 1. Игровая сборка (цель)

- Инстанс PrismLauncher: `/home/minemaster/.local/share/PrismLauncher/instances/dev`
- Minecraft **1.21.1**, NeoForge **21.1.262** (`mmc-pack.json`)
- Моды в инстансе (важные для нас):
  - `kubejs-neoforge-2101.7.2-build.377` + `rhino-2101.2.8-build.91`
  - `architectury-13.0.11-neoforge`, `jei-1.21.1-neoforge-19.70.0.467`
  - клиентские: sodium, lithium, ferritecore, modernfix, immediatelyfast, appleskin и т.д.
- Значит: сборка мода под **1.21.1 / NeoForge 21.1.x**, KubeJS версии 2101 (MC 1.21.1).
  KubeJS-интеграция — только `compileOnly`, в рантайме мод не обязателен.

## 2. Репозиторий

Сейчас пусто: только `AGENTS.md`, `notes/`, `sources/`, `temp/`, `.gitignore`, git без коммитов.
Gradle-обвязки нет — итерация 0 создаёт её с нуля.

```
/home/minemaster/dev/libresearch-mc
├── AGENTS.md
├── notes/           # заметки (в git)
├── sources/         # чужие исходники/jar (gitignored, читать только субагентам)
└── temp/            # черновики (gitignored)
```

## 3. Инструменты на машине

| Что | Версия / путь |
|---|---|
| Java (default) | OpenJDK **27** — для Gradle не использовать |
| Java для сборки | `/usr/lib/jvm/java-21-openjdk` (есть также graalvm 21, 17, 25) |
| Gradle wrapper dists (кэш) | `gradle-9.5.0-bin`, `gradle-9.6.1-bin` |
| Gradle caches | `~/.gradle/caches` (9.5.0, 9.6.1, 9.7.0) |
| ImageMagick | **7.1.2-34 Q16-HDRI** (`magick`, `convert`, `identify`) |
| Шрифты | 1319 в `fc-list` |

## 4. Кэш NeoForge (важно для офлайна)

Уже скачано предыдущей сборкой:

- `net.neoforged:moddev-gradle:2.0.140` (MDG)
- `net.neoforged:neoforge:21.1.228` — `userdev`, `universal`, `sources`
- `net.neoforged:neoform:1.21.1-20240808.144430`
- `net.neoforged:neoform-runtime:2.0.18`
- `~/.gradle/caches/neoformruntime` — **1.1 ГБ** артефактов, ассетов и промежуточных
  результатов, то есть декомпиляция Minecraft уже выполнялась
- `~/.gradle/caches/minecraft/net/minecraftforge/forge` (следы старого ForgeGradle-проекта)

## 5. Сеть

| Хост | Статус |
|---|---|
| `repo1.maven.org` | 200 OK |
| `plugins.gradle.org/m2` | 200 OK |
| `services.gradle.org/distributions` | 200 OK |
| `maven.neoforged.net` | **недоступен**: DNS отдаёт `2a02:6ea0:...` (CDN77), и IPv4, и IPv6 виснут по таймауту |

Вывод: свежие артефакты NeoForge скачать нельзя, но кэш позволяет собрать офлайн при
условии совпадения версий.

**Tor.** На машине есть прокси: HTTP `127.0.0.1:9060`, SOCKS5 `127.0.0.1:9050`. Через них
maven, судя по всему, не работает (выходные ноды CDN блокируются), но попробовать стоит
первым делом, если офлайн-сборка упрётся в отсутствующий артефакт:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
./gradlew -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=9060 build
# или socks: -DsocksProxyHost=127.0.0.1 -DsocksProxyPort=9050
```

**Крайний вариант — GitHub Actions.** Заказчик коммитит и привязывает remote; сборка
запускается на раннере (там сеть до `maven.neoforged.net` есть), готовый jar скачивается
артефактом. Воркфлоу кладём в `.github/workflows/build.yml`: `setup-java` 21, кэш Gradle,
`./gradlew build`, `actions/upload-artifact` с `build/libs/*.jar` (без `-sources`).
Это же даёт проверку, что проект собирается «с нуля», а не только на локальном кэше.

## 6. План сборки (итерация 0)

1. Wrapper: **Gradle 8.12** (скачивается с `services.gradle.org`, проверено) — MDG 2.0.x
   рассчитан на 8.8+, поддержка Gradle 9 не гарантирована. Если 9.x окажется совместимой,
   можно остаться на кэшированной 9.6.1.
2. `gradle.properties`: `minecraft_version=1.21.1`, `neo_version=21.1.228` (**не 262** —
   262 нет в кэше), `mod_id=libresearch`, `mod_version=0.1.0`.
3. `settings.gradle`: `pluginManagement` с репозиториями
   `maven.neoforged.net/releases` и `plugins.gradle.org/m2`; для офлайна —
   `resolutionStrategy.eachPlugin { useModule("net.neoforged:moddev-gradle:2.0.140") }`,
   чтобы не требовался plugin-marker.
4. Первый запуск: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew --offline build`.
5. Если MDG не резолвится офлайн — добавить jar MDG в `buildscript` classpath из
   `~/.gradle/caches/modules-2/files-2.1/net.neoforged/moddev-gradle/2.0.140/...`
   (костыль только для локальной сборки, в git не коммитить).
6. Проверка: `runClient` из IDE/Gradle с `JAVA_HOME=21`; в игре `/libresearch` отвечает.

## 7. Риски

| Риск | Обход |
|---|---|
| `maven.neoforged.net` недоступен | офлайн-сборка на кэше 21.1.228; при необходимости — VPN/прокси |
| Java 27 по умолчанию | всегда явный `JAVA_HOME` 21 + toolchain в `build.gradle` |
| Gradle 9 vs MDG 2.0.140 | wrapper 8.12 с services.gradle.org |
| NeoForge 21.1.228 в dev vs 21.1.262 в инстансе | API в пределах 21.1.x совместим, тестировать на 262 |
| KubeJS 2101.7.2 API нестабилен | интеграция последней итерацией, `compileOnly`, без жёсткой зависимости |
| Ассеты ImageMagick «черновые» | отдельная папка `tools/assets`, заказчик дорисовывает |

## 8. Проверка после сборки

- [ ] `./gradlew build` проходит офлайн
- [ ] `runClient` запускает игру, мод в списке
- [ ] блок ставится/ломается, BlockEntity сохраняется после перезахода
- [ ] собранный jar кладётся в инстанс и грузится вместе с KubeJS/JEI/Architectury
- [ ] `/libresearch validate` не находит ошибок в датапаке
