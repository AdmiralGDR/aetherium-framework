# Edge — Platform Abstraction Layer (PAL)

*English. Russian mirror: [`../ru/edge-pal.md`](../ru/edge-pal.md).*

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

## 2. The implementation (impure, in `aetherium-loader`)

Per the separation rule, the loader — the only module that knows NeoForge — provides the impl:

- `NeoForgePlatformBridge` (`implements PlatformBridge`), registered via
  `META-INF/services/org.aetherium.edge.PlatformBridge`.
- `NeoForgeEntityHandle` wraps `net.minecraft.world.entity.Entity` (reads `getX/Y/Z/getUUID`; writes
  `setPos` / `setDeltaMovement`).
- `NeoForgePlatformEvents` subscribes to NeoForge's bus (`ServerStartingEvent`/`ServerStoppingEvent`
  capture the server; `ServerTickEvent.Post` fans out `onServerTickEnd`; `EntityJoinLevelEvent` fans
  out `onEntityLoad`), registered on `NeoForge.EVENT_BUS` by the `@Mod` entrypoint.

Entity access walks `MinecraftServer.getAllLevels()` using the stable `getAllEntities()` /
`getEntity(UUID)`. All hook dispatch is negative-trust: a throwing mod hook is contained, never
breaking the server tick.

## 3. Why this matters

A mod compiled against `aetherium-core` + `aetherium-edge` is **loader-portable**: the same jar runs
wherever a `PlatformBridge` is registered. To support a new loader (e.g. Fabric), add one module that
implements `PlatformBridge` and ships the `ServiceLoader` file — no mod recompilation. The edge stays
pure; only the per-loader edge module imports game types.
