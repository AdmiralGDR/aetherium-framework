# Ephemeral JFR Probes — Zero-Overhead Telemetry

*English. Russian mirror: [`../ru/probes.md`](../ru/probes.md).*

Module: [`aetherium-injector`](../../aetherium-injector) (`org.aetherium.injector.probe`).

A conventional profiler pays a price forever: every instrumented call carries an `if (profilingEnabled)`
branch even when no one is profiling. Aetherium refuses that tax. Probes are **woven in only while a
profile is requested and removed afterwards** — an un-probed method contains no probe code at all, not
even a check.

## How "zero static overhead" is achieved

`ProbeWeaver` is a `ClassTransformer` driven by an active set of `ProbeTarget`s:

- **Active set empty / class not targeted** → `handles()` returns `false` → the class is left
  byte-for-byte untouched. There is literally no probe bytecode and no flag.
- **Target active** → the method gets `event.begin()` at entry and `event.commit()` before every return
  (net-zero operand stack, so the engine's `COMPUTE_FRAMES` keeps it verifiable). The JFR event records
  the method's wall-clock duration.

Because the event class (`AetheriumMethodEvent`, a standard `jdk.jfr.Event`) is referenced *only* from
woven bytecode, a method with no active probe has no reference to it whatsoever.

## Making it ephemeral — hot-swap via Instrumentation

`DynamicProbeController` flips the active set and re-transforms already-loaded classes through the JVM's
`Instrumentation.retransformClasses`:

- `enable(target)` weaves the probe into the loaded class **instantly**.
- `disable(target)` / `clear()` re-transforms from the cached **original** bytes, physically stripping
  the probe.

The retransform-capable `Instrumentation` is obtained from `AetheriumProbeAgent`, attached either at
startup (`-javaagent`) or **on demand by self-attaching via the Attach API** (`SelfAttach`, requires
`-Djdk.attach.allowAttachSelf=true`). If no agent can be acquired (locked-down JVM), the controller
degrades gracefully: the active set still feeds the load-time weaver, so probes apply at the next class
load instead of instantly.

## Verification

`aetherium profile` proves the full lifecycle:

```
probe OFF: output references AetheriumMethodEvent=false   (zero static overhead — no probe bytecode)
probe ON : output references AetheriumMethodEvent=true
JFR recording captured 50 'org.aetherium.MethodTiming' event(s) from 50 calls
dynamic hot-swap: active probes=0, instrumentation=live (instant hot-swap)
```
