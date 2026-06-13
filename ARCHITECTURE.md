# Aetherium Framework — Architecture

This is the canonical, top-level architecture document. Deep dives live in
[`docs/en/`](docs/en/) (English) and [`docs/ru/`](docs/ru/) (Russian). This file is
intentionally bilingual at the section level: each English section `(EN)` is
immediately followed by its Russian counterpart `(RU)`.

---

## 1. Problem statement (EN)

Minecraft mod loaders are mutually incompatible. A mod built for NeoForge will not
load on Fabric, and vice versa, because each loader defines its own entry-point
discovery, lifecycle events, registry access, and — critically — its own bytecode
patching mechanism (Mixin, coremods, access transformers). Authors therefore ship
2–4 separate builds and maintain divergent code paths.

Aetherium removes that duplication by interposing a **stable abstraction layer**.
A mod targets the Aetherium API once; Aetherium translates Aetherium-level
intentions into loader-specific actions at load time. The translation cost is paid
**once per JVM launch**, never per game tick.

## 1. Постановка задачи (RU)

Загрузчики модов Minecraft взаимно несовместимы. Мод, собранный под NeoForge, не
загрузится на Fabric и наоборот, поскольку каждый загрузчик определяет собственное
обнаружение точек входа, события жизненного цикла, доступ к реестрам и — что важнее
всего — собственный механизм патчинга байт-кода (Mixin, coremods, access
transformers). Поэтому авторы выпускают 2–4 отдельные сборки и сопровождают
расходящиеся ветви кода.

Aetherium устраняет это дублирование, вставляя **стабильный слой абстракции**. Мод
один раз нацеливается на API Aetherium; Aetherium транслирует намерения уровня
Aetherium в действия конкретного загрузчика во время загрузки. Стоимость трансляции
оплачивается **один раз за запуск JVM**, а не на каждом игровом тике.

---

## 2. Layered model (EN)

```
┌──────────────────────────────────────────────────────────────┐
│  Mod (compiled once against the Aetherium API)                 │
├──────────────────────────────────────────────────────────────┤
│  aetherium-core   — stable API, contracts, config, errors      │
├───────────────┬───────────────────────────┬──────────────────┤
│ aetherium-    │ aetherium-bytecode        │ aetherium-native  │
│ loader        │ (ASM transform engine)    │ (JNI/C++ bridge)  │
│ (loader shim) │                           │                   │
├───────────────┴───────────────────────────┴──────────────────┤
│  Underlying loader: NeoForge 1.21.1 (baseline) / Fabric / …    │
├──────────────────────────────────────────────────────────────┤
│  GraalVM 21 JVM  ·  Linux x86-64                               │
└──────────────────────────────────────────────────────────────┘
```

Data flow at launch:

