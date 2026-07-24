# Getting started: your first Aetherium mod

*English. Русская версия: [`../../ru/tutorials/getting-started.md`](../../ru/tutorials/getting-started.md).*

*A learning-oriented tutorial. In about ten minutes you will scaffold a mod project, add a custom
block with zero boilerplate, and verify the framework end-to-end — no prior Aetherium knowledge
assumed.*

## What you need

- Linux x86-64, **Java 21 (GraalVM)** on `PATH`.
- A clone of this repository, built once: `./gradlew build`.

## Step 1 — Build the CLI

```bash
./gradlew :aetherium-cli:installDist
alias aetherium=$PWD/aetherium-cli/build/install/aetherium-cli/bin/aetherium-cli
```

Check your machine can run everything:

```bash
aetherium doctor
```

You should see `DIAGNOSIS: READY`. If a line says `WARN`, the hint next to it tells you which JVM
flag to add — nothing blocks the tutorial.

## Step 2 — Scaffold a project

```bash
aetherium init my-first-mod
cd my-first-mod
```

`init` generated a complete, buildable Gradle project: toolchain pinned to Java 21, `--enable-preview`
wired, the Aetherium APIs on the classpath, an example mod class, NeoForge metadata, and an AGPL-3.0
license. You wrote none of it.

## Step 3 — Add a block (one annotation, no JSON)

Create `src/main/java/.../SteelBlock.java`:

```java
@AetheriumBlock(name = "steel_block", hardness = 5.0f, requiresTool = true)
public final class SteelBlock {
}
```

That single annotation is the whole feature. At compile time the annotation processor generates the
blockstate, the block and item models, the loot table, and the language entry, and records the block
in the content index; at load time the framework registers the block and its item on whatever loader
is running. There is no `Registry` call, no JSON file, no event subscriber.

## Step 4 — Build and inspect

```bash
./gradlew build
unzip -l build/libs/my-first-mod.jar | grep -E 'steel|index'
```

You will see the generated assets (`assets/…/steel_block.json`, loot table, lang) and
`META-INF/aetherium/content.index` bundled into the jar automatically.

## Step 5 — Verify the engine itself

Back in the framework repository, run the self-proving commands:

```bash
aetherium selftest   # bytecode engine: transform → verify → load → invoke
aetherium inject     # fluent injector + sandbox revert
aetherium acid       # transactional hooks: a failing mod rolls back completely
```

Every command prints `RESULT: PASS ✓` — the same checks run in `./gradlew check`.

## Where to go next

- **Do a task** → the [how-to guides](../how-to/inject-a-hook.md): inject a hook, sync off-heap data.
- **Look something up** → the [reference](../reference/cli.md): every CLI command, annotation and
  build knob.
- **Understand the design** → the [explanations](../explanation/bytecode-engine.md): O(1) dispatch,
  the ACID engine, SPIR-V compilation.
