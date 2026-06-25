# Network — Zero-GC Custom Payloads (`aetherium-network`)

*English. Russian mirror: [`../ru/network.md`](../ru/network.md). License: AGPL-3.0-or-later.*

A loader-agnostic custom-payload SPI. It contains **zero** `net.minecraft`/`net.neoforged` types; the
loader (`AetheriumNetworkBridge`) maps each channel to NeoForge's `PayloadRegistrar`.

## 1. API surface

| Type | Role |
|---|---|
| `NetworkPayload` | Marker: `String channelId()` (`"namespace:path"`). |
| `PayloadSink` / `PayloadSource` | Loader-agnostic write/read over the platform buffer. The key primitives are `writeSegment(MemorySegment, long)` / `readSegment(MemorySegment, long)` — **off-heap bulk copy, no `byte[]`, no boxing**. |
| `PayloadCodec<T>` | `encode(T, PayloadSink)` / `decode(PayloadSource)`; adapted to a platform `StreamCodec`. |
| `ClientPayloadHandler<T>` | `handle(T)` on the receiving side (run on the main thread by the loader). |
| `NetworkRegistry` | `register(codec, handler)` + `entries()` — pure data the loader bridges. |
| `StructArenaSyncPacket` | Ships `rowCount` contiguous rows of an off-heap `StructArena`. Holds a reference, not a copy. |
| `StructArenaSyncCodec` | `[int rowCount][rowCount × stride bytes]`; decodes straight into a pre-allocated client arena. |

## 2. Zero-GC path

The server computes into a `StructArena` (off-heap). `StructArenaSyncCodec.encode` writes the row count
then bulk-copies `rowCount × stride` bytes straight from the arena's `MemorySegment` into the network
buffer (`PayloadSink.writeSegment`). On the client, `decode` reads those bytes directly into a
**pre-allocated mirror** `StructArena` (`PayloadSource.readSegment`) — no per-packet allocation, no
intermediate heap array, no per-row objects. Server off-heap → wire → client off-heap, end to end.

## 3. Usage

```java
StructArena server = StructArena.allocate(layout, n);          // server-side, off-heap
StructArenaSyncPacket packet = new StructArenaSyncPacket(server, n);

StructArena clientMirror = StructArena.allocate(layout, n);    // allocated once at startup
NetworkRegistry.register(new StructArenaSyncCodec(clientMirror),
        received -> applyToWorld(received.arena(), received.rowCount()));
```

The loader bridge (`event.registrar("1").optional()` → `playToClient`) handles the platform wiring.

## — hierarchical sync (`TreeCodec`)

The flat `StructArenaDeltaCodec` is ideal for thousands of uniform off-heap entities, but gameplay state
(faction rosters, skill trees, quest graphs) is irregular and nested. `TreeNode` is a small tagged union
(object/list/string/long/double/bool/bytes) built fluently with `Tree`, and `TreeCodec`
serializes/deserializes it over the **same** `PayloadSink`/`PayloadSource` SPI as the flat path (with new
`writeBytes`/`readBytes` defaults). `TreeSyncPacket`/`TreeSyncCodec` ship it as a `NetworkPayload`.
Decoding is hardened — a depth limit defeats stack-overflow trees and per-element size limits defeat
hostile length fields. Proof: `aetherium tree`.
