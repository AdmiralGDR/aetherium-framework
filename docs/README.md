# Aetherium Documentation / Документация Aetherium

This `docs/` tree follows the **[Diátaxis](https://diataxis.fr/) framework**: every document lives in
exactly one of four quadrants, separated by what the reader is trying to do. It is also **strictly
bilingual** — every document exists in both English (`en/`) and Russian (`ru/`), kept in lock-step
within the same commit.

Дерево `docs/` следует фреймворку **[Diátaxis](https://diataxis.fr/)**: каждый документ живёт ровно в
одном из четырёх квадрантов, разделённых по задаче читателя. Оно также **строго двуязычное** — каждый
документ существует на английском (`en/`) и русском (`ru/`) и поддерживается синхронно в одном коммите.

## The four quadrants / Четыре квадранта

| | **Practical / Практика** | **Theoretical / Теория** |
|---|---|---|
| **Learning / Обучение** | [`tutorials/`](en/tutorials/) — lessons that take you from zero to a working mod / уроки от нуля до рабочего мода | [`explanation/`](en/explanation/) — how and why the subsystems are designed / как и почему устроены подсистемы |
| **Working / Работа** | [`how-to/`](en/how-to/) — recipes for a specific task / рецепты конкретных задач | [`reference/`](en/reference/) — precise API, CLI and annotation facts / точные факты об API, CLI и аннотациях |

## Tutorials — learning-oriented / Учебники

| Topic / Тема | English | Русский |
|---|---|---|
| Getting started: your first Aetherium mod | [en](en/tutorials/getting-started.md) | [ru](ru/tutorials/getting-started.md) |

## How-to guides — task-oriented / Практические руководства

| Task / Задача | English | Русский |
|---|---|---|
| Inject a hook into a vanilla method | [en](en/how-to/inject-a-hook.md) | [ru](ru/how-to/inject-a-hook.md) |
| Sync off-heap entity data over the network | [en](en/how-to/sync-off-heap-data.md) | [ru](ru/how-to/sync-off-heap-data.md) |

## Reference — information-oriented / Справочник

| Topic / Тема | English | Русский |
|---|---|---|
| CLI commands & developer tooling | [en](en/reference/cli.md) | [ru](ru/reference/cli.md) |
| Build system (Gradle, toolchain, catalog) | [en](en/reference/build-system.md) | [ru](ru/reference/build-system.md) |
| Gradle plugin (zero-config builds) | [en](en/reference/gradle-plugin.md) | [ru](ru/reference/gradle-plugin.md) |
| Content annotations (@AetheriumBlock/@AetheriumItem) | [en](en/reference/content.md) | [ru](ru/reference/content.md) |
| Auto-wiring annotations (@AetheriumInit) | [en](en/reference/autowiring.md) | [ru](ru/reference/autowiring.md) |
| Kotlin DSL (aetherium-ktx) | [en](en/reference/ktx.md) | [ru](ru/reference/ktx.md) |
| ConfigStore (typed JSON config, hot-reload) | [en](en/reference/config.md) | [ru](ru/reference/config.md) |

## Explanation — understanding-oriented / Пояснения

| Topic / Тема | English | Русский |
|---|---|---|
| Bytecode manipulation engine (ASM, O(1) dispatch) | [en](en/explanation/bytecode-engine.md) | [ru](ru/explanation/bytecode-engine.md) |
| The ACID engine (transactional hooks, TTD) | [en](en/explanation/acid.md) | [ru](ru/explanation/acid.md) |
| Injector (fluent cursor, DAG, Semantic Merger) | [en](en/explanation/injector.md) | [ru](ru/explanation/injector.md) |
| Native bridge (JNI / FFM, fallback ladder) | [en](en/explanation/native-bridge.md) | [ru](ru/explanation/native-bridge.md) |
| Performance architecture (StructArena, tick engine) | [en](en/explanation/performance.md) | [ru](ru/explanation/performance.md) |
| Compute (Java→SPIR-V compiler design) | [en](en/explanation/compute.md) | [ru](ru/explanation/compute.md) |
| Security (capability CIA isolation, memory domains) | [en](en/explanation/security.md) | [ru](ru/explanation/security.md) |
| **Shield (anti-reverse / anti-AI protection)** | [en](en/explanation/shield.md) | [ru](ru/explanation/shield.md) |
| **In-game verification & analysis** | [en](en/explanation/verify.md) | [ru](ru/explanation/verify.md) |
| SIMD (Vector API acceleration) | [en](en/explanation/simd.md) | [ru](ru/explanation/simd.md) |
| AppCDS (zero-parse class cache) | [en](en/explanation/appcds.md) | [ru](ru/explanation/appcds.md) |
| Ephemeral JFR probes (zero-overhead profiling) | [en](en/explanation/probes.md) | [ru](ru/explanation/probes.md) |
| Hot-swap (live class redefinition, DCEVM) | [en](en/explanation/hotswap.md) | [ru](ru/explanation/hotswap.md) |
| WASM (polyglot sandbox) | [en](en/explanation/wasm.md) | [ru](ru/explanation/wasm.md) |
| Network (custom-payload SPI, TreeCodec) | [en](en/explanation/network.md) | [ru](ru/explanation/network.md) |
| Delta-sync (dirty-bitmap networking) | [en](en/explanation/delta-sync.md) | [ru](ru/explanation/delta-sync.md) |
| Edge — Platform Abstraction Layer | [en](en/explanation/edge-pal.md) | [ru](ru/explanation/edge-pal.md) |
| Graphics (rendering abstraction, skeletal) | [en](en/explanation/gfx.md) | [ru](ru/explanation/gfx.md) |
| UI (declarative Flexbox GUI) | [en](en/explanation/ui.md) | [ru](ru/explanation/ui.md) |
| Game integration (NeoForge / @Mod / ModLauncher) | [en](en/explanation/game-integration.md) | [ru](ru/explanation/game-integration.md) |
| Chaos-engineering test suite | [en](en/explanation/testsuite.md) | [ru](ru/explanation/testsuite.md) |
| Fuzzer (SPIR-V + WASM hardening) | [en](en/explanation/fuzzer.md) | [ru](ru/explanation/fuzzer.md) |
| LSP backend (IDE autocomplete) | [en](en/explanation/lsp.md) | [ru](ru/explanation/lsp.md) |

Top-level [`../ARCHITECTURE.md`](../ARCHITECTURE.md) is itself bilingual (section-paired EN/RU) and is
the canonical system overview.
