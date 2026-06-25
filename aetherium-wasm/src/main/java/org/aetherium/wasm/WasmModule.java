/*
 * Aetherium Framework — a loaded (validated) WebAssembly module.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.wasm;

/**
 * A {@code .wasm} binary that has passed header validation, ready to instantiate in a {@link WasmSandbox}.
 *
 * <p>EN: Holds the module name and its raw bytes. {@link #instantiate(WasmSandbox)} evaluates it inside
 * the strict sandbox (memory & compute only). The bytes are validated to start with the WebAssembly
 * magic {@code \0asm} at load time, so a non-wasm file is rejected before it ever reaches the engine.
 * RU: Хранит имя модуля и его сырые байты. {@link #instantiate(WasmSandbox)} выполняет его внутри
 * строгой песочницы (только память и вычисления). Байты проверяются на магию WebAssembly {@code \0asm}
 * при загрузке, поэтому не-wasm файл отвергается до попадания в движок.
 */
public record WasmModule(String name, byte[] bytes) {

    /** WebAssembly magic bytes: {@code 0x00 'a' 's' 'm'}. */
    public static final int MAGIC = 0x6D736100; // little-endian \0asm

    /**
     * EN: Instantiate this module inside the sandbox; throws if GraalWASM is unavailable.
     * RU: Создать экземпляр модуля внутри песочницы; бросает, если GraalWASM недоступен.
     */
    public Object instantiate(WasmSandbox sandbox) {
        return sandbox.evalModule(bytes, name);
    }

    /** True if the bytes carry the WebAssembly magic header. */
    public boolean hasWasmMagic() {
        return bytes.length >= 4
                && (bytes[0] & 0xFF) == 0x00
                && (bytes[1] & 0xFF) == 0x61
                && (bytes[2] & 0xFF) == 0x73
                && (bytes[3] & 0xFF) == 0x6D;
    }
}
