# Changelog

All notable changes to the Aetherium Framework are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the
project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Все значимые изменения Aetherium Framework документируются здесь. Формат основан на
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); проект следует
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Added — Developer CLI & Chaos Engineering suite / CLI разработчика и набор Chaos Engineering (2026-06-13)

**EN**
- Expanded `aetherium-cli` into a real command dispatcher: `init`, `analyze`, `selftest`,
  `preflight`, `chaos`, and `--help`.
  - `init <name>` scaffolds a complete, AGPL-3.0 mod project (build scripts, NeoForge
    `neoforge.mods.toml`, an example mod using the `ComputePipeline` API, per-file AGPL headers).
    The name is sanitized to a valid modId; the generated mod compiles against the Aetherium API
    with **zero boilerplate**.
  - `analyze <path>` statically verifies a `.class`/`.jar`/dir against the target loader baseline
    (class-file version + ASM `CheckClassAdapter.verify`); backed by new
    `org.aetherium.bytecode.analyze.BytecodeAnalyzer`.
- New module `aetherium-testsuite` (depends on core+bytecode+native; nothing depends on it):
  Chaos Engineering engine that mutates dummy mod bytecode (`TRUNCATED`, `BITFLIP`,
  `HEADER_CORRUPT`, `TYPE_CONFUSION`, `STACK_UNDERFLOW`) and abuses FFM (use-after-free,
  out-of-bounds, alloc pressure — all FFM-guarded, never wild pointers).
- `ChaosHarness` runs 600 mods + ~100 native tasks on `newVirtualThreadPerTaskExecutor()`
  (≈700 virtual threads), asserting the framework contains every failure with **zero escapes**
  and the JVM never crashes. Verified run: 527 reverted / 73 transformed / 100 native contained /
  0 escapes → PASS.
- Bilingual docs `docs/{en,ru}/cli.md` and `docs/{en,ru}/testsuite.md`; docs index + README updated.

**RU**
- `aetherium-cli` расширен до реального диспетчера команд: `init`, `analyze`, `selftest`,
  `preflight`, `chaos` и `--help`.
  - `init <name>` создаёт полный мод-проект под AGPL-3.0 (скрипты сборки, `neoforge.mods.toml`,
    пример мода с API `ComputePipeline`, AGPL-заголовки в файлах). Имя нормализуется в валидный
    modId; сгенерированный мод компилируется с API Aetherium **без шаблонного кода**.
  - `analyze <path>` статически проверяет `.class`/`.jar`/каталог против базовой версии загрузчика
    (версия class-файла + ASM `CheckClassAdapter.verify`); на основе нового `BytecodeAnalyzer`.
- Новый модуль `aetherium-testsuite` (зависит от core+bytecode+native; от него ничто не зависит):
  движок Chaos Engineering, мутирующий фиктивный байт-код (`TRUNCATED`, `BITFLIP`,
  `HEADER_CORRUPT`, `TYPE_CONFUSION`, `STACK_UNDERFLOW`) и злоупотребляющий FFM (use-after-free,
  выход за границы, давление аллокаций — всё под защитой FFM, без диких указателей).
- `ChaosHarness` запускает 600 модов + ~100 нативных задач на `newVirtualThreadPerTaskExecutor()`
  (≈700 виртуальных потоков), утверждая, что фреймворк локализует каждый сбой с **нулём escape**
  и JVM не падает. Проверенный прогон: 527 откатов / 73 трансформации / 100 нативных локализованы /
  0 escape → PASS.
- Двуязычные доки `docs/{en,ru}/cli.md` и `docs/{en,ru}/testsuite.md`; индекс доков + README обновлены.

### Added — Native bridge, Vulkan scaffold, Pre-Flight Check / Нативный мост, каркас Vulkan, Pre-Flight (2026-06-13)

**EN**
- Implemented `aetherium-native` (depends only on `core`): C++ core (`src/main/cpp/aetherium_native.cpp`,
  AGPL header) compiled to `libaetherium_native.so` via CMake from Gradle (`compileNative`),
  bundled into the jar at `native/`. The `.so` has **no hard `libvulkan` dependency** (Vulkan via
  `dlopen`), verified with `ldd`.
