# Интеграция с игрой (NeoForge)

*Русский. Английская версия: [`../en/game-integration.md`](../../en/explanation/game-integration.md).*

Как Aetherium соединяется с реальной средой Minecraft/NeoForge — и как при этом остальной
фреймворк остаётся полностью свободным от игровых типов.

## Единственный «нечистый» модуль

Только `aetherium-loader` ссылается на NeoForge или Minecraft. Он применяет ModDevGradle
(см. [`build-system.md`](../reference/build-system.md) ) и содержит ровно один класс, импортирующий
`net.neoforged.*`: точку входа `@Mod`. `core`, `bytecode`, `native` и тест-мод проверены
grep — в них **ноль** ссылок `net.neoforged`/`net.minecraft`.

## Точка входа `@Mod`

`org.aetherium.loader.AetheriumNeoForgeEntrypoint`:

```java
@Mod(AetheriumNeoForgeEntrypoint.MOD_ID)              // MOD_ID = "aetherium"
public final class AetheriumNeoForgeEntrypoint {
    public AetheriumNeoForgeEntrypoint(IEventBus modEventBus) {
        modEventBus.addListener(this::onConstruct);   // самая ранняя фаза
    }
    private void onConstruct(FMLConstructModEvent event) {
        PreFlightCheck.run();                 // 1. самопроверка (тотальна, не бросает)
        DispatchBootstrap.installDefaultTable();       // 2. установка таблицы invokedynamic
        initializeAetheriumMods();                     // 3. поиск через ServiceLoader и init
    }
}
```

Три шага по порядку срабатывают на `FMLConstructModEvent` — самой ранней фазе жизненного
цикла мода — поэтому фреймворк готов до конструирования обычных модов:

1. **Pre-Flight Check** — выполняет самопроверку ASM + native и разрешает уровень
   возможностей. Тотальна и не бросает; при сбое деградирует, запуск продолжается.
2. **Установка таблицы диспетчеризации** — `DispatchBootstrap` строит `SymbolManifest` и
   устанавливает `MethodHandle[]` в `DispatchTable` **до** того, как любой преобразованный
   класс мода выполнит пониженный вызов. Это точка подключения, нужная понижению в
   `invokedynamic`.
3. **Обнаружение модов** — `ServiceLoader.load(AetheriumMod.class)` находит каждый мод
   Aetherium и вызывает `onInitialize(AetheriumContext)`. Один сбойный мод перехватывается
   и пропускается.

## Независимый от загрузчика SPI мода

Моды ориентируются на два чистых типа `aetherium-core` — никогда на NeoForge:

- `AetheriumMod` — `void onInitialize(AetheriumContext)`, регистрируется через
  `META-INF/services/org.aetherium.core.mod.AetheriumMod`.
- `AetheriumContext` — `log(String)` + `computeTier()`. Загрузчик предоставляет реализацию
  (логирование через SLF4J NeoForge).

## Тест-мод

`aetherium-testmod` (`HelloAetheriumMod`) зависит **только** от `aetherium-core`. Реализует
`AetheriumMod` и делает один вызов API Aetherium (`context.log(...)`) при инициализации.
Его jar несёт регистрацию ServiceLoader, поэтому загрузчик находит и запускает его при
старте игры. Он не импортирует ничего из NeoForge или Minecraft — доказательство «скомпилируй
один раз — запускай на любом загрузчике».

## Перехват классов во время выполнения (недостающее звено — подключено)

Классы модов преобразуются во время загрузки классов через ModLauncher, тем же разделением,
что использует Mixin:

- **`AetheriumTransformationService`** (`ITransformationService`) — обнаруживается через
  `META-INF/services/cpw.mods.modlauncher.api.ITransformationService` на bootstrap загрузки
  классов JVM. Это наше зарегистрированное присутствие в конвейере. `ITransformer`
  ModLauncher сопоставляет только *точные* имена классов, поэтому `transformers()` пуст, а
  реальная работа делегируется:
- **`AetheriumLaunchPlugin`** (`ILaunchPluginService`) — ModLauncher предлагает ему *каждый*
  загружаемый класс. `handlesClass(Type, isEmpty)` — **барьер производительности**: дешёвая
  проверка префикса (`AetheriumNamespaces`), возвращающая пустой набор фаз (пропуск) для
  `net.minecraft`, `net.neoforged`, `cpw.mods`, JDK и *собственных* пакетов фреймворка
  Aetherium, и `EnumSet.of(AFTER)` только для пространств имён модов Aetherium (с тест-модом
  по умолчанию, расширяемо через `-Daetherium.transform.packages=a.b,c.d`). Для принятых
  классов `processClass` сериализует узел в байты, делегирует чистому `BytecodeEngine`
  (понижает статические вызовы API в `invokedynamic`, верифицирует и возвращает **исходные**
  байты при любом сбое) и переписывает узел только при изменении байтов.

**Разделение соблюдено:** только `aetherium-loader` касается ModLauncher/ASM;
`aetherium-bytecode` не импортирует ни то, ни другое. **Откат:** поскольку `processClass`
делегирует движку (он тотален), сбойная трансформация возвращает исходный класс, и игра
продолжает загрузку.

### Проверено (эквивалент `runClient`, GUI не запускается)

- Загрузчик компилируется против декомпилированного classpath MC 1.21.1 + NeoForge; оба
  класса сервисов реализуют интерфейсы ModLauncher (подтверждено `javap`); `runClient` есть.
- **Обнаружение:** `ServiceLoader` (механизм, используемый ModLauncher на bootstrap) находит
  и `AetheriumTransformationService`, и `AetheriumLaunchPlugin` из собранных артефактов.
- **Фильтр:** `handlesClass` возвращает `[]` для `net/minecraft`, `net/neoforged` и
  собственных классов загрузчика; `[AFTER]` для `org/aetherium/testmod/*`.
- **End-to-end трансформация:** прогон класса `org/aetherium/testmod/Demo`, вызывающего
  статический фасад API, через `processClass` переписывает его `INVOKESTATIC` в
  `invokedynamic` (до: 1 static / 0 indy → после: 0 static / 1 indy), обеспеченный
  `DispatchTable`, которую точка входа устанавливает на `FMLConstructModEvent`.
- Сборка всего проекта зелёная; чистые модули свободны от Minecraft/NeoForge.