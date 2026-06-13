/*
 * Aetherium Framework — Vulkan probe result.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.native_bridge;

/**
 * Result of the Vulkan hardware-access probe (instance creation + device/queue enumeration).
 *
 * <p>EN: Pure data, no shader logic. {@link #available()} means a Vulkan instance was created;
 * {@code deviceCount}/{@code queueFamilyCount} describe what hardware-access surface exists. A
 * non-available probe is normal on headless or driverless hosts and triggers graceful degradation.
 * RU: Чистые данные, без логики шейдеров. {@link #available()} означает, что Vulkan-instance создан;
 * {@code deviceCount}/{@code queueFamilyCount} описывают доступную поверхность доступа к
 * оборудованию. Недоступность нормальна на headless/бездрайверных хостах и запускает мягкую
 * деградацию.
 *
 * @param available         a Vulkan instance was successfully created
 * @param deviceCount       number of physical devices enumerated
 * @param queueFamilyCount  queue families on physical device 0 (0 if no device)
 * @param status            raw C status code (0 = ok)
 */
public record VulkanProbe(boolean available, int deviceCount, int queueFamilyCount, int status) {

    /** A probe representing "Vulkan not reachable". */
    public static VulkanProbe unavailable(int status) {
        return new VulkanProbe(false, 0, 0, status);
    }

    /** True if at least one physical device with one queue family is usable for compute. */
    public boolean hasUsableDevice() {
        return available && deviceCount > 0 && queueFamilyCount > 0;
    }
}
