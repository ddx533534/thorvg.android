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

package org.thorvg.sample

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.thorvg.svg.SvgImageView

/**
 * Sample activity demonstrating SVG rendering with ThorVG.
 */
class SvgActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create layout programmatically
        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // Title
        layout.addView(TextView(this).apply {
            text = "ThorVG SVG Samples"
            textSize = 24f
            setPadding(0, 0, 0, 32)
        })

        // Example 1: Load from raw resource
        layout.addView(TextView(this).apply {
            text = "1. SVG from Raw Resource:"
            textSize = 18f
            setPadding(0, 16, 0, 16)
        })

        layout.addView(SvgImageView(this).apply {
            setSvgResource(R.raw.sample_icon)
            layoutParams = LinearLayout.LayoutParams(300, 300)
        })

        // Example 2: Load from string
        layout.addView(TextView(this).apply {
            text = "2. SVG from String:"
            textSize = 18f
            setPadding(0, 32, 0, 16)
        })

        val svgString = """
            <svg width="200" height="200" xmlns="http://www.w3.org/2000/svg">
              <rect x="10" y="10" width="180" height="180" rx="20" fill="#2196F3"/>
              <circle cx="100" cy="100" r="50" fill="#FFF"/>
              <text x="100" y="110" font-family="Arial" font-size="30" fill="#2196F3" text-anchor="middle">SVG</text>
            </svg>
        """.trimIndent()

        layout.addView(SvgImageView(this).apply {
            setSvgString(svgString)
            layoutParams = LinearLayout.LayoutParams(300, 300)
        })

        // Example 3: Another inline SVG
        layout.addView(TextView(this).apply {
            text = "3. Simple Shape:"
            textSize = 18f
            setPadding(0, 32, 0, 16)
        })

        val simpleShape = """
            <svg width="200" height="200" xmlns="http://www.w3.org/2000/svg">
              <polygon points="100,10 40,180 190,60 10,60 160,180"
                       fill="#FF5722" stroke="#D84315" stroke-width="3"/>
            </svg>
        """.trimIndent()

        layout.addView(SvgImageView(this).apply {
            setSvgString(simpleShape)
            layoutParams = LinearLayout.LayoutParams(300, 300)
        })

        scrollView.addView(layout)
        setContentView(scrollView)
    }
}
