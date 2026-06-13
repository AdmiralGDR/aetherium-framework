/*
 * Aetherium Framework — SIMD bulk math.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */

/**
 * SIMD bulk-math bridge.
 *
 * <p><b>EN.</b> {@link org.aetherium.core.simd.SimdMath} offers bulk vector operations with a correct
 * scalar implementation today and a runtime hook ({@code isVectorApiAvailable()}) for the incubating
 * Java Vector API, so an accelerated path can be added later without changing the caller API.
 *
 * <p><b>RU.</b> {@link org.aetherium.core.simd.SimdMath} предоставляет массовые векторные операции с
 * корректной скалярной реализацией сейчас и рантайм-хуком ({@code isVectorApiAvailable()}) для
 * инкубаторного Java Vector API, чтобы позже добавить ускоренный путь без изменения API вызывающих.
 */
package org.aetherium.core.simd;
