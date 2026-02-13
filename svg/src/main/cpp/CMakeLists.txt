cmake_minimum_required(VERSION 3.4.1)

# Configure import libs
set(THORVG_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../thorvg)

if (${ANDROID_ABI} STREQUAL "arm64-v8a" OR ${ANDROID_ABI} STREQUAL "x86_64")
    # Import ThorVG static library
    add_library(lib_thorvg STATIC IMPORTED)
    set_target_properties(lib_thorvg PROPERTIES IMPORTED_LOCATION
            ${THORVG_DIR}/lib/${ANDROID_ABI}/libthorvg.a)

    # Build application's shared lib
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=gnu++14")

    add_library(svg-libs SHARED
            svg-libs.cpp
            SvgData.cpp)

    target_include_directories(svg-libs PRIVATE
            ${THORVG_DIR}/inc)

    target_link_libraries(svg-libs
            android
            lib_thorvg
            -ljnigraphics
            -fopenmp
            -static-openmp
            log)
endif()
