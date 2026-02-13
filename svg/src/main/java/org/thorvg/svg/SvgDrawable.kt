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

package org.thorvg.svg

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.annotation.RawRes
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * A Drawable that renders SVG content using the ThorVG engine.
 *
 * This drawable supports loading SVG from:
 * - File paths
 * - String content
 * - Raw resources
 *
 * The SVG is rendered to a bitmap at the drawable's current bounds size.
 */
class SvgDrawable private constructor() : Drawable() {

    private var nativePtr: Long = 0
    private var bitmap: Bitmap? = null
    private var intrinsicWidth: Int = 0
    private var intrinsicHeight: Int = 0
    private var isDirty = true

    companion object {
        private const val TAG = "SvgDrawable"

        init {
            System.loadLibrary("svg-libs")
        }

        /**
         * Create SvgDrawable from a raw resource.
         *
         * @param resources Android Resources instance
         * @param resId Raw resource ID containing SVG content
         * @return SvgDrawable instance, or null if loading failed
         */
        @JvmStatic
        fun fromResource(resources: Resources, @RawRes resId: Int): SvgDrawable? {
            return try {
                val svgContent = resources.openRawResource(resId).use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        reader.readText()
                    }
                }
                fromString(svgContent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load SVG from resource: $resId", e)
                null
            }
        }

        /**
         * Create SvgDrawable from a file path.
         *
         * @param path Absolute file path to SVG file
         * @return SvgDrawable instance, or null if loading failed
         */
        @JvmStatic
        fun fromPath(path: String): SvgDrawable? {
            val drawable = SvgDrawable()
            val outSize = FloatArray(2)
            drawable.nativePtr = nLoadSvgFromPath(path, outSize)

            if (drawable.nativePtr == 0L) {
                Log.e(TAG, "Failed to load SVG from path: $path")
                return null
            }

            drawable.intrinsicWidth = outSize[0].toInt()
            drawable.intrinsicHeight = outSize[1].toInt()
            return drawable
        }

        /**
         * Create SvgDrawable from SVG string content.
         *
         * @param svgContent SVG XML content as string
         * @return SvgDrawable instance, or null if loading failed
         */
        @JvmStatic
        fun fromString(svgContent: String): SvgDrawable? {
            val drawable = SvgDrawable()
            val outSize = FloatArray(2)
            drawable.nativePtr = nLoadSvgFromString(svgContent, outSize)

            if (drawable.nativePtr == 0L) {
                Log.e(TAG, "Failed to load SVG from string")
                return null
            }

            drawable.intrinsicWidth = outSize[0].toInt()
            drawable.intrinsicHeight = outSize[1].toInt()
            return drawable
        }

        // JNI methods
        @JvmStatic
        private external fun nLoadSvgFromPath(path: String, outSize: FloatArray): Long

        @JvmStatic
        private external fun nLoadSvgFromString(content: String, outSize: FloatArray): Long

        @JvmStatic
        private external fun nSetSvgSize(svgPtr: Long, bitmap: Bitmap?, width: Float, height: Float)

        @JvmStatic
        private external fun nDrawSvg(svgPtr: Long, bitmap: Bitmap)

        @JvmStatic
        private external fun nDestroySvg(svgPtr: Long)
    }

    override fun draw(canvas: Canvas) {
        if (nativePtr == 0L) {
            Log.w(TAG, "Cannot draw: SVG not loaded")
            return
        }

        // Use SVG's intrinsic size, not the view bounds
        if (intrinsicWidth <= 0 || intrinsicHeight <= 0) {
            Log.w(TAG, "Invalid SVG size: ${intrinsicWidth}x${intrinsicHeight}")
            return
        }
        Log.d(TAG,"image view bounds: ${bounds.width()} - ${bounds.height()}, svg bounds: $intrinsicWidth, $intrinsicHeight") ;

        // Create or recreate bitmap using SVG's intrinsic size
        val currentBitmap = bitmap
        if (currentBitmap == null ||
            currentBitmap.width != intrinsicWidth ||
            currentBitmap.height != intrinsicHeight) {

            currentBitmap?.recycle()

            val newBitmap = Bitmap.createBitmap(
                intrinsicWidth,
                intrinsicHeight,
                Bitmap.Config.ARGB_8888
            )
            bitmap = newBitmap

            nSetSvgSize(nativePtr, newBitmap, intrinsicWidth.toFloat(), intrinsicHeight.toFloat())
            isDirty = true
        }

        // Render if dirty
        var cur_time = System.currentTimeMillis();
        if (isDirty) {
            bitmap?.let {
                nDrawSvg(nativePtr, it)
                isDirty = false
            }
        }
        Log.d(TAG, "绘制耗时: ${System.currentTimeMillis() - cur_time}")
        // Draw bitmap to canvas (ImageView will handle scaling via scaleType)
        bitmap?.let {
            val bounds = bounds
            canvas.drawBitmap(it, null, bounds, null)
        }
    }

    override fun getIntrinsicWidth(): Int = intrinsicWidth

    override fun getIntrinsicHeight(): Int = intrinsicHeight

    override fun setAlpha(alpha: Int) {
        // Not implemented - could be added if needed
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        // Not implemented - could be added if needed
    }

    @Deprecated("Deprecated in Drawable")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        super.onBoundsChange(bounds)
        // Don't mark as dirty - we render at intrinsic size, ImageView handles scaling
    }

    /**
     * Release native resources.
     * Should be called when the drawable is no longer needed.
     */
    fun release() {
        if (nativePtr != 0L) {
            nDestroySvg(nativePtr)
            nativePtr = 0
        }
        bitmap?.recycle()
        bitmap = null
    }
}
