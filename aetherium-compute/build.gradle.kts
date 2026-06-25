// aetherium-compute — runtime Java→SPIR-V compiler (pure-Java kernels → Vulkan compute binaries).
//
// EN: Takes a pure-Java method annotated @AetheriumComputeShader, analyses its bytecode with ASM
//     (the supported strict subset: primitives, arrays, loops, basic math — no object allocation), and
//     emits a structurally valid Vulkan SPIR-V binary (magic 0x07230203). The binary is handed to the
//     existing aetherium-native Vulkan bridge for dispatch (GPU when a device exists, CPU fallback
//     otherwise). Depends on aetherium-bytecode for the ASM surface and aetherium-native for the bridge.
// RU: Берёт чистый Java-метод с аннотацией @AetheriumComputeShader, анализирует его байт-код через ASM
//     (поддерживается строгое подмножество: примитивы, массивы, циклы, базовая математика — без
//     аллокации объектов) и выпускает структурно валидный бинарь Vulkan SPIR-V (магия 0x07230203).
//     Бинарь передаётся в существующий Vulkan-мост aetherium-native для диспетчеризации (GPU при
//     наличии устройства, иначе CPU-fallback).
dependencies {
    api(project(":aetherium-core"))            // StructArena / ComputePipeline contracts
    implementation(project(":aetherium-native")) // Vulkan bridge pass-through (VulkanProbe / dispatch)
    implementation(libs.bundles.asm)            // bytecode analysis of the annotated kernel method

    testImplementation(libs.junit.jupiter)
}
