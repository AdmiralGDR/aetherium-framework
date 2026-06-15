# Changelog

All notable changes to the Aetherium Framework are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the
project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Все значимые изменения Aetherium Framework документируются здесь. Формат основан на
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); проект следует
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Added — Declarative content + autonomous DataGen (eliminating "JSON Hell") (2026-06-15)

**EN**
- **`aetherium-content` (zero-boilerplate registries):** one annotation defines a whole block/item.
  `@AetheriumBlock(name, hardness, resistance, requiresTool, dropSelf, displayName)` and
  `@AetheriumItem(name, maxStackSize, displayName)` — empty class body, no `net.minecraft` import, no
  `DeferredRegister`, no JSON. A `javax.annotation.processing` processor
  (`AetheriumContentProcessor`, registered via `META-INF/services`) runs inside `javac`.
- **`aetherium-datagen` (autonomous, strictly pure):** turns declarative `ContentEntry` records into
  raw resource-pack/data-pack JSON — block model (`cube_all`), item model, blockstate, self-drop loot
  table, merged `lang/en_us.json`. **No Minecraft/NeoForge type, no external JSON library, no
  `GatherDataEvent`** — a build-time file generator. Targets the 1.21.1 data-pack layout (singular
  `loot_table/`). The processor writes the JSON into the compiler's `CLASS_OUTPUT` (so it lands in the
  jar automatically) plus a line-oriented runtime index `META-INF/aetherium/content.index`.
- **Loader bridging:** `aetherium-loader.AetheriumContentRegistrar` reads the content index from the
  classpath and, on NeoForge `RegisterEvent`, builds each `Block` (`strength` +
  `requiresCorrectToolForDrops`) in the BLOCK phase and **auto-wraps it in a `BlockItem`** (plus
  standalone `Item`s) in the ITEM phase — failures contained per entry. Wired in the entrypoint.
- **Zero-config:** the Gradle plugin now auto-adds `aetherium-content` as dependency + annotation
  processor and injects `-Aaetherium.modId=<mod id>`, so `@AetheriumBlock(name = "…")` needs no modId.
- **E2E verified:** `aetherium-testmod` gains `AetheriumSteelBlock` (one annotation) → its jar
  contains the compiled class + 5 generated JSON + the index; the `examples/loomthreader-demo`
  `aetheriumBundle` jar contains `DemoSteelBlock.class` + 5 auto-namespaced JSON + index + the embedded
  runtime. `ContentIndex.load` round-trips from the built jar.
- Bilingual `docs/{en,ru}/content.md`; docs index + README module tables updated.

**RU**
- **`aetherium-content` (реестры без шаблонов):** одна аннотация определяет целый блок/предмет.
  `@AetheriumBlock(name, hardness, resistance, requiresTool, dropSelf, displayName)` и
  `@AetheriumItem(name, maxStackSize, displayName)` — пустое тело класса, без импорта
  `net.minecraft`, без `DeferredRegister`, без JSON. Процессор `javax.annotation.processing`
  (`AetheriumContentProcessor`, зарегистрирован через `META-INF/services`) работает внутри `javac`.
- **`aetherium-datagen` (автономный, строго чистый):** превращает декларативные записи
  `ContentEntry` в «сырой» JSON ресурс-/дата-пака — модель блока (`cube_all`), модель предмета,
  blockstate, loot-таблицу самовыпадения, объединённый `lang/en_us.json`. **Без типов
  Minecraft/NeoForge, без внешних JSON-библиотек, без `GatherDataEvent`** — генератор файлов на этапе
  сборки. Ориентирован на раскладку дата-пака 1.21.1 (единственное число `loot_table/`). Процессор
  пишет JSON в `CLASS_OUTPUT` компилятора (он автоматически попадает в jar) плюс построчный
  рантайм-индекс `META-INF/aetherium/content.index`.
- **Мост загрузчика:** `aetherium-loader.AetheriumContentRegistrar` читает индекс контента из classpath
  и на `RegisterEvent` NeoForge строит каждый `Block` (`strength` + `requiresCorrectToolForDrops`) в
  фазе BLOCK и **автоматически оборачивает его в `BlockItem`** (плюс отдельные `Item`) в фазе ITEM —
  ошибки изолируются по записи. Подключено в точке входа.
- **Нулевая конфигурация:** Gradle-плагин теперь автоматически добавляет `aetherium-content` как
  зависимость + аннотационный процессор и подставляет `-Aaetherium.modId=<mod id>`, так что
  `@AetheriumBlock(name = "…")` не требует modId.
