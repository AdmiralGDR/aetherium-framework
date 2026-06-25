/*
 * Aetherium Framework — wasm module package docs.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */

/**
 * Polyglot WASM modding: run Rust/C/Go mods in a strict, memory-and-compute-only sandbox.
 *
 * <p>EN: {@link org.aetherium.wasm.WasmModuleLoader} validates {@code .wasm} binaries;
 * {@link org.aetherium.wasm.WasmSandbox} runs them in a GraalWASM {@code Context} (reached reflectively,
 * optional) built with filesystem and network access denied per {@link org.aetherium.wasm.WasmSecurityPolicy};
 * {@link org.aetherium.wasm.StructArenaWasmBridge} bridges WASM linear memory to the FFM
 * {@link org.aetherium.core.compute.StructArena} for secure off-heap entity physics.
 * RU: {@link org.aetherium.wasm.WasmModuleLoader} проверяет бинарь {@code .wasm};
 * {@link org.aetherium.wasm.WasmSandbox} исполняет его в {@code Context} GraalWASM (рефлексивно,
 * опционально) с запретом доступа к файловой системе и сети согласно
 * {@link org.aetherium.wasm.WasmSecurityPolicy}; {@link org.aetherium.wasm.StructArenaWasmBridge}
 * связывает линейную память WASM с FFM {@link org.aetherium.core.compute.StructArena}.
 */
package org.aetherium.wasm;
