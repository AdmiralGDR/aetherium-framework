// aetherium-ktx — zero-overhead Kotlin DSL over the Java APIs (the "conciseness" module).
//
// EN: Provides concise, type-safe Kotlin builder blocks for the injector (HookDag/AetheriumInjector),
//     the off-heap StructArena, and the DataGen content pipeline. Everything is an `inline` function or
//     a thin builder that lowers to the EXACT same Java calls — hooks still bind to the O(1)
//     invokedynamic HookTable, so there is zero runtime reflection and no extra dispatch layer. The
//     module wraps FFM-backed StructArena (compiled with --enable-preview), so the Kotlin compiler is
//     told to read/emit preview-flagged classfiles via -Xjvm-enable-preview, keeping it in lock-step
//     with the Java modules (tests already run with --enable-preview via the root build).
// RU: Предоставляет лаконичные, типобезопасные Kotlin-блоки для инжектора (HookDag/AetheriumInjector),
//     off-heap StructArena и конвейера контента DataGen. Всё — это `inline`-функции или тонкие
//     построители, понижающиеся к ТЕМ ЖЕ Java-вызовам: хуки по-прежнему привязываются к O(1)
//     invokedynamic-таблице HookTable, поэтому нет рефлексии в рантайме и лишнего слоя диспетчеризации.
//     Модуль оборачивает StructArena на FFM (скомпилирован с --enable-preview), поэтому компилятору
//     Kotlin указано читать/эмитировать preview-классы через -Xjvm-enable-preview, синхронно с
//     Java-модулями (тесты уже запускаются с --enable-preview через корневую сборку).
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    // The injector (and, transitively, aetherium-core + ASM) is the primary wrap target.
    api(project(":aetherium-injector"))
    // StructArena lives in core; DataGen content pipeline is pure-Java and build-time.
    api(project(":aetherium-core"))
    api(project(":aetherium-datagen"))

    testImplementation(libs.junit.jupiter)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        // Match the Java modules' preview posture: read preview-flagged deps (FFM StructArena) and
        // emit preview-flagged Kotlin classfiles. The root build already passes --enable-preview to
        // the Test JVM for every preview-capable module, so the round-trip is consistent.
        freeCompilerArgs.add("-Xjvm-enable-preview")
    }
}
