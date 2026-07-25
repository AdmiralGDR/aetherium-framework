# In-game mod verification & analysis

`aetherium-verify` is the runtime companion to the Shield: it lets a player (or an admin) **check and
analyze the loaded mods right in the game** — which mods are present, who signed them, and whether their
bytecode still matches what shipped.

## What it answers

For every loaded `AetheriumMod`, `ModInspector.snapshot(ClassLoader)` produces a `ModReport`:

| Field | Meaning |
|---|---|
| `modId` | the mod's id |
| `author` | the author read from the Shield **watermark** (`""` if unsigned) |
| `verdict` | `SIGNED_INTACT` / `TAMPERED` / `UNSIGNED` |
| `classesChecked` | how many classes were attributed to the mod (entrypoint + declared content) |
| `tamperedClasses` | the classes whose bytes no longer match the integrity manifest |
| `contentCount` | declarative `@AetheriumBlock`/`@AetheriumItem` pieces the mod ships |
| `nativeGuard` | whether the Zig native guard is the live checksum backend |

The verdict comes from `ModVerifier` (in `aetherium-shield`): it merges every
`META-INF/aetherium/shield-integrity.txt` on the classpath and re-hashes each class's bytes (SHA-256). A
mismatch means the class was **patched after protection** — a cracked jar, an injected backdoor, or a
defeated check.

## How you use it in game

The loader registers a built-in command:

```
/aetherium mods       # list every mod with verdict, author, class + content counts (chat)
/aetherium verify     # integrity summary; names any TAMPERED mod (level 2)
/aetherium inspect    # (client) opens the scrollable inspector screen
```

`/aetherium inspect` opens `AetheriumModInspectorScreen` — a scrollable, font-accurate list rendered through
the real loader UI adapter (see [ui](ui.md)); tampered mods are shown in red. Because the screen is a pure
`AetheriumScreen`, the whole inspector lays out and is hit-tested **headless** in `ModVerifySelfTest`
(`aetherium verify`) before it ever reaches a player.

## Enforcement (strong by default)

The loader runs `ModVerifier` at init and, by default, **refuses to initialize a tampered mod**:

```
Aetherium REFUSES tampered mod 'x' — its bytes do not match the Shield integrity manifest.
```

Set `-Daetherium.shield.enforce=false` for a report-only launch (the inspector still shows the tamper red,
and the mod loads). Unsigned mods (no manifest) are unaffected. A debugger/agent is surfaced separately by
the native guard (see [shield](shield.md) → the native guard) — heuristic, so it is reported rather than
hard-enforced.

## Why this is sovereign

The verifier depends only on the framework itself: the Shield's integrity manifest, the pure UI module, and
the zero-dependency Zig native guard for a fast native checksum (with a pure-Java fallback). No external
library, no service — the framework verifies its own mods, in its own runtime, on screen. Run
`aetherium verify` for the offline proof and `aetherium guard` for the native-guard status.
