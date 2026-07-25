/*
 * aetherium-loader — the ONLY module that integrates with the game (NeoForge via ModDevGradle).
 *
 * EN: Composes core + bytecode + native and adds the NeoForge @Mod entrypoint. ModDevGradle
 *     provides the decompiled Minecraft 1.21.1 + NeoForge classpath for compilation and the dev
 *     `runClient` task. The architectural rule holds: core/bytecode/native stay pure of any
 *     Minecraft/NeoForge type — only THIS module sees them. The produced jar is a drop-in mod
 *     (META-INF/neoforge.mods.toml).
 * RU: Композирует core + bytecode + native и добавляет точку входа @Mod NeoForge. ModDevGradle
 *     предоставляет декомпилированный classpath Minecraft 1.21.1 + NeoForge для компиляции и dev-
 *     задачу `runClient`. Архитектурное правило соблюдено: core/bytecode/native остаются чистыми
 *     от типов Minecraft/NeoForge — только ЭТОТ модуль их видит. Полученный jar — drop-in мод
 *     (META-INF/neoforge.mods.toml).
 */

plugins {
    alias(libs.plugins.moddev)
}

dependencies {
    api(project(":aetherium-core"))
    implementation(project(":aetherium-bytecode"))
    implementation(project(":aetherium-native"))
    implementation(project(":aetherium-edge"))    // loader provides the NeoForge PAL implementation
    implementation(project(":aetherium-network")) // loader bridges the payload SPI to PayloadRegistrar
    implementation(project(":aetherium-gfx"))     // loader bridges the render SPI to EntityRenderersEvent
    implementation(project(":aetherium-content"))  // declarative content annotations + runtime index
    implementation(project(":aetherium-datagen"))  // ContentIndex/ContentEntry model (pure, no MC)
    implementation(project(":aetherium-transformer")) // b: boot-layer transform service + shared symbols
    implementation(project(":aetherium-ui"))        // loader provides the GuiGraphics-backed UiRenderer + Screen
    implementation(project(":aetherium-shield"))    // runtime ModVerifier (integrity enforcement at init)
    implementation(project(":aetherium-verify"))    // in-game mod inspector + ModInspector snapshot
}

// ModDevGradle: decompiled Minecraft + NeoForge for this module only.
neoForge {
    version = libs.versions.neoforge.get()

    // A client dev-run (NOT executed in CI/headless here — we only verify compile & classpath).
    runs {
        register("client") {
            client()
        }
    }

    // Associate our main source set with the mod id declared in neoforge.mods.toml.
    mods {
        register("aetherium") {
            sourceSet(sourceSets["main"])
        }
    }
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

// ---- a: Jar-in-Jar the aetherium-* runtime this @Mod jar links against ------------------
// A drop-in @Mod jar must carry its own runtime, or FML lists it and the @Mod entrypoint fails to link
// against org/aetherium/core|edge|shield/** — the reported "mod present, no commands, no blocks".
// `implementation(project(...))` under ModDevGradle embeds NOTHING; inside runClient those projects sit
// on the Gradle classpath, which is exactly why this defect never surfaced in-repo. We embed ONLY the
// aetherium-* modules as whole nested jars under META-INF/jarjar (NeoForge's native, load-once-globally
// mechanism), NEVER ASM/SLF4J/platform libs — a second copy of those is a split package that aborts the
// launch before the window (). aetherium-transformer is EXCLUDED: it is the boot-layer GAMELIBRARY and
// ships as its own jar. Filtering to aetherium-* keeps this immune to the transitive-resolution trap.
val aetheriumJijJars = provider {
    configurations.getByName("runtimeClasspath").files.filter { f ->
        f.name.startsWith("aetherium-") && f.name.endsWith(".jar") &&
            !f.name.startsWith("aetherium-loader") && !f.name.startsWith("aetherium-transformer")
    }
}
val loaderJijDir = layout.buildDirectory.dir("generated/aetherium/loader-jarjar")
val jijGroup = project.group.toString()
val jijVersion = project.version.toString()

val generateLoaderJijMetadata by tasks.registering {
    group = "aetherium"
    description = "Writes META-INF/jarjar/metadata.json describing the loader's embedded aetherium-* runtime."
    dependsOn(configurations.named("runtimeClasspath"))
    inputs.files(aetheriumJijJars)
    outputs.dir(loaderJijDir)
    doLast {
        val metaInf = loaderJijDir.get().dir("META-INF/jarjar").asFile
        metaInf.mkdirs()
        val entries = aetheriumJijJars.get().sortedBy { it.name }.joinToString(",\n") { f ->
            val artifact = f.name.removeSuffix(".jar").removeSuffix("-$jijVersion")
            """    {
      "identifier": { "group": "$jijGroup", "artifact": "$artifact" },
      "version": { "range": "[$jijVersion,)", "artifactVersion": "$jijVersion" },
      "path": "META-INF/jarjar/${f.name}",
      "isObfuscated": false
    }"""
        }
        metaInf.resolve("metadata.json").writeText("{\n  \"jars\": [\n$entries\n  ]\n}\n")
    }
}

// Mark the jar as a NeoForge MOD-type artifact and embed the runtime under META-INF/jarjar.
tasks.named<Jar>("jar") {
    dependsOn(generateLoaderJijMetadata, configurations.named("runtimeClasspath"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes(
            "FMLModType" to "MOD",
            "Specification-Title" to "Aetherium",
            "Specification-Vendor" to "Aetherium Framework",
            "Specification-Version" to "1"
        )
    }
    from(aetheriumJijJars) { into("META-INF/jarjar") } // whole nested jars (not unzipped)
    from(loaderJijDir)                                 // the metadata.json that lists them
}

// ---- /: sovereign artifact self-check (the author's requested "five-line ASM/zip test") --
// Runs the framework's OWN ASM (aetherium-verify:ArtifactVerifier) over the two shipped jars and fails
// the build unless the loader is self-contained, the boot layer is preview-free, no platform library is
// bundled, and the MOD/GAMELIBRARY roles are correct. This would have caught every sub-failure before
// release. Wired into `check`, so a regression fails CI instead of a player's launch.
val artifactVerifierCp: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}
dependencies {
    artifactVerifierCp(project(":aetherium-verify")) // brings ASM transitively (via the injector's api)
}
val loaderJarFile = tasks.named<Jar>("jar").flatMap { it.archiveFile }
val transformerJarFile = project(":aetherium-transformer").tasks.named<Jar>("jar").flatMap { it.archiveFile }
val verifyJar by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Verify the shipped loader + transformer jars are actually runnable (own-ASM self-check)."
    dependsOn("jar", ":aetherium-transformer:jar")
    classpath = artifactVerifierCp
    mainClass.set("org.aetherium.verify.ArtifactVerifier")
    // aetherium-verify is a preview module; the flags are harmless for the preview-free ArtifactVerifier
    // and keep any transitively-touched class loadable on the toolchain JVM.
    jvmArgs("--enable-preview", "--enable-native-access=ALL-UNNAMED", "--add-modules=jdk.incubator.vector")
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(loaderJarFile.get().asFile.absolutePath, transformerJarFile.get().asFile.absolutePath)
    })
}
tasks.named("check") { dependsOn(verifyJar) }
