# Content & DataGen — Declarative Registries, Zero JSON

> Modules: [`aetherium-content`](../../../aetherium-content) (annotations + processor),
> [`aetherium-datagen`](../../../aetherium-datagen) (pure asset generator),
> bridged by `aetherium-loader`.

Adding a basic block to Minecraft normally costs a `DeferredRegister`, a hand-written `BlockItem`,
and **four-plus JSON files** (block model, item model, blockstate, loot table) plus a `lang` entry.
Aetherium eliminates all of it. **You write one annotation. The framework does 100% of the registry
and JSON work.**

## The whole API

```java
@AetheriumBlock(name = "steel_block", hardness = 5.0f, resistance = 6.0f, requiresTool = true)
public final class AetheriumSteelBlock {}
```

```java
@AetheriumItem(name = "steel_ingot", maxStackSize = 64)
public final class SteelIngot {}
```

That is the entire source. The class body is empty and **no `net.minecraft` import appears** — the
mod stays loader-agnostic.

### `@AetheriumBlock`

| Element        | Default        | Meaning |
|----------------|----------------|---------|
| `name`         | *(required)*   | Registry path, e.g. `steel_block`. |
| `modId`        | `""`           | Namespace. Blank → the `aetherium.modId` build option (the Gradle plugin injects the mod id), else `aetherium`. |
| `hardness`     | `1.0`          | Mining/destroy time. |
| `resistance`   | `-1.0`         | Blast resistance. Negative → mirror `hardness`. |
| `requiresTool` | `false`        | Require the correct tool to drop. |
| `dropSelf`     | `true`         | Generate a self-drop loot table. |
| `displayName`  | `""`           | `lang` label. Blank → derived from `name` (`steel_block` → `Steel Block`). |

### `@AetheriumItem`

| Element        | Default      | Meaning |
|----------------|--------------|---------|
| `name`         | *(required)* | Registry path. |
| `modId`        | `""`         | Namespace (same resolution as above). |
| `maxStackSize` | `64`         | Stack size. |
| `displayName`  | `""`         | `lang` label. |

## What gets generated (the JSON you never write)

At **compile time**, the `AetheriumContentProcessor` (a standard `javax.annotation.processing`
processor) calls the pure DataGen engine and writes — for one `@AetheriumBlock(name="steel_block")`
in mod `aetherium` — straight into the compiled output (so they land in the jar with no extra Gradle
wiring):

```
assets/aetherium/models/block/steel_block.json     # { parent: block/cube_all, textures.all: …:block/steel_block }
assets/aetherium/models/item/steel_block.json      # { parent: …:block/steel_block }
assets/aetherium/blockstates/steel_block.json      # { variants: { "": { model: …:block/steel_block } } }
data/aetherium/loot_table/blocks/steel_block.json  # self-drop pool (survives_explosion)
assets/aetherium/lang/en_us.json                   # { "block.aetherium.steel_block": "Steel Block" }
```

> **1.21 path note.** Minecraft 1.21 renamed the data-pack folder `loot_tables` → singular
> `loot_table`. The generator targets the 1.21.1 baseline and emits the singular form.

`lang` entries for every declared piece are merged into one `en_us.json` per mod id.

## How it registers (no `DeferredRegister`, no `BlockItem` boilerplate)

The processor also writes a small machine-readable index, `META-INF/aetherium/content.index`
(one pipe-delimited record per declaration). At load time `aetherium-loader`'s
`AetheriumContentRegistrar` reads that index from the classpath and, on NeoForge's `RegisterEvent`:

1. **BLOCK phase** — builds each `Block` from `BlockBehaviour.Properties.of().strength(hardness,
   resistance)` (plus `requiresCorrectToolForDrops()` when `requiresTool`) and registers it.
2. **ITEM phase** — **auto-wraps every block in a `BlockItem`** and registers standalone `Item`s.

Failures are contained per entry, so one bad declaration can't abort registration. This is the only
place that knows both the Aetherium content model *and* Minecraft's registries.

## Strict purity

