# WASM — Polyglot Sandbox

*English. Russian mirror: [`../ru/wasm.md`](../../ru/explanation/wasm.md).*

`aetherium-wasm` runs `.wasm` mods (compiled from **Rust / C / Go**) inside a strictly sandboxed
GraalWASM context — **memory and compute only, never the filesystem or network**.

## Security contract

`WasmSecurityPolicy.strict()` is the one and only supported policy: `filesystem` and `network` are always
denied, `memory` and `compute` are allowed, and there is **no API to turn host I/O on**. The
`WasmSandbox` builds its GraalVM `Context` with `IOAccess.NONE` and `HostAccess.NONE` and with thread /
native creation disabled. A tampered permissive policy is rejected by `assertStrict()` with a
`SecurityException`. Untrusted native code can crunch physics, but can never touch the disk, open a
socket, or reach into the JVM.

## Optional by design

GraalWASM is reached **entirely by reflection** (`org.graalvm.polyglot.*`), so the framework never hard-
depends on the GraalVM polyglot/wasm jars and the offline build stays green. Where the runtime and the
`wasm` language are installed, the sandbox runs real WebAssembly; where they are absent, `available()` is
`false` and the module degrades to **policy-only mode** (the policy and the bridge still work). The CLI
`doctor` reports availability.

## Loading

`WasmModuleLoader` reads a `.wasm` file, validates the WebAssembly magic `\0asm`, and wraps it in a
`WasmModule`. A file without the magic is rejected before it ever reaches the engine.
`WasmModule.instantiate(sandbox)` evaluates it inside the strict context.

## StructArena bridge

`StructArenaWasmBridge` bridges WASM linear memory to the off-heap FFM `StructArena`
([`performance.md`](performance.md)): it copies the entity bytes into a confined linear-memory segment,
runs the sandboxed `WasmCompute` kernel over **only that segment** (no host handle is ever passed in),
then copies the result back. The kernel seam is identical whether it is a real exported `.wasm` function
or a Java reference kernel, so the data path is exercised either way.

## Proof

```bash
aetherium wasm
```

The self-test confirms FS/network are denied, a permissive policy is rejected, the loader accepts a valid
module and rejects a non-wasm file, and the bridge round-trips entity bytes through linear memory while a
sandboxed `x += vx` physics kernel computes the correct result — all off-heap.
