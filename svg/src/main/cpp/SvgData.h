/*
 * Copyright (c) 2025 - 2026 ThorVG project. All rights reserved.

 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:

 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.

 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#ifndef THORVG_ANDROID_SVGDATA_H
#define THORVG_ANDROID_SVGDATA_H

#include <thorvg.h>
#include <cstdint>
#include <memory>

/**
 * Encapsulates SVG data and ThorVG rendering context.
 * Manages the lifecycle of Picture and Canvas objects.
 */
class SvgData {
public:
    std::unique_ptr<tvg::Picture> picture = nullptr;
    std::unique_ptr<tvg::SwCanvas> canvas = nullptr;
    uint32_t* buffer = nullptr;
    uint32_t width = 0;
    uint32_t height = 0;

    SvgData() = default;
    ~SvgData() = default;

    /**
     * Sets the render buffer and size.
     * Creates a new canvas and resizes the picture.
     */
    void setBufferSize(uint32_t* buf, float w, float h);

    /**
     * Renders the SVG to the buffer.
     */
    void draw();
};

#endif // THORVG_ANDROID_SVGDATA_H
