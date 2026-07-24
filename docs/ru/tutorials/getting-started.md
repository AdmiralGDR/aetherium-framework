# Начало работы: ваш первый мод на Aetherium

*Русский. English version: [`../../en/tutorials/getting-started.md`](../../en/tutorials/getting-started.md).*

*Обучающий учебник. Примерно за десять минут вы создадите каркас мод-проекта, добавите собственный
блок без единой строки шаблонного кода и проверите фреймворк end-to-end — предварительных знаний об
Aetherium не требуется.*

## Что понадобится

- Linux x86-64, **Java 21 (GraalVM)** в `PATH`.
- Клон этого репозитория, собранный один раз: `./gradlew build`.

## Шаг 1 — Соберите CLI

```bash
./gradlew :aetherium-cli:installDist
alias aetherium=$PWD/aetherium-cli/build/install/aetherium-cli/bin/aetherium-cli
```

Проверьте, что машина готова:

```bash
aetherium doctor
```

Вы должны увидеть `DIAGNOSIS: READY`. Если строка говорит `WARN`, подсказка рядом укажет, какой флаг
JVM добавить — учебнику ничего не мешает.

## Шаг 2 — Создайте каркас проекта

```bash
aetherium init my-first-mod
cd my-first-mod
```

`init` сгенерировал полный, собираемый Gradle-проект: тулчейн закреплён на Java 21, подключён
`--enable-preview`, API Aetherium на classpath, пример класса мода, метаданные NeoForge и лицензия
AGPL-3.0. Вы не написали ничего из этого.

## Шаг 3 — Добавьте блок (одна аннотация, никакого JSON)

Создайте `src/main/java/.../SteelBlock.java`:

```java
@AetheriumBlock(name = "steel_block", hardness = 5.0f, requiresTool = true)
public final class SteelBlock {
}
```

Эта единственная аннотация — вся фича. На этапе компиляции процессор аннотаций генерирует blockstate,
модели блока и предмета, таблицу лута и языковую запись, а также записывает блок в индекс контента; во
время загрузки фреймворк регистрирует блок и его предмет на любом работающем загрузчике. Ни вызова
`Registry`, ни JSON-файла, ни подписчика на события.

## Шаг 4 — Соберите и посмотрите внутрь

```bash
./gradlew build
unzip -l build/libs/my-first-mod.jar | grep -E 'steel|index'
```

Вы увидите сгенерированные ассеты (`assets/…/steel_block.json`, таблица лута, lang) и
`META-INF/aetherium/content.index`, автоматически упакованные в jar.

## Шаг 5 — Проверьте сам движок

Вернувшись в репозиторий фреймворка, запустите самодоказывающие команды:

```bash
aetherium selftest   # движок байт-кода: трансформация → верификация → загрузка → вызов
aetherium inject     # текучий инжектор + откат песочницы
aetherium acid       # транзакционные хуки: падающий мод откатывается целиком
```

Каждая команда печатает `RESULT: PASS ✓` — те же проверки выполняются в `./gradlew check`.

## Куда дальше

- **Решить задачу** → [практические руководства](../how-to/inject-a-hook.md): внедрить хук,
  синхронизировать off-heap данные.
- **Посмотреть справку** → [справочник](../reference/cli.md): каждая команда CLI, аннотация и
  настройка сборки.
- **Понять устройство** → [пояснения](../explanation/bytecode-engine.md): O(1)-диспетчеризация,
  движок ACID, компиляция SPIR-V.
