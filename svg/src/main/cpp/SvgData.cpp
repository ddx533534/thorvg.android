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

#include "SvgData.h"
#include <android/log.h>

#define LOG_TAG "SvgData"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

void SvgData::setBufferSize(uint32_t* buf, float w, float h) {
    buffer = buf;
    width = static_cast<uint32_t>(w);
    height = static_cast<uint32_t>(h);

    // Create new canvas
    canvas = tvg::SwCanvas::gen();
    if (!canvas) {
        LOGE("Failed to create SwCanvas");
        return;
    }

    // Set canvas target
    // Note: Android ARGB_8888 bitmap is actually ABGR in memory on little-endian systems
    if (canvas->target(buffer, width, width, height, tvg::SwCanvas::Colorspace::ABGR8888) != tvg::Result::Success) {
        LOGE("Failed to set canvas target");
        canvas.reset();
        return;
    }

    // Resize picture to fit the canvas (maintains aspect ratio)
    if (picture) {
        if (picture->size(w, h) != tvg::Result::Success) {
            LOGE("Failed to resize picture");
        }

        // Push picture to canvas (returns raw pointer that canvas manages)
        if (canvas->push(tvg::cast(picture.get())) != tvg::Result::Success) {
            LOGE("Failed to push picture to canvas");
        }
    }
}

void SvgData::draw() {
    if (!canvas) {
        LOGE("Canvas is null, cannot draw");
        return;
    }

    // Update canvas
    if (canvas->update() != tvg::Result::Success) {
        LOGE("Failed to update canvas");
        return;
    }

    // Draw to buffer
    if (canvas->draw() != tvg::Result::Success) {
        LOGE("Failed to draw canvas");
        return;
    }

    // Sync (wait for rendering to complete)
    if (canvas->sync() != tvg::Result::Success) {
        LOGE("Failed to sync canvas");
        return;
    }
}
