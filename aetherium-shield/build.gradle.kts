// aetherium-shield — the Sovereign Protection module.
//
// EN: A load-time / build-time bytecode PROTECTOR for a mod author's OWN compiled classes — hardening them
//     against reverse-engineering and, deliberately, against automated (AI/LLM) decompilation and analysis.
//     Runs its transformer chain INSIDE the aetherium-bytecode verification sandbox, so any pass that would
//     produce invalid bytecode reverts to the original class and never crashes the JVM (graceful degradation).
//     Depends only on aetherium-bytecode (+ transitively core) and ASM — never on the loader or any MC type.
//     Opt-in; the mod author remains responsible for their own mod's license terms.
// RU: Байткод-ПРОТЕКТОР времени сборки/загрузки для СОБСТВЕННЫХ классов автора мода — защита от реверс-
//     инжиниринга и, намеренно, от автоматического (ИИ/LLM) декомпилирования и анализа. Цепочка
//     трансформеров работает ВНУТРИ верификационной песочницы aetherium-bytecode: любой проход, дающий
//     невалидный байткод, откатывается к оригиналу и никогда не роняет JVM. Зависит только от
//     aetherium-bytecode (+ транзитивно core) и ASM. Подключается по желанию; за лицензию мода отвечает автор.
dependencies {
    api(project(":aetherium-bytecode"))
    // The shield's transformers manipulate ASM tree types and use ClassRemapper (asm-commons), so ASM is
    // an `api` dependency here (aetherium-bytecode keeps ASM as `implementation`, not transitive).
    api(libs.bundles.asm)

    testImplementation(libs.junit.jupiter)
}
