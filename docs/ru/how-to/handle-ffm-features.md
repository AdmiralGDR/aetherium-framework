# Как использовать FFM/off-heap возможность с безопасной деградацией

*Русский. Английская версия: [`../../en/how-to/handle-ffm-features.md`](../../en/how-to/handle-ffm-features.md).*

*Практическое руководство. Оно решает ловушку, в которую ровно один раз попадает каждый мод, трогающий
`java.lang.foreign` (off-heap `StructArena`, SIMD) — класс, которому нужен `--enable-preview` на лаунчере,
который его не передаёт. Про модель возможностей см. [native-bridge](../explanation/native-bridge.md).*

## Ловушка

Preview-класс бросает `UnsupportedClassVersionError` на обычном лаунчере. Это **`Error`, а не `Exception`** —
очевидный `catch (RuntimeException)` его не ловит — и всплывает он при первом *использовании*, глубоко внутри
инициализации, а не при загрузке класса, где его стали бы искать. Ручная защита — это ловить `Throwable`,
пробовать один раз, кэшировать вердикт и деградировать на каждом вызове. Легко ошибиться.

## Одна строка

`org.aetherium.core.Capabilities` делает это за вас. Выберите реализацию один раз при инициализации:

```java
import org.aetherium.core.Capabilities;

// Берёт off-heap движок, когда JVM позволяет, иначе чистый Java — ни один Error не утекает.
MyEngine engine = Capabilities.ffm(OffHeapEngine::new, PureJavaEngine::new);
```

`ffm(preview, fallback)` выполняет `preview` и при **любом `Throwable`** (включая семейство
`UnsupportedClassVersionError`) возвращает `fallback`. Это и есть всё исправление: тонкость «`Error`, а не
`Exception`» больше нельзя сделать неправильно.

## Повторное использование: проба один раз, затем фиксация

Если разрешаете лениво на каждом вызове, а не один раз при инициализации, используйте мемоизирующую форму,
чтобы деградировавший запуск не пробовал (и не проваливал) загрузку preview-класса снова:

```java
Supplier<MyBuffer> buffer = Capabilities.ffmLazy(OffHeapBuffer::new, HeapBuffer::new);
// первый get() пробует; каждый следующий идёт сразу к сработавшему пути.
```

## Просто флаг возможности

Когда значения нет, проверьте, грузится ли путь:

```java
if (Capabilities.available(SimdMath::warmUp)) {
    // быстрый путь
}
```

## На горячем пути: проба один раз, затем ветвление

`ffm` выполняет `preview` в `try`/`catch` на **каждом** вызове — нормально изредка, но расточительно на пути
за тик, где возможность отсутствует (каждый вызов создаёт и бросает исключение). Две формы этого избегают:

```java
// (a) Один кэшированный вердикт, затем обычное ветвление — минимальный вариант:
if (Capabilities.ffmAvailable()) {
    intensity = AnomalyEngine.get().intensity(slot);   // без try/catch на горячем пути
} else {
    intensity = 0f;
}

// (b) Мемоизирующая деградация с аргументом (когда обе стороны берут аргумент вызова):
Function<Integer, Float> intensityOf =
        Capabilities.ffmLazy(slot -> AnomalyEngine.get().intensity(slot), slot -> 0f);
float i = intensityOf.apply(slot);   // пробует один раз; далее сразу к сработавшему пути
```

`ffmAvailable()` пробует preview-класс ровно один раз и кэширует вердикт; `Function`-форма `ffmLazy` — это
`Supplier`-форма с аргументом.

Проверьте сами: `aetherium capabilities` запускает самотест, показывая, как `Error` из preview-поставщика
чисто деградирует в fallback, `ffmAvailable()` стабилен между вызовами, а `Function`-форма пробует ровно раз.
