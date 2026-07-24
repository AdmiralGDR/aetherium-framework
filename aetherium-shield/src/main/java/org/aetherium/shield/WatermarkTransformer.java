/*
 * Aetherium Framework — shield pass: author watermark.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.shield;

import org.aetherium.bytecode.ClassContext;
import org.aetherium.bytecode.ClassTransformer;
import org.aetherium.bytecode.TransformResult;

import java.util.ArrayList;

/**
 * Stamps each protected class with a {@link WatermarkAttribute} carrying the author signature — traceability
 * for a leaked/ripped mod. Adds an invisible, execution-neutral attribute; see {@link WatermarkAttribute}.
 */
public final class WatermarkTransformer implements ClassTransformer {

    private final int order;
    private final String author;

    public WatermarkTransformer(int order, String author) {
        this.order = order;
        this.author = author == null ? "" : author;
    }

    @Override
    public int order() {
        return order;
    }

    @Override
    public boolean handles(ClassContext context) {
        return true;
    }

    @Override
    public TransformResult apply(ClassContext context) {
        var node = context.node();
        if (node.attrs == null) {
            node.attrs = new ArrayList<>();
        }
        node.attrs.add(new WatermarkAttribute(author));
        return new TransformResult.Applied(node);
    }

    @Override
    public String id() {
        return "shield/watermark";
    }
}
