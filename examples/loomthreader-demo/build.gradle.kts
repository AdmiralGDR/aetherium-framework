/*
 * The ENTIRE build configuration a modder writes. The Aetherium plugin does the rest:
 * Java 21 + --enable-preview, the Maven repositories, the aetherium-core + aetherium-edge
 * dependencies, and a bundling task (`aetheriumBundle`) that embeds the runtime into the mod jar.
 */
plugins {
    id("org.aetherium.gradle") version "1.0.0-SNAPSHOT"
}

group = "com.example"
version = "0.1.0"

aetherium {
    version = "1.0.0-SNAPSHOT"
}
