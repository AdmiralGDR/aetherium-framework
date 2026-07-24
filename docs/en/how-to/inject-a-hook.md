# How to inject a hook into a vanilla method

*English. Русская версия: [`../../ru/how-to/inject-a-hook.md`](../../ru/how-to/inject-a-hook.md).*

*A task-oriented guide. It assumes you have a working mod project (see the
[getting-started tutorial](../tutorials/getting-started.md)); for the design behind the injector read
the [injector](../explanation/injector.md) and [ACID engine](../explanation/acid.md) explanations.*

## Add a simple (void) hook before a method returns

Register an `InjectionProvider` — the framework discovers it via `ServiceLoader` and applies it inside
the verification sandbox:

```java
public final class MyHooks implements InjectionProvider {
    @Override
    public void configure(AetheriumInjector injector) {
        injector.inClass("net/minecraft/world/entity/Entity")
                    .method("tick", "()V")
                        .findReturn()
                        .insertHookBefore(MyHooks::onEntityTick)   // lowered to O(1) invokedynamic
                    .commit();
    }

    public static void onEntityTick() { /* your logic */ }
}
```

Register it in `META-INF/services/org.aetherium.injector.InjectionProvider` (one line: the class
name). If you use the Gradle plugin or `@AetheriumInit` auto-wiring, that file is generated for you.

## Cancel a method (skip vanilla logic)

Use a *context hook* — it receives a `HookContext` and can cancel with a replacement return value:

```java
injector.inClass("net/minecraft/world/entity/player/Player")
            .method("getLuck", "()F")
                .toStart()
                .insertContextHookBefore(ctx -> ctx.cancel(1.0f))
            .commit();
```

## Read the target's arguments

Pass `captureArguments = true`; the boxed arguments become visible on the context:

```java
.insertContextHookBefore(ctx -> {
    int amount = (Integer) ctx.arg(0);
    if (amount > 100) {
        ctx.cancel(0);          // clamp: cancel with return value 0
    }
}, true)
```

## Order hooks across mods (and merge conflicting cancels)

Anchor a group of hooks and declare ordering constraints; the DAG resolves the run order and the
Semantic Merger runs all hooks against one shared context:

```java
injector.inClass("net/minecraft/world/entity/Entity")
            .method("hurt", "(F)Z")
                .at(InjectionAnchor.HEAD)
                .captureArguments()
                .hook("shields",  ShieldMod::onHurt)
                .hook("armor",    ArmorMod::onHurt).runAfter("shields")
            .commit();
```

## Apply a whole mod atomically (recommended)

Wrap the mod's hooks in a transaction so a failing hook rolls back *all* of the mod's edits instead of
leaving the game half-modded:

```java
EngineReport report = TransactionalInjector.create(loader)
        .mod("my_mod", injector, List.of(
                new TargetClass("net.minecraft.world.entity.Entity", entityBytes),
                new TargetClass("net.minecraft.world.entity.player.Player", playerBytes)))
        .apply();
```

If any class fails verification the whole mod is rolled back and disabled; other mods commit
independently. Verify the behaviour any time with `aetherium acid`.

## Declare a contract on your hook (checked before the game runs)

```java
@Ensures(Constraint.NON_NEGATIVE)
public static int lightLevel(HookContext ctx) { ... }
```

`aetherium analyze my-mod.jar` statically warns if a return can provably violate the contract.

## Check for conflicts before compiling

`aetherium lsp` (or `--serve` from your IDE) predicts duplicate ids, ordering cycles, and competing
cancels across mods before you ever launch the game.
