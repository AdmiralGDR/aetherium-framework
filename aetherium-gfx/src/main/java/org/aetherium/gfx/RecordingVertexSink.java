/*
 * Aetherium Framework — a VertexSink that records vertices (offline rendering / tests).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.gfx;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link VertexSink} that captures every committed vertex — so model/animation code can be exercised
 * with no GPU present (the self-test and golden-mesh checks use it).
 */
public final class RecordingVertexSink implements VertexSink {

    /** One committed vertex. */
    public record V(float x, float y, float z, int argb, float u, float v, float nx, float ny, float nz) {
    }

    private final List<V> vertices = new ArrayList<>();
    private float x;
    private float y;
    private float z;
    private int argb = 0xFFFFFFFF;
    private float u;
    private float v;
    private float nx;
    private float ny;
    private float nz;

    @Override
    public VertexSink vertex(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    @Override
    public VertexSink color(int argb) {
        this.argb = argb;
        return this;
    }

    @Override
    public VertexSink uv(float u, float v) {
        this.u = u;
        this.v = v;
        return this;
    }

    @Override
    public VertexSink normal(float nx, float ny, float nz) {
        this.nx = nx;
        this.ny = ny;
        this.nz = nz;
        return this;
    }

    @Override
    public void endVertex() {
        vertices.add(new V(x, y, z, argb, u, v, nx, ny, nz));
    }

    public List<V> vertices() {
        return List.copyOf(vertices);
    }

    public int count() {
        return vertices.size();
    }
}
