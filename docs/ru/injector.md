# Injector — текучая манипуляция байт-кодом («убийца Mixin»)

> Модуль: [`aetherium-injector`](../../aetherium-injector) — зависит только от `aetherium-bytecode`
> (движок ASM + диспетчеризация `invokedynamic`) и транзитивно от `aetherium-core`. Никогда не
> импортирует типы Minecraft/NeoForge. Подключается к загрузке классов через `aetherium-loader`.

Mixin управляет инъекциями аннотациями и **строковыми** селекторами (`@At("HEAD")`,
`@Inject(method = "...")`). Aetherium заменяет это **программным, строго типизированным текучим API**
на основе навигируемого `BytecodeCursor` и оборачивает каждую правку в верификационную песочницу
движка байт-кода, так что плохая инъекция не может уронить JVM.

## Поверхность текучего API

```java
AetheriumInjector injector = AetheriumInjector.create()
    .inClass("net/minecraft/world/entity/Entity")   // цель по JVM-имени (или Type)
        .method("tick", "()V")                       // цель по имени + дескриптору (типизированно, не строка-шаблон)
            .findReturn()                            // навигация по реальному графу инструкций
            .insertHookBefore(MyMod::asyncTick)      // маршрут в API Aetherium — понижается до O(1) invokedynamic
        .commit();                                   // финализация правила
injector.installHooks();                             // однократная привязка таблицы хуков
```

### `BytecodeCursor` — типизированная навигация и правка (без строк `@At`)

| Навигация | Правка | Понижение хука |
|---|---|---|
| `toStart()` / `toEnd()` | `insertBefore(InsnList)` | `insertHookBefore(hookId)` |
| `next()` / `previous()` | `insertAfter(InsnList)` | `insertHookAfter(hookId)` |
| `jumpTo(int index)` | `replace(InsnList)` | `replaceWithHook(hookId)` |
| `findOpcode(int)` / `tryFindOpcode(int)` | `delete()` | |
| `findReturn()` | | |

`MethodInjection` точно повторяет их, но **записывает** каждый вызов как операцию, воспроизводимую над
живым курсором при фактической загрузке целевого класса — поэтому инъекции *объявляются* при
инициализации и *применяются* лениво. Параллельной модели операций нет: записанные операции — это
`Consumer<BytecodeCursor>`, поэтому текучая поверхность и исполнитель — один и тот же код
`BytecodeCursor`.

### Понижение хука — `O(1)`, а не хрупкий статический вызов

`insertHookBefore(MyMod::asyncTick)` **не** порождает `INVOKESTATIC`. Хук (`AetheriumHook`,
функциональный интерфейс `void ()`) регистрируется в инжекторе, который назначает ему плотный ID;
курсор порождает `invokedynamic` (дескриптор `()V`), привязанный к `HookBootstrap`. При первом
выполнении JVM линкует его **однократно** с записью `HookTable` и кэширует `ConstantCallSite` — далее
вызов прямой и встраиваемый JIT. Это тот же механизм `invokedynamic` (`O(1)`), что движок применяет
для понижения API Aetherium, здесь выделенный под внедрённые хуки.

## Абсолютная безопасность — верификационная песочница

Инъекция в ванильный код опасна, поэтому инжектор **локализует каждый сбой**. `InjectorTransformer` —
обычный `ClassTransformer`, поэтому работает внутри `BytecodeEngine`, который:

1. пересчитывает фреймы карты стека (`COMPUTE_FRAMES`),
2. выполняет `CheckClassAdapter` + best-effort проверку потоков данных,
3. при **любом** `VerifyError`, неверном результате, исключении или таймауте на класс логирует
   структурированный `Diagnostic` и откатывает класс к **исходным** байтам.

Трансформер добавляет первую линию локализации: неосуществимая навигация `BytecodeCursor` бросает
`CursorException`, а отсутствие целевого метода сообщается — оба превращаются в
`TransformResult.Failed` со структурированной диагностикой, запускающей откат. **JVM никогда не падает;
сбойная инъекция просто оставляет ваниль нетронутой.**

Доказано `InjectorSelfTest` (запуск: `aetherium inject`):

```
programmatic injection : OK (compute()=21 via 1 hook call(s))
revert on bad bytecode : OK     # POP на пустом стеке → AE-VERIFY-001 → откат, класс по-прежнему возвращает 21
revert on cursor miss  : OK     # findOpcode(MONITORENTER) не найден → AE-INJECT-CURSOR → откат
```

## Мост загрузчика

Мод поставляет инъекции через независимый от загрузчика SPI `InjectionProvider` (регистрируется через
`META-INF/services/org.aetherium.injector.InjectionProvider`) — он объявляет, *что* внедрять, не
импортируя типы NeoForge/ModLauncher. На этапе загрузки `aetherium-loader`:

- находит каждого провайдера через `ServiceLoader`, даёт каждому наполнить общий `AetheriumInjector`,
- устанавливает объединённую таблицу хуков,
- добавляет трансформер инжектора в движок и
- в `AetheriumLaunchPlugin.handlesClass` пропускает класс через deny-list пространств имён, если на
  него нацелено правило инъекции — так перехватывается ванильная цель `net.minecraft`, а всё остальное
  остаётся нетронутым.

## Соблюдённые правила

- **Без сопоставления по строкам** — цели задаются JVM-именами + дескрипторами, навигация — типизированный
  `BytecodeCursor`. Эквивалента `@At("HEAD")` нет.
- **Абсолютная безопасность** — каждая модификация выполняется в верификационной песочнице ASM; все
  `VerifyError` и сбои навигации локализуются и откатываются со структурированным `Diagnostic`. Ноль
  жёстких падений JVM.
