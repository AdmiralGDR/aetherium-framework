/*
 * aetherium-fabric — the SECOND loader (Fabric), proving the framework is loader-agnostic (WS-5).
 *
 * EN: The framework's whole sovereignty claim is "one mod, any loader". aetherium-loader implements the pure
 *     SPIs against NeoForge; this module implements the SAME loader-neutral boot against Fabric's programming
 *     model — a real `net.fabricmc.api.ModInitializer` that runs the identical dispatch-table install +
 *     ServiceLoader<AetheriumMod> initialization the NeoForge entrypoint runs. Fabric-loader is `compileOnly`
 *     (it is a plain Maven jar carrying just the entrypoint interfaces — NO Fabric Loom / remapped Minecraft
 *     needed for THIS proof), so the boot-agnosticism is verified offline. The MC-wrapping PAL bridges
 *     (FabricPlatformBridge over Fabric's Yarn-mapped Minecraft) are the documented remaining piece that needs
 *     the Loom toolchain. Zero runtime dependency beyond the shared framework + the host loader.
 * RU: Вся суверенная идея фреймворка — «один мод, любой загрузчик». aetherium-loader реализует чистые SPI
 *     против NeoForge; этот модуль реализует ТУ ЖЕ loader-нейтральную загрузку против Fabric — настоящий
 *     `net.fabricmc.api.ModInitializer`, выполняющий идентичную установку таблицы диспатча +
 *     ServiceLoader<AetheriumMod>. Fabric-loader — `compileOnly` (обычный Maven-jar с интерфейсами точки
 *     входа; Loom/ремап Minecraft для ЭТОГО доказательства не нужны), поэтому агностичность загрузки
 *     проверяется офлайн. PAL-мосты поверх Minecraft (Yarn) — задокументированный остаток под Loom.
 */

repositories {
    maven {
        name = "FabricMC"
        url = uri("https://maven.fabricmc.net/")
    }
}

dependencies {
    api(project(":aetherium-core"))            // AetheriumMod / AetheriumContext / CapabilityTier / SymbolManifest
    implementation(project(":aetherium-bytecode"))    // DispatchTable (the O(1) invokedynamic target table)
    implementation(project(":aetherium-transformer")) // AetheriumSymbols — the SHARED manifest both loaders use
    implementation(project(":aetherium-shield"))       // ModVerifier / IntegrityManifest (integrity enforcement)

    // The Fabric entrypoint interfaces. compileOnly + provided by the Fabric loader at runtime — never bundled.
    compileOnly(libs.fabricloader)

    testImplementation(libs.junit.jupiter)
}
