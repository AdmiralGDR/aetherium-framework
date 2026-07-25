// aetherium-verify — in-game mod verification & analysis.
//
// EN: The runtime companion to the Shield: enumerate the loaded Aetherium mods, verify each against its
//     ship-time integrity manifest (SHA-256 + the native guard), read its author watermark, and render it all
//     in a scrollable in-game inspector screen (built on the pure aetherium-ui). Loader-agnostic and pure —
//     no net.minecraft — so the whole inspector is testable from a plain main.
// RU: Рантайм-компаньон Щита: перечислить загруженные моды Aetherium, проверить каждый против манифеста
//     целостности (SHA-256 + нативный гард), прочитать водяной знак автора и показать всё в прокручиваемом
//     внутриигровом экране-инспекторе (на чистом aetherium-ui). Независим от загрузчика и чист — без
//     net.minecraft — поэтому инспектор тестируется из обычного main.
dependencies {
    api(project(":aetherium-core"))
    api(project(":aetherium-shield"))   // ModVerifier, NativeGuard, WatermarkAttribute
    api(project(":aetherium-ui"))        // AetheriumScreen inspector
    api(project(":aetherium-datagen"))   // ContentIndex (content counts per mod)
    implementation(project(":aetherium-injector")) // HookTable (framework-level injected-hook count)

    testImplementation(libs.junit.jupiter)
}
