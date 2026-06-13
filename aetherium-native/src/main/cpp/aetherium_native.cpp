/*
 * Aetherium Framework — native bridge core (C ABI).
 * Copyright (C) 2026 RedstoneTeam.
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along
 * with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * ---------------------------------------------------------------------------
 * EN: A deliberately NARROW, versioned C ABI (a flat function table, not a C++
 *     class surface) so the Java FFM bindings target stable symbols. No exception
 *     ever crosses the boundary; every entry point validates its arguments and
 *     returns a status/value. Vulkan is reached via dlopen at call time so this
 *     library LOADS even on hosts with no Vulkan — graceful degradation happens
 *     in Java, not by failing to load the .so.
 * RU: Намеренно УЗКИЙ версионированный C ABI (плоская таблица функций, а не
 *     поверхность классов C++), чтобы Java FFM-привязки целились в стабильные
 *     символы. Ни одно исключение не пересекает границу; каждая точка входа
 *     проверяет аргументы и возвращает статус/значение. Vulkan достигается через
 *     dlopen во время вызова, поэтому библиотека ЗАГРУЖАЕТСЯ даже на хостах без
 *     Vulkan — мягкая деградация происходит в Java, а не при загрузке .so.
 */

#include <cstdint>
#include <cstddef>
#include <cstring>
#include <dlfcn.h>
#include <vulkan/vulkan.h>

extern "C" {

/* Bump on any incompatible change to the function table below. Java refuses a mismatch. */
#define AETH_ABI_VERSION 1

/* Status codes shared with the Java side. */
#define AETH_VK_OK            0
#define AETH_VK_UNAVAILABLE (-1)   /* libvulkan / symbols not present — degrade gracefully */
#define AETH_VK_BAD_ARGS    (-2)

__attribute__((visibility("default")))
int32_t aeth_native_abi_version(void) {
    return AETH_ABI_VERSION;
}

/*
 * EN: Sanity self-test — mirrors the Java dispatch "doubler" so the Pre-Flight Check can
 *     cross-verify that a native downcall produces the expected value.
 * RU: Самопроверка — повторяет Java-«удвоитель», чтобы Pre-Flight Check мог сверить, что
 *     нативный downcall даёт ожидаемое значение.
 */
__attribute__((visibility("default")))
int64_t aeth_self_test(int32_t input) {
    return static_cast<int64_t>(input) * 2;
}

/*
 * EN: Sums bytes of a buffer the JVM allocated inside an Arena. Proves Arena-owned memory
 *     crossing the FFM boundary; the JVM frees it deterministically on Arena.close().
 * RU: Суммирует байты буфера, выделенного JVM внутри Arena. Доказывает передачу
 *     Arena-памяти через границу FFM; JVM освобождает её детерминированно при Arena.close().
 */
__attribute__((visibility("default")))
int64_t aeth_sum_bytes(const uint8_t* data, size_t len) {
    if (data == nullptr) {
        return AETH_VK_BAD_ARGS;
    }
    int64_t sum = 0;
    for (size_t i = 0; i < len; ++i) {
        sum += static_cast<int64_t>(data[i]);
    }
    return sum;
}

/* Flat result struct: 4 x int32 = 16 bytes. Mirrored by a Java MemoryLayout. */
struct AethVkInfo {
    int32_t available;          /* 1 if a Vulkan instance was created */
    int32_t device_count;       /* number of physical devices */
    int32_t queue_family_count; /* queue families on physical device 0 */
    int32_t api_version;        /* packed VK_API_VERSION requested, else 0 */
};

/*
 * EN: The reliable HARDWARE ACCESS layer — NOT shader logic. Loads libvulkan via dlopen,
 *     creates a transient instance, enumerates physical devices and the queue families of
 *     device 0, then tears everything down. Returns AETH_VK_UNAVAILABLE on any absence or
 *     failure so the caller degrades to a CPU/pure-Java path.
 * RU: Надёжный слой ДОСТУПА К ОБОРУДОВАНИЮ — НЕ логика шейдеров. Загружает libvulkan через
 *     dlopen, создаёт временный instance, перечисляет физические устройства и семейства
 *     очередей устройства 0, затем всё разрушает. Возвращает AETH_VK_UNAVAILABLE при любом
 *     отсутствии/сбое, чтобы вызывающая сторона деградировала на CPU/чистую Java.
 */
__attribute__((visibility("default")))
int32_t aeth_vk_probe(AethVkInfo* out) {
    if (out == nullptr) {
        return AETH_VK_BAD_ARGS;
    }
    std::memset(out, 0, sizeof(AethVkInfo));

    void* lib = dlopen("libvulkan.so.1", RTLD_NOW | RTLD_LOCAL);
    if (lib == nullptr) {
        lib = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
    }
    if (lib == nullptr) {
        return AETH_VK_UNAVAILABLE;
    }

    auto create_instance =
        reinterpret_cast<PFN_vkCreateInstance>(dlsym(lib, "vkCreateInstance"));
    auto enumerate_devices =
        reinterpret_cast<PFN_vkEnumeratePhysicalDevices>(dlsym(lib, "vkEnumeratePhysicalDevices"));
    auto queue_family_props =
        reinterpret_cast<PFN_vkGetPhysicalDeviceQueueFamilyProperties>(
            dlsym(lib, "vkGetPhysicalDeviceQueueFamilyProperties"));
    auto destroy_instance =
        reinterpret_cast<PFN_vkDestroyInstance>(dlsym(lib, "vkDestroyInstance"));

    if (create_instance == nullptr || enumerate_devices == nullptr || queue_family_props == nullptr) {
        dlclose(lib);
        return AETH_VK_UNAVAILABLE;
    }

    VkApplicationInfo app_info{};
    app_info.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    app_info.pApplicationName = "AetheriumPreFlight";
    app_info.applicationVersion = VK_MAKE_VERSION(0, 1, 0);
    app_info.pEngineName = "Aetherium";
    app_info.apiVersion = VK_API_VERSION_1_0;
    out->api_version = static_cast<int32_t>(app_info.apiVersion);

    VkInstanceCreateInfo create_info{};
    create_info.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    create_info.pApplicationInfo = &app_info;

    VkInstance instance = VK_NULL_HANDLE;
    if (create_instance(&create_info, nullptr, &instance) != VK_SUCCESS) {
        dlclose(lib);
        return AETH_VK_UNAVAILABLE;
    }
    out->available = 1;

    uint32_t device_count = 0;
    enumerate_devices(instance, &device_count, nullptr);
    out->device_count = static_cast<int32_t>(device_count);

    if (device_count > 0) {
        uint32_t capped = device_count > 16u ? 16u : device_count;
        VkPhysicalDevice devices[16];
        enumerate_devices(instance, &capped, devices);

        uint32_t qf_count = 0;
        queue_family_props(devices[0], &qf_count, nullptr);
        out->queue_family_count = static_cast<int32_t>(qf_count);
    }

    if (destroy_instance != nullptr) {
        destroy_instance(instance, nullptr);
    }
    dlclose(lib);
    return AETH_VK_OK;
}

} /* extern "C" */
