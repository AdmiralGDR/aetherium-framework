# Changelog

All notable changes to the Aetherium Framework are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the
project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Все значимые изменения Aetherium Framework документируются здесь. Формат основан на
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); проект следует
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Added — "The Gameplay & UI Monolith": UI, content behaviors, advanced GFX, tree sync, SPIR-V math, gameplay PAL, structural hot-swap, universal jar (2026-06-24)

**EN**
- **UI framework (`aetherium-ui`):** a declarative, Flexbox-like GUI with **no Minecraft imports**. Build
  a screen with the `Ui` factory (`column`/`row`/`label`/`button`/`spacer`, fluent self-typed modifiers);
  `FlexLayout` computes absolute boxes (grow/justify/align/STRETCH), `UiRuntime` paints + hit-tests through
  a two-method `UiRenderer` SPI the loader adapts over `GuiGraphics`. Layout/paint/click all run offline.
  See [`docs/en/ui.md`](docs/en/ui.md).
- **SPIR-V `Math.*` polyfills (`aetherium-compute`):** `SpirvKernelBuilder` now imports **GLSL.std.450**
  and lowers a `java.lang.Math` call (`sin/cos/tan/sqrt/exp/log/abs/floor`) to an `OpExtInst`. A
  `Math.sin` kernel emits `OpExtInstImport "GLSL.std.450"` + `OpExtInst … Sin` (verified by `aetherium spirv`).
- **Content behaviors (`aetherium-content`):** `@AetheriumBlock`/`@AetheriumItem` accept a `behavior` class;
  when it implements `AetheriumMachineLogic` the processor records a `behaviors.index` and the loader
  auto-registers a ticking `BlockEntity` (no `BlockEntityType`/ticker/NBT boilerplate). `MachineContext`/
  `MachineState` are pure and unit-testable.
- **Advanced GFX (`aetherium-gfx`):** raw `VertexSink` (VertexConsumer mirror), `Mat4` + `PoseStack`
  (matrix abstractions), `RenderLayer` (RenderType), `Geometry.emitCuboid`, and skeletal-animation hooks
  `Bone`/`Skeleton` (forward kinematics) + `AetheriumModel`/`ModelRegistry` — the surface a GeckoLib-style
  engine needs through the PAL.
- **Hierarchical sync (`aetherium-network`):** `TreeNode` + `Tree` + `TreeCodec` (and `TreeSyncPacket`/
  `TreeSyncCodec`) serialize NBT/JSON-like business logic over the same buffer SPI as the flat delta;
  decoding is depth- and size-hardened. Added zero-GC `writeBytes`/`readBytes` defaults.
- **Gameplay PAL (`aetherium-edge`):** `PlayerAccess`/`PlayerHandle`, string-id `InventoryAccess`, and
  cancellable `onBlockInteract`/`onItemUse`/`onEntityAttack` events (return `InteractionResult`). New members
  are `default`, so the existing loader bridge is unchanged.
- **Structural hot-swap (`aetherium-hotswap`):** `DcevmSupport` detects DCEVM/HotswapAgent;
  `HotSwapEngine.structuralRedefineSupported()` reports when adding/removing live members is possible.
- **Universal jar (`aetherium-gradle-plugin`):** `aetherium { universal = true }` builds a single
  `<name>-universal.jar` embedding core + loader + dual NeoForge/Fabric metadata, manifest-stamped
  `Aetherium-Universal: true`.
- **CLI:** new `ui`, `gfx`, `tree`, `behavior`, `gameplay` self-test commands; `spirv` now reports the
  `Math.sin → GLSL.std.450` mapping. All pure modules keep the strict "no Minecraft imports" rule.

**RU**
- **UI-фреймворк (`aetherium-ui`):** декларативный Flexbox-подобный GUI **без импортов Minecraft**. Экран
  строится фабрикой `Ui` (`column`/`row`/`label`/`button`/`spacer`); `FlexLayout` вычисляет боксы
  (grow/justify/align/STRETCH), `UiRuntime` рисует и обрабатывает клики через SPI `UiRenderer` из двух
  методов. Раскладка/отрисовка/клик работают офлайн. См. [`docs/ru/ui.md`](docs/ru/ui.md).
- **Полифилы `Math.*` для SPIR-V (`aetherium-compute`):** `SpirvKernelBuilder` импортирует **GLSL.std.450**
  и понижает вызов `java.lang.Math` (`sin/cos/tan/sqrt/exp/log/abs/floor`) в `OpExtInst`. Ядро `Math.sin`
  выпускает `OpExtInstImport "GLSL.std.450"` + `OpExtInst … Sin` (проверяется `aetherium spirv`).
- **Поведения контента (`aetherium-content`):** `@AetheriumBlock`/`@AetheriumItem` принимают класс
  `behavior`; если он реализует `AetheriumMachineLogic`, процессор пишет `behaviors.index`, а загрузчик
  авто-регистрирует тикающую `BlockEntity` (без шаблона). `MachineContext`/`MachineState` чистые и тестируемы.
