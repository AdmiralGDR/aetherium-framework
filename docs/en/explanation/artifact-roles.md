# Artifact Roles — why Aetherium ships two jars, and how they stay runnable

*"The Playable Loader" · English. Russian mirror: [`../ru/artifact-roles.md`](../../ru/explanation/artifact-roles.md).*

## 1. Why this exists

consumer feedback was the first from a real play session, and it proved the framework had never
actually run as an installed mod. Three independent defects each blocked it; this document explains the
model that fixes all three, and the self-check that keeps it fixed.

## 2. Two artifacts, two roles

A NeoForge jar can be **either** a ModLauncher transformation service (loaded into the boot/service layer at
JVM bootstrap) **or** a mod (`FMLModType: MOD`, scanned by FML and given an `@Mod` entrypoint). A single jar
that declares *both* is claimed by ModLauncher for the service layer, after which FML never scans it as a mod
— so its `@Mod` entrypoint never runs. That is why the reported symptom was "the mod is listed, but there are
no commands and no blocks."

Aetherium therefore ships two jars:

| Artifact | `FMLModType` | Contains | Layer |
|---|---|---|---|
| `aetherium-transformer` | `GAMELIBRARY` | `ITransformationService` + `ILaunchPluginService` + the ASM transform engine | boot/service |
| `aetherium-loader` | `MOD` | the `@Mod` entrypoint, the PAL/UI implementations, and the embedded runtime | mod |

The transformer is the earliest Aetherium code to run; it performs load-time bytecode injection (the "Mixin
killer"). The loader registers the Platform Abstraction Layer, content, commands, and UI.

## 3. A drop-in mod must carry its runtime (Jar-in-Jar)

`implementation(project(":aetherium-core"))` under ModDevGradle puts a module on the *Gradle* classpath but
embeds nothing in the jar — so inside `runClient` everything works, while the distributed jar cannot link the
classes its own entrypoint references. The loader now embeds the `aetherium-*` modules as whole nested jars
under `META-INF/jarjar/` (NeoForge's native Jar-in-Jar, loaded once globally). Only `aetherium-*` is embedded:
ASM and SLF4J already live in the boot layer, and a second copy of a *named module* is a split package that
aborts the launch before the window even appears.

## 4. Preview classes and the vanilla JVM

Minecraft 1.21.1 runs on Java 21, where the FFM API is still *preview*. A class that directly uses FFM is
stamped with class-file minor version `0xFFFF` and throws `UnsupportedClassVersionError` on any JVM without
`--enable-preview` — which no vanilla launcher passes, and which the `Enable-Preview` manifest attribute does
**not** enable at runtime. Aetherium keeps FFM (it is the framework's sovereign, dependency-free performance
path) but keeps it **off the boot-critical path**:

- the transformer and the `@Mod` entrypoint are compiled *without* `--enable-preview`, so they carry minor
  `0x0000` and load anywhere;
- the transformer detects the flag at bootstrap and prints a clear, bilingual **AE-JAVA-002** advisory when it
  is absent, instead of leaving the player to decode a stack trace;
- the entrypoint's few FFM touchpoints (the SIMD boot banner, StructArena network registration) are guarded,
  so a flag-less JVM still boots and registers — those specific features simply degrade.

Isolating *every* FFM-using class behind `CapabilityTier` so the network/compute/SIMD modules also load
flag-free is a deliberate follow-up.

## 5. The framework verifies its own artifacts

`./gradlew verifyJar` runs `aetherium-verify:ArtifactVerifier`, which uses Aetherium's **own** ASM to assert,
on the shipped jars, that:

1. the loader is **self-contained** — every `org/aetherium/**` class it references is present directly, via
   Jar-in-Jar, or in the sibling transformer jar;
2. the boot layer is **preview-free** — no `0xFFFF` classes in the transformer or the `@Mod` entrypoint;
3. **no platform library is bundled** — no top-level `org/objectweb/asm` or `org/slf4j`;
4. the **roles are correct** — loader = `MOD` with no ModLauncher services, transformer = `GAMELIBRARY` with
   both services.

It is wired into `check`, so a regression fails the build instead of a player's launch. This is sovereignty
made concrete: the framework proves its own output correct rather than trusting the build.

## 6. "Ignition" — no package crosses the artifact boundary

tested the build in a real session and hit the last launch blocker: **the game did not start
at all.** The two-artifact split was correct in isolation, but the two jars *together* formed an illegal
module graph. `aetherium-transformer` runs in the boot layer *before* Jar-in-Jar extraction, so it must carry
`org/aetherium/{core,bytecode,injector}` as **loose** classes; `aetherium-loader` is a normal mod, so it
carries the same modules as **nested jars** under `META-INF/jarjar/`. NeoForge turns each into its own Java
module — and two modules that both export `org.aetherium.core` is a `java.lang.module.ResolutionException`
before any window opens (the message even blamed an unrelated mod).

The fix keeps both roles and removes the overlap: at packaging time the transformer's embedded copy is
**relocated** into a private `org.aetherium.boot.*` prefix using the framework's **own** `ClassRelocator`
(ASM `ClassRemapper`, the same tool `DependencyFlattener` already uses) driven by the new `BootRelocator` CLI.
The boot jar now exports `org/aetherium/transformer` + `org/aetherium/boot/{core,bytecode,injector}`; the
loader's nested jars still export `org/aetherium/{core,bytecode,injector}` — disjoint sets, no clash. The
relocation is deterministic, so the jar stays byte-reproducible.

One prerequisite made this safe: `AetheriumSymbols` (the shared dispatch manifest) moved from the transformer
to `org.aetherium.core.dispatch` in `aetherium-core`, so the loader and Fabric reference *their own* core copy
while the transformer references its *relocated* one. Both manifests have identical content → identical
dispatch IDs, so the table the loader installs and the `invokedynamic` sites the transformer writes still
agree.

`ArtifactVerifier` now **reproduces this offline** (`AE-MODULE-CLASH`, see [verify](verify.md)): it enumerates
every module the shipped set produces — each jar's loose classes as one module plus one per
`META-INF/jarjar/*.jar` — and fails if any package appears in two. The old fat build fails it; the relocated
build passes; and a real headless NeoForge server now boots to `Done` with the framework in the mod list (see
[how-to: prove the launch](../how-to/verify-the-launch.md)).

## 7. Why not a new programming language?

asked, given the volume of code ahead, whether Aetherium should build its own programming language.
The answer is **no, deliberately and permanently.** A new language is the largest liability MANIFEST warns
against: it *is* a new dependency (its own compiler and runtime), it abandons the JVM mechanical sympathy the
whole framework is built on (O(1) `invokedynamic` dispatch, load-time ASM, FFM/SIMD), and every mod author
would have to learn it. "Lower-level access and bigger tools" for authors is instead delivered by the
sovereign layer the framework *already owns* — the pure `BytecodeEngine`, the fluent injector (the "Mixin
killer"), the declarative `@Aetherium*` annotations, and the Kotlin DSL — which give power without inventing a
language. This is recorded here so the decision is not revisited by accident.
