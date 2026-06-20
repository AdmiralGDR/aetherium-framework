/*
 * Aetherium Framework — security capabilities (the unit of authority).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.security;

/**
 * The discrete privileges a mod can be granted — the vocabulary of the capability model.
 *
 * <p>EN: Aetherium replaces the JVM's removed {@code SecurityManager} with an explicit, capability-based
 * model: a mod holds <em>only</em> the authorities it was granted, and the framework checks the relevant
 * {@link Capability} before performing a sensitive action on the mod's behalf. Default is deny — an
 * ungranted capability is a {@link SecurityViolationException}. This is the enforcement vocabulary for
 * the CIA triad: Confidentiality ({@link #REFLECTION} into framework internals is refused),
 * Integrity ({@link #NATIVE_MEMORY} accesses are bounds-checked), and Availability (a contained
 * violation never crashes the host).
 *
 * <p>RU: Aetherium заменяет удалённый {@code SecurityManager} явной моделью на основе возможностей: мод
 * обладает <em>только</em> выданными полномочиями, и фреймворк проверяет соответствующую
 * {@link Capability} перед чувствительным действием от имени мода. По умолчанию — запрет. Это словарь
 * принуждения для триады CIA: конфиденциальность ({@link #REFLECTION} во внутренности фреймворка
 * запрещена), целостность ({@link #NATIVE_MEMORY} с проверкой границ) и доступность (локализованное
 * нарушение не роняет хост).
 */
public enum Capability {

    /** Deep reflection ({@code setAccessible}, private access). Never grants reach into framework internals. */
    REFLECTION,

    /** Direct off-heap (FFM) memory access — always mediated by {@link GuardedSegment} bounds checks. */
    NATIVE_MEMORY,

    /** Defining new classes at runtime (bytecode generation). */
    DEFINE_CLASS,

    /** Reading files outside the mod's own data directory. */
    FILE_READ,

    /** Writing files outside the mod's own data directory. */
    FILE_WRITE,

    /** Opening outbound network connections. */
    NETWORK
}
