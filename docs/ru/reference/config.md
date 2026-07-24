# ConfigStore — типизированная конфигурация (справочник)

`aetherium-config` предоставляет `ConfigStore<T>`, чтобы ни один мод не переписывал заново загрузку JSON,
валидацию, атомарную запись и горячую перезагрузку. Построен на устойчивом (по глубине/размеру) `TreeNode`
(из `aetherium-network`); внешней JSON-библиотеки нет.

## API

```java
public final class ConfigStore<T> implements AutoCloseable {
    interface Codec<T> { TreeNode toTree(T v); T fromTree(TreeNode t); }

    static <T> ConfigStore<T> open(Path file, Codec<T> codec, T defaults); // пишет defaults, если файла нет
    T get();
    void set(T value);            // нормализация + атомарная запись
    void save();                  // атомарная запись текущего значения
    void reload();                // перечитать, нормализовать, уведомить слушателей
    ConfigStore<T> validate(UnaryOperator<T> normalizer);  // ограничение/заполнение при каждой загрузке
    ConfigStore<T> onReload(Consumer<T> listener);
    ConfigStore<T> watch();       // горячая перезагрузка через WatchService (окно 80 мс)
    void close();
}
```

## Поведение

| Аспект | Гарантия |
|---|---|
| **Формат** | форматированный JSON с сортировкой ключей через `TreeJson` (редактируется человеком) |
| **Атомарность** | запись в `.tmp`-сосед, затем `ATOMIC_MOVE` — сбой посреди записи не обрезает файл |
| **Устойчивость** | `TreeJson.parse` — ограниченный парсер рекурсивного спуска: макс. глубина, без мусора в хвосте, битый ввод → `AetheriumException` |
| **Горячая перезагрузка** | `watch()` запускает демон `WatchService`; правка админа перечитывает, ревалидирует и уведомляет слушателей `onReload` |
| **Локализация** | битая ручная правка локализуется — остаётся последнее валидное значение, наблюдатель выживает |
| **Валидация** | `validate(normalizer)` ограничивает/заполняет каждое загруженное значение (сразу применяется к текущему) |

## Пример

```java
record FactionConfig(String name, int maxMembers, double taxRate) {}

ConfigStore<FactionConfig> store = ConfigStore.open(
        dir.resolve("faction.json"), FACTION_CODEC, new FactionConfig("Iron Vanguard", 20, 0.05))
    .validate(c -> new FactionConfig(c.name(), Math.max(1, Math.min(50, c.maxMembers())), c.taxRate()))
    .onReload(c -> LOG.info("конфиг перезагружен: {}", c))
    .watch();

FactionConfig cfg = store.get();
```

Запустите `aetherium config` для сквозного self-test (defaults, round-trip, валидация, горячая перезагрузка,
локализация битой правки).
