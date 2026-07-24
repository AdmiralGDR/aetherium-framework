/*
 * aetherium-testsuite — Chaos Engineering validation.
 *
 * EN: Deliberately mutates dummy mod bytecode and stresses the engine + native fallback paths on
 *     hundreds of virtual threads, asserting the JVM never crashes. Depends on core, bytecode and
 *     native (it must drive all three); it is NOT depended upon by any production module.
 * RU: Намеренно мутирует фиктивный байт-код модов и нагружает движок + нативные пути отката на
 *     сотнях виртуальных потоков, утверждая, что JVM никогда не падает. Зависит от core, bytecode и
 *     native (должен управлять всеми тремя); ни один продакшен-модуль от него не зависит.
 */

plugins {
    application
}

dependencies {
    implementation(project(":aetherium-core"))
    implementation(project(":aetherium-bytecode"))
    implementation(project(":aetherium-native"))
    implementation(libs.bundles.asm) // to synthesize dummy + mutated classes

    testImplementation(libs.junit.jupiter) // FFM zero-leak audit runs in ./gradlew check
}

application {
    mainClass.set("org.aetherium.testsuite.ChaosMain")
    applicationDefaultJvmArgs = listOf(
        "--enable-preview", "--enable-native-access=ALL-UNNAMED",
        "--add-modules=jdk.incubator.vector",
        // Capital debugging: let the FFM leak audit read the JVM's own native-memory account.
        "-XX:NativeMemoryTracking=summary")
}

// EN: The FFM zero-leak audit corroborates its ledger against JVM Native Memory Tracking; enable NMT
//     for the test JVM so `./gradlew check` runs the full three-witness proof, not the degraded one.
// RU: Аудит нулевых утечек FFM сверяет свой реестр с Native Memory Tracking JVM; включаем NMT для
//     тестовой JVM, чтобы `./gradlew check` выполнял полное доказательство с тремя свидетелями.
tasks.withType<Test>().configureEach {
    jvmArgs("-XX:NativeMemoryTracking=summary")
}
