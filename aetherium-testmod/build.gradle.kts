/*
 * aetherium-testmod — an in-game test mod targeting the Aetherium API (NOT NeoForge).
 *
 * EN: Deliberately depends ONLY on aetherium-core. It implements the loader-agnostic
 *     `AetheriumMod` SPI and registers via ServiceLoader; it never imports a NeoForge or Minecraft
 *     class. This proves the "compile once, run on any loader" contract and the separation of
 *     concerns (only aetherium-loader touches the game).
 * RU: Намеренно зависит ТОЛЬКО от aetherium-core. Реализует независимый от загрузчика SPI
 *     `AetheriumMod` и регистрируется через ServiceLoader; никогда не импортирует класс NeoForge
 *     или Minecraft. Это доказывает контракт «скомпилируй один раз — запускай на любом загрузчике»
 *     и разделение ответственности (только aetherium-loader касается игры).
 */

dependencies {
    implementation(project(":aetherium-core"))
    implementation(project(":aetherium-network")) // zero-GC StructArena sync SPI
    implementation(project(":aetherium-gfx"))      // loader-agnostic render SPI
    implementation(project(":aetherium-edge"))     // Platform Abstraction Layer (entities + Block/Level PAL)
    implementation(project(":aetherium-content"))  // declarative @AetheriumBlock/@AetheriumItem API
    implementation(project(":aetherium-injector")) // programmatic fluent bytecode injection (Mixin killer)
    // The content annotation processor generates the resource JSON (models/blockstates/loot/lang)
    // straight into the compiled output at build time — the "JSON Hell" eliminator.
    annotationProcessor(project(":aetherium-content"))
}
