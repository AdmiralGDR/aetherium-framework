# CLI и инструменты разработчика

*Русский. Английская версия: [`../en/cli.md`](../en/cli.md).*

CLI `aetherium` (`aetherium-cli`) — основной опыт разработчика: сводит настройку
мод-проекта к нулю шаблонного кода и предоставляет инструменты статического анализа и
валидации.

## Запуск

После сборки создаётся устанавливаемый дистрибутив:

```bash
./gradlew :aetherium-cli:installDist
aetherium-cli/build/install/aetherium-cli/bin/aetherium-cli --help
# или во время разработки:
./gradlew :aetherium-cli:run --args="<command> [args]"
```

CLI запускается с `--enable-preview` и `--enable-native-access=ALL-UNNAMED` (FFM).

## Команды

| Команда | Назначение |
|---------|------------|
| `init <name>` | Создать новый совместимый с Aetherium мод-проект (без шаблонного кода). |
| `analyze <path>` | Статически проверить `.class` / `.jar` / каталог против ограничений загрузчика. |
| `selftest` | End-to-end симуляция движка байт-кода (чтение → трансформация → верификация → загрузка → вызов). |
| `preflight` | Pre-Flight Check фреймворка (ASM + native + уровень возможностей). |
| `chaos [n]` | Стресс-тест Chaos Engineering (по умолчанию 600 имитируемых модов). |
| `--help`, `-h`, `help` | Показать меню справки. |

### `init <name>`

Генерирует готовый к сборке проект Gradle в `./<modId>/` (имя нормализуется в валидный
mod id, напр. `"My Cool Mod"` → `my-cool-mod`). Проект содержит:

- `build.gradle.kts` / `settings.gradle.kts` / `gradle.properties` — тулчейн Java 21,
  `--enable-preview`, зависимости на API-модули Aetherium.
- `src/main/java/.../<Name>Mod.java` — пример мода, уже использующего API
  `ComputePipeline`. **Разработчик не пишет JNI/FFM/ASM-обвязку.**
- `src/main/resources/META-INF/neoforge.mods.toml` — drop-in метаданные NeoForge,
  `license = "AGPL-3.0-or-later"`.
- `LICENSE` + AGPL-заголовки в файлах — сгенерированные проекты наследуют копилефт.

```bash
aetherium init my-mod
cd my-mod && ./gradlew build
```

### `analyze <path>`

Читает `.class`, `.jar` (каждый класс) или каталог классов и сообщает по каждому классу:
имя, мажорную версию class-файла, превышает ли она целевую (Java 21 = major 65) и
принимает ли его верификатор ASM. Только для чтения — ничего не исполняется и не
определяется. Код возврата `0` при чистом результате, `1` при найденных проблемах.

```bash
aetherium analyze build/libs/my-mod.jar
```

### `chaos [n]`

Запускает набор Chaos Engineering: `n` (по умолчанию 600) враждебных «модов»
загружаются одновременно на виртуальных потоках вместе с задачами злоупотребления FFM.
Утверждает, что фреймворк локализует каждый сбой (ноль escape) и JVM никогда не падает.
См. [`testsuite.md`](testsuite.md).

### `selftest` / `preflight`

`selftest` прогоняет движок байт-кода end-to-end (см. [`bytecode-engine.md`](bytecode-engine.md));
`preflight` выполняет внутренний Pre-Flight Check фреймворка и печатает выбранный уровень
возможностей (см. [`native-bridge.md`](native-bridge.md) ).
