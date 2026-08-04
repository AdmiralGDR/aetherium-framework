# Edge — Platform Abstraction Layer (PAL)

*English. Russian mirror: [`../ru/edge-pal.md`](../../ru/explanation/edge-pal.md).*

`aetherium-edge` is the standardized bridge that lets a loader-agnostic Aetherium mod read and push
results back into the live game **without importing a single NeoForge/Fabric/Minecraft type**. It
closes the "Edge Gap": Aetherium computes off-heap and in parallel, and the PAL is how those results
re-enter vanilla.

## 1. The contract (pure, in `aetherium-edge`)

```java
PlatformBridge bridge = Platform.bridge();      // ServiceLoader-resolved; never null
bridge.platformName();                           // "neoforge" | "fabric" | "none"
bridge.isGameAvailable();                        // false outside a running game

EntityAccess e = bridge.entities();
e.byId(uuid);                                    // Optional<EntityHandle>
e.forEach(h -> h.addVelocity(0, 0.1, 0));        // visit loaded entities
e.count();

bridge.events().onServerTickEnd(() -> { ... });  // after Aetherium's Sync Barrier
bridge.events().onEntityLoad(h -> { ... });

EntityHandle h = ...;                             // id(), x()/y()/z(), setPosition(...), addVelocity(...)
```

These interfaces — `PlatformBridge`, `EntityAccess`, `EntityHandle`, `EdgeEvents` — contain **zero**
game types. `Platform` resolves the active `PlatformBridge` via `ServiceLoader`; if none is present
(unit test, CLI, dedicated compute), it returns a safe **no-op bridge** (`platform="none"`,
`count()==0`, hooks ignored) so mod code never NPEs off-platform.

### 1b. The Block PAL — blocks, block entities, and levels

Optimization mods need more than entities: they touch **blocks, block entities, and the level
itself**. `bridge.levels()` exposes that without a single `net.minecraft` import:

```java
LevelAccess levels = bridge.levels();
LevelContext level = levels.primary().orElseThrow();      // overworld; or byDimension("minecraft:the_nether")
levels.forEach(l -> ...);                                  // every loaded level

BlockPos pos = new BlockPos(0, 64, 0);                     // pure value type (offset/above/below helpers)
if (level.isLoaded(pos)) {                                 // chunk-loaded check before any touch
    BlockHandle block = level.blockAt(pos);
    block.blockId();                                       // "minecraft:stone"
    block.isAir(); block.destroySpeed();                   // common hot-path reads
    block.property("facing");                              // Optional<String> block-state property

    level.setBlock(pos, "minecraft:glowstone");            // place by registry id
    level.scheduleNeighborUpdate(pos);                     // redstone / neighbour propagation
}

level.blockEntityAt(pos).ifPresent(be -> {                 // Optional<BlockEntityAccess>
    be.typeId();                                           // "minecraft:chest"
    be.readInt("fuel");                                    // OptionalInt — typed NBT, no CompoundTag leak
    be.writeLong("aetherium:last_tick", now);              // push results back; loader marks it dirty
});
```

`BlockPos`, `BlockHandle`, `BlockEntityAccess`, `LevelContext`, `LevelAccess` are all pure: block
state is read as plain strings/values, block-entity NBT is a small typed key/value surface, and
coordinates are an immutable record — so no `Block`/`BlockState`/`BlockEntity`/`Level`/`CompoundTag`
type ever reaches mod code. The no-op bridge reports no levels (`primary()` empty), so the Block PAL is
safe to call off-platform too.

## 2. The implementation (impure, in `aetherium-loader`)

Per the separation rule, the loader — the only module that knows NeoForge — provides the impl:

- `NeoForgePlatformBridge` (`implements PlatformBridge`), registered via
  `META-INF/services/org.aetherium.edge.PlatformBridge`.
- `NeoForgeEntityHandle` wraps `net.minecraft.world.entity.Entity` (reads `getX/Y/Z/getUUID`; writes
  `setPos` / `setDeltaMovement`).
