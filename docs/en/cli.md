# CLI & Developer Tooling

*English. Russian mirror: [`../ru/cli.md`](../ru/cli.md).*

The `aetherium` CLI (`aetherium-cli`) is the primary developer experience: it reduces
mod-project setup to zero boilerplate and provides static analysis and validation tools.

## Running

After a build, an installable distribution is produced:

```bash
./gradlew :aetherium-cli:installDist
aetherium-cli/build/install/aetherium-cli/bin/aetherium-cli --help
# or, during development:
./gradlew :aetherium-cli:run --args="<command> [args]"
```

The CLI runs with `--enable-preview` and `--enable-native-access=ALL-UNNAMED` (FFM).

## Commands

| Command | Purpose |
|---------|---------|
| `init <name>` | Scaffold a new Aetherium-compatible mod project (zero boilerplate). |
| `analyze <path>` | Statically verify a `.class` / `.jar` / directory against loader constraints. |
| `selftest` | Bytecode-engine end-to-end simulation (read → transform → verify → load → invoke). |
| `inject` | Fluent injector self-test: cancellation, DAG ordering + Semantic Merger, sandbox revert. |
| `simd` | Report the SIMD lane width and verify the Vector API path equals scalar. |
| `cdscache [test]` | AppCDS zero-parse cache status, or the store→reopen→warm-hit round-trip test. |
| `profile` | Verify ephemeral JFR probes (zero overhead off, JFR fires on, hot-swap). |
| `security` | Verify the capability-based CIA-triad guards (default-deny, FFM bounds, reflection). |
| `doctor` | Check this host's readiness (Java 21+, `--enable-preview`, Vector API, FFM native access). |
| `entitysim [n]` | Data-oriented entity stress test (default 10000 entities). |
| `preflight` | Framework Pre-Flight Check (ASM + native + capability tier). |
| `chaos [n]` | Chaos Engineering stress test (default 600 simulated mods). |
| `--help`, `-h`, `help` | Show the help menu. |

### `init <name>`

Generates a ready-to-build Gradle project under `./<modId>/` (the name is sanitized into
a valid mod id, e.g. `"My Cool Mod"` → `my-cool-mod`). The project contains:

- `build.gradle.kts` / `settings.gradle.kts` / `gradle.properties` — Java 21 toolchain,
  `--enable-preview`, dependencies on the Aetherium API modules.
- `src/main/java/.../<Name>Mod.java` — an example mod that already uses the
  `ComputePipeline` API. **The developer writes no JNI/FFM/ASM glue.**
- `src/main/resources/META-INF/neoforge.mods.toml` — drop-in NeoForge metadata,
  `license = "AGPL-3.0-or-later"`.
- `LICENSE` + per-file AGPL headers — generated projects inherit the framework's copyleft.

```bash
aetherium init my-mod
cd my-mod && ./gradlew build
```

### `analyze <path>`

Reads a `.class`, a `.jar` (every class entry), or a directory of classes and reports,
per class: name, class-file major version, whether it exceeds the target (Java 21 =
major 65), and whether ASM's verifier accepts it. Read-only — nothing is executed or
defined. Exit code `0` when clean, `1` when problems are found.

```bash
aetherium analyze build/libs/my-mod.jar
```

### `chaos [n]`

Runs the Chaos Engineering suite: `n` (default 600) hostile "mods" are loaded
simultaneously on virtual threads, alongside FFM misuse tasks. It asserts the framework
contains every failure (zero escapes) and the JVM never crashes. See
[`testsuite.md`](testsuite.md).

### `selftest` / `preflight`

`selftest` exercises the bytecode engine end-to-end (see [`bytecode-engine.md`](bytecode-engine.md));
`preflight` runs the framework's internal Pre-Flight Check and prints the resolved
capability tier (see [`native-bridge.md`](native-bridge.md) ).
