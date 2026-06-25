/*
 * Aetherium Framework — live class hot-swap engine (Instrumentation.redefineClasses).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.hotswap;

import org.aetherium.injector.probe.InstrumentationSupport;
import org.objectweb.asm.ClassReader;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Replaces a class's bytecode in the <em>running</em> JVM — the heart of zero-downtime mod iteration.
 *
 * <p>EN: Given fresh {@code .class} bytes, the engine derives the binary class name straight from the
 * bytes (ASM {@link ClassReader#getClassName()}), finds the matching already-loaded {@link Class}, and
 * calls {@link Instrumentation#redefineClasses} to swap the method bodies in place — the game keeps
 * running with the new code. {@link Instrumentation} is acquired through the shared
 * {@link InstrumentationSupport} (the same Attach-API path the ephemeral JFR probes use), so on a
 * locked-down JVM the engine degrades to {@link HotSwapResult.Status#NO_INSTRUMENTATION} instead of
 * failing. After each successful swap it notifies every {@link HotSwapListener}, which is how the
 * injector re-resolves its {@link org.aetherium.injector.LiveHookGraph} so hooks stay correctly ordered
 * live. Standard JVM redefinition rules apply (method-body changes only; no added/removed
 * members) — a rejected redefinition is reported, never fatal.
 *
 * <p>RU: По свежим байтам {@code .class} движок выводит бинарное имя класса прямо из байт (ASM
 * {@link ClassReader#getClassName()}), находит соответствующий уже загруженный {@link Class} и
 * вызывает {@link Instrumentation#redefineClasses}, заменяя тела методов на месте — игра продолжает
 * работать с новым кодом. {@link Instrumentation} берётся через общий {@link InstrumentationSupport}
 * (тот же путь Attach API, что и у эфемерных JFR-зондов), поэтому на заблокированной JVM движок
 * деградирует до {@link HotSwapResult.Status#NO_INSTRUMENTATION}, а не падает. После каждого успешного
 * свопа уведомляются все {@link HotSwapListener} — так инжектор заново разрешает свой
 * {@link org.aetherium.injector.LiveHookGraph}. Действуют стандартные правила переопределения JVM
 * (только тела методов; без добавления/удаления членов) — отвергнутое переопределение сообщается, но
 * никогда не фатально.
 */
public final class HotSwapEngine {

    private final CopyOnWriteArrayList<HotSwapListener> listeners = new CopyOnWriteArrayList<>();
    private final Set<String> swapped = ConcurrentHashMap.newKeySet();

    /** Register a reconciliation listener (e.g. the injector's live-DAG re-resolve). */
    public HotSwapEngine onReload(HotSwapListener listener) {
        listeners.add(listener);
        return this;
    }

    /** True if instant, already-loaded redefinition is possible on this JVM. */
    public boolean available() {
        return InstrumentationSupport.available();
    }

    /**
     * EN: True if <strong>structural</strong> hot-swap (adding/removing fields and methods of a live
     * class) is available — i.e. an enhanced runtime (DCEVM / HotswapAgent) is present. When true, the
     * standard {@link #redefine} path additionally accepts schema-changing bytecode; when false those
     * edits are rejected gracefully and need a restart. See {@link DcevmSupport}.
     * RU: True, если доступен <strong>структурный</strong> hot-swap (добавление/удаление полей и методов
     * живого класса) — присутствует усиленный рантайм (DCEVM / HotswapAgent). При true путь
     * {@link #redefine} дополнительно принимает изменяющий схему байткод. См. {@link DcevmSupport}.
     */
    public boolean structuralRedefineSupported() {
        return DcevmSupport.structuralRedefineAvailable();
    }

    /**
     * EN: Redefine whatever class {@code newClassBytes} describes (name read from the bytes).
     * RU: Переопределить класс, описанный {@code newClassBytes} (имя читается из байт).
     */
    public HotSwapResult redefine(byte[] newClassBytes) {
        String binaryName = new ClassReader(newClassBytes).getClassName().replace('/', '.');
        Instrumentation inst = InstrumentationSupport.acquire();
        if (inst == null) {
            return HotSwapResult.noInstrumentation(binaryName);
        }
        Class<?> target = findLoaded(inst, binaryName);
        if (target == null) {
            return HotSwapResult.notLoaded(binaryName);
        }
        return redefine(target, newClassBytes);
    }

    /**
     * EN: Redefine a specific already-loaded {@link Class} with new bytes.
     * RU: Переопределить конкретный уже загруженный {@link Class} новыми байтами.
     */
    public HotSwapResult redefine(Class<?> target, byte[] newClassBytes) {
        String binaryName = target.getName();
        Instrumentation inst = InstrumentationSupport.acquire();
        if (inst == null) {
            return HotSwapResult.noInstrumentation(binaryName);
        }
        if (!inst.isModifiableClass(target)) {
            return HotSwapResult.rejected(binaryName, "class is not modifiable");
        }
        try {
            inst.redefineClasses(new ClassDefinition(target, newClassBytes));
        } catch (Throwable rejected) {
            // Contained: a bad edit must never crash the host (verify error, schema change, …).
            return HotSwapResult.rejected(binaryName, rejected.getClass().getSimpleName()
                    + ": " + rejected.getMessage());
        }
        swapped.add(binaryName);
        notifyListeners(binaryName);
        return HotSwapResult.redefined(binaryName);
    }

    /** Binary names of classes this engine has swapped at least once (for status reporting). */
    public List<String> swappedClasses() {
        return List.copyOf(swapped);
    }

    private void notifyListeners(String binaryName) {
        for (HotSwapListener listener : listeners) {
            try {
                listener.onClassRedefined(binaryName);
            } catch (Throwable isolated) {
                // One bad reconciler must not abort the swap or the others.
            }
        }
    }

    private static Class<?> findLoaded(Instrumentation inst, String binaryName) {
        for (Class<?> c : inst.getAllLoadedClasses()) {
            if (c.getName().equals(binaryName)) {
                return c;
            }
        }
        return null;
    }
}
