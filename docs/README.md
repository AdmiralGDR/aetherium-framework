# Aetherium Documentation Index / Указатель документации

This `docs/` tree is **strictly bilingual**. Every document exists in both English
(`en/`) and Russian (`ru/`) and the two are kept in lock-step — a change to one must
be mirrored in the other within the same commit.

Дерево `docs/` **строго двуязычное**. Каждый документ существует на английском (`en/`)
и русском (`ru/`), и они поддерживаются синхронно — изменение в одном обязано быть
отражено в другом в том же коммите.

| Topic / Тема                          | English          | Русский          |
|---------------------------------------|------------------|------------------|
| Bytecode Manipulation Engine (ASM)    | [en/bytecode-engine.md](en/bytecode-engine.md) | [ru/bytecode-engine.md](ru/bytecode-engine.md) |
| Native Bridge (JNI / FFM)             | [en/native-bridge.md](en/native-bridge.md)     | [ru/native-bridge.md](ru/native-bridge.md)     |
| Build System (Gradle, toolchain, API) | [en/build-system.md](en/build-system.md)       | [ru/build-system.md](ru/build-system.md)       |
| CLI & Developer Tooling               | [en/cli.md](en/cli.md)                          | [ru/cli.md](ru/cli.md)                          |
| Chaos Engineering Test Suite          | [en/testsuite.md](en/testsuite.md)              | [ru/testsuite.md](ru/testsuite.md)              |
| Game Integration (NeoForge / @Mod)    | [en/game-integration.md](en/game-integration.md)| [ru/game-integration.md](ru/game-integration.md)|
| Performance Architecture              | [en/performance.md](en/performance.md)          | [ru/performance.md](ru/performance.md)          |
| Gradle Plugin (zero-config builds)    | [en/gradle-plugin.md](en/gradle-plugin.md)      | [ru/gradle-plugin.md](ru/gradle-plugin.md)      |
| Edge — Platform Abstraction Layer     | [en/edge-pal.md](en/edge-pal.md)                | [ru/edge-pal.md](ru/edge-pal.md)                |
| Network (custom-payload SPI)          | [en/network.md](en/network.md)                  | [ru/network.md](ru/network.md)                  |
| Graphics (rendering abstraction)      | [en/gfx.md](en/gfx.md)                          | [ru/gfx.md](ru/gfx.md)                          |
| Content & DataGen (declarative)       | [en/content.md](en/content.md)                  | [ru/content.md](ru/content.md)                  |
| Injector (fluent bytecode, Mixin-kill)| [en/injector.md](en/injector.md)                | [ru/injector.md](ru/injector.md)                |
| SIMD (Vector API acceleration)        | [en/simd.md](en/simd.md)                        | [ru/simd.md](ru/simd.md)                        |
| AppCDS (zero-parse class cache)       | [en/appcds.md](en/appcds.md)                    | [ru/appcds.md](ru/appcds.md)                    |
| Ephemeral JFR Probes (zero-overhead)  | [en/probes.md](en/probes.md)                    | [ru/probes.md](ru/probes.md)                    |
| Security (capability CIA isolation)   | [en/security.md](en/security.md)                | [ru/security.md](ru/security.md)                |
| Compute (Java→SPIR-V compiler)        | [en/compute.md](en/compute.md)                  | [ru/compute.md](ru/compute.md)                  |
| Hot-Swap (live class redefinition)    | [en/hotswap.md](en/hotswap.md)                  | [ru/hotswap.md](ru/hotswap.md)                  |
| WASM (polyglot sandbox)               | [en/wasm.md](en/wasm.md)                        | [ru/wasm.md](ru/wasm.md)                        |
| Delta-Sync (dirty-bitmap networking)  | [en/delta-sync.md](en/delta-sync.md)            | [ru/delta-sync.md](ru/delta-sync.md)            |
| Kotlin DSL (zero-overhead wrappers)   | [en/ktx.md](en/ktx.md)                          | [ru/ktx.md](ru/ktx.md)                          |
| Fuzzer (SPIR-V + WASM hardening)      | [en/fuzzer.md](en/fuzzer.md)                    | [ru/fuzzer.md](ru/fuzzer.md)                    |
| LSP Backend (IDE autocomplete)        | [en/lsp.md](en/lsp.md)                          | [ru/lsp.md](ru/lsp.md)                          |
| Auto-Wiring (@AetheriumInit, no main) | [en/autowiring.md](en/autowiring.md)            | [ru/autowiring.md](ru/autowiring.md)            |
| UI (declarative Flexbox GUI)          | [en/ui.md](en/ui.md)                            | [ru/ui.md](ru/ui.md)                            |

Top-level [`../ARCHITECTURE.md`](../ARCHITECTURE.md) is itself bilingual (section-paired
EN/RU) and is the canonical system overview.
