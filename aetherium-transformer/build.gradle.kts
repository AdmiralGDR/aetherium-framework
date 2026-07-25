/*
 * aetherium-transformer — the ModLauncher BOOT-layer transformation service (b).
 *
 * EN: Split out of aetherium-loader so the two ModLauncher roles no longer collide in one jar. This
 *     artifact is a NeoForge `FMLModType=GAMELIBRARY`: it carries ONLY the ModLauncher services
 *     (ITransformationService + ILaunchPluginService) and the pure ASM transform engine, so FML never
 *     tries to load a `@Mod` from it — that is aetherium-loader's job now. It compiles against the
 *     ModLauncher API and SLF4J as `compileOnly` (the boot layer provides both at runtime; they are
 *     NEVER bundled — a second copy of ASM/SLF4J is a split package that aborts the launch, ).
 *     Compiled WITHOUT `--enable-preview` (root build) so it loads on any JVM (c): it uses no
 *     FFM, and its aetherium-* deps are FFM-free (class-file minor 0x0000). The jar flat-embeds those
 *     deps because the boot layer runs before the mod layer's Jar-in-Jar is extracted.
 * RU: Выделен из aetherium-loader, чтобы две роли ModLauncher не сталкивались в одном jar. Это
 *     `FMLModType=GAMELIBRARY`: несёт ТОЛЬКО сервисы ModLauncher (ITransformationService +
 *     ILaunchPluginService) и чистый ASM-движок, поэтому FML не пытается грузить из него `@Mod` — это
 *     теперь дело aetherium-loader. Компилируется против API ModLauncher и SLF4J как `compileOnly`
 *     (boot-слой даёт их в рантайме; НИКОГДА не встраиваются — вторая копия ASM/SLF4J = split package,
 *     обрывающий запуск, ). Собирается БЕЗ `--enable-preview` (корневой build), чтобы грузиться на
 *     любой JVM (c): FFM не используется, а зависимости aetherium-* свободны от FFM (minor 0x0000).
 *     Jar плоско встраивает эти зависимости, т.к. boot-слой работает до распаковки Jar-in-Jar мод-слоя.
 */

dependencies {
    api(project(":aetherium-bytecode"))            // ASM engine + invokedynamic runtime (+ core transitively)
    implementation(project(":aetherium-injector"))  // the fluent injector aggregated into the engine

    // Boot-layer APIs — present at runtime via the ModLauncher boot layer; must NOT ship in this jar.
    compileOnly(libs.modlauncher)
    compileOnly(libs.slf4j.api)
}

// Mark the jar as a NeoForge game-library (NOT a mod) and flat-embed the aetherium-* runtime the boot
// layer needs before any Jar-in-Jar is extracted. ASM/SLF4J are intentionally excluded (boot-provided).
tasks.named<Jar>("jar") {
    // Embedding zipTree(runtimeClasspath jars) loses the producer-task link, so declare it explicitly
    // (Gradle's implicit-dependency validation otherwise fails the build).
    dependsOn(configurations.named("runtimeClasspath"))
    manifest {
        attributes(
            "FMLModType" to "GAMELIBRARY",
            "Specification-Title" to "Aetherium Transformer",
            "Specification-Vendor" to "Aetherium Framework",
            "Specification-Version" to "1"
        )
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.map { cp ->
        cp.filter { it.name.startsWith("aetherium-") }.map { zipTree(it) }
    }) {
        // Keep only classes/resources; drop the embedded jars' own manifests and module descriptors.
        exclude("META-INF/MANIFEST.MF", "META-INF/maven/**", "module-info.class")
        // c: core's FFM packages (off-heap StructArena, mmap I/O, SIMD) are preview-compiled
        // (0xFFFF) and are NEVER used by the ASM transform pipeline — keep them out of the boot layer so
        // this jar stays 100% vanilla-loadable. Enforced by ArtifactVerifier's AE-PREVIEW-LEAK check.
        exclude("org/aetherium/core/compute/**", "org/aetherium/core/io/**", "org/aetherium/core/simd/**")
    }
}