- **Продвинутый GFX (`aetherium-gfx`):** сырой `VertexSink`, `Mat4` + `PoseStack`, `RenderLayer`,
  `Geometry.emitCuboid` и хуки скелетной анимации `Bone`/`Skeleton` + `AetheriumModel`/`ModelRegistry`.
- **Иерархическая синхронизация (`aetherium-network`):** `TreeNode` + `Tree` + `TreeCodec` (и
  `TreeSyncPacket`/`TreeSyncCodec`) сериализуют NBT/JSON-like логику поверх того же SPI буфера; декодирование
  укреплено по глубине и размеру. Добавлены zero-GC default-методы `writeBytes`/`readBytes`.
- **Геймплейный PAL (`aetherium-edge`):** `PlayerAccess`/`PlayerHandle`, `InventoryAccess` по строковым id и
  отменяемые события `onBlockInteract`/`onItemUse`/`onEntityAttack` (возвращают `InteractionResult`). Новые
  методы — `default`, мост загрузчика не меняется.
- **Структурный hot-swap (`aetherium-hotswap`):** `DcevmSupport` детектит DCEVM/HotswapAgent;
  `HotSwapEngine.structuralRedefineSupported()` сообщает о возможности живого добавления/удаления членов.
- **Универсальный jar (`aetherium-gradle-plugin`):** `aetherium { universal = true }` собирает единый
  `<name>-universal.jar` (core + loader + двойные метаданные NeoForge/Fabric, штамп `Aetherium-Universal`).
- **CLI:** новые команды `ui`, `gfx`, `tree`, `behavior`, `gameplay`; `spirv` сообщает о маппинге
  `Math.sin → GLSL.std.450`. Все чистые модули соблюдают правило «без импортов Minecraft».

### Added — "The Monolith Polish": Kotlin DSL, fuzzer, LSP backend, zero-config auto-wiring (2026-06-23)

**EN**
- **Kotlin DSL (`aetherium-ktx`):** zero-overhead, type-safe Kotlin builder blocks over the injector
  (`HookDag`), `StructArena`, and DataGen. Every entry is an `inline`/extension function that lowers to the
  identical Java fluent call — hooks still bind to the `O(1)` `invokedynamic` `HookTable`, so there is **no
  runtime reflection** and no extra dispatch layer. `injector { inject("…Entity::tick") { hook("id") {
  cancelIf { intArg(0) > 100 } } } }.install()` replaces the verbose `inClass().method().at().captureArguments()
  .hook(id, ctx -> { … cancel })…commit(); installHooks()`. Built with `-Xjvm-enable-preview` so Kotlin stays
  in lock-step with the FFM-preview Java modules. See [`docs/en/ktx.md`](docs/en/ktx.md).
- **Aggressive fuzzer (`aetherium-fuzzer`):** a deterministic, reproducibly-seeded coverage fuzzer for the
  SPIR-V compiler and the WASM sandbox/bridge. Four targets bombard `SpirvModule.wrap`/`verify`/dispatch, the
  Java→SPIR-V ASM front-end, the `.wasm` loader, and the `StructArena`↔WASM bridge with empties, runts,
  unaligned and magic-prefixed blobs, bit-flipped real binaries, and out-of-bounds memory requests; every case
  is caught, so a passing campaign proves no input crashes the JVM/host. Runs automatically during
  `./gradlew check`. See [`docs/en/fuzzer.md`](docs/en/fuzzer.md).
- **Bugs the fuzzer surfaced (fixed):** (1) `SpirvModule` header accessors threw `IndexOutOfBoundsException`
  on a truncated/external binary — now bounds-safe, with a new public `SpirvModule.wrap(byte[])`; (2) the
  compiler leaked raw ASM exceptions and an IAE for a non-positive `localSizeX` — `compileBytes(byte[])` now
  normalizes every failure to `UnsupportedShaderException`; (3) `StructArenaWasmBridge.runPhysics` leaked an
  off-heap allocation per call (native-memory exhaustion under load) — now a bounded, reused grow-on-demand
  scratch buffer.
- **LSP backend (`aetherium-cli`):** the CLI now doubles as a Language Server — `Content-Length`-framed
  JSON-RPC over stdio (dependency-free JSON), vanilla-method injection-point autocomplete
  (`VanillaMethodIndex`), and **pre-compile hook-conflict prediction** (`ConflictPredictor`) that runs the
  real `LiveHookGraph`/`HookDag` to flag ordering cycles, duplicate ids, invalid anchors, and competing
  cancellations. `aetherium lsp` (self-test) / `aetherium lsp --serve` (stdio). See [`docs/en/lsp.md`](docs/en/lsp.md).
- **Zero-config auto-wiring (`@AetheriumInit`):** a developer annotates a `public static void
  m(AetheriumContext)` and writes **no** entrypoint class and **no** services file. `AetheriumInitProcessor`
  discovers the methods at compile time, orders them into a deterministic DAG (`InitOrdering`,
  `runBefore`/`runAfter`), and generates an `AetheriumMod` that invokes them by **direct static call** plus
  the `META-INF/services` registration — **no runtime reflection, no classpath scanning**. See
  [`docs/en/autowiring.md`](docs/en/autowiring.md).
