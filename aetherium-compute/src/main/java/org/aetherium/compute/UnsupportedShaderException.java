/*
 * Aetherium Framework — raised when a kernel leaves the supported Java subset.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.compute;

/**
 * Thrown when an {@code @AetheriumComputeShader} method uses constructs outside the compilable subset.
 *
 * <p>EN: The Java→SPIR-V path supports only primitives, primitive arrays, loops and basic math
 * ({@code + - *}). Object allocation, reference types, and heap method calls are rejected here rather
 * than producing an invalid binary.
 * RU: Путь Java→SPIR-V поддерживает только примитивы, примитивные массивы, циклы и базовую математику
 * ({@code + - *}). Аллокация объектов, ссылочные типы и вызовы в кучу отвергаются здесь, а не приводят
 * к выпуску невалидного бинаря.
 */
public final class UnsupportedShaderException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnsupportedShaderException(String message) {
        super(message);
    }
}
