# Injector — Fluent Bytecode Manipulation (the "Mixin Killer")

> Module: [`aetherium-injector`](../../../aetherium-injector) — depends only on `aetherium-bytecode`
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

## `HookContext` — `this`, arguments, and method cancellation

A bare `void ()` hook can observe, but a Mixin replacement must also reach the receiver and arguments
and **cancel the original method**. That is `ContextualHook` + `HookContext`:

```java
AetheriumInjector injector = AetheriumInjector.create()
    .inClass("net/minecraft/world/entity/LivingEntity")
        .method("hurt", "(Lnet/minecraft/world/damagesource/DamageSource;F)Z")
            .toStart()
            .insertContextHookBefore(ctx -> {            // self + cancel, no arg boxing
                if (isInvulnerable(ctx.self())) ctx.cancel(false);   // skip vanilla; return false
            }, /* captureArguments = */ true)            // opt in to read the arguments
        .commit();
injector.installHooks();
```

`HookContext` carries:

| Member | Purpose |
|---|---|
| `self()` | the receiver (`this`), or `null` for a static method |
| `arg(int)` / `argCount()` | the captured arguments, boxed (out-of-range returns `null`, never throws) |
| `cancel()` | cancel a `void` method — skip the rest of the body |
| `cancel(Object value)` | cancel a value-returning method, supplying the return value |
| `isCancelled()` / `returnValue()` | read by the injected bytecode |

### How cancellation is lowered (frame-correct, `COMPUTE_FRAMES`-clean)

The cursor emits, in front of the target instruction:

```
NEW HookContext ; DUP ; <self> ; <args[]> ; INVOKESPECIAL <init> ; ASTORE ctx
ALOAD ctx ; INVOKEDYNAMIC invoke(LHookContext;)V          // the O(1) hook call
ALOAD ctx ; INVOKEVIRTUAL isCancelled()Z ; IFEQ CONT      // not cancelled → continue vanilla body
  <RETURN | unbox+xRETURN | CHECKCAST+ARETURN>            // cancelled → return ctx.returnValue()
CONT:
  ... original instruction ...
```

Every path other than the early return is **net-zero on the operand stack**, the context lives in a
fresh local slot, and the early return discards the stack legally — so `COMPUTE_FRAMES` recomputes a
valid frame at `CONT` and the JVM verifier accepts the class. A primitive return is unboxed inline
(`Integer.intValue()` etc.); a reference return is `CHECKCAST` to the real return type.

### Performance — boxing only where you ask for it

The standing rule is to avoid hot-path boxing. The injector honors it on a gradient:

- **`AetheriumHook`** (the `void ()` hook) — zero allocation, the hot path.
- **`ContextualHook` without argument capture** — one small `HookContext` allocation for `self` +
  cancellation; **no primitive is boxed**.
- **`ContextualHook` with `captureArguments = true`** — opt-in, boxes the arguments into an
  `Object[]`; use it only when a hook must read them.
- The return value is boxed **only on the cold cancellation path** — you are returning early and
  skipping the whole vanilla body, so it is net-positive.

`this` and the locals are pushed **directly** into the typed `invokedynamic` site rather than being
threaded through a reflective `Object[]`, keeping the dispatch itself allocation-free and inlinable.

## DAG hook ordering + the ASM Semantic Merger

Integer "priorities" are a dumb ordering primitive — two mods both pick `1000` and the result is a coin
toss. Aetherium replaces them with a **dependency DAG**: a hook declares *relationships*, and
`HookDag` computes a deterministic topological order (Kahn's algorithm, smallest-declaration-index
tie-break, so the order is stable and reproducible across launches; a true cycle throws
`HookCycleException` and is contained).

```java
injector.inClass("net/minecraft/world/entity/player/Player")
    .method("hurt", "(Lnet/minecraft/world/damagesource/DamageSource;F)Z")
    .at(InjectionAnchor.HEAD)              // typed anchor, not @At("HEAD")
        .captureArguments()
        .hook("shield_mod:block", ShieldMod::onHurt).runBefore("armor_mod:absorb")
        .hook("armor_mod:absorb", ArmorMod::onHurt)
    .commit();
```

### The double-cancel conflict, and how the Merger resolves it

What happens when **two hooks both call `ctx.cancel()`**? A naive "one block per hook" lowering would
let the first hook's early `return` fire immediately — the second hook never runs, and whichever mod's
transform happened to be applied last silently wins. That is exactly the kind of order-dependent
conflict the DAG is meant to kill.

The **ASM Semantic Merger** lowers a whole DAG-ordered group as a *single* shared-context block with
*one* cancellation epilogue:

```
NEW HookContext ; ... ; ASTORE ctx        // built once for the group
ALOAD ctx ; INVOKEDYNAMIC hook_a          // every hook runs, in DAG order,
ALOAD ctx ; INVOKEDYNAMIC hook_b          // each observing the previous one's writes to ctx
ALOAD ctx ; isCancelled() ; IFEQ CONT     // ONE cancellation decision, after all hooks
  <return ctx.returnValue()>
CONT: ...original body...
```

So the cancellation conditions are **combined dynamically**: every hook gets to run and inspect the
running decision (`ctx.isCancelled()`, `ctx.returnValue()`) and either defer to it or refine it, and the
single epilogue applies the final, deterministic result. The self-test (`aetherium inject`) proves it
with two hooks declared in reverse order (`mod_b` `runAfter` `mod_a`):

```
DAG resolved order (declared [mod_b, mod_a] + runAfter) = [mod_a, mod_b]
double-cancel merge: merged(123) = 9   # mod_a cancel(7); mod_b reads 7 from the shared ctx, cancels 7+2=9
```

`mod_a` cancels with `7`; instead of returning `7` and starving `mod_b`, the merger runs `mod_b` too,
which sees the shared context already holds `7` and **combines** it into `9`. One method, both hooks
honored, deterministic outcome — no priority guessing, no last-transform-wins race.

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
method cancellation    : OK     # context hook ctx.cancel(99) → compute() returns 99, vanilla 21 bypassed
arg read + value cancel: OK     # hook reads arg0=10, cancels with arg0+5 → doubleIt(10) returns 15, not 20
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

## Multi-mod coexistence ()

Hook IDs are allocated from a **process-wide, append-only** space in `HookTable`
(`registerVoid`/`registerContext`), assigned at hook-declaration time. Two independently built
`AetheriumInjector`s — i.e. two different mods — therefore never share or overwrite each other's IDs.
(Previously each injector counted from 0 and `installHooks()` *replaced* the whole table, so a second
injecting mod silently clobbered the first, or threw `BootstrapMethodError` inside a vanilla method.)
`installHooks()` is now idempotent, and `TransactionalInjector` no longer rebinds the table per mod. Proof:
`aetherium coexist` builds two mods, invokes each, and asserts both hooks fire with zero cross-talk.
