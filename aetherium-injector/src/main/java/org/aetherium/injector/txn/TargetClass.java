/*
 * Aetherium Framework — one class a mod's hook transaction targets.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.injector.txn;

import java.util.Objects;

/**
 * A single class a mod injects into, paired with its pristine (vanilla) bytes.
 *
 * <p>EN: The transaction applies the mod's rules to {@code vanillaBytes} inside the verification
 * sandbox; the ordered list of {@code TargetClass}es defines the deterministic hook-application order,
 * which is exactly the order rolled back (in reverse) if a later hook fails.
 * RU: Транзакция применяет правила мода к {@code vanillaBytes} внутри песочницы верификации;
 * упорядоченный список {@code TargetClass} задаёт детерминированный порядок применения хуков — именно
 * он откатывается (в обратном порядке), если поздний хук падает.
 *
 * @param binaryName   the JVM binary class name (e.g. {@code net.minecraft.world.entity.Entity})
 * @param vanillaBytes the original, untransformed class bytes
 */
public record TargetClass(String binaryName, byte[] vanillaBytes) {

    public TargetClass {
        Objects.requireNonNull(binaryName, "binaryName");
        Objects.requireNonNull(vanillaBytes, "vanillaBytes");
    }
}