- **CLI:** new `fuzz [n]` and `lsp [--serve]` commands.
- **Audit:** scanned `aetherium-core` + `aetherium-loader`; removed an unused import (`SymbolManifest`), no
  dead members found — already lean.

**RU**
- **Kotlin DSL (`aetherium-ktx`):** типобезопасные Kotlin-блоки без накладных расходов над инжектором
  (`HookDag`), `StructArena` и DataGen. Каждый вход — `inline`/extension-функция, понижающаяся к тем же
  Java-вызовам: хуки по-прежнему привязаны к `O(1)` `invokedynamic`-таблице `HookTable`, поэтому **нет
  рантайм-рефлексии** и лишнего слоя диспетчеризации. Собрано с `-Xjvm-enable-preview`, синхронно с
  FFM-preview Java-модулями. См. [`docs/ru/ktx.md`](docs/ru/ktx.md).
- **Агрессивный фаззер (`aetherium-fuzzer`):** детерминированный, воспроизводимо засеянный фаззер для
  компилятора SPIR-V и песочницы/моста WASM. Четыре цели бомбардируют `SpirvModule.wrap`/`verify`/диспетч,
  ASM-front-end Java→SPIR-V, загрузчик `.wasm` и мост `StructArena`↔WASM пустыми/огрызками/невыровненными/
  магия-и-мусор блобами, инверсиями реальных бинарей и запросами памяти вне границ; каждый случай перехвачен,
  поэтому успешная кампания доказывает, что вход не роняет JVM/хост. Выполняется автоматически при
  `./gradlew check`. См. [`docs/ru/fuzzer.md`](docs/ru/fuzzer.md).
- **Найденные фаззером баги (исправлены):** (1) аксессоры заголовка `SpirvModule` бросали
  `IndexOutOfBoundsException` на усечённом/внешнем бинаре — теперь безопасны по границам, добавлен публичный
  `SpirvModule.wrap(byte[])`; (2) компилятор «протекал» сырыми исключениями ASM и IAE при неположительном
  `localSizeX` — `compileBytes(byte[])` нормализует любой сбой в `UnsupportedShaderException`; (3)
  `StructArenaWasmBridge.runPhysics` «протекал» off-heap аллокацию на каждый вызов — теперь ограниченный
  переиспользуемый scratch-буфер.
- **LSP-бэкенд (`aetherium-cli`):** CLI теперь и Language Server — JSON-RPC с обрамлением `Content-Length` по
  stdio (JSON без зависимостей), автодополнение точек инъекции ванильных методов (`VanillaMethodIndex`) и
  **предсказание конфликтов хуков до компиляции** (`ConflictPredictor`) на настоящем `LiveHookGraph`/`HookDag`:
  циклы порядка, дубли id, неверные якоря, конкурирующие отмены. `aetherium lsp` / `aetherium lsp --serve`. См.
  [`docs/ru/lsp.md`](docs/ru/lsp.md).
- **Авто-связывание без конфигурации (`@AetheriumInit`):** разработчик помечает `public static void
  m(AetheriumContext)` и не пишет **ни** класс-entrypoint, **ни** services-файл. `AetheriumInitProcessor`
  находит методы во время компиляции, выстраивает их в детерминированный DAG (`InitOrdering`,
  `runBefore`/`runAfter`) и генерирует `AetheriumMod`, вызывающий их **прямым статическим вызовом**, плюс
  запись `META-INF/services` — **без рантайм-рефлексии и без сканирования classpath**. См.
  [`docs/ru/autowiring.md`](docs/ru/autowiring.md).
- **CLI:** новые команды `fuzz [n]` и `lsp [--serve]`.
- **Аудит:** просканированы `aetherium-core` + `aetherium-loader`; удалён неиспользуемый импорт
  (`SymbolManifest`), мёртвого кода не найдено — уже компактно.

### Added — "The Singularity": hot-swap, Java→SPIR-V, WASM sandbox, delta-sync (2026-06-22)

**EN**
- **Java→SPIR-V compiler (`aetherium-compute`):** a runtime compiler that turns a pure-Java method
  annotated `@AetheriumComputeShader` into a Vulkan **SPIR-V** binary. `JavaToSpirvCompiler` reads the
  kernel bytecode with ASM (never executing it), recognises the supported strict subset (primitives,
  primitive arrays, loops, `+ - *`; object allocation rejected), and `SpirvKernelBuilder` emits a
  structurally valid module for `dst[i] = a[i] OP b[i]` — header magic `0x07230203`, `GLCompute` entry
  point, `LocalSize` mode, std430 SSBO graph, `gl_GlobalInvocationID`. `SpirvModule#verify()` walks the
  word-stream; `SpirvVulkanDispatch` routes the binary into the `aetherium-native` Vulkan bridge (GPU when
  a device exists, CPU fallback otherwise). The float array-add kernel compiles to a 732-byte / 46-instruction
  module.
