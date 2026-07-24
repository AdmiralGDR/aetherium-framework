# Bytecode Manipulation Engine (ASM-based)

*Module: `aetherium-bytecode` · English. Russian mirror: [`../ru/bytecode-engine.md`](../../ru/explanation/bytecode-engine.md).*

## 1. Purpose

The engine rewrites mod classes at load time so that loader-agnostic API calls are
lowered to concrete, `O(1)`-dispatchable call sites (see `ARCHITECTURE.md` –). It
is built on [OW2 ASM](https://asm.ow2.io/) — chosen over Javassist/ByteBuddy for its
zero-overhead visitor model and precise control over the constant pool and stack map
frames, which matters for the verification guarantees below.

## 2. Pipeline

```
 raw byte[]  ──►  ClassReader  ──►  [ TransformChain ]  ──►  ClassWriter  ──►  verify  ──►  defined class
     │                                    │                                      │
  retained                         ordered, pure                          CheckClassAdapter
  (fallback)                       transformers                           + JVM re-verify
```

1. **Read** — `ClassReader` parses the original bytes. The original `byte[]` is
   retained for the lifetime of the transform so any failure is reversible.
2. **Transform** — an ordered `TransformChain` of independent `ClassTransformer`s
   visits the class. Each transformer is a pure function `(ClassNode) → ClassNode`
   with no shared mutable state, enabling parallel execution across classes on virtual
   threads.
3. **Write** — `ClassWriter` with `COMPUTE_FRAMES` emits new bytes. Frame computation
   is the load-phase cost we accept once; it never touches the run phase.
4. **Verify** — `CheckClassAdapter.verify()` plus a JVM re-verification pass. Only a
   class that passes both is defined; otherwise we fall back ().

## 3. Transformer contract

```java
public interface ClassTransformer {              // open SPI, see note below

    /** Dense, build-assigned priority. Lower runs first. Never hardcoded inline. */
    int order();

    /** True if this transformer has any work to do for the class — cheap pre-filter. */
    boolean handles(ClassContext context);

    /** Apply the transform; mutates context.node() in place, reports via TransformResult. */
    TransformResult apply(ClassContext context);

    /** Stable id for diagnostics; defaults to the simple class name. */
    default String id() { return getClass().getSimpleName(); }
}
```

> **Design note (implemented).** An earlier draft sketched this as `sealed`. It is
> deliberately an **open SPI** instead: the loader — and later, mods — must contribute
> their own transformers from *other* modules without `aetherium-bytecode` enumerating
> them. Modularity is preserved by the dependency rule, not by sealing: implementations
> may depend only on `core` + ASM. The `ClassContext` carries the parsed `ClassNode`, so
> `handles`/`apply` take the context (not a raw node).

`TransformResult` **is** a sealed type: `Applied(ClassNode)`, `Skipped(reason)`, or
`Failed(diagnostic)`. Exhaustive pattern matching in the engine driver removes defensive
branches and makes every outcome explicit.

## 4. Dispatch lowering (the core transform)

`DispatchLoweringTransformer` finds every `INVOKESTATIC` to the configured abstract API
owner and replaces it with an `invokedynamic` to a shared bootstrap, passing the symbol's
**dense integer ID** (read from `aetherium-core`'s symbol manifest — *not* a literal in
the transformer). The runtime classes live in `org.aetherium.bytecode.runtime`:

- `AetheriumBootstraps.bootstrapDispatch(Lookup, String, MethodType, int id)` resolves
  `DispatchTable.handle(id)` and returns a `ConstantCallSite`.
- `DispatchTable` is a flat `MethodHandle[]` installed once at load time; `handle(id)` is
  a bare array index.

Result: one-time linkage, then JIT-inlined direct calls. Adding a loader changes only the
installed table, never this transformer — the anti-hardcoding contract. (Verified by the
`aetherium-cli selftest`: a lowered `compute(21)` call routes through the table and
returns `42`.)

## 5. Error handling & fallback

- **Pre-filter** (`handles`) avoids touching classes with no API references — most
  classes are never rewritten at all.
- **Per-class isolation**: each class transforms on its own virtual thread with a
  timeout. Exception / timeout / verification failure → structured diagnostic + fall
  back to the retained original `byte[]`. One bad class never aborts the launch.
- **Class-file version pinning**: constructs newer than the engine understands cause a
  *skip with diagnostic*, never a guess.

## 6. Performance & memory

- Load phase: `O(n)` in classes × transformers, parallelized across cores.
- Run phase: `O(1)` per API call (linked `invokedynamic`). No reflection, no boxing.
- Memory: transient `ClassNode`s are released after writing; only the dispatch table
  (a few KB) persists. Retained originals are dropped once a class is successfully
  defined.

## 7. Security (maps to CIA)

- **Integrity** — double verification before `defineClass`; transforms on copies.
- **Availability** — timeouts + per-class isolation + guaranteed fallback.
- **Confidentiality** — transformers receive a `ClassContext` with host paths redacted;
  they cannot read outside the class graph they were handed.
