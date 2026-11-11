# Eurise AI: llama.cpp (MIT) dai sorgenti in third_party/llama.cpp, solo CPU, più un ponte JNI.
#
# Sul telefono (arm64) la ggml si compila come fa l'esempio Android di llama.cpp: un pezzo comune e una variante della
# parte CPU per ogni famiglia di processori (armv8.0, 8.2 con dotprod, 8.6 con i8mm, 9.x), scelta all'avvio secondo il
# processore. Così i telefoni nuovi usano le istruzioni veloci e quelli vecchi non si fermano.
# Sul computer (prova della compilazione) tutto statico e ottimizzato per quella macchina.
cmake_minimum_required(VERSION 3.22.1)
project(eurise_ai C CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

get_filename_component(LLAMA ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/llama.cpp ABSOLUTE)

set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(GGML_CPU_KLEIDIAI OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE ON CACHE BOOL "" FORCE)
if (ANDROID)
    set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
    set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
    set(GGML_BACKEND_DL ON CACHE BOOL "" FORCE)
    set(GGML_CPU_ALL_VARIANTS ON CACHE BOOL "" FORCE)
else()
    set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
    set(GGML_NATIVE ON CACHE BOOL "" FORCE)
    set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
endif()
add_subdirectory(${LLAMA}/ggml ${CMAKE_BINARY_DIR}/ggml)

# llama: compilata qui (il suo CMake chiede quello principale di llama.cpp, che non teniamo), statica dentro il ponte.
file(GLOB LLAMA_CORE ${LLAMA}/src/*.cpp)
file(GLOB LLAMA_MODELS ${LLAMA}/src/models/*.cpp)
set(LLAMA_SOURCES ${LLAMA_CORE} ${LLAMA_MODELS})
# Come nel CMake di llama.cpp: solo i modelli si compilano a gruppi, il nucleo file per file.
set_source_files_properties(${LLAMA_CORE} PROPERTIES SKIP_UNITY_BUILD_INCLUSION ON)
set(LLAMA_VERSION "eurise")
set(LLAMA_BUILD_COMMIT "af6b524")
configure_file(${LLAMA}/src/llama-version.h.in ${CMAKE_BINARY_DIR}/llama-version.h @ONLY)
add_library(llama STATIC ${LLAMA_SOURCES})
set_target_properties(llama PROPERTIES POSITION_INDEPENDENT_CODE ON UNITY_BUILD ON UNITY_BUILD_BATCH_SIZE 16)
target_include_directories(llama PRIVATE ${LLAMA}/src ${CMAKE_BINARY_DIR} PUBLIC ${LLAMA}/include)
target_compile_options(llama PRIVATE -O3 -ffunction-sections -fdata-sections)
find_package(Threads REQUIRED)
target_link_libraries(llama PUBLIC ggml PRIVATE Threads::Threads)

if (ANDROID)
    add_library(eurise_ai SHARED eurise_ai_jni.cpp)
    target_compile_options(eurise_ai PRIVATE -O3 -fvisibility=hidden -ffunction-sections -fdata-sections)
    target_link_options(eurise_ai PRIVATE -Wl,--gc-sections)
    find_library(LOG_LIB log)
    target_link_libraries(eurise_ai llama ggml ${LOG_LIB})
else()
    # Stesso motore dell'APK, per misurare comprensione e velocità durante la compilazione.
    add_executable(eurise_ai_test host_test.cpp)
    target_link_libraries(eurise_ai_test llama ggml)
endif()