- **Проверено E2E:** в `aetherium-testmod` добавлен `AetheriumSteelBlock` (одна аннотация) → его jar
  содержит скомпилированный класс + 5 сгенерированных JSON + индекс; jar `aetheriumBundle` из
  `examples/loomthreader-demo` содержит `DemoSteelBlock.class` + 5 авто-неймспейснутых JSON + индекс +
  встроенный рантайм. `ContentIndex.load` корректно читает данные из собранного jar.
- Двуязычные `docs/{en,ru}/content.md`; индекс docs и таблицы модулей в README обновлены.

### Added — DevEx infrastructure + Platform Abstraction Layer (Maven, Gradle plugin, relocation, Edge) (2026-06-15)

**EN**
- **Maven publishing:** all consumable library modules (`aetherium-core`, `-bytecode`, `-native`,
  `-edge`, `-network`, `-gfx`) apply `maven-publish` and publish to `mavenLocal`, so dependent mods
  resolve `org.aetherium:<module>:1.0.0-SNAPSHOT` by coordinate instead of vendoring a physical jar.
- **Zero-config Gradle plugin:** new `aetherium-gradle-plugin` (`id "org.aetherium.gradle"`). A single
  `aetherium { version = "…" }` block wires the toolchain (Java 21 + `--enable-preview`), repositories,
  the framework dependencies, and an `aetheriumBundle` task that JarJar-style embeds the Aetherium
  classes into the mod artifact. The plugin module is **not** compiled with `--enable-preview` (its
  classes load in the Gradle daemon). Verified end-to-end via `examples/loomthreader-demo` consuming
  the framework purely through Gradle (bundle embeds 49 Aetherium classes + the mod class).
- **ASM namespace relocation:** `aetherium-bytecode.relocate.{Relocation, ClassRelocator}` shade a
  bundled library into a private namespace (e.g. `com.google.common` → `org.aetherium.shadow.guava`)
  via `ClassRemapper` — the correct, descriptor-aware way to relocate. `loader.DependencyFlattener`
  ships a `commonLibraryRelocations()` set (guava/gson/kotlin/jackson/commons/fastutil). The public
  API takes/returns only `byte[]`/`Relocation`, so ASM never leaks past `aetherium-bytecode`.
- **Classpath-aware analysis (false-positive fix):** `aetherium-cli analyze … --classpath <cp>` builds
  a `URLClassLoader` (parented to the platform loader) and threads it into `BytecodeAnalyzer`, so the
  verifier resolves vanilla `net.minecraft` types instead of flagging them. Verified: a class merging
  two subtypes of an off-classpath supertype reports a false positive without `--classpath` and
  verifies CLEAN with it.
- **Platform Abstraction Layer (the "Edge"):** new `aetherium-edge` module defines a strictly abstract,
  loader-agnostic Hook SPI — `EntityHandle`, `EntityAccess`, `EdgeEvents`, `PlatformBridge`, and
  `Platform.bridge()` (ServiceLoader with a no-op fallback). `aetherium-loader` provides the NeoForge
  implementation (`NeoForgePlatformBridge` + `NeoForgeEntityHandle` + `NeoForgePlatformEvents`,
  registered via `META-INF/services`), the only place that touches Minecraft types. Mods push
  off-heap-computed results back into live entities without importing a single game type.
- Bilingual `docs/{en,ru}/{gradle-plugin,edge-pal,network,gfx}.md`; docs index + README module tables
  updated. AGPL-3.0 headers on all new files.

**RU**
- **Публикация в Maven:** все потребляемые библиотечные модули (`aetherium-core`, `-bytecode`,
  `-native`, `-edge`, `-network`, `-gfx`) применяют `maven-publish` и публикуются в `mavenLocal`,
  поэтому зависимые моды резолвят `org.aetherium:<module>:1.0.0-SNAPSHOT` по координате, а не вендорят
  физический jar.
- **Gradle-плагин с нулевой конфигурацией:** новый `aetherium-gradle-plugin` (`id "org.aetherium.gradle"`).
  Один блок `aetherium { version = "…" }` настраивает тулчейн (Java 21 + `--enable-preview`),
  репозитории, зависимости фреймворка и задачу `aetheriumBundle`, встраивающую классы Aetherium в
  артефакт мода (в стиле JarJar). Модуль плагина **не** компилируется с `--enable-preview` (его классы
  грузятся в демоне Gradle). Проверено end-to-end через `examples/loomthreader-demo`, потребляющий
  фреймворк исключительно через Gradle (bundle встраивает 49 классов Aetherium + класс мода).
