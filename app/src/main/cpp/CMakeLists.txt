cmake_minimum_required(VERSION 3.22)
project(xuabadai_ai)

set(CMAKE_CXX_STANDARD 17)

# Build llama.cpp using its own CMake system (provides GGML_VERSION, GGML_COMMIT etc.)
set(LLAMA_CUDA OFF CACHE BOOL "" FORCE)
set(LLAMA_VULKAN OFF CACHE BOOL "" FORCE)
set(LLAMA_METAL OFF CACHE BOOL "" FORCE)
set(LLAMA_KOMPUTE OFF CACHE BOOL "" FORCE)
set(LLAMA_SYCL OFF CACHE BOOL "" FORCE)
set(LLAMA_ACCELERATE OFF CACHE BOOL "" FORCE)
set(LLAMA_BLAS OFF CACHE BOOL "" FORCE)
set(LLAMA_RPC OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL ON CACHE BOOL "" FORCE)  # Enable for model download
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_CLI OFF CACHE BOOL "" FORCE)

# Use llama.cpp's cmake as subdirectory
add_subdirectory(llama.cpp)

# JNI bridge library
add_library(xuabadai_jni SHARED xuabadai_jni.cpp)

target_include_directories(xuabadai_jni PRIVATE
    llama.cpp/include
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(xuabadai_jni
    llama
    log
    android
)
