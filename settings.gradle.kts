/*
 * Aetherium Framework — Gradle multi-module settings (foundation placeholder).
 *
 * EN: Declares the module graph. Build logic (toolchain pinning to GraalVM 21,
 *     `--enable-preview`, ASM/native tasks) is wired in the next phase; this file only
 *     fixes the module names so the layout in ARCHITECTURE.md is authoritative.
 * RU: Объявляет граф модулей. Логика сборки (фиксация тулчейна на GraalVM 21,
 *     `--enable-preview`, задачи ASM/нативной сборки) подключается на следующем этапе;
 *     этот файл лишь фиксирует имена модулей, делая структуру из ARCHITECTURE.md 
 *     канонической.
 */
rootProject.name = "aetherium"

include(
    "aetherium-core",      // stable API, contracts, config, error model
    "aetherium-bytecode",  // ASM transform engine
    "aetherium-native",    // JNI / FFM native bridge
    "aetherium-loader",    // loader shims (NeoForge baseline)
    "aetherium-cli",       // developer CLI / IDE tooling
    "aetherium-testsuite", // chaos-engineering stress & fallback validation
    "aetherium-testmod",   // in-game test mod targeting the Aetherium API (not NeoForge)
    "aetherium-edge",      // Platform Abstraction Layer (PAL) — loader-agnostic vanilla bridge SPI
    "aetherium-network",   // loader-agnostic custom-payload SPI (zero-GC StructArena sync)
    "aetherium-gfx",       // loader-agnostic rendering / model-registration abstraction
    "aetherium-datagen",   // pure (no-MC) build-time asset/JSON generator
    "aetherium-content",   // declarative content annotations + processor (zero-boilerplate registries)
    "aetherium-gradle-plugin" // zero-config build plugin for mod developers
)
