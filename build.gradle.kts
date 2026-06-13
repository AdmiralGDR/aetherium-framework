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

subprojects {
    apply(plugin = "java-library")

    repositories {
        mavenCentral()
        maven {
            name = "NeoForged"
            url = uri("https://maven.neoforged.net/releases")
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
    tasks.withType<JavaCompile>().configureEach {
        options.release.set(javaVersion)
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("--enable-preview", "-Xlint:all,-preview,-processing"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        jvmArgs("--enable-preview")
    }

    tasks.withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions).apply {
            addBooleanOption("-enable-preview", true)
            addStringOption("-release", javaVersion.toString())
            encoding = "UTF-8"
            quiet()
        }
    }

    // Stamp every jar so the artifact is self-describing and reproducible.
    tasks.withType<Jar>().configureEach {
        manifest {
            attributes(
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
                "Implementation-Vendor" to "Aetherium Framework",
                "Enable-Preview" to "true"
            )
        }
    }
}