1. The underlying loader boots and hands control to the `aetherium-loader` shim
   (registered as that loader's coremod / language adapter / pre-launch hook).
2. The shim builds a **dispatch table** — see — mapping every abstract API symbol
   to its concrete loader implementation. This is the only expensive step.
3. `aetherium-bytecode` rewrites mod classes that reference the API so each call
   site targets a stable `invokedynamic` bootstrap, linked once to the dispatch
   table entry.
4. From then on, every API call is a constant-time linked call. No lookups, no
   `instanceof` chains, no reflection on the hot path.

## 2. Слоистая модель (RU)

```
┌──────────────────────────────────────────────────────────────┐
│  Мод (скомпилирован один раз под API Aetherium)                │
├──────────────────────────────────────────────────────────────┤
│  aetherium-core   — стабильный API, контракты, конфиг, ошибки  │
├───────────────┬───────────────────────────┬──────────────────┤
│ aetherium-    │ aetherium-bytecode        │ aetherium-native  │
│ loader        │ (движок ASM-трансформации)│ (мост JNI/C++)    │
│ (прослойка)   │                           │                   │
├───────────────┴───────────────────────────┴──────────────────┤
│  Базовый загрузчик: NeoForge 1.21.1 (база) / Fabric / …        │
├──────────────────────────────────────────────────────────────┤
│  GraalVM 21 JVM  ·  Linux x86-64                               │
└──────────────────────────────────────────────────────────────┘
```

Поток данных при запуске:

1. Базовый загрузчик стартует и передаёт управление прослойке `aetherium-loader`
   (зарегистрированной как coremod / языковой адаптер / pre-launch хук загрузчика).
2. Прослойка строит **таблицу диспетчеризации** — см. — сопоставляя каждый
   абстрактный символ API его конкретной реализации в загрузчике. Это единственный
   дорогой шаг.
3. `aetherium-bytecode` переписывает классы мода, ссылающиеся на API, так чтобы
   каждая точка вызова целилась в стабильный bootstrap `invokedynamic`, связанный
   один раз с записью таблицы диспетчеризации.
4. С этого момента каждый вызов API — это связанный вызов за константное время. Без
   поиска, без цепочек `instanceof`, без рефлексии на «горячем пути».

---

## 3. The `O(1)` runtime guarantee (EN)

The performance constraint — `O(1)` low-level interactions at runtime — is met by a
strict separation of phases:

- **Load phase** (`O(n)` in the number of API symbols, run once): discovery,
  validation, dispatch-table construction, bytecode rewriting.
- **Run phase** (`O(1)` per call, run forever): every abstract call has been lowered
  to a single linked `invokedynamic` call site or a direct field/array access into
  the dispatch table.

We deliberately use `invokedynamic` with a `ConstantCallSite`: after the first
linkage the JIT inlines through it as if it were a direct call, so the abstraction is
free at steady state. The dispatch table itself is a flat array indexed by a
compile-time-assigned dense integer ID — array indexing is `O(1)` with no hashing.

Memory overhead is bounded: one dispatch-table array per loader (sized to the symbol
count, a few KB), plus the `CallSite` objects the JVM would allocate for any
`invokedynamic` anyway. No per-call allocation, no per-tick boxing.

## 3. Гарантия `O(1)` во время выполнения (RU)

Ограничение по производительности — `O(1)` для низкоуровневых взаимодействий во время
выполнения — достигается строгим разделением фаз:

- **Фаза загрузки** (`O(n)` от числа символов API, выполняется однократно):
  обнаружение, валидация, построение таблицы диспетчеризации, переписывание
  байт-кода.
- **Фаза выполнения** (`O(1)` на вызов, выполняется постоянно): каждый абстрактный
  вызов понижен до одной связанной точки вызова `invokedynamic` или прямого доступа
  к полю/массиву таблицы диспетчеризации.

Мы намеренно используем `invokedynamic` с `ConstantCallSite`: после первой линковки
JIT встраивает вызов так, будто он прямой, поэтому абстракция бесплатна в
установившемся режиме. Сама таблица диспетчеризации — плоский массив, индексируемый
плотным целочисленным ID, назначенным во время компиляции; индексация массива — это
`O(1)` без хеширования.

Накладные расходы по памяти ограничены: один массив таблицы диспетчеризации на
загрузчик (размером с число символов, несколько КБ) плюс объекты `CallSite`, которые
JVM в любом случае выделяет для `invokedynamic`. Никаких аллокаций на вызов, никакого
боксинга на тик.

---

## 4. Dispatch table & invokedynamic linkage (EN)

Each abstract API operation is assigned a **dense, stable integer ID** at build time
(stored in `aetherium-core`'s symbol manifest, not hardcoded in transformers). At
load time the shim populates `MethodHandle[] table` where `table[id]` is the resolved
handle for the active loader. The bytecode engine rewrites every API call site to:

```
invokedynamic aetherium_dispatch(args...) → bootstrap(AetheriumBootstraps, id)
```

The bootstrap method looks up `table[id]`, wraps it in a `ConstantCallSite`, and
returns it. The JVM links the call site exactly once; subsequent executions are
direct, JIT-inlinable calls. Adding a new loader means supplying a new `table`; **no
mod and no transformer changes.** This is the anti-hardcoding guarantee.

## 4. Таблица диспетчеризации и линковка invokedynamic (RU)

Каждой абстрактной операции API во время сборки назначается **плотный стабильный
целочисленный ID** (хранится в манифесте символов `aetherium-core`, не зашит в
трансформерах). Во время загрузки прослойка заполняет `MethodHandle[] table`, где
`table[id]` — разрешённый хэндл для активного загрузчика. Движок байт-кода переписывает
каждую точку вызова API в:

```
invokedynamic aetherium_dispatch(args...) → bootstrap(AetheriumBootstraps, id)
```

Метод bootstrap ищет `table[id]`, оборачивает его в `ConstantCallSite` и возвращает.
JVM линкует точку вызова ровно один раз; последующие выполнения — прямые,
встраиваемые JIT вызовы. Добавление нового загрузчика означает предоставление новой
`table`; **без изменений в модах и трансформерах.** Это и есть гарантия отсутствия
жёсткого кодирования.

---

## 5. Java 21 / GraalVM features in use (EN)

- **Sealed interfaces + records** for the symbol manifest and transformation results
  — exhaustive `switch` pattern matching, no defensive `default` branches.
- **Pattern matching for `switch`** to dispatch over transformation node kinds.
- **Virtual threads** for parallel, isolated class transformation during the load
  phase (each transform is independent → embarrassingly parallel, no shared mutable
  state).
- **`java.lang.foreign` (FFM, preview)** as the *preferred* native pathway, with the
  classic **JNI** bridge as a fallback where FFM is not yet viable (see
  `docs/en/native-bridge.md`).
- **`invokedynamic` + `MethodHandles`** for the `O(1)` dispatch described in 
- Compiled and run with `--enable-preview` on GraalVM 21; preview gating is
  centralized in the build, never scattered across modules.

## 5. Используемые возможности Java 21 / GraalVM (RU)

- **Sealed-интерфейсы + records** для манифеста символов и результатов трансформации —
  исчерпывающий `switch` с сопоставлением с образцом, без защитных веток `default`.
- **Сопоставление с образцом в `switch`** для диспетчеризации по видам узлов
  трансформации.
- **Виртуальные потоки** для параллельной изолированной трансформации классов на фазе
  загрузки (каждая трансформация независима → тривиально параллелизуема, без
  разделяемого изменяемого состояния).
- **`java.lang.foreign` (FFM, preview)** как *предпочтительный* нативный путь, с
  классическим мостом **JNI** в качестве отката там, где FFM пока неприменим (см.
  `docs/ru/native-bridge.md`).
- **`invokedynamic` + `MethodHandles`** для `O(1)`-диспетчеризации из 
- Компиляция и запуск с `--enable-preview` на GraalVM 21; управление preview
  централизовано в сборке, не разбросано по модулям.

---

## 6. Backward compatibility (EN)

Backward compatibility is preserved on three axes:

1. **Toward the loader.** The shim only *adds* coremods/transformers through the
   loader's own public extension points. It never rewrites loader-internal classes,
   so a loader update does not silently break us — at worst a symbol fails to resolve
   and we fall back ().
2. **Toward the mod.** The Aetherium API is versioned with semantic versioning and
   `core` exposes capability flags. A mod queries `Aetherium.capabilities()` and
   degrades gracefully; we never remove a symbol within a major version.
3. **Toward older bytecode.** Transformers operate at a pinned class-file version and
   refuse (rather than guess) when they encounter constructs newer than they
   understand, emitting a diagnostic and skipping that class.

## 6. Обратная совместимость (RU)

Обратная совместимость сохраняется по трём осям:

1. **К загрузчику.** Прослойка только *добавляет* coremods/трансформеры через
   собственные публичные точки расширения загрузчика. Она никогда не переписывает
   внутренние классы загрузчика, поэтому обновление загрузчика не ломает нас тихо — в
   худшем случае символ не разрешится и мы откатимся ().
2. **К моду.** API Aetherium версионируется по семантическому версионированию, а
   `core` экспонирует флаги возможностей. Мод запрашивает `Aetherium.capabilities()`
   и деградирует корректно; мы не удаляем символы в пределах мажорной версии.
3. **К старому байт-коду.** Трансформеры работают с зафиксированной версией
   class-файла и отказываются (а не угадывают), встретив конструкции новее
   понимаемых, выдавая диагностику и пропуская такой класс.

---

## 7. Security & fallback model (CIA triad) (EN)

Aetherium executes untrusted mod bytecode and rewrites it; security is first-class.

- **Confidentiality** — the native bridge exposes only an explicit allow-list of
  capabilities. Mods cannot reach arbitrary JNI/FFM symbols; the bridge brokers every
  call and redacts host paths from diagnostics.
- **Integrity** — every transformation is verified with ASM's `CheckClassAdapter`
  plus a JVM re-verification pass before the class is defined. Transforms are applied
  to a *copy*; the original `byte[]` is retained so any failure is fully reversible.
  Optional artifact signing (manifest digests) detects tampering before load.
- **Availability** — no single mod can wedge the loader. Each transform runs with a
  timeout on its own virtual thread; on exception, timeout, or verification failure
  the engine logs a structured diagnostic and **falls back** to the untransformed
  class (or disables just that mod), never aborting the whole launch.

Fallback policy is layered: `FFM → JNI → pure-Java shim → disable-feature`. The
active tier is chosen at load time by capability probing and recorded in the launch
report. Nothing is hardcoded to assume a tier is present.

## 7. Безопасность и модель отката (триада CIA) (RU)

Aetherium исполняет недоверенный байт-код модов и переписывает его; безопасность —
первоклассна.

- **Конфиденциальность** — нативный мост экспонирует только явный белый список
  возможностей. Моды не могут достучаться до произвольных символов JNI/FFM; мост
  посредничает в каждом вызове и вычищает пути хоста из диагностики.
- **Целостность** — каждая трансформация проверяется `CheckClassAdapter` из ASM плюс
  повторной верификацией JVM перед определением класса. Трансформации применяются к
  *копии*; исходный `byte[]` сохраняется, поэтому любой сбой полностью обратим.
  Опциональная подпись артефактов (дайджесты манифеста) обнаруживает подмену до
  загрузки.
- **Доступность** — ни один мод не может заклинить загрузчик. Каждая трансформация
  выполняется с таймаутом в собственном виртуальном потоке; при исключении, таймауте
  или провале верификации движок логирует структурированную диагностику и
  **откатывается** к нетрансформированному классу (или отключает лишь этот мод),
  никогда не прерывая весь запуск.

Политика отката слоиста: `FFM → JNI → чисто-Java прослойка → отключение функции`.
Активный уровень выбирается во время загрузки зондированием возможностей и
фиксируется в отчёте запуска. Ничто не зашито жёстко в предположении наличия уровня.

---

## 8. Module dependency rules (EN)

```
aetherium-cli ──► aetherium-loader ──► aetherium-core ◄── aetherium-bytecode
                          │                  ▲                    │
                          └──────────────────┴────────────────────┘
                                             │
                                    aetherium-native ──► aetherium-core
```

- `aetherium-core` depends on **nothing** internal (leaf). It defines all contracts.
- No cycles are permitted; the build fails if one is introduced.
- `aetherium-bytecode` and `aetherium-native` know `core` only — never each other,
  never the loader. The loader composes them.

## 8. Правила зависимостей модулей (RU)

```
aetherium-cli ──► aetherium-loader ──► aetherium-core ◄── aetherium-bytecode
                          │                  ▲                    │
                          └──────────────────┴────────────────────┘
                                             │
                                    aetherium-native ──► aetherium-core
```

- `aetherium-core` не зависит **ни от чего** внутреннего (лист). Он определяет все
  контракты.
- Циклы запрещены; сборка падает при их появлении.
- `aetherium-bytecode` и `aetherium-native` знают только `core` — никогда друг друга
  и никогда загрузчик. Их композирует загрузчик.

---

## 9. Status (EN) / Статус (RU)

This document describes the **target** architecture. The repository is at the
**foundation** phase: structure, documentation, and module placeholders are in place;
the engines are specified but not yet implemented. See [`CHANGELOG.md`](CHANGELOG.md).

Документ описывает **целевую** архитектуру. Репозиторий на этапе **основания**:
структура, документация и заглушки модулей готовы; движки специфицированы, но ещё не
реализованы. См. [`CHANGELOG.md`](CHANGELOG.md).
