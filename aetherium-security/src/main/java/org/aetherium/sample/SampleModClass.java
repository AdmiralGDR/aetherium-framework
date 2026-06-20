/*
 * Aetherium Framework — a stand-in "mod's own class" for the security self-test.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.sample;

/**
 * EN: A non-protected class representing a mod's own code. The reflection guard must <em>allow</em> a
 * capability-holding mod to reflect into this (its own state) while still refusing framework internals.
 *
 * RU: Незащищённый класс, представляющий собственный код мода. Охрана рефлексии должна <em>разрешать</em>
 * моду с возможностью рефлексию сюда (его собственное состояние), всё ещё отказывая во внутренностях.
 */
public final class SampleModClass {

    @SuppressWarnings("unused")
    private int value;
}
