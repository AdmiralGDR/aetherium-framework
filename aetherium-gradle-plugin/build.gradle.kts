/*
 * aetherium-gradle-plugin — zero-config build plugin for mod developers.
 *
 * EN: Provides the `aetherium { version = "..." }` DSL. Applying this plugin auto-configures a mod
 *     project: Java 21 toolchain + --enable-preview, the Maven repositories, the Aetherium API
 *     dependencies, and a bundling task that JarJar-style embeds the Aetherium runtime into the mod
 *     jar. NOT compiled with --enable-preview (guarded in the root build) because plugin classes run
 *     in the Gradle daemon. Published to Maven so mods apply it by id.
 * RU: Предоставляет DSL `aetherium { version = "..." }`. Применение плагина автоматически настраивает
 *     мод-проект: тулчейн Java 21 + --enable-preview, Maven-репозитории, зависимости API Aetherium и
 *     задачу упаковки, встраивающую рантайм Aetherium в jar мода. НЕ компилируется с --enable-preview
 *     (запрещено в корневой сборке), т.к. классы плагина выполняются в демоне Gradle. Публикуется в Maven.
 */

plugins {
    `java-gradle-plugin`
    `maven-publish` // so java-gradle-plugin contributes the plugin + marker publications to mavenLocal
}

gradlePlugin {
    plugins {
        create("aetheriumPlugin") {
            id = "org.aetherium.gradle"
            implementationClass = "org.aetherium.gradle.AetheriumGradlePlugin"
            displayName = "Aetherium Gradle Plugin"
            description = "Zero-config builds for Aetherium-powered Minecraft mods."
        }
    }
}