- `NeoForgePlatformEvents` subscribes to NeoForge's bus (`ServerStartingEvent`/`ServerStoppingEvent`
  capture the server; `ServerTickEvent.Post` fans out `onServerTickEnd`; `EntityJoinLevelEvent` fans
  out `onEntityLoad`), registered on `NeoForge.EVENT_BUS` by the `@Mod` entrypoint.
- `NeoForgeLevelContext` / `NeoForgeBlockHandle` / `NeoForgeBlockEntityAccess` back the Block PAL over
  `Level`: `getBlockState`/`getBlockEntity`/`isLoaded`, placement via `setBlockAndUpdate` (registry id
  parsed through `BuiltInRegistries.BLOCK`), neighbour updates via `updateNeighborsAt`, and block-entity
  NBT mapped onto `saveWithoutMetadata` / `loadWithComponents` with the level's `registryAccess()`.

Entity and level access walk `MinecraftServer.getAllLevels()` using the stable `getAllEntities()` /
`getEntity(UUID)` / `overworld()`. All hook dispatch is negative-trust: a throwing mod hook is
contained, never breaking the server tick.

## 3. Why this matters

A mod compiled against `aetherium-core` + `aetherium-edge` is **loader-portable**: the same jar runs
wherever a `PlatformBridge` is registered. To support a new loader (e.g. Fabric), add one module that
implements `PlatformBridge` and ships the `ServiceLoader` file — no mod recompilation. The edge stays
pure; only the per-loader edge module imports game types.

## — gameplay PAL (players, inventory, interactions)

The PAL now covers gameplay, not just entities/blocks: `PlayerAccess` (`PlatformBridge.players()`) and
`PlayerHandle` (name, health, chat, `inventory()`), an `InventoryAccess` that addresses items by namespaced
string id (no `ItemStack` type), and **cancellable interaction events** on `EdgeEvents` —
`onBlockInteract`, `onItemUse`, `onEntityAttack` — whose listeners return an `InteractionResult`
(`PASS`/`CANCEL`); the loader maps `CANCEL` onto cancelling the native event. The new members are `default`
no-ops, so an existing bridge keeps compiling. Proof: `aetherium gameplay`.

## Commands, lifecycle events, and persistence ()

Three gaps the a downstream mod feedback called out are now closed, all as pure SPI in `aetherium-edge` with
NeoForge implementations in the loader:

- **Commands & chat.** `EdgeCommands` (`PlatformBridge.commands()`) registers a command by name with a
  `CommandSpec` (permission level + typed `ArgType` args) and a `CommandHandler` receiving tokenized args +
  the sender `PlayerHandle`. `EdgeEvents.onChatMessage` adds the chat hook. The loader's
  `NeoForgeCommandBridge` translates each registration into Brigadier on `RegisterCommandsEvent` — no
  Brigadier type crosses the boundary.
- **Gameplay lifecycle events.** `EdgeEvents` gains `onBlockBreak` (with a player-placed flag),
  `onEntityDeath`, `onEntityDamaged`, `onPlayerJoin`/`onPlayerLeave`, and `onServerStarting`/`onServerStopping`
  — the events gameplay and persistence are actually built on. The loader now wires **every** event
  (including the interaction hooks that were previously declared but never bridged in-game) and
  implements `players()`.
- **Persistence.** `WorldStore` (`PlatformBridge.worldStore()`) reads/writes namespaced `(modId, key)`
  `TreeNode` documents atomically into the world save directory (`NeoForgeWorldStore`, `ATOMIC_MOVE` over
  `TreeCodec` bytes); an in-memory default keeps mods testable off-platform. Proof: `aetherium gameplay`.

## Ergonomics (, )

`PlayerHandle.hasPermission(int level)` lets one command gate mixed-privilege sub-commands (the loader maps
it to `ServerPlayer.hasPermissions`); `InventoryAccess.EMPTY` is the no-op inventory a fake `PlayerHandle`
returns in tests, matching `PlayerAccess.EMPTY` / `EdgeCommands.NONE` / `WorldStore.inMemory()`.

## — a second loader proves the abstraction (Fabric)

