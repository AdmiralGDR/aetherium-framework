// aetherium-injector — programmatic Fluent API for deep bytecode manipulation (the "Mixin killer").
//
// EN: A power-user, strongly-typed alternative to Mixin. It depends ONLY on aetherium-bytecode (the
//     ASM engine + the invokedynamic dispatch runtime) and, transitively, aetherium-core — never on
//     the loader or any Minecraft/NeoForge type. Injections are applied inside the bytecode engine's
//     verification sandbox, so a bad injection reverts to the original bytes and never crashes the JVM.
// RU: Строго типизированная альтернатива Mixin для опытных пользователей. Зависит ТОЛЬКО от
//     aetherium-bytecode (движок ASM + рантайм диспетчеризации invokedynamic) и транзитивно от
//     aetherium-core — никогда от загрузчика или типов Minecraft/NeoForge. Инъекции применяются внутри
//     верификационной «песочницы» движка байт-кода, поэтому плохая инъекция откатывается к исходным
//     байтам и никогда не роняет JVM.
dependencies {
    api(project(":aetherium-bytecode"))
    // The fluent cursor exposes ASM tree types (InsnList, MethodNode, …) in its public API, so ASM
    // is an `api` dependency here (aetherium-bytecode keeps ASM as `implementation`, not transitive).
    api(libs.bundles.asm)

    testImplementation(libs.junit.jupiter)
}
