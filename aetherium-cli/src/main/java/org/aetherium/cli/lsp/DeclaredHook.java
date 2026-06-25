/*
 * Aetherium Framework — a mod-declared hook as seen by the LSP (pre-compile).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.cli.lsp;

import java.util.List;

/**
 * One hook a mod intends to install, described well enough to predict conflicts before compilation.
 *
 * <p>EN: The IDE sends these (parsed from the mod's source or DSL blocks); the {@link ConflictPredictor}
 * reasons over them — running the very same DAG sort the loader uses — to flag cycles, duplicate ids,
 * invalid anchors, and competing cancellations <em>before</em> the modder builds. Pure data.
 * RU: IDE присылает их (разобранные из исходника/DSL-блоков мода); {@link ConflictPredictor} рассуждает
 * над ними — той же DAG-сортировкой, что и загрузчик — чтобы отметить циклы, дубли id, неверные якоря и
 * конкурирующие отмены <em>до</em> сборки. Чистые данные.
 *
 * @param id        the hook's stable id
 * @param target    the {@code owner::method} it attaches to
 * @param anchor    the anchor ({@code HEAD} / {@code RETURN})
 * @param cancels   whether the hook may cancel the vanilla method
 * @param runBefore ids this hook must precede
 * @param runAfter  ids this hook must follow
 */
public record DeclaredHook(String id, String target, String anchor, boolean cancels,
                           List<String> runBefore, List<String> runAfter) {

    public DeclaredHook {
        runBefore = List.copyOf(runBefore);
        runAfter = List.copyOf(runAfter);
    }

    /** Hooks sharing this key compete for the same attachment site and share one cancellation epilogue. */
    public String group() {
        return target + "@" + anchor;
    }
}
