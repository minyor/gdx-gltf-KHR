cmake_minimum_required(VERSION 3.18.1)
project(gdx-gltf-native)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Basis Universal transcoder sources
set(BASIS_SOURCES
    basis/basisu_transcoder.cpp
)

# Zstd library sources
file(GLOB ZSTD_COMMON_SOURCES "basis/common/*.c")
file(GLOB ZSTD_COMPRESS_SOURCES "basis/compress/*.c")
file(GLOB ZSTD_DECOMPRESS_SOURCES "basis/decompress/*.c")

set(ZSTD_SOURCES
    ${ZSTD_COMMON_SOURCES}
    ${ZSTD_COMPRESS_SOURCES}
    ${ZSTD_DECOMPRESS_SOURCES}
)

# Meshoptimizer sources
file(GLOB MESHOPT_SOURCES "meshopt/src/*.cpp")

# JNI wrappers
set(JNI_SOURCES
    basis_transcoder.cpp
    meshopt_jni.cpp
)

add_library(gdx-gltf-native SHARED
    ${BASIS_SOURCES}
    ${ZSTD_SOURCES}
    ${MESHOPT_SOURCES}
    ${JNI_SOURCES}
)

target_include_directories(gdx-gltf-native PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/basis
    ${CMAKE_CURRENT_SOURCE_DIR}/meshopt
)

# Enable KTX2+ZSTD support
target_compile_definitions(gdx-gltf-native PRIVATE
    BASISD_SUPPORT_KTX2=1
    BASISD_SUPPORT_KTX2_ZSTD=1
)

# Compilation flags
target_compile_options(gdx-gltf-native PRIVATE
    -fno-strict-aliasing
    -O2
)

target_link_libraries(gdx-gltf-native
    log
    android
)