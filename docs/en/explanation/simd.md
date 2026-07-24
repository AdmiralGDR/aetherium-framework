# SIMD — Vector API Hardware Acceleration

*English. Russian mirror: [`../ru/simd.md`](../../ru/explanation/simd.md).*

Module: [`aetherium-core`](../../../aetherium-core) (`org.aetherium.core.simd`).

Aetherium exposes Java 21's **Vector API** (SIMD) to modders with **zero boilerplate** and a guaranteed
scalar fallback. Particle systems, fluid sims, and bulk entity math run 256/512-bit wide on the CPU's
vector units instead of one element at a time.

## Zero-boilerplate API

```java
// A particle column lives off-heap (Structure-of-Arrays), one component per lane.
try (VectorLane posX = VectorLane.allocate(100_000);
     VectorLane velX = VectorLane.allocate(100_000)) {
    velX.fill(1.5f);
    posX.mulAddFrom(velX, dt);   // pos += vel*dt across 100k particles — one wide SIMD sweep
    float total = posX.sum();    // horizontal SIMD reduction
}
```

No `jdk.incubator.vector` import, no FFM, no lane bookkeeping. `SimdMath` also offers the raw kernels
over `float[]`, `double[]`, and any off-heap `MemorySegment` (e.g. a `StructArena` packed component):

```java
SimdMath.mulAddInPlace(dstSegment, srcSegment, scale, count);  // dst[i] += src[i]*scale
SimdMath.mulAdd(velArray, posArray, dt, outArray);             // out[i] = vel[i]*dt + pos[i]
String backend = SimdMath.backend();  // "Vector API ..., 256-bit lanes (8 floats/op)"
```

## Why a `VectorLane` (SoA), not a strided `StructArena` field

SIMD wants its operands **contiguous**. An Array-of-Structs (`StructArena`, interleaved fields) makes a
single field strided and un-vectorizable. `VectorLane` is the dual: one component packed back-to-back
off-heap, so the whole column is a single wide sweep. Use `StructArena` for random per-entity access and
`VectorLane` for bulk vector math over one component.

## Safe isolation (no hard incubator dependency)

The Vector API is an **incubator** module. A hard dependency would force every consumer onto
`--add-modules jdk.incubator.vector`. Aetherium isolates *all* `jdk.incubator.vector` references into a
single class, `VectorKernels`, touched only after `SimdMath.isVectorApiAvailable()` confirms the module
is present. If it is absent the class is never loaded and an **identical scalar implementation** runs —
so the framework never throws `NoClassDefFoundError`, and `aetherium-core`'s `--add-modules` flag is
scoped to that one module. Every accelerated call is additionally wrapped so a runtime surprise degrades
to scalar rather than failing (availability over fragility).

## Verification

`aetherium simd` reports the lane width and proves the SIMD path is numerically identical to scalar on a
heap `float[]`, an off-heap `VectorLane` of 1,000,003 elements, and a deliberately sub-lane length (the
scalar tail):

```
SIMD backend: Vector API (jdk.incubator.vector), 256-bit lanes (8 floats/op)
heap float[10007] mulAdd vs scalar: identical
off-heap VectorLane[1000003] pos+=vel*dt vs scalar (sampled): identical
sub-lane tail length=7: exact
max abs error vs scalar: 0.0
```
