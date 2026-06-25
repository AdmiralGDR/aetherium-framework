/*
 * Aetherium Framework — outcome of a single hot-swap redefinition.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.hotswap;

/**
 * The result of one {@link HotSwapEngine#redefine} call.
 *
 * <p>EN: A hot-swap can fully apply ({@link Status#REDEFINED}), be impossible because no live
 * {@link java.lang.instrument.Instrumentation} could be acquired ({@link Status#NO_INSTRUMENTATION},
 * the locked-down-JVM degrade path), target a class that is not loaded yet ({@link Status#NOT_LOADED}),
 * or be refused by the JVM verifier / redefinition rules ({@link Status#REJECTED}). Carrying the class
 * name and a detail string makes the watcher's log self-explanatory.
 * RU: Hot-swap может полностью примениться ({@link Status#REDEFINED}), быть невозможным из-за того, что
 * не удалось получить живой {@link java.lang.instrument.Instrumentation}
 * ({@link Status#NO_INSTRUMENTATION}, путь деградации на заблокированной JVM), нацеливаться на ещё не
 * загруженный класс ({@link Status#NOT_LOADED}) или быть отвергнутым верификатором/правилами
 * переопределения JVM ({@link Status#REJECTED}).
 */
public record HotSwapResult(String className, Status status, String detail) {

    public enum Status {
        /** The class image was atomically replaced in the running JVM. */
        REDEFINED,
        /** No retransform/redefine-capable agent is available (degrade: edit applies on next launch). */
        NO_INSTRUMENTATION,
        /** The target class is not loaded yet — nothing to redefine right now. */
        NOT_LOADED,
        /** The JVM refused the new bytes (schema change, verify error, …). */
        REJECTED
    }

    public boolean redefined() {
        return status == Status.REDEFINED;
    }

    static HotSwapResult redefined(String className) {
        return new HotSwapResult(className, Status.REDEFINED, "class image replaced live");
    }

    static HotSwapResult noInstrumentation(String className) {
        return new HotSwapResult(className, Status.NO_INSTRUMENTATION,
                "no live Instrumentation (start with -Djdk.attach.allowAttachSelf=true); applies on next launch");
    }

    static HotSwapResult notLoaded(String className) {
        return new HotSwapResult(className, Status.NOT_LOADED, "class not loaded in this JVM yet");
    }

    static HotSwapResult rejected(String className, String detail) {
        return new HotSwapResult(className, Status.REJECTED, detail);
    }
}
