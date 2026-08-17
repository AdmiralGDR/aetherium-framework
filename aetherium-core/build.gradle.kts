/*
 * aetherium-core — the stable, loader-agnostic API. THE LEAF MODULE.
 *
 * EN: Depends on NOTHING internal (ARCHITECTURE.md). It may only use the JDK (incl. the FFM
 *     preview API). Adding an internal `project(...)` dependency here is a design violation.
 * RU: Не зависит НИ ОТ ЧЕГО внутреннего (ARCHITECTURE.md). Может использовать только JDK
 *     (включая preview-API FFM). Добавление внутренней зависимости `project(...)` сюда —
 *     нарушение дизайна.
 */

// Intentionally no internal dependencies. core is the leaf.
dependencies {
    testImplementation(libs.junit.jupiter)
}

// SIMD: the Vector API lives in the incubator module jdk.incubator.vector. Only `aetherium-core`
// references it (isolated in VectorKernels), so the --add-modules flag is scoped here at compile time.
// Consumers that want the accelerated path add the same flag at runtime (the CLI/testsuite do); without
// it, SimdMath transparently falls back to scalar, so this never becomes a hard requirement downstream.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("--add-modules=jdk.incubator.vector")
}
tasks.withType<Test>().configureEach {
    jvmArgs("--add-modules=jdk.incubator.vector")
}
