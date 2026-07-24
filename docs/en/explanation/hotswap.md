# Hot-Swap — Live Class Redefinition

*English. Russian mirror: [`../ru/hotswap.md`](../../ru/explanation/hotswap.md).*

`aetherium-hotswap` pushes recompiled `.class` files into the **running** game with no restart — the
zero-downtime developer-iteration loop.

## How it works

1. **`ClassFileWatcher`** registers a recursive `WatchService` over the modder's build output (e.g.
   `build/classes/java/main`). When `javac`/Gradle rewrites a `.class`, the watcher reads the new bytes.
2. **`HotSwapEngine`** derives the binary class name straight from the bytes (ASM `ClassReader`), finds
   the matching already-loaded `Class`, and calls `Instrumentation.redefineClasses()` to swap the method
   bodies in place.
3. **`HotSwapListener`s** are notified after each successful swap; the injector subscribes one that
   re-resolves its `LiveHookGraph`, so injected hooks stay correctly ordered live.

## Instrumentation acquisition

A retransform/redefine-capable `Instrumentation` is obtained through the injector's shared
`InstrumentationSupport` — the **same** Attach-API self-attach the ephemeral JFR probes use (see
[`probes.md`](probes.md)). On a locked-down JVM (no `-Djdk.attach.allowAttachSelf=true`) the engine
degrades to `HotSwapResult.Status.NO_INSTRUMENTATION` and the edit applies on the next launch instead of
instantly — it never fails hard. Standard JVM redefinition rules apply: **method-body changes only** (no
added/removed members); a rejected redefinition is reported, never fatal.

## Live DAG reconciliation

`LiveHookGraph` wraps the deterministic `HookDag` topological sort ([`injector.md`](injector.md)) behind
a *mutable* registry. A redefined class can `register`/`remove` hooks, and `resolve()` re-runs the sort
over the current set — so the running game always executes hooks in the freshly reconciled, reproducible
order.

## Proof

```bash
aetherium hotswap
```

The self-test generates two versions of a class (`currentValue()` returning `1`, then `2`), loads v1,
redefines it live to v2, and observes `currentValue() == 2` **with no restart** — then shows the hook
order reconcile from `[core, render, physics]` to `[core, render, physics, lighting]`.

## — structural hot-swap (DCEVM / HotswapAgent)

Stock HotSpot's `redefineClasses` accepts method-body changes only. `DcevmSupport` detects an enhanced
runtime — DCEVM (by VM name/version) or HotswapAgent (on the classpath), or an explicit
`-Daetherium.hotswap.structural=true` override — and, when present with instrumentation,
`HotSwapEngine.structuralRedefineSupported()` reports that **structural** hot-swap (adding/removing fields
and methods of a live class) is available; the same `redefine` call then accepts schema-changing bytecode.
When absent, such edits are still rejected gracefully (a restart is needed). Reported by `aetherium hotswap`.
