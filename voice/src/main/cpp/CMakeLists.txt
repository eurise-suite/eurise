# Eurise Voce: whisper.cpp (MIT) con il solo backend CPU, più un piccolo ponte JNI.
cmake_minimum_required(VERSION 3.22.1)
project(eurise_voice C CXX)
set(CMAKE_CXX_STANDARD 17)

set(WHISPER ${CMAKE_SOURCE_DIR}/../../../../third_party/whisper.cpp)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
add_subdirectory(${WHISPER}/ggml ${CMAKE_BINARY_DIR}/ggml)

add_library(eurise_whisper SHARED
    ${WHISPER}/src/whisper.cpp
    ${WHISPER}/src/aneforge/whisper-aneforge.cpp
    eurise_whisper.c)
target_include_directories(eurise_whisper PRIVATE ${WHISPER}/include ${WHISPER}/src ${WHISPER}/ggml/include)
target_compile_definitions(eurise_whisper PRIVATE WHISPER_VERSION="eurise")
target_compile_options(eurise_whisper PRIVATE -O3 -fvisibility=hidden -ffunction-sections -fdata-sections)
target_link_options(eurise_whisper PRIVATE -Wl,--gc-sections)
find_library(LOG_LIB log)
target_link_libraries(eurise_whisper ggml ${LOG_LIB})
