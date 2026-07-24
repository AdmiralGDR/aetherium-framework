/*
 * aetherium-edge — the Platform Abstraction Layer (PAL).
 *
 * EN: A strictly abstract, loader-agnostic bridge SPI. It DEFINES how an Aetherium mod interacts
 *     with vanilla concepts (entity positioning, basic events) without importing any NeoForge/Fabric
 *     type. aetherium-loader (which knows NeoForge) provides the implementation via ServiceLoader.
 *     Depends only on aetherium-core; published to Maven so mods compile against it.
 * RU: Строго абстрактный, независимый от загрузчика SPI-мост. Он ОПРЕДЕЛЯЕТ, как мод Aetherium
 *     взаимодействует с ванильными концепциями (позиционирование сущностей, базовые события) без
 *     импорта типов NeoForge/Fabric. aetherium-loader (знающий NeoForge) предоставляет реализацию
 *     через ServiceLoader. Зависит только от aetherium-core; публикуется в Maven.
 */

dependencies {
    api(project(":aetherium-core"))
    // WorldStore persists loader-agnostic TreeNode documents through the hardened TreeCodec. network
    // depends only on core, so edge -> network -> core stays acyclic.
    api(project(":aetherium-network"))

    testImplementation(libs.junit.jupiter)
}
