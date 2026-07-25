// Aetherium Framework — native bridge core (Zig, sovereign C ABI).
// Copyright (C) 2026 RedstoneTeam. Licensed under AGPL-3.0-or-later.
// See <https://www.gnu.org/licenses/>.
//
// EN: A deliberately NARROW, versioned C ABI (a flat function table) so the Java FFM bindings target stable
//     symbols. Rewritten from C++ to Zig in to delete the C++/CMake build dependency and unify the
//     framework's native surface on one toolchain (independence / Dependency Quarantine). The library links
//     only libc (for dlopen); Vulkan is reached via dlopen AT CALL TIME, so this .so LOADS everywhere and
//     graceful degradation happens in Java, not by failing to load. Same symbols/ABI as the old C++ version
//     (aeth_native_abi_version / aeth_self_test / aeth_sum_bytes / aeth_vk_probe), so the FFM side is unchanged.
// RU: Намеренно УЗКИЙ версионированный C ABI. Переписан с C++ на Zig в Фазе 23, чтобы убрать зависимость от
//     C++/CMake и унифицировать нативную поверхность на одном тулчейне. Линкует только libc (для dlopen);
//     Vulkan достигается через dlopen во время вызова, поэтому .so загружается везде. Символы/ABI те же.

const std = @import("std");
const c = std.c;

const AETH_ABI_VERSION: i32 = 1;
const AETH_VK_OK: i32 = 0;
const AETH_VK_UNAVAILABLE: i32 = -1;
const AETH_VK_BAD_ARGS: i32 = -2;

/// Bumped on any incompatible change to the function table; the Java side refuses a mismatch.
export fn aeth_native_abi_version() i32 {
    return AETH_ABI_VERSION;
}

/// Sanity self-test — mirrors the Java dispatch "doubler" so the Pre-Flight Check can cross-verify a downcall.
export fn aeth_self_test(input: i32) i64 {
    return @as(i64, input) * 2;
}

/// Sum the bytes of an Arena-owned buffer that crossed the FFM boundary (proves memory ownership handoff).
export fn aeth_sum_bytes(data: ?[*]const u8, len: usize) i64 {
    const p = data orelse return AETH_VK_BAD_ARGS;
    var sum: i64 = 0;
    var i: usize = 0;
    while (i < len) : (i += 1) sum += @as(i64, p[i]);
    return sum;
}

/// Flat result struct: 4 x int32 = 16 bytes. Mirrored by a Java MemoryLayout.
const AethVkInfo = extern struct {
    available: i32,
    device_count: i32,
    queue_family_count: i32,
    api_version: i32,
};

// --- minimal Vulkan ABI (declared here; no vulkan.h dependency) ---------------------------------
const VkResult = i32;
const VkInstance = ?*anyopaque;
const VkPhysicalDevice = ?*anyopaque;
const VK_STRUCTURE_TYPE_APPLICATION_INFO: i32 = 0;
const VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO: i32 = 1;
const VK_API_VERSION_1_0: u32 = (1 << 22);

const VkApplicationInfo = extern struct {
    sType: i32,
    pNext: ?*const anyopaque = null,
    pApplicationName: ?[*:0]const u8,
    applicationVersion: u32,
    pEngineName: ?[*:0]const u8,
    engineVersion: u32,
    apiVersion: u32,
};

const VkInstanceCreateInfo = extern struct {
    sType: i32,
    pNext: ?*const anyopaque = null,
    flags: u32 = 0,
    pApplicationInfo: ?*const VkApplicationInfo,
    enabledLayerCount: u32 = 0,
    ppEnabledLayerNames: ?[*]const [*:0]const u8 = null,
    enabledExtensionCount: u32 = 0,
    ppEnabledExtensionNames: ?[*]const [*:0]const u8 = null,
};

const PFN_vkCreateInstance = *const fn (*const VkInstanceCreateInfo, ?*const anyopaque, *VkInstance) callconv(.c) VkResult;
const PFN_vkEnumeratePhysicalDevices = *const fn (VkInstance, *u32, ?[*]VkPhysicalDevice) callconv(.c) VkResult;
const PFN_vkGetPhysicalDeviceQueueFamilyProperties = *const fn (VkPhysicalDevice, *u32, ?*anyopaque) callconv(.c) void;
const PFN_vkDestroyInstance = *const fn (VkInstance, ?*const anyopaque) callconv(.c) void;

