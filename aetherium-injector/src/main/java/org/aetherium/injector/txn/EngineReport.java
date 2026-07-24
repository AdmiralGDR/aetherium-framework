/*
 * Aetherium Framework — the aggregate result of a transactional injection pass over many mods.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.injector.txn;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The aggregate outcome of applying every registered mod's hook transaction, mod by mod.
 *
 * <p>EN: Each mod either commits or rolls back <em>independently</em>; a rolled-back mod is simply
 * disabled and never contributes bytes, while the others proceed (Availability). {@link #published}
 * exposes the effective, post-transaction class table — the transformed bytes of a class that some
 * committed mod produced, and {@link Optional#empty()} for a class only touched by rolled-back mods
 * (the loader then keeps the vanilla bytes). This is the ACID isolation boundary: the published table
 * never contains a partially-applied mod.
 *
 * <p>RU: Каждый мод коммитится или откатывается <em>независимо</em>; откаченный мод просто отключается
 * и не даёт байтов, остальные продолжают (доступность). {@link #published} отдаёт эффективную таблицу
 * классов после транзакции — преобразованные байты класса, произведённые каким-то закоммиченным модом,
 * и {@link Optional#empty()} для класса, к которому прикоснулись только откаченные моды (загрузчик тогда
 * оставляет ванильные байты). Опубликованная таблица никогда не содержит частично применённый мод.
 *
 * @param results          per-mod transaction results, in registration order
 * @param publishedClasses effective transformed bytes keyed by binary class name (committed mods only)
 */
public record EngineReport(Map<String, TransactionResult> results,
                           Map<String, byte[]> publishedClasses) {

    public EngineReport {
        results = Map.copyOf(results);
        // Defensive shallow copy of the class table (byte[] values are treated as immutable by contract).
        publishedClasses = new LinkedHashMap<>(publishedClasses);
    }

    /** The effective published bytes for a class, or empty if no committed mod produced them. */
    public Optional<byte[]> published(String binaryName) {
        return Optional.ofNullable(publishedClasses.get(binaryName));
    }

    public List<TransactionResult> committed() {
        return results.values().stream().filter(TransactionResult::committed).toList();
    }

    public List<TransactionResult> rolledBack() {
        return results.values().stream().filter(TransactionResult::rolledBack).toList();
    }

    public int committedCount() {
        return committed().size();
    }

    public int rolledBackCount() {
        return rolledBack().size();
    }
}
