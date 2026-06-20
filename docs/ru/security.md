# Безопасность — изоляция CIA на основе возможностей

*Русский. Английский оригинал: [`../en/security.md`](../en/security.md).*

Модуль: [`aetherium-security`](../../aetherium-security) (`org.aetherium.security`).

`SecurityManager` JVM удалён. Aetherium заменяет его явной **моделью возможностей**: мод обладает только
выданными полномочиями, фреймворк проверяет соответствующую `Capability` перед любым чувствительным
действием, а по умолчанию — **запрет**. Это слой принуждения для триады CIA.

## Модель

```java
SecurityPolicy policy = SecurityPolicy.global();
policy.grant(CapabilityGrant.of("my_mod", Capability.NATIVE_MEMORY, Capability.REFLECTION));

policy.require("my_mod", Capability.NATIVE_MEMORY);   // возврат; иначе SecurityViolationException
policy.allows("other_mod", Capability.FILE_WRITE);    // false — не выдано (default deny)
```

`Capability` — это словарь: `REFLECTION`, `NATIVE_MEMORY`, `DEFINE_CLASS`, `FILE_READ`, `FILE_WRITE`,
`NETWORK`. Незарегистрированный мод получает `CapabilityGrant.none` — он не может ничего привилегированного.

## Целостность — границы FFM-памяти

FFM даёт модам сырую off-heap мощь; случайное смещение — дыра в безопасности памяти. Мод никогда не
получает сырой `MemorySegment` — он получает `GuardedSegment`, который (1) создаётся только обладателем
`NATIVE_MEMORY` и (2) проверяет каждый доступ по границам выданной области, превращая попытку выхода в
локализованное `SecurityViolationException` вместо неопределённого поведения:

```java
GuardedSegment view = GuardedSegment.grant(policy, "my_mod", segment); // требует NATIVE_MEMORY
view.setInt(0, 42);        // ок
view.setInt(62, 1);        // 62+4 > 64 → SecurityViolationException (нет записи за границей)
```

## Конфиденциальность — охрана рефлексии

Глубокая рефлексия (`setAccessible`) — путь, которым враждебный мод прочитал бы приватное состояние
другого мода или обошёл песочницу инжектора. `ReflectionGuard.makeAccessible` требует двух правил:
(1) мод должен обладать `REFLECTION` и (2) цель не должна быть в **защищённом** пакете — второе правило
абсолютно и действует даже при наличии возможности. Защищённые префиксы: `org.aetherium.loader`,
`org.aetherium.injector`, `org.aetherium.bytecode.runtime`, `org.aetherium.security`, `java.lang.invoke`,
`jdk.internal.`. Мод может интроспектировать свои классы, но никогда — фреймворк.

## Стратегия JPMS

На module path фреймворк отдаёт внутренние пакеты через **квалифицированные экспорты**
(`exports … to …`), чтобы их читали только санкционированные модули, и держит runtime
диспетчеризации/инжектора неэкспортированным. Слой возможностей выше — рантайм-дополнение, действующее и
на classpath, где границы JPMS не принуждаются.

## Проверка

`aetherium security` проверяет каждый инвариант, который прощупывал бы враждебный мод:

```
default-deny            : OK
granted capability ok   : OK
FFM in-bounds access    : OK
FFM out-of-bounds block : OK   (целостность)
internal reflection deny: OK   (конфиденциальность)
own-class reflection ok : OK
```
