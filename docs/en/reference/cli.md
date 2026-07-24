# CLI & Developer Tooling

*English. Russian mirror: [`../ru/cli.md`](../../ru/reference/cli.md).*

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
| `analyze <path>` | Statically verify a `.class` / `.jar` / directory against loader constraints **and hook `@Requires`/`@Ensures` contracts**. See [`acid.md`](../explanation/acid.md). |
| `selftest` | Bytecode-engine end-to-end simulation (read → transform → verify → load → invoke). |
| `inject` | Fluent injector self-test: cancellation, DAG ordering + Semantic Merger, sandbox revert. |
| `acid` | Prove transactional (ACID **A**tomicity) hooks: a mod's failing hook rolls back all its hooks. See [`acid.md`](../explanation/acid.md). |
| `ttd` | Time-Travel Debugger (ACID **D**urability): bounded delta journal + byte-exact rewind + fault capture. See [`acid.md`](../explanation/acid.md). |
| `contracts` | Static hook contract verification (ACID **C**onsistency): symbolic `@Ensures` return-sign checking. See [`acid.md`](../explanation/acid.md). |
| `domains` | FFM memory-domain isolation (ACID **I**solation): cross-mod access denied without a grant. See [`acid.md`](../explanation/acid.md). |
| `simd` | Report the SIMD lane width and verify the Vector API path equals scalar. |
| `cdscache [test]` | AppCDS zero-parse cache status, or the store→reopen→warm-hit round-trip test. |
| `profile` | Verify ephemeral JFR probes (zero overhead off, JFR fires on, hot-swap). |
| `security` | Verify the capability-based CIA-triad guards (default-deny, FFM bounds, reflection). |
| `spirv` | Compile a pure-Java `@AetheriumComputeShader` kernel to SPIR-V; prove the magic word `0x07230203`. |
| `hotswap` | Verify the live class hot-swap engine (`Instrumentation.redefineClasses`) + live DAG reconciliation. |
| `wasm` | Verify the polyglot WASM sandbox (deny filesystem/network) + the `StructArena` memory bridge. |
| `delta` | Verify delta-sync networking (dirty bitmap; transmit only changed rows). |
| `fuzz [n]` | Aggressively fuzz the SPIR-V + WASM attack surface (default 10000 cases/target); prove no input crashes the JVM/host. See [`fuzzer.md`](../explanation/fuzzer.md). |
| `lsp [--serve]` | Run the Language Server backend self-test, or serve LSP over stdio for an IDE (`--serve`). See [`lsp.md`](../explanation/lsp.md). |
| `ui` | Verify the declarative UI framework (flex layout + paint + click dispatch). See [`ui.md`](../explanation/ui.md). |
| `gfx` | Verify the advanced GFX pipeline (matrix/PoseStack/skeleton/vertex). |
| `tree` | Verify hierarchical `TreeCodec` sync (NBT/JSON-like round-trip + depth guard). |
| `behavior` | Verify content behaviors (`@AetheriumMachineLogic` BlockEntity ticking + behavior index). |
| `gameplay` | Verify the gameplay PAL (player/inventory access + cancellable interaction events). |
| `doctor` | Check this host's readiness (Java 21+, `--enable-preview`, Vector API, FFM native access, GraalWASM). |
| `entitysim [n]` | Data-oriented entity stress test (default 10000 entities). |
| `ffmaudit [n]` | FFM zero-leak audit: churn `n` entity lifecycles (default 10 000 000) through `StructArena` on virtual threads; prove exact release via the ArenaAuditor ledger + NMT + JFR. |
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
[`testsuite.md`](../explanation/testsuite.md).

### `selftest` / `preflight`

`selftest` exercises the bytecode engine end-to-end (see [`bytecode-engine.md`](../explanation/bytecode-engine.md));
`preflight` runs the framework's internal Pre-Flight Check and prints the resolved
capability tier (see [`native-bridge.md`](../explanation/native-bridge.md) ).

### `fuzz [n]`

Runs the aggressive fuzzing campaign (`n` cases per target, default 10000) over the
SPIR-V verifier/dispatch, the Java→SPIR-V compiler front-end, the `.wasm` loader, and the
`StructArena`↔WASM bridge. Every adversarial input must surface as a clean contractual
exception, never a JVM/host crash. The same campaign runs automatically during
`./gradlew check`. See [`fuzzer.md`](../explanation/fuzzer.md).

### `lsp [--serve]`

Without arguments, runs the Language Server backend self-test (vanilla-method autocomplete,
pre-compile hook-conflict prediction, JSON-RPC framing). With `--serve`, speaks
`Content-Length`-framed JSON-RPC over stdio so an IDE can connect. See [`lsp.md`](../explanation/lsp.md).