- **Live hot-swap engine (`aetherium-hotswap`):** `ClassFileWatcher` (a recursive `WatchService` over the
  build output) feeds changed `.class` bytes to `HotSwapEngine`, which derives the class name from the bytes
  and calls `Instrumentation.redefineClasses()` to replace the method bodies in the **running** JVM — no
  restart. Instrumentation is acquired through the injector's shared `InstrumentationSupport` (the same
  Attach-API self-attach the ephemeral JFR probes use); a locked-down JVM degrades to
  `NO_INSTRUMENTATION`. Each successful swap notifies `HotSwapListener`s, which re-resolve the new
  `LiveHookGraph` (a mutable wrapper over `HookDag`) so injected hooks stay deterministically ordered live.
- **Polyglot WASM sandbox (`aetherium-wasm`):** loads `.wasm` mods (Rust/C/Go) into a GraalWASM
  `Context` reached **reflectively** (no hard dependency — the offline build stays green; degrades to
  policy-only mode when absent). `WasmSecurityPolicy.strict()` denies filesystem and network by
  construction and the `Context` is built with `IOAccess.NONE`/`HostAccess.NONE`; only **memory and
  compute** are permitted. `StructArenaWasmBridge` bridges WASM linear memory to the FFM `StructArena`,
  running a sandboxed kernel over off-heap entity bytes with no host handle.
- **Delta-sync networking (`aetherium-network`):** `StructArenaDeltaPacket`/`StructArenaDeltaCodec` add a
  per-row `DirtyBitmap` alongside the off-heap structs and transmit **only the changed rows** — contiguous
  dirty rows are coalesced into runs and shipped as single zero-copy `writeSegment` slices.
  `StructArenaDelta` diffs the live arena against an off-heap shadow of the last-sent state to compute the
  bitmap each tick. In the self-test, a 7-of-4096-row change ships 112 bytes instead of 65 536 (byte-exact
  reconstruction).
- **CLI:** new `spirv`, `hotswap`, `wasm`, `delta` self-test commands; `doctor` now also reports GraalWASM
  polyglot availability.

**RU**
- **Компилятор Java→SPIR-V (`aetherium-compute`):** рантайм-компилятор, превращающий чистый Java-метод с
  аннотацией `@AetheriumComputeShader` в бинарь Vulkan **SPIR-V**. `JavaToSpirvCompiler` читает байт-код
  ядра через ASM (не исполняя), распознаёт строгое подмножество (примитивы, примитивные массивы, циклы,
  `+ - *`; аллокация объектов отвергается), а `SpirvKernelBuilder` выпускает структурно валидный модуль
  для `dst[i] = a[i] OP b[i]` — магия заголовка `0x07230203`, точка входа `GLCompute`, режим `LocalSize`,
  граф std430-SSBO, `gl_GlobalInvocationID`. `SpirvModule#verify()` проходит поток слов; `SpirvVulkanDispatch`
  направляет бинарь в Vulkan-мост `aetherium-native` (GPU при наличии устройства, иначе CPU-fallback).
- **Движок живого hot-swap (`aetherium-hotswap`):** `ClassFileWatcher` (рекурсивный `WatchService` над
  выводом сборки) передаёт изменённые байты `.class` в `HotSwapEngine`, который выводит имя класса из байт
  и вызывает `Instrumentation.redefineClasses()`, заменяя тела методов в **работающей** JVM — без
  перезапуска. `Instrumentation` берётся через общий `InstrumentationSupport` инжектора (тот же self-attach
  через Attach API, что и эфемерные JFR-зонды); заблокированная JVM деградирует до `NO_INSTRUMENTATION`.
  Каждый успешный своп уведомляет `HotSwapListener`, которые заново разрешают `LiveHookGraph` (изменяемую
  обёртку над `HookDag`).
- **Polyglot WASM-песочница (`aetherium-wasm`):** загружает `.wasm`-моды (Rust/C/Go) в `Context` GraalWASM,
  достигаемый **рефлексивно** (без жёсткой зависимости — офлайн-сборка зелёная; деградирует в режим
  только-политики при отсутствии). `WasmSecurityPolicy.strict()` запрещает файловую систему и сеть по
  построению, а `Context` строится с `IOAccess.NONE`/`HostAccess.NONE`; разрешены только **память и
  вычисления**. `StructArenaWasmBridge` связывает линейную память WASM с FFM `StructArena`.
- **Delta-sync сеть (`aetherium-network`):** `StructArenaDeltaPacket`/`StructArenaDeltaCodec` добавляют
  построчный `DirtyBitmap` рядом с off-heap структурами и передают **только изменённые строки** —
  непрерывные грязные строки объединяются в пробеги и отправляются одиночными zero-copy срезами
  `writeSegment`. `StructArenaDelta` диффит живую арену с off-heap тенью. В самотесте изменение 7 из 4096
  строк отправляет 112 байт вместо 65 536 (побайтовое восстановление).
