# Injector — Fluent Bytecode Manipulation (the "Mixin Killer")

> Module: [`aetherium-injector`](../../aetherium-injector) — depends only on `aetherium-bytecode`
> (ASM engine + `invokedynamic` dispatch) and, transitively, `aetherium-core`. Never imports a
> Minecraft/NeoForge type. Bridged into class-loading by `aetherium-loader`.

Mixin steers injections with annotations and **string-based** selectors (`@At("HEAD")`,
`@Inject(method = "...")`). Aetherium replaces that with a **programmatic, strongly-typed fluent API**
built on a navigable `BytecodeCursor`, and wraps every edit in the bytecode engine's verification
sandbox so a bad injection can never crash the JVM.

## The Fluent API surface

```java
AetheriumInjector injector = AetheriumInjector.create()
    .inClass("net/minecraft/world/entity/Entity")   // target by JVM internal name (or a Type)
        .method("tick", "()V")                       // target by name + descriptor (typed, not a string pattern)
            .findReturn()                            // navigate the real instruction graph
            .insertHookBefore(MyMod::asyncTick)      // route into an Aetherium API — lowered to O(1) invokedynamic
        .commit();                                   // finalize the rule
injector.installHooks();                             // bind the hook dispatch table once
```

### `BytecodeCursor` — typed navigation & editing (no `@At` strings)

| Navigation | Editing | Hook lowering |
|---|---|---|
| `toStart()` / `toEnd()` | `insertBefore(InsnList)` | `insertHookBefore(hookId)` |
| `next()` / `previous()` | `insertAfter(InsnList)` | `insertHookAfter(hookId)` |
| `jumpTo(int index)` | `replace(InsnList)` | `replaceWithHook(hookId)` |
| `findOpcode(int)` / `tryFindOpcode(int)` | `delete()` | |
| `findReturn()` | | |

`MethodInjection` mirrors these exactly, but **records** each call as an operation replayed against a
live cursor when the target class actually loads — so injections are *declared* at init and *applied*
lazily. There is no parallel operation model: the recorded ops are `Consumer<BytecodeCursor>`, so the
fluent surface and the executor are the same `BytecodeCursor` code.

### Hook lowering — `O(1)`, not a brittle static call

`insertHookBefore(MyMod::asyncTick)` does **not** emit an `INVOKESTATIC`. The hook
(`AetheriumHook`, a `void ()` functional interface) is registered with the injector, which assigns it
a dense ID; the cursor emits an `invokedynamic` (descriptor `()V`) bound to `HookBootstrap`. On first
execution the JVM links it **once** against the `HookTable` entry and caches a `ConstantCallSite` —
after that the call is a direct, JIT-inlinable dispatch. This is the same `O(1)` `invokedynamic`
mechanism the engine uses to lower the Aetherium API, dedicated here to injected hooks.

## Absolute safety — the verification sandbox

Injecting into vanilla code is dangerous, so the injector **contains every failure**. An
`InjectorTransformer` is a plain `ClassTransformer`, so it runs inside `BytecodeEngine`, which:

1. recomputes stack-map frames (`COMPUTE_FRAMES`),
2. runs `CheckClassAdapter` + best-effort dataflow verification,
3. on **any** `VerifyError`, malformed result, thrown exception, or per-class timeout, logs a
   structured `Diagnostic` and reverts the class to its **original** bytes.

The transformer adds the first line of containment: a `BytecodeCursor` navigation that can't be
satisfied throws `CursorException`, and a missing target method is reported — both become a
`TransformResult.Failed` with a structured diagnostic that triggers the revert. **The JVM is never
allowed to crash; a failed injection simply leaves vanilla untouched.**

Proven by `InjectorSelfTest` (run it with `aetherium inject`):

```
programmatic injection : OK (compute()=21 via 1 hook call(s))
revert on bad bytecode : OK     # POP on empty stack → AE-VERIFY-001 → reverted, class still returns 21
revert on cursor miss  : OK     # findOpcode(MONITORENTER) not found → AE-INJECT-CURSOR → reverted
```

## Loader bridging

A mod contributes injections through the loader-agnostic `InjectionProvider` SPI (registered via
`META-INF/services/org.aetherium.injector.InjectionProvider`) — it declares *what* to inject without
importing any NeoForge/ModLauncher type. At load time `aetherium-loader`:

- discovers every provider via `ServiceLoader`, lets each populate one shared `AetheriumInjector`,
- installs the combined hook table,
- adds the injector's transformer to the engine, and
- in `AetheriumLaunchPlugin.handlesClass`, lets a class through the namespace deny-list when an
  injection rule targets it — so a vanilla `net.minecraft` target is intercepted, while everything
  else stays untouched.

## Rules honored

- **No string-based matching** — targets are JVM internal names + descriptors, navigation is the typed
  `BytecodeCursor`. There is no `@At("HEAD")` equivalent.
- **Absolute safety** — every modification runs in the ASM verification sandbox; all `VerifyError`s
  and navigation failures are contained and reverted with a structured `Diagnostic`. Zero hard JVM
  crashes.
