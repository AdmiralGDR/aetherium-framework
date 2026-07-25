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
