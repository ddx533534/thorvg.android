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

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.annotation.RawRes

/**
 * An ImageView that renders SVG content using the ThorVG engine.
 *
 * This view extends AppCompatImageView and provides convenient methods
 * to load SVG from various sources:
 * - Raw resources
 * - File paths
 * - String content
 *
 * Usage in XML:
 * ```xml
 * <org.thorvg.svg.SvgImageView
 *     android:layout_width="wrap_content"
 *     android:layout_height="wrap_content"
 *     app:svgRes="@raw/icon" />
 * ```
 *
 * Usage in code:
 * ```kotlin
 * svgImageView.setSvgResource(R.raw.icon)
 * // or
 * svgImageView.setSvgPath("/sdcard/icon.svg")
 * // or
 * svgImageView.setSvgString("<svg>...</svg>")
 * ```
 */
class SvgImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ImageView(context, attrs, defStyleAttr) {

    private var svgDrawable: SvgDrawable? = null

    companion object {
        private const val TAG = "SvgImageView"
    }

    init {
        // Parse XML attributes
        val a = context.obtainStyledAttributes(attrs, R.styleable.SvgImageView, defStyleAttr, 0)
        try {
            val resId = a.getResourceId(R.styleable.SvgImageView_svgRes, -1)
            if (resId != -1) {
                setSvgResource(resId)
            }
        } finally {
            a.recycle()
        }
    }

    /**
     * Load SVG from a raw resource.
     *
     * @param resId Raw resource ID containing SVG content
     */
    fun setSvgResource(@RawRes resId: Int) {
        releaseSvg()
        svgDrawable = SvgDrawable.fromResource(context.resources, resId)
        if (svgDrawable != null) {
            setImageDrawable(svgDrawable)
        } else {
            Log.e(TAG, "Failed to load SVG from resource: $resId")
        }
    }

    /**
     * Load SVG from a file path.
     *
     * @param path Absolute file path to SVG file
     */
    fun setSvgPath(path: String) {
        releaseSvg()
        svgDrawable = SvgDrawable.fromPath(path)
        if (svgDrawable != null) {
            setImageDrawable(svgDrawable)
        } else {
            Log.e(TAG, "Failed to load SVG from path: $path")
        }
    }

    /**
     * Load SVG from string content.
     *
     * @param svgContent SVG XML content as string
     */
    fun setSvgString(svgContent: String) {
        releaseSvg()
        svgDrawable = SvgDrawable.fromString(svgContent)
        if (svgDrawable != null) {
            setImageDrawable(svgDrawable)
        } else {
            Log.e(TAG, "Failed to load SVG from string")
        }
    }

    /**
     * Release the current SVG drawable and its native resources.
     */
    private fun releaseSvg() {
        svgDrawable?.release()
        svgDrawable = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        releaseSvg()
    }
}
