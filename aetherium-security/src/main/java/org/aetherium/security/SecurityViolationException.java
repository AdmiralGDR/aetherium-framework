/*
 * Aetherium Framework — security violation (contained, never crashes the host).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.security;

/**
 * Thrown when a mod attempts an action it lacks the {@link Capability} for, or an FFM access that would
 * escape its granted bounds.
 *
 * <p>EN: A structured, catchable signal — the framework's guards throw it instead of letting an
 * unauthorized reflective access or out-of-bounds memory write proceed. Callers (the loader) contain it
 * the same way they contain a transform failure, so a misbehaving mod is isolated, not fatal.
 *
 * <p>RU: Структурированный перехватываемый сигнал — охраны фреймворка бросают его вместо того, чтобы
 * пропустить несанкционированный рефлексивный доступ или запись за границами памяти.
 */
public final class SecurityViolationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SecurityViolationException(String message) {
        super(message);
    }
}
