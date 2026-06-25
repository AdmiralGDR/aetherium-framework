# Fuzzer — Hardening the SPIR-V + WASM Attack Surface

*English. Russian mirror: [`../ru/fuzzer.md`](../ru/fuzzer.md).*

`aetherium-fuzzer` is an aggressive, deterministic coverage fuzzer for the framework's two pieces of
genuinely unsafe surface — the **Java→SPIR-V compiler** ([`compute.md`](compute.md)) and the **polyglot
WASM sandbox/bridge** ([`wasm.md`](wasm.md)). Its job is to prove that *no* malformed binary, illegal
opcode, or out-of-bounds memory request can crash the JVM or the host OS: every adversarial input must
surface as a clean, contractual exception.

## How it works

Each [`FuzzTarget`](../../aetherium-fuzzer/src/main/java/org/aetherium/fuzzer/FuzzTarget.java) drives one
entry point with one randomized case and declares which exception type is its *documented* rejection;
the `FuzzEngine` classifies anything else as a crash. Because every case is caught — including `Error` —
a passing campaign is exactly equivalent to "the JVM survived every input". FFM bounds-checks turn
out-of-bounds accesses into `IndexOutOfBoundsException` rather than a native segfault, so a caught
throwable *is* the proof. Cases are seeded from `(campaignSeed, targetName, iteration)`, so any finding
is replayable from its reported seed.

The adversarial inputs bias toward the boundaries that actually break decoders (empties, 1–3 byte runts,
non-word-aligned lengths, valid-magic-then-garbage, and bit-flips of a known-good binary), not flat
random blobs that real parsers reject on the first byte.

## Targets

| Target | Drives | Clean rejection |
|--------|--------|-----------------|
| `spirv.wrap+verify+dispatch` | `SpirvModule.wrap` + every header accessor + `verify()` + dispatch | **none** — accessors are total |
| `compute.compileBytes(ASM)` | the ASM class-parsing front-end (incl. bit-flipped real kernels) | `UnsupportedShaderException` |
| `wasm.loadBytes(magic)` | the `.wasm` magic validator | `IllegalArgumentException` |
| `wasm.bridge.runPhysics(OOB)` | the `StructArena`↔WASM bridge with OOB sizes + misbehaving kernels | `IndexOutOfBoundsException` |

## Runs on every build

The campaign is an ordinary JUnit test
([`FuzzerCheckTest`](../../aetherium-fuzzer/src/test/java/org/aetherium/fuzzer/FuzzerCheckTest.java)), so
it executes automatically during `./gradlew check`. It asserts zero unexpected throwables **and** that
the campaign actually reached the contractual reject paths (a fuzzer that only feeds ignored inputs
proves nothing).

## Bugs this surfaced (now fixed)

Building the targets surfaced three real robustness defects in the pre-code, all now fixed —
the fuzzer is their permanent regression guard:

1. **SPIR-V header accessors threw on truncated input.** `magic()`/`version()`/`idBound()`/`headerHex()`
   read fixed word offsets with no bounds check, so an externally-supplied or truncated binary threw
   `IndexOutOfBoundsException`. They are now bounds-safe (out-of-range reads as `0`); `verify()` remains
   the sole authority on well-formedness. A public `SpirvModule.wrap(byte[])` now exists for
   externally-supplied `.spv`.
2. **The compiler leaked raw ASM exceptions.** `ClassReader` throws `ArrayIndexOutOfBoundsException` /
   `IllegalArgumentException` on a non-class blob; these escaped the front-end. The new
   `compileBytes(byte[])` normalizes every parse failure to `UnsupportedShaderException`, and a
   non-positive `localSizeX` annotation is rejected the same way instead of leaking the builder's IAE.
3. **The WASM bridge leaked off-heap memory per call.** `runPhysics` allocated a fresh linear-memory
   segment into a long-lived confined arena on every invocation, so a tight per-tick / fuzz loop grew
   native memory unbounded until the host was starved. It now reuses a grow-on-demand scratch buffer
   bounded to the high-water mark.

## Proof

```bash
aetherium fuzz            # default 10000 cases/target
aetherium fuzz 50000      # crank it up
```

A passing run reports the per-target case/handled/rejected/crashed counts, the total clean rejections
(proof the reject paths were reached), and **0 unexpected crashes**.
