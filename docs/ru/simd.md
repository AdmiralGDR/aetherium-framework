# SIMD — аппаратное ускорение через Vector API

*Русский. Английский оригинал: [`../en/simd.md`](../en/simd.md).*

Модуль: [`aetherium-core`](../../aetherium-core) (`org.aetherium.core.simd`).

Aetherium предоставляет модам **Vector API** Java 21 (SIMD) с **нулём шаблонного кода** и гарантированным
скалярным откатом. Системы частиц, симуляции жидкостей и массовая математика сущностей считаются полосами
256/512 бит на векторных блоках CPU, а не поэлементно.

## API без шаблонного кода

```java
// Колонка частиц живёт off-heap (Structure-of-Arrays), один компонент на полосу.
try (VectorLane posX = VectorLane.allocate(100_000);
     VectorLane velX = VectorLane.allocate(100_000)) {
    velX.fill(1.5f);
    posX.mulAddFrom(velX, dt);   // pos += vel*dt для 100k частиц — один широкий SIMD-проход
    float total = posX.sum();    // горизонтальная SIMD-редукция
}
```

Без импорта `jdk.incubator.vector`, без FFM, без учёта полос. `SimdMath` также даёт сырые ядра над
`float[]`, `double[]` и любым off-heap `MemorySegment` (напр. упакованным компонентом `StructArena`):

```java
SimdMath.mulAddInPlace(dstSegment, srcSegment, scale, count);  // dst[i] += src[i]*scale
SimdMath.mulAdd(velArray, posArray, dt, outArray);             // out[i] = vel[i]*dt + pos[i]
String backend = SimdMath.backend();  // "Vector API ..., 256-bit lanes (8 floats/op)"
```

## Почему `VectorLane` (SoA), а не strided-поле `StructArena`

SIMD требует **непрерывных** операндов. Array-of-Structs (`StructArena`, чередующиеся поля) делает
отдельное поле strided и невекторизуемым. `VectorLane` — двойник: один компонент упакован подряд off-heap,
поэтому вся колонка — один широкий проход. Используйте `StructArena` для случайного доступа к сущности и
`VectorLane` для массовой векторной математики над одним компонентом.

## Безопасная изоляция (без жёсткой зависимости от инкубатора)

Vector API — **инкубаторный** модуль. Жёсткая зависимость навязала бы всем потребителям
`--add-modules jdk.incubator.vector`. Aetherium изолирует *все* ссылки на `jdk.incubator.vector` в одном
классе `VectorKernels`, к которому обращаются лишь после подтверждения `SimdMath.isVectorApiAvailable()`.
Если модуля нет, класс не загружается и выполняется **идентичная скалярная реализация** — фреймворк
никогда не бросает `NoClassDefFoundError`, а флаг `--add-modules` ограничен этим одним модулем. Каждый
ускоренный вызов дополнительно обёрнут, чтобы сюрприз рантайма деградировал в скаляр, а не падал.

## Проверка

`aetherium simd` сообщает ширину полосы и доказывает численную идентичность SIMD-пути скаляру на heap
`float[]`, off-heap `VectorLane` из 1 000 003 элементов и намеренно суб-полосной длине (скалярный хвост):

```
SIMD backend: Vector API (jdk.incubator.vector), 256-bit lanes (8 floats/op)
heap float[10007] mulAdd vs scalar: identical
off-heap VectorLane[1000003] pos+=vel*dt vs scalar (sampled): identical
sub-lane tail length=7: exact
max abs error vs scalar: 0.0
```
