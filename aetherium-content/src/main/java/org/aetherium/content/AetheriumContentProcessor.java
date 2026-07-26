/*
 * Aetherium Framework — content annotation processor (build-time generator + index emitter).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.content;

import org.aetherium.datagen.AssetGenerator;
import org.aetherium.datagen.BehaviorEntry;
import org.aetherium.datagen.BehaviorIndex;
import org.aetherium.datagen.ContentEntry;
import org.aetherium.datagen.ContentIndex;
import org.aetherium.datagen.ContentKind;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The compile-time engine behind the zero-boilerplate content API.
 *
 * <p>EN: Discovers {@code @AetheriumBlock}/{@code @AetheriumItem} classes during compilation and, when
 * the final round is over, (1) generates every resource JSON via the pure {@link AssetGenerator} and
 * writes it into the compiler's {@code CLASS_OUTPUT} (so it lands in the mod jar with no extra Gradle
 * wiring), and (2) writes the {@link ContentIndex} the loader reads at runtime to auto-register the
 * content. It uses no Minecraft type and runs entirely inside {@code javac} — satisfying the strict
 * "datagen runs outside the game" rule. The mod id comes from the annotation, else the
 * {@code -Aaetherium.modId=<id>} option, else {@code "aetherium"}.
 *
 * <p>RU: Движок этапа компиляции за API контента без шаблонов. Находит классы
 * {@code @AetheriumBlock}/{@code @AetheriumItem} при компиляции и по завершении последнего раунда
 * (1) генерирует весь JSON ресурсов через чистый {@link AssetGenerator} и пишет его в
 * {@code CLASS_OUTPUT} компилятора (он попадает в jar без дополнительной настройки Gradle), и
 * (2) пишет {@link ContentIndex}, читаемый загрузчиком в рантайме для авто-регистрации. Не использует
 * типы Minecraft и работает целиком внутри {@code javac}. Mod id берётся из аннотации, иначе из опции
 * {@code -Aaetherium.modId=<id>}, иначе {@code "aetherium"}.
 */
@SupportedOptions({AetheriumContentProcessor.OPTION_MOD_ID, AetheriumContentProcessor.OPTION_RESOURCES_DIR})
public final class AetheriumContentProcessor extends AbstractProcessor {

    static final String OPTION_MOD_ID = "aetherium.modId";
    static final String OPTION_RESOURCES_DIR = "aetherium.resourcesDir";
    private static final String DEFAULT_MOD_ID = "aetherium";

    private static final String MACHINE_LOGIC = "org.aetherium.content.AetheriumMachineLogic";

    private final List<ContentEntry> collected = new ArrayList<>();
    private final List<BehaviorEntry> behaviors = new ArrayList<>();

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(AetheriumBlock.class.getCanonicalName(), AetheriumItem.class.getCanonicalName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        String fallbackModId = processingEnv.getOptions().getOrDefault(OPTION_MOD_ID, DEFAULT_MOD_ID);

        for (Element el : roundEnv.getElementsAnnotatedWith(AetheriumBlock.class)) {
            AetheriumBlock a = el.getAnnotation(AetheriumBlock.class);
            if (a == null) {
                continue;
            }
            String modId = resolveModId(a.modId(), fallbackModId);
            collected.add(new ContentEntry(
                    ContentKind.BLOCK,
                    modId,
                    a.name(),
                    el.toString(),
                    a.hardness(),
                    a.resistance(),
                    a.requiresTool(),
                    a.dropSelf(),
                    64,
                    a.displayName()));
            recordBehavior(ContentKind.BLOCK, modId, a.name(), behaviorMirror(a::behavior));
        }

        for (Element el : roundEnv.getElementsAnnotatedWith(AetheriumItem.class)) {
            AetheriumItem a = el.getAnnotation(AetheriumItem.class);
            if (a == null) {
                continue;
            }
            String modId = resolveModId(a.modId(), fallbackModId);
            collected.add(new ContentEntry(
                    ContentKind.ITEM,
                    modId,
                    a.name(),
                    el.toString(),
                    1.0f,
                    1.0f,
                    false,
                    false,
                    a.maxStackSize(),
                    a.displayName()));
            recordBehavior(ContentKind.ITEM, modId, a.name(), behaviorMirror(a::behavior));
        }

        if (roundEnv.processingOver() && !collected.isEmpty()) {
            emit();
            warnMissingTextures();
            if (!behaviors.isEmpty()) {
                emitBehaviors();
            }
        }
        return false; // never claim the annotations — allow other processors to observe them too
    }

