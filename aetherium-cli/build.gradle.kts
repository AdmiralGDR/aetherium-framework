/*
 * aetherium-cli — developer CLI / IDE tooling entry point.
 *
 * EN: Application module. Runs with --enable-preview so the FFM-backed capability probes behave
 *     identically to production. Depends on core (+ loader, for inspection tooling later).
 * RU: Модуль-приложение. Запускается с --enable-preview, чтобы зондирование возможностей на
 *     базе FFM вело себя так же, как в продакшене. Зависит от core (+ loader, для инструментов
 *     инспекции в дальнейшем).
 */

plugins {
    application
}

dependencies {
    implementation(project(":aetherium-core"))
    implementation(project(":aetherium-bytecode"))    // selftest + analyze (BytecodeAnalyzer)
    implementation(project(":aetherium-injector"))     // inject command (InjectorSelfTest)
    implementation(project(":aetherium-security"))     // security command (CIA-triad self-test)
    implementation(project(":aetherium-loader"))       // preflight (PreFlightCheck)
    implementation(project(":aetherium-testsuite"))    // chaos command
}

application {
    mainClass.set("org.aetherium.cli.AetheriumCli")
    // --enable-preview: FFM is a preview API on 21. --enable-native-access: FFM downcalls are
    // restricted methods; granting access keeps the native bridge quiet instead of warning.
    applicationDefaultJvmArgs = listOf(
        "--enable-preview", "--enable-native-access=ALL-UNNAMED",
        // SIMD: enable the incubator Vector API so the `simd` command exercises hardware lanes
        // (without it, SimdMath transparently falls back to scalar).
        "--add-modules=jdk.incubator.vector",
        // Ephemeral probes: allow the JVM to attach the probe agent to itself for on-demand hot-swap.
        "-Djdk.attach.allowAttachSelf=true")
}
