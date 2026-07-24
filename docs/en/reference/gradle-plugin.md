# Gradle Plugin — Zero-Config Mod Builds

*English. Russian mirror: [`../ru/gradle-plugin.md`](../../ru/reference/gradle-plugin.md).*

The `org.aetherium.gradle` plugin (`aetherium-gradle-plugin`) collapses an Aetherium mod's entire build
to a single DSL block. It replaces the old workflow of vendoring a physical `aetherium-core.jar` and
hand-wiring Shadow/JarJar — the bottleneck reported by the LoomThreader port.

## 1. Prerequisites

Publish the framework to your local Maven once (any consumer resolves it by coordinate afterwards):

```bash
cd AetheriumFramework
./gradlew publishToMavenLocal
```

This installs `org.aetherium:aetherium-core|bytecode|native|edge:1.0.0-SNAPSHOT` **and** the plugin
marker `org.aetherium.gradle` into `~/.m2`.

## 2. Usage (the whole build)

`settings.gradle.kts`:

```kotlin
pluginManagement { repositories { mavenLocal(); gradlePluginPortal() } }
rootProject.name = "my-mod"
```

`build.gradle.kts`:

```kotlin
plugins { id("org.aetherium.gradle") version "1.0.0-SNAPSHOT" }

aetherium {
    version = "1.0.0-SNAPSHOT"   // the only required setting
}
```

That single block applies `java-library`, pins the **Java 21 toolchain with `--enable-preview`**
(the public API uses preview FFM), wires `mavenLocal` + `mavenCentral` + NeoForged, and adds the
`aetherium-core` + `aetherium-edge` dependencies. The modder writes against the Aetherium API only.

## 3. The DSL (`AetheriumExtension`)

| Property | Default | Meaning |
|---|---|---|
| `version` | `1.0.0-SNAPSHOT` | Aetherium framework version resolved from Maven. |
| `modId` | project name | Mod id for the generated loader metadata (sanitized to `[a-z][a-z0-9_]*`). |
| `displayName` | `modId` | Human-readable mod name in the metadata. |
| `bundle` | `true` | Register `aetheriumBundle` (JarJar-style self-contained jar). |
| `includeBytecode` | `false` | Also depend on `aetherium-bytecode` (mods shipping their own transformers). |
| `generateMetadata` | `true` | Auto-generate `neoforge.mods.toml` + `fabric.mod.json`. |

The mod's **own** version comes from the standard Gradle `version = "..."`; `aetherium.version` is the
*framework* version.

## 4. Loader metadata — the "not a mod" fix

With `generateMetadata` on (default), the plugin runs `generateAetheriumMetadata` before
`processResources`, writing both `META-INF/neoforge.mods.toml` and `fabric.mod.json` into a generated
resources dir that feeds **both** the normal `jar` and `aetheriumBundle`. So the single output jar is
recognized natively by NeoForge **and** Fabric with zero hand-authored metadata — closing the gap where
a bundled jar was rejected as "not a mod". The `modId` is sanitized to NeoForge's required shape
(`[a-z][a-z0-9_]*`, hyphens become underscores), so it is valid on both loaders.

## 5. Packaging — `aetheriumBundle`

```bash
./gradlew build           # → build/libs/my-mod-<v>.jar          (mod classes only)
./gradlew aetheriumBundle # → build/libs/my-mod-<v>-bundle.jar   (mod + embedded aetherium-*)
```

`aetheriumBundle` embeds **only** the `aetherium-*` artifacts from the runtime classpath (never
Minecraft/NeoForge), producing one drop-in jar with `DuplicatesStrategy.EXCLUDE`.

## 5. Verified

A dummy consumer applying only the plugin builds with no vendored jars: the plugin + all API
artifacts resolve from `mavenLocal`, `build` produces the mod jar, and `aetheriumBundle` produces a
self-contained jar embedding the framework (49 `org/aetherium/*` classes for a core+edge mod).

## — Universal Jar

With `aetherium { universal = true }`, the plugin registers an `aetheriumUniversalJar` task that produces a
single `<name>-universal.jar` embedding the mod, the whole Aetherium runtime (core + loader, only
`aetherium-*` artifacts — never Minecraft/NeoForge), and the **unified** `META-INF/neoforge.mods.toml` +
`fabric.mod.json` metadata, stamped with an `Aetherium-Universal: true` manifest advertising both loaders.
The result is a foolproof, drop-in jar for players on either loader. `embedLoader` (default true) toggles
embedding the loader.
