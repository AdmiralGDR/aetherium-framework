/*
 * aetherium-bytecode — ASM-based bytecode manipulation engine.
 *
 * EN: Knows `core` only (never the loader, never `native`). Pulls the full ASM surface from the
 *     centralized version catalog.
 * RU: Знает только `core` (никогда загрузчик, никогда `native`). Подтягивает полную поверхность
 *     ASM из централизованного каталога версий.
 */

dependencies {
    api(project(":aetherium-core"))
    implementation(libs.bundles.asm)
}
