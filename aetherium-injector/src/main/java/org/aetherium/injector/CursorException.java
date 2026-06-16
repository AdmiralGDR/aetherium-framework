/*
 * Aetherium Framework — cursor navigation/mutation failure.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.injector;

/**
 * Thrown by a {@link BytecodeCursor} when a navigation or mutation cannot be satisfied — e.g.
 * {@code findOpcode} found no match, or an edit was attempted with no instruction under the cursor.
 *
 * <p>EN: This is an <em>expected</em> failure mode, not a crash: the {@link InjectorTransformer}
 * catches it, reports a structured {@code Diagnostic}, and the bytecode engine reverts the class to
 * its original bytes. It never escapes to the JVM.
 *
 * <p>RU: Это <em>ожидаемый</em> режим отказа, а не сбой: {@link InjectorTransformer} ловит его,
 * сообщает структурированный {@code Diagnostic}, и движок байт-кода откатывает класс к исходным
 * байтам. Оно никогда не доходит до JVM.
 */
public final class CursorException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CursorException(String message) {
        super(message);
    }
}
