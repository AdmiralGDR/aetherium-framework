/*
 * Aetherium Framework — the abstract sign lattice used by the contract symbolic analyzer.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.cli.contract;

import org.aetherium.injector.contract.Constraint;

/**
 * A tiny abstract domain over integral values — the state the {@link SignInterpreter} tracks.
 *
 * <p>EN: Rather than track concrete values (undecidable in general), the analyzer tracks each stack slot
 * as one of {@code NEGATIVE / ZERO / POSITIVE / UNKNOWN}. Constant pushes give a precise sign; {@code neg}
 * and integer {@code +,-,*} combine signs with the usual rules; anything the analyzer cannot follow
 * (a loaded variable, a method call result) becomes {@link #UNKNOWN}. A return whose sign is proven to lie
 * outside an {@link Constraint} is a definite violation; an {@link #UNKNOWN} return is merely unverified.
 *
 * <p>RU: Вместо конкретных значений (в общем случае неразрешимо) анализатор отслеживает каждый слот стека
 * как один из {@code NEGATIVE / ZERO / POSITIVE / UNKNOWN}. Константы дают точный знак; {@code neg} и
 * целочисленные {@code +,-,*} комбинируют знаки обычными правилами; всё, что анализатор не может
 * проследить, становится {@link #UNKNOWN}. Возврат с доказанным знаком вне {@link Constraint} — явное
 * нарушение; {@link #UNKNOWN}-возврат просто не проверен.
 */
public enum Sign {
    NEGATIVE,
    ZERO,
    POSITIVE,
    UNKNOWN;

    /** The abstract negation of this sign. */
    public Sign negate() {
        return switch (this) {
            case NEGATIVE -> POSITIVE;
            case POSITIVE -> NEGATIVE;
            case ZERO -> ZERO;
            case UNKNOWN -> UNKNOWN;
        };
    }

    /** The sign of an integer literal. */
    public static Sign of(long value) {
        if (value < 0) {
            return NEGATIVE;
        }
        return value == 0 ? ZERO : POSITIVE;
    }

    /** Abstract multiply (sign rule). */
    public static Sign mul(Sign a, Sign b) {
        if (a == ZERO || b == ZERO) {
            return ZERO;
        }
        if (a == UNKNOWN || b == UNKNOWN) {
            return UNKNOWN;
        }
        return a == b ? POSITIVE : NEGATIVE;
    }

    /** Abstract add (only precise when both operands share a sign or one is zero). */
    public static Sign add(Sign a, Sign b) {
        if (a == ZERO) {
            return b;
        }
        if (b == ZERO) {
            return a;
        }
        if (a == UNKNOWN || b == UNKNOWN) {
            return UNKNOWN;
        }
        return a == b ? a : UNKNOWN; // POSITIVE + NEGATIVE could be anything
    }

    /** Abstract subtract, defined as {@code a + (-b)}. */
    public static Sign sub(Sign a, Sign b) {
        return add(a, b.negate());
    }

    /**
     * Whether this proven sign definitely violates {@code constraint}. {@link #UNKNOWN} never
     * <em>definitely</em> violates (it is reported separately as unverified).
     */
    public boolean violates(Constraint constraint) {
        return switch (constraint) {
            case ANY -> false;
            case NON_NEGATIVE -> this == NEGATIVE;                 // must be >= 0
            case POSITIVE -> this == NEGATIVE || this == ZERO;     // must be > 0
            case NON_POSITIVE -> this == POSITIVE;                 // must be <= 0
            case NEGATIVE -> this == POSITIVE || this == ZERO;     // must be < 0
        };
    }
}
