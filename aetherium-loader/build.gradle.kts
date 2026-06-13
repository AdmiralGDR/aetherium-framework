/*
 * aetherium-loader — loader shim, packaged as a drop-in NeoForge 1.21.1 mod.
 *
 * EN: Composes core + bytecode + native. The produced jar carries valid NeoForge mod metadata
 *     (META-INF/neoforge.mods.toml) so FML *discovers and recognizes* it when dropped into a
 *     standard `mods/` folder, co-existing with ordinary mods — no special setup for players.
 *     Full game-runtime wiring (ModDevGradle userdev + jar-in-jar dependency bundling) is a
 *     documented later phase; see docs/en/build-system.md §"NeoForge integration".
 * RU: Композирует core + bytecode + native. Полученный jar несёт валидные метаданные мода
 *     NeoForge (META-INF/neoforge.mods.toml), поэтому FML *обнаруживает и распознаёт* его при
 *     помещении в стандартную папку `mods/`, сосуществуя с обычными модами — без особой
 *     настройки для игроков. Полная привязка к среде выполнения игры (ModDevGradle userdev +
 *     упаковка зависимостей jar-in-jar) — задокументированный следующий этап; см.
 *     docs/ru/build-system.md §"Интеграция с NeoForge".
 */

dependencies {
    api(project(":aetherium-core"))
    implementation(project(":aetherium-bytecode"))
    implementation(project(":aetherium-native"))
}

// Expose the mod coordinates to resource filtering so neoforge.mods.toml is never hardcoded
// to a version that can drift from the build (anti-hardcoding rule).
tasks.named<ProcessResources>("processResources") {
    val tokens = mapOf(
        "version" to project.version.toString(),
        "minecraft" to libs.versions.minecraft.get(),
        "neoforge" to libs.versions.neoforge.get()
    )
    inputs.properties(tokens)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand(tokens)
    }
}

// Mark the jar as a NeoForge mod-type artifact in its manifest (alongside the toml).
tasks.named<Jar>("jar") {
    manifest {
        attributes(
            "FMLModType" to "MOD",
            "Specification-Title" to "Aetherium",
            "Specification-Vendor" to "Aetherium Framework",
            "Specification-Version" to "1"
        )
    }
}
