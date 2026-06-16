/*
 * Aetherium Framework — ModLauncher launch plugin (class interception).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.loader;

import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.aetherium.core.diag.DiagnosticTranslator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;

/**
 * The actual runtime interception point: ModLauncher offers every loaded class here.
 *
 * <p>EN: {@link #handlesClass} is the performance gate — a cheap namespace check via
 * {@link AetheriumNamespaces} that returns "no phases" (skip) for vanilla {@code net.minecraft}/
 * NeoForge/JDK/framework classes, so the engine is invoked <em>only</em> for Aetherium-mod classes.
 * For those, {@link #processClass} delegates to the pure {@link AetheriumTransformEngine}: it
 * serializes the node to bytes, runs the engine (which lowers API calls to {@code invokedynamic},
 * verifies, and — crucially — returns the <em>original</em> bytes on any failure), and only rewrites
 * the node when the bytes actually changed. A failed transform thus leaves the class untouched and
 * the game keeps loading. This module speaks ModLauncher; {@code aetherium-bytecode} never does.
 *
 * <p>RU: Реальная точка перехвата: ModLauncher предлагает сюда каждый загружаемый класс.
 * {@link #handlesClass} — барьер производительности: дешёвая проверка пространства имён через
 * {@link AetheriumNamespaces}, возвращающая «нет фаз» (пропуск) для ванильных
 * {@code net.minecraft}/NeoForge/JDK/фреймворка, поэтому движок вызывается <em>только</em> для
 * классов модов Aetherium. Для них {@link #processClass} делегирует чистому
 * {@link AetheriumTransformEngine}: сериализует узел в байты, запускает движок (понижает вызовы API
 * в {@code invokedynamic}, верифицирует и — главное — возвращает <em>исходные</em> байты при любом
 * сбое) и переписывает узел только при реальном изменении байтов. Сбойная трансформация оставляет
 * класс нетронутым, и игра продолжает загрузку.
 */
public final class AetheriumLaunchPlugin implements ILaunchPluginService {

    private static final Logger LOG = LoggerFactory.getLogger("Aetherium/LaunchPlugin");
    private static final String NAME = "aetherium";

    private final AetheriumTransformEngine engine = AetheriumTransformEngine.create();

    @Override
    public String name() {
        return NAME;
    }

    /**
     * The namespace filter. Returns the phases we want to run in — or an empty set to be skipped
     * entirely. This is what protects vanilla/NeoForge classes from ever reaching the engine.
     */
    @Override
    public EnumSet<Phase> handlesClass(Type classType, boolean isEmpty) {
        if (isEmpty || classType == null) {
            return EnumSet.noneOf(Phase.class);
        }
        String internalName = classType.getInternalName();
        // Aetherium-mod classes (API lowering) OR any class a programmatic injection rule targets —
        // the latter deliberately lets a vanilla net.minecraft target through the namespace deny-list,
        // because that is the whole point of the injector (the "Mixin killer").
        if (AetheriumNamespaces.shouldTransform(internalName) || engine.hasInjectionFor(internalName)) {
            // Run AFTER other plugins (e.g. Mixin) have had their say.
            return EnumSet.of(Phase.AFTER);
        }
        return EnumSet.noneOf(Phase.class);
    }

    /** Transform an Aetherium-mod class via the pure engine. Returns true iff the node changed. */
    @Override
    public boolean processClass(Phase phase, ClassNode classNode, Type classType) {
        try {
            // Node -> bytes (ModLauncher's ASM), engine (our ASM, on bytes), back to node.
            ClassWriter writer = new ClassWriter(0);
            classNode.accept(writer);
            byte[] input = writer.toByteArray();

            byte[] output = engine.transform(input); // never throws; original bytes on failure

            if (Arrays.equals(input, output)) {
                return false; // no change (or a contained failure already reverted)
            }

            repopulate(classNode, output);
            LOG.debug("Aetherium transformed {}", classType.getInternalName());
            return true;
        } catch (Throwable unexpected) {
            // Belt-and-suspenders: the engine is total, but if anything here throws we must NOT
            // let it reach ModLauncher. Log a human-readable diagnostic and leave the class as-is.
            LOG.warn("Aetherium skipped {} after an unexpected error: {}",
                    classType.getInternalName(),
                    DiagnosticTranslator.translate(unexpected).english());
            return false;
        }
    }

    /**
     * Replace the contents of {@code node} with the parsed {@code bytes}. We clear the appendable
     * fields first, then re-parse, because {@link ClassReader#accept} appends to (rather than
     * replaces) the node's collections.
     */
    private static void repopulate(ClassNode node, byte[] bytes) {
        node.methods = new ArrayList<>();
        node.fields = new ArrayList<>();
        node.innerClasses = new ArrayList<>();
        node.interfaces = new ArrayList<>();
        node.visibleAnnotations = null;
        node.invisibleAnnotations = null;
        node.visibleTypeAnnotations = null;
        node.invisibleTypeAnnotations = null;
        node.attrs = null;
        node.nestMembers = null;
        node.permittedSubclasses = null;
        node.recordComponents = null;
        node.module = null;
        new ClassReader(bytes).accept(node, 0);
    }
}
