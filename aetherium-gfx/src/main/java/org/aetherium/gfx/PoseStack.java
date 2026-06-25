/*
 * Aetherium Framework — a transform stack (the loader-agnostic PoseStack abstraction).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.gfx;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A push/pop stack of {@link Mat4} transforms — the pure mirror of Minecraft's {@code PoseStack}.
 *
 * <p>EN: {@link #translate}/{@link #scale}/{@link #rotate*} post-multiply the top matrix (child-relative
 * composition, like OpenGL/Blaze3D), so a {@link Skeleton} can be posed bone-by-bone. {@link #last()} is
 * the current accumulated transform an animation engine reads to place geometry; the loader copies it into
 * the real {@code PoseStack}. No game type involved.
 * RU: {@link #translate}/{@link #scale}/{@link #rotate*} домножают верхнюю матрицу справа (композиция
 * относительно ребёнка, как в OpenGL/Blaze3D), поэтому {@link Skeleton} можно позиционировать покостно.
 * {@link #last()} — текущая накопленная трансформация; загрузчик копирует её в реальный {@code PoseStack}.
 */
public final class PoseStack {

    private final Deque<Mat4> stack = new ArrayDeque<>();

    public PoseStack() {
        stack.push(Mat4.identity());
    }

    /** The current (top) transform. */
    public Mat4 last() {
        return stack.peek();
    }

    /** Duplicate the top transform (save). */
    public void pushPose() {
        stack.push(stack.peek());
    }

    /** Discard the top transform (restore). */
    public void popPose() {
        if (stack.size() <= 1) {
            throw new IllegalStateException("PoseStack underflow: popPose without a matching pushPose");
        }
        stack.pop();
    }

    public int depth() {
        return stack.size();
    }

    public PoseStack translate(float x, float y, float z) {
        return apply(Mat4.translation(x, y, z));
    }

    public PoseStack scale(float x, float y, float z) {
        return apply(Mat4.scaling(x, y, z));
    }

    public PoseStack rotateX(float radians) {
        return apply(Mat4.rotationX(radians));
    }

    public PoseStack rotateY(float radians) {
        return apply(Mat4.rotationY(radians));
    }

    public PoseStack rotateZ(float radians) {
        return apply(Mat4.rotationZ(radians));
    }

    /** Post-multiply an arbitrary transform onto the top of the stack. */
    public PoseStack mul(Mat4 transform) {
        return apply(transform);
    }

    private PoseStack apply(Mat4 transform) {
        Mat4 top = stack.pop();
        stack.push(top.multiply(transform));
        return this;
    }
}
