/*
 * Aetherium Framework — predicts hook conflicts before compilation (LSP diagnostics).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.cli.lsp;

import org.aetherium.injector.HookCycleException;
import org.aetherium.injector.LiveHookGraph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Predicts hook ordering/anchor conflicts <em>before</em> the modder compiles — the LSP's headline value.
 *
 * <p>EN: Given the set of {@link DeclaredHook}s an IDE has parsed, this reasons over them using the
 * <strong>exact same DAG engine the loader runs at weave time</strong> ({@link LiveHookGraph} →
 * {@code HookDag}), so a predicted "OK" matches runtime behavior. It reports: duplicate ids; ordering
 * cycles (per attachment group, the real {@link HookCycleException}); invalid anchors for known vanilla
 * targets ({@link VanillaMethodIndex}); and multiple cancelling hooks sharing one anchor (which the
 * Semantic Merger will compose, but the author should review). Each finding is an LSP-shaped diagnostic.
 * RU: По набору {@link DeclaredHook}, разобранных IDE, рассуждает <strong>тем же DAG-движком, что и
 * загрузчик при вплетении</strong> ({@link LiveHookGraph} → {@code HookDag}), поэтому предсказанное «OK»
 * совпадает с рантаймом. Сообщает: дубли id; циклы порядка (настоящий {@link HookCycleException});
 * неверные якоря для известных целей; и несколько отменяющих хуков на одном якоре.
 */
public final class ConflictPredictor {

    private ConflictPredictor() {
    }

    /** Severity levels mapped onto LSP diagnostic severities (1=Error, 2=Warning). */
    public enum Severity { ERROR, WARNING }

    /** A single predicted problem. */
    public record Conflict(Severity severity, String code, String message, List<String> hookIds) {
        public Conflict {
            hookIds = List.copyOf(hookIds);
        }

        /** Render as an LSP-style diagnostic object. */
        public Map<String, Object> toDiagnostic() {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("severity", severity == Severity.ERROR ? 1 : 2);
            d.put("code", code);
            d.put("message", message);
            d.put("source", "aetherium");
            d.put("relatedHooks", hookIds);
            return d;
        }
    }

    /** Analyze the whole declaration set and return every predicted conflict (empty == clean). */
    public static List<Conflict> predict(List<DeclaredHook> hooks) {
        List<Conflict> out = new ArrayList<>();
        reportDuplicateIds(hooks, out);
        reportInvalidAnchors(hooks, out);

        // Group by attachment site; each group is one merged hook block at weave time.
        Map<String, List<DeclaredHook>> byGroup = new LinkedHashMap<>();
        for (DeclaredHook h : hooks) {
            byGroup.computeIfAbsent(h.group(), g -> new ArrayList<>()).add(h);
        }
        for (Map.Entry<String, List<DeclaredHook>> e : byGroup.entrySet()) {
            reportCycles(e.getValue(), out);
            reportCompetingCancellations(e.getKey(), e.getValue(), out);
        }
        return out;
    }

    private static void reportDuplicateIds(List<DeclaredHook> hooks, List<Conflict> out) {
        Set<String> seen = new HashSet<>();
        Set<String> dup = new HashSet<>();
        for (DeclaredHook h : hooks) {
            if (!seen.add(h.id())) {
                dup.add(h.id());
            }
        }
        for (String id : dup) {
            out.add(new Conflict(Severity.ERROR, "duplicate-id",
                    "hook id '" + id + "' is declared more than once; ids must be unique", List.of(id)));
        }
    }

    private static void reportInvalidAnchors(List<DeclaredHook> hooks, List<Conflict> out) {
        for (DeclaredHook h : hooks) {
            // Only judge anchors for targets we actually know; unknown targets are not flagged.
            if (!VanillaMethodIndex.forOwner(ownerOf(h.target())).isEmpty()
                    && !VanillaMethodIndex.isValidAnchor(h.target(), h.anchor())) {
                out.add(new Conflict(Severity.WARNING, "invalid-anchor",
                        "anchor '" + h.anchor() + "' is not a known-valid attachment for " + h.target(),
                        List.of(h.id())));
            }
        }
    }

    /** Run the real DAG sort over a group; a {@link HookCycleException} is a genuine, pre-compile cycle. */
    private static void reportCycles(List<DeclaredHook> group, List<Conflict> out) {
        LiveHookGraph graph = new LiveHookGraph();
        // Register first so runBefore/runAfter never fail on a forward reference within the group.
        for (DeclaredHook h : group) {
            graph.register(h.id(), ctx -> { /* shape only — predictor needs structure, not behavior */ });
        }
        for (DeclaredHook h : group) {
            if (!h.runBefore().isEmpty()) {
                graph.runBefore(h.id(), h.runBefore().toArray(String[]::new));
            }
            if (!h.runAfter().isEmpty()) {
                graph.runAfter(h.id(), h.runAfter().toArray(String[]::new));
            }
        }
        try {
            graph.resolve();
        } catch (HookCycleException cycle) {
            out.add(new Conflict(Severity.ERROR, "ordering-cycle",
                    "cyclic runBefore/runAfter constraints: " + cycle.getMessage(),
                    group.stream().map(DeclaredHook::id).toList()));
        }
    }

    private static void reportCompetingCancellations(String group, List<DeclaredHook> hooks, List<Conflict> out) {
        List<String> cancellers = hooks.stream().filter(DeclaredHook::cancels).map(DeclaredHook::id).toList();
        if (cancellers.size() > 1) {
            out.add(new Conflict(Severity.WARNING, "competing-cancel",
                    cancellers.size() + " hooks at " + group + " can each cancel the vanilla method; the "
                            + "Semantic Merger composes them (all run; the last cancel sets the return) — "
                            + "review the order", cancellers));
        }
    }

    private static String ownerOf(String target) {
        int i = target.indexOf("::");
        return i < 0 ? target : target.substring(0, i);
    }
}
