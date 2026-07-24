# Система сборки

*Русский. Английская версия: [`../en/build-system.md`](../../en/reference/build-system.md).*

## 1. Обзор

Aetherium использует **многопроектную сборку Gradle** (Gradle 8.8, Kotlin DSL). Wrapper
зафиксирован в репозитории, поэтому `./gradlew` поднимает одинаковую сборку везде —
глобальная установка Gradle не нужна.

```
settings.gradle.kts        граф модулей (rootProject.name = "aetherium")
build.gradle.kts           общая конфигурация для всех модулей
gradle.properties          параметры (без авто-загрузки JDK, параллельность, кэш)
gradle/libs.versions.toml  централизованный каталог версий (отказ от хардкода)
gradle/wrapper/…           зафиксированный wrapper Gradle 8.8
<module>/build.gradle.kts  специфика модуля
```

## 2. Тулчейн и preview-функции

Корневой скрипт фиксирует **тулчейн Java 21** для каждого модуля:

```kotlin
configure<JavaPluginExtension> {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}
```

**Авто-загрузка JDK отключена** (`gradle.properties`), поэтому тулчейн разрешается в
локально установленный **GraalVM 21**. Это намеренно: FFM API (`java.lang.foreign`) —
*preview*-функция в Java 21, и JDK времени компиляции и времени выполнения обязаны точно
совпадать. `--enable-preview` включён **глобально и централизованно**:

- `JavaCompile`: `options.release = 21` + `--enable-preview`
- `Test`: `jvmArgs("--enable-preview")`
- `Javadoc`: `-enable-preview`
- `aetherium-cli` (application): `applicationDefaultJvmArgs = ["--enable-preview"]`

Проверка: скомпилированные классы несут **minor-версию class-файла `65535` (`0xFFFF`)** —
маркер preview JVM. `./gradlew :aetherium-cli:run` сообщает `preview : enabled`.

## 3. Граф модулей и правило листа

```
aetherium-cli ──► aetherium-loader ──► aetherium-core ◄── aetherium-bytecode
                          │                  ▲                    │
                          └──────────────────┴──────── aetherium-native
```

- `aetherium-core` **не объявляет внутренних зависимостей** — это лист. Его
  `build.gradle.kts` намеренно содержит пустой блок зависимостей; добавление туда
  `project(...)` — нарушение дизайна (`ARCHITECTURE.md` ).
- `aetherium-bytecode` и `aetherium-native` зависят только от `core`.
- `aetherium-loader` композирует все три. `aetherium-cli` — фронтенд-приложение.
- Циклов нет. `./gradlew projects` и `./gradlew build` проходят.

## 4. Зависимости и каталог версий

Все версии живут в `gradle/libs.versions.toml` — никогда не вписываются в скрипты
модулей. Движок байт-кода подтягивает полную поверхность ASM одним бандлом:

```kotlin
implementation(libs.bundles.asm)   // asm, asm-tree, asm-commons, asm-util, asm-analysis @ 9.8
```

Сборка **работает офлайн** (`./gradlew build --offline`), поскольку зафиксированные
артефакты ASM 9.8 присутствуют в локальном кэше Gradle.

## 5. Интеграция с NeoForge — упаковка drop-in мода

Продукт должен помещаться в стандартную папку `mods/` и сосуществовать с обычными
модами **без особой настройки для игрока**. Распознавание системой FML NeoForge
управляется метаданными мода, поэтому `aetherium-loader` поставляет валидный
`META-INF/neoforge.mods.toml`:

- Поля версий/диапазонов **подставляются Gradle** (`processResources` → `expand`) из
  каталога версий, поэтому не могут разойтись со сборкой.
- Манифест jar помечен `FMLModType = MOD`.
- Зависимости от `neoforge`/`minecraft` используют `ordering = "NONE"`, `side = "BOTH"` —
  мы интегрируемся неинвазивно и никогда не навязываем другим модам конфликты порядка
  загрузки.

### Интеграция ModDevGradle (подключена)

`aetherium-loader` применяет плагин **ModDevGradle** (`net.neoforged.moddev`, из
каталога) — и это *единственный* модуль, который это делает. Он предоставляет
декомпилированный classpath Minecraft 1.21.1 + NeoForge `21.1.x` для компиляции и dev-
задачу `runClient`:

```kotlin
plugins { alias(libs.plugins.moddev) }
neoForge {
    version = libs.versions.neoforge.get()
    runs { register("client") { client() } }
    mods { register("aetherium") { sourceSet(sourceSets["main"]) } }
}
```

- Точка входа `@Mod` (`AetheriumNeoForgeEntrypoint`) компилируется против реального
  декомпилированного classpath MC/NeoForge (проверено: перекомпилировано ~5300 исходников
  MC, наш класс собран). `./gradlew :aetherium-loader:tasks` показывает `runClient`.
- **Разделение ответственности соблюдено:** `core`, `bytecode`, `native` и
  `aetherium-testmod` содержат **ноль** ссылок `net.neoforged`/`net.minecraft`
  (проверено grep). Только единственный класс точки входа импортирует NeoForge.
  ModDevGradle держит classpath MC вне нижестоящих потребителей (напр. `aetherium-cli`),
  поэтому остальная сборка остаётся лёгкой.
- Запуск GUI вне области этого этапа; мы проверяем только *компиляцию + разрешение
  classpath*.

## 6. Поверхность API `aetherium-core`

Реализовано на этом этапе (всё в `org.aetherium.core`, лист):

| Тип | Роль |
|-----|------|
| `Symbol` (record) | Абстрактный символ API с плотным ID, назначенным при сборке. |
| `SymbolManifest` (sealed) + `Builder` | Неизменяемая карта id↔символ; `byId(int)` — путь `O(1)`. |
| `ArraySymbolManifest` | Реализация на плоском массиве — индекс массива, без хеширования. |
| `CapabilityTier` (enum) | Лестница отката `FFM → JNI → PURE_JAVA → DISABLED`. |
| `Capability` (record) | Пространственно-именованный дескриптор возможности. |
| `CapabilityProvider` | Провайдер на уровне, с зондом `isAvailable()` фазы загрузки. |
| `FallbackChain<P>` | Упорядоченные провайдеры; разрешает первый доступный, поглощает сбои зонда. |
| `CapabilityRegistry` | Зондирует один раз, запоминает; далее поиск за `O(1)`. |
| `Diagnostic` (record) + `AetheriumException` | Структурированная, безопасная для хоста модель ошибок. |
| `compute.OffHeapAllocator` | Off-heap память на FFM `Arena` (с confined-реализацией по умолчанию). |
| `compute.ComputePipeline` | Контракт асинхронного GPU/ускоренного вычисления (заглушка). |
| `compute.ComputeCapabilities` | Известные константы `Capability` для вычислений. |

## 7. Частые команды

```bash
./gradlew projects                # показать граф модулей (проверка конфигурации)
./gradlew build                   # скомпилировать + собрать все модули
./gradlew build --offline         # то же, используя только локальный кэш
./gradlew :aetherium-cli:run      # запустить CLI (с --enable-preview)
./gradlew :aetherium-loader:jar   # собрать drop-in jar мода NeoForge
```
