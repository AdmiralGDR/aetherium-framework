# Chaos Engineering Test Suite

*English. Russian mirror: [`../ru/testsuite.md`](../ru/testsuite.md).*

`aetherium-testsuite` deliberately attacks the framework to prove its safety guarantees
hold under catastrophic, concurrent mod failure. It depends on `core`, `bytecode`, and
`native`; no production module depends on it.

## What it does

It synthesizes hostile "mods" and feeds them to the **real** `BytecodeEngine` and the
**real** FFM layer, then asserts a single invariant: **the framework contains every
failure and the JVM never crashes** (zero escapes).

### Bytecode corruption (`ChaosMutators`)

| Kind | Injected fault |
|------|----------------|
| `VALID` | a healthy class (control sample) |
| `TRUNCATED` | class file cut in half |
| `BITFLIP` | random bytes flipped past the header |
| `HEADER_CORRUPT` | smashed `0xCAFEBABE` magic |
| `TYPE_CONFUSION` | returns an `int` where a reference is declared |
| `STACK_UNDERFLOW` | `POP` on an empty stack |

The engine must read-or-fail, transform-or-fail, verify-or-fail, and on **any** failure
revert to the input bytes plus a structured `Diagnostic` — never throw. The harness also
installs a transformer that randomly throws (~30%) to stress the transformer-exception
path even on valid input.

### Native / FFM chaos (`NativeChaos`)

Uses only **FFM-guarded** misuse — never a wild pointer (which would be a real,
uncatchable `SIGSEGV`):

- **use-after-free** — touch a segment after its `Arena` is closed → `IllegalStateException`.
- **out-of-bounds** — read far past a segment's end → `IndexOutOfBoundsException`.
- **alloc pressure** — allocate and abandon many segments to stress the allocator.

Each must be contained as a catchable Java exception.

## Concurrency

`ChaosHarness` runs every task on `Executors.newVirtualThreadPerTaskExecutor()` — one
virtual thread per simulated mod — to reproduce the load of **500+ heavy mods**
initializing simultaneously. The default is 600 mods + ~100 native tasks (≈700 virtual
threads).

## Running

```bash
aetherium-cli chaos          # default 600 mods
aetherium-cli chaos 1000     # scale up
./gradlew :aetherium-testsuite:run --args="600"   # standalone
```

## Pass criterion

`ChaosReport.passed()` requires `escaped == 0`, `nativeEscaped == 0`, and full accounting
(`transformedOk + reverted == modTasks`). Exit code `0` on pass. A representative run:
600 bytecode tasks (≈527 safely reverted, ≈73 transformed) + 100 native tasks (100
contained), **0 escapes, PASS**.
