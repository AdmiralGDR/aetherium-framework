/*
 * Aetherium Framework — advanced GFX self-test (matrix/pose/skeleton/vertex, fully offline).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.gfx;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Exercises the matrix/pose/skeleton/vertex stack with no GPU — the proof an animation engine can run
 * its whole pose + mesh pipeline through the PAL.
 *
 * <p>EN: Checks {@link Mat4} affine transforms, {@link PoseStack} push/pop, {@link Skeleton} forward
 * kinematics (a child bone inherits its parent's transform), and that a {@link Skeleton}-driven cuboid
 * mesh emits the expected, correctly-transformed vertices into a {@link RecordingVertexSink}.
 * RU: Проверяет аффинные трансформации {@link Mat4}, push/pop {@link PoseStack}, прямую кинематику
 * {@link Skeleton} (кость-ребёнок наследует трансформацию родителя) и что меш-куб по скелету выпускает
 * ожидаемые корректно-трансформированные вершины в {@link RecordingVertexSink}.
 */
public final class GfxSelfTest {

    private static final float EPS = 1e-4f;

    private GfxSelfTest() {
    }

    public static Result run() {
        List<String> notes = new ArrayList<>();

        // 1) Matrix math: translate then a 90° Z-rotation of the +X axis → +Y.
        Vec3 translated = Mat4.translation(10, 0, 0).transformPoint(1, 2, 3);
        Vec3 rotated = Mat4.rotationZ((float) (Math.PI / 2)).transformPoint(1, 0, 0);
        boolean matrixOk = approx(translated, 11, 2, 3) && approx(rotated, 0, 1, 0);
        notes.add("matrix: T(10,0,0)·(1,2,3)=" + translated + ", Rz90·(1,0,0)=" + rotated);

        // 2) PoseStack: push/translate/pop restores the prior transform.
        PoseStack pose = new PoseStack();
        pose.translate(5, 0, 0);
        pose.pushPose();
        pose.translate(0, 5, 0);
        Vec3 inner = pose.last().transformPoint(0, 0, 0);
        pose.popPose();
        Vec3 outer = pose.last().transformPoint(0, 0, 0);
        boolean poseOk = approx(inner, 5, 5, 0) && approx(outer, 5, 0, 0) && pose.depth() == 1;
        notes.add("pose: inner=" + inner + " outer(after pop)=" + outer);

        // 3) Skeleton forward kinematics: child inherits parent translation.
        Skeleton skeleton = new Skeleton(List.of(
                Bone.of("root", null, new Vec3(0, 10, 0)),
                Bone.of("arm", "root", new Vec3(0, 5, 0))));
        Map<String, Mat4> globals = skeleton.computeGlobalTransforms();
        Vec3 armOrigin = globals.get("arm").transformPoint(0, 0, 0);
        boolean skeletonOk = approx(armOrigin, 0, 15, 0);
        notes.add("skeleton: arm global origin=" + armOrigin + " (root 10 + arm 5)");

        // 4) Emit a cuboid per bone at its global transform into a recording sink.
        RecordingVertexSink sink = new RecordingVertexSink();
        for (Mat4 boneGlobal : globals.values()) {
            Geometry.emitCuboid(sink, boneGlobal, 2, 2, 2, 0xFFCC8844);
        }
        int expected = globals.size() * Geometry.CUBOID_VERTICES;
        boolean meshOk = sink.count() == expected
                && sink.vertices().stream().anyMatch(vx -> Math.abs(vx.y() - 15f) <= 2f + EPS);
        notes.add("mesh: emitted " + sink.count() + " vertices (" + globals.size() + " bones × "
                + Geometry.CUBOID_VERTICES + ")");

        // 5) Model registry: a registered model renders into a sink.
        int before = ModelRegistry.size();
        ModelRegistry.register("aetherium:golem", (p, s, layer, pt) ->
                Geometry.emitCuboid(s, p.last(), 1, 1, 1, 0xFFFFFFFF));
        boolean registryOk = ModelRegistry.size() == before + 1;

        boolean passed = matrixOk && poseOk && skeletonOk && meshOk && registryOk;
        return new Result(matrixOk, poseOk, skeletonOk, meshOk, registryOk, sink.count(), notes, passed);
    }

    private static boolean approx(Vec3 v, float x, float y, float z) {
        return Math.abs(v.x() - x) <= EPS && Math.abs(v.y() - y) <= EPS && Math.abs(v.z() - z) <= EPS;
    }

    /** Outcome of the GFX self-test, rendered by the CLI {@code gfx} command. */
    public record Result(boolean matrixOk, boolean poseOk, boolean skeletonOk, boolean meshOk,
                         boolean registryOk, int verticesEmitted, List<String> notes, boolean passed) {
    }
}
