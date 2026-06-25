// aetherium-wasm — polyglot WASM sandbox (GraalWASM), memory + compute only.
//
// EN: Loads .wasm mods (Rust/C/Go) into a strictly sandboxed GraalVM polyglot Context — filesystem and
//     network access are denied by construction; only memory and compute are permitted. GraalWASM is
//     reached REFLECTIVELY (no compile-time dependency) so the offline build stays green whether or not
//     the GraalVM polyglot/wasm jars are present; when absent the module degrades to policy-only mode.
//     Bridges WASM linear memory to the FFM StructArena for secure off-heap entity physics.
// RU: Загружает .wasm-моды (Rust/C/Go) в строго изолированный polyglot-контекст GraalVM — доступ к
//     файловой системе и сети запрещён по построению; разрешены только память и вычисления. GraalWASM
//     достигается РЕФЛЕКСИВНО (без зависимости времени компиляции), поэтому офлайн-сборка остаётся
//     зелёной независимо от наличия jar GraalVM polyglot/wasm; при отсутствии модуль деградирует в
//     режим только-политики. Связывает линейную память WASM с FFM StructArena для безопасной off-heap
//     физики сущностей.
dependencies {
    api(project(":aetherium-core"))   // StructArena bridge target (off-heap entity store)

    testImplementation(libs.junit.jupiter)
}
