# Build System

*English. Russian mirror: [`../ru/build-system.md`](../ru/build-system.md).*

## 1. Overview

Aetherium uses a **Gradle multi-project build** (Gradle 8.8, Kotlin DSL). The wrapper is
committed, so `./gradlew` bootstraps the exact build everywhere — no global Gradle
install required.

```
settings.gradle.kts        module graph (rootProject.name = "aetherium")
build.gradle.kts           shared config applied to every module
gradle.properties          runtime knobs (no JDK auto-download, parallel, caching)
gradle/libs.versions.toml  centralized version catalog (anti-hardcoding)
gradle/wrapper/…           committed Gradle 8.8 wrapper
<module>/build.gradle.kts  per-module specifics
```

## 2. Toolchain & preview features

The root script pins the **Java 21 toolchain** for every module:

```kotlin
configure<JavaPluginExtension> {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}
```

JDK **auto-download is disabled** (`gradle.properties`), so the toolchain resolves to
the locally installed **GraalVM 21**. This is deliberate: the FFM API
(`java.lang.foreign`) is a *preview* feature in Java 21, and the compile-time and
runtime JDKs must match exactly. `--enable-preview` is enabled **globally and centrally**:

- `JavaCompile`: `options.release = 21` + `--enable-preview`
- `Test`: `jvmArgs("--enable-preview")`
- `Javadoc`: `-enable-preview`
- `aetherium-cli` (application): `applicationDefaultJvmArgs = ["--enable-preview"]`

Verification: compiled classes carry class-file **minor version `65535` (`0xFFFF`)**, the
JVM's preview marker. `./gradlew :aetherium-cli:run` reports `preview : enabled`.

## 3. Module graph & the leaf rule

```
aetherium-cli ──► aetherium-loader ──► aetherium-core ◄── aetherium-bytecode
                          │                  ▲                    │
                          └──────────────────┴──────── aetherium-native
```

- `aetherium-core` declares **no internal dependencies** — it is the leaf. Its
  `build.gradle.kts` intentionally has an empty dependency block; adding a
  `project(...)` there is a design violation (`ARCHITECTURE.md` ).
- `aetherium-bytecode` and `aetherium-native` depend on `core` only.
- `aetherium-loader` composes all three. `aetherium-cli` is the application front-end.
- No cycles. `./gradlew projects` and `./gradlew build` both pass.

## 4. Dependencies & the version catalog

All versions live in `gradle/libs.versions.toml` — never inlined in module scripts.
The bytecode engine pulls the full ASM surface via a single bundle:

```kotlin
implementation(libs.bundles.asm)   // asm, asm-tree, asm-commons, asm-util, asm-analysis @ 9.8
```

The build is **offline-capable** (`./gradlew build --offline`) because the pinned ASM
9.8 artifacts are present in the local Gradle cache.

## 5. NeoForge integration — drop-in mod packaging

The product must drop into a standard `mods/` folder and co-exist with ordinary mods,
**no special player setup**. Recognition by NeoForge's FML is driven by mod metadata,
so `aetherium-loader` ships a valid `META-INF/neoforge.mods.toml`:

- Version/range fields are **templated by Gradle** (`processResources` → `expand`) from
  the version catalog, so they can never drift from the build.
- The jar manifest is stamped `FMLModType = MOD`.
- Dependencies on `neoforge`/`minecraft` use `ordering = "NONE"`, `side = "BOTH"` — we
  integrate non-invasively and never force load-order conflicts on other mods.

### ModDevGradle integration (wired)

`aetherium-loader` applies the **ModDevGradle** plugin (`net.neoforged.moddev`, from the
catalog) — and it is the *only* module that does. It provides the decompiled Minecraft
1.21.1 + NeoForge `21.1.x` classpath for compilation and a `runClient` dev task:

```kotlin
plugins { alias(libs.plugins.moddev) }
neoForge {
    version = libs.versions.neoforge.get()
    runs { register("client") { client() } }
    mods { register("aetherium") { sourceSet(sourceSets["main"]) } }
}
```

- The `@Mod` entrypoint (`AetheriumNeoForgeEntrypoint`) compiles against the real
  decompiled MC/NeoForge classpath (verified: ~5300 MC sources recompiled, our class
  built). `./gradlew :aetherium-loader:tasks` shows `runClient`.
- **Separation of concerns holds:** `core`, `bytecode`, `native`, and `aetherium-testmod`
  contain **zero** `net.neoforged`/`net.minecraft` references (grep-verified). Only the
  single entrypoint class imports NeoForge. ModDevGradle keeps the MC classpath off
  downstream consumers (e.g. `aetherium-cli`), so the rest of the build stays light.
- Running the GUI is out of scope here; we verify *compile + classpath resolution* only.

## 6. The `aetherium-core` API surface

Implemented in this phase (all in `org.aetherium.core`, the leaf):

| Type | Role |
|------|------|
| `Symbol` (record) | Abstract API symbol with a dense, build-assigned integer ID. |
| `SymbolManifest` (sealed) + `Builder` | Immutable id↔symbol map; `byId(int)` is the `O(1)` runtime path. |
| `ArraySymbolManifest` | Flat-array impl — array index, no hashing. |
| `CapabilityTier` (enum) | The fallback ladder `FFM → JNI → PURE_JAVA → DISABLED`. |
| `Capability` (record) | Namespaced capability descriptor. |
| `CapabilityProvider` | A provider at a tier, with a load-time `isAvailable()` probe. |
| `FallbackChain<P>` | Ordered providers; resolves first available, swallows probe failures. |
| `CapabilityRegistry` | Probes once, memoizes; `O(1)` lookups thereafter. |
| `Diagnostic` (record) + `AetheriumException` | Structured, host-safe error model. |
| `compute.OffHeapAllocator` | FFM `Arena`-based off-heap memory (with confined default). |
| `compute.ComputePipeline` | Async GPU/accelerated compute contract (placeholder). |
| `compute.ComputeCapabilities` | Well-known compute `Capability` constants. |

## 7. Common commands

```bash
./gradlew projects                # show the module graph (config check)
./gradlew build                   # compile + assemble all modules
./gradlew build --offline         # same, using only the local cache
./gradlew :aetherium-cli:run      # run the CLI (with --enable-preview)
./gradlew :aetherium-loader:jar   # produce the drop-in NeoForge mod jar
```
