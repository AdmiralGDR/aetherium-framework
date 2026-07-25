/*
 * aetherium-native — FFM native bridge (Zig).
 *
 * EN: Knows `core` only. Compiles the ZIG side (src/main/zig → libaetherium_native.so) with a single
 *     `zig build-lib` (no C++/CMake dependency — ) and bundles the .so into the jar under `native/`
 *     so the FFM loader can extract and link it. It links only libc (for dlopen); Vulkan is reached by
 *     runtime dlopen, so there is no libvulkan build/link dependency. If Zig is absent the native task is
 *     skipped (onlyIf) and the .so is simply not bundled — the runtime degrades to pure Java via Pre-Flight.
 * RU: Знает только `core`. Компилирует сторону ZIG (src/main/zig → libaetherium_native.so) одним
 *     `zig build-lib` (без зависимости от C++/CMake — Фаза 23) и упаковывает .so в jar в каталог `native/`.
 *     Линкует только libc (для dlopen); Vulkan — через runtime dlopen, без зависимости от libvulkan. Если
 *     Zig отсутствует, задача пропускается (onlyIf) и рантайм деградирует на чистую Java через Pre-Flight.
 */

import java.io.File

dependencies {
    api(project(":aetherium-core"))
}

// the native bridge is now Zig, not C++ — no CMake/g++ dependency, one toolchain (like the shield
// guard). It links only libc (for dlopen); Vulkan is reached by runtime dlopen, so there is no libvulkan
// build/link dependency. Deterministic ReleaseSmall output supports reproducible builds.
val zigSource = layout.projectDirectory.file("src/main/zig/aetherium_native.zig")
val nativeBuildDir = layout.buildDirectory.dir("native")

fun executableOnPath(name: String): Boolean =
    System.getenv("PATH")?.split(File.pathSeparator)?.any { dir ->
        File(dir, name).canExecute()
    } ?: false

val haveZig = executableOnPath("zig")

val compileNative by tasks.registering(Exec::class) {
    group = "native"
    description = "Compile the zero-C++-dependency Zig native bridge (libaetherium_native.so)."
    onlyIf { haveZig }
    inputs.file(zigSource)
    outputs.dir(nativeBuildDir)
    doFirst { nativeBuildDir.get().asFile.mkdirs() }
    workingDir = nativeBuildDir.get().asFile
    commandLine(
        "zig", "build-lib", "-dynamic", "-O", "ReleaseSmall", "-lc",
        "--name", "aetherium_native",
        zigSource.asFile.absolutePath
    )
}

// Bundle the compiled .so into the jar under `native/` so FFM can extract & link it at runtime.
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
        println("zig toolchain available: $haveZig")
        println("native build dir: ${nativeBuildDir.get().asFile}")
    }
}
