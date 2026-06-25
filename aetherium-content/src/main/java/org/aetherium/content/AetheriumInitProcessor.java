/*
 * Aetherium Framework — @AetheriumInit zero-config entrypoint processor (compile-time auto-wiring).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.content;

import org.aetherium.core.mod.AetheriumInit;
import org.aetherium.datagen.InitMethod;
import org.aetherium.datagen.InitOrdering;
import org.aetherium.datagen.InitSourceWriter;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Discovers {@code @AetheriumInit} methods at compile time and generates the zero-config entrypoint.
 *
 * <p>EN: For each {@code public static void m(AetheriumContext)} annotated with
 * {@link AetheriumInit}, this records an {@link InitMethod}; once the final round is over it
 * {@link InitOrdering orders} them into a deterministic DAG and {@link InitSourceWriter generates} a
 * single {@code AetheriumMod} that invokes them by <strong>direct static call</strong> in order, plus the
 * matching {@code META-INF/services} registration. The developer therefore writes <em>no</em> entrypoint
 * class and <em>no</em> services file, and the running game uses <em>no</em> reflection or classpath
 * scanning to find the inits — discovery happened entirely in {@code javac}. A bad signature is a
 * compile error pointing at the offending method; a dependency cycle fails the build. The mod id (for the
 * generated class name + {@code id()}) comes from {@code -Aaetherium.modId=<id>}, else {@code "aetherium"}.
 *
 * <p>RU: Для каждого {@code public static void m(AetheriumContext)}, помеченного {@link AetheriumInit},
 * записывается {@link InitMethod}; по завершении последнего раунда они {@link InitOrdering упорядочиваются}
 * в детерминированный DAG, и {@link InitSourceWriter генерируется} один {@code AetheriumMod}, вызывающий
 * их <strong>прямым статическим вызовом</strong>, плюс запись {@code META-INF/services}. Разработчик не
 * пишет <em>ни</em> класс-entrypoint, <em>ни</em> services-файл, а игра не использует <em>ни</em>
 * рефлексию, <em>ни</em> сканирование classpath — обнаружение целиком в {@code javac}. Неверная сигнатура
 * — ошибка компиляции; цикл зависимостей валит сборку.
 */
@SupportedOptions(AetheriumInitProcessor.OPTION_MOD_ID)
public final class AetheriumInitProcessor extends AbstractProcessor {

    static final String OPTION_MOD_ID = "aetherium.modId";
    private static final String CONTEXT_FQN = "org.aetherium.core.mod.AetheriumContext";
    private static final String MOD_SERVICE = "META-INF/services/org.aetherium.core.mod.AetheriumMod";

    private final List<InitMethod> collected = new ArrayList<>();
    private boolean generated;

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(AetheriumInit.class.getCanonicalName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element el : roundEnv.getElementsAnnotatedWith(AetheriumInit.class)) {
            if (!validateAndCollect(el)) {
                continue; // an error was already reported against el
            }
        }
        if (roundEnv.processingOver() && !generated && !collected.isEmpty()) {
            generate();
        }
        return false; // never claim the annotation — let other processors observe it too
    }

    /** Validate the signature ({@code public static void m(AetheriumContext)}) and record the method. */
    private boolean validateAndCollect(Element el) {
        if (el.getKind() != ElementKind.METHOD) {
            error(el, "@AetheriumInit may only annotate methods");
            return false;
        }
        ExecutableElement m = (ExecutableElement) el;
        Set<Modifier> mods = m.getModifiers();
        if (!mods.contains(Modifier.PUBLIC) || !mods.contains(Modifier.STATIC)) {
            error(el, "@AetheriumInit method must be public static");
            return false;
        }
        if (m.getReturnType().getKind() != javax.lang.model.type.TypeKind.VOID) {
            error(el, "@AetheriumInit method must return void");
            return false;
        }
        List<? extends VariableElement> params = m.getParameters();
        if (params.size() != 1 || !CONTEXT_FQN.equals(params.get(0).asType().toString())) {
            error(el, "@AetheriumInit method must take exactly one parameter of type " + CONTEXT_FQN);
            return false;
        }

        TypeElement owner = (TypeElement) m.getEnclosingElement();
        AetheriumInit ann = m.getAnnotation(AetheriumInit.class);
        String id = ann.id().isBlank()
                ? owner.getSimpleName() + "." + m.getSimpleName()
                : ann.id();
        collected.add(new InitMethod(
                id,
                owner.getQualifiedName().toString(),
                m.getSimpleName().toString(),
                List.of(ann.runBefore()),
                List.of(ann.runAfter())));
        return true;
    }

    private void generate() {
        // The generated AetheriumMod's class name + id() are mod-id-scoped to prevent collisions when
        // multiple Aetherium mods share a classpath. A silent "aetherium" fallback would make two mods
        // generate the same class/service entry — so a missing -Aaetherium.modId is a hard build error.
        String modId = processingEnv.getOptions().get(OPTION_MOD_ID);
        if (modId == null || modId.isBlank()) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Aetherium @AetheriumInit requires the mod id: pass -Aaetherium.modId=<id> to javac "
                            + "(the Aetherium Gradle plugin sets this automatically). Refusing to generate a "
                            + "collision-prone default entrypoint.");
            return;
        }
        List<InitMethod> ordered;
        try {
            ordered = InitOrdering.order(collected);
        } catch (IllegalStateException bad) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Aetherium @AetheriumInit wiring failed: " + bad.getMessage());
            return;
        }

        try {
            // (1) The generated AetheriumMod with direct static dispatch in DAG order.
            String qualified = InitSourceWriter.qualifiedName(modId);
            JavaFileObject src = processingEnv.getFiler().createSourceFile(qualified);
            try (Writer w = src.openWriter()) {
                w.write(InitSourceWriter.generate(modId, ordered));
            }

            // (2) Register it for ServiceLoader — no hand-written services file needed.
            FileObject service = processingEnv.getFiler()
                    .createResource(StandardLocation.CLASS_OUTPUT, "", MOD_SERVICE);
            try (Writer w = service.openWriter()) {
                w.write("# Generated by Aetherium @AetheriumInit processor.\n");
                w.write(InitSourceWriter.serviceEntry(modId));
                w.write('\n');
            }

            generated = true;
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                    "Aetherium: auto-wired " + ordered.size()
                            + " @AetheriumInit method(s) into " + qualified + " (no entrypoint boilerplate).");
        } catch (IOException io) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Aetherium @AetheriumInit generation failed: " + io.getMessage());
        }
    }

    private void error(Element el, String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, el);
    }
}
