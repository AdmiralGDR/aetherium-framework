/*
 * Aetherium Framework — root build script.
 *
 * EN: Applies shared configuration to every module: Java 21 toolchain (resolved to the local
 *     GraalVM), globally enabled `--enable-preview` for the FFM API, UTF-8, and the two Maven
 *     repositories we draw from. Per-module specifics live in each module's build.gradle.kts.
 *     `aetherium-core` stays a leaf (no internal dependencies) — enforced by convention, see
 *     ARCHITECTURE.md 
 * RU: Применяет общую конфигурацию ко всем модулям: тулчейн Java 21 (разрешается в локальный
 *     GraalVM), глобально включённый `--enable-preview` для FFM API, UTF-8 и два Maven-репозитория.
 *     Специфика модулей — в их собственных build.gradle.kts. `aetherium-core` остаётся листом
 *     (без внутренних зависимостей) — по соглашению, см. ARCHITECTURE.md 
 */

plugins {
    java
}

val aetheriumGroup: String = providers.gradleProperty("aetherium.group").get()
val aetheriumVersion: String = providers.gradleProperty("aetherium.version").get()
val javaVersion: Int = libs.versions.java.get().toInt()

allprojects {
    group = aetheriumGroup
    version = aetheriumVersion
}

// Library modules that publish to Maven so dependent mods can resolve them by coordinate.
val publishableModules = setOf(
    "aetherium-core", "aetherium-bytecode", "aetherium-native", "aetherium-edge",
    "aetherium-network", "aetherium-config", "aetherium-gfx", "aetherium-datagen", "aetherium-content",
    "aetherium-injector", "aetherium-shield", "aetherium-verify", "aetherium-security",
    "aetherium-compute", "aetherium-hotswap", "aetherium-wasm", "aetherium-ktx",
    "aetherium-ui",
    // b: the boot-layer transformation service is its own publishable artifact.
    "aetherium-transformer")

// Some modules must NOT be compiled with --enable-preview:
//  - aetherium-gradle-plugin: its classes run in the Gradle daemon, which refuses preview classes.
//  - aetherium-datagen / aetherium-content: these run as ANNOTATION PROCESSORS inside the consumer's
//    javac. Preview-flagged processor classes fail to load unless the compiler JVM also has the flag,
//    so we keep them plain (they are pure Java and use no FFM/preview API anyway).
//  - aetherium-transformer: runs in the ModLauncher BOOT layer, before any mod loads, on whatever JVM
//    the player launched. It must load without `--enable-preview` (c), so we compile it plain.
//    This is also a hard guardrail: if any boot-path class ever reaches for an FFM/preview API, the
//    build fails here instead of the player's launch. (Its deps — bytecode/injector/core-subset — are
//    all FFM-free, so they carry class-file minor 0x0000 and load anywhere.)
private val nonPreviewModules = setOf(
    "aetherium-gradle-plugin", "aetherium-datagen", "aetherium-content", "aetherium-transformer")
fun Project.usesPreview(): Boolean = name !in nonPreviewModules

subprojects {
    apply(plugin = "java-library")

    repositories {
        mavenCentral()
        maven {
            name = "NeoForged"
            url = uri("https://maven.neoforged.net/releases")
        }
    }

    // Maven publishing for the consumable library modules (publishToMavenLocal).
    if (name in publishableModules) {
        apply(plugin = "maven-publish")
        configure<PublishingExtension> {
            publications {
                create<MavenPublication>("maven") {
                    from(components["java"])
                }
            }
        }
    }

    // Pin the toolchain to Java 21. Auto-download is disabled (gradle.properties) so this
    // resolves to the locally installed GraalVM 21 — keeping compile-time FFM preview in
    // lock-step with the runtime.
    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(javaVersion))
        }
        withSourcesJar()
    }

    // Globally enable preview features (FFM / java.lang.foreign lives behind --enable-preview
    // on Java 21). Centralized here, never scattered per-module (ARCHITECTURE.md ).
    val preview = usesPreview()
    tasks.withType<JavaCompile>().configureEach {
        options.release.set(javaVersion)
        options.encoding = "UTF-8"
        if (preview) {
            options.compilerArgs.addAll(listOf("--enable-preview", "-Xlint:all,-preview,-processing"))
        } else {
            options.compilerArgs.add("-Xlint:all,-processing")
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        if (preview) {
            // --enable-preview for the FFM API; --enable-native-access so FFM downcalls (the shield's Zig
            // NativeGuard, the native bridge) don't warn/deny; the Vector API module for SIMD-touching code.
            jvmArgs("--enable-preview", "--enable-native-access=ALL-UNNAMED",
                "--add-modules=jdk.incubator.vector")
        }
    }

    tasks.withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions).apply {
            if (preview) {
                addBooleanOption("-enable-preview", true)
            }
            addStringOption("-release", javaVersion.toString())
            encoding = "UTF-8"
            quiet()
        }
    }

    // Stamp every jar so the artifact is self-describing, and make it byte-for-byte REPRODUCIBLE
    // (MANIFEST axiom V — Cryptographic Reproducibility): a normalized entry order and zeroed timestamps
    // mean the same sources produce the same jar hash on any machine. Verified by `verifyReproducible`.
    tasks.withType<Jar>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
        manifest {
            attributes(
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
                "Implementation-Vendor" to "Aetherium Framework"
            )
            // c: only claim Enable-Preview on modules actually compiled with it. Stamping it
            // on a preview-free jar (e.g. aetherium-transformer, the boot-layer service) is misleading —
            // the author rightly flagged that this attribute enables nothing at runtime anyway.
            if (preview) {
                attributes("Enable-Preview" to "true")
            }
        }
    }
}
