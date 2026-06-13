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
    implementation(project(":aetherium-bytecode"))   // for the `selftest` engine simulation
    implementation(project(":aetherium-loader"))
}

application {
    mainClass.set("org.aetherium.cli.AetheriumCli")
    applicationDefaultJvmArgs = listOf("--enable-preview")
}
