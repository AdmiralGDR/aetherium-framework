/*
 * Aetherium Framework — reflection guard (Confidentiality of framework internals).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.security;

import java.lang.reflect.AccessibleObject;
import java.util.List;
import java.util.Objects;

/**
 * Mediates deep reflection so a mod cannot reach into framework internals — the Confidentiality guard.
 *
 * <p>EN: Deep reflection ({@code setAccessible(true)}) is how a hostile or careless mod would read
 * another mod's private state, tamper with the dispatch tables, or defeat the injector's sandbox. The
 * guard enforces two rules before any {@code setAccessible} the framework performs on a mod's behalf:
 * (1) the mod must hold {@link Capability#REFLECTION}, and (2) the target must not live in a
 * <strong>protected</strong> framework/JDK-internal package — that second rule is absolute and holds
 * even with the capability, so a mod can introspect its own classes but never the loader, injector,
 * dispatch runtime, or {@code java.lang.invoke}. Violations are contained as
 * {@link SecurityViolationException}.
 *
 * <p>RU: Глубокая рефлексия ({@code setAccessible(true)}) — путь, которым враждебный или небрежный мод
 * прочитал бы приватное состояние другого мода, подменил таблицы диспетчеризации или обошёл песочницу
 * инжектора. Охрана требует перед любым {@code setAccessible} от имени мода: (1) наличие
 * {@link Capability#REFLECTION} и (2) цель не должна быть в <strong>защищённом</strong> внутреннем
 * пакете — второе правило абсолютно и действует даже при наличии возможности.
 */
public final class ReflectionGuard {

    /** Packages a mod may never reflect into, capability or not (Confidentiality + Integrity). */
    private static final List<String> PROTECTED_PREFIXES = List.of(
            "org.aetherium.loader",
            "org.aetherium.injector",
            "org.aetherium.bytecode.runtime",
            "org.aetherium.security",
            "java.lang.invoke",
            "jdk.internal.");

    private final SecurityPolicy policy;

    public ReflectionGuard(SecurityPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /**
     * Perform a guarded {@code setAccessible(true)} on behalf of {@code modId}. Throws
     * {@link SecurityViolationException} if the mod lacks {@link Capability#REFLECTION} or the target is
     * a protected framework/JDK-internal member.
     */
    public void makeAccessible(String modId, AccessibleObject member, Class<?> declaringClass) {
        policy.require(modId, Capability.REFLECTION);
        guardTarget(modId, declaringClass);
        member.setAccessible(true);
    }

    /** True if reflecting into {@code target} is forbidden regardless of capability. */
    public static boolean isProtected(Class<?> target) {
        String name = target.getName();
        for (String prefix : PROTECTED_PREFIXES) {
            if (prefix.endsWith(".")) {
                if (name.startsWith(prefix)) {            // e.g. "jdk.internal."
                    return true;
                }
            } else if (name.equals(prefix) || name.startsWith(prefix + ".")) {
                // package-boundary aware: "org.aetherium.security" must NOT match "org.aetherium.securitydemo"
                return true;
            }
        }
        return false;
    }

    /** Throw unless {@code modId} is allowed to reflect into {@code target}'s package. */
    public void guardTarget(String modId, Class<?> target) {
        if (isProtected(target)) {
            throw new SecurityViolationException("mod '" + modId
                    + "' attempted reflective access into protected internals: " + target.getName());
        }
    }
}
