# Game Integration (NeoForge)

*English. Russian mirror: [`../ru/game-integration.md`](../../ru/explanation/game-integration.md).*

How Aetherium meets the real Minecraft/NeoForge runtime — and how it keeps the rest of
the framework completely free of game types.

## The one impure module

Only `aetherium-loader` references NeoForge or Minecraft. It applies ModDevGradle (see
[`build-system.md`](../reference/build-system.md) ) and contains exactly one class that imports
`net.neoforged.*`: the `@Mod` entrypoint. `core`, `bytecode`, `native`, and the test mod
are grep-verified to contain **zero** `net.neoforged`/`net.minecraft` references.

## The `@Mod` entrypoint

`org.aetherium.loader.AetheriumNeoForgeEntrypoint`:

```java
@Mod(AetheriumNeoForgeEntrypoint.MOD_ID)              // MOD_ID = "aetherium"
public final class AetheriumNeoForgeEntrypoint {
    public AetheriumNeoForgeEntrypoint(IEventBus modEventBus) {
        modEventBus.addListener(this::onConstruct);   // hook the earliest phase
    }
    private void onConstruct(FMLConstructModEvent event) {
        PreFlightCheck.run();                 // 1. self-test (total, never throws)
        DispatchBootstrap.installDefaultTable();       // 2. install invokedynamic table
        initializeAetheriumMods();                     // 3. ServiceLoader-discover & init
    }
}
```

The three steps, in order, fire on `FMLConstructModEvent` — the earliest mod-lifecycle
phase — so the framework is ready before ordinary mods construct:

1. **Pre-Flight Check** — runs the ASM + native self-test and resolves the capability
   tier. Total and non-throwing; on failure it degrades and the launch proceeds.
2. **Dispatch table install** — `DispatchBootstrap` builds the `SymbolManifest` and
   installs the `MethodHandle[]` into `DispatchTable` **before** any transformed mod
   class runs a lowered call. This is the hook point the `invokedynamic` lowering needs.
3. **Mod discovery** — `ServiceLoader.load(AetheriumMod.class)` finds every Aetherium mod
   and calls `onInitialize(AetheriumContext)`. One failing mod is caught and skipped.

## The loader-agnostic mod SPI

Mods target two pure `aetherium-core` types — never NeoForge:

- `AetheriumMod` — `void onInitialize(AetheriumContext)`, registered via
  `META-INF/services/org.aetherium.core.mod.AetheriumMod`.
- `AetheriumContext` — `log(String)` + `computeTier()`. The loader supplies the impl
  (logging through NeoForge's SLF4J).

## The test mod

`aetherium-testmod` (`HelloAetheriumMod`) depends **only** on `aetherium-core`. It
implements `AetheriumMod` and makes a single Aetherium API call (`context.log(...)`) at
init. Its jar carries the ServiceLoader registration, so the loader discovers and runs it
at game start. It imports nothing from NeoForge or Minecraft — the "compile once, run on
any loader" proof.

## Runtime class interception (the missing link — wired)

Mod classes are transformed at class-load time through ModLauncher, using the same split
Mixin uses:

- **`AetheriumTransformationService`** (`ITransformationService`) — discovered via
  `META-INF/services/cpw.mods.modlauncher.api.ITransformationService` at the JVM
  class-loading bootstrap. It is our registered presence in the launch pipeline.
  ModLauncher's `ITransformer` matches *exact* class names only, so `transformers()` is
  empty and the real work is delegated to:
- **`AetheriumLaunchPlugin`** (`ILaunchPluginService`) — ModLauncher offers it *every*
  loaded class. `handlesClass(Type, isEmpty)` is the **performance gate**: a cheap prefix
  test (`AetheriumNamespaces`) that returns an empty phase set (skip) for `net.minecraft`,
  `net.neoforged`, `cpw.mods`, the JDK, and Aetherium's *own* framework packages, and
  `EnumSet.of(AFTER)` only for Aetherium-mod namespaces (seeded with the test mod,
  extensible via `-Daetherium.transform.packages=a.b,c.d`). For accepted classes,
  `processClass` serializes the node to bytes, delegates to the pure `BytecodeEngine`
  (which lowers static API calls to `invokedynamic`, verifies, and returns the **original**
  bytes on any failure), and rewrites the node only if the bytes changed.

**Separation holds:** only `aetherium-loader` touches ModLauncher/ASM; `aetherium-bytecode`
never imports either. **Fallback:** because `processClass` delegates to the engine (which
is total), a failed transform reverts to the original class and the game keeps loading.

### Verified (equivalent to `runClient`, GUI not launched)

- The loader compiles against the decompiled MC 1.21.1 + NeoForge classpath; both service
  classes implement their ModLauncher interfaces (`javap`-confirmed); `runClient` exists.
- **Discovery:** `ServiceLoader` (the mechanism ModLauncher uses at bootstrap) finds both
  `AetheriumTransformationService` and `AetheriumLaunchPlugin` from the built artifacts.
- **Filter:** `handlesClass` returns `[]` for `net/minecraft`, `net/neoforged`, and the
  loader's own classes; `[AFTER]` for `org/aetherium/testmod/*`.
- **End-to-end transform:** feeding an `org/aetherium/testmod/Demo` class that calls the
  static API facade through `processClass` rewrites its `INVOKESTATIC` into an
  `invokedynamic` (before: 1 static / 0 indy → after: 0 static / 1 indy), backed by the
  `DispatchTable` the entrypoint installs on `FMLConstructModEvent`.
- Full-project build is green; pure modules stay free of Minecraft/NeoForge.
