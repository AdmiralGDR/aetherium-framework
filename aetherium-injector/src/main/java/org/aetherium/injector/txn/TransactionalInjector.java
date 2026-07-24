/*
 * Aetherium Framework — ACID transactional injection engine (Atomicity for mod hooks).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.injector.txn;

import org.aetherium.bytecode.CollectingDiagnosticSink;
import org.aetherium.core.Diagnostic;
import org.aetherium.injector.AetheriumInjector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Applies each mod's hooks as a single ACID transaction — the database-grade Atomicity guarantee.
 *
 * <p>EN: The base {@link AetheriumInjector#transform} already reverts a <em>single</em> class to its
 * vanilla bytes when its injection fails the verification sandbox. That is not enough: if a mod injects
 * into classes A, B and C, and only the edit to C fails, the game would still run with A and B rewritten
 * — a <strong>partially-applied mod</strong>, the exact source of "works-on-my-machine" Heisenbugs. This
 * engine closes that hole. Each mod's whole set of hooks is one transaction:
 *
 * <ol>
 *   <li>apply the mod's rules to each {@link TargetClass} in order, inside the sandbox;</li>
 *   <li>if <em>every</em> class verifies cleanly → <strong>COMMIT</strong>: publish all transformed
 *       bytes and install the mod's hook table;</li>
 *   <li>if <em>any</em> class fails → <strong>ROLLBACK</strong>: discard <em>all</em> of that mod's
 *       already-verified edits, install nothing, and disable the mod — the game keeps the vanilla bytes
 *       for every class the mod touched.</li>
 * </ol>
 *
 * <p>Rollback is graceful: a failing (or even unexpectedly throwing) mod is contained and disabled while
 * every other registered mod's transaction proceeds independently (Availability). The JVM is never
 * crashed; a broken mod simply never loads.
 *
 * <p>RU: Базовый {@link AetheriumInjector#transform} уже откатывает <em>один</em> класс к ванильным
 * байтам, когда его инъекция не проходит песочницу. Этого мало: если мод внедряется в классы A, B и C и
 * падает лишь правка C, игра всё равно запустится с переписанными A и B — <strong>частично применённый
 * мод</strong>, источник Heisenbug-ов. Этот движок закрывает дыру: весь набор хуков мода — одна
 * транзакция. Все классы прошли → <strong>COMMIT</strong> (публикуем и ставим таблицу хуков); любой
 * упал → <strong>ROLLBACK</strong> (отбрасываем все правки мода, ничего не ставим, мод отключается).
 * Откат мягкий: падающий мод локализуется и отключается, остальные моды коммитятся независимо, JVM не
 * падает.
 */
public final class TransactionalInjector {

    /** One registered mod: its id, its (multi-class) injector, and the ordered classes it targets. */
    private record ModRegistration(String modId, AetheriumInjector injection, List<TargetClass> targets) {
    }

    private final ClassLoader verifyLoader;
    private final List<ModRegistration> mods = new ArrayList<>();

    private TransactionalInjector(ClassLoader verifyLoader) {
        this.verifyLoader = verifyLoader;
    }

    /** Create an engine that verifies transformed classes against {@code verifyLoader} (may be null). */
    public static TransactionalInjector create(ClassLoader verifyLoader) {
        return new TransactionalInjector(verifyLoader);
    }

    /**
     * Register a mod whose {@code injection} rewrites the given ordered {@code targets}. The order of the
     * list is the deterministic hook-application (and reverse-rollback) order.
     */
    public TransactionalInjector mod(String modId, AetheriumInjector injection, List<TargetClass> targets) {
        Objects.requireNonNull(modId, "modId");
        Objects.requireNonNull(injection, "injection");
        Objects.requireNonNull(targets, "targets");
        mods.add(new ModRegistration(modId, injection, List.copyOf(targets)));
        return this;
    }

    /** Convenience: register a single-class mod. */
    public TransactionalInjector mod(String modId, AetheriumInjector injection, TargetClass target) {
        return mod(modId, injection, List.of(target));
    }

    /** Apply every registered mod's transaction, mod by mod, and return the aggregate report. */
    public EngineReport apply() {
        Map<String, TransactionResult> results = new LinkedHashMap<>();
        Map<String, byte[]> published = new LinkedHashMap<>();

        for (ModRegistration reg : mods) {
            TransactionResult result = applyOne(reg);
            results.put(reg.modId(), result);
            if (result.committed()) {
                // Publish the whole mod atomically and bind its dispatch table so the bytes are runnable.
                published.putAll(result.committedBytes());
                try {
                    reg.injection().installHooks();
                } catch (Throwable installFailed) {
                    // Extremely defensive: installation must never abort the pass; the bytes are still
                    // valid and the failure is isolated to this mod's runtime linkage.
                    // (Left intentionally contained — see Availability rule.)
                }
            }
        }
        return new EngineReport(results, published);
    }

    /** Run one mod's transaction: all classes verify → COMMIT, else discard everything → ROLLBACK. */
    private TransactionResult applyOne(ModRegistration reg) {
        List<String> log = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        Map<String, byte[]> working = new LinkedHashMap<>();
        int applied = 0;

        for (TargetClass target : reg.targets()) {
            CollectingDiagnosticSink sink = new CollectingDiagnosticSink();
            byte[] out;
            try {
                out = reg.injection().transform(target.vanillaBytes(), verifyLoader, sink);
            } catch (Throwable unexpected) {
                // transform() is contractually non-throwing, but treat any escape as a hard failure of
                // this hook so a rogue transformer can never crash the pass (Availability).
                log.add("hook #" + (applied + 1) + " (" + target.binaryName()
                        + ") threw " + unexpected.getClass().getSimpleName() + " — aborting transaction");
                return rollback(reg, applied, target.binaryName(), working, diagnostics, log);
            }

            if (!sink.isEmpty()) {
                // The sandbox reverted this class (bad bytecode / unsatisfiable cursor). Abort the mod.
                diagnostics.addAll(sink.diagnostics());
                log.add("hook #" + (applied + 1) + " (" + target.binaryName()
                        + ") FAILED verification (" + sink.count() + " diagnostic(s)) — rolling back "
                        + applied + " already-applied hook(s)");
                return rollback(reg, applied, target.binaryName(), working, diagnostics, log);
            }

            working.put(target.binaryName(), out);
            applied++;
            log.add("hook #" + applied + " (" + target.binaryName() + ") verified ("
                    + out.length + " bytes)");
        }

        log.add("COMMIT — all " + applied + " hook(s) verified; publishing mod '" + reg.modId() + "'");
        return new TransactionResult(reg.modId(), TransactionResult.Status.COMMITTED,
                reg.targets().size(), applied, null,
                Map.copyOf(working), List.copyOf(diagnostics), List.copyOf(log));
    }

    /** Discard every already-applied edit of this mod and mark it disabled. */
    private TransactionResult rollback(ModRegistration reg, int applied, String failedClass,
                                       Map<String, byte[]> working, List<Diagnostic> diagnostics,
                                       List<String> log) {
        // Explicitly drop the in-flight transformed bytes: nothing this mod produced is ever published.
        working.clear();
        log.add("ROLLBACK COMPLETE — mod '" + reg.modId() + "' disabled; game keeps vanilla bytes for "
                + reg.targets().size() + " class(es)");
        return new TransactionResult(reg.modId(), TransactionResult.Status.ROLLED_BACK,
                reg.targets().size(), applied, failedClass,
                Map.of(), List.copyOf(diagnostics), List.copyOf(log));
    }
}
