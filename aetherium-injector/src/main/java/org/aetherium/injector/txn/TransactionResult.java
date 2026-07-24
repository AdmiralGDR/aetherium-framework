/*
 * Aetherium Framework — the outcome of applying one mod's hooks as a single ACID transaction.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.injector.txn;

import org.aetherium.core.Diagnostic;

import java.util.List;
import java.util.Map;

/**
 * The all-or-nothing result of applying a single mod's hook transaction.
 *
 * <p>EN: A mod's entire set of hooks is one ACID transaction: it either {@link Status#COMMITTED}
 * (every targeted class verified and its transformed bytes are published), or it is
 * {@link Status#ROLLED_BACK} (at least one hook failed the verification sandbox, so <em>every</em>
 * already-applied hook of that same mod is discarded and the mod is disabled — the game never sees a
 * partially-injected mod). {@link #appliedBeforeAbort()} records how many hooks had already verified
 * cleanly at the moment the transaction aborted; those are exactly the edits that were rolled back.
 *
 * <p>RU: Весь набор хуков мода — одна ACID-транзакция: либо {@link Status#COMMITTED} (каждый целевой
 * класс прошёл верификацию, и его преобразованные байты публикуются), либо {@link Status#ROLLED_BACK}
 * (хотя бы один хук не прошёл песочницу верификации, поэтому <em>каждый</em> уже применённый хук того
 * же мода отбрасывается, а мод отключается — игра никогда не видит частично внедрённый мод).
 * {@link #appliedBeforeAbort()} фиксирует, сколько хуков уже чисто верифицировалось в момент отмены;
 * именно эти правки были откачены.
 *
 * @param modId             the owning mod
 * @param status            COMMITTED (published) or ROLLED_BACK (disabled)
 * @param hookCount         the number of targeted classes (hooks) in the transaction
 * @param appliedBeforeAbort how many hooks verified cleanly before an abort (0 for a clean commit path counts all)
 * @param failedClass       the class whose hook failed verification, or {@code null} on commit
 * @param committedBytes    the published transformed class bytes (non-empty only when COMMITTED)
 * @param diagnostics       contained diagnostics emitted by the sandbox on the failing hook
 * @param log               human-readable, bilingual-friendly trace lines
 */
public record TransactionResult(String modId,
                                Status status,
                                int hookCount,
                                int appliedBeforeAbort,
                                String failedClass,
                                Map<String, byte[]> committedBytes,
                                List<Diagnostic> diagnostics,
                                List<String> log) {

    /** Whether a mod's transaction was published in full or discarded in full. */
    public enum Status {
        /** Every hook verified; the transformed classes are published and the hooks installed. */
        COMMITTED,
        /** A hook failed verification; the whole mod was rolled back and disabled. */
        ROLLED_BACK
    }

    public boolean committed() {
        return status == Status.COMMITTED;
    }

    public boolean rolledBack() {
        return status == Status.ROLLED_BACK;
    }
}
