/*
 * Aetherium Framework — JSON-RPC (LSP) stdio transport around the backend.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.cli.lsp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Speaks the LSP base protocol ({@code Content-Length}-framed JSON-RPC) over a pair of streams.
 *
 * <p>EN: Reads {@code Content-Length: N\r\n\r\n<body>} messages, hands each parsed request to the
 * {@link LspBackend}, and writes framed responses — the transport an IDE connects to via stdio. The loop
 * exits on the {@code exit} notification or on end-of-stream. A malformed/garbage frame produces a
 * JSON-RPC parse-error response instead of tearing the server down, so a flaky client can't crash it.
 * RU: Читает сообщения {@code Content-Length: N\r\n\r\n<тело>}, передаёт каждый разобранный запрос
 * {@link LspBackend} и пишет обрамлённые ответы — транспорт, к которому IDE подключается по stdio. Цикл
 * завершается на уведомлении {@code exit} или конце потока. Битый кадр даёт ответ parse-error, а не валит
 * сервер.
 */
public final class AetheriumLspServer {

    private final LspBackend backend;

    public AetheriumLspServer(LspBackend backend) {
        this.backend = backend;
    }

    /** Run the read/dispatch/write loop until {@code exit} or end-of-input. */
    public void serve(InputStream in, OutputStream out) throws IOException {
        while (true) {
            String body = readMessage(in);
            if (body == null) {
                return; // end of stream
            }
            LspBackend.Reply reply = handle(body);
            if (reply.response() != null) {
                writeMessage(out, Json.write(reply.response()));
            }
            if (reply.stop()) {
                return;
            }
        }
    }

    /** Parse + dispatch a single message body; never throws — a bad frame becomes a parse-error reply. */
    LspBackend.Reply handle(String body) {
        Map<String, Object> request;
        try {
            request = Json.parseObject(body);
        } catch (RuntimeException malformed) {
            Map<String, Object> err = new java.util.LinkedHashMap<>();
            err.put("jsonrpc", "2.0");
            err.put("id", null);
            err.put("error", Map.of("code", -32700, "message", "parse error: " + malformed.getMessage()));
            return LspBackend.Reply.of(err);
        }
        return backend.dispatch(request);
    }

    /** Read one {@code Content-Length}-framed message body, or {@code null} at end of stream. */
    private static String readMessage(InputStream in) throws IOException {
        int contentLength = -1;
        StringBuilder header = new StringBuilder();
        int b;
        // Read header lines until a blank line (\r\n\r\n).
        while ((b = in.read()) != -1) {
            header.append((char) b);
            if (header.length() >= 4 && header.substring(header.length() - 4).equals("\r\n\r\n")) {
                break;
            }
        }
        if (b == -1 && header.length() == 0) {
            return null;
        }
        for (String line : header.toString().split("\r\n")) {
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase("Content-Length")) {
                try {
                    contentLength = Integer.parseInt(line.substring(colon + 1).trim());
                } catch (NumberFormatException ignored) {
                    contentLength = -1;
                }
            }
        }
        if (contentLength < 0) {
            return null;
        }
        byte[] buf = in.readNBytes(contentLength);
        return new String(buf, StandardCharsets.UTF_8);
    }

    /** Write {@code body} as a framed JSON-RPC message. */
    private static void writeMessage(OutputStream out, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        String header = "Content-Length: " + payload.length + "\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.US_ASCII));
        out.write(payload);
        out.flush();
    }
}