Until now the PAL had one implementation (NeoForge), so "loader-agnostic" was a claim, not a demonstration.
`aetherium-fabric` is the second: a real Fabric `ModInitializer` (`AetheriumFabricMod`) whose `onInitialize`
hands off to a loader-neutral `FabricBoot` that runs the **identical** sequence the NeoForge entrypoint runs —
it installs the same `AetheriumSymbols.MANIFEST`-keyed O(1) dispatch table, discovers every `AetheriumMod` via
`ServiceLoader`, enforces the Shield integrity manifest, and initialises each mod with an `AetheriumContext`.

Crucially, `net.fabricmc:fabric-loader` is a **plain Maven jar** carrying just the entrypoint interfaces, so it
is a `compileOnly` dependency — the boot-agnosticism compiles and is tested **without** Fabric Loom or a
remapped Minecraft. `aetherium fabric` (and `:aetherium-fabric:test`) show the shared dispatch handle
resolving `compute:doubler(21) = 42` — the same value the NeoForge table produces — and the mod SPI booting
under Fabric. The only loader-specific code is the few-line entrypoint shell; the framework itself is shared.
The remaining piece — the MC-wrapping PAL bridges (`FabricPlatformBridge` over Fabric's Yarn-mapped Minecraft)
— needs the Loom toolchain and is the documented next step; the abstraction it plugs into is already proven.

## — the local player, block placement, and what the loader ships (/)

Three small gaps the feedback named, all pure SPI additions with NeoForge wiring:

- **`PlayerAccess.local()` → `Optional<PlayerHandle>` ().** `byId`/`byName`/`online()` answer server questions;
  a *client* keybind (see [ui](ui.md) → `registerKeybind`) needed to answer "who am I" so it can open a screen
  about the player who pressed it. `local()` returns the client's own player — `Minecraft.getInstance().player`
  wrapped as a `PlayerHandle` — in single-player *and* multiplayer alike, and `Optional.empty()` on a dedicated
  server (no single local player). It is a `default` returning empty, so no existing bridge breaks; the NeoForge
  override is guarded by `FMLEnvironment.dist.isClient()` and delegates to a client-only `ClientLocalPlayer`, so a
  dedicated server never links the client type (the same isolation `NeoForgeUiAccess` uses).
- **`EdgeEvents.onBlockPlace` ().** The counterpart to `onBlockBreak`: a cancellable listener
  (`PlayerHandle` — null for a dispenser/mob placement — `BlockPos`, `blockId`) fired when a block enters the
  world, so a mod can arm/register its own block the moment it appears instead of waiting for a first
  interaction. Wired to NeoForge's `BlockEvent.EntityPlaceEvent`; `CANCEL` vetoes the placement. Proof:
  `aetherium gameplay`.
- **What the loader embeds vs what you ship ().** `aetherium-loader.jar` bundles, as Jar-in-Jar nested
  jars, exactly the runtime its `@Mod` entrypoint links against: `aetherium-core`, `-bytecode`, `-native`,
  `-edge`, `-network`, `-gfx`, `-content`, `-datagen`, `-ui`, `-shield`, `-verify` (see
  [artifact-roles](artifact-roles.md)). It does **not** embed modules it does not use — notably
  **`aetherium-config`**, and the opt-in `-security`/`-compute`/`-hotswap`/`-wasm`. A mod that uses one of these
  must add it as its own dependency (the Aetherium Gradle plugin wires the framework deps for you); otherwise it
  resolves at build time, not as a surprise at launch.

## additions

- **`InventoryAccess.selectedSlot()` / `heldItemId()`** — the player's held hotbar slot (`-1` off-platform),
  so "restrict the item I'm holding" is one always-correctly-spelled click instead of a hotbar picker.
- **`Platform.installForTesting(bridge)`** — an opt-in, reversible test hook that lets a headless test present a
  local player (`players().local()`), so a code path reading the local player is testable in its *present*
  branch, without a `META-INF/services` entry that would change the bridge for every other test. Restore with
  `installForTesting(null)`. Test scope only — production always uses the `ServiceLoader`-resolved bridge.
- **The directional `Network` facade** (serverbound receive + send, sender-aware) lives here in
  `aetherium-edge` because the serverbound handler carries a `PlayerHandle` — see [network](network.md).
