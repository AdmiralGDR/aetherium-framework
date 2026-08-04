# Network — Zero-GC Custom Payloads (`aetherium-network`)

*English. Russian mirror: [`../ru/network.md`](../../ru/explanation/network.md). License: AGPL-3.0-or-later.*

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

## — the directional matrix (serverbound) + a side model

Through the network was **receive-only and clientbound-only**: `NetworkRegistry.register` wired only
`playToClient`, and there was no send API. A mod's in-game settings screen therefore worked in single-player
(same process writes the config the server reads) but **did nothing on a dedicated server** — the operator
edited their own client's config. asked for the missing direction; this ships the whole matrix.

Because the serverbound handler needs the sender's `PlayerHandle` (an `aetherium-edge` type), and
`aetherium-network` sits *below* edge, the directional facade lives in **`aetherium-edge`** (`Network`), while
`aetherium-network` stays pure. Directions:

| Call | Direction | Where |
|---|---|---|
| `NetworkRegistry.register(codec, handler)` | server → client (receive) | client |
| `Network.registerServerbound(codec, handler)` | client → server (receive) | server |
| `Network.sendToServer(payload)` | client → server (send) | client |
| `Network.sendToClient(target, payload)` / `sendToAllClients(payload)` | server → client(s) (send) | server |
| `Network.relayToClient(target, payload)` | client ↔ client (server-side relay) | server |

```java
// server side: accept an admin edit, gated on the sender's permission — the sender is from the connection,
// never the payload, so it cannot be spoofed.
Network.registerServerbound(new SetRuleCodec(), (PlayerHandle sender, SetRule p) -> {
    if (sender.hasPermission(2)) rules.apply(p);
});

// client side: the settings screen pushes the edit to the server it is connected to.
Network.sendToServer(new SetRule("maxMembers", 12));
```

Sends route through a `PayloadTransport` the loader installs (NeoForge's `PacketDistributor`); off-platform it
is a no-op, so `Network.send*` never throws in a test or tool. The loader's `AetheriumNetworkBridge` now wires
**both** `playToClient` and `playToServer`, mapping a target `PlayerHandle` back to a `ServerPlayer` by UUID.

### Safe by default (protection)

A serverbound channel is a new attack surface — a malicious client can push admin packets. The framework makes
the channel safe **without the author writing the checks**:

- **Sender identity** comes from the connection (`IPayloadContext.player()`), never the payload, so it can't be
  forged; gate with `sender.hasPermission(level)`.
- **Size cap** — an oversized payload is rejected *before decode* (`Network.withinSizeLimit` against the
  readable bytes), so a hostile length field never allocates. Default 32 KiB, per-channel overridable.
- **Rate limit** — a per-sender, per-channel token bucket (`ServerboundGuard`) drops a flood before the mod
  handler runs (`Network.deliver` returns `false`).

The Shield covers the codec classes too: a `PayloadCodec.channelId()` literal is just a method-returned string,
so string encryption hides it — `aetherium harden-check` on a shielded jar reports **zero** readable channel
names. Proof of the whole matrix offline: `aetherium network`.

### A side model — both-side, server-side, client-side

`@AetheriumInit(side = …)` (with `org.aetherium.core.mod.Side`: `BOTH` default, `SERVER`, `CLIENT`) lets an
author declare where an init runs, and the generated entrypoint gates the call by `Side.activeOn`: a `CLIENT`
init **never runs on a dedicated server** (so a client-side mod can't crash one), while `SERVER`/`BOTH` run
wherever they are safe (server logic is fine on a client's integrated server). The loader supplies the JVM's
physical side through `AetheriumContext.side()`. This is how a mod is written both-side, server-side, or
client-side with no dist boilerplate; client↔client is a client → serverbound → `relayToClient` hop.

## — hierarchical sync (`TreeCodec`)

The flat `StructArenaDeltaCodec` is ideal for thousands of uniform off-heap entities, but gameplay state
(faction rosters, skill trees, quest graphs) is irregular and nested. `TreeNode` is a small tagged union
(object/list/string/long/double/bool/bytes) built fluently with `Tree`, and `TreeCodec`
serializes/deserializes it over the **same** `PayloadSink`/`PayloadSource` SPI as the flat path (with new
`writeBytes`/`readBytes` defaults). `TreeSyncPacket`/`TreeSyncCodec` ship it as a `NetworkPayload`.
Decoding is hardened — a depth limit defeats stack-overflow trees and per-element size limits defeat
hostile length fields. Proof: `aetherium tree`.
