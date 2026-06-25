// aetherium-ui — declarative, Flexbox-like cross-platform GUI framework.
//
// EN: STRICTLY PURE. No net.minecraft / net.neoforged types. It is a declarative widget tree + a
//     Flexbox layout engine + a tiny renderer SPI (UiRenderer) the loader implements over the platform's
//     GuiGraphics/DrawContext. A mod describes its screen once, declaratively; the loader paints it. The
//     same tree lays out and hit-tests offline (no game), which is what the self-test exercises.
// RU: СТРОГО ЧИСТЫЙ. Без типов net.minecraft / net.neoforged. Декларативное дерево виджетов + движок
//     раскладки Flexbox + крошечный SPI рендерера (UiRenderer), реализуемый загрузчиком поверх
//     GuiGraphics/DrawContext платформы. Мод описывает экран декларативно один раз; загрузчик рисует.
dependencies {
    api(project(":aetherium-core"))

    testImplementation(libs.junit.jupiter)
}
