# Game Integration (NeoForge)

*English. Russian mirror: [`../ru/game-integration.md`](../ru/game-integration.md).*

How Aetherium meets the real Minecraft/NeoForge runtime — and how it keeps the rest of
the framework completely free of game types.

## The one impure module

Only `aetherium-loader` references NeoForge or Minecraft. It applies ModDevGradle (see
[`build-system.md`](build-system.md) ) and contains exactly one class that imports
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

## What is verified vs. next steps

- **Verified here:** the loader compiles against the decompiled MC 1.21.1 + NeoForge
  classpath; the `@Mod` entrypoint, dispatch-table install, and ServiceLoader wiring
  compile and resolve; `runClient` exists; full-project build is green; separation of
  concerns holds. (The GUI is intentionally not launched in this environment.)
- **Next step:** register a NeoForge `ITransformationService` so mod classes are run
  through the `BytecodeEngine` at class-load time — that turns the testmod's API calls
  into the linked `invokedynamic` sites the installed `DispatchTable` already backs.
  The dispatch mechanism itself is already proven end-to-end by `aetherium-cli selftest`.
