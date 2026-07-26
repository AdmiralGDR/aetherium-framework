# How to prove your mod launches on the framework

*English. Russian version: [`../../ru/how-to/verify-the-launch.md`](../../ru/how-to/verify-the-launch.md).*

*A task-oriented guide for mod authors and framework devs. It answers 's top ask — "make the game
launch, and let us verify it precisely and clearly." For the design behind the checks read
[artifact-roles](../explanation/artifact-roles.md) and [verify](../explanation/verify.md).*

There are two levels of proof. Start with the offline gate — it is instant and needs no game; escalate to the
real server only when you want the ground truth.

## 1. The offline gate (instant, no game, no account)

```
./gradlew check          # runs verifyJar + bootSmoke among the rest
# or just the artifact checks:
./gradlew verifyJar
```

`verifyJar` runs `ArtifactVerifier` over the *shipped* jars and asserts, with the framework's own ASM, that:

- **no package crosses the artifact boundary** (`AE-MODULE-CLASH`) — the module-graph defect that used to
  crash the game before any window. If two modules in the shipped set export the same package, this fails and
  names it; a clean run prints
  `✓ no-cross-artifact-package-clash — no package is exported by two modules across the shipped set`;
- the loader is self-contained, the boot path is preview-free, no platform library is bundled, and the
  `MOD` / `GAMELIBRARY` roles are correct.

This is what you run in CI. It catches the launch blocker in ~1 s without downloading Minecraft.

## 2. The definitive proof (a real headless server)

When you want to *see* the game start with your mod, boot a real NeoForge dedicated server. No display and no
Minecraft account are needed — a dedicated server is headless, and the launch blocker happens at module
resolution, before any window.

```
# framework + the bundled testmod:
./scripts/launch-check.sh

# framework + YOUR mod:
./scripts/launch-check.sh build/aetherium-test-server /path/to/your-mod.jar
```

The script builds the shipped jars, installs the NeoForge 1.21.1 server on first run (it downloads Minecraft +
NeoForge — this needs internet, once), stages `aetherium-transformer` + `aetherium-loader` + any extra mods
you pass into `mods/`, boots headless, auto-stops, and greps the log. A pass prints:

```
  ✓ no module ResolutionException
  ✓ Aetherium Framework is in the mod list
  ✓ server reached Done (started)
RESULT: LAUNCH OK ✓
```

Override the JVM with `AETHERIUM_JAVA=/path/to/java21` and the NeoForge version with `AETHERIUM_NEOFORGE=…`.
The full server log is left at `<server-dir>/launch-check.log` for inspection.

## 3. Proving a machine behaviour actually dispatches

If your mod declares `@AetheriumBlock(behavior = MyLogic.class)`, prove it ticks in-game: boot the server as
above, then in its console place the block and read its data back —

```
setblock 0 100 0 yourmod:your_machine
data get block 0 100 0
```

A ticking machine shows its persisted state growing, e.g. `{aeth_longs: {ticks: 598L}, aeth_age: 598L}` — the
`tick(ctx)` callback fired 598 times and the count survived in NBT. See
[game-integration → machine dispatch](../explanation/game-integration.md).
