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

// ---- RELOCATE the boot-layer's embedded framework copy into a private prefix -------------
// The boot jar must be self-contained (it runs before Jar-in-Jar extraction), but shipping org/aetherium/
// {core,bytecode,injector} as LOOSE classes made it a module that exports the same packages as the loader's
// Jar-in-Jar copies → java.lang.module.ResolutionException before the window. Fix: shade the embedded copy
// (and rewrite this module's own references to it) into org/aetherium/boot/… using the framework's own
// ClassRelocator, run as a forked build step. FFM/preview packages are still excluded (never enter boot).
val bootRelocatorCp: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}
dependencies {
    bootRelocatorCp(project(":aetherium-bytecode")) // BootRelocator + ClassRelocator + ASM
}
val relocatedBootDir = layout.buildDirectory.dir("generated/aetherium/boot-relocated")
val ownClassesDirs = sourceSets["main"].output.classesDirs
val embeddedAetheriumJars = configurations.runtimeClasspath.map { cp ->
    cp.filter { it.name.startsWith("aetherium-") }
}
val relocateBootRuntime by tasks.registering(JavaExec::class) {
    group = "aetherium"
    description = "Shades the boot jar's embedded core/bytecode/injector into org/aetherium/boot/… ()."
    dependsOn("classes", configurations.named("runtimeClasspath"))
    inputs.files(ownClassesDirs)
    inputs.files(embeddedAetheriumJars)
    outputs.dir(relocatedBootDir)
    classpath = bootRelocatorCp
    mainClass.set("org.aetherium.bytecode.relocate.BootRelocator")
    jvmArgs("--enable-preview", "--enable-native-access=ALL-UNNAMED", "--add-modules=jdk.incubator.vector")
    argumentProviders.add(CommandLineArgumentProvider {
        val a = mutableListOf<String>()
        a += relocatedBootDir.get().asFile.absolutePath
        a += "org.aetherium.core:org.aetherium.boot.core," +
                "org.aetherium.bytecode:org.aetherium.boot.bytecode," +
                "org.aetherium.injector:org.aetherium.boot.injector"
        a += "org/aetherium/core/compute/,org/aetherium/core/io/,org/aetherium/core/simd/"
        ownClassesDirs.files.forEach { a += it.absolutePath }
        embeddedAetheriumJars.get().files.forEach { a += it.absolutePath }
        a
    })
    doFirst { relocatedBootDir.get().asFile.deleteRecursively() } // clean staging → reproducible
}

// The jar ships the RELOCATED classes (not the raw source-set classes, which reference un-relocated core),
// plus this module's resources (the ModLauncher service files — unchanged, they name org.aetherium.transformer).
tasks.named<Jar>("jar") {
    dependsOn(relocateBootRuntime)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes(
            "FMLModType" to "GAMELIBRARY",
            "Specification-Title" to "Aetherium Transformer",
            "Specification-Vendor" to "Aetherium Framework",
            "Specification-Version" to "1"
        )
    }
    // Drop the raw compiled classes (they'd still reference org/aetherium/core); the relocated staging dir
    // carries this module's own classes (path unchanged, references rewritten) + the shaded boot/… deps.
    val rawClassDirs = ownClassesDirs.files
    exclude { fte -> rawClassDirs.any { fte.file.absolutePath.startsWith(it.absolutePath) } }
    from(relocatedBootDir)
}
