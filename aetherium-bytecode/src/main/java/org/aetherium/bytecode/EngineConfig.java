package org.aetherium.bytecode;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable engine configuration.
 *
 * <p>EN: Centralizes the ASM knobs and the safety budget so nothing is hardcoded across the engine.
 * {@code writerFlags} defaults to {@link ClassWriter#COMPUTE_FRAMES} (the load-phase cost we accept
 * once); {@code parsingOptions} defaults to {@link ClassReader#SKIP_FRAMES} since we recompute them;
 * {@code perClassTimeout} bounds each isolated transform so a pathological class cannot wedge the
 * load phase; {@code verify} toggles the best-effort dataflow verification pass.
 *
 * <p>RU: Централизует параметры ASM и бюджет безопасности, чтобы ничего не было зашито по движку.
 * {@code writerFlags} по умолчанию {@link ClassWriter#COMPUTE_FRAMES} (стоимость фазы загрузки,
 * принимаемая однократно); {@code parsingOptions} по умолчанию {@link ClassReader#SKIP_FRAMES},
 * так как мы их пересчитываем; {@code perClassTimeout} ограничивает каждую изолированную
 * трансформацию, чтобы патологический класс не заклинил фазу загрузки; {@code verify} включает
 * best-effort проверку потоков данных.
 *
 * @param asmApi          ASM API level (e.g. {@link Opcodes#ASM9})
 * @param writerFlags     {@link ClassWriter} flags
 * @param parsingOptions  {@link ClassReader} parsing options
 * @param perClassTimeout per-class transform timeout
 * @param verify          run the best-effort {@code CheckClassAdapter} dataflow verification
 */
public record EngineConfig(int asmApi, int writerFlags, int parsingOptions, Duration perClassTimeout, boolean verify) {

    public EngineConfig {
        Objects.requireNonNull(perClassTimeout, "perClassTimeout");
        if (perClassTimeout.isNegative() || perClassTimeout.isZero()) {
            throw new IllegalArgumentException("perClassTimeout must be positive: " + perClassTimeout);
        }
    }

    /** Sensible defaults: ASM9, COMPUTE_FRAMES, SKIP_FRAMES, 5s timeout, verification on. */
    public static EngineConfig defaults() {
        return new EngineConfig(
                Opcodes.ASM9,
                ClassWriter.COMPUTE_FRAMES,
                ClassReader.SKIP_FRAMES,
                Duration.ofSeconds(5),
                true);
    }
}
