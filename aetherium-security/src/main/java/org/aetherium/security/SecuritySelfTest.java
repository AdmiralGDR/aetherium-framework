/*
 * Aetherium Framework — CIA-triad security self-test.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.security;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

/**
 * Proves the capability model enforces default-deny, FFM bounds (Integrity), and internal-package
 * confidentiality.
 *
 * <p>EN: Exercises the four invariants a hostile mod would probe: (1) an ungranted mod is denied
 * (default-deny); (2) a granted mod passes the same check; (3) a {@link GuardedSegment} blocks an
 * out-of-bounds FFM write while allowing in-bounds access; (4) {@link ReflectionGuard} refuses
 * reflective access into a protected framework package even when the mod holds
 * {@link Capability#REFLECTION}, yet permits reflecting into the mod's own class.
 *
 * <p>RU: Проверяет четыре инварианта, которые прощупывал бы враждебный мод: (1) невыданный мод получает
 * отказ (default-deny); (2) выданный мод проходит ту же проверку; (3) {@link GuardedSegment} блокирует
 * запись FFM за границами, допуская доступ в границах; (4) {@link ReflectionGuard} отказывает в
 * рефлексии в защищённый пакет даже при наличии {@link Capability#REFLECTION}, но разрешает рефлексию в
 * собственный класс мода.
 */
public final class SecuritySelfTest {

    private SecuritySelfTest() {
    }

    public record Result(boolean defaultDenyOk,
                         boolean grantedAllowed,
                         boolean ffmBoundsEnforced,
                         boolean ffmInBoundsOk,
                         boolean internalReflectionDenied,
                         boolean ownReflectionAllowed,
                         List<String> notes) {
        public boolean passed() {
            return defaultDenyOk && grantedAllowed && ffmBoundsEnforced && ffmInBoundsOk
                    && internalReflectionDenied && ownReflectionAllowed;
        }
    }

    public static Result run() throws Exception {
        List<String> notes = new ArrayList<>();
        SecurityPolicy policy = SecurityPolicy.global();
        policy.reset();

        // (1) default-deny: an unregistered mod has no NATIVE_MEMORY capability.
        boolean defaultDenyOk = !policy.allows("untrusted_mod", Capability.NATIVE_MEMORY);
        notes.add("default-deny: untrusted_mod NATIVE_MEMORY allowed=" + !defaultDenyOk + " (want false)");

        // (2) granted mod passes.
        policy.grant(CapabilityGrant.of("trusted_mod", Capability.NATIVE_MEMORY, Capability.REFLECTION));
        boolean grantedAllowed = policy.allows("trusted_mod", Capability.NATIVE_MEMORY);
        notes.add("granted: trusted_mod NATIVE_MEMORY allowed=" + grantedAllowed + " (want true)");

        // (3) FFM bounds: an out-of-bounds write is converted to a contained violation; in-bounds works.
        boolean ffmBoundsEnforced;
        boolean ffmInBoundsOk;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(64);
            GuardedSegment guarded = GuardedSegment.grant(policy, "trusted_mod", seg);
            guarded.setInt(0, 0xCAFEBABE);
            ffmInBoundsOk = guarded.getInt(0) == 0xCAFEBABE;
            boolean blocked;
            try {
                guarded.setInt(62, 1); // 62+4 > 64 -> must be refused
                blocked = false;
            } catch (SecurityViolationException expected) {
                blocked = true;
            }
            ffmBoundsEnforced = blocked;
        }
        notes.add("FFM in-bounds ok=" + ffmInBoundsOk + ", out-of-bounds blocked=" + ffmBoundsEnforced);

        // (4) reflection guard: protected internals are off-limits even WITH the capability.
        ReflectionGuard guard = new ReflectionGuard(policy);
        boolean internalReflectionDenied;
        try {
            guard.guardTarget("trusted_mod", Class.forName("org.aetherium.security.SecurityPolicy"));
            internalReflectionDenied = false;
        } catch (SecurityViolationException expected) {
            internalReflectionDenied = true;
        }
        // ...but the mod can reflect into its own (non-protected) class.
        boolean ownReflectionAllowed;
        try {
            Class<?> own = org.aetherium.sample.SampleModClass.class;
            guard.makeAccessible("trusted_mod", own.getDeclaredField("value"), own);
            ownReflectionAllowed = true;
        } catch (SecurityViolationException denied) {
            ownReflectionAllowed = false;
        }
        notes.add("reflection: protected-internal denied=" + internalReflectionDenied
                + ", own-class allowed=" + ownReflectionAllowed);

        policy.reset();
        return new Result(defaultDenyOk, grantedAllowed, ffmBoundsEnforced, ffmInBoundsOk,
                internalReflectionDenied, ownReflectionAllowed, List.copyOf(notes));
    }
}
