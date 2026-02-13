# ThorVG SVG Module for Android

## Overview

This module provides a simple way to render SVG files on Android using the ThorVG vector graphics engine. It offers an ImageView-style API for static SVG rendering with support for multiple data sources (files, resources, strings).

### Key Features

- **Static SVG Rendering**: High-performance vector graphics rendering via ThorVG
- **Simple API**: ImageView-compatible interface with `SvgImageView` and `SvgDrawable`
- **Multiple Sources**: Load from file paths, raw resources, or string content
- **Native Resolution**: Renders at SVG's intrinsic size with automatic scaling support
- **DPI Adaptation**: Full support for Android's DPI system via ImageView's `scaleType`
- **Lightweight**: Small binary footprint (~150KB for ThorVG)

## Architecture

The module uses a three-layer architecture:

```
┌──────────────────────────────��──────────────────┐
│  Kotlin Layer (Public API)                      │
│  - SvgImageView.kt                              │
│  - SvgDrawable.kt                               │
└────────────────┬────────────────────────────────┘
                 │ JNI calls
┌────────────────▼────────────────────────────────┐
│  JNI Bridge Layer (C++)                         │
│  - svg-libs.cpp                                 │
│  - Converts Java objects to native types       │
└────────────────┬────────────────────────────────┘
                 │ ThorVG API calls
┌────────────────▼────────────────────────────────┐
│  ThorVG Engine Layer (C++)                      │
│  - SvgData.cpp                                  │
│  - Picture, SwCanvas, Initializer               │
└─────────────────────────────────────────────────┘
```

## Complete Call Chain

### 1. Loading SVG from String

**User Code (Kotlin)**
```kotlin
svgImageView.setSvgString(svgContent)
```

**↓ SvgImageView.kt**
```kotlin
fun setSvgString(svgContent: String) {
    val drawable = SvgDrawable.fromString(svgContent)
    setImageDrawable(drawable)
}
```

**↓ SvgDrawable.kt**
```kotlin
companion object {
    fun fromString(svgContent: String): SvgDrawable? {
        val drawable = SvgDrawable()
        val outSize = FloatArray(2)
        // JNI call to native layer
        drawable.nativePtr = nLoadSvgFromString(svgContent, outSize)

        if (drawable.nativePtr == 0L) return null

        drawable.intrinsicWidth = outSize[0].toInt()
        drawable.intrinsicHeight = outSize[1].toInt()
        return drawable
    }

    private external fun nLoadSvgFromString(content: String, outSize: FloatArray): Long
}
```

**↓ svg-libs.cpp (JNI Bridge)**
```cpp
JNIEXPORT jlong JNICALL
Java_org_thorvg_svg_SvgDrawable_nLoadSvgFromString(
    JNIEnv *env, jclass clazz, jstring content, jfloatArray outSize) {

    // Initialize ThorVG (first time only)
    static bool initialized = false;
    if (!initialized) {
        tvg::Initializer::init(tvg::CanvasEngine::Sw, 2);
        initialized = true;
    }

    // Convert Java string to C++ string
    const char *contentStr = env->GetStringUTFChars(content, nullptr);
    const size_t contentLen = env->GetStringUTFLength(content);

    // Create SvgData wrapper
    auto svgData = std::make_unique<SvgData>();

    // Load SVG data via ThorVG
    auto result = svgData->load(contentStr, contentLen);
    env->ReleaseStringUTFChars(content, contentStr);

    if (result != tvg::Result::Success) {
        return 0;
    }

    // Get SVG size
    float width, height;
    svgData->size(width, height);

    // Return size to Java
    jfloat sizeArray[2] = {width, height};
    env->SetFloatArrayRegion(outSize, 0, 2, sizeArray);

    // Return pointer as long
    return reinterpret_cast<jlong>(svgData.release());
}
```

