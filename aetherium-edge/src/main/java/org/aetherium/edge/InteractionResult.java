/*
 * Aetherium Framework — result of a gameplay interaction hook.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.edge;

/**
 * The outcome a gameplay interaction listener returns — let vanilla proceed, or veto it.
 *
 * <p>EN: A loader-agnostic stand-in for Minecraft's {@code InteractionResult}/event cancellation. The
 * loader maps {@link #CANCEL} onto cancelling the native event (e.g. setting the event result to
 * {@code FAIL}/{@code CONSUME}); {@link #PASS} lets vanilla behavior run. No Minecraft type involved.
 * RU: Независимая от загрузчика замена {@code InteractionResult}/отмены события Minecraft. Загрузчик
 * отображает {@link #CANCEL} на отмену нативного события; {@link #PASS} пропускает ванильное поведение.
 */
public enum InteractionResult {
    /** Allow the vanilla interaction to proceed. */
    PASS,
    /** Veto the vanilla interaction (the loader cancels the native event). */
    CANCEL
}
