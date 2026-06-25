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

### 1b. Block PAL — блоки, блок-сущности и уровни

Модам-оптимизаторам нужны не только сущности: они касаются **блоков, блок-сущностей и самого уровня**.
`bridge.levels()` раскрывает это без единого импорта `net.minecraft`:

```java
LevelAccess levels = bridge.levels();
LevelContext level = levels.primary().orElseThrow();      // обычный мир; или byDimension("minecraft:the_nether")
levels.forEach(l -> ...);                                  // все загруженные уровни

BlockPos pos = new BlockPos(0, 64, 0);                     // чистый value-тип (помощники offset/above/below)
if (level.isLoaded(pos)) {                                 // проверка загрузки чанка перед касанием
    BlockHandle block = level.blockAt(pos);
    block.blockId();                                       // "minecraft:stone"
    block.isAir(); block.destroySpeed();                   // типичные чтения горячего пути
    block.property("facing");                              // Optional<String> свойство состояния блока

    level.setBlock(pos, "minecraft:glowstone");            // установка по id реестра
    level.scheduleNeighborUpdate(pos);                     // распространение редстоуна / соседей
}

level.blockEntityAt(pos).ifPresent(be -> {                 // Optional<BlockEntityAccess>
    be.typeId();                                           // "minecraft:chest"
    be.readInt("fuel");                                    // OptionalInt — типизированный NBT без утечки CompoundTag
    be.writeLong("aetherium:last_tick", now);              // возврат результатов; загрузчик помечает dirty
});
```

`BlockPos`, `BlockHandle`, `BlockEntityAccess`, `LevelContext`, `LevelAccess` — все чистые: состояние
блока читается как простые строки/значения, NBT блок-сущности — небольшая типизированная поверхность
ключ/значение, координаты — неизменяемый record, поэтому ни один тип
`Block`/`BlockState`/`BlockEntity`/`Level`/`CompoundTag` не достигает кода мода. No-op мост сообщает об
отсутствии уровней (`primary()` пуст), так что Block PAL безопасно вызывать и вне платформы.

## 2. Реализация (нечистая, в `aetherium-loader`)

По правилу разделения реализацию даёт загрузчик — единственный модуль, знающий NeoForge:

- `NeoForgePlatformBridge` (`implements PlatformBridge`), зарегистрирован через
  `META-INF/services/org.aetherium.edge.PlatformBridge`.
- `NeoForgeEntityHandle` оборачивает `net.minecraft.world.entity.Entity` (чтение `getX/Y/Z/getUUID`;
  запись `setPos` / `setDeltaMovement`).
- `NeoForgePlatformEvents` подписывается на шину NeoForge (`ServerStartingEvent`/`ServerStoppingEvent`
  захватывают сервер; `ServerTickEvent.Post` рассылает `onServerTickEnd`; `EntityJoinLevelEvent` —
  `onEntityLoad`), регистрируется на `NeoForge.EVENT_BUS` точкой входа `@Mod`.
- `NeoForgeLevelContext` / `NeoForgeBlockHandle` / `NeoForgeBlockEntityAccess` реализуют Block PAL
  поверх `Level`: `getBlockState`/`getBlockEntity`/`isLoaded`, установка через `setBlockAndUpdate` (id
  реестра разбирается через `BuiltInRegistries.BLOCK`), обновления соседей через `updateNeighborsAt`, а
  NBT блок-сущности — на `saveWithoutMetadata` / `loadWithComponents` с `registryAccess()` уровня.

Доступ к сущностям и уровням обходит `MinecraftServer.getAllLevels()` через стабильные
`getAllEntities()` / `getEntity(UUID)` / `overworld()`. Вся диспетчеризация хуков — negative-trust:
бросивший хук мода локализуется и не ломает тик сервера.

## 3. Зачем это нужно

Мод, скомпилированный против `aetherium-core` + `aetherium-edge`, **переносим между загрузчиками**: тот
же jar работает везде, где зарегистрирован `PlatformBridge`. Чтобы поддержать новый загрузчик (напр.
Fabric), добавьте один модуль, реализующий `PlatformBridge`, с файлом `ServiceLoader` — без
перекомпиляции мода. Edge остаётся чистым; игровые типы импортирует только модуль-кромка загрузчика.

## Фаза 17 — геймплейный PAL (игроки, инвентарь, взаимодействия)

PAL теперь покрывает геймплей, а не только сущности/блоки: `PlayerAccess` (`PlatformBridge.players()`) и
`PlayerHandle` (имя, здоровье, чат, `inventory()`), `InventoryAccess`, адресующий предметы по
namespaced-строке (без типа `ItemStack`), и **отменяемые события взаимодействия** в `EdgeEvents` —
`onBlockInteract`, `onItemUse`, `onEntityAttack` — слушатели которых возвращают `InteractionResult`
(`PASS`/`CANCEL`); загрузчик отображает `CANCEL` на отмену нативного события. Новые методы — `default`
no-op, поэтому существующий мост продолжает компилироваться. Доказательство: `aetherium gameplay`.
