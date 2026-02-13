cmake_minimum_required(VERSION 3.4.1)

# Configure import libs
set(THORVG_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../thorvg)
set(LOTTIE_CPP_DIR ${CMAKE_CURRENT_SOURCE_DIR}/lottie)
set(SVG_CPP_DIR ${CMAKE_CURRENT_SOURCE_DIR}/svg)

if (${ANDROID_ABI} STREQUAL "arm64-v8a" OR ${ANDROID_ABI} STREQUAL "x86_64")
    # Import ThorVG static library
    add_library(lib_thorvg STATIC IMPORTED)
    set_target_properties(lib_thorvg PROPERTIES IMPORTED_LOCATION
            ${THORVG_DIR}/lib/${ANDROID_ABI}/libthorvg.a)

    # Build unified shared library containing both SVG and Lottie support
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=gnu++14")

    add_library(thorvg-native SHARED
            # Lottie source files
            ${LOTTIE_CPP_DIR}/lottie-libs.cpp
            ${LOTTIE_CPP_DIR}/LottieData.cpp
            # SVG source files
            ${SVG_CPP_DIR}/svg-libs.cpp
            ${SVG_CPP_DIR}/SvgData.cpp)

    target_include_directories(thorvg-native PRIVATE
            ${THORVG_DIR}/inc
            ${LOTTIE_CPP_DIR}
            ${SVG_CPP_DIR})

    target_link_libraries(thorvg-native
            android
            lib_thorvg
            -ljnigraphics
            -fopenmp
            -static-openmp
            log)
endif ()
