/**
 * Aetherium Framework — ASM-based bytecode manipulation engine.
 *
 * <p><b>EN.</b> The load-phase machinery that rewrites mod classes so loader-agnostic API calls are
 * lowered to {@code O(1)} {@code invokedynamic} dispatch. The entry point is
 * {@link org.aetherium.bytecode.BytecodeEngine}: {@code ClassReader → TransformChain → ClassWriter}
 * with {@code COMPUTE_FRAMES}, structural + dataflow verification, virtual-thread parallelism, and a
 * revert-to-original safety net. Transformers implement the open
 * {@link org.aetherium.bytecode.ClassTransformer} SPI; the core transform is
 * {@link org.aetherium.bytecode.transform.DispatchLoweringTransformer}. Runtime linkage lives in
 * {@link org.aetherium.bytecode.runtime}. This module depends only on {@code aetherium-core} and ASM
 * — never on any loader.
 *
 * <p><b>RU.</b> Механизм фазы загрузки, переписывающий классы модов так, чтобы вызовы API,
 * независимые от загрузчика, понижались до {@code O(1)}-диспетчеризации {@code invokedynamic}. Точка
 * входа — {@link org.aetherium.bytecode.BytecodeEngine}: {@code ClassReader → TransformChain →
 * ClassWriter} с {@code COMPUTE_FRAMES}, структурной проверкой и проверкой потоков данных,
 * параллелизмом на виртуальных потоках и страховкой отката к оригиналу. Трансформеры реализуют
 * открытый SPI {@link org.aetherium.bytecode.ClassTransformer}; ключевая трансформация —
 * {@link org.aetherium.bytecode.transform.DispatchLoweringTransformer}. Рантайм-линковка — в
 * {@link org.aetherium.bytecode.runtime}. Модуль зависит только от {@code aetherium-core} и ASM —
 * никогда от загрузчика.
 */
package org.aetherium.bytecode;
