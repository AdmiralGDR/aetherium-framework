# Auto-Wiring — Zero-Config Initialization (`@AetheriumInit`)

*English. Russian mirror: [`../ru/autowiring.md`](../../ru/reference/autowiring.md).*

A mod no longer needs to write an `AetheriumMod` entrypoint, a `META-INF/services` file, or any
initialization plumbing. A developer annotates a static method with **`@AetheriumInit`** and the framework
discovers it **at compile time** and wires up everything else — with **zero runtime reflection and zero
classpath scanning**.

```java
public final class MyMod {
    @AetheriumInit(runAfter = "registry")
    public static void setup(AetheriumContext ctx) {
        ctx.log("MyMod up on tier " + ctx.computeTier());
    }
}
```

That is the whole entrypoint. No `implements AetheriumMod`, no services file, no `@Mod`.

## How it works (entirely in `javac`)

`AetheriumInitProcessor` (an annotation processor in `aetherium-content`) runs during the consumer's
compilation:

1. It discovers every `@AetheriumInit` method and validates the signature — it must be
   `public static void m(AetheriumContext)`; a bad signature is a **compile error** pointing at the
   offending method.
2. It orders them into a deterministic init DAG with `InitOrdering` — the same Kahn sort, stable
   declaration-index tie-break, and `runBefore`/`runAfter` model as the hook DAG ([`injector.md`](../explanation/injector.md)).
   A cycle or a duplicate id **fails the build** instead of guessing.
3. `InitSourceWriter` generates a single `AetheriumMod` whose `onInitialize` invokes each init by
   **direct static call**, in order, and emits the matching `META-INF/services` registration.

At runtime the loader's existing `ServiceLoader.load(AetheriumMod.class)` finds the generated class and
calls it ([`game-integration.md`](../explanation/game-integration.md)) — the init methods run as plain, statically-linked
calls. There is no reflection, no annotation lookup, and nothing scans the classpath: discovery already
happened in the compiler, which is also why the framework's runtime footprint for this feature is
effectively zero.

The generated class name is scoped by mod id (`-Aaetherium.modId=<id>`, default `aetherium`), so two
Aetherium mods on one classpath never collide.

## Relationship to the explicit API

The hand-written `AetheriumMod` SPI still works exactly as before — `@AetheriumInit` is purely additive,
generating an `AetheriumMod` for you. Both paths converge on the same `ServiceLoader` discovery the loader
already performs.

## Proof

The pure ordering + generation logic is unit-tested in `aetherium-datagen`
([`InitWiringTest`](../../../aetherium-datagen/src/test/java/org/aetherium/datagen/InitWiringTest.java)), and
the processor is verified end-to-end by a real in-process `javac` run in `aetherium-content`
([`AetheriumInitProcessorTest`](../../../aetherium-content/src/test/java/org/aetherium/content/AetheriumInitProcessorTest.java)):
annotated methods only → a generated, reflection-free entrypoint + service registration, with the init
calls emitted in DAG order.
