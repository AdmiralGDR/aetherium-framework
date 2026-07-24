// aetherium-gfx — loader-agnostic rendering / model-registration abstraction.
// Depends ONLY on the zero-external-dependency core leaf (for the structured Diagnostic/AetheriumException
// error model used to reject duplicate renderer/model keys). MUST NOT import net.minecraft / net.neoforged
// (no Blaze3D types). The loader adapts AetheriumRenderContext over PoseStack + VertexConsumer and bridges
// RenderRegistry to EntityRenderersEvent.
dependencies {
    api(project(":aetherium-core"))
    testImplementation(libs.junit.jupiter)
}
