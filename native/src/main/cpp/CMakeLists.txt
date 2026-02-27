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
    # Release optimizations
    # Size optimization (-Os) or speed optimization (-O3)
    set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -Os -DNDEBUG")
    set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} -Os -DNDEBUG")

    # Function and data sections for dead code elimination
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -ffunction-sections -fdata-sections")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -ffunction-sections -fdata-sections")

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

    # Link-time optimizations
    target_link_options(thorvg-native PRIVATE
            -Wl,--gc-sections           # Remove unused sections
            -Wl,--strip-all             # Strip all symbols
            -Wl,--no-undefined          # Catch linking errors early
            -Wl,-O3                     # Linker optimization level
    )
endif ()
