# Content и DataGen — декларативные реестры, ноль JSON

> Модули: [`aetherium-content`](../../../aetherium-content) (аннотации + процессор),
> [`aetherium-datagen`](../../../aetherium-datagen) (чистый генератор ассетов),
> связаны через `aetherium-loader`.

Добавление базового блока в Minecraft обычно стоит `DeferredRegister`, рукописного `BlockItem` и
**четырёх с лишним JSON-файлов** (модель блока, модель предмета, blockstate, loot-таблица) плюс
запись `lang`. Aetherium устраняет всё это. **Вы пишете одну аннотацию. Фреймворк делает 100% работы
по реестру и JSON.**

## Весь API

```java
@AetheriumBlock(name = "steel_block", hardness = 5.0f, resistance = 6.0f, requiresTool = true)
public final class AetheriumSteelBlock {}
```

```java
@AetheriumItem(name = "steel_ingot", maxStackSize = 64)
public final class SteelIngot {}
```

Это весь исходный код. Тело класса пустое, и **импорта `net.minecraft` нет** — мод остаётся
независимым от загрузчика.

### `@AetheriumBlock`

| Элемент        | По умолчанию     | Значение |
|----------------|------------------|----------|
| `name`         | *(обязательно)*  | Путь в реестре, напр. `steel_block`. |
| `modId`        | `""`             | Пространство имён. Пусто → опция сборки `aetherium.modId` (Gradle-плагин подставляет mod id), иначе `aetherium`. |
| `hardness`     | `1.0`            | Время добычи/разрушения. |
| `resistance`   | `-1.0`           | Взрывоустойчивость. Отрицательное → равно `hardness`. |
| `requiresTool` | `false`          | Требовать правильный инструмент для выпадения. |
| `dropSelf`     | `true`           | Генерировать loot-таблицу самовыпадения. |
| `displayName`  | `""`             | Метка `lang`. Пусто → выводится из `name` (`steel_block` → `Steel Block`). |

### `@AetheriumItem`

| Элемент        | По умолчанию     | Значение |
|----------------|------------------|----------|
| `name`         | *(обязательно)*  | Путь в реестре. |
| `modId`        | `""`             | Пространство имён (то же разрешение). |
| `maxStackSize` | `64`             | Размер стака. |
| `displayName`  | `""`             | Метка `lang`. |

## Что генерируется (JSON, который вы не пишете)

На **этапе компиляции** `AetheriumContentProcessor` (обычный процессор
`javax.annotation.processing`) вызывает чистый движок DataGen и пишет — для одного
`@AetheriumBlock(name="steel_block")` в моде `aetherium` — прямо в скомпилированный вывод (поэтому
они попадают в jar без дополнительной настройки Gradle):

```
assets/aetherium/models/block/steel_block.json     # { parent: block/cube_all, textures.all: …:block/steel_block }
assets/aetherium/models/item/steel_block.json      # { parent: …:block/steel_block }
assets/aetherium/blockstates/steel_block.json      # { variants: { "": { model: …:block/steel_block } } }
data/aetherium/loot_table/blocks/steel_block.json  # пул самовыпадения (survives_explosion)
assets/aetherium/lang/en_us.json                   # { "block.aetherium.steel_block": "Steel Block" }
```

> **Замечание о пути в 1.21.** Minecraft 1.21 переименовал папку дата-пака `loot_tables` → в
> единственное число `loot_table`. Генератор ориентирован на базис 1.21.1 и пишет форму в
> единственном числе.

Записи `lang` для каждого объявления объединяются в один `en_us.json` на mod id.

## Как регистрируется (без `DeferredRegister`, без шаблона `BlockItem`)

Процессор также пишет небольшой машиночитаемый индекс `META-INF/aetherium/content.index`
(по одной записи с разделителем «|» на объявление). На этапе загрузки `AetheriumContentRegistrar` из
`aetherium-loader` читает этот индекс из classpath и на `RegisterEvent` NeoForge:

1. **Фаза BLOCK** — строит каждый `Block` из `BlockBehaviour.Properties.of().strength(hardness,
   resistance)` (плюс `requiresCorrectToolForDrops()` при `requiresTool`) и регистрирует его.
2. **Фаза ITEM** — **автоматически оборачивает каждый блок в `BlockItem`** и регистрирует отдельные
   `Item`.

Ошибки изолируются по записи, поэтому одно плохое объявление не прерывает регистрацию. Это
единственное место, знающее и модель контента Aetherium, *и* реестры Minecraft.

## Строгая чистота

