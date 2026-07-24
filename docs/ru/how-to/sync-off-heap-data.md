# Как синхронизировать off-heap данные сущностей по сети

*Русский. English version: [`../../en/how-to/sync-off-heap-data.md`](../../en/how-to/sync-off-heap-data.md).*

*Практическое руководство. Устройство этих API описано в пояснениях
[performance](../explanation/performance.md), [delta-sync](../explanation/delta-sync.md) и
[network](../explanation/network.md).*

## Храните сущности off-heap

Определите схему один раз и выделите непрерывную арену без GC:

```java
StructLayout layout = StructLayout.builder()
        .doubles("x").doubles("y").doubles("z")
        .floats("health")
        .build();

try (StructArena entities = StructArena.allocate(layout, 10_000)) {
    StructField x = layout.field("x");
    entities.setDouble(42, x, 128.5);        // O(1), с проверкой границ, без аллокаций
}
```

Всегда выделяйте в try-with-resources (или закрывайте явно): вся арена освобождается детерминированно
при `close()` — это контракт нулевых утечек, который доказывает `aetherium ffmaudit`.

## Передавайте только изменённое (плоский delta-sync)

Отслеживайте «грязные» строки по теневой копии и передавайте только их:

```java
StructArenaDelta delta = new StructArenaDelta(entities, rowCount);
DirtyBitmap dirty = delta.computeDirty(entities, rowCount);   // 1 бит на строку

StructArenaDeltaPacket packet = new StructArenaDeltaPacket(entities, rowCount, dirty);
StructArenaDeltaCodec.encode(packet, sink);                    // строки без копирования
```

На клиенте `StructArenaDeltaCodec.decode` накладывает ровно грязные строки в локальную арену —
байт-в-байт, с типичной экономией >99 % против полной синхронизации (проверьте: `aetherium delta`).

## Синхронизация иерархических данных (NBT/JSON-подобных)

Для древовидного состояния (данные фракций, конфиги машин) используйте `TreeCodec` вместо плоского
кодека:

```java
TreeNode tree = Tree.object()
        .put("name", "AetherFaction")
        .put("score", 9001L)
        .put("members", Tree.list(Tree.of("alice"), Tree.of("bob")))
        .build();

TreeSyncCodec.encode(new TreeSyncPacket(tree), sink);
```

Декодер укреплён (лимиты глубины/элементов/байтов), поэтому враждебные пакеты падают чисто.
Проверьте: `aetherium tree`.

## Журналируйте тики для time-travel отладки (опционально)

Во время разработки оберните цикл тиков в Time-Travel Debugger — дельта каждого тика журналируется в
ограниченный кольцевой буфер, а крах замораживает сцену для байт-точной перемотки:

```java
TtdEngine ttd = new TtdEngine(entities, 64);
ttd.tick((arena, tick) -> { /* ваша физика */ });
// после сбоя: ttd.rewind(3).getDouble(entityIndex, xField)
```

См. [пояснение движка ACID](../explanation/acid.md) и `aetherium ttd`.
