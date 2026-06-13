# Aetherium Framework — native bridge build.
# EN: Builds libaetherium_native.so. Links only libdl (for dlopen); Vulkan is loaded at
#     runtime via dlopen, so the library has NO hard dependency on libvulkan and loads
#     everywhere. Vulkan headers are required only at compile time.
# RU: Собирает libaetherium_native.so. Линкует только libdl (для dlopen); Vulkan грузится
#     во время выполнения через dlopen, поэтому у библиотеки НЕТ жёсткой зависимости от
#     libvulkan, и она загружается везде. Заголовки Vulkan нужны только при компиляции.
cmake_minimum_required(VERSION 3.16)
project(aetherium_native CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_VISIBILITY_PRESET hidden)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

add_library(aetherium_native SHARED aetherium_native.cpp)
set_target_properties(aetherium_native PROPERTIES OUTPUT_NAME aetherium_native PREFIX "lib")

# Vulkan headers for typedefs only (we do not link the loader).
find_path(VULKAN_INCLUDE_DIR vulkan/vulkan.h)
if(VULKAN_INCLUDE_DIR)
    target_include_directories(aetherium_native PRIVATE ${VULKAN_INCLUDE_DIR})
endif()

target_link_libraries(aetherium_native PRIVATE ${CMAKE_DL_LIBS})
