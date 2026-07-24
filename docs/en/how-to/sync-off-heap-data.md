# How to sync off-heap entity data over the network

*English. Русская версия: [`../../ru/how-to/sync-off-heap-data.md`](../../ru/how-to/sync-off-heap-data.md).*

*A task-oriented guide. The designs behind these APIs are explained in
[performance](../explanation/performance.md), [delta-sync](../explanation/delta-sync.md) and
[network](../explanation/network.md).*

## Store your entities off-heap

Define a schema once and allocate a contiguous, GC-free arena:

```java
StructLayout layout = StructLayout.builder()
        .doubles("x").doubles("y").doubles("z")
        .floats("health")
        .build();

try (StructArena entities = StructArena.allocate(layout, 10_000)) {
    StructField x = layout.field("x");
    entities.setDouble(42, x, 128.5);        // O(1), bounds-checked, no allocation
}
```

Always allocate in a try-with-resources (or close explicitly): the entire arena frees
deterministically on `close()` — this is the zero-leak contract that `aetherium ffmaudit` proves.

## Send only what changed (flat delta-sync)

Track dirty rows against a shadow copy and transmit only those:

```java
StructArenaDelta delta = new StructArenaDelta(entities, rowCount);
DirtyBitmap dirty = delta.computeDirty(entities, rowCount);   // 1 bit per row

StructArenaDeltaPacket packet = new StructArenaDeltaPacket(entities, rowCount, dirty);
StructArenaDeltaCodec.encode(packet, sink);                    // zero-copy row slices
```

On the client, `StructArenaDeltaCodec.decode` patches exactly the dirty rows into the local arena —
byte-exact, with a typical saving of >99 % versus a full sync (verify with `aetherium delta`).

## Sync hierarchical data (NBT/JSON-like)

For tree-shaped state (faction data, machine configs) use `TreeCodec` instead of the flat codec:

```java
TreeNode tree = Tree.object()
        .put("name", "AetherFaction")
        .put("score", 9001L)
        .put("members", Tree.list(Tree.of("alice"), Tree.of("bob")))
        .build();

TreeSyncCodec.encode(new TreeSyncPacket(tree), sink);
```

The decoder is hardened (depth/element/byte limits), so hostile packets fail cleanly. Verify with
`aetherium tree`.

## Journal ticks for time-travel debugging (optional)

While developing, wrap your tick loop in the Time-Travel Debugger — every tick's delta is journaled
into a bounded ring buffer, and a crash freezes the scene for byte-exact rewinding:

```java
TtdEngine ttd = new TtdEngine(entities, 64);
ttd.tick((arena, tick) -> { /* your physics */ });
// after a fault: ttd.rewind(3).getDouble(entityIndex, xField)
```

See the [ACID engine explanation](../explanation/acid.md) and `aetherium ttd`.
