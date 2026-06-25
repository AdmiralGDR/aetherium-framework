/*
 * Aetherium Framework — immutable WASM sandbox security policy.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.wasm;

/**
 * The capability policy enforced on every WASM mod — <strong>deny filesystem and network, by design</strong>.
 *
 * <p>EN: Polyglot WASM mods (compiled from Rust/C/Go) are untrusted native code. This policy is the
 * security contract: {@link #filesystem()} and {@link #network()} are always {@code false} and there is
 * no API to turn them on — the {@link WasmSandbox} builds its GraalVM {@code Context} with IO denied and
 * no host access. Only {@link #memory()} and {@link #compute()} are granted, so a mod can crunch entity
 * physics in its linear memory but can never touch the disk, open a socket, or reach into the JVM. The
 * {@link #strict()} factory is the only supported configuration; {@link #assertStrict()} guards against
 * a tampered instance.
 * RU: Polyglot WASM-моды (из Rust/C/Go) — недоверенный нативный код. Эта политика — контракт
 * безопасности: {@link #filesystem()} и {@link #network()} всегда {@code false}, и нет API, чтобы их
 * включить — {@link WasmSandbox} строит {@code Context} GraalVM с запретом IO и без доступа к хосту.
 * Разрешены только {@link #memory()} и {@link #compute()}, поэтому мод может считать физику сущностей в
 * своей линейной памяти, но не может тронуть диск, открыть сокет или влезть в JVM. Фабрика
 * {@link #strict()} — единственная поддерживаемая конфигурация.
 */
public record WasmSecurityPolicy(boolean filesystem, boolean network, boolean memory, boolean compute) {

    /** The one and only supported policy: filesystem/network denied, memory/compute allowed. */
    public static WasmSecurityPolicy strict() {
        return new WasmSecurityPolicy(false, false, true, true);
    }

    /**
     * EN: Guard that this policy denies host I/O. Thrown if it was constructed to allow FS/network.
     * RU: Защита: эта политика запрещает host-IO. Бросается, если разрешён доступ к ФС/сети.
     */
    public void assertStrict() {
        if (filesystem || network) {
            throw new SecurityException("WASM sandbox policy must deny filesystem and network access");
        }
        if (!memory || !compute) {
            throw new IllegalStateException("WASM sandbox policy must permit memory and compute");
        }
    }

    /** A short human-readable summary for logs / the CLI doctor. */
    public String describe() {
        return "filesystem=DENY network=DENY memory=ALLOW compute=ALLOW";
    }
}