- **ASM-релокация пространств имён:** `aetherium-bytecode.relocate.{Relocation, ClassRelocator}`
  «затеняют» встроенную библиотеку в приватное пространство имён (напр. `com.google.common` →
  `org.aetherium.shadow.guava`) через `ClassRemapper` — корректный способ с учётом дескрипторов.
  `loader.DependencyFlattener` поставляет набор `commonLibraryRelocations()`
  (guava/gson/kotlin/jackson/commons/fastutil). Публичный API принимает/возвращает только
  `byte[]`/`Relocation`, поэтому ASM не утекает за пределы `aetherium-bytecode`.
- **Анализ с учётом classpath (исправление ложных срабатываний):** `aetherium-cli analyze … --classpath <cp>`
  строит `URLClassLoader` (родитель — платформенный загрузчик) и пробрасывает его в `BytecodeAnalyzer`,
  так что верификатор резолвит ванильные типы `net.minecraft`, а не помечает их. Проверено: класс,
  объединяющий два подтипа суперкласса вне classpath, даёт ложное срабатывание без `--classpath` и
  проходит как CLEAN с ним.
- **Слой абстракции платформы («Edge»):** новый модуль `aetherium-edge` определяет строго абстрактный,
  независимый от загрузчика Hook SPI — `EntityHandle`, `EntityAccess`, `EdgeEvents`, `PlatformBridge` и
  `Platform.bridge()` (ServiceLoader с no-op откатом). `aetherium-loader` предоставляет реализацию для
  NeoForge (`NeoForgePlatformBridge` + `NeoForgeEntityHandle` + `NeoForgePlatformEvents`,
  зарегистрировано через `META-INF/services`) — единственное место, касающееся типов Minecraft. Моды
  возвращают вычисленные off-heap результаты в живые сущности, не импортируя ни одного игрового типа.
- Двуязычные `docs/{en,ru}/{gradle-plugin,edge-pal,network,gfx}.md`; индекс docs и таблицы модулей в
  README обновлены. Заголовки AGPL-3.0 во всех новых файлах.

### Added — Performance architecture (StructArena, async tick, dedup, SIMD/mmap) (2026-06-13)

**EN**
- **Data-oriented memory:** `core.compute.StructArena` + `StructLayout` + `StructField` — contiguous
  off-heap Array-of-Structs via FFM for cache-friendly bulk entity updates (zero GC, `O(1)` access).
- **Async tick:** `core.tick.AetheriumTickEngine` runs tasks on virtual threads, joins at a 50 ms
  **Sync Barrier**, commits on the main thread (no `ConcurrentModificationException`); zero-boilerplate
  `@AetheriumAsyncTick` annotation + `AsyncTickTask` SPI + `TickReport`. Tasks that throw/timeout are
  contained, never crashing the tick.
- **Dependency deduplication (Library Hell):** `loader.DependencyFlattener` resolves embedded mod
  libraries to one winner per `group:artifact` (highest version) with a conflict log. Verified:
  5 conflicting libs → 2 winners, 3 deduped.
- **SIMD & mmap bridges:** `core.simd.SimdMath` (scalar now, Vector-API-detect hook) and
  `core.io.MappedRegion` (FFM `FileChannel.map(..., Arena)` zero-GC streaming).
- **Verified stress test:** new `aetherium-testsuite.EntityChaosHarness` + `aetherium-cli entitysim` —
  10,000 entities × 200 ticks × 250 virtual threads/tick = 2,000,000 updates in ~111 ms
  (~18M updates/sec), slowest tick 25.9 ms (<50 ms budget), **0 escapes, no deadlock, 0 mismatches**,
  annotation DX OK → PASS.
- Bilingual `docs/{en,ru}/performance.md`; docs index updated. AGPL-3.0 headers on all new files.

**RU**
- **Data-oriented память:** `core.compute.StructArena` + `StructLayout` + `StructField` — непрерывный
  off-heap Array-of-Structs через FFM для кэш-дружественных массовых обновлений сущностей (zero GC,
  доступ `O(1)`).
- **Асинхронный тик:** `core.tick.AetheriumTickEngine` запускает задачи на виртуальных потоках,
  объединяет 50-мс **Sync-барьером**, фиксирует на главном потоке (без `ConcurrentModificationException`);
  аннотация `@AetheriumAsyncTick` без шаблонного кода + SPI `AsyncTickTask` + `TickReport`. Задачи с
  исключениями/таймаутами локализуются, не роняя тик.
