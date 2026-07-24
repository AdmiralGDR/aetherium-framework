/*
 * Aetherium Framework — shield pass: strip debug/metadata.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.shield;

import org.aetherium.bytecode.ClassContext;
import org.aetherium.bytecode.ClassTransformer;
import org.aetherium.bytecode.TransformResult;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Removes every scrap of debug metadata: the source file name, the source-debug extension, line-number
 * tables, and local-variable name/type tables.
 *
 * <p>EN: Debug metadata is a gift to both a human decompiler <em>and</em> an AI: line numbers re-associate
 * bytecode with the original structure, and local-variable tables hand over the author's meaningful variable
 * names. Stripping them forces any analysis to work from raw bytecode with synthetic {@code var1}/{@code var2}
 * names — the first and cheapest anti-analysis layer.
 * RU: Отладочная информация — подарок и человеку-декомпилятору, и ИИ: номера строк восстанавливают структуру
 * исходника, а таблицы локальных переменных выдают осмысленные имена автора. Их удаление заставляет любой
 * анализ работать с сырым байткодом и синтетическими именами {@code var1}/{@code var2}.
 */
public final class DebugStripTransformer implements ClassTransformer {

    private final int order;

    public DebugStripTransformer(int order) {
        this.order = order;
    }

    @Override
    public int order() {
        return order;
    }

    @Override
    public boolean handles(ClassContext context) {
        return true; // every protected class benefits
    }

    @Override
    public TransformResult apply(ClassContext context) {
        var node = context.node();
        node.sourceFile = null;
        node.sourceDebug = null;
        for (MethodNode method : node.methods) {
            method.localVariables = null;
            method.visibleLocalVariableAnnotations = null;
            method.invisibleLocalVariableAnnotations = null;
            method.parameters = null; // method-parameter names (MethodParameters attribute)
            if (method.instructions != null) {
                var it = method.instructions.iterator();
                while (it.hasNext()) {
                    if (it.next() instanceof LineNumberNode) {
                        it.remove();
                    }
                }
            }
        }
        return new TransformResult.Applied(node);
    }

    @Override
    public String id() {
        return "shield/debug-strip";
    }
}