**↓ SvgData.cpp (ThorVG Wrapper)**
```cpp
tvg::Result SvgData::load(const char *data, size_t size) {
    // Create ThorVG Picture (SVG container)
    picture = tvg::Picture::gen();
    if (!picture) {
        return tvg::Result::InsufficientCondition;
    }

    // Load SVG content into Picture
    // Parameters: data, length, mimetype, copy, override
    auto result = picture->load(data, size, "svg", nullptr, true);
    return result;
}

bool SvgData::size(float &w, float &h) {
    if (!picture) return false;

    // Get SVG's intrinsic dimensions
    picture->size(&w, &h);
    return true;
}
```

### 2. Rendering the SVG

**Android Framework**
```
View.invalidate() → View.onDraw() → Drawable.draw()
```

**↓ SvgDrawable.kt**
```kotlin
override fun draw(canvas: Canvas) {
    if (nativePtr == 0L) return
    if (intrinsicWidth <= 0 || intrinsicHeight <= 0) return

    // Create bitmap at SVG's intrinsic size
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

        // JNI call to set size
        nSetSvgSize(nativePtr, newBitmap, intrinsicWidth.toFloat(), intrinsicHeight.toFloat())
        isDirty = true
    }

    // Render if needed
    if (isDirty) {
        bitmap?.let {
            // JNI call to render
            nDrawSvg(nativePtr, it)
            isDirty = false
        }
    }

    // Draw bitmap to canvas (ImageView handles scaling via scaleType)
    bitmap?.let {
        canvas.drawBitmap(it, null, bounds, null)
    }
}
```

**↓ svg-libs.cpp (JNI Bridge)**
```cpp
JNIEXPORT void JNICALL
Java_org_thorvg_svg_SvgDrawable_nSetSvgSize(
    JNIEnv *env, jclass clazz, jlong svgPtr, jobject bitmap, jfloat width, jfloat height) {

    auto svgData = reinterpret_cast<SvgData *>(svgPtr);
    if (!svgData) return;

    // Get native bitmap info
    AndroidBitmapInfo info;
    AndroidBitmap_getInfo(env, bitmap, &info);

    // Lock bitmap pixels
    void *pixels;
    AndroidBitmap_lockPixels(env, bitmap, &pixels);

    // Set ThorVG canvas target
    svgData->setCanvasTarget(
        static_cast<uint32_t *>(pixels),
        info.width, info.height, info.stride / 4
    );

    AndroidBitmap_unlockPixels(env, bitmap);

    // Set SVG size
    svgData->setSize(width, height);
}

JNIEXPORT void JNICALL
Java_org_thorvg_svg_SvgDrawable_nDrawSvg(
    JNIEnv *env, jclass clazz, jlong svgPtr, jobject bitmap) {

    auto svgData = reinterpret_cast<SvgData *>(svgPtr);
    if (!svgData) return;

    // Lock bitmap pixels
    void *pixels;
    AndroidBitmap_lockPixels(env, bitmap, &pixels);

    // Update canvas target and render
    AndroidBitmapInfo info;
    AndroidBitmap_getInfo(env, bitmap, &info);
    svgData->setCanvasTarget(
        static_cast<uint32_t *>(pixels),
        info.width, info.height, info.stride / 4
    );

    // Render SVG to bitmap
    svgData->draw();

    AndroidBitmap_unlockPixels(env, bitmap);
}
```

**↓ SvgData.cpp (ThorVG Rendering)**
```cpp
void SvgData::setCanvasTarget(uint32_t *buffer, uint32_t width, uint32_t height, uint32_t stride) {
    if (!canvas) {
        canvas = tvg::SwCanvas::gen();
    }

    // Set rendering target (ARGB8888 bitmap)
    canvas->target(buffer, width, stride, height, tvg::SwCanvas::Colorspace::ARGB8888);
}

void SvgData::setSize(float width, float height) {
    if (!picture) return;

    // Scale picture to target size
    picture->size(width, height);
}

bool SvgData::draw() {
    if (!canvas || !picture) return false;

    // Clear canvas
    canvas->clear();

    // Add picture to canvas (transfers ownership temporarily)
    canvas->push(tvg::cast(picture.get()));

    // Render scene
    if (canvas->draw() != tvg::Result::Success) {
        return false;
    }

    // Flush rendering to bitmap
    if (canvas->sync() != tvg::Result::Success) {
        return false;
    }

    return true;
}
```

