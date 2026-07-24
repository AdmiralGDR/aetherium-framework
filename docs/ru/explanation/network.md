# Сеть — пакеты без аллокаций (`aetherium-network`)

*Русский. Английский оригинал: [`../en/network.md`](../../en/explanation/network.md). Лицензия: AGPL-3.0-or-later.*

Загрузчик-агностичный SPI кастомных пакетов. Содержит **ноль** типов `net.minecraft`/`net.neoforged`;
загрузчик (`AetheriumNetworkBridge`) сопоставляет каждый канал с `PayloadRegistrar` NeoForge.

## 1. Поверхность API

| Тип | Роль |
|---|---|
| `NetworkPayload` | Маркер: `String channelId()` (`"namespace:path"`). |
| `PayloadSink` / `PayloadSource` | Запись/чтение поверх буфера платформы. Ключевые примитивы — `writeSegment(MemorySegment, long)` / `readSegment(MemorySegment, long)` — **массовое копирование off-heap, без `byte[]`, без боксинга**. |
| `PayloadCodec<T>` | `encode(T, PayloadSink)` / `decode(PayloadSource)`; адаптируется к `StreamCodec` платформы. |
| `ClientPayloadHandler<T>` | `handle(T)` на приёмной стороне (загрузчик выполняет на главном потоке). |
| `NetworkRegistry` | `register(codec, handler)` + `entries()` — чистые данные, которые мостит загрузчик. |
| `StructArenaSyncPacket` | Передаёт `rowCount` подряд идущих строк off-heap `StructArena`. Хранит ссылку, не копию. |
| `StructArenaSyncCodec` | `[int rowCount][rowCount × stride байт]`; декодирует прямо в заранее выделенную клиентскую арену. |

## 2. Путь без аллокаций

Сервер считает в `StructArena` (off-heap). `StructArenaSyncCodec.encode` пишет число строк, затем
массово копирует `rowCount × stride` байт прямо из `MemorySegment` арены в сетевой буфер
(`PayloadSink.writeSegment`). На клиенте `decode` читает эти байты напрямую в **заранее выделенную
арену-зеркало** (`PayloadSource.readSegment`) — без аллокаций на пакет, без промежуточного массива в
куче, без объектов на строку. Off-heap сервера → провод → off-heap клиента.

## 3. Использование

```java
StructArena server = StructArena.allocate(layout, n);          // на сервере, off-heap
StructArenaSyncPacket packet = new StructArenaSyncPacket(server, n);

StructArena clientMirror = StructArena.allocate(layout, n);    // выделяется один раз при старте
NetworkRegistry.register(new StructArenaSyncCodec(clientMirror),
        received -> applyToWorld(received.arena(), received.rowCount()));
```

Связывание с платформой (`event.registrar("1").optional()` → `playToClient`) делает мост загрузчика.

## Фаза 17 — иерархическая синхронизация (`TreeCodec`)

Плоский `StructArenaDeltaCodec` идеален для тысяч однородных off-heap сущностей, но геймплейное состояние
(составы фракций, деревья навыков, графы квестов) нерегулярно и вложено. `TreeNode` — небольшое
размеченное объединение (object/list/string/long/double/bool/bytes), строится через `Tree`, а `TreeCodec`
сериализует/десериализует его поверх **того же** SPI `PayloadSink`/`PayloadSource`, что и плоский путь
(с новыми default-методами `writeBytes`/`readBytes`). `TreeSyncPacket`/`TreeSyncCodec` отправляют его как
`NetworkPayload`. Декодирование укреплено — лимит глубины и лимиты размера. Доказательство: `aetherium tree`.
