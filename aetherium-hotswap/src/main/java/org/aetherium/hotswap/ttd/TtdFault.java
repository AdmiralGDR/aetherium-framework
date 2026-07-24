/*
 * Aetherium Framework — a captured tick fault for post-mortem time-travel inspection.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.hotswap.ttd;

/**
 * The frozen evidence of a tick that threw — the crash the Time-Travel Debugger lets you rewind from.
 *
 * <p>EN: When a guarded tick body throws, {@link TtdEngine} does not commit; instead it snapshots the
 * live (partially-mutated) arena and pairs it with the throwable and tick number here. The developer can
 * then compare {@link #faultState()} (what the arena looked like at the moment of the crash) against the
 * last known-good states reconstructed from the journal — pinpointing the exact tick and entity whose
 * value went wrong.
 *
 * <p>RU: Когда защищённое тело тика бросает исключение, {@link TtdEngine} не коммитит; вместо этого он
 * снимает живую (частично изменённую) арену и связывает её с исключением и номером тика. Разработчик
 * может сравнить {@link #faultState()} (состояние в момент краха) с последними «хорошими» состояниями,
 * реконструированными из журнала — находя точный тик и сущность с неверным значением.
 *
 * @param tick       the tick number that faulted (never committed)
 * @param faultState the live arena state captured at the moment of the throw
 * @param cause      the throwable the tick body raised
 */
public record TtdFault(long tick, ArenaSnapshot faultState, Throwable cause) {

    public String summary() {
        return "tick " + tick + " faulted: " + cause.getClass().getSimpleName()
                + (cause.getMessage() == null ? "" : ": " + cause.getMessage());
    }
}
