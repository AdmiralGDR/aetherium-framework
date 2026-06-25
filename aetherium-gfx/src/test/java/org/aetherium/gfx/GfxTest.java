/*
 * Aetherium Framework — advanced GFX tests.
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.gfx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GfxTest {

    @Test
    void gfxSelfTestPasses() {
        GfxSelfTest.Result r = GfxSelfTest.run();
        assertTrue(r.passed(), () -> "gfx self-test failed: " + r.notes());
        assertTrue(r.matrixOk());
        assertTrue(r.poseOk());
        assertTrue(r.skeletonOk());
        assertTrue(r.meshOk());
    }

    @Test
    void matrixMultiplyComposesTransforms() {
        // Scale by 2 then translate by 3 on X: a point at x=1 → (1*2)+3 = 5 when T·S applied.
        Mat4 ts = Mat4.translation(3, 0, 0).multiply(Mat4.scaling(2, 2, 2));
        Vec3 p = ts.transformPoint(1, 0, 0);
        assertEquals(5f, p.x(), 1e-5f);
    }

    @Test
    void poseStackUnderflowIsRejected() {
        PoseStack pose = new PoseStack();
        try {
            pose.popPose(); // only the identity base is present
            org.junit.jupiter.api.Assertions.fail("expected underflow");
        } catch (IllegalStateException expected) {
            // ok
        }
    }
}
