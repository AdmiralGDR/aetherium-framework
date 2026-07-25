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
// EN: Plugin resolution repositories. The Gradle Plugin Portal is required for the Kotlin JVM plugin
//     (aetherium-ktx); mavenCentral mirrors its dependencies. JDK toolchain auto-download stays off
//     (gradle.properties) — that knob is unrelated to plugin/dependency resolution.
// RU: Репозитории для разрешения плагинов. Портал плагинов Gradle нужен для плагина Kotlin JVM
//     (aetherium-ktx); mavenCentral отражает его зависимости. Авто-загрузка JDK-тулчейна остаётся
//     выключенной (gradle.properties) — этот флаг не связан с разрешением плагинов/зависимостей.
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "aetherium"

include(
    "aetherium-core",      // stable API, contracts, config, error model
    "aetherium-bytecode",  // ASM transform engine
    "aetherium-native",    // JNI / FFM native bridge
    "aetherium-transformer", // ModLauncher boot-layer transformation service (FMLModType=GAMELIBRARY)
    "aetherium-loader",    // loader shims (NeoForge baseline)
    "aetherium-cli",       // developer CLI / IDE tooling
    "aetherium-testsuite", // chaos-engineering stress & fallback validation
    "aetherium-testmod",   // in-game test mod targeting the Aetherium API (not NeoForge)
    "aetherium-edge",      // Platform Abstraction Layer (PAL) — loader-agnostic vanilla bridge SPI
    "aetherium-network",   // loader-agnostic custom-payload SPI (zero-GC StructArena sync)
    "aetherium-config",    // world/mod config store: JSON-over-TreeNode, atomic write, hot-reload
    "aetherium-gfx",       // loader-agnostic rendering / model-registration abstraction
    "aetherium-datagen",   // pure (no-MC) build-time asset/JSON generator
    "aetherium-content",   // declarative content annotations + processor (zero-boilerplate registries)
    "aetherium-injector",  // fluent BytecodeCursor injection API (the "Mixin killer")
    "aetherium-shield",    // sovereign anti-reverse-engineering / anti-AI protection (obfuscation + integrity)
    "aetherium-verify",    // in-game mod verification & analysis (integrity, watermark, inspector screen)
    "aetherium-security",  // capability-based CIA-triad isolation (reflection + FFM bounds guards)
    "aetherium-compute",   // Java→SPIR-V runtime compiler (pure-Java kernels → Vulkan binaries)
    "aetherium-hotswap",   // live class hot-swap engine (WatchService + Instrumentation.redefineClasses)
    "aetherium-wasm",      // polyglot WASM sandbox (GraalWASM, memory+compute only, no FS/network)
    "aetherium-ktx",       // zero-overhead Kotlin DSL over the injector / StructArena / DataGen APIs
    "aetherium-fuzzer",    // aggressive coverage fuzzer for the SPIR-V + WASM attack surface
    "aetherium-ui",        // declarative, Flexbox-like cross-platform GUI framework (no MC imports)
    "aetherium-gradle-plugin" // zero-config build plugin for mod developers
)
