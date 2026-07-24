# Как внедрить хук в ванильный метод

*Русский. English version: [`../../en/how-to/inject-a-hook.md`](../../en/how-to/inject-a-hook.md).*

*Практическое руководство. Предполагается рабочий мод-проект (см.
[учебник по началу работы](../tutorials/getting-started.md)); устройство инжектора описано в
пояснениях [injector](../explanation/injector.md) и [движок ACID](../explanation/acid.md).*

## Добавить простой (void) хук перед возвратом метода

Зарегистрируйте `InjectionProvider` — фреймворк находит его через `ServiceLoader` и применяет внутри
песочницы верификации:

```java
public final class MyHooks implements InjectionProvider {
    @Override
    public void configure(AetheriumInjector injector) {
        injector.inClass("net/minecraft/world/entity/Entity")
                    .method("tick", "()V")
                        .findReturn()
                        .insertHookBefore(MyHooks::onEntityTick)   // понижается до O(1) invokedynamic
                    .commit();
    }

    public static void onEntityTick() { /* ваша логика */ }
}
```

Зарегистрируйте его в `META-INF/services/org.aetherium.injector.InjectionProvider` (одна строка — имя
класса). Если вы используете Gradle-плагин или авто-подключение `@AetheriumInit`, файл генерируется
за вас.

## Отменить метод (пропустить ванильную логику)

Используйте *контекстный хук* — он получает `HookContext` и может отменить с заменой возвращаемого
значения:

```java
injector.inClass("net/minecraft/world/entity/player/Player")
            .method("getLuck", "()F")
                .toStart()
                .insertContextHookBefore(ctx -> ctx.cancel(1.0f))
            .commit();
```

## Прочитать аргументы цели

Передайте `captureArguments = true`; упакованные аргументы становятся видимы в контексте:

```java
.insertContextHookBefore(ctx -> {
    int amount = (Integer) ctx.arg(0);
    if (amount > 100) {
        ctx.cancel(0);          // ограничение: отмена с возвратом 0
    }
}, true)
```

## Упорядочить хуки между модами (и слить конфликтующие отмены)

Заякорите группу хуков и объявите ограничения порядка; DAG разрешает порядок запуска, а семантический
слиятель запускает все хуки над одним общим контекстом:

```java
injector.inClass("net/minecraft/world/entity/Entity")
            .method("hurt", "(F)Z")
                .at(InjectionAnchor.HEAD)
                .captureArguments()
                .hook("shields",  ShieldMod::onHurt)
                .hook("armor",    ArmorMod::onHurt).runAfter("shields")
            .commit();
```

## Применить весь мод атомарно (рекомендуется)

Оберните хуки мода в транзакцию — падающий хук откатит *все* правки мода, вместо того чтобы оставить
игру наполовину модифицированной:

```java
EngineReport report = TransactionalInjector.create(loader)
        .mod("my_mod", injector, List.of(
                new TargetClass("net.minecraft.world.entity.Entity", entityBytes),
                new TargetClass("net.minecraft.world.entity.player.Player", playerBytes)))
        .apply();
```

Если любой класс не проходит верификацию, весь мод откатывается и отключается; остальные моды
коммитятся независимо. Проверить поведение можно в любой момент: `aetherium acid`.

## Объявить контракт на хук (проверяется до запуска игры)

```java
@Ensures(Constraint.NON_NEGATIVE)
public static int lightLevel(HookContext ctx) { ... }
```

`aetherium analyze my-mod.jar` статически предупредит, если возврат доказуемо нарушает контракт.

## Проверить конфликты до компиляции

`aetherium lsp` (или `--serve` из IDE) предсказывает дубликаты идентификаторов, циклы порядка и
конкурирующие отмены между модами до запуска игры.
