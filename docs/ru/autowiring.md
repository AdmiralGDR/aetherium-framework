# Авто-связывание — инициализация без конфигурации (`@AetheriumInit`)

*Русский. Английская версия: [`../en/autowiring.md`](../en/autowiring.md).*

Моду больше не нужно писать entrypoint `AetheriumMod`, файл `META-INF/services` или любую обвязку
инициализации. Разработчик помечает статический метод аннотацией **`@AetheriumInit`**, и фреймворк находит
его **во время компиляции** и связывает всё остальное — **без рантайм-рефлексии и без сканирования
classpath**.

```java
public final class MyMod {
    @AetheriumInit(runAfter = "registry")
    public static void setup(AetheriumContext ctx) {
        ctx.log("MyMod запущен на уровне " + ctx.computeTier());
    }
}
```

Это весь entrypoint. Без `implements AetheriumMod`, без services-файла, без `@Mod`.

## Как это работает (целиком в `javac`)

`AetheriumInitProcessor` (аннотационный процессор в `aetherium-content`) выполняется во время компиляции
потребителя:

1. Находит каждый метод `@AetheriumInit` и проверяет сигнатуру — она обязана быть
   `public static void m(AetheriumContext)`; неверная сигнатура — **ошибка компиляции** с указанием на
   проблемный метод.
2. Выстраивает их в детерминированный DAG инициализации через `InitOrdering` — та же сортировка Кана,
   стабильный tie-break по индексу объявления и модель `runBefore`/`runAfter`, что у DAG хуков
   ([`injector.md`](injector.md)). Цикл или дубликат id **валит сборку**, а не угадывает.
3. `InitSourceWriter` генерирует один `AetheriumMod`, чей `onInitialize` вызывает каждый init **прямым
   статическим вызовом** по порядку и выпускает соответствующую запись `META-INF/services`.

В рантайме существующий `ServiceLoader.load(AetheriumMod.class)` загрузчика находит сгенерированный класс
и вызывает его ([`game-integration.md`](game-integration.md)) — методы init выполняются как обычные
статически слинкованные вызовы. Нет рефлексии, нет поиска по аннотациям, ничто не сканирует classpath:
обнаружение уже произошло в компиляторе, поэтому рантайм-footprint этой функции практически нулевой.

Имя сгенерированного класса включает mod id (`-Aaetherium.modId=<id>`, по умолчанию `aetherium`), поэтому
два мода Aetherium на одном classpath не конфликтуют.

## Связь с явным API

Рукописный SPI `AetheriumMod` по-прежнему работает как раньше — `@AetheriumInit` чисто аддитивен,
генерируя `AetheriumMod` за вас. Оба пути сходятся к одному обнаружению через `ServiceLoader`.

## Доказательство

Чистая логика упорядочивания + генерации покрыта юнит-тестами в `aetherium-datagen`
([`InitWiringTest`](../../aetherium-datagen/src/test/java/org/aetherium/datagen/InitWiringTest.java)), а
процессор проверен end-to-end реальным внутрипроцессным прогоном `javac` в `aetherium-content`
([`AetheriumInitProcessorTest`](../../aetherium-content/src/test/java/org/aetherium/content/AetheriumInitProcessorTest.java)):
только аннотированные методы → сгенерированный entrypoint без рефлексии + регистрация сервиса, с вызовами
init в порядке DAG.
