/*
 * Aetherium Framework — fuzz target: the .wasm module loader (magic validation).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.fuzzer.target;

import org.aetherium.fuzzer.FuzzTarget;
import org.aetherium.wasm.WasmModule;
import org.aetherium.wasm.WasmModuleLoader;

import java.util.random.RandomGenerator;

/**
 * Feeds hostile bytes to {@link WasmModuleLoader#loadBytes(byte[], String)} and the magic check.
 *
 * <p>EN: A {@code .wasm} mod arrives as untrusted bytes. The loader must validate the WebAssembly magic
 * {@code \0asm} and reject anything else with {@link IllegalArgumentException} <em>before</em> the bytes
 * reach the engine — never with an out-of-bounds read on a runt input. This target mixes empties, runts,
 * unaligned and pure-random blobs (rejected) with valid-magic-prefixed garbage (accepted at the loader
 * layer, since structural validation is the engine's job), and also drives {@link WasmModule#hasWasmMagic()}
 * directly.
 * RU: {@code .wasm}-мод приходит недоверенными байтами. Загрузчик обязан проверить магию WebAssembly
 * {@code \0asm} и отвергнуть прочее через {@link IllegalArgumentException} <em>до</em> попадания в движок —
 * никогда не чтением за границей на огрызке. Цель смешивает пустые/огрызки/случайные блобы (отвергаются)
 * с валидной-магией-и-мусором (принимается на уровне загрузчика) и дёргает {@link WasmModule#hasWasmMagic()}.
 */
public final class WasmLoaderFuzzTarget implements FuzzTarget {

    // WebAssembly magic \0asm as little-endian bytes 00 61 73 6D.
    private static final int[] WASM_MAGIC_LE = {0x6D736100};
    private static final int MAX_LEN = 256;

    private final WasmModuleLoader loader = new WasmModuleLoader();

    @Override
    public String name() {
        return "wasm.loadBytes(magic)";
    }

    @Override
    public void exercise(RandomGenerator rng) {
        byte[] blob = FuzzBytes.hostile(rng, WASM_MAGIC_LE, MAX_LEN);
        // hasWasmMagic must be total on any length, including 0..3 bytes.
        new WasmModule("fuzz", blob).hasWasmMagic();
        // Accepted (valid magic) or rejected (IllegalArgumentException); never anything else.
        WasmModule module = loader.loadBytes(blob, "fuzz.wasm");
        module.hasWasmMagic();
    }

    @Override
    public boolean expects(Throwable t) {
        // Bad magic is the loader's documented rejection.
        return t instanceof IllegalArgumentException;
    }
}