    private void emit() {
        try {
            // (1) Resource JSON → CLASS_OUTPUT (bundled into the jar automatically).
            Map<String, String> files = AssetGenerator.generate(collected);
            Set<String> written = new LinkedHashSet<>();
            for (Map.Entry<String, String> f : files.entrySet()) {
                FileObject out = processingEnv.getFiler()
                        .createResource(StandardLocation.CLASS_OUTPUT, "", f.getKey());
                try (Writer w = out.openWriter()) {
                    w.write(f.getValue());
                }
                written.add(f.getKey());
            }

            // (2) Runtime index → CLASS_OUTPUT (read by the loader to auto-register content).
            FileObject index = processingEnv.getFiler()
                    .createResource(StandardLocation.CLASS_OUTPUT, "", ContentIndex.RESOURCE);
            try (Writer w = index.openWriter()) {
                w.write("# Aetherium content index — generated; do not edit.\n");
                for (ContentEntry e : collected) {
                    w.write(ContentIndex.serialize(e));
                    w.write('\n');
                }
            }

            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                    "Aetherium: generated " + written.size() + " asset file(s) for "
                            + collected.size() + " declared content piece(s).");
        } catch (IOException io) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Aetherium content generation failed: " + io.getMessage());
        }
    }

    /**
     * Warn () when a declared block's texture is missing, naming the exact path the generated model
     * references. 's {@code AssetGenerator} stopped substituting a vanilla texture (a content framework
     * must not guess a mod's art), so a block whose author ships no PNG renders as the missing-texture
     * checkerboard — a green build that looks broken. The processor already knows the path it wrote into the
     * model, so it can say exactly what to add. Best-effort: if the PNG is already resolvable in
     * {@code CLASS_OUTPUT} (Gradle copied it from resources), stay silent; when detection is inconclusive we
     * still warn — the message names the correct path, so a false warning is a harmless nudge, never wrong.
     */
    private void warnMissingTextures() {
        for (ContentEntry e : collected) {
            if (e.kind() != ContentKind.BLOCK) {
                continue;
            }
            String path = "assets/" + e.modId() + "/textures/block/" + e.name() + ".png";
            if (textureLikelyPresent(path)) {
                continue;
            }
            processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                    "Aetherium: block '" + e.modId() + ":" + e.name() + "' expects a texture at " + path
                            + " — ship that 16x16 PNG or the block renders as the missing-texture checkerboard.");
        }
    }

    /**
     * Is {@code path} present so it will land in the jar? Reliable when {@code -Aaetherium.resourcesDir} is set
     * (the Aetherium Gradle plugin passes the mod's real {@code src/main/resources} dirs, which the processor's
     * {@code CLASS_OUTPUT} never contains); otherwise falls back to a best-effort {@code CLASS_OUTPUT} probe and,
     * failing that, warns — the message names the correct path, so a false warning is a harmless nudge.
     */
    private boolean textureLikelyPresent(String path) {
        String resourceDirs = processingEnv.getOptions().get(OPTION_RESOURCES_DIR);
        if (resourceDirs != null && !resourceDirs.isBlank()) {
            for (String dir : resourceDirs.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
                if (dir.isBlank()) {
                    continue;
                }
                if (new java.io.File(dir, path).isFile()) {
                    return true;
                }
            }
            return false; // authoritative: the plugin told us exactly where resources are, and it is not there
        }
        try {
            FileObject fo = processingEnv.getFiler().getResource(StandardLocation.CLASS_OUTPUT, "", path);
            try (var in = fo.openInputStream()) {
                return in.read() != -1; // any byte read → the PNG is present
            }
        } catch (IOException | IllegalArgumentException probablyAbsent) {
            return false; // not there (or not knowable yet) → warn, naming the path
        }
    }

    private static String resolveModId(String annotationValue, String fallback) {
        return (annotationValue == null || annotationValue.isBlank()) ? fallback : annotationValue.trim();
    }

    /**
     * Read an annotation's {@code Class<?> behavior()} as a {@link TypeMirror}. Accessing the value during
     * processing throws {@link MirroredTypeException} (the class may be uncompiled) — the standard way to
     * obtain the mirror.
     */
    private static TypeMirror behaviorMirror(Supplier<Class<?>> accessor) {
        try {
            accessor.get();
            return null; // unreachable for a Class<?> member, but keeps the compiler happy
        } catch (MirroredTypeException mte) {
            return mte.getTypeMirror();
        }
    }

    /** Record a non-default behavior binding (and whether it is machine logic) for the behavior index. */
    private void recordBehavior(ContentKind kind, String modId, String name, TypeMirror behavior) {
        if (behavior == null) {
            return;
        }
        String fqn = behavior.toString();
        if (fqn.equals("java.lang.Object") || fqn.isBlank()) {
            return; // the default → no behavior
        }
        behaviors.add(new BehaviorEntry(kind, modId, name, fqn, implementsMachineLogic(behavior)));
    }

    /** True if {@code behavior} implements {@link org.aetherium.content.AetheriumMachineLogic}. */
    private boolean implementsMachineLogic(TypeMirror behavior) {
        TypeElement machineLogic = processingEnv.getElementUtils().getTypeElement(MACHINE_LOGIC);
        if (machineLogic == null) {
            return false;
        }
        return processingEnv.getTypeUtils().isAssignable(
                behavior, processingEnv.getTypeUtils().erasure(machineLogic.asType()));
    }

    /** Write the behavior index (machine-logic blocks, item behaviors) for the loader to wire. */
    private void emitBehaviors() {
        try {
            FileObject index = processingEnv.getFiler()
                    .createResource(StandardLocation.CLASS_OUTPUT, "", BehaviorIndex.RESOURCE);
            try (Writer w = index.openWriter()) {
                w.write("# Aetherium behavior index — generated; do not edit.\n");
                for (BehaviorEntry e : behaviors) {
                    w.write(BehaviorIndex.serialize(e));
                    w.write('\n');
                }
            }
            long machines = behaviors.stream().filter(BehaviorEntry::machineLogic).count();
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE,
                    "Aetherium: wired " + behaviors.size() + " content behavior(s), "
                            + machines + " with auto BlockEntity ticking.");
        } catch (IOException io) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Aetherium behavior index generation failed: " + io.getMessage());
        }
    }
}