### 3. Memory Cleanup

**↓ SvgDrawable.kt**
```kotlin
fun release() {
    if (nativePtr != 0L) {
        nDestroySvg(nativePtr)
        nativePtr = 0
    }
    bitmap?.recycle()
    bitmap = null
}
```

**↓ svg-libs.cpp**
```cpp
JNIEXPORT void JNICALL
Java_org_thorvg_svg_SvgDrawable_nDestroySvg(JNIEnv *env, jclass clazz, jlong svgPtr) {
    auto svgData = reinterpret_cast<SvgData *>(svgPtr);
    delete svgData;  // Triggers ~SvgData()
}
```

**↓ SvgData.cpp**
```cpp
SvgData::~SvgData() {
    // unique_ptr automatically destroys picture and canvas
    picture.reset();
    canvas.reset();
}
```

## Usage Tutorial

### 1. Add Module Dependency

In your app's `build.gradle`:

```gradle
dependencies {
    implementation project(':svg')
}
```

### 2. Use SvgImageView in Layout

```xml
<org.thorvg.svg.SvgImageView
    android:id="@+id/svg_view"
    android:layout_width="200dp"
    android:layout_height="200dp"
    android:scaleType="centerInside"
    android:background="#f0f0f0" />
```

### 3. Load SVG in Activity/Fragment

#### Load from Raw Resource

```kotlin
val svgView = findViewById<SvgImageView>(R.id.svg_view)
svgView.setSvgResource(R.raw.my_svg)
```

#### Load from File Path

```kotlin
svgView.setSvgPath("/sdcard/Download/image.svg")
```

#### Load from String

```kotlin
val svgContent = """
    <svg width="100" height="100" xmlns="http://www.w3.org/2000/svg">
        <circle cx="50" cy="50" r="40" fill="red" />
    </svg>
""".trimIndent()

svgView.setSvgString(svgContent)
```

### 4. Use SvgDrawable Directly

For more control, use `SvgDrawable` directly:

```kotlin
val drawable = SvgDrawable.fromString(svgContent)
if (drawable != null) {
    imageView.setImageDrawable(drawable)

    // Remember to release when done
    // drawable.release()
}
```

### 5. Handle ScaleType

`SvgImageView` inherits all `ImageView` scale types:

```kotlin
svgView.scaleType = ImageView.ScaleType.CENTER_INSIDE  // Fit inside, maintain aspect
svgView.scaleType = ImageView.ScaleType.CENTER_CROP     // Fill view, crop if needed
svgView.scaleType = ImageView.ScaleType.FIT_XY          // Stretch to fill
```

## API Reference

### SvgImageView

Extends `androidx.appcompat.widget.AppCompatImageView`

#### Methods

- `setSvgResource(@RawRes resId: Int)`: Load SVG from raw resource
- `setSvgPath(path: String)`: Load SVG from file path
- `setSvgString(svgContent: String)`: Load SVG from string content

### SvgDrawable

Extends `android.graphics.drawable.Drawable`

#### Static Factory Methods

- `fromResource(resources: Resources, @RawRes resId: Int): SvgDrawable?`
- `fromPath(path: String): SvgDrawable?`
- `fromString(svgContent: String): SvgDrawable?`

#### Instance Methods

- `release()`: Release native resources (call when done)

#### Properties

- `intrinsicWidth: Int`: SVG's native width in pixels
- `intrinsicHeight: Int`: SVG's native height in pixels

## Build Instructions

### Prerequisites

- Android Studio (latest version recommended)
- Android NDK (configured in Android Studio)
- Meson build system: `brew install meson` (macOS) or equivalent

### Building ThorVG Library

The ThorVG library must be built for Android architectures before building the module:

