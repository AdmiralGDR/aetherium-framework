/*
 * Aetherium Framework — declarative UI framework package.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */

/**
 * EN: A declarative, Flexbox-like GUI framework with <strong>no Minecraft imports</strong>. A mod builds
 * a screen as a {@link org.aetherium.ui.Widget} tree via the {@link org.aetherium.ui.Ui} factory
 * ({@code column}/{@code row}/{@code label}/{@code button}/{@code spacer}), the
 * {@link org.aetherium.ui.FlexLayout} engine computes absolute boxes, and {@link org.aetherium.ui.UiRuntime}
 * paints + hit-tests through a two-method {@link org.aetherium.ui.UiRenderer} SPI the loader implements
 * over {@code GuiGraphics}. Layout, paint, and click dispatch all run offline (see
 * {@link org.aetherium.ui.UiSelfTest}).
 *
 * <p>RU: Декларативный Flexbox-подобный GUI-фреймворк <strong>без импортов Minecraft</strong>. Мод строит
 * экран как дерево {@link org.aetherium.ui.Widget} через фабрику {@link org.aetherium.ui.Ui}, движок
 * {@link org.aetherium.ui.FlexLayout} вычисляет боксы, а {@link org.aetherium.ui.UiRuntime} рисует и
 * обрабатывает клики через SPI {@link org.aetherium.ui.UiRenderer} из двух методов, реализуемый
 * загрузчиком поверх {@code GuiGraphics}. Раскладка, отрисовка и клики работают офлайн.
 */
package org.aetherium.ui;
