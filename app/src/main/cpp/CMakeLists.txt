cmake_minimum_required(VERSION 3.22.1)
project(thumbkey_whisper LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_CURL OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)

set(WHISPER_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/whisper.cpp")
add_subdirectory("${WHISPER_ROOT}" "${CMAKE_CURRENT_BINARY_DIR}/whisper" EXCLUDE_FROM_ALL)

add_library(thumbkey_whisper SHARED whisper_jni.cpp)
target_include_directories(thumbkey_whisper PRIVATE "${WHISPER_ROOT}/include")
target_link_libraries(thumbkey_whisper PRIVATE whisper android log)
target_link_options(thumbkey_whisper PRIVATE "-Wl,-z,max-page-size=16384")
