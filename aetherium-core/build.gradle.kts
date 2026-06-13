/*
 * aetherium-core — the stable, loader-agnostic API. THE LEAF MODULE.
 *
 * EN: Depends on NOTHING internal (ARCHITECTURE.md ). It may only use the JDK (incl. the FFM
 *     preview API). Adding an internal `project(...)` dependency here is a design violation.
 * RU: Не зависит НИ ОТ ЧЕГО внутреннего (ARCHITECTURE.md ). Может использовать только JDK
 *     (включая preview-API FFM). Добавление внутренней зависимости `project(...)` сюда —
 *     нарушение дизайна.
 */

// Intentionally no internal dependencies. core is the leaf.
dependencies {
    // testImplementation(libs.junit.jupiter)   // wired in a later phase
}