- **Дедупликация зависимостей (Library Hell):** `loader.DependencyFlattener` сводит встроенные
  библиотеки к одному победителю на `group:artifact` (наивысшая версия) с журналом конфликтов.
  Проверено: 5 конфликтующих → 2 победителя, 3 дедуплицировано.
- **Мосты SIMD и mmap:** `core.simd.SimdMath` (скаляр сейчас, хук определения Vector API) и
  `core.io.MappedRegion` (FFM `FileChannel.map(..., Arena)`, потоки без GC).
- **Проверенный стресс-тест:** новый `EntityChaosHarness` + `aetherium-cli entitysim` — 10 000
  сущностей × 200 тиков × 250 vthreads/тик = 2 000 000 обновлений за ~111 мс (~18M/сек), самый
  медленный тик 25.9 мс (<50 мс), **0 escape, нет взаимоблокировок, 0 несоответствий**, DX OK → PASS.
- Двуязычный `docs/{en,ru}/performance.md`; индекс доков обновлён. Заголовки AGPL-3.0 во всех файлах.

### Added — Runtime class interception (ModLauncher) / Перехват классов во время выполнения (2026-06-13)

**EN**
- Wired the "missing link": mod classes are now transformed at class-load time via ModLauncher.
  - `AetheriumTransformationService` (`ITransformationService`) — registered via
    `META-INF/services/cpw.mods.modlauncher.api.ITransformationService`; the discovered entry.
  - `AetheriumLaunchPlugin` (`ILaunchPluginService`) — registered via its services file; the
    real per-class hook. `handlesClass` is the performance gate; `processClass` delegates to the
    pure `BytecodeEngine` (node→bytes→engine→node), returning original bytes on any failure.
  - `AetheriumNamespaces` — namespace filter: hard deny-list (net.minecraft, net.neoforged,
    cpw.mods, JDK, **and Aetherium's own framework packages**) + allow-list (test mod default,
    extensible via `-Daetherium.transform.packages`). The engine never runs on vanilla/NeoForge.
  - `AetheriumTransformEngine` — loader-side holder owning one pure engine (shared `SymbolManifest`
    + `DispatchLoweringTransformer`), logging diagnostics via SLF4J. `DispatchBootstrap` reuses the
    same manifest so dispatch-table IDs always agree.
- **Separation preserved:** `aetherium-bytecode` still imports no ModLauncher/NeoForge type; only
  `aetherium-loader` does. ModLauncher's `ITransformer` matches exact class names, so broad
  namespace interception uses `ILaunchPluginService` (the same split Mixin uses) — documented.
- **Verified (GUI not launched):** loader compiles against MC 1.21.1 + NeoForge; both services
  implement their interfaces (javap); `ServiceLoader` discovers both from the built artifacts;
  filter returns `[]` for net.minecraft/net.neoforged/self and `[AFTER]` for the test-mod
  namespace; `processClass` lowers an `org/aetherium/testmod/Demo` API call from `INVOKESTATIC`
  to `invokedynamic` (1→0 static, 0→1 indy). Full build green.
- Updated `docs/{en,ru}/game-integration.md` (runtime-interception section).

**RU**
- Подключено «недостающее звено»: классы модов теперь преобразуются во время загрузки через ModLauncher.
  - `AetheriumTransformationService` (`ITransformationService`) — через
    `META-INF/services/cpw.mods.modlauncher.api.ITransformationService`; обнаруживаемая точка входа.
  - `AetheriumLaunchPlugin` (`ILaunchPluginService`) — через свой services-файл; реальный per-class
    хук. `handlesClass` — барьер производительности; `processClass` делегирует чистому
    `BytecodeEngine` (узел→байты→движок→узел), возвращая исходные байты при любом сбое.
  - `AetheriumNamespaces` — фильтр пространств имён: жёсткий deny-list (net.minecraft, net.neoforged,
    cpw.mods, JDK **и собственные пакеты Aetherium**) + allow-list (тест-мод по умолчанию, расширяемо
    через `-Daetherium.transform.packages`). Движок не работает на ванили/NeoForge.
  - `AetheriumTransformEngine` — держатель на стороне загрузчика с одним чистым движком (общий
    `SymbolManifest` + `DispatchLoweringTransformer`), логирует диагностику через SLF4J.
    `DispatchBootstrap` переиспользует тот же манифест, чтобы ID таблицы всегда совпадали.
