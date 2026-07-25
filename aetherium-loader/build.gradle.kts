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
    implementation(project(":aetherium-injector"))  // fluent bytecode injection (Mixin killer) bridge
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
