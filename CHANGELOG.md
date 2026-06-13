# Changelog

All notable changes to the Aetherium Framework are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the
project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Все значимые изменения Aetherium Framework документируются здесь. Формат основан на
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); проект следует
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Added — Build system & core API / Система сборки и API ядра (2026-06-12)

**EN**
- Wired the Gradle 8.8 multi-project build (Kotlin DSL): root `build.gradle.kts`,
  `gradle.properties`, centralized version catalog `gradle/libs.versions.toml`, and a
  committed Gradle wrapper. `./gradlew projects` and `./gradlew build` are green.
- Pinned the Java 21 toolchain (auto-download disabled → resolves to local GraalVM 21)
  and enabled `--enable-preview` globally (compile/test/javadoc/run) for the FFM API.
  Verified preview class-file minor version `0xFFFF` and `preview : enabled` at runtime.
- Packaged `aetherium-loader` as a drop-in NeoForge 1.21.1 mod: `META-INF/neoforge.mods.toml`
  with Gradle-templated version/range fields, `FMLModType = MOD` manifest, non-invasive
  (`ordering = NONE`, `side = BOTH`) dependencies so it co-exists in a standard `mods/` folder.
- Implemented the baseline `aetherium-core` API (leaf module, no internal deps): the
  **Symbol Manifest** (`Symbol`, `SymbolManifest` + `Builder`, `ArraySymbolManifest` with
  `O(1)` `byId`) and the **Capability/Fallback registry** (`CapabilityTier`, `Capability`,
  `CapabilityProvider`, `FallbackChain`, `CapabilityRegistry`), plus a structured error
  model (`Diagnostic`, `AetheriumException`).
- Laid Hardware & Compute groundwork in `org.aetherium.core.compute`: `OffHeapAllocator`
  (FFM `Arena`-backed, confined default), `ComputePipeline` (async accelerated-compute
  placeholder), and `ComputeCapabilities` constants.
- Documented the build system bilingually: `docs/en/build-system.md`, `docs/ru/build-system.md`.

**RU**
- Подключена многопроектная сборка Gradle 8.8 (Kotlin DSL): корневой `build.gradle.kts`,
  `gradle.properties`, централизованный каталог версий `gradle/libs.versions.toml` и
  зафиксированный wrapper. `./gradlew projects` и `./gradlew build` зелёные.
- Зафиксирован тулчейн Java 21 (авто-загрузка отключена → разрешается в локальный GraalVM 21)
  и глобально включён `--enable-preview` (compile/test/javadoc/run) для FFM API. Проверены
  preview minor-версия class-файла `0xFFFF` и `preview : enabled` во время выполнения.
- `aetherium-loader` упакован как drop-in мод NeoForge 1.21.1: `META-INF/neoforge.mods.toml`
  с подставляемыми Gradle полями версий, манифест `FMLModType = MOD`, неинвазивные
  зависимости (`ordering = NONE`, `side = BOTH`) для сосуществования в стандартной `mods/`.
- Реализован базовый API `aetherium-core` (модуль-лист, без внутренних зависимостей):
  **Манифест Символов** (`Symbol`, `SymbolManifest` + `Builder`, `ArraySymbolManifest` с
  `O(1)` `byId`) и **Реестр Возможностей/Откатов** (`CapabilityTier`, `Capability`,
  `CapabilityProvider`, `FallbackChain`, `CapabilityRegistry`), плюс структурированная модель
  ошибок (`Diagnostic`, `AetheriumException`).
- Заложена основа Hardware & Compute в `org.aetherium.core.compute`: `OffHeapAllocator`
  (на FFM `Arena`, confined по умолчанию), `ComputePipeline` (заглушка асинхронного
  ускоренного вычисления) и константы `ComputeCapabilities`.
- Система сборки задокументирована двуязычно: `docs/en/build-system.md`, `docs/ru/build-system.md`.

### Added — Foundation phase / Этап основания (2026-06-12)

**EN**
- Initialized local Git repository on the `main` branch.
- Created the modular Gradle source layout: `aetherium-core`, `aetherium-bytecode`,
  `aetherium-native`, `aetherium-loader`, `aetherium-cli`.
- Established the strict bilingual documentation structure under `docs/en/` and
  `docs/ru/`.
- Authored top-level `README.md` (bilingual) and `ARCHITECTURE.md` (bilingual,
  section-paired) describing the meta-loader design, the `O(1)` runtime dispatch
  guarantee, Java 21 / GraalVM feature usage, backward-compatibility strategy, and the
  CIA-triad security & fallback model.
- Drafted deep-dive design docs: Bytecode Manipulation Engine (ASM) and Native Bridge
  (JNI / `java.lang.foreign`), in both languages.
- Added the `aetherium-cli` placeholder entry point.
- Added `.gitignore` and `LICENSE` (Apache-2.0).

**RU**
- Инициализирован локальный Git-репозиторий в ветке `main`.
- Создана модульная структура исходников Gradle: `aetherium-core`,
  `aetherium-bytecode`, `aetherium-native`, `aetherium-loader`, `aetherium-cli`.
- Установлена строгая двуязычная структура документации в `docs/en/` и `docs/ru/`.
- Написаны корневые `README.md` (двуязычный) и `ARCHITECTURE.md` (двуязычный, с
  парными секциями), описывающие дизайн мета-загрузчика, гарантию `O(1)`-диспетчеризации
  во время выполнения, использование возможностей Java 21 / GraalVM, стратегию обратной
  совместимости и модель безопасности и отката по триаде CIA.
- Подготовлены детальные проектные документы: движок манипуляции байт-кодом (ASM) и
  нативный мост (JNI / `java.lang.foreign`) на обоих языках.
- Добавлена заглушка точки входа `aetherium-cli`.
- Добавлены `.gitignore` и `LICENSE` (Apache-2.0).

### Notes / Примечания
- Engines are **specified, not implemented**. The next phase wires the Gradle build
  graph and lands `aetherium-core` contracts. / Движки **специфицированы, но не
  реализованы**. Следующий этап подключает граф сборки Gradle и реализует контракты
  `aetherium-core`.
