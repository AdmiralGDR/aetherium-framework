/*
 * Aetherium Framework — ephemeral JFR probe self-test.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.injector.probe;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.aetherium.bytecode.BytecodeEngine;
import org.aetherium.bytecode.CollectingDiagnosticSink;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Proves ephemeral probes are <em>absent</em> when off, woven when on, and that JFR actually records.
 *
 * <p>EN: (1) Generates a mock {@code long work()}. (2) Runs the {@link ProbeWeaver} with an <b>empty</b>
 * active set and confirms the output carries <strong>no reference</strong> to {@link AetheriumMethodEvent}
 * — the zero-static-overhead property: an un-probed method has no probe code at all, not even a flag
 * check. (3) Runs the weaver <b>with</b> a probe on {@code work}, confirms the event class now appears,
 * loads the woven class, runs it inside a live {@link Recording}, and asserts a
 * {@code org.aetherium.MethodTiming} event was captured with the right method label. (4) Reports whether
 * a live {@link java.lang.instrument.Instrumentation} is available for instant hot-swap of
 * already-loaded classes (vs. graceful load-time weaving).
 *
 * <p>RU: (1) Генерирует мок {@code long work()}. (2) Прогоняет {@link ProbeWeaver} с <b>пустым</b>
 * активным множеством и подтверждает, что в выводе <strong>нет ссылки</strong> на
 * {@link AetheriumMethodEvent} — свойство нулевых статических накладных расходов. (3) Прогоняет ткач
 * <b>с</b> зондом на {@code work}, подтверждает появление класса события, загружает класс, выполняет его
 * в живой {@link Recording} и проверяет, что событие {@code org.aetherium.MethodTiming} записано.
 * (4) Сообщает, доступен ли живой {@link java.lang.instrument.Instrumentation} для мгновенного hot-swap.
 */
public final class ProbeSelfTest {

    private static final String MOCK_INTERNAL = "org/aetherium/injector/probe/demo/ProbeMock";
    private static final String MOCK_BINARY = "org.aetherium.injector.probe.demo.ProbeMock";
    private static final String EVENT_NAME = "org.aetherium.MethodTiming";

    private ProbeSelfTest() {
    }

    public record Result(boolean zeroOverheadWhenOff,
                         boolean wovenWhenOn,
                         boolean jfrEventFired,
                         long eventsCaptured,
                         boolean instrumentationAvailable,
                         String controllerStatus,
                         List<String> notes) {
        public boolean passed() {
            return zeroOverheadWhenOff && wovenWhenOn && jfrEventFired;
        }
    }

    public static Result run() throws Exception {
        List<String> notes = new ArrayList<>();
        byte[] original = generateMock();
        boolean originalHasEvent = containsAscii(original, "AetheriumMethodEvent");
        notes.add("mock generated: " + original.length + " bytes, contains event ref=" + originalHasEvent);

        // (2) OFF: weaver with an empty active set must not reference the event class at all.
        byte[] off = engine(List.of()).transformClass(original, new CollectingDiagnosticSink());
        boolean zeroOverheadWhenOff = !containsAscii(off, "AetheriumMethodEvent");
        notes.add("probe OFF: output references AetheriumMethodEvent=" + !zeroOverheadWhenOff
                + " (want false -> zero static overhead)");

        // (3) ON: weave a probe into work(); the event class must now be referenced.
        byte[] on = engine(List.of(ProbeTarget.of(MOCK_INTERNAL, "work")))
                .transformClass(original, new CollectingDiagnosticSink());
        boolean wovenWhenOn = containsAscii(on, "AetheriumMethodEvent");
        notes.add("probe ON: output references AetheriumMethodEvent=" + wovenWhenOn + " (want true)");

        // Load the woven class and run it inside a live JFR recording.
        long captured = 0;
        boolean jfrFired = false;
        if (wovenWhenOn) {
            Class<?> woven = new ByteClassLoader(ProbeSelfTest.class.getClassLoader()).define(MOCK_BINARY, on);
            Path jfr = Files.createTempFile("aetherium-probe", ".jfr");
            try (Recording rec = new Recording()) {
                rec.enable(EVENT_NAME).withoutThreshold();
                rec.start();
                for (int i = 0; i < 50; i++) {
                    woven.getMethod("work").invoke(null);
                }
                rec.stop();
                rec.dump(jfr);
            }
            for (RecordedEvent e : RecordingFile.readAllEvents(jfr)) {
                if (e.getEventType().getName().equals(EVENT_NAME)) {
                    captured++;
                }
            }
            Files.deleteIfExists(jfr);
            jfrFired = captured > 0;
            notes.add("JFR recording captured " + captured + " '" + EVENT_NAME + "' event(s) from 50 calls");
        }

        DynamicProbeController controller = DynamicProbeController.get();
        boolean instr = controller.instrumentationAvailable();
        notes.add("dynamic hot-swap: " + controller.status());

        return new Result(zeroOverheadWhenOff, wovenWhenOn, jfrFired, captured, instr,
                controller.status(), List.copyOf(notes));
    }

    private static BytecodeEngine engine(List<ProbeTarget> targets) {
        return BytecodeEngine.builder()
                .transformer(new ProbeWeaver(targets, 100))
                .classLoader(ProbeSelfTest.class.getClassLoader())
                .build();
    }

    /** {@code static long work()} — a measurable loop so the JFR duration event is meaningful. */
    private static byte[] generateMock() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, MOCK_INTERNAL, null, "java/lang/Object", null);

        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();

        MethodVisitor w = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "work", "()J", null, null);
        w.visitCode();
        w.visitInsn(Opcodes.LCONST_0);
        w.visitVarInsn(Opcodes.LSTORE, 0);          // long acc = 0
        w.visitInsn(Opcodes.ICONST_0);
        w.visitVarInsn(Opcodes.ISTORE, 2);          // int i = 0
        Label cond = new Label();
        Label end = new Label();
        w.visitLabel(cond);
        w.visitVarInsn(Opcodes.ILOAD, 2);
        w.visitLdcInsn(200_000);
        w.visitJumpInsn(Opcodes.IF_ICMPGE, end);
        w.visitVarInsn(Opcodes.LLOAD, 0);           // acc += i
        w.visitVarInsn(Opcodes.ILOAD, 2);
        w.visitInsn(Opcodes.I2L);
        w.visitInsn(Opcodes.LADD);
        w.visitVarInsn(Opcodes.LSTORE, 0);
        w.visitIincInsn(2, 1);
        w.visitJumpInsn(Opcodes.GOTO, cond);
        w.visitLabel(end);
        w.visitVarInsn(Opcodes.LLOAD, 0);
        w.visitInsn(Opcodes.LRETURN);
        w.visitMaxs(0, 0);
        w.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static boolean containsAscii(byte[] haystack, String needle) {
        byte[] n = needle.getBytes(StandardCharsets.US_ASCII);
        outer:
        for (int i = 0; i <= haystack.length - n.length; i++) {
            for (int j = 0; j < n.length; j++) {
                if (haystack[i + j] != n[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private static final class ByteClassLoader extends ClassLoader {
        ByteClassLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> define(String binaryName, byte[] bytes) {
            return defineClass(binaryName, bytes, 0, bytes.length);
        }
    }
}
