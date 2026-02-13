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

#include <android/bitmap.h>
#include <thorvg.h>
#include <jni.h>
#include <string>
#include <android/log.h>
#include "SvgData.h"

#define LOG_TAG "svg-libs"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

using namespace std;

/**
 * Load SVG from file path.
 */
extern "C" jlong
Java_org_thorvg_jni_Svg_nLoadSvgFromPath(
        JNIEnv *env, jclass clazz, jstring path, jfloatArray outSize) {

    // Initialize ThorVG engine (2 threads)
    if (tvg::Initializer::init(tvg::CanvasEngine::Sw, 2) != tvg::Result::Success) {
        LOGE("Failed to initialize ThorVG");
        return 0;
    }

    const char *pathStr = env->GetStringUTFChars(path, nullptr);
    if (!pathStr) {
        LOGE("Failed to get path string");
        tvg::Initializer::term(tvg::CanvasEngine::Sw);
        return 0;
    }

    auto *svgData = new SvgData();

    // Create Picture and load SVG
    svgData->picture = tvg::Picture::gen();
    if (!svgData->picture) {
        LOGE("Failed to generate Picture");
        delete svgData;
        env->ReleaseStringUTFChars(path, pathStr);
        tvg::Initializer::term(tvg::CanvasEngine::Sw);
        return 0;
    }

    if (svgData->picture->load(pathStr) != tvg::Result::Success) {
        LOGE("Failed to load SVG from path: %s", pathStr);
        delete svgData;
        env->ReleaseStringUTFChars(path, pathStr);
        tvg::Initializer::term(tvg::CanvasEngine::Sw);
        return 0;
    }

    // Get SVG intrinsic size
    float w, h;
    if (svgData->picture->size(&w, &h) != tvg::Result::Success) {
        LOGE("Failed to get SVG size");
        w = 0;
        h = 0;
    }

    LOGD("Loaded SVG from path: %s, size: %.0fx%.0f", pathStr, w, h);

    // Return size information
    jfloat *sizeArray = env->GetFloatArrayElements(outSize, nullptr);
    if (sizeArray) {
        sizeArray[0] = w;
        sizeArray[1] = h;
        env->ReleaseFloatArrayElements(outSize, sizeArray, 0);
    }

    env->ReleaseStringUTFChars(path, pathStr);
    return reinterpret_cast<jlong>(svgData);
}

/**
 * Load SVG from string content.
 */
extern "C" jlong
Java_org_thorvg_jni_Svg_nLoadSvgFromString(
        JNIEnv *env, jclass clazz, jstring content, jfloatArray outSize) {

    // Initialize ThorVG engine (2 threads)
    if (tvg::Initializer::init(tvg::CanvasEngine::Sw, 2) != tvg::Result::Success) {
        LOGE("Failed to initialize ThorVG");
        return 0;
    }

    const char *contentStr = env->GetStringUTFChars(content, nullptr);
    if (!contentStr) {
        LOGE("Failed to get content string");
        tvg::Initializer::term(tvg::CanvasEngine::Sw);
        return 0;
    }

    const jsize contentLen = env->GetStringUTFLength(content);

    LOGD("Loading SVG from string, length: %d", contentLen);
    LOGD("SVG content preview: %.100s", contentStr);

    auto *svgData = new SvgData();

    // Create Picture and load SVG from memory
    svgData->picture = tvg::Picture::gen();
    if (!svgData->picture) {
        LOGE("Failed to generate Picture");
        delete svgData;
        env->ReleaseStringUTFChars(content, contentStr);
        tvg::Initializer::term(tvg::CanvasEngine::Sw);
        return 0;
    }

    // Load from memory (mimeType: "svg", rpath=nullptr, copy=true)
    auto loadResult = svgData->picture->load(contentStr, contentLen, "svg", true);
    LOGD("Picture::load() result: %d", static_cast<int>(loadResult));

    if (loadResult != tvg::Result::Success) {
        LOGE("Failed to load SVG from string, result code: %d", static_cast<int>(loadResult));
        delete svgData;
        env->ReleaseStringUTFChars(content, contentStr);
        tvg::Initializer::term(tvg::CanvasEngine::Sw);
        return 0;
    }

    // Get SVG intrinsic size
    float w, h;
    if (svgData->picture->size(&w, &h) != tvg::Result::Success) {
        LOGE("Failed to get SVG size");
        w = 0;
        h = 0;
    }

    LOGD("Loaded SVG from string, size: %.0fx%.0f", w, h);

    // Return size information
    jfloat *sizeArray = env->GetFloatArrayElements(outSize, nullptr);
    if (sizeArray) {
        sizeArray[0] = w;
        sizeArray[1] = h;
        env->ReleaseFloatArrayElements(outSize, sizeArray, 0);
    }

    env->ReleaseStringUTFChars(content, contentStr);
    return reinterpret_cast<jlong>(svgData);
}

/**
 * Set SVG rendering size.
 */
extern "C" void
Java_org_thorvg_jni_Svg_nSetSvgSize(
        JNIEnv *env, jclass clazz, jlong svgPtr, jobject bitmap, jfloat width, jfloat height) {

    if (svgPtr == 0) {
        LOGE("Invalid SVG pointer");
        return;
    }

    auto *svgData = reinterpret_cast<SvgData *>(svgPtr);
    void *buffer;

    if (AndroidBitmap_lockPixels(env, bitmap, &buffer) >= 0) {
        svgData->setBufferSize(static_cast<uint32_t *>(buffer), width, height);
        AndroidBitmap_unlockPixels(env, bitmap);
        LOGD("Set SVG size: %.0fx%.0f", width, height);
    } else {
        LOGE("Failed to lock bitmap pixels");
    }
}

/**
 * Draw SVG to bitmap.
 */
extern "C" void
Java_org_thorvg_jni_Svg_nDrawSvg(
        JNIEnv
        *env,
        jclass clazz, jlong
        svgPtr,
        jobject bitmap
) {

    if (svgPtr == 0) {
        LOGE("Invalid SVG pointer");
        return;
    }

    auto *svgData = reinterpret_cast<SvgData *>(svgPtr);
    void *buffer;

    if (
            AndroidBitmap_lockPixels(env, bitmap, &buffer
            ) >= 0) {
        svgData->

                draw();

        AndroidBitmap_unlockPixels(env, bitmap
        );
    } else {
        LOGE("Failed to lock bitmap pixels");
    }
}

/**
 * Destroy SVG and release resources.
 */
extern "C" void
Java_org_thorvg_jni_Svg_nDestroySvg(
        JNIEnv
        *env,
        jclass clazz, jlong
        svgPtr) {

    if (svgPtr == 0) {
        LOGE("Invalid SVG pointer");
        return;
    }

    auto *svgData = reinterpret_cast<SvgData *>(svgPtr);
    delete
            svgData;

// Terminate ThorVG engine
    tvg::Initializer::term(tvg::CanvasEngine::Sw);

    LOGD("SVG destroyed and ThorVG terminated");
}
