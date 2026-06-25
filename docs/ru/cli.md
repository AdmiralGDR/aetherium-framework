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
| `inject` | Самотест инжектора: отмена, DAG-порядок + семантический слиятель, откат песочницы. |
| `simd` | Сообщить ширину SIMD-полосы и проверить равенство пути Vector API скаляру. |
| `cdscache [test]` | Статус AppCDS-кэша без разбора или round-trip тест запись→переоткрытие→тёплое попадание. |
| `profile` | Проверить эфемерные JFR-зонды (ноль накладных off, JFR срабатывает on, hot-swap). |
| `security` | Проверить охраны CIA на основе возможностей (default-deny, границы FFM, рефлексия). |
| `spirv` | Скомпилировать чистое Java-ядро `@AetheriumComputeShader` в SPIR-V; доказать магию `0x07230203`. |
| `hotswap` | Проверить движок живого hot-swap (`Instrumentation.redefineClasses`) + согласование DAG вживую. |
| `wasm` | Проверить polyglot WASM-песочницу (запрет ФС/сети) + мост памяти `StructArena`. |
| `delta` | Проверить delta-sync сеть (битовая карта; передавать только изменённые строки). |
| `fuzz [n]` | Агрессивно фаззить поверхность SPIR-V + WASM (по умолчанию 10000 случаев/цель); доказать, что вход не роняет JVM/хост. См. [`fuzzer.md`](fuzzer.md). |
| `lsp [--serve]` | Запустить самотест LSP-бэкенда или отдавать LSP по stdio для IDE (`--serve`). См. [`lsp.md`](lsp.md). |
| `ui` | Проверить декларативный UI-фреймворк (flex-раскладка + отрисовка + клики). См. [`ui.md`](ui.md). |
| `gfx` | Проверить продвинутый GFX-конвейер (matrix/PoseStack/скелет/вершины). |
| `tree` | Проверить иерархическую синхронизацию `TreeCodec` (round-trip NBT/JSON-like + лимит глубины). |
| `behavior` | Проверить поведения контента (`@AetheriumMachineLogic` тикинг BlockEntity + индекс поведений). |
| `gameplay` | Проверить геймплейный PAL (доступ к игроку/инвентарю + отменяемые события взаимодействия). |
| `doctor` | Проверить готовность хоста (Java 21+, `--enable-preview`, Vector API, нативный доступ FFM, GraalWASM). |
| `entitysim [n]` | Дата-ориентированный стресс-тест сущностей (по умолчанию 10000 сущностей). |
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

### `fuzz [n]`

Запускает агрессивную фаззинг-кампанию (`n` случаев на цель, по умолчанию 10000) по
верификатору/диспетчу SPIR-V, front-end компилятора Java→SPIR-V, загрузчику `.wasm` и мосту
`StructArena`↔WASM. Любой враждебный вход обязан проявиться чистым контрактным исключением, а
не крашем JVM/хоста. Та же кампания выполняется автоматически при `./gradlew check`. См.
[`fuzzer.md`](fuzzer.md).

### `lsp [--serve]`

Без аргументов запускает самотест LSP-бэкенда (автодополнение ванильных методов, предсказание
конфликтов хуков до компиляции, обрамление JSON-RPC). С `--serve` говорит на JSON-RPC с
обрамлением `Content-Length` по stdio, чтобы подключилась IDE. См. [`lsp.md`](lsp.md).
