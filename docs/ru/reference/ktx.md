# Kotlin DSL (`aetherium-ktx`)

*Русский. Английская версия: [`../en/ktx.md`](../../en/reference/ktx.md).*

`aetherium-ktx` — тонкий слой Kotlin **без накладных расходов** поверх Java-API. Каждая точка входа —
это `inline`-функция или маленький построитель этапа инициализации, понижающийся к **тем же самым**
Java-вызовам: внедрённые хуки по-прежнему привязываются к `O(1)` `invokedynamic`-таблице `HookTable`,
поэтому **нет рефлексии в рантайме** и нет лишнего слоя диспетчеризации. Оборачивает три подсистемы:
инжектор (`HookDag` / `AetheriumInjector`), off-heap `StructArena` и конвейер контента `DataGen`.

## Внедрение хука — Java против Kotlin

DSL нацелен на слитую, упорядоченную DAG-группу хуков (`runBefore` / `runAfter`, без чисел-приоритетов)
и контекстный канал отмены. Защита `void`-метода, отменяющая вызов, когда первый `int`-аргумент
превышает 100:

**Java (было):**

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

**Kotlin (стало):**

```kotlin
val injector = injector {
    inject("net.minecraft.world.entity.Entity::tick") {
        captureArgs()
        hook("mymod:tick_guard") { cancelIf { intArg(0) > 100 } }
    }
}.install()
```

Цель `package.Class::method` принимает точки или слэши; `descriptor` по умолчанию `()V`, `anchor` —
`HEAD`. Типизированные аксессоры (`intArg`, `longArg`, `floatArg`, `boolArg`, `argAs<T>`) читают
захваченные аргументы без приведения типов и никогда не бросают исключение за границами. Порядок
объявляется в теле хука через `runBefore` / `runAfter`.

## StructArena

```kotlin
val layout = structLayout { floats("x"); floats("vx") }
structArena(layout, 4_096) {                // выделяет off-heap, авто-закрытие (use)
    val x = field("x"); val vx = field("vx")
    for (i in 0 until count()) setFloat(i, x, getFloat(i, x) + getFloat(i, vx))
}
```

`structArena` — `inline`, поэтому ни лямбда, ни `try`/`finally` из `use` не остаются в байт-коде: это
то же off-heap-хранилище без GC, что выделяет Java-API.

## DataGen

```kotlin
val files = content {
    block("mymod", "steel_block", hardness = 5.0f)
    item("mymod", "ruby")
}.generate()   // относительный-путь-ресурса -> содержимое, через чистый Java-AssetGenerator
```

## Замечание о сборке

Модуль применяет `org.jetbrains.kotlin.jvm` (разрешается из Gradle Plugin Portal) и компилируется с
`-Xjvm-enable-preview`, чтобы компилятор Kotlin читал и эмитировал preview-классы синхронно с
Java-модулями на FFM. Тесты выполняются на JVM с `--enable-preview`, который корневая сборка уже
настраивает для каждого preview-совместимого модуля.

## Паритет с движком ACID (Фаза 20)

DSL полностью открывает поверхность ACID Фазы 19:

- **Транзакции (атомарность):** `transaction(loader) { mod("id", "binary.Name" to bytes) { inject(...) { ... } } }`
  оборачивает `TransactionalInjector` — падающий хук откатывает весь мод, соседи коммитятся.
  Kotlin-представления: `report.isCommitted("id")`, `report.isRolledBack("id")`,
  `report.publishedOrNull(name)`.
- **Отмена со значением:** `hook("id") { cancelWith(42) }` — безусловный паритет `ctx.cancel(value)`
  (перегрузка с предикатом `cancelWith(v) { cond }` сохраняется).
- **Контракты (согласованность):** `typealias`-ы `Ensures`/`Requires` и реэкспортированные константы
  (`NON_NEGATIVE`, `POSITIVE`, …) — Kotlin-хук аннотируется так же, и `aetherium analyze` читает те же
  дескрипторы из его байткода.
- **PAL уровня:** `level[pos]` / `level[x, y, z]` (чтение), `level[pos] = "minecraft:stone"` (запись),
  `blockEntityOrNull(pos)`, деструктуризация `BlockPos` `val (x, y, z) = pos` и `pos + Triple(dx, dy, dz)`.