- **CLI:** новые команды самотестов `spirv`, `hotswap`, `wasm`, `delta`; `doctor` теперь сообщает и о
  доступности GraalWASM.

### Added — 1.0.0-RC polish: boot banner + `doctor` environment health check (2026-06-19)

**EN**
- **Boot banner (`aetherium-loader`):** a concise, dependency-free framed ASCII banner logged once at
  `FMLConstructModEvent` (`BootBanner`). It prints the framework version and the *live* status of each
  acceleration tier — `SIMD Vector API [ ACTIVE/scalar ]`, `AppCDS Cache [ WARM n / COLD / disabled ]`,
  `Vulkan Compute [ READY n dev / n/a ]`, `Compute Tier [ FFM/JNI/PURE_JAVA ]` — computed from the real
  probes (`SimdMath`, `AppCdsManager`, `PreFlightCheck`). One-time, never spams the log.
- **`aetherium doctor`:** an environment health check that scans the host for readiness — Java 21+,
  `--enable-preview`, the `jdk.incubator.vector` SIMD module, and FFM native access (`--enable-native-access`
  + a live off-heap allocation) — printing per-check `[ OK ]`/`[ WARN ]` with remediation hints and a final
  `READY` / `NEEDS ATTENTION` diagnosis.
- `docs/{en,ru}/cli.md` command tables refreshed with every current command; `.gitignore` ignores the
  `.aetherium/` runtime cache.

**RU**
- **Boot-баннер (`aetherium-loader`):** лаконичный обрамлённый ASCII-баннер без зависимостей, логируемый
  один раз на `FMLConstructModEvent` (`BootBanner`). Печатает версию фреймворка и *живой* статус каждого
  уровня ускорения (SIMD / AppCDS / Vulkan / уровень вычислений), вычисленный из реальных зондов. Один
  раз, без засорения лога.
- **`aetherium doctor`:** проверка готовности окружения — Java 21+, `--enable-preview`, SIMD-модуль
  `jdk.incubator.vector` и нативный доступ FFM (`--enable-native-access` + живая off-heap аллокация) — с
  построчным `[ OK ]`/`[ WARN ]` и итоговым диагнозом `READY` / `NEEDS ATTENTION`.
- Таблицы команд `docs/{en,ru}/cli.md` обновлены всеми текущими командами; `.gitignore` игнорирует
  рантайм-кэш `.aetherium/`.

### Added — Enterprise/AAA: DAG hooks + Semantic Merger, SIMD, AppCDS, ephemeral JFR probes, CIA security (2026-06-19)

**EN**
- **DAG hook resolution + ASM Semantic Merger (`aetherium-injector`):** integer priorities replaced by a
  dependency DAG — `method.at(InjectionAnchor.HEAD).hook(id, h).runBefore(...)/.runAfter(...)`. `HookDag`
  topologically sorts (Kahn, stable declaration-index tie-break, `HookCycleException` on a cycle). The
  **Semantic Merger** lowers a whole group as ONE shared-`HookContext` block with a SINGLE cancellation
  epilogue, so multiple `ctx.cancel()` calls compose instead of conflict: all hooks run in DAG order,
  each observing the running decision, then one deterministic return. Verified (`aetherium inject`):
  declared `[mod_b, mod_a]` + `runAfter` resolves to `[mod_a, mod_b]`; `mod_a` cancels 7, `mod_b` reads 7
  from the shared context and combines to 9 → `merged(123)=9`.
- **SIMD Vector API (`aetherium-core`):** zero-boilerplate `VectorLane` (off-heap SoA column) + `SimdMath`
  kernels (`mulAddInPlace`/`scaleInPlace`/`sum` over `MemorySegment`, `float[]`/`double[]` FMA). All
  `jdk.incubator.vector` use is isolated in `VectorKernels`, loaded only when present (else identical
  scalar fallback — no hard incubator dependency). `aetherium simd`: 256-bit lanes (8 floats/op), heap +
  1M-element off-heap lane + scalar-tail all numerically identical to scalar (max error 0.0).
- **AppCDS zero-parse caching (`aetherium-loader`):** `AppCdsManager` persists transformed bytes to an
  `mmap`'d archive keyed by `name + hash(original)`; a launch-boundary hit returns them with a slice copy,
  skipping the entire ASM pipeline. Hash key = automatic per-class invalidation on MC/NeoForge updates.
  Also emits a class list + `-XX:SharedArchiveFile` flags for the JDK's own `.jsa` AppCDS. Verified
  (`aetherium cdscache test`): cold miss → reopen mmap → warm hit (zero ASM parse) → stale invalidation.
- **Ephemeral JFR probes (`aetherium-injector` `probe`):** `ProbeWeaver` weaves `jdk.jfr`
  begin/commit ONLY for active `ProbeTarget`s — an un-probed method has no probe bytecode at all (no
  hot-path conditional). `DynamicProbeController` hot-swaps via `Instrumentation.retransformClasses`,
  acquiring it from `AetheriumProbeAgent` (startup `-javaagent` or on-demand Attach-API self-attach),
  degrading to load-time weaving if locked down. `aetherium profile`: off → no event ref; on → 50/50 JFR
  events captured; live self-attach hot-swap available.
