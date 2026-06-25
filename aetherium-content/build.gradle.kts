// aetherium-content — declarative content API (annotations) + the annotation processor.
//
// EN: Defines the zero-boilerplate annotations (@AetheriumBlock, @AetheriumItem) and the
//     javax.annotation.processing processor that, at compile time, (1) generates the resource JSON
//     via aetherium-datagen and (2) emits a runtime index (META-INF/aetherium/content.index) the
//     loader reads to auto-register content. Pure Java — no Minecraft/NeoForge types.
// RU: Определяет аннотации без шаблонного кода (@AetheriumBlock, @AetheriumItem) и процессор
//     javax.annotation.processing, который во время компиляции (1) генерирует JSON ресурсов через
//     aetherium-datagen и (2) пишет рантайм-индекс (META-INF/aetherium/content.index), читаемый
//     загрузчиком для авто-регистрации контента. Чистая Java — без типов Minecraft/NeoForge.
dependencies {
    api(project(":aetherium-core"))
    api(project(":aetherium-datagen"))

    testImplementation(libs.junit.jupiter)
}
