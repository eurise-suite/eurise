# Prototipo di Eurise Traduci: slimt (MIT, inferenza dei modelli di traduzione di Firefox/Bergamot) con ruy (Apache 2.0)
# e SentencePiece (Apache 2.0), compilati dai sorgenti di third_party, più un piccolo ponte JNI.
cmake_minimum_required(VERSION 3.22.1)
project(eurise_translate C CXX)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(THIRD ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party)
set(SLIMT ${THIRD}/slimt)
set(RUY ${THIRD}/ruy)
set(SPM ${THIRD}/sentencepiece)
set(GEN ${CMAKE_CURRENT_BINARY_DIR}/gen)
set(OPT -O2 -w -ffunction-sections -fdata-sections -fvisibility=hidden -fvisibility-inlines-hidden)

# ruy (moltiplicazione di matrici a 8 bit) ---------------------------------------------------------------------
file(GLOB RUY_SRC ${RUY}/ruy/*.cc)
list(APPEND RUY_SRC ${RUY}/ruy/profiler/instrumentation.cc)
add_library(ruy STATIC ${RUY_SRC})
target_include_directories(ruy PUBLIC ${RUY})
target_compile_options(ruy PRIVATE ${OPT})
set_target_properties(ruy PROPERTIES CXX_STANDARD 14)
if(NOT ANDROID)
  target_link_libraries(ruy PUBLIC pthread)
endif()

# SentencePiece (solo la parte che divide il testo, niente addestramento) ----------------------------------------
configure_file(${SPM}/config.h.in ${GEN}/config.h)
file(GLOB PROTOBUF_LITE_SRC ${SPM}/third_party/protobuf-lite/*.cc)
add_library(sentencepiece STATIC
    ${PROTOBUF_LITE_SRC}
    ${SPM}/third_party/absl/flags/flag.cc
    ${SPM}/src/builtin_pb/sentencepiece.pb.cc ${SPM}/src/builtin_pb/sentencepiece_model.pb.cc
    ${SPM}/src/bpe_model.cc ${SPM}/src/char_model.cc ${SPM}/src/error.cc ${SPM}/src/filesystem.cc
    ${SPM}/src/model_factory.cc ${SPM}/src/model_interface.cc ${SPM}/src/normalizer.cc
    ${SPM}/src/sentencepiece_processor.cc ${SPM}/src/unigram_model.cc ${SPM}/src/util.cc ${SPM}/src/word_model.cc)
target_include_directories(sentencepiece PUBLIC ${SPM}/src ${SPM}/src/builtin_pb ${SPM}/third_party/protobuf-lite ${SPM} ${GEN})
target_compile_definitions(sentencepiece PUBLIC HAVE_PTHREAD=1 _USE_INTERNAL_STRING_VIEW)
target_compile_options(sentencepiece PRIVATE ${OPT})
set_target_properties(sentencepiece PROPERTIES CXX_STANDARD 17)

# slimt ----------------------------------------------------------------------------------------------------------
set(PROJECT_VERSION_STRING_FULL "0.1.0-eurise")
configure_file(${SLIMT}/slimt/Version.hh.in ${GEN}/slimt/Version.hh @ONLY)
file(WRITE ${GEN}/slimt/Export.hh "#pragma once\n#define SLIMT_EXPORT\n#define SLIMT_NO_EXPORT\n#define SLIMT_DEPRECATED\n")
set(SLIMT_SRC Aligned Annotation Arena Batcher Frontend Input Io Model Modules QMM Request Response Shortlist Tensor
    TensorOps TextProcessor Transformer Utils Vocabulary)
list(TRANSFORM SLIMT_SRC PREPEND ${SLIMT}/slimt/)
list(TRANSFORM SLIMT_SRC APPEND .cc)
add_library(slimt STATIC ${SLIMT_SRC})
target_include_directories(slimt PUBLIC ${SLIMT} ${GEN})
target_compile_definitions(slimt PRIVATE SLIMT_HAS_RUY)
if(CMAKE_SYSTEM_PROCESSOR MATCHES "aarch64|arm64")
  target_compile_definitions(slimt PRIVATE USE_NEON)
elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "x86_64|AMD64")
  target_compile_definitions(slimt PRIVATE USE_SSE2)
endif()
target_compile_options(slimt PRIVATE ${OPT})
target_link_libraries(slimt PUBLIC sentencepiece PRIVATE ruy)

# Ponte JNI o prova sul computer ---------------------------------------------------------------------------------
if(ANDROID)
  add_library(eurise_translate SHARED eurise_translate.cpp)
  target_compile_options(eurise_translate PRIVATE ${OPT})
  target_link_options(eurise_translate PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL)
  find_library(LOG_LIB log)
  target_link_libraries(eurise_translate slimt ruy ${LOG_LIB})
else()
  add_executable(eurise_translate_test host_test.cpp)
  target_link_libraries(eurise_translate_test slimt ruy pthread)
endif()
