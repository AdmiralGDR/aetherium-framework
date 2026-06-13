/*
 * aetherium-native — JNI / FFM native bridge.
 *
 * EN: Knows `core` only. Compiles the C++ side (src/main/cpp → libaetherium_native.so) with
 *     CMake and bundles the .so into the jar under `native/` so the FFM loader can extract and
 *     link it. If the C++ toolchain is absent, the native tasks are skipped (onlyIf) and the .so
 *     is simply not bundled — the runtime then degrades to pure Java via the Pre-Flight Check.
 * RU: Знает только `core`. Компилирует сторону C++ (src/main/cpp → libaetherium_native.so) через
 *     CMake и упаковывает .so в jar в каталог `native/`, чтобы FFM-загрузчик мог извлечь и
 *     слинковать его. Если тулчейн C++ отсутствует, нативные задачи пропускаются (onlyIf) и .so
 *     просто не упаковывается — рантайм деградирует на чистую Java через Pre-Flight Check.
 */

import java.io.File

dependencies {
    api(project(":aetherium-core"))
}

val cppSourceDir = layout.projectDirectory.dir("src/main/cpp")
val nativeBuildDir = layout.buildDirectory.dir("native")

fun executableOnPath(name: String): Boolean =
    System.getenv("PATH")?.split(File.pathSeparator)?.any { dir ->
        File(dir, name).canExecute()
    } ?: false

val haveToolchain = executableOnPath("cmake") && (executableOnPath("g++") || executableOnPath("clang++"))

val cmakeConfigure by tasks.registering(Exec::class) {
    group = "native"
    description = "Configure the native build with CMake."
    onlyIf { haveToolchain }
    inputs.dir(cppSourceDir)
    outputs.dir(nativeBuildDir)
    doFirst { nativeBuildDir.get().asFile.mkdirs() }
    commandLine(
        "cmake",
        "-S", cppSourceDir.asFile.absolutePath,
        "-B", nativeBuildDir.get().asFile.absolutePath,
        "-DCMAKE_BUILD_TYPE=Release"
    )
}

val compileNative by tasks.registering(Exec::class) {
    group = "native"
    description = "Compile libaetherium_native.so."
    onlyIf { haveToolchain }
    dependsOn(cmakeConfigure)
    inputs.dir(cppSourceDir)
    outputs.dir(nativeBuildDir)
    commandLine(
        "cmake",
        "--build", nativeBuildDir.get().asFile.absolutePath,
        "--config", "Release"
    )
}

// Bundle the compiled .so into the jar under `native/` so FFM can extract & link it at runtime.
// Precise include + no empty dirs keeps CMake build cruft out of the jar.
tasks.named<ProcessResources>("processResources") {
    dependsOn(compileNative)
    includeEmptyDirs = false
    from(nativeBuildDir) {
        include("libaetherium_native.so")
        into("native")
    }
}

// Convenience: report what the native build decided to do.
tasks.register("nativeInfo") {
    group = "native"
    description = "Print whether the native toolchain is available."
    doLast {
        println("native toolchain available: $haveToolchain")
        println("native build dir: ${nativeBuildDir.get().asFile}")
    }
}
