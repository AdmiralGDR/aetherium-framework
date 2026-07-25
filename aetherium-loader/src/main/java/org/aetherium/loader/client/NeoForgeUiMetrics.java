/*
 * Aetherium Framework — Font-backed UiMetrics (client).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.loader.client;

import net.minecraft.client.gui.Font;
import org.aetherium.ui.UiMetrics;

/**
 * Font-accurate {@link UiMetrics} over Minecraft's {@code Font}.
 *
 * <p>EN: stressed this: the offline {@code UiMetrics.DEFAULT} approximates 6px per char, which is
 * wrong for a real font and <em>very</em> wrong for Cyrillic. In game we measure with the actual
 * {@code Font.width(String)} and {@code Font.lineHeight}, so layout matches the glyphs the player sees.
 * RU: подчёркивает это: офлайн-метрика 6px/символ сильно врёт, особенно для кириллицы. В игре измеряем
 * настоящим {@code Font.width}/{@code Font.lineHeight}, поэтому раскладка совпадает с реальными глифами.
 */
final class NeoForgeUiMetrics implements UiMetrics {

    private final Font font;

    NeoForgeUiMetrics(Font font) {
        this.font = font;
    }

    @Override
    public int textWidth(String text) {
        return text == null ? 0 : font.width(text);
    }

    @Override
    public int lineHeight() {
        return font.lineHeight;
    }
}