```bash
cd thorvg.android

# Build for arm64-v8a (64-bit ARM)
./gradlew svg:setupCrossBuild -Pabi=1
./build_libthorvg.sh

# Build for x86_64 (64-bit emulator)
./gradlew svg:setupCrossBuild -Pabi=3
./build_libthorvg.sh
```

This script:
1. Configures cross-compilation for the target Android ABI
2. Builds ThorVG with SVG loader enabled, OpenMP disabled
3. Outputs static library to `thorvg/lib/{ABI}/libthorvg.a`

### Building the Module

```bash
./gradlew :svg:assembleDebug
```

The module will:
1. Locate pre-built ThorVG static libraries
2. Compile C++ JNI bridge code
3. Link everything into `libsvg-libs.so`
4. Package into AAR

## Technical Details

### Memory Management

- **Kotlin Layer**: Uses standard object lifecycle, Bitmap recycling
- **JNI Layer**: Returns raw pointers cast to `jlong`, caller must call destroy
- **C++ Layer**: Uses `std::unique_ptr` for automatic cleanup of ThorVG objects

### Threading

- ThorVG initialization uses 2 threads for software rendering
- OpenMP is disabled to avoid runtime conflicts between modules
- Rendering happens on the UI thread (Android's standard drawing model)

### Rendering Pipeline

1. **Load Phase**: SVG parsed into `tvg::Picture` (vector representation)
2. **Size Phase**: Canvas target set to match Android Bitmap
3. **Draw Phase**: ThorVG rasterizes vectors into ARGB8888 bitmap
4. **Display Phase**: Bitmap drawn to Canvas with hardware acceleration

### Color Format

- ThorVG renders to `ARGB8888` format (8 bits per channel, pre-multiplied alpha)
- Matches Android's `Bitmap.Config.ARGB_8888`
- Full transparency and color accuracy support

## Troubleshooting

### Issue: "Failed to load SVG" (Result code 5)

**Cause**: SVG loader not enabled in ThorVG build

**Solution**: Ensure `build_libthorvg.sh` has `-Dloaders="svg"`:
```bash
meson setup build -Dloaders="svg" ...
```

### Issue: Crash on startup with OpenMP error

**Cause**: Multiple modules statically linking OpenMP

**Solution**: OpenMP is disabled in ThorVG build via `-Dextra=""`. Do not re-enable it.

### Issue: SVG appears blurry or pixelated

**Cause**: SVG rendered at low resolution then scaled up

**Solution**: The module automatically uses SVG's intrinsic size. Check your SVG has proper width/height attributes:
```xml
<svg width="200" height="200" ...>
```

### Issue: Build fails with "Cannot find libthorvg.a"

**Cause**: ThorVG library not built for current ABI

**Solution**: Run the build script for your target ABI:
```bash
./gradlew svg:setupCrossBuild -Pabi=1  # arm64-v8a
./build_libthorvg.sh
```

### Issue: SVG not displaying in layout preview

**Cause**: Android Studio layout preview doesn't execute JNI code

**Solution**: This is expected. Test on a device or emulator.

## Performance Characteristics

- **Initial Load**: ~5-20ms for typical SVG (100KB, moderate complexity)
- **Rendering**: ~2-10ms per frame (depends on SVG complexity)
- **Memory**: ~2x SVG intrinsic size in pixels (ARGB = 4 bytes/pixel)
- **ThorVG Library**: ~150KB binary footprint

## Limitations

- **Static Only**: No animation support (use ThorVG Lottie module for animations)
- **No Interactivity**: Cannot intercept touch events on SVG elements
- **No Dynamic Updates**: Cannot change SVG properties after loading
- **Software Rendering**: Uses CPU rasterization (ThorVG's Sw backend)

## Future Enhancements

- [ ] Caching mechanism for frequently used SVGs
- [ ] Async loading with placeholder support
- [ ] Streaming for very large SVG files
- [ ] GPU rendering backend (OpenGL ES)
- [ ] SVG property animations
- [ ] Interactive event handling

## License

MIT License - See project root LICENSE file

Copyright (c) 2025 - 2026 ThorVG project