- **CIA-triad security (`aetherium-security`, new module):** default-deny `SecurityPolicy` +
  `CapabilityGrant`/`Capability`; `GuardedSegment` (capability-gated, bounds-checked FFM view — Integrity);
  `ReflectionGuard` (refuses reflection into protected framework/JDK-internal packages even with the
  capability — Confidentiality). `aetherium security`: all six invariants PASS.
- New CLI commands: `simd`, `cdscache [test]`, `profile`, `security`. Bilingual docs added
  (`docs/{en,ru}/simd.md`, `appcds.md`, `probes.md`, `security.md`) + injector DAG/Merger section; docs
  index + README module tables updated. Full `./gradlew build` green.

**RU**
- **DAG-порядок хуков + семантический слиятель ASM (`aetherium-injector`):** целочисленные приоритеты
  заменены DAG зависимостей — `method.at(InjectionAnchor.HEAD).hook(id, h).runBefore(...)/.runAfter(...)`.
  `HookDag` топологически сортирует (Кан, стабильно по индексу объявления, `HookCycleException` при цикле).
  **Слиятель** понижает всю группу как ОДИН блок с общим `HookContext` и ОДНИМ эпилогом отмены — несколько
  `ctx.cancel()` кооперируются, а не конфликтуют. Проверено: `[mod_b, mod_a]`+`runAfter` → `[mod_a, mod_b]`;
  `mod_a` отменяет 7, `mod_b` читает 7 и комбинирует в 9 → `merged(123)=9`.
- **SIMD Vector API (`aetherium-core`):** `VectorLane` (off-heap SoA-колонка) + ядра `SimdMath`; весь
  `jdk.incubator.vector` изолирован в `VectorKernels` (иначе идентичный скалярный откат). `aetherium simd`:
  полосы 256 бит (8 float/оп), heap + off-heap 1M + хвост — численно идентично скаляру (ошибка 0.0).
- **AppCDS без повторного разбора (`aetherium-loader`):** `AppCdsManager` сохраняет преобразованные байты
  в `mmap`-архив с ключом `имя + hash(исходных)`; попадание через границу запуска пропускает весь конвейер
  ASM. Также пишет список классов и флаги `-XX:SharedArchiveFile` для `.jsa` AppCDS. Проверено
  (`aetherium cdscache test`): холодный промах → mmap → тёплое попадание → инвалидация.
- **Эфемерные JFR-зонды (`aetherium-injector` `probe`):** `ProbeWeaver` вплетает begin/commit ТОЛЬКО для
  активных целей — у незондированного метода нет кода зонда (нет проверки на горячем пути).
  `DynamicProbeController` делает hot-swap через `Instrumentation.retransformClasses` (агент через
  `-javaagent` или самоподключение Attach API), деградируя до ткача времени загрузки. `aetherium profile`:
  off → нет ссылки; on → 50/50 событий JFR; живой self-attach доступен.
- **Безопасность CIA (`aetherium-security`, новый модуль):** default-deny `SecurityPolicy`;
  `GuardedSegment` (FFM с проверкой границ — целостность); `ReflectionGuard` (запрет рефлексии во
  внутренние пакеты даже при наличии возможности — конфиденциальность). `aetherium security`: все 6 PASS.
- Новые команды CLI: `simd`, `cdscache [test]`, `profile`, `security`. Двуязычные доки добавлены; индекс
  доков и таблицы модулей README обновлены. Полная сборка `./gradlew build` зелёная.

### Added — HookContext (method cancellation) + Block/Level PAL (LoomThreader migration feedback) (2026-06-19)

**EN**
- **Injector `HookContext` — `this`, arguments, and cancellation:** the final Mixin-killer capability.
  `ContextualHook` receives a `HookContext` exposing `self()`, `arg(int)`/`argCount()`, and — crucially —
  `cancel()` / `cancel(Object)` to **bypass the original vanilla method**. The fluent API gains
  `MethodInjection.insertContextHookBefore/After(hook, captureArguments)`.
- **Frame-correct ASM cancellation lowering:** the cursor builds the context (`this` + optional args)
  directly into a typed `invokedynamic` site (new `HookBootstrap.bootstrapContextHook` +
  `HookTable` context array), then emits `isCancelled()` → `IFEQ CONT` → an immediate
  `RETURN`/unboxed `xRETURN`/`CHECKCAST+ARETURN`. Every non-cancel path is net-zero on the operand
  stack, so `COMPUTE_FRAMES` recomputes valid frames — verified by the JVM loading and running the
  transformed classes (no `VerifyError`).
- **Performance:** boxing is opt-in — the `void` `AetheriumHook` stays allocation-free; a context hook
  without argument capture boxes nothing; arguments box only when requested; the return value boxes
  only on the cold cancel path. `this`/locals are pushed straight into the typed `invokedynamic`.
