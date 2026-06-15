# Aetherium Framework

> A high-performance, universal Minecraft modding ecosystem and meta-loader.
>
> Высокопроизводительная универсальная экосистема моддинга Minecraft и мета-загрузчик.

[![Java](https://img.shields.io/badge/Java-21%20(GraalVM)-orange)]()
[![Baseline](https://img.shields.io/badge/baseline-NeoForge%201.21.1-blue)]()
[![Status](https://img.shields.io/badge/status-foundation-lightgrey)]()

---

## 🇬🇧 English

### What is Aetherium?

Aetherium is a **modding meta-layer**. It sits *between* a mod and the underlying
mod loader (NeoForge, Fabric, Forge, …) and exposes a single, stable, loader-agnostic
API. A mod compiled against Aetherium runs on **any** supported loader without
recompilation, because Aetherium performs the loader-specific translation at load
time through controlled bytecode transformation.

The design goals, in priority order:

1. **Universality** — one mod artifact, many loaders.
2. **Performance** — runtime dispatch is `O(1)`; all per-loader analysis happens
   once, at load time, never on the hot path.
3. **Safety** — every transformation is validated, sandboxed, and reversible. A
   failed transform falls back to a known-good path instead of corrupting the JVM.
4. **Modularity** — the engine, the native bridge, the loader shim, and the CLI are
   independent Gradle modules with no cyclic dependencies.

### Module layout

| Module                | Responsibility                                                        |
|-----------------------|-----------------------------------------------------------------------|
| `aetherium-core`      | Public API, service-loader contracts, configuration, error model.     |
| `aetherium-bytecode`  | ASM-based bytecode manipulation engine (class transformers, weaving). |
| `aetherium-native`    | JNI / C++ native bridge for low-level JVM & OS interactions.          |
| `aetherium-loader`    | Loader shims that adapt NeoForge/Fabric/Forge to the Aetherium API.   |
| `aetherium-cli`       | Developer CLI: `init`, `analyze`, `selftest`, `preflight`, `chaos`.   |
| `aetherium-testsuite` | Chaos Engineering stress & fallback validation.                       |
| `aetherium-testmod`   | In-game test mod targeting the Aetherium API (not NeoForge).          |
| `aetherium-edge`      | Platform Abstraction Layer (PAL): loader-agnostic vanilla bridge SPI. |
| `aetherium-network`   | Loader-agnostic custom-payload SPI (zero-GC StructArena sync).        |
| `aetherium-gfx`       | Loader-agnostic rendering / model-registration abstraction.           |
| `aetherium-gradle-plugin` | Zero-config build plugin for mod developers (publish, bundle).    |

### Requirements

- **OS:** Linux (x86-64). Other platforms are a non-goal for the foundation phase.
- **JDK:** Java 21, **GraalVM** with preview features enabled (`--enable-preview`).
- **Loader baseline:** NeoForge 1.21.1 (the reference compatibility target).
- **Build:** Gradle (wrapper committed once the build graph is finalized).

### Quick start (foundation phase)

```bash
git clone <local-repo> aetherium && cd aetherium
# Build graph is being assembled; for now inspect the architecture:
less ARCHITECTURE.md
less docs/en/bytecode-engine.md
```

### Documentation policy

**Every** architectural decision, design choice, and non-trivial code block is
documented in **both English and Russian**. English lives in [`docs/en/`](docs/en/),
Russian in [`docs/ru/`](docs/ru/). The two trees are kept in lock-step: a change to
one **must** be mirrored in the other in the same commit. See
[`ARCHITECTURE.md`](ARCHITECTURE.md) for the system design and
[`CHANGELOG.md`](CHANGELOG.md) for the running history.

---

## 🇷🇺 Русский

### Что такое Aetherium?

Aetherium — это **мета-слой моддинга**. Он располагается *между* модом и нижележащим
загрузчиком модов (NeoForge, Fabric, Forge, …) и предоставляет единый стабильный
API, независимый от загрузчика. Мод, скомпилированный под Aetherium, работает на
**любом** поддерживаемом загрузчике без перекомпиляции, поскольку Aetherium выполняет
специфичную для загрузчика трансляцию во время загрузки посредством контролируемой
трансформации байт-кода.

Цели проектирования в порядке приоритета:

1. **Универсальность** — один артефакт мода, множество загрузчиков.
2. **Производительность** — диспетчеризация во время выполнения имеет сложность
   `O(1)`; весь анализ, специфичный для загрузчика, выполняется один раз во время
   загрузки и никогда — на «горячем пути».
3. **Безопасность** — каждая трансформация проверяется, изолируется и обратима.
   Неудачная трансформация откатывается к заведомо рабочему пути вместо повреждения
   JVM.
4. **Модульность** — движок, нативный мост, прослойка загрузчика и CLI являются
   независимыми модулями Gradle без циклических зависимостей.

### Структура модулей

| Модуль                | Ответственность                                                        |
|-----------------------|------------------------------------------------------------------------|
| `aetherium-core`      | Публичный API, контракты ServiceLoader, конфигурация, модель ошибок.   |
| `aetherium-bytecode`  | Движок манипуляции байт-кодом на основе ASM (трансформеры, вплетение). |
| `aetherium-native`    | Нативный мост JNI / C++ для низкоуровневого взаимодействия с JVM и ОС. |
| `aetherium-loader`    | Прослойки загрузчиков, адаптирующие NeoForge/Fabric/Forge к API.       |
| `aetherium-cli`       | CLI разработчика: `init`, `analyze`, `selftest`, `preflight`, `chaos`. |
| `aetherium-testsuite` | Стресс-валидация Chaos Engineering и проверка откатов.                 |
| `aetherium-testmod`   | Внутриигровой тест-мод под API Aetherium (не NeoForge).               |
| `aetherium-edge`      | Слой абстракции платформы (PAL): независимый от загрузчика мост к ванили. |
| `aetherium-network`   | Независимый от загрузчика SPI кастомных пакетов (zero-GC синхронизация StructArena). |
| `aetherium-gfx`       | Независимая от загрузчика абстракция рендеринга / регистрации моделей. |
| `aetherium-gradle-plugin` | Build-плагин с нулевой конфигурацией для разработчиков модов (публикация, bundle). |

### Требования

- **ОС:** Linux (x86-64). Другие платформы — не цель этапа основания.
- **JDK:** Java 21, **GraalVM** с включёнными preview-функциями (`--enable-preview`).
- **Базовый загрузчик:** NeoForge 1.21.1 (эталонная цель совместимости).
- **Сборка:** Gradle (wrapper будет зафиксирован после финализации графа сборки).

### Политика документации

**Каждое** архитектурное решение, проектный выбор и нетривиальный блок кода
документируется на **английском и русском** языках. Английский — в [`docs/en/`](docs/en/),
русский — в [`docs/ru/`](docs/ru/). Деревья поддерживаются синхронно: изменение в
одном **обязано** быть отражено в другом в том же коммите. См.
[`ARCHITECTURE.md`](ARCHITECTURE.md) для дизайна системы и
[`CHANGELOG.md`](CHANGELOG.md) для истории изменений.

---

## License

**GNU Affero General Public License v3.0 (AGPL-3.0)** — see [`LICENSE`](LICENSE).

**EN.** Aetherium is licensed under the AGPL-3.0, a strong copyleft license. Any
derivative work — and critically, **any modified version made available to users over a
network** (e.g. a modded game server) — must be released in full corresponding source
under the same AGPL-3.0 terms. This deliberately keeps the universal modding meta-layer
and everything built on top of it open.

**RU.** Aetherium распространяется под лицензией AGPL-3.0 — сильной копилефт-лицензией.
Любая производная работа — и, что особенно важно, **любая изменённая версия,
предоставляемая пользователям по сети** (например, моддинговый игровой сервер) — должна
быть выпущена с полным соответствующим исходным кодом на тех же условиях AGPL-3.0. Это
намеренно сохраняет открытыми универсальный мета-слой моддинга и всё, что на нём
построено.
