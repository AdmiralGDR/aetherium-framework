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

## Proving the game launches ()

's top priority was that **framework devs and mod authors can precisely verify the game actually
launches.** Two checks now cover that — one instant and offline, one definitive.

### Offline gate — `./gradlew verifyJar` / `check` (~1 s, no game)

`ArtifactVerifier` gained a **cross-artifact module-clash** check (`AE-MODULE-CLASH`) that reproduces the
boot crash without a game. It enumerates every module the shipped set produces — each jar's loose
classes as one module, **plus one module per `META-INF/jarjar/*.jar`** (keyed by file name, so JiJ's own dedup
is not miscounted) — and fails if any package appears in two modules. The old fat transformer + loader **fail**
it naming `org/aetherium/core …`; the relocated build **passes**. `BootHarness` (`bootSmoke`) runs the same
assertion on the shipped set, and `ArtifactVerifierTest` pins both directions. All three are wired into
`check`, so the module-graph defect fails CI instead of a player's launch.

### Definitive proof — `./scripts/launch-check.sh` (a real headless server)

For the ground truth, `scripts/launch-check.sh` boots a **real** NeoForge 1.21.1 dedicated server (headless —
the crash is at module resolution, before any window, so no display and no Minecraft account are needed)
with the framework staged, and asserts: no `ResolutionException`, the framework appears in the FML mod list,
and the server reaches `Done`. Pass extra mod jars to stage them too. It is the same flow an author uses to
prove their own mod launches on the framework — see
[how-to: prove the launch](../how-to/verify-the-launch.md).

## Proving the client renders ()

A dedicated server proves the game *loads*, but not that it *renders* — and was a client-only GUI
bug (every screen blurred). Two checks now cover the client:

- **Offline `AE-UI-BLUR`** (in `verifyJar`/`bootSmoke`, so in `check`): ASM over the shipped loader jar asserts
  `AetheriumScreenAdapter.render` never calls `Screen.render` (whose `renderBackground` would re-blur the
  finished GUI). Reliable, no game — it catches the regression before a player sees it.
- **Definitive: `scripts/launch-check-client.sh`** boots a **real NeoForge 1.21.1 client** headless under Xvfb
  with software GL (llvmpipe), and asserts no `ResolutionException`, the framework constructs, and the client
  reaches title-screen / GL-init markers (`Backend library: LWJGL`, `Reloading ResourceManager`, …) — i.e. the
  GUI can render. **No Minecraft account is needed** (an offline client reaches the title screen and loads mods
  without login). Together with the server `launch-check.sh` this proves the game launches for the framework
  *and* mods, on both sides. If the environment cannot bring up software GL, the script says so and the offline
  guard + server check remain the CI truth.
