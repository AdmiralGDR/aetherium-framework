# Native Bridge (JNI / C++ with FFM fallback ordering)

*Module: `aetherium-native` · English. Russian mirror: [`../ru/native-bridge.md`](../ru/native-bridge.md).*

## 1. Purpose

Some operations Aetherium needs are not expressible in pure Java at acceptable cost:
direct memory-mapped buffer manipulation, OS-level thread affinity, fast hashing of
class bytes, and low-latency probing of loader-native data structures. The native
bridge provides these through a **brokered, allow-listed** interface — mods never call
native code directly.

## 2. Pathway selection & fallback ordering

The bridge is *not* hardcoded to one mechanism. At load time it probes capabilities
and selects the highest viable tier, recording the choice in the launch report:

```
 FFM (java.lang.foreign, preview)   ← preferred on GraalVM 21
        │  not viable?
        ▼
 JNI (classic native methods + .so) ← portable fallback
        │  not viable?
        ▼
 pure-Java shim                     ← correctness over speed
        │  not viable?
        ▼
 feature disabled (graceful)        ← never crash the launch
```

**FFM** (`java.lang.foreign`, preview in 21) is preferred: no hand-written JNI glue,
`MethodHandle`-based downcalls that the JIT inlines, and `Arena`-scoped memory with
deterministic release. **JNI** remains as a fully supported fallback where a required
symbol or platform quirk isn't reachable via FFM. The pure-Java shim guarantees
*correctness* even when neither native tier is available.

## 3. C++ side (`src/main/cpp`)

- Compiled to a single `libaetherium_native.so` (Linux x86-64 baseline).
- Exposes a **narrow, versioned C ABI** — a flat function table, not a C++ class
  surface — so both FFM downcalls and JNI bindings target the same symbols.
- All entry points validate arguments and return structured error codes; no exception
  crosses the boundary. A non-zero code triggers the Java-side fallback ladder.

## 4. The `O(1)` and memory contracts

- Every bridged operation is a constant-time call: a linked FFM `MethodHandle` or a
  bound JNI method id resolved **once** at load time, then reused.
- Native memory is owned by a confined `Arena` per logical scope; release is
  deterministic and prompt — no reliance on GC finalization, no leaks across ticks.
- No per-call allocation on the hot path; buffers are pre-sized at load time.

## 5. Security (CIA triad)

- **Confidentiality** — the bridge exposes only an explicit allow-list of native
  capabilities. There is no generic "call arbitrary symbol" entry point. Host paths
  and addresses are redacted from any diagnostic surfaced to mods.
- **Integrity** — argument validation on both sides of the boundary; the C ABI is
  versioned and the Java side refuses a mismatched library rather than calling into
  it. Native buffers are bounds-checked before use.
- **Availability** — a native fault is contained: structured error codes (never raw
  signals where avoidable), the fallback ladder of , and per-scope `Arena`s that
  free cleanly on failure. A missing or broken `.so` degrades to the pure-Java shim,
  never a JVM crash.

## 6. Build & packaging notes

- The C++ toolchain and the `.so` artifact are produced by a dedicated Gradle task
  (to be wired in the next phase); the `.so` is excluded from VCS (see `.gitignore`)
  and built reproducibly.
- FFM downcalls run under `--enable-preview` on GraalVM 21; the preview flag is set
  centrally by the build, not per-module.
