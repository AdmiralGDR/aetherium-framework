/*
 * Aetherium Framework — loader for .wasm mod binaries.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.wasm;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads and validates {@code .wasm} mod binaries (compiled from Rust/C/Go) before sandboxed execution.
 *
 * <p>EN: Reads the bytes, checks the WebAssembly magic header, and wraps them in a {@link WasmModule}.
 * It never executes anything — instantiation happens explicitly inside a {@link WasmSandbox}. A file
 * without the {@code \0asm} magic is rejected with {@link IllegalArgumentException} rather than handed to
 * the engine.
 * RU: Читает байты, проверяет магический заголовок WebAssembly и оборачивает их в {@link WasmModule}.
 * Ничего не исполняет — создание экземпляра происходит явно внутри {@link WasmSandbox}. Файл без магии
 * {@code \0asm} отвергается {@link IllegalArgumentException}, а не передаётся движку.
 */
public final class WasmModuleLoader {

    /** Load and validate a {@code .wasm} file from disk. */
    public WasmModule load(Path wasmFile) {
        try {
            byte[] bytes = Files.readAllBytes(wasmFile);
            String name = wasmFile.getFileName().toString();
            return loadBytes(bytes, name);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read wasm module " + wasmFile, e);
        }
    }

    /** Validate already-read bytes as a named WebAssembly module. */
    public WasmModule loadBytes(byte[] bytes, String name) {
        WasmModule module = new WasmModule(name, bytes);
        if (!module.hasWasmMagic()) {
            throw new IllegalArgumentException("not a WebAssembly binary (missing \\0asm magic): " + name);
        }
        return module;
    }
}
