# Графика — абстракция рендеринга (`aetherium-gfx`)

*Русский. Английский оригинал: [`../en/gfx.md`](../../en/explanation/gfx.md). Лицензия: AGPL-3.0-or-later.*

Загрузчик-агностичный SPI рендеринга. Содержит **ноль** типов `net.minecraft`/`net.neoforged`/Blaze3D;
загрузчик (`AetheriumRenderBridge`) адаптирует его на `PoseStack` + `MultiBufferSource` и регистрирует
через `EntityRenderersEvent.RegisterRenderers` NeoForge.

## 1. Поверхность API

| Тип | Роль |
|---|---|
| `AetheriumRenderContext` | Фасад рисования без Blaze3D: `pushPose`/`popPose`, `translate`, `scale`, `setColor(rgba)`, `drawCuboid(sx,sy,sz)`. |
| `AetheriumEntityRenderer` | `render(AetheriumRenderContext, float partialTick)` + `double shadowRadius()`. Поза уже сдвинута к интерполированной позиции сущности. |
| `RenderRegistry` | `register(entityTypeKey, renderer)` + `entries()` — чистые данные, которые мостит загрузчик. |

## 2. Как работает мост (сторона загрузчика)

`AetheriumRenderBridge` (регистрируется только на клиенте) разрешает каждый `entityTypeKey`
(`"namespace:path"`) в его `EntityType` и регистрирует тонкий `EntityRenderer`, чей `render` строит
`PoseStackRenderContext` (оборачивая реальные `PoseStack` + `MultiBufferSource`) и делегирует
`AetheriumEntityRenderer` мода. `drawCuboid` рисует линейный бокс через `LevelRenderer.renderLineBox`.
Мод не импортирует ни одного клиентского типа.

## 3. Использование

```java
RenderRegistry.register("minecraft:armor_stand", (ctx, partialTick) -> {
    ctx.pushPose();
    ctx.setColor(0.2f, 0.8f, 1.0f, 1.0f);
    ctx.drawCuboid(0.6, 1.8, 0.6);
    ctx.popPose();
});
```

Регистрация — в `onInitialize` мода (загрузчик-агностично); загрузчик связывает её с Blaze3D на нужной
фазе жизненного цикла только на клиенте.

## Фаза 17 — продвинутый рендеринг (матрица / поза / вершины / скелет)

Помимо фасада-куба `AetheriumRenderContext`, модуль теперь предоставляет сырые блоки, нужные движку
анимации (в духе GeckoLib), независимо от загрузчика: `Mat4` (чистая аффинная математика 4×4),
`PoseStack` (стек трансформаций push/pop), `VertexSink` (зеркало `VertexConsumer` —
`vertex().color().uv().normal().endVertex()`), `RenderLayer` (перечисление `RenderType`) и
`Geometry.emitCuboid`. Скелетная анимация — через `Bone`/`Skeleton`: `Skeleton.computeGlobalTransforms()`
выполняет прямую кинематику (`parentGlobal × boneLocal`). Модель привязывается через `ModelRegistry`.
Загрузчик адаптирует `VertexSink`/`PoseStack` поверх Blaze3D. Доказательство: `aetherium gfx`.
