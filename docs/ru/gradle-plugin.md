# Gradle-плагин — сборка модов без конфигурации

*Русский. Английский оригинал: [`../en/gradle-plugin.md`](../en/gradle-plugin.md).*

Плагин `org.aetherium.gradle` (`aetherium-gradle-plugin`) сводит всю сборку мода Aetherium к одному
DSL-блоку. Он заменяет старый процесс с вендорингом физического `aetherium-core.jar` и ручной
настройкой Shadow/JarJar — то самое узкое место из отчёта по порту LoomThreader.

## 1. Предусловие

Один раз опубликуйте фреймворк в локальный Maven (далее любой потребитель резолвит его по координате):

```bash
cd AetheriumFramework
./gradlew publishToMavenLocal
```

Это устанавливает `org.aetherium:aetherium-core|bytecode|native|edge:1.0.0-SNAPSHOT` **и** маркер
плагина `org.aetherium.gradle` в `~/.m2`.

## 2. Использование (вся сборка)

`settings.gradle.kts`:

```kotlin
pluginManagement { repositories { mavenLocal(); gradlePluginPortal() } }
rootProject.name = "my-mod"
```

`build.gradle.kts`:

```kotlin
plugins { id("org.aetherium.gradle") version "1.0.0-SNAPSHOT" }

aetherium {
    version = "1.0.0-SNAPSHOT"   // единственная обязательная настройка
}
```

Один блок применяет `java-library`, фиксирует **тулчейн Java 21 с `--enable-preview`** (публичный API
использует preview FFM), подключает `mavenLocal` + `mavenCentral` + NeoForged и добавляет зависимости
`aetherium-core` + `aetherium-edge`. Моддер пишет только под API Aetherium.

## 3. DSL (`AetheriumExtension`)

| Свойство | По умолчанию | Назначение |
|---|---|---|
| `version` | `1.0.0-SNAPSHOT` | Версия фреймворка Aetherium из Maven. |
| `modId` | имя проекта | Mod id для генерируемых метаданных (приводится к `[a-z][a-z0-9_]*`). |
| `displayName` | `modId` | Человекочитаемое имя мода в метаданных. |
| `bundle` | `true` | Регистрировать `aetheriumBundle` (самодостаточный jar в стиле JarJar). |
| `includeBytecode` | `false` | Дополнительно зависеть от `aetherium-bytecode` (моды со своими трансформерами). |
| `generateMetadata` | `true` | Автогенерация `neoforge.mods.toml` + `fabric.mod.json`. |

Версия самого мода берётся из стандартного Gradle `version = "..."`; `aetherium.version` — версия
*фреймворка*.

## 4. Метаданные загрузчика — исправление «not a mod»

При включённом `generateMetadata` (по умолчанию) плагин запускает `generateAetheriumMetadata` перед
`processResources`, записывая `META-INF/neoforge.mods.toml` и `fabric.mod.json` в генерируемую папку
ресурсов, которая питает **и** обычный `jar`, **и** `aetheriumBundle`. Итоговый jar распознаётся
NeoForge **и** Fabric нативно без ручных метаданных — закрывая дефект, когда bundle отклонялся как
«not a mod». `modId` приводится к требуемой NeoForge форме (`[a-z][a-z0-9_]*`, дефисы → подчёркивания)
и валиден на обоих загрузчиках.

## 5. Упаковка — `aetheriumBundle`

```bash
./gradlew build           # → build/libs/my-mod-<v>.jar          (только классы мода)
./gradlew aetheriumBundle # → build/libs/my-mod-<v>-bundle.jar   (мод + встроенные aetherium-*)
```

`aetheriumBundle` встраивает **только** артефакты `aetherium-*` из runtime-classpath (никогда
Minecraft/NeoForge), давая единый drop-in jar со стратегией `DuplicatesStrategy.EXCLUDE`.

## 5. Проверено

Тестовый потребитель, применяющий лишь плагин, собирается без вендоренных jar: плагин и все артефакты
API резолвятся из `mavenLocal`, `build` даёт jar мода, а `aetheriumBundle` — самодостаточный jar со
встроенным фреймворком (49 классов `org/aetherium/*` для мода на core+edge).

## Фаза 17 — универсальный jar

При `aetherium { universal = true }` плагин регистрирует задачу `aetheriumUniversalJar`, собирающую единый
`<name>-universal.jar` со встроенным модом, всем рантаймом Aetherium (core + loader, только артефакты
`aetherium-*` — никогда Minecraft/NeoForge) и **объединёнными** метаданными
`META-INF/neoforge.mods.toml` + `fabric.mod.json`, со штампом манифеста `Aetherium-Universal: true`. Итог —
надёжный jar для игроков на любом загрузчике. `embedLoader` (по умолчанию true) управляет встраиванием loader.
