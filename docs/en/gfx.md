# Graphics — Render Abstraction (`aetherium-gfx`)

*English. Russian mirror: [`../ru/gfx.md`](../ru/gfx.md). License: AGPL-3.0-or-later.*

A loader-agnostic rendering SPI. It contains **zero** `net.minecraft`/`net.neoforged`/Blaze3D types;
the loader (`AetheriumRenderBridge`) adapts it onto `PoseStack` + `MultiBufferSource` and registers it
through NeoForge's `EntityRenderersEvent.RegisterRenderers`.

## 1. API surface

| Type | Role |
|---|---|
| `AetheriumRenderContext` | Blaze3D-free drawing facade: `pushPose`/`popPose`, `translate`, `scale`, `setColor(rgba)`, `drawCuboid(sx,sy,sz)`. |
| `AetheriumEntityRenderer` | `render(AetheriumRenderContext, float partialTick)` + `double shadowRadius()`. The pose is pre-translated to the entity's interpolated position. |
| `RenderRegistry` | `register(entityTypeKey, renderer)` + `entries()` — pure data the loader bridges. |

## 2. How the bridge works (loader side)

`AetheriumRenderBridge` (registered only on the client dist) resolves each `entityTypeKey`
(`"namespace:path"`) to its `EntityType` and registers a thin `EntityRenderer` whose `render` builds a
`PoseStackRenderContext` (wrapping the real `PoseStack` + `MultiBufferSource`) and forwards to the mod's
`AetheriumEntityRenderer`. `drawCuboid` emits a line box via `LevelRenderer.renderLineBox`. The mod
never imports a client type.

## 3. Usage

```java
RenderRegistry.register("minecraft:armor_stand", (ctx, partialTick) -> {
    ctx.pushPose();
    ctx.setColor(0.2f, 0.8f, 1.0f, 1.0f);
    ctx.drawCuboid(0.6, 1.8, 0.6);
    ctx.popPose();
});
```

Registration happens during the mod's `onInitialize` (loader-agnostic); the loader fans it out to
Blaze3D at the right lifecycle phase on the client only.
