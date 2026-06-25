/*
 * Aetherium Framework — the Language Server Protocol backend logic.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.cli.lsp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Aetherium Language Server's request handlers — autocomplete for vanilla injection points and
 * pre-compile hook-conflict prediction.
 *
 * <p>EN: This is the loader-agnostic "brain" the {@link AetheriumLspServer} wraps with JSON-RPC framing.
 * It answers standard {@code initialize} / {@code textDocument/completion} plus two Aetherium extensions:
 * {@code aetherium/injectionPoints} (the valid targets for a class) and {@code aetherium/predictConflicts}
 * (run the real DAG over declared hooks and return diagnostics). Keeping the logic free of any stream/IO
 * makes it directly unit-testable; the server only adds transport.
 * RU: Loader-агностичный «мозг», который {@link AetheriumLspServer} оборачивает JSON-RPC-обрамлением.
 * Отвечает на стандартные {@code initialize} / {@code textDocument/completion} и два расширения Aetherium:
 * {@code aetherium/injectionPoints} и {@code aetherium/predictConflicts} (реальный DAG → диагностики).
 */
public final class LspBackend {

    /** A dispatch outcome: the JSON-RPC response object (or {@code null} for a notification), and stop flag. */
    public record Reply(Map<String, Object> response, boolean stop) {
        static Reply of(Map<String, Object> response) {
            return new Reply(response, false);
        }

        static Reply notification() {
            return new Reply(null, false);
        }

        static Reply exit() {
            return new Reply(null, true);
        }
    }

    /** Dispatch one parsed JSON-RPC request, returning the response envelope to send (or none). */
    @SuppressWarnings("unchecked")
    public Reply dispatch(Map<String, Object> request) {
        Object id = request.get("id");
        String method = String.valueOf(request.get("method"));
        Map<String, Object> params = request.get("params") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();

        return switch (method) {
            case "initialize" -> Reply.of(ok(id, serverCapabilities()));
            case "initialized" -> Reply.notification();
            case "textDocument/completion" ->
                    Reply.of(ok(id, Map.of("isIncomplete", false, "items", completion(prefixOf(params)))));
            case "aetherium/injectionPoints" ->
                    Reply.of(ok(id, Map.of("points", injectionPoints(str(params.get("owner"))))));
            case "aetherium/predictConflicts" ->
                    Reply.of(ok(id, Map.of("diagnostics", predict(parseHooks(params.get("hooks"))))));
            case "shutdown" -> Reply.of(ok(id, null));
            case "exit" -> Reply.exit();
            default -> id == null ? Reply.notification()
                    : Reply.of(error(id, -32601, "method not found: " + method));
        };
    }

    // ---- features ---------------------------------------------------------------------------------

    /** Completion items for known vanilla injection points matching {@code prefix}. */
    public List<Map<String, Object>> completion(String prefix) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (InjectionPoint p : VanillaMethodIndex.complete(prefix)) {
            items.add(p.toCompletionItem());
        }
        return items;
    }

    /** The valid injection points declared on {@code owner} (internal or dotted name). */
    public List<Map<String, Object>> injectionPoints(String owner) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (InjectionPoint p : VanillaMethodIndex.forOwner(owner)) {
            out.add(p.toCompletionItem());
        }
        return out;
    }

    /** Run the real conflict predictor over declared hooks and return LSP diagnostics. */
    public List<Map<String, Object>> predict(List<DeclaredHook> hooks) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ConflictPredictor.Conflict c : ConflictPredictor.predict(hooks)) {
            out.add(c.toDiagnostic());
        }
        return out;
    }

    /** Parse the {@code hooks} param (a JSON array of objects) into {@link DeclaredHook}s. */
    @SuppressWarnings("unchecked")
    public List<DeclaredHook> parseHooks(Object hooksParam) {
        List<DeclaredHook> hooks = new ArrayList<>();
        if (!(hooksParam instanceof List<?> list)) {
            return hooks;
        }
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) {
                continue;
            }
            Map<String, Object> h = (Map<String, Object>) m;
            hooks.add(new DeclaredHook(
                    str(h.get("id")),
                    str(h.get("target")),
                    h.get("anchor") == null ? "HEAD" : str(h.get("anchor")),
                    Boolean.TRUE.equals(h.get("cancels")),
                    strList(h.get("runBefore")),
                    strList(h.get("runAfter"))));
        }
        return hooks;
    }

    /** The capabilities object returned from {@code initialize}. */
    public Map<String, Object> serverCapabilities() {
        Map<String, Object> caps = new LinkedHashMap<>();
        caps.put("completionProvider", Map.of("triggerCharacters", List.of(":", ".")));
        caps.put("textDocumentSync", 1);
        Map<String, Object> aetherium = new LinkedHashMap<>();
        aetherium.put("injectionPoints", true);
        aetherium.put("predictConflicts", true);
        aetherium.put("knownTargets", VanillaMethodIndex.all().size());
        caps.put("aetherium", aetherium);
        return Map.of("capabilities", caps,
                "serverInfo", Map.of("name", "aetherium-lsp", "version", "1"));
    }

    // ---- JSON-RPC envelopes -----------------------------------------------------------------------

    private static Map<String, Object> ok(Object id, Object result) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("jsonrpc", "2.0");
        r.put("id", id);
        r.put("result", result);
        return r;
    }

    private static Map<String, Object> error(Object id, int code, String message) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("jsonrpc", "2.0");
        r.put("id", id);
        r.put("error", Map.of("code", code, "message", message));
        return r;
    }

    private static String prefixOf(Map<String, Object> params) {
        // Accept either a plain {prefix} or an LSP {context:{...}} — we just need the typed text.
        Object prefix = params.get("prefix");
        if (prefix != null) {
            return str(prefix);
        }
        Object query = params.get("query");
        return query == null ? "" : str(query);
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<String> strList(Object o) {
        if (!(o instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>(list.size());
        for (Object e : list) {
            out.add(str(e));
        }
        return out;
    }
}
