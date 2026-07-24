# Native Bridge (JNI / C++ with FFM fallback ordering)

*Module: `aetherium-native` · English. Russian mirror: [`../ru/native-bridge.md`](../../ru/explanation/native-bridge.md).*

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

- The `.so` is built by CMake driven from `aetherium-native/build.gradle.kts`
  (`cmakeConfigure` → `compileNative`) and bundled into the jar at
  `native/libaetherium_native.so`. If the C++ toolchain is absent the native tasks are
  skipped (`onlyIf`) and the `.so` is simply not bundled — the Pre-Flight Check then
  degrades to pure Java. Build artifacts (`.so`) are excluded from VCS (`.gitignore`).
- FFM downcalls run under `--enable-preview` on GraalVM 21; the CLI also passes
  `--enable-native-access=ALL-UNNAMED`. Flags are set centrally by the build.

## 7. Implementation status (what exists now)

Implemented in `org.aetherium.native_bridge`:

| Type | Role |
|------|------|
| `NativeLibrary` | FFM `SymbolLookup` + `MethodHandle` downcalls built once → `O(1)` calls; `Arena`-scoped lifetime. |
| `NativeBridge` | High-level, allow-listed surface: `selfTest`, `allocateAndSum` (Arena-owned memory crossing FFM), `probeVulkan`, ABI check. |
| `VulkanProbe` | Result of the hardware-access scaffold (instance + device + queue-family enumeration; **no shader logic**). |
| `NativeProbe` | Non-throwing probe used by Pre-Flight; decides `FFM` vs `PURE_JAVA`. |
| `NativeCapabilityProviders` | `FallbackChain<CapabilityProvider>` for the `FFM → PURE_JAVA` ladder. |
| `compute.PureJavaComputePipeline` / `compute.NativeComputePipeline` | `ComputePipeline` implementations (the native one is the Vulkan scaffold; shaders TODO). |

The C ABI (`src/main/cpp/aetherium_native.cpp`) is a flat function table:
`aeth_native_abi_version`, `aeth_self_test`, `aeth_sum_bytes`, `aeth_vk_probe`. Vulkan is
reached via `dlopen` at call time, so the library has **no hard `libvulkan` dependency**
(verified with `ldd`) and loads everywhere.

## 8. Pre-Flight Check & diagnostic translation

The Pre-Flight Check (`org.aetherium.loader.PreFlightCheck`, run before mod loading)
validates both subsystems with real work — a dummy ASM transform and a dummy native
allocation — then resolves the compute tier. It is **total**: any failure (incl. a
missing/broken `.so`) is caught, translated to a bilingual `Explanation` by
`org.aetherium.core.diag.DiagnosticTranslator` (mapping `UnsatisfiedLinkError`,
`ClassFormatError`, `VerifyError`, `BootstrapMethodError`, … to plain EN/RU text), logged
as a structured `Diagnostic`, and the launch proceeds on the pure-Java tier. Demonstrated
via `aetherium-cli preflight` (healthy → `FFM`; forced-missing lib → `PURE_JAVA`, launch
still allowed).
