# Архитектура производительности

*Русский. Английская версия: [`../en/performance.md`](../en/performance.md).*

Aetherium атакует фундаментальные ограничения Minecraft — Library Hell, промахи кэша и
однопоточный тик — низкоуровневыми механизмами, скрытыми за крошечным API.

## 1. Дедупликация зависимостей (Library Hell)

`org.aetherium.loader.DependencyFlattener` сводит объединение встроенных библиотек всех
модов к **одному победителю на `group:artifact`** (наивысшая версия) с журналом конфликтов,
поэтому в JVM существует только один экземпляр каждой библиотеки. Чистый и юнит-тестируемый.

```text
вход: kotlin-stdlib 1.8.10 (ModA), 1.9.24 (ModB), 1.9.0 (ModC), guava 31.1 (ModA), 33.0 (ModD)
→ победители: kotlin-stdlib 1.9.24, guava 33.0-jre   (дедуплицировано 3, конфликты залогированы)
```

## 2. Data-oriented память — `StructArena` (против cache-miss)

`org.aetherium.core.compute.StructArena` хранит N сущностей **непрерывно off-heap** (FFM),
поэтому их обход идёт линейно по памяти и максимизирует попадания в кэш L1/L2. Нет
Java-объектов на сущность → нет давления на GC и блужданий по указателям.

```java
StructLayout entity = StructLayout.builder()
    .doubles("x").doubles("y").doubles("z")
    .doubles("vx").doubles("vy").doubles("vz")
    .build();
try (StructArena arena = StructArena.allocate(entity, 10_000)) {
    StructField x = entity.field("x"), vx = entity.field("vx");
    arena.setDouble(i, x, arena.getDouble(i, x) + arena.getDouble(i, vx));
}
```

Доступ — `segment.get(layout, index*stride + offset)`: одна операция с проверкой границ,
`O(1)`, без аллокаций. Непересекающиеся срезы можно обновлять разными потоками без блокировок.

## 3. Асинхронный тик — `AetheriumTickEngine` + `@AetheriumAsyncTick`

`org.aetherium.core.tick.AetheriumTickEngine` выгружает тяжёлую логику на виртуальные потоки
Java 21 и объединяет их **Sync-барьером** до конца 50-мс тика, затем фиксирует результаты на
главном потоке — поэтому параллельный тик свободен от `ConcurrentModificationException`.

Ноль шаблонного кода — моддер пишет только:

```java
@AetheriumAsyncTick("physics")
void updatePhysics() { /* тяжёлая работа над своими данными */ }
// engine.registerAnnotated(myMod);  engine.tick();
```

Или программно через `AsyncTickTask` (`computeAsync()` — параллельная фаза + `commit()` —
фаза главного потока). Задача, бросившая исключение или превысившая бюджет, локализуется и
учитывается в `TickReport`; тик не падает.

## 4. SIMD и memory-mapped потоки (заглушки/мосты)

- `org.aetherium.core.simd.SimdMath` — массовая векторная математика (FMA, scale, dot) с
  корректной скалярной реализацией сейчас и `isVectorApiAvailable()`, определяющим
  инкубаторный Java Vector API во время выполнения (без навязывания `--add-modules` потребителям).
- `org.aetherium.core.io.MappedRegion` — отображает файлы в FFM `MemorySegment` через
  `FileChannel.map(..., Arena)` для потоковой обработки чанков/ассетов без GC и без кучи;
  отображение в области Arena и детерминированно снимается при `close()`.

## 5. Проверенный стресс-тест (`aetherium-cli entitysim`)

10 000 data-oriented сущностей, продвигаемых параллельно на 250 виртуальных потоках/тик в
течение 200 тиков:

```text
сущностей 10 000 · off-heap 480 000 байт (zero GC) · 200 тиков · 250 vthreads/тик
2 000 000 обновлений за ~111 мс → ~18 000 000 обновлений/сек · самый медленный тик 25.9 мс (<50 мс)
escapes=0 · взаимоблокировки=нет (все тики на Sync-барьере) · несоответствий=0 · @AetheriumAsyncTick DX OK
РЕЗУЛЬТАТ: PASS ✓
```

Корректность проверена точно (каждая сущность продвинулась на `ticks × velocity`), доказывая,
что параллельные обновления свободны от гонок благодаря непересекающимся off-heap срезам.