- **Edge Block/Level PAL:** `aetherium-edge` gains `BlockPos` (pure value type), `BlockHandle`
  (`blockId`/`isAir`/`destroySpeed`/`property`), `BlockEntityAccess` (typed NBT key/value, no
  `CompoundTag` leak), `LevelContext` (`blockAt`/`blockEntityAt`/`setBlock`/`scheduleNeighborUpdate`/
  `isLoaded`/`dimension`), and `LevelAccess` (`primary`/`byDimension`/`forEach`) on
  `PlatformBridge.levels()`; the no-op bridge reports no levels off-platform.
- **Loader bridging:** `NeoForgeLevelContext` / `NeoForgeBlockHandle` / `NeoForgeBlockEntityAccess`
  back the Block PAL over `Level` (`getBlockState`/`getBlockEntity`/`isLoaded`/`setBlockAndUpdate`/
  `updateNeighborsAt`, NBT via `saveWithoutMetadata`/`loadWithComponents` + `registryAccess()`);
  `NeoForgePlatformBridge.levels()` walks `getAllLevels()`/`overworld()`.
- **E2E verified:** `aetherium inject` self-test now also proves cancellation (`compute()` → 99, vanilla
  21 bypassed) and arg-read + value-cancel (`doubleIt(10)` → 15). `aetherium-testmod` adds a context-hook
  injection that reads a damage argument and cancels it to 0 — 2 rules discovered via `ServiceLoader`,
  applied through the sandbox (0 diagnostics).
- Bilingual `docs/{en,ru}/injector.md` (HookContext + cancellation) and `docs/{en,ru}/edge-pal.md`
  (Block PAL) updated in lock-step.

**RU**
- **`HookContext` инжектора — `this`, аргументы и отмена:** финальная возможность «убийцы Mixin».
  `ContextualHook` получает `HookContext` с `self()`, `arg(int)`/`argCount()` и — главное —
  `cancel()` / `cancel(Object)` для **обхода исходного ванильного метода**. Текучий API получает
  `MethodInjection.insertContextHookBefore/After(hook, captureArguments)`.
- **Корректное по фреймам понижение отмены в ASM:** курсор строит контекст (`this` + опц. аргументы)
  прямо в типизированную точку `invokedynamic` (новый `HookBootstrap.bootstrapContextHook` + массив
  контекст-хуков в `HookTable`), затем эмитит `isCancelled()` → `IFEQ CONT` → немедленный
  `RETURN`/распакованный `xRETURN`/`CHECKCAST+ARETURN`. Любой путь без отмены нулевой по стеку, поэтому
  `COMPUTE_FRAMES` пересчитывает валидные фреймы — проверено загрузкой и запуском преобразованных
  классов в JVM (без `VerifyError`).
- **Производительность:** упаковка по выбору — `void`-хук `AetheriumHook` без аллокаций; контекст-хук
  без захвата аргументов ничего не упаковывает; аргументы упаковываются лишь по запросу; возвращаемое
  значение — только на холодном пути отмены. `this`/локальные кладутся прямо в типизированный
  `invokedynamic`.
- **Block/Level PAL кромки:** `aetherium-edge` получает `BlockPos` (чистый value-тип), `BlockHandle`
  (`blockId`/`isAir`/`destroySpeed`/`property`), `BlockEntityAccess` (типизированный NBT ключ/значение,
  без утечки `CompoundTag`), `LevelContext` (`blockAt`/`blockEntityAt`/`setBlock`/
  `scheduleNeighborUpdate`/`isLoaded`/`dimension`) и `LevelAccess` (`primary`/`byDimension`/`forEach`)
  на `PlatformBridge.levels()`; no-op мост сообщает об отсутствии уровней вне платформы.
- **Мост загрузчика:** `NeoForgeLevelContext` / `NeoForgeBlockHandle` / `NeoForgeBlockEntityAccess`
  реализуют Block PAL поверх `Level` (`getBlockState`/`getBlockEntity`/`isLoaded`/`setBlockAndUpdate`/
  `updateNeighborsAt`, NBT через `saveWithoutMetadata`/`loadWithComponents` + `registryAccess()`);
  `NeoForgePlatformBridge.levels()` обходит `getAllLevels()`/`overworld()`.
- **Проверено E2E:** self-test `aetherium inject` теперь доказывает и отмену (`compute()` → 99, ваниль
  21 в обход), и чтение аргумента + отмену со значением (`doubleIt(10)` → 15). `aetherium-testmod`
  добавляет контекст-хук, читающий аргумент урона и отменяющий его в 0 — 2 правила через `ServiceLoader`,
  применены через песочницу (0 диагностик).
- Двуязычные `docs/{en,ru}/injector.md` (HookContext + отмена) и `docs/{en,ru}/edge-pal.md` (Block PAL)
  обновлены синхронно.

### Added — Fluent bytecode injector (the "Mixin killer") with a verification sandbox (2026-06-15)

