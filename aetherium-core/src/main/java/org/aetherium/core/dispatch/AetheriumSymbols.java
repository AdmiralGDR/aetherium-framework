/*
 * Aetherium Framework — shared symbol manifest (single source of truth for O(1) dispatch IDs).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.core.dispatch;

import org.aetherium.core.SymbolManifest;

/**
 * The one {@link SymbolManifest} shared by the dispatch-lowering transformer (boot layer) and the loader's /
 * Fabric's dispatch table (mod layer), so the {@code invokedynamic} IDs they assign always agree.
 *
 * <p>EN: moved this from {@code aetherium-transformer} to {@code aetherium-core}. The transformer's
 * embedded copy of {@code aetherium-core} is relocated to {@code org/aetherium/boot/…} to stop the boot-layer
 * jar and the loader's Jar-in-Jar copies from exporting the same packages (the {@code ResolutionException}
 * that crashed the launch). If this holder still lived in the transformer, its {@link SymbolManifest}-typed
 * {@code MANIFEST} would resolve to the relocated boot type and the loader/Fabric — which reference their OWN
 * (non-relocated) core — would not link. Living in core, each side reads its own copy; the manifests have
 * identical content (same symbols → same IDs), so the table (loader populates) and the lowered sites
 * (transformer writes) still agree. Pure ({@code aetherium-core} only) — no FFM, no ModLauncher.
 * RU: перенёс это из {@code aetherium-transformer} в {@code aetherium-core}. Встроенная в трансформер копия
 * core релоцируется в {@code org/aetherium/boot/…}, чтобы boot-jar и Jar-in-Jar-копии загрузчика не
 * экспортировали одни и те же пакеты (тот самый {@code ResolutionException}). Если бы holder остался в
 * трансформере, его {@code MANIFEST} типа {@link SymbolManifest} указывал бы на релоцированный boot-тип, и
 * загрузчик/Fabric (ссылающиеся на СВОЮ, нерелоцированную core) не слинковались бы. В core каждая сторона
 * читает свою копию; содержимое манифестов идентично (те же символы → те же ID), поэтому таблица и точки
 * согласованы. Чистый (только core) — без FFM и ModLauncher.
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
