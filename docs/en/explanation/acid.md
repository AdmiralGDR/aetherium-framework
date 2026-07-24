# The ACID Engine — database-grade reliability for mod interactions

*English. Русская версия: [`../ru/acid.md`](../../ru/explanation/acid.md).*

applies the database world's **ACID** guarantees — Atomicity, Consistency, Isolation,
Durability — to bytecode injection and off-heap memory, to eradicate the three classic modding failure
modes: **Heisenbugs**, **silent state corruption**, and **partially-loaded mods**. Each pillar is a
pure, offline-testable module with its own `aetherium` CLI self-test.

| Pillar | Guarantee | Module | CLI |
|--------|-----------|--------|-----|
| **A**tomicity | A mod's hooks apply all-or-nothing | `aetherium-injector` (`txn`) | `aetherium acid` |
| **C**onsistency | Hook return values are contract-checked before running | `aetherium-cli` (`contract`) | `aetherium contracts`, `analyze` |
| **I**solation | A mod cannot touch another mod's FFM memory | `aetherium-security` | `aetherium domains` |
| **D**urability | Every tick is journaled; rewind on a crash | `aetherium-hotswap` (`ttd`) | `aetherium ttd` |

## Atomicity — Transactional Hooks

The base injector already reverts a **single** class to vanilla when its injection fails the
verification sandbox. That is not enough: if a mod injects into classes A, B and C and only the edit to
C fails, the game still runs with A and B rewritten — a **partially-applied mod**, the exact source of
"works-on-my-machine" Heisenbugs.

`TransactionalInjector` treats a mod's whole set of hooks as one ACID transaction:

```java
TransactionalInjector engine = TransactionalInjector.create(verifyLoader)
    .mod("gravity_plus", gravityInjector, List.of(
        new TargetClass("net.minecraft...Entity",       entityBytes),
        new TargetClass("net.minecraft...LivingEntity", livingBytes),
        new TargetClass("net.minecraft...Player",        playerBytes)))  // this one fails verification
    .mod("speed_mod",    speedInjector,   new TargetClass("net.minecraft...Mob", mobBytes));

EngineReport report = engine.apply();
```

- Every targeted class verifies → **COMMIT**: all transformed bytes are published and the mod's hook
  table is installed.
- **Any** class fails → **ROLLBACK**: *every* already-verified edit of that mod is discarded, nothing is
  installed, and the mod is disabled. The game keeps the vanilla bytes for every class the mod touched.

Rollback is **graceful** (the rule): a failing mod is contained and disabled while every other mod's
transaction proceeds independently. The JVM is never crashed — a broken mod simply never loads.
`EngineReport.published(binaryName)` exposes the effective post-transaction class table, which by
construction never contains a partially-applied mod.

`aetherium acid` proves it end-to-end: a mod with three hooks whose **third** fails rolls back hooks 1
and 2 (verified: nothing published, the classes run vanilla), while a healthy neighbour mod still
commits and runs.

## Consistency — Contract Verification

Annotate hook logic with `@Requires` (a precondition on an argument) and `@Ensures` (a postcondition on
the return value), using the small, analyzable `Constraint` vocabulary
(`NON_NEGATIVE`, `POSITIVE`, `NON_POSITIVE`, `NEGATIVE`):

```java
@Ensures(Constraint.NON_NEGATIVE)             // a light level must never be negative
public static int lightLevel(HookContext ctx) { ... }
```

The CLI's `ContractAnalyzer` reads these annotations straight from the compiled bytecode and runs a
**basic symbolic (sign) interpreter** over each method: constant pushes carry a precise sign, `neg` and
integer `+ - *` combine signs, and anything it cannot follow (a loaded variable, a call result, control
flow) becomes `UNKNOWN`. The verdict per hook is:

- **SATISFIED** — every return is proven to satisfy the constraint.
- **VIOLATED** — a return is proven to violate it (a warning surfaced *before* the game runs, e.g. a
  `NON_NEGATIVE` hook that can `return -1`).
- **UNVERIFIED** — the sign could not be proven (reported, never a false alarm).

`aetherium contracts` runs the self-test; `aetherium analyze <jar>` now additionally scans every class
for hook contracts and reports the verdicts. Nothing is executed — it is pure static analysis.

## Isolation — FFM Memory Domains

`GuardedSegment` stops a mod from stepping *outside* a segment it was handed; it does nothing about a mod
reaching into a segment owned by a *different* mod. `MemoryDomainRegistry` closes that gap:

```java
MemoryDomainRegistry registry = MemoryDomainRegistry.create(SecurityPolicy.global());
MemoryDomainHandle domain = registry.allocate("mod_a", 64);        // needs NATIVE_MEMORY
registry.open("mod_a", domain.domainId()).setInt(0, 0xA5A5);       // owner: OK

registry.open("mod_b", domain.domainId());                          // SecurityViolationException!
registry.grantAccess("mod_a", domain.domainId(), "mod_b");          // owner shares explicitly
registry.open("mod_b", domain.domainId()).getInt(0);                // now OK
```

Each domain carries a random `UUID` capability (`MemoryDomainHandle`) — a 122-bit value that cannot be
guessed or forged. A mod may open a domain **only** if it is the owner or has been **explicitly granted**
access by the owner; every other request is a contained `SecurityViolationException`, never silent
corruption of a neighbour's state. Only the owner may grant or revoke, and allocation still requires the
base `Capability.NATIVE_MEMORY` grant. `aetherium domains` proves the full lifecycle.

## Durability — The Time-Travel Debugger

`StructArenaJournal` records a **strictly bounded** ring buffer of per-tick memory *deltas* over a
`StructArena`. On each commit it diffs the live arena against a single shadow mirror, coalesces the
changed bytes into runs, and stores only their before/after images as one frame. Frames live in a
fixed-capacity ring, so the footprint is capped at `shadow + capacity × 2 × byteSize` regardless of how
many ticks run (the rule) — a typical tick that nudges a few entities costs a few dozen bytes, not a full
snapshot.

`TtdEngine` drives the simulation through journaled ticks and freezes the crash scene on a fault:

```java
TtdEngine engine = new TtdEngine(arena, /* ring capacity */ 64);

engine.tick((a, tick) -> { /* advance physics: x += vx … */ });   // COMMITTED (delta journaled)
engine.tick((a, tick) -> { throw new IllegalStateException(); });  // FAULTED (not committed, contained)

if (engine.hasFault()) {
    ArenaSnapshot crash  = engine.fault().faultState();  // the arena at the moment of the throw
    ArenaSnapshot before = engine.rewind(0);             // last known-good committed state
    ArenaSnapshot older  = engine.rewind(5);             // five ticks earlier — byte-exact
    double x = older.getDouble(entityIndex, xField);     // inspect any past value
}
```

A faulting tick is **not** committed, so the journaled history stays intact — the developer steps
backward through the last known-good tick states to see exactly which entity's value went wrong before
the exception. `aetherium ttd` runs 2 000 ticks, proves the footprint stayed under a tiny constant
ceiling, reconstructs past states byte-exactly (with clamping past the retained window), and captures a
contained tick fault while keeping the committed history intact.

## Guarantees

- **No Minecraft imports** in the pure modules; the transactional injector, memory domains, contract
  vocabulary, and the journal are all loader-agnostic and offline-testable.
- **The JVM is never crashed.** Every failure path (a bad hook, a cross-domain access, a contract
  violation, a faulting tick) becomes a contained, structured error — Availability by construction.
- Bounded memory: the TTD ring buffer's ceiling is independent of tick count
  (`StructArenaJournal.maxRetainedBytes()`).
