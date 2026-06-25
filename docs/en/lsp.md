# LSP Backend — IDE Autocomplete & Pre-Compile Conflict Prediction

*English. Russian mirror: [`../ru/lsp.md`](../ru/lsp.md).*

The Aetherium CLI doubles as a **Language Server Protocol** backend (`org.aetherium.cli.lsp`). It gives
an IDE two things modders otherwise discover only after a failed build: real, validated **injection-point
autocomplete** for vanilla Minecraft methods, and **hook-conflict prediction** run with the very same DAG
engine the loader uses — *before* compilation.

## Transport

`AetheriumLspServer` speaks the LSP base protocol — `Content-Length`-framed JSON-RPC 2.0 over stdio —
backed by a tiny dependency-free JSON reader/writer (`Json`), keeping the framework offline-first. A
malformed frame yields a JSON-RPC parse-error response rather than tearing the server down.

```bash
aetherium lsp --serve     # real Language Server over stdio (point your IDE at it)
aetherium lsp             # run the backend self-test instead
```

## Autocomplete: valid injection points

`VanillaMethodIndex` is a curated, loader-agnostic catalogue of frequently-injected vanilla methods —
JVM internal names + descriptors + the anchors valid for each (`HEAD`, `RETURN`), never a Minecraft
import. The backend answers `textDocument/completion` (fuzzy prefix match) and the Aetherium extension
`aetherium/injectionPoints` (all targets on a class) with LSP completion items, so the IDE can offer
`net.minecraft.world.entity.Entity::tick` and tell the modder which anchors make sense there. A real
deployment augments this from the mapped Minecraft jar; the curated seed makes the feature useful offline.

## Conflict prediction (the headline)

`aetherium/predictConflicts` takes the hooks an IDE parsed from the mod's source/DSL and runs
`ConflictPredictor` over them. Crucially, ordering is checked with the **real** `LiveHookGraph` →
`HookDag` ([`injector.md`](injector.md)), so a predicted "OK" matches runtime weaving. It reports:

- **`ordering-cycle`** (error) — a genuine `runBefore`/`runAfter` cycle within an attachment group.
- **`duplicate-id`** (error) — two hooks declared with the same id.
- **`invalid-anchor`** (warning) — an anchor that is not valid for a known vanilla target.
- **`competing-cancel`** (warning) — multiple cancelling hooks sharing one anchor; the Semantic Merger
  composes them (all run; the last cancel sets the return), but the author should review the order.

Each finding is returned as an LSP-shaped diagnostic (`severity`, `code`, `message`, `relatedHooks`).

## Proof

```bash
aetherium lsp
```

The self-test confirms completion surfaces real vanilla targets, the predictor catches an ordering cycle
and competing cancellations while passing a clean set, and a full framed JSON-RPC `initialize`
round-trips through the transport.
