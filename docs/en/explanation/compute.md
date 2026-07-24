# Compute — Java→SPIR-V Compiler

*English. Russian mirror: [`../ru/compute.md`](../../ru/explanation/compute.md).*

`aetherium-compute` compiles a **pure-Java method** into a Vulkan **SPIR-V** binary at runtime, so a
modder can write a GPU kernel in plain Java — no shader language, no JNI, no FFM glue.

## The kernel

Annotate an ordinary method over primitive arrays with `@AetheriumComputeShader`:

```java
@AetheriumComputeShader(localSizeX = 64)
public static void add(float[] a, float[] b, float[] c, int n) {
    for (int i = 0; i < n; i++) {
        c[i] = a[i] + b[i];
    }
}
```

## Supported subset

The compiler intentionally supports a **strict subset**: primitives, primitive arrays, loops, and basic
math (`+ - *`). It does **not** support object allocation or reference types — `new`, `anewarray`, and
reference array stores are rejected with `UnsupportedShaderException` rather than producing an invalid
binary.

## Pipeline

1. **`JavaToSpirvCompiler`** reads the kernel class with ASM (it never executes it), locates the method,
   and scans the instruction list: a primitive-array store (`FASTORE`/`IASTORE`) fixes the element type;
   an arithmetic opcode (`F/IADD`, `F/ISUB`, `F/IMUL`) fixes the operation.
2. **`SpirvKernelBuilder`** emits a structurally valid SPIR-V module for `dst[i] = a[i] OP b[i]`:
   `OpCapability Shader`, `OpMemoryModel Logical GLSL450`, a `GLCompute` entry point, the `LocalSize`
   execution mode, the std430 SSBO type/decoration graph (three buffers, bindings 0/1/2), the
   `gl_GlobalInvocationID` built-in, and the function body.
3. **`SpirvModule`** holds the little-endian word stream and exposes the header. `verify()` walks the
   instruction stream word-by-word, proving it is parseable and begins with the magic `0x07230203`.
4. **`SpirvVulkanDispatch`** routes the binary into the `aetherium-native` Vulkan bridge — dispatching on
   a real device when one exists ([`native-bridge.md`](native-bridge.md)), CPU fallback otherwise.

## Proof

```bash
aetherium spirv
```

The float array-add kernel compiles to a **732-byte / 46-instruction** module whose header reads
`magic=0x07230203 version=0x00010000 generator=0x00000000 bound=29 schema=0`. Full GPU execution
requires a driver; the binary itself is spec-shaped and verified today.

## — `Math.*` polyfills (GLSL.std.450)

The compiler now lowers a recognised `java.lang.Math` call in a kernel to a **GLSL.std.450** extended
instruction. A kernel `out[i] = (float) Math.sin(in[i])` compiles to a two-buffer module that imports
`GLSL.std.450` (`OpExtInstImport`) and emits `OpExtInst … Sin` over the float element. Supported:
`sin, cos, tan, sqrt, exp, log, abs, floor` (float-only — these are floating-point intrinsics). The
`aetherium spirv` self-test compiles `sineWave` and confirms the `OpExtInst Sin` is present and the module
verifies structurally.
