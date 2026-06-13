package org.aetherium.bytecode;

import org.aetherium.core.Diagnostic;
import org.aetherium.core.SymbolManifest;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The Aetherium bytecode manipulation engine: {@code ClassReader → TransformChain → ClassWriter}.
 *
 * <p>EN: The single entry point that hides all ASM complexity from callers (zero-boilerplate goal).
 * It is <strong>safe by construction</strong>: it retains the original {@code byte[]} and, on
 * <em>any</em> failure — a transformer throwing, a {@link TransformResult.Failed}, a structural
 * check failing, a verification error, or a per-class timeout — it logs a structured
 * {@link Diagnostic} and returns the <em>original</em> bytes. A single bad class can never crash the
 * launch. Batches run on {@link Executors#newVirtualThreadPerTaskExecutor() virtual threads}, one
 * per class, because each transform is pure and independent.
 *
 * <p>RU: Единственная точка входа, скрывающая всю сложность ASM от вызывающих (цель «ноль
 * шаблонного кода»). Он <strong>безопасен по построению</strong>: сохраняет исходный {@code byte[]}
 * и при <em>любом</em> сбое — исключении трансформера, {@link TransformResult.Failed}, провале
 * структурной проверки, ошибке верификации или таймауте — логирует структурированный
 * {@link Diagnostic} и возвращает <em>исходные</em> байты. Один плохой класс не может уронить
 * запуск. Пакеты выполняются на {@link Executors#newVirtualThreadPerTaskExecutor() виртуальных
 * потоках}, по одному на класс, поскольку каждая трансформация чиста и независима.
 */
public final class BytecodeEngine {

    private final SymbolManifest manifest;
    private final TransformChain chain;
    private final EngineConfig config;
    private final ClassLoader classLoader;

    private BytecodeEngine(SymbolManifest manifest, TransformChain chain, EngineConfig config, ClassLoader classLoader) {
        this.manifest = manifest;
        this.chain = chain;
        this.config = config;
        this.classLoader = classLoader;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Transform one class. Never throws: returns transformed bytes on success, or the original
     * bytes (plus a logged diagnostic) on any failure.
     */
    public byte[] transformClass(byte[] original, DiagnosticSink sink) {
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(sink, "sink");

        String internalName = "<unreadable>";
        try {
            ClassReader reader = new ClassReader(original);
            internalName = reader.getClassName();

            ClassNode node = new ClassNode(config.asmApi());
            reader.accept(node, config.parsingOptions());

            ClassContext context = new ClassContext(node, node.name, manifest);

            for (ClassTransformer transformer : chain.transformers()) {
                if (!transformer.handles(context)) {
                    continue;
                }
                TransformResult result = transformer.apply(context);
                switch (result) {
                    case TransformResult.Applied ignored -> {
                        // node mutated in place; continue down the chain
                    }
                    case TransformResult.Skipped ignored -> {
                        // nothing to do
                    }
                    case TransformResult.Failed failed -> {
                        sink.accept(failed.diagnostic());
                        return original; // revert: a reported failure is a hard stop for this class
                    }
                }
            }

            ClassWriter writer = new LoaderAwareClassWriter(config.writerFlags(), classLoader);
            // Structural verification (no class loading): throws on malformed visit sequences.
            node.accept(new CheckClassAdapter(writer, false));
            byte[] transformed = writer.toByteArray();

            if (config.verify()) {
                String errors = verifyDataflow(transformed);
                if (errors != null && !errors.isBlank()) {
                    sink.accept(Diagnostic.error(
                            "AE-VERIFY-001",
                            "Bytecode verification failed for " + node.name + "; reverting. " + firstLine(errors)));
                    return original;
                }
            }

            return transformed;
        } catch (Throwable failure) {
            // Catch Throwable on purpose: a transformer or ASM must never take down the load phase.
            sink.accept(Diagnostic.error(
                    "AE-TRANSFORM-001",
                    "Transform threw for " + internalName + "; reverting to original ("
                            + failure.getClass().getSimpleName() + ": " + failure.getMessage() + ")"));
            return original;
        }
    }

    /**
     * Transform a batch of classes in parallel on virtual threads. Order of the returned map matches
     * the input. Each class is isolated by a {@link EngineConfig#perClassTimeout() timeout}; a class
     * that exceeds it is cancelled and reverts to its original bytes.
     */
    public Map<String, byte[]> transformAll(Map<String, byte[]> classes, DiagnosticSink sink) {
        Objects.requireNonNull(classes, "classes");
        Objects.requireNonNull(sink, "sink");

        Map<String, byte[]> output = new LinkedHashMap<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Map<String, Future<byte[]>> futures = new LinkedHashMap<>();
            for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
                byte[] original = entry.getValue();
                futures.put(entry.getKey(), executor.submit(() -> transformClass(original, sink)));
            }

            long timeoutMillis = config.perClassTimeout().toMillis();
            for (Map.Entry<String, Future<byte[]>> entry : futures.entrySet()) {
                String name = entry.getKey();
                try {
                    output.put(name, entry.getValue().get(timeoutMillis, TimeUnit.MILLISECONDS));
                } catch (TimeoutException timeout) {
                    entry.getValue().cancel(true);
                    sink.accept(Diagnostic.error(
                            "AE-TIMEOUT-001",
                            "Transform of " + name + " exceeded " + config.perClassTimeout() + "; reverting."));
                    output.put(name, classes.get(name));
                } catch (ExecutionException execution) {
                    sink.accept(Diagnostic.error(
                            "AE-TASK-001",
                            "Transform task failed for " + name + "; reverting (" + execution.getCause() + ")."));
                    output.put(name, classes.get(name));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    output.put(name, classes.get(name));
                }
            }
        }
        return output;
    }

    public SymbolManifest manifest() {
        return manifest;
    }

    public EngineConfig config() {
        return config;
    }

    /**
     * Best-effort dataflow verification. Returns the verifier's error text (non-blank on failure),
     * or {@code null} when the verifier could not run at all — in which case we accept the
     * structurally-valid bytes and rely on the JVM's own verification at {@code defineClass}.
     */
    private String verifyDataflow(byte[] bytes) {
        StringWriter buffer = new StringWriter();
        try (PrintWriter out = new PrintWriter(buffer)) {
            CheckClassAdapter.verify(new ClassReader(bytes), classLoader, false, out);
        } catch (Throwable verifierInfrastructure) {
            // e.g. a referenced type could not be loaded for type-checking. Not a proof that the
            // bytecode is invalid, so don't penalize the transform.
            return null;
        }
        return buffer.toString();
    }

    private static String firstLine(String text) {
        int newline = text.indexOf('\n');
        String line = newline >= 0 ? text.substring(0, newline) : text;
        return line.length() > 200 ? line.substring(0, 200) + "…" : line;
    }

    /** Builder for {@link BytecodeEngine}. */
    public static final class Builder {
        private SymbolManifest manifest = SymbolManifest.builder().build();
        private final List<ClassTransformer> transformers = new ArrayList<>();
        private EngineConfig config = EngineConfig.defaults();
        private ClassLoader classLoader = BytecodeEngine.class.getClassLoader();

        private Builder() {
        }

        public Builder manifest(SymbolManifest manifest) {
            this.manifest = Objects.requireNonNull(manifest, "manifest");
            return this;
        }

        public Builder transformer(ClassTransformer transformer) {
            this.transformers.add(Objects.requireNonNull(transformer, "transformer"));
            return this;
        }

        public Builder config(EngineConfig config) {
            this.config = Objects.requireNonNull(config, "config");
            return this;
        }

        public Builder classLoader(ClassLoader classLoader) {
            this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
            return this;
        }

        public BytecodeEngine build() {
            return new BytecodeEngine(
                    manifest,
                    TransformChain.of(transformers.toArray(ClassTransformer[]::new)),
                    config,
                    classLoader);
        }
    }
}
