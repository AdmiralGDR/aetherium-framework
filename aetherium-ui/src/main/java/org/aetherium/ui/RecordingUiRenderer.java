/*
 * Aetherium Framework — a UiRenderer that records draw calls (offline rendering / tests).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link UiRenderer} that captures every draw call as a {@link Cmd} instead of painting — so a screen
 * can be laid out and "rendered" with no game present (the self-test and golden-image checks use it).
 */
public final class RecordingUiRenderer implements UiRenderer {

    /** One recorded draw call. */
    public record Cmd(String kind, int x, int y, int w, int h, String text, int argb) {
    }

    private final List<Cmd> commands = new ArrayList<>();

    @Override
    public void fillRect(int x, int y, int width, int height, int argb) {
        commands.add(new Cmd("fill", x, y, width, height, null, argb));
    }

    @Override
    public void drawText(int x, int y, String text, int argb) {
        commands.add(new Cmd("text", x, y, 0, 0, text, argb));
    }

    @Override
    public void pushClip(int x, int y, int width, int height) {
        commands.add(new Cmd("clip", x, y, width, height, null, 0));
    }

    @Override
    public void popClip() {
        commands.add(new Cmd("unclip", 0, 0, 0, 0, null, 0));
    }

    public List<Cmd> commands() {
        return List.copyOf(commands);
    }

    public int fillCount() {
        return count("fill");
    }

    public int textCount() {
        return count("text");
    }

    public int clipCount() {
        return count("clip");
    }

    private int count(String kind) {
        return (int) commands.stream().filter(c -> c.kind().equals(kind)).count();
    }
}