`aetherium-datagen` is a **pure Java file generator**: it has no `net.minecraft`/`net.neoforged`
dependency and no external JSON library, and it runs entirely at build time — **not** via NeoForge's
`GatherDataEvent`. The runtime index (`ContentIndex`) is the only hand-off into the game; it carries
plain primitives/strings, so the "no Minecraft in datagen" line is never crossed.

## Zero-config with the Gradle plugin

Applying the [Aetherium Gradle plugin](gradle-plugin.md) wires everything automatically — it adds the
content dependency, registers the annotation processor, and injects `-Aaetherium.modId=<your mod id>`
so `@AetheriumBlock(name = "…")` needs no `modId`. Run `aetheriumBundle` and the generated JSON is
bundled next to your compiled classes. No build wiring, no JSON, no registry code.

## — content behaviors (auto BlockEntity + ticking)

`@AetheriumBlock`/`@AetheriumItem` now accept a `behavior` class. When it implements
`AetheriumMachineLogic` (`tick(MachineContext)` + `onPlaced`/`onRemoved`), the annotation processor records
it in a `behaviors.index` (alongside the content index) and the loader auto-registers a ticking
`BlockEntity` — no `BlockEntityType`, ticker, or NBT boilerplate. `MachineContext`/`MachineState` are pure
(no Minecraft type), so a machine's logic is unit-testable offline. Proof: `aetherium behavior`.

## Block textures ()

Since the generated block model references your own texture (`"<modid>:block/<name>"`) instead of
substituting a vanilla one — a content framework must not guess a mod's art. The cost is that a block with no
`assets/<modid>/textures/block/<name>.png` renders as the black-and-magenta missing-texture checkerboard: a
green build that looks broken. So the annotation processor now **warns at compile time**, naming the exact
path it expects:

```
warning: Aetherium: block 'mymod:reactor' expects a texture at assets/mymod/textures/block/reactor.png
         — ship that 16x16 PNG or the block renders as the missing-texture checkerboard.
```

The check is reliable when you use the Aetherium Gradle plugin (it tells the processor where your resources
live, since the processor's own output never contains `src/main/resources`); ship the PNG and the warning
disappears. Item icons work the same way (`assets/<modid>/textures/item/<name>.png`).

## Lang files for every language ()

The creative-tab title comes from the generated `itemGroup.<modid>` key, which the processor emits into
`en_us.json`. If your mod ships other languages, the plugin's `mergeAetheriumLang` now **warns** when a
generated key is present in `en_us` but missing from another shipped language — otherwise a non-English player
silently sees the English title. Add the key to each `assets/<modid>/lang/<lang>.json` you ship and the
warning clears.

## Machine API additions ()

- **`MachineState.hasLong`/`hasString` + `removeLong`/`removeString`.** "Absent" is now distinct from "zero" —
  a machine can tell "unclaimed" from "owned by faction 0". `getLong(key, fallback)` returns the fallback only
  when the key is absent; `removeLong` makes it absent again (and the removal persists through NBT).
- **`MachineContext.level()` → `Optional<LevelContext>`.** A machine can read and mutate the world around it —
  neighbouring blocks, chunk loadedness, block entities — without re-deriving its position from
  `x()`/`y()`/`z()`. The loader fills it from the block entity's level; it is empty in a pure unit test.
- **`Keys` constants for keybinds.** `AetheriumUi.registerKeybind(key, category, Keys.G, action)` replaces a
  magic `71` — `org.aetherium.ui.Keys` is a zero-dependency holder of the GLFW key codes (letters, digits,
  F-keys, arrows, editing keys) Minecraft uses, so a typo is a compile error, not a silent mis-binding.

### additions

- **`MachineState.clear()`** resets a machine to factory state (both maps). **`longKeys()`/`stringKeys()`**
  return a `Set<String>` **snapshot** you can iterate while calling `removeLong`/`removeString`. And `longs()`/
  `strings()` are documented as returning **immutable copies** — safe to keep and iterate, never a live view, so
  you need not copy defensively.