`aetherium-datagen` — это **чистый Java-генератор файлов**: без зависимости
`net.minecraft`/`net.neoforged` и без внешней JSON-библиотеки, работает целиком на этапе сборки —
**не** через `GatherDataEvent` NeoForge. Рантайм-индекс (`ContentIndex`) — единственная передача в
игру; он содержит обычные примитивы/строки, поэтому граница «без Minecraft в datagen» не нарушается.

## Нулевая конфигурация с Gradle-плагином

Применение [Gradle-плагина Aetherium](gradle-plugin.md) подключает всё автоматически — добавляет
зависимость контента, регистрирует аннотационный процессор и подставляет
`-Aaetherium.modId=<ваш mod id>`, так что `@AetheriumBlock(name = "…")` не требует `modId`. Запустите
`aetheriumBundle`, и сгенерированный JSON будет упакован рядом с вашими скомпилированными классами.
Без настройки сборки, без JSON, без кода реестра.

## Фаза 17 — поведения контента (авто-BlockEntity + тикинг)

`@AetheriumBlock`/`@AetheriumItem` теперь принимают класс `behavior`. Если он реализует
`AetheriumMachineLogic` (`tick(MachineContext)` + `onPlaced`/`onRemoved`), процессор записывает его в
`behaviors.index` (рядом с индексом контента), и загрузчик авто-регистрирует тикающую `BlockEntity` — без
шаблона `BlockEntityType`/тикера/NBT. `MachineContext`/`MachineState` чистые (без типов Minecraft),
поэтому логика машины тестируется офлайн. Доказательство: `aetherium behavior`.

## Текстуры блоков ()

С Фазы 24 сгенерированная модель блока ссылается на вашу текстуру (`"<modid>:block/<name>"`), а не подставляет
ванильную — фреймворк контента не должен угадывать арт мода. Цена в том, что блок без
`assets/<modid>/textures/block/<name>.png` рисуется чёрно-фиолетовой «шашечкой» отсутствующей текстуры: зелёная
сборка, которая выглядит сломанной. Поэтому процессор аннотаций теперь **предупреждает на компиляции**, называя
точный ожидаемый путь:

```
warning: Aetherium: block 'mymod:reactor' expects a texture at assets/mymod/textures/block/reactor.png
         — ship that 16x16 PNG or the block renders as the missing-texture checkerboard.
```

Проверка надёжна при использовании Gradle-плагина Aetherium (он сообщает процессору, где лежат ваши ресурсы, —
собственный вывод процессора никогда не содержит `src/main/resources`); положите PNG — и предупреждение
исчезнет. Иконки предметов работают так же (`assets/<modid>/textures/item/<name>.png`).

## Языковые файлы для каждого языка ()

Название вкладки креатива берётся из сгенерированного ключа `itemGroup.<modid>`, который процессор пишет в
`en_us.json`. Если ваш мод поставляет другие языки, задача плагина `mergeAetheriumLang` теперь **предупреждает**,
когда сгенерированный ключ есть в `en_us`, но отсутствует в другом поставляемом языке — иначе не-англоязычный
игрок молча видит английское название. Добавьте ключ в каждый `assets/<modid>/lang/<lang>.json` — и
предупреждение исчезнет.

## Дополнения API машин ()

- **`MachineState.hasLong`/`hasString` + `removeLong`/`removeString`.** «Отсутствует» теперь отличается от
  «ноль» — машина различает «не занято» и «принадлежит фракции 0». `getLong(key, fallback)` возвращает
  fallback только когда ключ отсутствует; `removeLong` снова делает его отсутствующим (удаление сохраняется
  через NBT).
- **`MachineContext.level()` → `Optional<LevelContext>`.** Машина может читать и менять мир вокруг —
  соседние блоки, загруженность чанка, блок-сущности — не выводя заново позицию из `x()`/`y()`/`z()`. Загрузчик
  заполняет это из уровня блок-сущности; в чистом юнит-тесте — пусто.
- **Константы `Keys` для клавиш.** `AetheriumUi.registerKeybind(key, category, Keys.G, action)` заменяет
  магическое `71` — `org.aetherium.ui.Keys` — держатель кодов GLFW без зависимостей (буквы, цифры, F-клавиши,
  стрелки, клавиши редактирования), поэтому опечатка — ошибка компиляции, а не тихая неверная привязка.

### Дополнения 

- **`MachineState.clear()`** сбрасывает машину в заводское состояние (обе карты). **`longKeys()`/`stringKeys()`**
  возвращают **снимок** `Set<String>`, по которому можно итерироваться, вызывая `removeLong`/`removeString`. А
  `longs()`/`strings()` документированы как возвращающие **неизменяемые копии** — их можно хранить и обходить, это
  никогда не живое представление, так что защитное копирование не нужно.
