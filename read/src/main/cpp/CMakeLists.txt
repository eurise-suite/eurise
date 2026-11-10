# Eurise Leggi: Tesseract (Apache 2.0) e Leptonica (BSD) compilati dai sorgenti in third_party, più un piccolo ponte JNI.
# Niente librerie d'immagine esterne: le immagini arrivano già come pixel da Android (Bitmap RGBA_8888).
cmake_minimum_required(VERSION 3.22.1)
project(eurise_read C CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(THIRD ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party)
set(LEPT ${THIRD}/leptonica)
set(TESS ${THIRD}/tesseract)
set(GEN ${CMAKE_CURRENT_BINARY_DIR}/gen)

# Leptonica ------------------------------------------------------------------------------------------------------
set(ENDIANNESS L_LITTLE_ENDIAN)
set(APPLE_UNIVERSAL_BUILD 0)
configure_file(${LEPT}/src/endianness.h.in ${GEN}/endianness.h @ONLY)
file(GLOB LEPT_SRC ${LEPT}/src/*.c)
add_library(leptonica STATIC ${LEPT_SRC})
target_include_directories(leptonica PUBLIC ${GEN} ${LEPT}/src)
# ANDROID_BUILD spegne in environ.h le librerie d'immagine che di base si danno per presenti (jpeg).
target_compile_definitions(leptonica PUBLIC ANDROID_BUILD)
target_compile_options(leptonica PRIVATE -O2 -w -ffunction-sections -fdata-sections -fvisibility=hidden)

# Tesseract ------------------------------------------------------------------------------------------------------
set(TESSERACT_MAJOR 5)
set(TESSERACT_MINOR 5)
set(TESSERACT_MICRO 1)
set(GENERIC_MAJOR_VERSION ${TESSERACT_MAJOR})
set(GENERIC_MINOR_VERSION ${TESSERACT_MINOR})
set(GENERIC_MICRO_VERSION ${TESSERACT_MICRO})
set(PACKAGE_VERSION "${TESSERACT_MAJOR}.${TESSERACT_MINOR}.${TESSERACT_MICRO}")
configure_file(${TESS}/include/tesseract/version.h.in ${GEN}/tesseract/version.h @ONLY)

set(TESS_DIRS ccmain ccstruct ccutil classify cutil dict lstm textord viewer wordrec)
set(TESS_SRC "")
foreach(dir ${TESS_DIRS})
  file(GLOB part ${TESS}/src/${dir}/*.cpp)
  list(APPEND TESS_SRC ${part})
endforeach()
# Motore legacy tolto (i modelli tessdata_fast sono solo LSTM): stesso elenco del CMakeLists di Tesseract 5.5.1.
set(TESS_LEGACY
    ccmain/adaptions.cpp ccmain/docqual.cpp ccmain/equationdetect.cpp ccmain/fixspace.cpp ccmain/fixxht.cpp
    ccmain/osdetect.cpp ccmain/par_control.cpp ccmain/recogtraining.cpp ccmain/superscript.cpp ccmain/tessbox.cpp
    ccmain/tfacepp.cpp ccstruct/fontinfo.cpp ccstruct/params_training_featdef.cpp ccutil/ambigs.cpp
    ccutil/bitvector.cpp ccutil/indexmapbidi.cpp classify/adaptive.cpp classify/adaptmatch.cpp classify/blobclass.cpp
    classify/cluster.cpp classify/clusttool.cpp classify/cutoffs.cpp classify/featdefs.cpp classify/float2int.cpp
    classify/fpoint.cpp classify/intfeaturespace.cpp classify/intfx.cpp classify/intmatcher.cpp classify/intproto.cpp
    classify/kdtree.cpp classify/mf.cpp classify/mfoutline.cpp classify/mfx.cpp classify/normfeat.cpp
    classify/normmatch.cpp classify/ocrfeatures.cpp classify/outfeat.cpp classify/picofeat.cpp classify/protos.cpp
    classify/shapeclassifier.cpp classify/shapetable.cpp classify/tessclassifier.cpp classify/trainingsample.cpp
    dict/permdawg.cpp dict/hyphen.cpp wordrec/associate.cpp wordrec/chop.cpp wordrec/chopper.cpp wordrec/drawfx.cpp
    wordrec/findseam.cpp wordrec/gradechop.cpp wordrec/language_model.cpp wordrec/lm_consistency.cpp
    wordrec/lm_pain_points.cpp wordrec/lm_state.cpp wordrec/outlines.cpp wordrec/params_model.cpp wordrec/pieces.cpp
    wordrec/plotedges.cpp wordrec/render.cpp wordrec/segsearch.cpp wordrec/wordclass.cpp)
foreach(f ${TESS_LEGACY})
  list(REMOVE_ITEM TESS_SRC ${TESS}/src/${f})
endforeach()
list(APPEND TESS_SRC
    ${TESS}/src/api/baseapi.cpp ${TESS}/src/api/capi.cpp ${TESS}/src/api/renderer.cpp
    ${TESS}/src/api/altorenderer.cpp ${TESS}/src/api/pagerenderer.cpp ${TESS}/src/api/hocrrenderer.cpp
    ${TESS}/src/api/lstmboxrenderer.cpp ${TESS}/src/api/pdfrenderer.cpp ${TESS}/src/api/wordstrboxrenderer.cpp
    ${TESS}/src/arch/dotproduct.cpp ${TESS}/src/arch/simddetect.cpp ${TESS}/src/arch/intsimdmatrix.cpp)

# TESSERACT_IMAGEDATA_AS_PIX: le parole restano Pix in memoria invece di passare da un PNG (qui non c'è libpng).
set(TESS_DEFS GRAPHICS_DISABLED DISABLED_LEGACY_ENGINE FAST_FLOAT TESSERACT_IMAGEDATA_AS_PIX)
if(CMAKE_SYSTEM_PROCESSOR MATCHES "aarch64|arm64")
  list(APPEND TESS_SRC ${TESS}/src/arch/dotproductneon.cpp ${TESS}/src/arch/intsimdmatrixneon.cpp)
  list(APPEND TESS_DEFS HAVE_NEON)
endif()

add_library(tesseract STATIC ${TESS_SRC})
target_include_directories(tesseract PUBLIC ${GEN} ${TESS}/include)
target_include_directories(tesseract PRIVATE ${TESS}/src
    ${TESS}/src/arch ${TESS}/src/ccmain ${TESS}/src/ccstruct ${TESS}/src/ccutil ${TESS}/src/classify
    ${TESS}/src/cutil ${TESS}/src/dict ${TESS}/src/lstm ${TESS}/src/textord ${TESS}/src/viewer ${TESS}/src/wordrec)
target_compile_definitions(tesseract PRIVATE ${TESS_DEFS})
target_compile_options(tesseract PRIVATE -O2 -w -ffunction-sections -fdata-sections -fvisibility=hidden)
target_link_libraries(tesseract PUBLIC leptonica)

# Ponte JNI ------------------------------------------------------------------------------------------------------
if(ANDROID)
  add_library(eurise_ocr SHARED eurise_ocr.cpp)
  target_compile_options(eurise_ocr PRIVATE -O2 -fvisibility=hidden -ffunction-sections -fdata-sections)
  target_link_options(eurise_ocr PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL)
  find_library(LOG_LIB log)
  find_library(JNIGRAPHICS_LIB jnigraphics)
  target_link_libraries(eurise_ocr tesseract ${JNIGRAPHICS_LIB} ${LOG_LIB})
else()
  # Prova sul computer (senza Android): un programmino che legge un'immagine PNM con gli stessi passi del ponte.
  add_executable(eurise_ocr_test host_test.cpp)
  target_link_libraries(eurise_ocr_test tesseract pthread)
endif()
