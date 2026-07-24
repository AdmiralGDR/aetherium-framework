# Performance Architecture

*English. Russian mirror: [`../ru/performance.md`](../../ru/explanation/performance.md).*

Aetherium attacks Minecraft's fundamental limits — Library Hell, cache misses, and the
single-thread tick — with low-level machinery the framework hides behind a tiny API.

## 1. Dependency deduplication (Library Hell)

`org.aetherium.loader.DependencyFlattener` resolves the union of every mod's embedded
libraries to **one winner per `group:artifact`** (highest version), with a conflict log,
so only a single instance of each library exists in the JVM. Pure and unit-testable.

```text
input: kotlin-stdlib 1.8.10 (ModA), 1.9.24 (ModB), 1.9.0 (ModC), guava 31.1 (ModA), 33.0 (ModD)
→ winners: kotlin-stdlib 1.9.24, guava 33.0-jre   (deduped 3, conflicts logged)
```

## 2. Data-oriented memory — `StructArena` (anti-cache-miss)

`org.aetherium.core.compute.StructArena` stores N entities **contiguously off-heap** (FFM),
so iterating them walks memory linearly and maximizes L1/L2 cache hits. No per-entity Java
objects → no GC pressure, no pointer-chasing.

```java
StructLayout entity = StructLayout.builder()
    .doubles("x").doubles("y").doubles("z")
    .doubles("vx").doubles("vy").doubles("vz")
    .build();
try (StructArena arena = StructArena.allocate(entity, 10_000)) {
    StructField x = entity.field("x"), vx = entity.field("vx");
    arena.setDouble(i, x, arena.getDouble(i, x) + arena.getDouble(i, vx));
}
```

Access is `segment.get(layout, index*stride + offset)` — one bounds-checked op, `O(1)`,
no allocation. Disjoint slices can be updated by different threads with no locks.

## 3. Async tick — `AetheriumTickEngine` + `@AetheriumAsyncTick`

`org.aetherium.core.tick.AetheriumTickEngine` offloads heavy logic onto Java 21 virtual
threads and joins them at a **Sync Barrier** before the 50 ms tick ends, then commits
results on the main thread — so parallel ticking is free of `ConcurrentModificationException`.

Zero boilerplate — a modder writes only:

```java
@AetheriumAsyncTick("physics")
void updatePhysics() { /* heavy work on data this method owns */ }
// engine.registerAnnotated(myMod);  engine.tick();
```

Or programmatically via `AsyncTickTask` (`computeAsync()` parallel phase + `commit()`
main-thread phase). A task that throws or overruns the budget is contained and counted in
the `TickReport`; the tick never crashes.

## 4. SIMD & memory-mapped streaming (placeholders/bridges)

- `org.aetherium.core.simd.SimdMath` — bulk vector math (FMA, scale, dot) with a correct
  scalar implementation now and `isVectorApiAvailable()` detecting the incubating Java
  Vector API at runtime (no hard `--add-modules` dependency forced on consumers).
- `org.aetherium.core.io.MappedRegion` — maps files into an FFM `MemorySegment` via
  `FileChannel.map(..., Arena)` for zero-GC, zero-heap chunk/asset streaming; the mapping
  is Arena-scoped and unmapped deterministically on `close()`.

## 5. Verified stress test (`aetherium-cli entitysim`)

10,000 data-oriented entities advanced in parallel across 250 virtual threads/tick for
200 ticks:

```text
entities 10,000 · off-heap 480,000 bytes (zero GC) · 200 ticks · 250 vthreads/tick
2,000,000 entity updates in ~111 ms → ~18,000,000 updates/sec · slowest tick 25.9 ms (<50 ms budget)
escapes=0 · deadlocks=none (all ticks joined at the Sync Barrier) · mismatches=0 · @AetheriumAsyncTick DX OK
RESULT: PASS ✓
```

Correctness is verified exactly (every entity advanced by `ticks × velocity`), proving the
parallel updates are race-free thanks to disjoint off-heap slices.
