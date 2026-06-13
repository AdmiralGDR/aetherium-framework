# Интеграция с игрой (NeoForge)

*Русский. Английская версия: [`../en/game-integration.md`](../en/game-integration.md).*

Как Aetherium соединяется с реальной средой Minecraft/NeoForge — и как при этом остальной
фреймворк остаётся полностью свободным от игровых типов.

## Единственный «нечистый» модуль

Только `aetherium-loader` ссылается на NeoForge или Minecraft. Он применяет ModDevGradle
(см. [`build-system.md`](build-system.md) ) и содержит ровно один класс, импортирующий
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

## Что проверено и что дальше

- **Проверено здесь:** загрузчик компилируется против декомпилированного classpath MC
  1.21.1 + NeoForge; точка входа `@Mod`, установка таблицы диспетчеризации и обвязка
  ServiceLoader компилируются и разрешаются; `runClient` существует; сборка всего проекта
  зелёная; разделение ответственности соблюдено. (GUI намеренно не запускается в этой среде.)
- **Следующий шаг:** зарегистрировать `ITransformationService` NeoForge, чтобы классы модов
  прогонялись через `BytecodeEngine` во время загрузки классов — это превратит вызовы API
  тест-мода в связанные точки `invokedynamic`, которые уже обеспечивает установленная
  `DispatchTable`. Сам механизм диспетчеризации уже доказан end-to-end через
  `aetherium-cli selftest`.