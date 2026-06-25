// aetherium-fuzzer — aggressive, deterministic coverage fuzzer for the unsafe attack surface.
//
// EN: Bombards the Java→SPIR-V compiler (aetherium-compute) and the polyglot WASM sandbox/bridge
//     (aetherium-wasm) with malformed binaries, illegal opcodes, and out-of-bounds memory requests to
//     prove the JVM and host OS never crash — every adversarial input must surface as a clean,
//     contractual exception (or be handled), never a raw VM error or native fault. The campaign runs as
//     an ordinary JUnit test, so it executes automatically during `./gradlew check` (rule).
// RU: Бомбардирует компилятор Java→SPIR-V (aetherium-compute) и polyglot-песочницу/мост WASM
//     (aetherium-wasm) некорректными бинарями, нелегальными опкодами и запросами памяти вне границ,
//     доказывая, что JVM и хост-ОС никогда не падают — любой враждебный вход обязан проявиться чистым
//     контрактным исключением, а не сырой ошибкой VM или нативным сбоем. Кампания — обычный JUnit-тест,
//     поэтому выполняется автоматически при `./gradlew check` (правило фазы 16).
dependencies {
    implementation(project(":aetherium-core"))      // StructArena / StructLayout (bridge fuzz target)
    implementation(project(":aetherium-compute"))    // Java→SPIR-V compiler + SpirvModule (fuzz targets)
    implementation(project(":aetherium-native"))     // VulkanProbe (offline dispatch target)
    implementation(project(":aetherium-wasm"))        // sandbox + StructArena bridge (fuzz targets)
    implementation(libs.bundles.asm)                  // craft/mutate adversarial .class bytes

    testImplementation(libs.junit.jupiter)
}
