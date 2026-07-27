/*
 * Aetherium Framework — named key codes for keybinds ().
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.ui;

/**
 * Named GLFW key codes for {@link AetheriumUi#registerKeybind}, so a mod writes {@code Keys.G} instead of a
 * magic {@code 71}.
 *
 * <p>EN: {@code registerKeybind} takes an {@code int} default key; nothing validates a hand-written number, so
 * a typo is a silent mis-binding. These constants are the GLFW codes Minecraft uses (e.g. {@link #G} is 71),
 * kept as a plain zero-dependency holder in the pure UI module — no {@code org.lwjgl} import, no game type.
 * The names match the physical keys on a US keyboard.
 * RU: Именованные коды клавиш GLFW для {@link AetheriumUi#registerKeybind}, чтобы писать {@code Keys.G}, а не
 * магическое {@code 71}. Число никто не проверяет, поэтому опечатка — тихая ошибка привязки. Это те же коды
 * GLFW, что использует Minecraft; чистый держатель констант без зависимостей (без {@code org.lwjgl}).
 */
public final class Keys {

    private Keys() {
    }

    /** No key bound (the vanilla "unbound" sentinel). */
    public static final int UNBOUND = -1;

    // Letters (GLFW_KEY_A … GLFW_KEY_Z).
    public static final int A = 65;
    public static final int B = 66;
    public static final int C = 67;
    public static final int D = 68;
    public static final int E = 69;
    public static final int F = 70;
    public static final int G = 71;
    public static final int H = 72;
    public static final int I = 73;
    public static final int J = 74;
    public static final int K = 75;
    public static final int L = 76;
    public static final int M = 77;
    public static final int N = 78;
    public static final int O = 79;
    public static final int P = 80;
    public static final int Q = 81;
    public static final int R = 82;
    public static final int S = 83;
    public static final int T = 84;
    public static final int U = 85;
    public static final int V = 86;
    public static final int W = 87;
    public static final int X = 88;
    public static final int Y = 89;
    public static final int Z = 90;

    // Number row (GLFW_KEY_0 … GLFW_KEY_9).
    public static final int NUM_0 = 48;
    public static final int NUM_1 = 49;
    public static final int NUM_2 = 50;
    public static final int NUM_3 = 51;
    public static final int NUM_4 = 52;
    public static final int NUM_5 = 53;
    public static final int NUM_6 = 54;
    public static final int NUM_7 = 55;
    public static final int NUM_8 = 56;
    public static final int NUM_9 = 57;

    // Function keys.
    public static final int F1 = 290;
    public static final int F2 = 291;
    public static final int F3 = 292;
    public static final int F4 = 293;
    public static final int F5 = 294;
    public static final int F6 = 295;
    public static final int F7 = 296;
    public static final int F8 = 297;
    public static final int F9 = 298;
    public static final int F10 = 299;
    public static final int F11 = 300;
    public static final int F12 = 301;

    // Editing + navigation.
    public static final int SPACE = 32;
    public static final int ENTER = 257;
    public static final int TAB = 258;
    public static final int BACKSPACE = 259;
    public static final int INSERT = 260;
    public static final int DELETE = 261;
    public static final int ESCAPE = 256;
    public static final int RIGHT = 262;
    public static final int LEFT = 263;
    public static final int DOWN = 264;
    public static final int UP = 265;
    public static final int PAGE_UP = 266;
    public static final int PAGE_DOWN = 267;
    public static final int HOME = 268;
    public static final int END = 269;

    // Common modifiers (the left-hand ones, which vanilla defaults to).
    public static final int LEFT_SHIFT = 340;
    public static final int LEFT_CONTROL = 341;
    public static final int LEFT_ALT = 342;
    public static final int RIGHT_SHIFT = 344;
    public static final int RIGHT_CONTROL = 345;
    public static final int RIGHT_ALT = 346;
}
