/*
 * LoomThreader demo — a standalone mod project that consumes Aetherium via Gradle/Maven
 * (no vendored jars). Resolves the framework + the zero-config plugin from mavenLocal.
 */
pluginManagement {
    repositories {
        mavenLocal()        // the locally-published Aetherium Gradle plugin
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "loomthreader-demo"
