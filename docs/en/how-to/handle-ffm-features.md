# How to use an FFM/off-heap feature that degrades safely

*English. Russian version: [`../../ru/how-to/handle-ffm-features.md`](../../ru/how-to/handle-ffm-features.md).*

*A task-oriented guide. It solves the one trap every mod that touches `java.lang.foreign` (off-heap
`StructArena`, SIMD) hits exactly once — a class that needs `--enable-preview` on a launcher that does not pass
it. See [native-bridge](../explanation/native-bridge.md) for the capability model behind it.*

## The trap

A preview-compiled class throws `UnsupportedClassVersionError` on a stock launcher. That is an **`Error`, not an
`Exception`** — so the obvious `catch (RuntimeException)` misses it — and it surfaces at the first *use*, deep
inside init, not at class-load time where you would look for it. Hand-rolling the guard means catching
`Throwable`, probing once, caching the verdict, and degrading every call site. Easy to get wrong.

## The one-liner

`org.aetherium.core.Capabilities` does it for you. Pick the implementation once at init:

```java
import org.aetherium.core.Capabilities;

// Uses the off-heap engine when the JVM allows it, else the pure-Java one — no Error escapes.
MyEngine engine = Capabilities.ffm(OffHeapEngine::new, PureJavaEngine::new);
```

`ffm(preview, fallback)` runs `preview` and, on **any `Throwable`** (including the `UnsupportedClassVersionError`
family), returns `fallback`. That's the whole fix: the `Error`-not-`Exception` subtlety is now impossible to get
wrong.

## Repeated use: probe once, then stick

If you resolve lazily at each call site instead of once at init, use the memoizing form so a degraded launch
never re-attempts (and re-fails) the preview class load:

```java
Supplier<MyBuffer> buffer = Capabilities.ffmLazy(OffHeapBuffer::new, HeapBuffer::new);
// first get() probes; every later get() goes straight to whichever path worked.
```

## Just a feature flag

When there is no value to produce, probe whether the path loads:

```java
if (Capabilities.available(SimdMath::warmUp)) {
    // fast path
}
```

Prove it yourself: `aetherium capabilities` runs the self-test, showing an `Error` thrown from the preview
supplier degrading cleanly to the fallback.