/// The reliable HARDWARE ACCESS layer — NOT shader logic. Loads libvulkan via dlopen, creates a transient
/// instance, enumerates physical devices and the queue families of device 0, then tears everything down.
/// Returns AETH_VK_UNAVAILABLE on any absence/failure so the caller degrades to a CPU/pure-Java path.
export fn aeth_vk_probe(out_opt: ?*AethVkInfo) i32 {
    const out = out_opt orelse return AETH_VK_BAD_ARGS;
    out.* = .{ .available = 0, .device_count = 0, .queue_family_count = 0, .api_version = 0 };

    var lib = c.dlopen("libvulkan.so.1", .{ .NOW = true });
    if (lib == null) lib = c.dlopen("libvulkan.so", .{ .NOW = true });
    const handle = lib orelse return AETH_VK_UNAVAILABLE;
    defer _ = c.dlclose(handle);

    const create_instance: PFN_vkCreateInstance = @ptrCast(c.dlsym(handle, "vkCreateInstance") orelse return AETH_VK_UNAVAILABLE);
    const enumerate: PFN_vkEnumeratePhysicalDevices = @ptrCast(c.dlsym(handle, "vkEnumeratePhysicalDevices") orelse return AETH_VK_UNAVAILABLE);
    const qf_props: PFN_vkGetPhysicalDeviceQueueFamilyProperties = @ptrCast(c.dlsym(handle, "vkGetPhysicalDeviceQueueFamilyProperties") orelse return AETH_VK_UNAVAILABLE);
    const destroy: ?PFN_vkDestroyInstance = @ptrCast(c.dlsym(handle, "vkDestroyInstance"));

    var app = VkApplicationInfo{
        .sType = VK_STRUCTURE_TYPE_APPLICATION_INFO,
        .pApplicationName = "AetheriumPreFlight",
        .applicationVersion = (1 << 12),
        .pEngineName = "Aetherium",
        .engineVersion = (1 << 12),
        .apiVersion = VK_API_VERSION_1_0,
    };
    out.api_version = @intCast(app.apiVersion);

    const ci = VkInstanceCreateInfo{
        .sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO,
        .pApplicationInfo = &app,
    };

    var instance: VkInstance = null;
    if (create_instance(&ci, null, &instance) != 0) return AETH_VK_UNAVAILABLE;
    out.available = 1;

    var device_count: u32 = 0;
    _ = enumerate(instance, &device_count, null);
    out.device_count = @intCast(device_count);

    if (device_count > 0) {
        var capped: u32 = if (device_count > 16) 16 else device_count;
        var devices: [16]VkPhysicalDevice = undefined;
        _ = enumerate(instance, &capped, &devices);
        var qfc: u32 = 0;
        qf_props(devices[0], &qfc, null);
        out.queue_family_count = @intCast(qfc);
    }

    if (destroy) |d| d(instance, null);
    return AETH_VK_OK;
}

// --- Real GPU compute dispatch (WS-6) --------------------------------------------------
// Runs a compiled unary SPIR-V kernel (two std430 SSBOs: binding 0 = input, binding 1 = output, set 0,
// indexed by gl_GlobalInvocationID.x) on a real Vulkan compute queue and reads the result back. Pure Zig
// over libvulkan reached by dlopen — NO vulkan.h, NO build/link dependency (MANIFEST: dependency-free).
// Any failure returns a negative code so the Java side degrades to the CPU/SIMD path. Handles: dispatchable
// (instance/device/queue/cmdbuf) are pointers; non-dispatchable (buffer/memory/module/pipeline/...) are u64.

const VkDevice = ?*anyopaque;
const VkQueue = ?*anyopaque;
const VkCommandBuffer = ?*anyopaque;
const VkBuffer = u64;
const VkDeviceMemory = u64;
const VkShaderModule = u64;
const VkDescriptorSetLayout = u64;
const VkPipelineLayout = u64;
const VkPipeline = u64;
const VkDescriptorPool = u64;
const VkDescriptorSet = u64;
const VkCommandPool = u64;
const VkDeviceSize = u64;

const VK_QUEUE_COMPUTE_BIT: u32 = 0x2;
const VK_DESCRIPTOR_TYPE_STORAGE_BUFFER: i32 = 7;
const VK_SHADER_STAGE_COMPUTE_BIT: u32 = 0x20;
const VK_BUFFER_USAGE_STORAGE_BUFFER_BIT: u32 = 0x20;
const VK_MEMORY_HOST_VISIBLE: u32 = 0x2;
const VK_MEMORY_HOST_COHERENT: u32 = 0x4;
const VK_COMMAND_BUFFER_USAGE_ONE_TIME: u32 = 0x1;
const VK_WHOLE_SIZE: u64 = ~@as(u64, 0);

const ST_DEVICE_QUEUE_CREATE_INFO: i32 = 2;
const ST_DEVICE_CREATE_INFO: i32 = 3;
const ST_SUBMIT_INFO: i32 = 4;
const ST_MEMORY_ALLOCATE_INFO: i32 = 5;
const ST_BUFFER_CREATE_INFO: i32 = 12;
const ST_SHADER_MODULE_CREATE_INFO: i32 = 16;
const ST_PIPELINE_SHADER_STAGE_CREATE_INFO: i32 = 18;
const ST_COMPUTE_PIPELINE_CREATE_INFO: i32 = 29;
const ST_PIPELINE_LAYOUT_CREATE_INFO: i32 = 30;
const ST_DESCRIPTOR_SET_LAYOUT_CREATE_INFO: i32 = 32;
const ST_DESCRIPTOR_POOL_CREATE_INFO: i32 = 33;
const ST_DESCRIPTOR_SET_ALLOCATE_INFO: i32 = 34;
const ST_WRITE_DESCRIPTOR_SET: i32 = 35;
const ST_COMMAND_POOL_CREATE_INFO: i32 = 39;
const ST_COMMAND_BUFFER_ALLOCATE_INFO: i32 = 40;
const ST_COMMAND_BUFFER_BEGIN_INFO: i32 = 42;

