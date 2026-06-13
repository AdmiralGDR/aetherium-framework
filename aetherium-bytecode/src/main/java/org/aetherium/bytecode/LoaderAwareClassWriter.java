package org.aetherium.bytecode;

import org.objectweb.asm.ClassWriter;

/**
 * A {@link ClassWriter} that resolves common superclasses against a supplied {@link ClassLoader}.
 *
 * <p>EN: {@link ClassWriter#COMPUTE_FRAMES} must compute common superclasses, which means loading
 * referenced types. The default {@code ClassWriter} uses its own defining loader — wrong for mod
 * classes. We override {@link #getClassLoader()} to use the loader's classpath, and we make
 * {@link #getCommonSuperClass} <em>fail-safe</em>: if a type cannot be resolved (a class genuinely
 * absent at transform time), we fall back to {@code java/lang/Object} rather than throwing. That
 * keeps a single unresolvable reference from aborting the whole transform — the JVM performs the
 * authoritative verification at {@code defineClass} anyway ({@code docs/en/native-bridge.md} aside:
 * availability over fragility).
 *
 * <p>RU: {@link ClassWriter#COMPUTE_FRAMES} должен вычислять общие суперклассы, что означает
 * загрузку ссылаемых типов. Стандартный {@code ClassWriter} использует свой загрузчик — неверно для
 * классов модов. Мы переопределяем {@link #getClassLoader()}, чтобы использовать classpath
 * загрузчика, и делаем {@link #getCommonSuperClass} <em>отказоустойчивым</em>: если тип не
 * разрешается, мы откатываемся к {@code java/lang/Object}, а не бросаем исключение. Это не даёт
 * одной неразрешимой ссылке прервать всю трансформацию — окончательную верификацию всё равно
 * выполняет JVM при {@code defineClass}.
 */
final class LoaderAwareClassWriter extends ClassWriter {

    private final ClassLoader classLoader;

    LoaderAwareClassWriter(int flags, ClassLoader classLoader) {
        super(flags);
        this.classLoader = classLoader;
    }

    @Override
    protected ClassLoader getClassLoader() {
        return classLoader;
    }

    @Override
    protected String getCommonSuperClass(String type1, String type2) {
        try {
            return super.getCommonSuperClass(type1, type2);
        } catch (RuntimeException | LinkageError unresolved) {
            // Conservative, always-valid fallback: Object is a supertype of everything.
            return "java/lang/Object";
        }
    }
}
