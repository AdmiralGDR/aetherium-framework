# Security — Capability-Based CIA-Triad Isolation

*English. Russian mirror: [`../ru/security.md`](../ru/security.md).*

Module: [`aetherium-security`](../../aetherium-security) (`org.aetherium.security`).

The JVM's `SecurityManager` is gone. Aetherium replaces it with an explicit **capability model**:
a mod holds only the authorities it was granted, the framework checks the relevant `Capability` before
any sensitive action, and the default is **deny**. This is the enforcement layer for the CIA triad.

## The model

```java
SecurityPolicy policy = SecurityPolicy.global();
policy.grant(CapabilityGrant.of("my_mod", Capability.NATIVE_MEMORY, Capability.REFLECTION));

policy.require("my_mod", Capability.NATIVE_MEMORY);   // returns; throws SecurityViolationException if not held
policy.allows("other_mod", Capability.FILE_WRITE);    // false — never granted (default deny)
```

`Capability` is the vocabulary: `REFLECTION`, `NATIVE_MEMORY`, `DEFINE_CLASS`, `FILE_READ`,
`FILE_WRITE`, `NETWORK`. A mod that was never registered resolves to `CapabilityGrant.none` — it can do
nothing privileged.

## Integrity — FFM memory bounds

FFM hands mods raw off-heap power; a stray offset is a memory-safety hole. A mod never receives a raw
`MemorySegment` — it receives a `GuardedSegment`, which (1) is only constructible by a holder of
`NATIVE_MEMORY` and (2) bounds-checks every access against the granted region, converting an escape
attempt into a contained `SecurityViolationException` instead of undefined behavior:

```java
GuardedSegment view = GuardedSegment.grant(policy, "my_mod", segment); // requires NATIVE_MEMORY
view.setInt(0, 42);        // ok
view.setInt(62, 1);        // 62+4 > 64 → SecurityViolationException (no OOB write)
```

## Confidentiality — reflection guard

Deep reflection (`setAccessible`) is how a hostile mod would read another mod's private state or defeat
the injector's sandbox. `ReflectionGuard.makeAccessible` enforces two rules: (1) the mod must hold
`REFLECTION`, and (2) the target must not be in a **protected** package — this second rule is absolute
and holds even with the capability. Protected prefixes include `org.aetherium.loader`,
`org.aetherium.injector`, `org.aetherium.bytecode.runtime`, `org.aetherium.security`,
`java.lang.invoke`, and `jdk.internal.`. A mod can introspect its own classes, never the framework's.

## JPMS strategy

On the module path, the framework ships internal packages via **qualified exports** (`exports … to …`)
so only sanctioned modules can read them, and keeps the dispatch/injector runtime un-exported. The
capability layer above is the runtime complement that holds on the classpath too, where JPMS boundaries
are not enforced.

## Verification

`aetherium security` exercises every invariant a hostile mod would probe:

```
default-deny            : OK
granted capability ok   : OK
FFM in-bounds access    : OK
FFM out-of-bounds block : OK   (Integrity)
internal reflection deny: OK   (Confidentiality)
own-class reflection ok : OK
```