const VkExtent3D = extern struct { width: u32, height: u32, depth: u32 };
const VkQueueFamilyProperties = extern struct {
    queueFlags: u32,
    queueCount: u32,
    timestampValidBits: u32,
    minImageTransferGranularity: VkExtent3D,
};
const VkMemoryType = extern struct { propertyFlags: u32, heapIndex: u32 };
const VkMemoryHeap = extern struct { size: VkDeviceSize, flags: u32 };
const VkPhysicalDeviceMemoryProperties = extern struct {
    memoryTypeCount: u32,
    memoryTypes: [32]VkMemoryType,
    memoryHeapCount: u32,
    memoryHeaps: [16]VkMemoryHeap,
};
const VkDeviceQueueCreateInfo = extern struct {
    sType: i32,
    pNext: ?*const anyopaque = null,
    flags: u32 = 0,
    queueFamilyIndex: u32,
    queueCount: u32,
    pQueuePriorities: ?[*]const f32,
};
const VkDeviceCreateInfo = extern struct {
    sType: i32,
    pNext: ?*const anyopaque = null,
    flags: u32 = 0,
    queueCreateInfoCount: u32,
    pQueueCreateInfos: ?[*]const VkDeviceQueueCreateInfo,
    enabledLayerCount: u32 = 0,
    ppEnabledLayerNames: ?*const anyopaque = null,
    enabledExtensionCount: u32 = 0,
    ppEnabledExtensionNames: ?*const anyopaque = null,
    pEnabledFeatures: ?*const anyopaque = null,
};
const VkBufferCreateInfo = extern struct {
    sType: i32,
    pNext: ?*const anyopaque = null,
    flags: u32 = 0,
    size: VkDeviceSize,
    usage: u32,
    sharingMode: i32 = 0,
    queueFamilyIndexCount: u32 = 0,
    pQueueFamilyIndices: ?[*]const u32 = null,
};
const VkMemoryRequirements = extern struct { size: VkDeviceSize, alignment: VkDeviceSize, memoryTypeBits: u32 };
const VkMemoryAllocateInfo = extern struct {
    sType: i32,
    pNext: ?*const anyopaque = null,
    allocationSize: VkDeviceSize,
    memoryTypeIndex: u32,
};
const VkShaderModuleCreateInfo = extern struct {
    sType: i32,
    pNext: ?*const anyopaque = null,
    flags: u32 = 0,
    codeSize: usize,
    pCode: ?[*]const u32,
};
const VkDescriptorSetLayoutBinding = extern struct {
    binding: u32,
    descriptorType: i32,
    descriptorCount: u32,
    stageFlags: u32,
    pImmutableSamplers: ?*const anyopaque = null,
};
const VkDescriptorSetLayoutCreateInfo = extern struct {
    sType: i32,
    pNext: ?*const anyopaque = null,
    flags: u32 = 0,
    bindingCount: u32,
    pBindings: ?[*]const VkDescriptorSetLayoutBinding,
};
const VkPipelineLayoutCreateInfo = extern struct {
    sType: i32,
    pNext: ?*const anyopaque = null,
    flags: u32 = 0,
    setLayoutCount: u32,
    pSetLayouts: ?[*]const VkDescriptorSetLayout,
    pushConstantRangeCount: u32 = 0,
    pPushConstantRanges: ?*const anyopaque = null,
};
const VkPipelineShaderStageCreateInfo = extern struct {
    sType: i32,
    pNext: ?*const anyopaque = null,
    flags: u32 = 0,
    stage: u32,
    module: VkShaderModule,
    pName: ?[*:0]const u8,
    pSpecializationInfo: ?*const anyopaque = null,
};
const VkComputePipelineCreateInfo = extern struct {
    sType: i32,
    pNext: ?*const anyopaque = null,
    flags: u32 = 0,
    stage: VkPipelineShaderStageCreateInfo,
    layout: VkPipelineLayout,
    basePipelineHandle: VkPipeline = 0,
    basePipelineIndex: i32 = 0,
};
const VkDescriptorPoolSize = extern struct { type: i32, descriptorCount: u32 };
const VkDescriptorPoolCreateInfo = extern struct {
    sType: i32,
    pNext: ?*const anyopaque = null,
    flags: u32 = 0,
    maxSets: u32,
    poolSizeCount: u32,
    pPoolSizes: ?[*]const VkDescriptorPoolSize,
};
const VkDescriptorSetAllocateInfo = extern struct {
    sType: i32,
    pNext: ?*const anyopaque = null,
    descriptorPool: VkDescriptorPool,
    descriptorSetCount: u32,
    pSetLayouts: ?[*]const VkDescriptorSetLayout,
};
const VkDescriptorBufferInfo = extern struct { buffer: VkBuffer, offset: VkDeviceSize, range: VkDeviceSize };
const VkWriteDescriptorSet = extern struct {
    sType: i32,
    pNext: ?*const anyopaque = null,
    dstSet: VkDescriptorSet,
    dstBinding: u32,
    dstArrayElement: u32 = 0,
    descriptorCount: u32,
    descriptorType: i32,
    pImageInfo: ?*const anyopaque = null,
    pBufferInfo: ?[*]const VkDescriptorBufferInfo,
    pTexelBufferView: ?*const anyopaque = null,
};
const VkCommandPoolCreateInfo = extern struct {
    sType: i32,
    pNext: ?*const anyopaque = null,
    flags: u32 = 0,
    queueFamilyIndex: u32,
};
const VkCommandBufferAllocateInfo = extern struct {
    sType: i32,
    pNext: ?*const anyopaque = null,
    commandPool: VkCommandPool,
    level: i32 = 0,
    commandBufferCount: u32,
};
const VkCommandBufferBeginInfo = extern struct {
    sType: i32,
    pNext: ?*const anyopaque = null,
    flags: u32 = 0,
    pInheritanceInfo: ?*const anyopaque = null,
};
const VkSubmitInfo = extern struct {
    sType: i32,
    pNext: ?*const anyopaque = null,
    waitSemaphoreCount: u32 = 0,
    pWaitSemaphores: ?*const anyopaque = null,
    pWaitDstStageMask: ?*const anyopaque = null,
    commandBufferCount: u32,
    pCommandBuffers: ?[*]const VkCommandBuffer,
    signalSemaphoreCount: u32 = 0,
    pSignalSemaphores: ?*const anyopaque = null,
};

