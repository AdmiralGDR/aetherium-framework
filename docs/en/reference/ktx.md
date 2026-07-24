# Kotlin DSL (`aetherium-ktx`)

*English. Russian mirror: [`../ru/ktx.md`](../../ru/reference/ktx.md).*

`aetherium-ktx` is a thin, **zero-overhead** Kotlin layer over the Java APIs. Every entry point is an
`inline` function or a small init-time builder that lowers to the **exact same** Java calls — injected
hooks still bind to the `O(1)` `invokedynamic` `HookTable`, so there is **no runtime reflection** and no
extra dispatch layer. It wraps three subsystems: the injector (`HookDag` / `AetheriumInjector`), the
off-heap `StructArena`, and the `DataGen` content pipeline.

## Injecting a hook — Java vs Kotlin

The DSL targets the merged, DAG-ordered hook group (`runBefore` / `runAfter`, no priority numbers) and
the context-aware cancellation channel. A `void`-method guard that cancels when the first `int` arg
exceeds 100:

**Java (before):**

```java
AetheriumInjector injector = AetheriumInjector.create();
injector.inClass("net/minecraft/world/entity/Entity")
        .method("tick", "()V")
        .at(InjectionAnchor.HEAD)
            .captureArguments()
            .hook("mymod:tick_guard", ctx -> {
                Object a0 = ctx.arg(0);
                if (a0 instanceof Integer i && i > 100) {
                    ctx.cancel();
                }
            })
        .commit();
injector.installHooks();
```

**Kotlin (after):**

```kotlin
val injector = injector {
    inject("net.minecraft.world.entity.Entity::tick") {
        captureArgs()
        hook("mymod:tick_guard") { cancelIf { intArg(0) > 100 } }
    }
}.install()
```

The `package.Class::method` target accepts dots or slashes; `descriptor` defaults to `()V` and `anchor`
to `HEAD`. Typed accessors (`intArg`, `longArg`, `floatArg`, `boolArg`, `argAs<T>`) read captured
arguments without a cast and never throw out of range. Ordering is declared in the hook body with
`runBefore` / `runAfter`.

## StructArena

```kotlin
val layout = structLayout { floats("x"); floats("vx") }
structArena(layout, 4_096) {                // allocates off-heap, auto-closes (use)
    val x = field("x"); val vx = field("vx")
    for (i in 0 until count()) setFloat(i, x, getFloat(i, x) + getFloat(i, vx))
}
```

`structArena` is `inline`, so neither the lambda nor the `try`/`finally` of `use` survives in bytecode —
it is the same off-heap, zero-GC store the Java API allocates.

## DataGen

```kotlin
val files = content {
    block("mymod", "steel_block", hardness = 5.0f)
    item("mymod", "ruby")
}.generate()   // resource-relative-path -> file-content, via the pure-Java AssetGenerator
```

## Build note

The module applies `org.jetbrains.kotlin.jvm` (resolved from the Gradle Plugin Portal) and compiles with
`-Xjvm-enable-preview` so the Kotlin compiler reads and emits preview-flagged classfiles in lock-step
with the FFM-backed Java modules. Tests run on the `--enable-preview` JVM the root build already
configures for every preview-capable module.

## ACID engine parity ()

The DSL fully exposes the ACID surface:

- **Transactions (Atomicity):** `transaction(loader) { mod("id", "binary.Name" to bytes) { inject(...) { ... } } }`
  wraps `TransactionalInjector` — a failing hook rolls back the whole mod while siblings commit.
  Kotlin-friendly views: `report.isCommitted("id")`, `report.isRolledBack("id")`,
  `report.publishedOrNull(name)`.
- **Value cancel:** `hook("id") { cancelWith(42) }` — unconditional `ctx.cancel(value)` parity
  (the predicate overload `cancelWith(v) { cond }` remains).
- **Contracts (Consistency):** `typealias`es `Ensures`/`Requires` and re-exported constants
  (`NON_NEGATIVE`, `POSITIVE`, …) — a Kotlin hook annotates identically and `aetherium analyze`
  reads the same descriptors from its bytecode.
- **Level PAL:** `level[pos]` / `level[x, y, z]` (get), `level[pos] = "minecraft:stone"` (set),
  `blockEntityOrNull(pos)`, `BlockPos` destructuring `val (x, y, z) = pos` and `pos + Triple(dx, dy, dz)`.
