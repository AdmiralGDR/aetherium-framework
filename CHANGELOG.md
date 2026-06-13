# Changelog

All notable changes to the Aetherium Framework are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the
project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Все значимые изменения Aetherium Framework документируются здесь. Формат основан на
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); проект следует
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Changed — Relicensed to AGPL-3.0 / Смена лицензии на AGPL-3.0 (2026-06-12)

**EN**
- Relicensed the project from Apache-2.0 to the **GNU Affero General Public License v3.0**
  (strong network copyleft). Replaced `LICENSE` with the full AGPL-3.0 text, updated the
  README license section (bilingual), and set `license = "AGPL-3.0-or-later"` in the
  loader's `neoforge.mods.toml`.

**RU**
- Проект переведён с Apache-2.0 на **GNU Affero General Public License v3.0** (сильный
  сетевой копилефт). `LICENSE` заменён полным текстом AGPL-3.0, обновлён раздел лицензии в
  README (двуязычно), в `neoforge.mods.toml` загрузчика установлено
  `license = "AGPL-3.0-or-later"`.

### Added — Bytecode engine (ASM) / Движок байт-кода (ASM) (2026-06-12)

**EN**
- Implemented the `aetherium-bytecode` engine: `ClassReader → TransformChain → ClassWriter`
  with `COMPUTE_FRAMES` (`BytecodeEngine`, `TransformChain`, `EngineConfig`, `ClassContext`).
- Open `ClassTransformer` SPI (revised from the earlier `sealed` sketch so the loader/mods can
  contribute transformers) + sealed `TransformResult` (`Applied`/`Skipped`/`Failed`).
- Virtual-thread execution: `transformAll` runs one isolated task per class via
  `Executors.newVirtualThreadPerTaskExecutor()`, each bounded by a per-class timeout.
- `DispatchLoweringTransformer`: rewrites `INVOKESTATIC` Aetherium API calls into
  `invokedynamic` bound to `AetheriumBootstraps.bootstrapDispatch`, using dense IDs from the
  `SymbolManifest`; `DispatchTable` is the flat `MethodHandle[]` for `O(1)` runtime dispatch.
- Safety/fallback: original `byte[]` retained; on transformer exception, `Failed`, structural
  check failure, dataflow-verification error, or timeout, the engine logs a structured
  `Diagnostic` and returns the original bytes — never crashes. `LoaderAwareClassWriter` makes
  frame computation fail-safe; `CheckClassAdapter` provides structural + best-effort dataflow
  verification.
- Verified end-to-end via `aetherium-cli selftest`: reads a dummy class, applies a mock + the
  dispatch transform, verifies, loads and invokes it (`Demo.run() == 42`, routed through the
  dispatch table), and confirms the revert-to-original fallback (1 diagnostic, original bytes).
- `aetherium-bytecode` depends only on `aetherium-core` + ASM (verified); no loader logic.
- Updated `docs/{en,ru}/bytecode-engine.md` to match the implemented contracts.

**RU**
- Реализован движок `aetherium-bytecode`: `ClassReader → TransformChain → ClassWriter` с
  `COMPUTE_FRAMES` (`BytecodeEngine`, `TransformChain`, `EngineConfig`, `ClassContext`).
- Открытый SPI `ClassTransformer` (изменён с раннего `sealed`-наброска, чтобы загрузчик/моды
  могли поставлять трансформеры) + sealed `TransformResult` (`Applied`/`Skipped`/`Failed`).
- Выполнение на виртуальных потоках: `transformAll` запускает по одной изолированной задаче на
  класс через `Executors.newVirtualThreadPerTaskExecutor()`, каждая ограничена таймаутом.
- `DispatchLoweringTransformer`: переписывает вызовы `INVOKESTATIC` API Aetherium в
  `invokedynamic`, привязанный к `AetheriumBootstraps.bootstrapDispatch`, используя плотные ID
  из `SymbolManifest`; `DispatchTable` — плоский `MethodHandle[]` для `O(1)`-диспетчеризации.
- Безопасность/откат: исходный `byte[]` сохраняется; при исключении трансформера, `Failed`,
  провале структурной проверки, ошибке верификации потоков данных или таймауте движок логирует
  структурированный `Diagnostic` и возвращает исходные байты — никогда не падает.
  `LoaderAwareClassWriter` делает вычисление кадров отказоустойчивым; `CheckClassAdapter` даёт
  структурную и best-effort верификацию потоков данных.
- Проверено end-to-end через `aetherium-cli selftest`: читает фиктивный класс, применяет mock +
  трансформацию диспетчеризации, верифицирует, загружает и вызывает (`Demo.run() == 42`,
  через таблицу диспетчеризации), и подтверждает откат к оригиналу (1 диагностика, исходные байты).
- `aetherium-bytecode` зависит только от `aetherium-core` + ASM (проверено); без логики загрузчика.
- Обновлены `docs/{en,ru}/bytecode-engine.md` под реализованные контракты.

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
