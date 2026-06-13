/*
 * Aetherium Framework — native bridge (Java side).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */

/**
 * Aetherium Framework — JNI / FFM native bridge (Java side).
 *
 * <p><b>EN.</b> Brokered access to {@code libaetherium_native.so} via the Java 21 FFM API
 * ({@code java.lang.foreign}). {@link org.aetherium.native_bridge.NativeLibrary} builds the
 * {@code MethodHandle} downcalls once for {@code O(1)} invocation;
 * {@link org.aetherium.native_bridge.NativeBridge} is the high-level, allow-listed surface with
 * Arena-owned memory; {@link org.aetherium.native_bridge.NativeProbe} is the non-throwing Pre-Flight
 * building block; {@link org.aetherium.native_bridge.VulkanProbe} carries the hardware-access scaffold
 * result. Compute pipelines live in {@code compute}. Depends only on {@code aetherium-core}.
 *
 * <p><b>RU.</b> Посреднический доступ к {@code libaetherium_native.so} через FFM API Java 21.
 * {@link org.aetherium.native_bridge.NativeLibrary} строит downcall-хэндлы один раз для
 * {@code O(1)}-вызова; {@link org.aetherium.native_bridge.NativeBridge} — высокоуровневая поверхность
 * из белого списка с памятью, принадлежащей Arena; {@link org.aetherium.native_bridge.NativeProbe} —
 * не бросающий блок Pre-Flight; {@link org.aetherium.native_bridge.VulkanProbe} несёт результат
 * каркаса доступа к оборудованию. Конвейеры вычислений — в {@code compute}. Зависит только от
 * {@code aetherium-core}.
 */
package org.aetherium.native_bridge;
