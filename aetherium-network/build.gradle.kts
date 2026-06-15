// aetherium-network — loader-agnostic custom-payload SPI (zero-GC StructArena synchronization).
// Pure: depends only on aetherium-core. MUST NOT import net.minecraft / net.neoforged — the loader
// bridges this SPI to the platform's packet system.
dependencies {
    api(project(":aetherium-core"))
}