- FFM bindings: `NativeLibrary` builds `MethodHandle` downcalls once (`O(1)` invocation),
  `Arena`-scoped lifetime; `NativeBridge` is the allow-listed surface with Arena-owned memory
  crossing the boundary (`allocateAndSum`); ABI version checked against the C source.
- Vulkan **hardware-access scaffold** (no shader logic): `aeth_vk_probe` creates a transient
  instance, enumerates physical devices + queue families; surfaced as `VulkanProbe`. Compute
  pipelines `PureJavaComputePipeline` / `NativeComputePipeline` implement core `ComputePipeline`
  (no-boilerplate: mods only see the interface).
- **Pre-Flight Check** (`org.aetherium.loader.PreFlightCheck`): runs a dummy ASM transform
  (`EngineSelfTest`) + dummy native allocation (`NativeProbe`), resolves the compute tier via
  `CapabilityRegistry`. Total/non-throwing; on native failure it degrades to `PURE_JAVA` and the
  launch proceeds.
- **Bilingual diagnostic translator** (`org.aetherium.core.diag.DiagnosticTranslator` + `Explanation`):
  maps raw `UnsatisfiedLinkError`/`ClassFormatError`/`VerifyError`/`BootstrapMethodError`/… to plain
  English+Russian explanations and structured `Diagnostic`s, without crashing.
- New CLI command `aetherium-cli preflight`. Verified both paths: healthy → tier `FFM`, Vulkan
  available (3 devices / 6 queue families via Mesa); forced-missing lib → `PURE_JAVA`, `LAUNCH
  ALLOWED` with a bilingual `AE-NATIVE-001` warning. CLI runs with `--enable-native-access`.
- Updated `docs/{en,ru}/native-bridge.md` (implementation status, Pre-Flight, diagnostics).

**RU**
- Реализован `aetherium-native` (зависит только от `core`): ядро C++ (`aetherium_native.cpp`,
  заголовок AGPL) компилируется в `libaetherium_native.so` через CMake из Gradle (`compileNative`)
  и упаковывается в jar в `native/`. У `.so` **нет жёсткой зависимости от `libvulkan`** (Vulkan
  через `dlopen`), проверено `ldd`.
- FFM-привязки: `NativeLibrary` строит downcall-`MethodHandle` один раз (вызов `O(1)`), время жизни
  в области `Arena`; `NativeBridge` — поверхность из белого списка с Arena-памятью, пересекающей
  границу (`allocateAndSum`); версия ABI сверяется с C-исходником.
- **Каркас доступа к оборудованию Vulkan** (без логики шейдеров): `aeth_vk_probe` создаёт временный
  instance, перечисляет физические устройства + семейства очередей; представлен как `VulkanProbe`.
  Конвейеры `PureJavaComputePipeline` / `NativeComputePipeline` реализуют core `ComputePipeline`
  (без шаблонов: моды видят лишь интерфейс).
- **Pre-Flight Check** (`org.aetherium.loader.PreFlightCheck`): выполняет фиктивную ASM-трансформацию
  (`EngineSelfTest`) + фиктивную нативную аллокацию (`NativeProbe`), разрешает уровень через
  `CapabilityRegistry`. Тотален/не бросает; при нативном сбое деградирует на `PURE_JAVA`, запуск
  продолжается.
- **Двуязычный транслятор диагностики** (`DiagnosticTranslator` + `Explanation`): сопоставляет сырые
  `UnsatisfiedLinkError`/`ClassFormatError`/`VerifyError`/`BootstrapMethodError`/… с понятными
  объяснениями на английском+русском и структурированными `Diagnostic`, без краха.
- Новая команда CLI `aetherium-cli preflight`. Проверены оба пути: исправно → уровень `FFM`, Vulkan
  доступен (3 устройства / 6 семейств очередей через Mesa); принудительно нет библиотеки →
  `PURE_JAVA`, `LAUNCH ALLOWED` с двуязычным предупреждением `AE-NATIVE-001`.
- Обновлены `docs/{en,ru}/native-bridge.md` (статус реализации, Pre-Flight, диагностика).

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
