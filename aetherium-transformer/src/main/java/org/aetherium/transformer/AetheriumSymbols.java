/*
 * Aetherium Framework — shared symbol manifest (single source of truth for O(1) dispatch IDs).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.transformer;

import org.aetherium.core.SymbolManifest;

/**
 * The one {@link SymbolManifest} shared by the dispatch-lowering transformer (here, boot layer) and the
 * loader's dispatch table (mod layer), so the {@code invokedynamic} IDs they assign always agree.
 *
 * <p>EN: b split the transform engine out of {@code aetherium-loader} into this boot-layer
 * module. The manifest was previously a package-private constant on the engine; the loader's
 * {@code DispatchBootstrap} still needs it, and it now lives across a module boundary, so it is a small
 * <strong>public</strong> holder both sides import. Pure ({@code aetherium-core} only) — no FFM, no
 * ModLauncher, so it carries class-file minor {@code 0x0000} and loads on any JVM.
 * RU: b выделил движок трансформации из {@code aetherium-loader} в этот boot-модуль. Манифест раньше был
 * package-private константой движка; загрузчику ({@code DispatchBootstrap}) он по-прежнему нужен, а теперь
 * лежит за границей модуля — поэтому это небольшой <strong>public</strong>-holder, импортируемый обеими
 * сторонами. Чистый (только {@code aetherium-core}) — без FFM и ModLauncher, minor {@code 0x0000}.
 */
public final class AetheriumSymbols {

    private AetheriumSymbols() {
    }

    /** JVM internal name of the abstract API facade whose static calls get lowered to {@code invokedynamic}. */
    public static final String API_OWNER_INTERNAL = "org/aetherium/api/AetheriumApi";

    /** Manifest namespace for API symbols. */
    public static final String API_NAMESPACE = "compute";

    /**
     * The framework symbol manifest — the single source of truth shared by the dispatch-lowering
     * transformer and the dispatch table, so IDs always agree.
     */
    public static final SymbolManifest MANIFEST = SymbolManifest.builder()
            .add(API_NAMESPACE, "doubler", "(I)I")
            .build();
}
