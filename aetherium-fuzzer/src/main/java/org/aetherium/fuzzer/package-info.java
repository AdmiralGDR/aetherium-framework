/*
 * Aetherium Framework — fuzzing engine package.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */

/**
 * EN: An aggressive, deterministic coverage fuzzer for Aetherium's unsafe attack surface — the
 * Java→SPIR-V compiler and the polyglot WASM sandbox/bridge. Each {@link org.aetherium.fuzzer.FuzzTarget}
 * exercises one entry point with adversarial input and declares which exception type is a clean,
 * contractual rejection; the {@link org.aetherium.fuzzer.FuzzEngine} classifies everything else as a
 * crash. Because every case is caught, a passing campaign is a proof that no input crashed the JVM or
 * the host OS. Cases are seeded reproducibly so any finding can be replayed from its reported seed.
 *
 * <p>RU: Агрессивный детерминированный фаззер для небезопасной поверхности Aetherium — компилятора
 * Java→SPIR-V и polyglot-песочницы/моста WASM. Каждый {@link org.aetherium.fuzzer.FuzzTarget}
 * прогоняет одну точку входа враждебным входом и объявляет, какой тип исключения — чистый контрактный
 * отказ; {@link org.aetherium.fuzzer.FuzzEngine} классифицирует всё прочее как краш. Так как каждый
 * случай перехвачен, успешная кампания — доказательство, что ни один вход не уронил JVM или хост-ОС.
 * Случаи семенуются воспроизводимо, поэтому любую находку можно повторить по её сиду.
 */
package org.aetherium.fuzzer;