- **Разделение сохранено:** `aetherium-bytecode` не импортирует типы ModLauncher/NeoForge; только
  `aetherium-loader`. `ITransformer` ModLauncher сопоставляет точные имена, поэтому широкий перехват
  использует `ILaunchPluginService` (то же разделение у Mixin) — задокументировано.
- **Проверено (GUI не запускается):** загрузчик компилируется против MC 1.21.1 + NeoForge; оба
  сервиса реализуют интерфейсы (javap); `ServiceLoader` находит оба из артефактов; фильтр возвращает
  `[]` для net.minecraft/net.neoforged/self и `[AFTER]` для пространства тест-мода; `processClass`
  понижает вызов API класса `org/aetherium/testmod/Demo` из `INVOKESTATIC` в `invokedynamic`
  (1→0 static, 0→1 indy). Сборка зелёная.
- Обновлён `docs/{en,ru}/game-integration.md` (раздел перехвата во время выполнения).

### Added — NeoForge game integration (ModDevGradle) / Интеграция с игрой NeoForge (2026-06-13)

**EN**
- `aetherium-loader` now applies **ModDevGradle** (`net.neoforged.moddev` 2.0.141, via the
  catalog) and compiles against the decompiled **Minecraft 1.21.1 + NeoForge 21.1.233**
  classpath; a `runClient` dev task is generated. NeoForge pin bumped 21.1.93 → 21.1.233
  (locally cached userdev).
- Implemented the `@Mod` entrypoint `AetheriumNeoForgeEntrypoint` — the *only* class that
  imports NeoForge. On `FMLConstructModEvent` it (1) runs the `PreFlightCheck`, (2) installs
  the `invokedynamic` dispatch table (`DispatchBootstrap`), and (3) discovers
  `AetheriumMod`s via `ServiceLoader` and calls `onInitialize(AetheriumContext)`.
- Added the loader-agnostic mod SPI to `aetherium-core` (pure): `mod.AetheriumMod` +
  `mod.AetheriumContext` (`log`, `computeTier`). No NeoForge/Minecraft types.
- New module `aetherium-testmod` (`HelloAetheriumMod`) depending **only** on `aetherium-core`;
  registers via `META-INF/services` and makes a single Aetherium API call at init.
- **Architectural rule verified:** grep confirms zero `net.neoforged`/`net.minecraft`
  references in core/bytecode/native/testmod; only the loader entrypoint touches the game.
  Full build green; loader compiled against ~5300 decompiled MC sources; `runClient` present.
  (GUI intentionally not launched; compile + classpath resolution verified.)
- Bilingual docs `docs/{en,ru}/game-integration.md`; build-system.md NeoForge section updated
  (previously "deferred" → now wired); docs index + README + CHANGELOG updated.

**RU**
- `aetherium-loader` теперь применяет **ModDevGradle** (`net.neoforged.moddev` 2.0.141, из
  каталога) и компилируется против декомпилированного classpath **Minecraft 1.21.1 + NeoForge
  21.1.233**; создаётся dev-задача `runClient`. Пин NeoForge поднят 21.1.93 → 21.1.233
  (локально закэшированный userdev).
- Реализована точка входа `@Mod` `AetheriumNeoForgeEntrypoint` — *единственный* класс,
  импортирующий NeoForge. На `FMLConstructModEvent` он (1) выполняет `PreFlightCheck`,
  (2) устанавливает таблицу диспетчеризации `invokedynamic` (`DispatchBootstrap`) и
  (3) находит `AetheriumMod` через `ServiceLoader` и вызывает `onInitialize(AetheriumContext)`.
- В `aetherium-core` добавлен независимый от загрузчика SPI мода (чистый): `mod.AetheriumMod`
  + `mod.AetheriumContext` (`log`, `computeTier`). Без типов NeoForge/Minecraft.
- Новый модуль `aetherium-testmod` (`HelloAetheriumMod`), зависящий **только** от
  `aetherium-core`; регистрируется через `META-INF/services` и делает один вызов API при init.
- **Архитектурное правило проверено:** grep подтверждает ноль ссылок
  `net.neoforged`/`net.minecraft` в core/bytecode/native/testmod; только точка входа загрузчика
  касается игры. Сборка зелёная; загрузчик скомпилирован против ~5300 декомпилированных
  исходников MC; `runClient` присутствует. (GUI намеренно не запускается; проверены компиляция
  и разрешение classpath.)
- Двуязычные доки `docs/{en,ru}/game-integration.md`; раздел NeoForge в build-system.md обновлён
  (было «отложено» → теперь подключено); индекс доков + README + CHANGELOG обновлены.

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
