/*
 * Aetherium Framework — a bone hierarchy with global-transform resolution (animation hook).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.gfx;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A hierarchy of {@link Bone}s that resolves each bone's global (model-space) transform.
 *
 * <p>EN: The data structure a skeletal animation engine poses each frame. {@link #computeGlobalTransforms()}
 * walks the bones (parents first) chaining {@code parentGlobal × boneLocal} — the standard forward-kinematics
 * pass — so a model can then emit geometry per bone via {@link Geometry#emitCuboid}. Pure; runs and is
 * tested with no game present. Bones must be listed parents-before-children.
 * RU: Структура данных, которую движок скелетной анимации позиционирует каждый кадр.
 * {@link #computeGlobalTransforms()} обходит кости (родители первыми), сцепляя {@code parentGlobal ×
 * boneLocal} — стандартный проход прямой кинематики. Кости перечисляются «родители раньше детей».
 */
public final class Skeleton {

    private final List<Bone> bones;

    public Skeleton(List<Bone> bones) {
        this.bones = List.copyOf(bones);
    }

    public List<Bone> bones() {
        return bones;
    }

    /**
     * EN: Resolve every bone's global transform via forward kinematics.
     * RU: Разрешить глобальную трансформацию каждой кости прямой кинематикой.
     *
     * @return an ordered map {@code boneName → global Mat4}
     * @throws IllegalStateException if a bone references a parent not yet resolved (bad ordering/cycle)
     */
    public Map<String, Mat4> computeGlobalTransforms() {
        Map<String, Mat4> global = new LinkedHashMap<>(bones.size() * 2);
        for (Bone bone : bones) {
            Mat4 parentGlobal;
            if (bone.parent() == null) {
                parentGlobal = Mat4.identity();
            } else {
                parentGlobal = global.get(bone.parent());
                if (parentGlobal == null) {
                    throw new IllegalStateException("bone '" + bone.name() + "' references unresolved parent '"
                            + bone.parent() + "' (list parents before children)");
                }
            }
            global.put(bone.name(), parentGlobal.multiply(bone.localMatrix()));
        }
        return global;
    }
}