fn vkSym(handle: *anyopaque, comptime T: type, name: [*:0]const u8) ?T {
    return @ptrCast(c.dlsym(handle, name) orelse return null);
}

/// Dispatch a unary SPIR-V compute kernel; 0 on success, negative on any failure (Java falls back to CPU).
export fn aeth_vk_dispatch(
    spirv_ptr: ?[*]const u8,
    spirv_len: usize,
    in_ptr: ?[*]const f32,
    out_ptr: ?[*]f32,
    elem_count: u32,
    local_size_x: u32,
) i32 {
    const spirv = spirv_ptr orelse return AETH_VK_BAD_ARGS;
    const input = in_ptr orelse return AETH_VK_BAD_ARGS;
    const output = out_ptr orelse return AETH_VK_BAD_ARGS;
    if (elem_count == 0 or (spirv_len % 4) != 0) return AETH_VK_BAD_ARGS;

    var lib = c.dlopen("libvulkan.so.1", .{ .NOW = true });
    if (lib == null) lib = c.dlopen("libvulkan.so", .{ .NOW = true });
    const handle = lib orelse return AETH_VK_UNAVAILABLE;
    defer _ = c.dlclose(handle);

    // Resolve every core function we need up front; a single missing symbol → unavailable.
    const createInstance = vkSym(handle, PFN_vkCreateInstance, "vkCreateInstance") orelse return AETH_VK_UNAVAILABLE;
    const destroyInstance = vkSym(handle, PFN_vkDestroyInstance, "vkDestroyInstance") orelse return AETH_VK_UNAVAILABLE;
    const enumerate = vkSym(handle, PFN_vkEnumeratePhysicalDevices, "vkEnumeratePhysicalDevices") orelse return AETH_VK_UNAVAILABLE;
    const getQFP = vkSym(handle, PFN_vkGetPhysicalDeviceQueueFamilyProperties, "vkGetPhysicalDeviceQueueFamilyProperties") orelse return AETH_VK_UNAVAILABLE;
    const getMemProps = vkSym(handle, *const fn (VkPhysicalDevice, *VkPhysicalDeviceMemoryProperties) callconv(.c) void, "vkGetPhysicalDeviceMemoryProperties") orelse return AETH_VK_UNAVAILABLE;
    const createDevice = vkSym(handle, *const fn (VkPhysicalDevice, *const VkDeviceCreateInfo, ?*const anyopaque, *VkDevice) callconv(.c) VkResult, "vkCreateDevice") orelse return AETH_VK_UNAVAILABLE;
    const destroyDevice = vkSym(handle, *const fn (VkDevice, ?*const anyopaque) callconv(.c) void, "vkDestroyDevice") orelse return AETH_VK_UNAVAILABLE;
    const getDeviceQueue = vkSym(handle, *const fn (VkDevice, u32, u32, *VkQueue) callconv(.c) void, "vkGetDeviceQueue") orelse return AETH_VK_UNAVAILABLE;
    const createBuffer = vkSym(handle, *const fn (VkDevice, *const VkBufferCreateInfo, ?*const anyopaque, *VkBuffer) callconv(.c) VkResult, "vkCreateBuffer") orelse return AETH_VK_UNAVAILABLE;
    const destroyBuffer = vkSym(handle, *const fn (VkDevice, VkBuffer, ?*const anyopaque) callconv(.c) void, "vkDestroyBuffer") orelse return AETH_VK_UNAVAILABLE;
    const getBufReq = vkSym(handle, *const fn (VkDevice, VkBuffer, *VkMemoryRequirements) callconv(.c) void, "vkGetBufferMemoryRequirements") orelse return AETH_VK_UNAVAILABLE;
    const allocMem = vkSym(handle, *const fn (VkDevice, *const VkMemoryAllocateInfo, ?*const anyopaque, *VkDeviceMemory) callconv(.c) VkResult, "vkAllocateMemory") orelse return AETH_VK_UNAVAILABLE;
    const freeMem = vkSym(handle, *const fn (VkDevice, VkDeviceMemory, ?*const anyopaque) callconv(.c) void, "vkFreeMemory") orelse return AETH_VK_UNAVAILABLE;
    const bindBufMem = vkSym(handle, *const fn (VkDevice, VkBuffer, VkDeviceMemory, VkDeviceSize) callconv(.c) VkResult, "vkBindBufferMemory") orelse return AETH_VK_UNAVAILABLE;
    const mapMem = vkSym(handle, *const fn (VkDevice, VkDeviceMemory, VkDeviceSize, VkDeviceSize, u32, *?*anyopaque) callconv(.c) VkResult, "vkMapMemory") orelse return AETH_VK_UNAVAILABLE;
    const unmapMem = vkSym(handle, *const fn (VkDevice, VkDeviceMemory) callconv(.c) void, "vkUnmapMemory") orelse return AETH_VK_UNAVAILABLE;
    const createShader = vkSym(handle, *const fn (VkDevice, *const VkShaderModuleCreateInfo, ?*const anyopaque, *VkShaderModule) callconv(.c) VkResult, "vkCreateShaderModule") orelse return AETH_VK_UNAVAILABLE;
    const destroyShader = vkSym(handle, *const fn (VkDevice, VkShaderModule, ?*const anyopaque) callconv(.c) void, "vkDestroyShaderModule") orelse return AETH_VK_UNAVAILABLE;
    const createDSL = vkSym(handle, *const fn (VkDevice, *const VkDescriptorSetLayoutCreateInfo, ?*const anyopaque, *VkDescriptorSetLayout) callconv(.c) VkResult, "vkCreateDescriptorSetLayout") orelse return AETH_VK_UNAVAILABLE;
    const destroyDSL = vkSym(handle, *const fn (VkDevice, VkDescriptorSetLayout, ?*const anyopaque) callconv(.c) void, "vkDestroyDescriptorSetLayout") orelse return AETH_VK_UNAVAILABLE;
    const createPL = vkSym(handle, *const fn (VkDevice, *const VkPipelineLayoutCreateInfo, ?*const anyopaque, *VkPipelineLayout) callconv(.c) VkResult, "vkCreatePipelineLayout") orelse return AETH_VK_UNAVAILABLE;
    const destroyPL = vkSym(handle, *const fn (VkDevice, VkPipelineLayout, ?*const anyopaque) callconv(.c) void, "vkDestroyPipelineLayout") orelse return AETH_VK_UNAVAILABLE;
    const createCompute = vkSym(handle, *const fn (VkDevice, u64, u32, *const VkComputePipelineCreateInfo, ?*const anyopaque, *VkPipeline) callconv(.c) VkResult, "vkCreateComputePipelines") orelse return AETH_VK_UNAVAILABLE;
    const destroyPipeline = vkSym(handle, *const fn (VkDevice, VkPipeline, ?*const anyopaque) callconv(.c) void, "vkDestroyPipeline") orelse return AETH_VK_UNAVAILABLE;
    const createDescPool = vkSym(handle, *const fn (VkDevice, *const VkDescriptorPoolCreateInfo, ?*const anyopaque, *VkDescriptorPool) callconv(.c) VkResult, "vkCreateDescriptorPool") orelse return AETH_VK_UNAVAILABLE;
    const destroyDescPool = vkSym(handle, *const fn (VkDevice, VkDescriptorPool, ?*const anyopaque) callconv(.c) void, "vkDestroyDescriptorPool") orelse return AETH_VK_UNAVAILABLE;
    const allocDS = vkSym(handle, *const fn (VkDevice, *const VkDescriptorSetAllocateInfo, *VkDescriptorSet) callconv(.c) VkResult, "vkAllocateDescriptorSets") orelse return AETH_VK_UNAVAILABLE;
    const updateDS = vkSym(handle, *const fn (VkDevice, u32, ?[*]const VkWriteDescriptorSet, u32, ?*const anyopaque) callconv(.c) void, "vkUpdateDescriptorSets") orelse return AETH_VK_UNAVAILABLE;
    const createCmdPool = vkSym(handle, *const fn (VkDevice, *const VkCommandPoolCreateInfo, ?*const anyopaque, *VkCommandPool) callconv(.c) VkResult, "vkCreateCommandPool") orelse return AETH_VK_UNAVAILABLE;
    const destroyCmdPool = vkSym(handle, *const fn (VkDevice, VkCommandPool, ?*const anyopaque) callconv(.c) void, "vkDestroyCommandPool") orelse return AETH_VK_UNAVAILABLE;
    const allocCmd = vkSym(handle, *const fn (VkDevice, *const VkCommandBufferAllocateInfo, *VkCommandBuffer) callconv(.c) VkResult, "vkAllocateCommandBuffers") orelse return AETH_VK_UNAVAILABLE;
    const beginCmd = vkSym(handle, *const fn (VkCommandBuffer, *const VkCommandBufferBeginInfo) callconv(.c) VkResult, "vkBeginCommandBuffer") orelse return AETH_VK_UNAVAILABLE;
    const endCmd = vkSym(handle, *const fn (VkCommandBuffer) callconv(.c) VkResult, "vkEndCommandBuffer") orelse return AETH_VK_UNAVAILABLE;
    const cmdBindPipeline = vkSym(handle, *const fn (VkCommandBuffer, i32, VkPipeline) callconv(.c) void, "vkCmdBindPipeline") orelse return AETH_VK_UNAVAILABLE;
    const cmdBindDS = vkSym(handle, *const fn (VkCommandBuffer, i32, VkPipelineLayout, u32, u32, ?[*]const VkDescriptorSet, u32, ?*const anyopaque) callconv(.c) void, "vkCmdBindDescriptorSets") orelse return AETH_VK_UNAVAILABLE;
    const cmdDispatch = vkSym(handle, *const fn (VkCommandBuffer, u32, u32, u32) callconv(.c) void, "vkCmdDispatch") orelse return AETH_VK_UNAVAILABLE;
    const queueSubmit = vkSym(handle, *const fn (VkQueue, u32, ?[*]const VkSubmitInfo, u64) callconv(.c) VkResult, "vkQueueSubmit") orelse return AETH_VK_UNAVAILABLE;
    const queueWaitIdle = vkSym(handle, *const fn (VkQueue) callconv(.c) VkResult, "vkQueueWaitIdle") orelse return AETH_VK_UNAVAILABLE;

    // Instance.
    var app = VkApplicationInfo{
        .sType = VK_STRUCTURE_TYPE_APPLICATION_INFO,
        .pApplicationName = "AetheriumCompute",
        .applicationVersion = (1 << 12),
        .pEngineName = "Aetherium",
        .engineVersion = (1 << 12),
        .apiVersion = VK_API_VERSION_1_0,
    };
    const ici = VkInstanceCreateInfo{ .sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO, .pApplicationInfo = &app };
    var instance: VkInstance = null;
    if (createInstance(&ici, null, &instance) != 0) return AETH_VK_UNAVAILABLE;
    defer destroyInstance(instance, null);

    var device_count: u32 = 0;
    if (enumerate(instance, &device_count, null) != 0 or device_count == 0) return AETH_VK_UNAVAILABLE;
    var phys: [16]VkPhysicalDevice = undefined;
    var capped: u32 = if (device_count > 16) 16 else device_count;
    if (enumerate(instance, &capped, &phys) != 0) return AETH_VK_UNAVAILABLE;
    const gpu = phys[0];

    // Find a compute-capable queue family.
    var qfc: u32 = 0;
    getQFP(gpu, &qfc, null);
    if (qfc == 0) return AETH_VK_UNAVAILABLE;
    var families: [16]VkQueueFamilyProperties = undefined;
    var qcap: u32 = if (qfc > 16) 16 else qfc;
    getQFP(gpu, &qcap, &families);
    var qfi: u32 = 0xFFFFFFFF;
    var fi: u32 = 0;
    while (fi < qcap) : (fi += 1) {
        if ((families[fi].queueFlags & VK_QUEUE_COMPUTE_BIT) != 0) {
            qfi = fi;
            break;
        }
    }
    if (qfi == 0xFFFFFFFF) return AETH_VK_UNAVAILABLE;

    // Logical device + compute queue.
    const priority: f32 = 1.0;
    const qci = VkDeviceQueueCreateInfo{
        .sType = ST_DEVICE_QUEUE_CREATE_INFO,
        .queueFamilyIndex = qfi,
        .queueCount = 1,
        .pQueuePriorities = @ptrCast(&priority),
    };
    const dci = VkDeviceCreateInfo{
        .sType = ST_DEVICE_CREATE_INFO,
        .queueCreateInfoCount = 1,
        .pQueueCreateInfos = @ptrCast(&qci),
    };
    var device: VkDevice = null;
    if (createDevice(gpu, &dci, null, &device) != 0) return AETH_VK_UNAVAILABLE;
    defer destroyDevice(device, null);
    var queue: VkQueue = null;
    getDeviceQueue(device, qfi, 0, &queue);

    // Memory properties (find a host-visible + coherent type).
    var mem_props: VkPhysicalDeviceMemoryProperties = undefined;
    getMemProps(gpu, &mem_props);

    const lsx: u32 = if (local_size_x == 0) 1 else local_size_x;
    const groups: u32 = (elem_count + lsx - 1) / lsx;
    const padded: u32 = groups * lsx;
    const buf_bytes: VkDeviceSize = @as(VkDeviceSize, padded) * 4;

    // Two SSBOs (input a=0, output c=1), each host-visible so we can up/download without staging.
    var bufs: [2]VkBuffer = .{ 0, 0 };
    var mems: [2]VkDeviceMemory = .{ 0, 0 };
    var created: usize = 0;
    defer {
        var i: usize = created;
        while (i > 0) {
            i -= 1;
            if (mems[i] != 0) freeMem(device, mems[i], null);
            if (bufs[i] != 0) destroyBuffer(device, bufs[i], null);
        }
    }
    var b: usize = 0;
    while (b < 2) : (b += 1) {
        const bci = VkBufferCreateInfo{
            .sType = ST_BUFFER_CREATE_INFO,
            .size = buf_bytes,
            .usage = VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
        };
        if (createBuffer(device, &bci, null, &bufs[b]) != 0) return AETH_VK_UNAVAILABLE;
        var req: VkMemoryRequirements = undefined;
        getBufReq(device, bufs[b], &req);
        const mt = pickMemoryType(&mem_props, req.memoryTypeBits) orelse return AETH_VK_UNAVAILABLE;
        const mai = VkMemoryAllocateInfo{
            .sType = ST_MEMORY_ALLOCATE_INFO,
            .allocationSize = req.size,
            .memoryTypeIndex = mt,
        };
        if (allocMem(device, &mai, null, &mems[b]) != 0) return AETH_VK_UNAVAILABLE;
        if (bindBufMem(device, bufs[b], mems[b], 0) != 0) return AETH_VK_UNAVAILABLE;
        created = b + 1;
    }

    // Upload input into buffer 0 (pad tail with zeros).
    {
        var mapped: ?*anyopaque = null;
        if (mapMem(device, mems[0], 0, VK_WHOLE_SIZE, 0, &mapped) != 0) return AETH_VK_UNAVAILABLE;
        const dst: [*]f32 = @ptrCast(@alignCast(mapped.?));
        var i: u32 = 0;
        while (i < padded) : (i += 1) dst[i] = if (i < elem_count) input[i] else 0.0;
        unmapMem(device, mems[0]);
    }

    // Shader module from the SPIR-V binary.
    const smci = VkShaderModuleCreateInfo{
        .sType = ST_SHADER_MODULE_CREATE_INFO,
        .codeSize = spirv_len,
        .pCode = @ptrCast(@alignCast(spirv)),
    };
    var shader: VkShaderModule = 0;
    if (createShader(device, &smci, null, &shader) != 0) return AETH_VK_UNAVAILABLE;
    defer destroyShader(device, shader, null);

    // Descriptor set layout (2 storage buffers) + pipeline layout.
    const bindings = [2]VkDescriptorSetLayoutBinding{
        .{ .binding = 0, .descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, .descriptorCount = 1, .stageFlags = VK_SHADER_STAGE_COMPUTE_BIT },
        .{ .binding = 1, .descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, .descriptorCount = 1, .stageFlags = VK_SHADER_STAGE_COMPUTE_BIT },
    };
    const dslci = VkDescriptorSetLayoutCreateInfo{ .sType = ST_DESCRIPTOR_SET_LAYOUT_CREATE_INFO, .bindingCount = 2, .pBindings = &bindings };
    var dsl: VkDescriptorSetLayout = 0;
    if (createDSL(device, &dslci, null, &dsl) != 0) return AETH_VK_UNAVAILABLE;
    defer destroyDSL(device, dsl, null);
    const plci = VkPipelineLayoutCreateInfo{ .sType = ST_PIPELINE_LAYOUT_CREATE_INFO, .setLayoutCount = 1, .pSetLayouts = @ptrCast(&dsl) };
    var pl: VkPipelineLayout = 0;
    if (createPL(device, &plci, null, &pl) != 0) return AETH_VK_UNAVAILABLE;
    defer destroyPL(device, pl, null);

    // Compute pipeline.
    const cpci = VkComputePipelineCreateInfo{
        .sType = ST_COMPUTE_PIPELINE_CREATE_INFO,
        .stage = .{
            .sType = ST_PIPELINE_SHADER_STAGE_CREATE_INFO,
            .stage = VK_SHADER_STAGE_COMPUTE_BIT,
            .module = shader,
            .pName = "main",
        },
        .layout = pl,
    };
    var pipeline: VkPipeline = 0;
    if (createCompute(device, 0, 1, &cpci, null, &pipeline) != 0) return AETH_VK_UNAVAILABLE;
    defer destroyPipeline(device, pipeline, null);

    // Descriptor pool + set, pointing the two bindings at the two buffers.
    const psize = VkDescriptorPoolSize{ .type = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, .descriptorCount = 2 };
    const dpci = VkDescriptorPoolCreateInfo{ .sType = ST_DESCRIPTOR_POOL_CREATE_INFO, .maxSets = 1, .poolSizeCount = 1, .pPoolSizes = @ptrCast(&psize) };
    var pool: VkDescriptorPool = 0;
    if (createDescPool(device, &dpci, null, &pool) != 0) return AETH_VK_UNAVAILABLE;
    defer destroyDescPool(device, pool, null);
    const dsai = VkDescriptorSetAllocateInfo{ .sType = ST_DESCRIPTOR_SET_ALLOCATE_INFO, .descriptorPool = pool, .descriptorSetCount = 1, .pSetLayouts = @ptrCast(&dsl) };
    var dset: VkDescriptorSet = 0;
    if (allocDS(device, &dsai, &dset) != 0) return AETH_VK_UNAVAILABLE;
    const binfos = [2]VkDescriptorBufferInfo{
        .{ .buffer = bufs[0], .offset = 0, .range = VK_WHOLE_SIZE },
        .{ .buffer = bufs[1], .offset = 0, .range = VK_WHOLE_SIZE },
    };
    const writes = [2]VkWriteDescriptorSet{
        .{ .sType = ST_WRITE_DESCRIPTOR_SET, .dstSet = dset, .dstBinding = 0, .descriptorCount = 1, .descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, .pBufferInfo = @ptrCast(&binfos[0]) },
        .{ .sType = ST_WRITE_DESCRIPTOR_SET, .dstSet = dset, .dstBinding = 1, .descriptorCount = 1, .descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, .pBufferInfo = @ptrCast(&binfos[1]) },
    };
    updateDS(device, 2, &writes, 0, null);

    // Command buffer: bind pipeline + descriptor set, dispatch the workgroups.
    const cpci2 = VkCommandPoolCreateInfo{ .sType = ST_COMMAND_POOL_CREATE_INFO, .queueFamilyIndex = qfi };
    var cmdpool: VkCommandPool = 0;
    if (createCmdPool(device, &cpci2, null, &cmdpool) != 0) return AETH_VK_UNAVAILABLE;
    defer destroyCmdPool(device, cmdpool, null);
    const cbai = VkCommandBufferAllocateInfo{ .sType = ST_COMMAND_BUFFER_ALLOCATE_INFO, .commandPool = cmdpool, .commandBufferCount = 1 };
    var cmd: VkCommandBuffer = null;
    if (allocCmd(device, &cbai, &cmd) != 0) return AETH_VK_UNAVAILABLE;
    const cbbi = VkCommandBufferBeginInfo{ .sType = ST_COMMAND_BUFFER_BEGIN_INFO, .flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME };
    if (beginCmd(cmd, &cbbi) != 0) return AETH_VK_UNAVAILABLE;
    cmdBindPipeline(cmd, 1, pipeline); // 1 = VK_PIPELINE_BIND_POINT_COMPUTE
    cmdBindDS(cmd, 1, pl, 0, 1, @ptrCast(&dset), 0, null);
    cmdDispatch(cmd, groups, 1, 1);
    if (endCmd(cmd) != 0) return AETH_VK_UNAVAILABLE;

    const si = VkSubmitInfo{ .sType = ST_SUBMIT_INFO, .commandBufferCount = 1, .pCommandBuffers = @ptrCast(&cmd) };
    if (queueSubmit(queue, 1, @ptrCast(&si), 0) != 0) return AETH_VK_UNAVAILABLE;
    if (queueWaitIdle(queue) != 0) return AETH_VK_UNAVAILABLE;

    // Read the output buffer back.
    {
        var mapped: ?*anyopaque = null;
        if (mapMem(device, mems[1], 0, VK_WHOLE_SIZE, 0, &mapped) != 0) return AETH_VK_UNAVAILABLE;
        const src: [*]const f32 = @ptrCast(@alignCast(mapped.?));
        var i: u32 = 0;
        while (i < elem_count) : (i += 1) output[i] = src[i];
        unmapMem(device, mems[1]);
    }
    return AETH_VK_OK;
}

fn pickMemoryType(props: *const VkPhysicalDeviceMemoryProperties, type_bits: u32) ?u32 {
    const want: u32 = VK_MEMORY_HOST_VISIBLE | VK_MEMORY_HOST_COHERENT;
    var i: u32 = 0;
    while (i < props.memoryTypeCount) : (i += 1) {
        const usable = (type_bits & (@as(u32, 1) << @intCast(i))) != 0;
        if (usable and (props.memoryTypes[i].propertyFlags & want) == want) return i;
    }
    return null;
}
