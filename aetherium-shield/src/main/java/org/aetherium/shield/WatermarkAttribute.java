/*
 * Aetherium Framework — shield author watermark (a custom class attribute).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.shield;

import org.objectweb.asm.Attribute;
import org.objectweb.asm.ByteVector;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;

import java.nio.charset.StandardCharsets;

/**
 * A non-standard class attribute carrying an author signature — so a leaked or ripped mod is traceable back
 * to its author. Protecting the <em>author</em>, not only the code.
 *
 * <p>EN: The JVM ignores unknown attributes, so this rides along invisibly and does not affect execution.
 * Unlike a field or string it is not referenced by any code, so a naive strip pass won't remove it, and it
 * survives ordinary recompilation of surrounding code. {@link #payload} is {@code "author|epochMillis"}.
 * RU: JVM игнорирует неизвестные атрибуты, поэтому знак едет незаметно и не влияет на исполнение. В отличие
 * от поля или строки он не упоминается кодом, поэтому наивное удаление его не заденет.
 */
public final class WatermarkAttribute extends Attribute {

    static final String TYPE = "AetheriumShield";

    private String payload;

    /** Prototype constructor (used when reading). */
    public WatermarkAttribute() {
        super(TYPE);
        this.payload = "";
    }

    public WatermarkAttribute(String author) {
        super(TYPE);
        this.payload = (author == null ? "" : author) + "|" + reproducibleTimestamp();
    }

    /**
     * A build timestamp that keeps protected jars byte-reproducible (MANIFEST axiom V). A wall-clock
     * {@code System.currentTimeMillis()} made the watermark — and therefore every shielded class — differ on
     * every run, breaking reproducible builds. We honour the reproducible-builds standard {@code
     * SOURCE_DATE_EPOCH} (seconds since the epoch) when set, and otherwise stamp a fixed {@code 0}: the
     * author (the part that actually traces a leaked jar) is unchanged, and two builds of the same sources
     * now produce identical bytes.
     */
    private static long reproducibleTimestamp() {
        String epoch = System.getenv("SOURCE_DATE_EPOCH");
        if (epoch != null && !epoch.isBlank()) {
            try {
                return Long.parseLong(epoch.trim()) * 1000L;
            } catch (NumberFormatException ignored) {
                // fall through to the deterministic default
            }
        }
        return 0L;
    }

    public String payload() {
        return payload;
    }

    /** The author portion of the watermark (before the {@code |}). */
    public String author() {
        int bar = payload.indexOf('|');
        return bar < 0 ? payload : payload.substring(0, bar);
    }

    @Override
    protected Attribute read(ClassReader cr, int off, int length, char[] charBuffer,
                             int codeAttributeOffset, Label[] labels) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = (byte) cr.readByte(off + i);
        }
        WatermarkAttribute a = new WatermarkAttribute();
        a.payload = new String(bytes, StandardCharsets.UTF_8);
        return a;
    }

    @Override
    protected ByteVector write(ClassWriter cw, byte[] code, int codeLength, int maxStack, int maxLocals) {
        ByteVector v = new ByteVector();
        v.putByteArray(payload.getBytes(StandardCharsets.UTF_8), 0, payload.getBytes(StandardCharsets.UTF_8).length);
        return v;
    }

    /** Extract the watermark from a class, or {@code null} if it carries none. */
    public static WatermarkAttribute extract(byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        Capture capture = new Capture();
        reader.accept(capture, new Attribute[]{new WatermarkAttribute()}, 0);
        return capture.found;
    }

    /** Minimal visitor that captures a {@link WatermarkAttribute} if present. */
    private static final class Capture extends org.objectweb.asm.ClassVisitor {
        private WatermarkAttribute found;

        Capture() {
            super(org.objectweb.asm.Opcodes.ASM9);
        }

        @Override
        public void visitAttribute(Attribute attribute) {
            if (attribute instanceof WatermarkAttribute w) {
                found = w;
            }
            super.visitAttribute(attribute);
        }
    }
}