**EN**
- **`aetherium-injector` (programmatic, strongly-typed):** a Mixin replacement built on a navigable
  `BytecodeCursor` — **no annotations, no string-based `@At` matching**. `AetheriumInjector` is the
  fluent registry: `inClass(internalName).method(name, desc)` targets typed; the cursor navigates the
  real instruction graph (`toStart`/`toEnd`/`next`/`previous`/`jumpTo`/`findOpcode`/`findReturn`) and
  edits it (`insertBefore`/`insertAfter`/`replace`/`delete`). `MethodInjection` records each step as a
  `Consumer<BytecodeCursor>` replayed lazily when the target loads (one cursor model, no drift).
- **O(1) hook lowering:** `insertHookBefore/After`/`replaceWithHook(AetheriumHook)` (e.g.
  `MyMod::asyncTick`) emit an `invokedynamic` (descriptor `()V`) bound to `HookBootstrap`, linked once
  against the `HookTable` into a `ConstantCallSite` — never a brittle static call. Same `O(1)`
  invokedynamic dispatch mechanism the engine uses for API lowering, dedicated to injected hooks.
- **Absolute-safety sandbox:** `InjectorTransformer` is a plain `ClassTransformer`, so every edit runs
  inside `BytecodeEngine` (COMPUTE_FRAMES + `CheckClassAdapter`/dataflow verify). On any `VerifyError`,
  malformed result, `CursorException`, or missing target, it logs a structured `Diagnostic`
  (`AE-VERIFY-001` / `AE-INJECT-CURSOR` / `AE-INJECT-404`) and reverts the class to its **original**
  bytes. The JVM never crashes.
- **Loader bridging:** mods contribute via the loader-agnostic `InjectionProvider` SPI
  (`META-INF/services`); `AetheriumTransformEngine` discovers all providers, installs the hook table,
  and adds the injector transformer; `AetheriumLaunchPlugin.handlesClass` now lets a class through the
  namespace deny-list when an injection rule targets it (so a vanilla `net.minecraft` target is
  intercepted).
- **E2E verified:** `aetherium-testmod` ships `MockInterceptTarget` + `TestmodInjectionProvider`
  (one fluent rule, no Minecraft import) — discovered via `ServiceLoader`, transformed through the
  sandbox, the injected hook fires exactly once and the value is preserved. `InjectorSelfTest`
  (CLI `aetherium inject`) proves positive injection + two revert cases (invalid bytecode, cursor miss).
- Bilingual `docs/{en,ru}/injector.md`; docs index + README module tables updated.

**RU**
- **`aetherium-injector` (программный, строго типизированный):** замена Mixin на навигируемом
  `BytecodeCursor` — **без аннотаций, без строкового `@At`**. `AetheriumInjector` — текучий реестр:
  `inClass(...).method(name, desc)` задаёт цель типизированно; курсор ходит по реальному графу
  инструкций (`toStart`/`toEnd`/`next`/`previous`/`jumpTo`/`findOpcode`/`findReturn`) и правит его
  (`insertBefore`/`insertAfter`/`replace`/`delete`). `MethodInjection` записывает каждый шаг как
  `Consumer<BytecodeCursor>`, воспроизводимый лениво при загрузке цели.
- **Понижение хука O(1):** `insertHookBefore/After`/`replaceWithHook(AetheriumHook)` (напр.
  `MyMod::asyncTick`) порождают `invokedynamic` (`()V`), привязанный к `HookBootstrap` и линкуемый
  однократно с `HookTable` в `ConstantCallSite` — не хрупкий статический вызов.
- **Песочница абсолютной безопасности:** `InjectorTransformer` — обычный `ClassTransformer`, поэтому
  каждая правка выполняется в `BytecodeEngine` (COMPUTE_FRAMES + проверка `CheckClassAdapter`/потоков).
  При любом `VerifyError`, неверном результате, `CursorException` или отсутствии цели логируется
  структурированный `Diagnostic` (`AE-VERIFY-001` / `AE-INJECT-CURSOR` / `AE-INJECT-404`) и класс
  откатывается к **исходным** байтам. JVM не падает.
- **Мост загрузчика:** моды поставляют инъекции через независимый SPI `InjectionProvider`
  (`META-INF/services`); `AetheriumTransformEngine` находит провайдеров, устанавливает таблицу хуков и
  добавляет трансформер; `AetheriumLaunchPlugin.handlesClass` пропускает класс через deny-list, если на
  него нацелено правило (перехват ванильной цели `net.minecraft`).
- **Проверено E2E:** `aetherium-testmod` поставляет `MockInterceptTarget` + `TestmodInjectionProvider`
  (одно текучее правило, без импорта Minecraft) — обнаружено через `ServiceLoader`, преобразовано через
  песочницу, хук срабатывает ровно один раз, значение сохраняется. `InjectorSelfTest`
  (CLI `aetherium inject`) доказывает позитивную инъекцию + два случая отката.
- Двуязычные `docs/{en,ru}/injector.md`; индекс docs и таблицы модулей в README обновлены.

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
