/*
 * Aetherium Framework — LSP backend self-test (completion + conflict prediction + RPC framing).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.cli.lsp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Exercises the Language Server backend end-to-end without an IDE attached.
 *
 * <p>EN: Confirms (1) completion surfaces real vanilla injection points, (2) the conflict predictor flags
 * an ordering cycle <em>and</em> competing cancellations while passing a clean set, and (3) a full
 * {@code Content-Length}-framed JSON-RPC {@code initialize} round-trips through {@link AetheriumLspServer}.
 * The CLI {@code lsp} command renders the result; {@code lsp --serve} enters the real stdio loop instead.
 * RU: Проверяет, что (1) автодополнение выдаёт реальные ванильные точки инъекции, (2) предиктор отмечает
 * цикл порядка <em>и</em> конкурирующие отмены, пропуская чистый набор, и (3) полный {@code initialize} с
 * обрамлением {@code Content-Length} проходит туда-обратно через {@link AetheriumLspServer}.
 */
public final class LspSelfTest {

    private LspSelfTest() {
    }

    public static Result run() {
        List<String> notes = new ArrayList<>();
        LspBackend backend = new LspBackend();

        // 1) Completion returns real vanilla targets.
        List<Map<String, Object>> tickItems = backend.completion("tick");
        boolean completionOk = !tickItems.isEmpty()
                && tickItems.stream().anyMatch(i -> String.valueOf(i.get("label")).contains("Entity::tick"));
        notes.add("completion('tick') → " + tickItems.size() + " items (e.g. "
                + (tickItems.isEmpty() ? "—" : tickItems.get(0).get("label")) + ")");

        // 2a) A real ordering cycle is predicted before compilation.
        List<DeclaredHook> cyclic = List.of(
                hook("a", "net.minecraft.world.entity.Entity::tick", "HEAD", false, List.of("b"), List.of()),
                hook("b", "net.minecraft.world.entity.Entity::tick", "HEAD", false, List.of("a"), List.of()));
        List<ConflictPredictor.Conflict> cyc = ConflictPredictor.predict(cyclic);
        boolean cycleDetected = cyc.stream().anyMatch(c -> c.code().equals("ordering-cycle"));
        notes.add("predictConflicts(cyclic) → " + codes(cyc));

        // 2b) A clean, well-ordered set produces no conflicts.
        List<DeclaredHook> clean = List.of(
                hook("core", "net.minecraft.world.entity.Entity::tick", "HEAD", false, List.of(), List.of()),
                hook("phys", "net.minecraft.world.entity.Entity::tick", "HEAD", false, List.of(), List.of("core")));
        boolean cleanOk = ConflictPredictor.predict(clean).isEmpty();

        // 2c) Two cancelling hooks at one anchor raise a (reviewable) warning.
        List<DeclaredHook> cancels = List.of(
                hook("shield", "net.minecraft.world.entity.player.Player::hurt", "HEAD", true, List.of(), List.of()),
                hook("armor", "net.minecraft.world.entity.player.Player::hurt", "HEAD", true, List.of(), List.of()));
        List<ConflictPredictor.Conflict> can = ConflictPredictor.predict(cancels);
        boolean cancelWarned = can.stream().anyMatch(c -> c.code().equals("competing-cancel"));
        notes.add("predictConflicts(2×cancel @Player::hurt) → " + codes(can));

        // 3) A framed JSON-RPC initialize round-trips through the transport.
        boolean rpcOk = rpcRoundTrips(backend, notes);

        int knownTargets = VanillaMethodIndex.all().size();
        boolean passed = completionOk && cycleDetected && cleanOk && cancelWarned && rpcOk;
        return new Result(completionOk, cycleDetected, cleanOk, cancelWarned, rpcOk,
                knownTargets, tickItems.size(), notes, passed);
    }

    private static boolean rpcRoundTrips(LspBackend backend, List<String> notes) {
        try {
            String init = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";
            String exit = "{\"jsonrpc\":\"2.0\",\"method\":\"exit\"}";
            byte[] input = (frame(init) + frame(exit)).getBytes(StandardCharsets.UTF_8);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            new AetheriumLspServer(backend).serve(new ByteArrayInputStream(input), out);

            String response = out.toString(StandardCharsets.UTF_8);
            boolean framed = response.startsWith("Content-Length:");
            String json = response.substring(response.indexOf("\r\n\r\n") + 4);
            Map<String, Object> parsed = Json.parseObject(json);
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) parsed.get("result");
            boolean hasCaps = result != null && result.get("capabilities") instanceof Map;
            notes.add("RPC initialize → framed=" + framed + ", capabilities advertised=" + hasCaps);
            return framed && hasCaps;
        } catch (Exception e) {
            notes.add("RPC round-trip failed: " + e);
            return false;
        }
    }

    private static String frame(String body) {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        return "Content-Length: " + b.length + "\r\n\r\n" + body;
    }

    private static DeclaredHook hook(String id, String target, String anchor, boolean cancels,
                                     List<String> before, List<String> after) {
        return new DeclaredHook(id, target, anchor, cancels, before, after);
    }

    private static String codes(List<ConflictPredictor.Conflict> conflicts) {
        return conflicts.isEmpty() ? "(none)"
                : conflicts.stream().map(ConflictPredictor.Conflict::code).toList().toString();
    }

    /** Outcome of the LSP self-test, rendered by the CLI {@code lsp} command. */
    public record Result(boolean completionOk, boolean cycleDetected, boolean cleanOk,
                         boolean cancelWarned, boolean rpcOk, int knownTargets, int completionCount,
                         List<String> notes, boolean passed) {
    }
}
