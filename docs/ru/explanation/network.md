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

## Фаза 29 — направленная матрица (serverbound) + модель сторон

До Фазы 28 сеть была **только на приём и только клиентская**: `NetworkRegistry.register` подключал лишь
`playToClient`, а API отправки не было. Экран настроек мода работал в одиночной игре (тот же процесс пишет
конфиг, который читает сервер), но **на выделенном сервере не делал ничего** — оператор правил конфиг своего
клиента. попросил недостающее направление; здесь поставляется вся матрица.

Так как серверный обработчик получает `PlayerHandle` отправителя (тип `aetherium-edge`), а
`aetherium-network` лежит *ниже* edge, направленный фасад (`Network`) живёт в **`aetherium-edge`**, а
`aetherium-network` остаётся чистым. Направления:

| Вызов | Направление | Где |
|---|---|---|
| `NetworkRegistry.register(codec, handler)` | сервер → клиент (приём) | клиент |
| `Network.registerServerbound(codec, handler)` | клиент → сервер (приём) | сервер |
| `Network.sendToServer(payload)` | клиент → сервер (отправка) | клиент |
| `Network.sendToClient(target, payload)` / `sendToAllClients(payload)` | сервер → клиент(ы) | сервер |
| `Network.relayToClient(target, payload)` | клиент ↔ клиент (релей через сервер) | сервер |

```java
// на сервере: принять правку админа, с проверкой права отправителя. Отправитель берётся из соединения,
// а не из пакета — подделать нельзя.
Network.registerServerbound(new SetRuleCodec(), (PlayerHandle sender, SetRule p) -> {
    if (sender.hasPermission(2)) rules.apply(p);
});

// на клиенте: экран настроек шлёт правку на сервер, к которому подключён.
Network.sendToServer(new SetRule("maxMembers", 12));
```

Отправка идёт через `PayloadTransport`, который устанавливает загрузчик (`PacketDistributor` NeoForge); вне
игры это no-op, поэтому `Network.send*` не падает в тесте/инструменте. Мост `AetheriumNetworkBridge` теперь
подключает **оба** направления (`playToClient` и `playToServer`) и находит целевого `ServerPlayer` по UUID.

### Безопасно по умолчанию (защита)

Серверный канал — новая поверхность атаки: злонамеренный клиент может слать админ-пакеты. Фреймворк делает
канал безопасным **без кода автора**: идентичность отправителя — из соединения (`IPayloadContext.player()`),
не из пакета; **лимит размера** отбрасывает большой пакет *до декодирования* (`Network.withinSizeLimit`,
по умолчанию 32 КиБ, настраивается); **лимит частоты** — токен-бакет на отправителя/канал (`ServerboundGuard`)
отбрасывает флуд до обработчика (`Network.deliver` вернёт `false`).

Щит покрывает и классы кодеков: литерал `PayloadCodec.channelId()` — это строка, возвращаемая методом, поэтому
шифрование строк её прячет — `aetherium harden-check` на защищённом jar показывает **ноль** читаемых имён
каналов. Доказательство всей матрицы офлайн: `aetherium network`.

### Модель сторон — both-side, server-side, client-side

`@AetheriumInit(side = …)` (с `org.aetherium.core.mod.Side`: `BOTH` по умолчанию, `SERVER`, `CLIENT`) даёт
автору объявить, где выполняется init, а сгенерированная точка входа гейтит вызов через `Side.activeOn`:
`CLIENT`-init **никогда не выполняется на выделенном сервере** (клиентский мод не уронит сервер), а
`SERVER`/`BOTH` выполняются там, где безопасно (серверная логика нормальна на встроенном сервере клиента).
Загрузчик сообщает физическую сторону JVM через `AetheriumContext.side()`. Так мод пишется both-side,
server-side или client-side без dist-бойлерплейта; client↔client — это хоп клиент → serverbound →
`relayToClient`.

## Фаза 17 — иерархическая синхронизация (`TreeCodec`)

Плоский `StructArenaDeltaCodec` идеален для тысяч однородных off-heap сущностей, но геймплейное состояние
(составы фракций, деревья навыков, графы квестов) нерегулярно и вложено. `TreeNode` — небольшое
размеченное объединение (object/list/string/long/double/bool/bytes), строится через `Tree`, а `TreeCodec`
сериализует/десериализует его поверх **того же** SPI `PayloadSink`/`PayloadSource`, что и плоский путь
(с новыми default-методами `writeBytes`/`readBytes`). `TreeSyncPacket`/`TreeSyncCodec` отправляют его как
`NetworkPayload`. Декодирование укреплено — лимит глубины и лимиты размера. Доказательство: `aetherium tree`.
