/*
 * Aetherium Framework — an unforgeable handle (UUID capability) to an owned FFM memory domain.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.security;

import java.util.Objects;
import java.util.UUID;

/**
 * The token a mod holds for one off-heap memory domain it owns — the Isolation capability.
 *
 * <p>EN: When a mod allocates a {@linkplain MemoryDomainRegistry memory domain}, it is handed this
 * handle carrying a random {@link UUID}. The UUID is the capability: another mod cannot open the domain
 * without either <em>being</em> the owner or having been {@linkplain MemoryDomainRegistry#grantAccess
 * explicitly granted} access to that exact id. Because the id is a 122-bit random value it cannot be
 * guessed or forged, so FFM segments owned by different mods are isolated by construction — the
 * database-grade Isolation guarantee applied to memory.
 *
 * <p>RU: Когда мод выделяет {@linkplain MemoryDomainRegistry домен памяти}, ему выдаётся этот хэндл со
 * случайным {@link UUID}. UUID и есть возможность (capability): другой мод не может открыть домен, не
 * будучи владельцем или без {@linkplain MemoryDomainRegistry#grantAccess явно выданного} доступа к
 * этому идентификатору. Так как id — 122-битное случайное значение, его нельзя угадать или подделать,
 * поэтому FFM-сегменты разных модов изолированы по построению.
 *
 * @param domainId the unforgeable domain capability id
 * @param owner    the mod that allocated (and owns) the domain
 * @param byteSize the domain's size in bytes
 */
public record MemoryDomainHandle(UUID domainId, String owner, long byteSize) {

    public MemoryDomainHandle {
        Objects.requireNonNull(domainId, "domainId");
        Objects.requireNonNull(owner, "owner");
        if (byteSize < 0) {
            throw new IllegalArgumentException("byteSize must be >= 0: " + byteSize);
        }
    }
}
