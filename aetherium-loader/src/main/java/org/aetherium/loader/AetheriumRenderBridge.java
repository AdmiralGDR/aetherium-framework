/*
 * Aetherium Framework — render bridge (SPI → NeoForge EntityRenderersEvent / Blaze3D).
 * Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
 * See <https://www.gnu.org/licenses/>.
 */
package org.aetherium.loader;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.aetherium.gfx.AetheriumEntityRenderer;
import org.aetherium.gfx.AetheriumRenderContext;
import org.aetherium.gfx.RenderRegistry;

/**
 * Bridges the pure {@link RenderRegistry} to NeoForge's {@code EntityRenderersEvent.RegisterRenderers}.
 *
 * <p>EN: For each registered renderer it resolves the entity type by id and registers a thin
 * {@link EntityRenderer} that delegates to the mod's {@link AetheriumEntityRenderer}, passing a
 * {@link PoseStackRenderContext} that adapts the loader-agnostic {@link AetheriumRenderContext} onto a
 * real Blaze3D {@code PoseStack} + {@code MultiBufferSource}. This class is the <em>only</em> place
 * client/Minecraft render types are touched; it is registered (in the entrypoint) only on the client
 * dist, so the server never loads these classes.
 *
 * <p>RU: Для каждого зарегистрированного рендера разрешает тип сущности по id и регистрирует тонкий
 * {@link EntityRenderer}, делегирующий {@link AetheriumEntityRenderer} через
 * {@link PoseStackRenderContext}, адаптирующий {@link AetheriumRenderContext} на реальные
 * {@code PoseStack} + {@code MultiBufferSource}. Это единственное место с клиентскими типами рендера;
 * регистрируется только на клиенте.
 */
public final class AetheriumRenderBridge {

    private AetheriumRenderBridge() {}

    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        for (RenderRegistry.Entry entry : RenderRegistry.entries()) {
            bind(event, entry);
        }
    }

    @SuppressWarnings("unchecked")
    private static void bind(EntityRenderersEvent.RegisterRenderers event, RenderRegistry.Entry entry) {
        final ResourceLocation id = ResourceLocation.parse(entry.entityTypeKey());
        final EntityType<?> raw = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        if (raw == null) {
            return;
        }
        final EntityType<Entity> entityType = (EntityType<Entity>) raw;
        final AetheriumEntityRenderer delegate = entry.renderer();
        event.registerEntityRenderer(entityType, context -> new BridgeRenderer(context, delegate));
    }

    /** Minimal {@link EntityRenderer} that forwards to a loader-agnostic {@link AetheriumEntityRenderer}. */
    private static final class BridgeRenderer extends EntityRenderer<Entity> {

        private final AetheriumEntityRenderer delegate;

        BridgeRenderer(EntityRendererProvider.Context context, AetheriumEntityRenderer delegate) {
            super(context);
            this.delegate = delegate;
            this.shadowRadius = (float) delegate.shadowRadius();
        }

        @Override
        public void render(Entity entity, float entityYaw, float partialTick, PoseStack pose,
                           MultiBufferSource buffers, int packedLight) {
            pose.pushPose();
            delegate.render(new PoseStackRenderContext(pose, buffers), partialTick);
            pose.popPose();
            super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
        }

        @Override
        public ResourceLocation getTextureLocation(Entity entity) {
            return ResourceLocation.withDefaultNamespace("textures/misc/white.png");
        }
    }

    /** Adapts {@link AetheriumRenderContext} onto Blaze3D's {@link PoseStack} + line buffer. */
    private static final class PoseStackRenderContext implements AetheriumRenderContext {

        private final PoseStack pose;
        private final MultiBufferSource buffers;
        private float r = 1.0f;
        private float g = 1.0f;
        private float b = 1.0f;
        private float a = 1.0f;

        PoseStackRenderContext(PoseStack pose, MultiBufferSource buffers) {
            this.pose = pose;
            this.buffers = buffers;
        }

        @Override
        public void pushPose() {
            pose.pushPose();
        }

        @Override
        public void popPose() {
            pose.popPose();
        }

        @Override
        public void translate(double x, double y, double z) {
            pose.translate(x, y, z);
        }

        @Override
        public void scale(float x, float y, float z) {
            pose.scale(x, y, z);
        }

        @Override
        public void setColor(float red, float green, float blue, float alpha) {
            this.r = red;
            this.g = green;
            this.b = blue;
            this.a = alpha;
        }

        @Override
        public void drawCuboid(double sizeX, double sizeY, double sizeZ) {
            final VertexConsumer lines = buffers.getBuffer(RenderType.lines());
            final double hx = sizeX / 2.0;
            final double hy = sizeY / 2.0;
            final double hz = sizeZ / 2.0;
            LevelRenderer.renderLineBox(pose, lines, -hx, -hy, -hz, hx, hy, hz, r, g, b, a);
        }
    }
}
