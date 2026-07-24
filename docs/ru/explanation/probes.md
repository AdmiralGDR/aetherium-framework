# Эфемерные JFR-зонды — телеметрия с нулевыми накладными расходами

*Русский. Английский оригинал: [`../en/probes.md`](../../en/explanation/probes.md).*

Модуль: [`aetherium-injector`](../../../aetherium-injector) (`org.aetherium.injector.probe`).

Обычный профайлер платит вечно: каждый инструментированный вызов несёт ветвление `if (включено)`, даже
когда никто не профилирует. Aetherium отказывается от этого налога. Зонды **вплетаются только на время
запроса профиля и убираются после** — у незондированного метода нет кода зонда вовсе, даже проверки.

## Как достигается «ноль статических накладных расходов»

`ProbeWeaver` — это `ClassTransformer`, управляемый активным множеством `ProbeTarget`:

- **Множество пусто / класс не цель** → `handles()` возвращает `false` → класс не меняется ни на байт.
  Кода зонда и флага нет буквально.
- **Цель активна** → на входе метода появляется `event.begin()`, перед каждым возвратом — `event.commit()`
  (нулевой баланс стека, поэтому `COMPUTE_FRAMES` движка сохраняет верифицируемость). JFR-событие
  записывает длительность метода по «стенным часам».

Поскольку класс события (`AetheriumMethodEvent`, стандартное `jdk.jfr.Event`) ссылается *только* из
вплетённого байт-кода, у метода без активного зонда нет ссылки на него вовсе.

## Эфемерность — hot-swap через Instrumentation

`DynamicProbeController` переключает активное множество и ретрансформирует уже загруженные классы через
`Instrumentation.retransformClasses`:

- `enable(target)` вплетает зонд в загруженный класс **мгновенно**.
- `disable(target)` / `clear()` ретрансформируют из кэшированных **исходных** байт, физически убирая зонд.

`Instrumentation` со способностью retransform берётся из `AetheriumProbeAgent`, подключённого либо на
старте (`-javaagent`), либо **по запросу самоподключением через Attach API** (`SelfAttach`, требует
`-Djdk.attach.allowAttachSelf=true`). Если агент недоступен, контроллер деградирует мягко: активное
множество всё равно питает ткач времени загрузки.

## Проверка

`aetherium profile` доказывает весь жизненный цикл:

```
probe OFF: output references AetheriumMethodEvent=false   (ноль накладных расходов — нет кода зонда)
probe ON : output references AetheriumMethodEvent=true
JFR recording captured 50 'org.aetherium.MethodTiming' event(s) from 50 calls
dynamic hot-swap: active probes=0, instrumentation=live (instant hot-swap)
```
