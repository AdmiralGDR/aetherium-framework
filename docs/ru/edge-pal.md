# Edge — слой абстракции платформы (PAL)

*Русский. Английский оригинал: [`../en/edge-pal.md`](../en/edge-pal.md).*

`aetherium-edge` — стандартизированный мост, позволяющий загрузчик-агностичному моду Aetherium читать
и возвращать результаты в живую игру **без единого импорта типов NeoForge/Fabric/Minecraft**. Он
закрывает «разрыв кромки»: Aetherium считает вне кучи и параллельно, а PAL — это путь возврата
результатов в ваниль.

## 1. Контракт (чистый, в `aetherium-edge`)

```java
PlatformBridge bridge = Platform.bridge();      // резолвится через ServiceLoader; не null
bridge.platformName();                           // "neoforge" | "fabric" | "none"
bridge.isGameAvailable();                        // false вне запущенной игры

EntityAccess e = bridge.entities();
e.byId(uuid);                                    // Optional<EntityHandle>
e.forEach(h -> h.addVelocity(0, 0.1, 0));        // обход загруженных сущностей
e.count();

bridge.events().onServerTickEnd(() -> { ... });  // после Sync-барьера Aetherium
bridge.events().onEntityLoad(h -> { ... });

EntityHandle h = ...;                             // id(), x()/y()/z(), setPosition(...), addVelocity(...)
```

Интерфейсы — `PlatformBridge`, `EntityAccess`, `EntityHandle`, `EdgeEvents` — не содержат **ни одного**
игрового типа. `Platform` резолвит активный `PlatformBridge` через `ServiceLoader`; если его нет
(юнит-тест, CLI, чистые вычисления), возвращается безопасный **no-op мост** (`platform="none"`,
`count()==0`, хуки игнорируются), так что код мода не падает с NPE вне платформы.

## 2. Реализация (нечистая, в `aetherium-loader`)

По правилу разделения реализацию даёт загрузчик — единственный модуль, знающий NeoForge:

- `NeoForgePlatformBridge` (`implements PlatformBridge`), зарегистрирован через
  `META-INF/services/org.aetherium.edge.PlatformBridge`.
- `NeoForgeEntityHandle` оборачивает `net.minecraft.world.entity.Entity` (чтение `getX/Y/Z/getUUID`;
  запись `setPos` / `setDeltaMovement`).
- `NeoForgePlatformEvents` подписывается на шину NeoForge (`ServerStartingEvent`/`ServerStoppingEvent`
  захватывают сервер; `ServerTickEvent.Post` рассылает `onServerTickEnd`; `EntityJoinLevelEvent` —
  `onEntityLoad`), регистрируется на `NeoForge.EVENT_BUS` точкой входа `@Mod`.

Доступ к сущностям обходит `MinecraftServer.getAllLevels()` через стабильные `getAllEntities()` /
`getEntity(UUID)`. Вся диспетчеризация хуков — negative-trust: бросивший хук мода локализуется и не
ломает тик сервера.

## 3. Зачем это нужно

Мод, скомпилированный против `aetherium-core` + `aetherium-edge`, **переносим между загрузчиками**: тот
же jar работает везде, где зарегистрирован `PlatformBridge`. Чтобы поддержать новый загрузчик (напр.
Fabric), добавьте один модуль, реализующий `PlatformBridge`, с файлом `ServiceLoader` — без
перекомпиляции мода. Edge остаётся чистым; игровые типы импортирует только модуль-кромка загрузчика.
