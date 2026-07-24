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
