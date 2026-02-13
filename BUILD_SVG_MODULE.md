# Building the SVG Module

This guide explains how to build the ThorVG SVG module for Android.

## Prerequisites

- Android SDK installed
- NDK installed (version specified in `gradle/libs.versions.toml`)
- Gradle 8.x or higher
- ThorVG core library available in `../thorvg/`

## Quick Start

### Step 1: Cross-Compile ThorVG for Android

The SVG module requires ThorVG to be cross-compiled into a static library for each Android architecture.

#### For ARM64 (arm64-v8a):

```bash
# 1. Setup cross-build configuration
gradle svg:setupCrossBuild -Pabi=1

# 2. Cross-compile ThorVG
./build_libthorvg.sh

# 3. Copy library to svg module
./copy_libthorvg.sh 1
```

#### For x86_64:

```bash
# 1. Setup cross-build configuration
gradle svg:setupCrossBuild -Pabi=2

# 2. Cross-compile ThorVG
./build_libthorvg.sh

# 3. Copy library to svg module
./copy_libthorvg.sh 2
```

**Note**: Repeat for both architectures if you want to support both.

### Step 2: Build the SVG Module

```bash
# Build debug version
gradle :svg:assembleDebug

# Build release version
gradle :svg:assembleRelease

# Build both
gradle :svg:build
```

### Step 3: Build and Run Sample App

```bash
# Install debug version to device/emulator
gradle :sample:installDebug

# Or just build APK
gradle :sample:assembleDebug
```

## What Gets Built

### ThorVG Static Libraries

After cross-compilation:
```
thorvg/lib/
├── arm64-v8a/
│   └── libthorvg.a
└── x86_64/
    └── libthorvg.a
```

### SVG Module Outputs

After building:
```
svg/build/outputs/
├── aar/
│   ├── svg-debug.aar
│   └── svg-release.aar
└── native/
    ├── arm64-v8a/
    │   └── libsvg-libs.so
    └── x86_64/
        └── libsvg-libs.so
```

## Troubleshooting

### Error: "ThorVG library not found"

**Solution**: Make sure you've cross-compiled ThorVG first:
```bash
gradle svg:setupCrossBuild -Pabi=1
./build_libthorvg.sh
./copy_libthorvg.sh 1
```

### Error: "NDK not found"

**Solution**: Install NDK via Android Studio SDK Manager or set `ANDROID_NDK_HOME` environment variable.

### Error: "Meson not found"

**Solution**: Install Meson build system:
```bash
# macOS
brew install meson

# Linux
sudo apt install meson

# Or via pip
pip install meson
```

### CMake Build Errors

**Solution**: Make sure your NDK version matches the one specified in `build.gradle`:
```gradle
ndkVersion libs.versions.ndkVersion.get()
```

## Build Configurations

### Debug Build

```bash
gradle :svg:assembleDebug
```

Features:
- Includes debug symbols
- Logging enabled
- No ProGuard optimization
- Larger binary size

### Release Build

```bash
gradle :svg:assembleRelease
```

Features:
- Optimized code
- ProGuard enabled
- Smaller binary size
- Ready for production

## Testing

### Run Sample App

```bash
# Connect device or start emulator
adb devices

# Install and run
gradle :sample:installDebug
adb shell am start -n org.thorvg.sample/.MainActivity
```

### Navigate to SVG Samples

1. Launch app
2. Click "SVG Samples" button
3. View different SVG rendering examples

## Advanced Options

### Build for Specific Architecture Only

Edit `svg/src/main/cpp/CMakeLists.txt` to target specific ABIs:

```cmake
if (${ANDROID_ABI} STREQUAL "arm64-v8a")
    # Only build for ARM64
    ...
endif()
```

### Adjust Thread Count

Edit `svg-libs.cpp` to change ThorVG thread count:

```cpp
tvg::Initializer::init(tvg::CanvasEngine::Sw, 2)  // Change 2 to desired value
```

### Enable/Disable Logging

Logs are enabled by default. To disable, remove or comment out `LOGD` calls in:
- `svg-libs.cpp`
- `SvgData.cpp`

## Clean Build

To clean all build artifacts:

```bash
# Clean svg module
gradle :svg:clean

# Clean everything
gradle clean

# Also clean ThorVG build
rm -rf thorvg/build
rm -rf thorvg/lib
```

## Continuous Integration

For CI builds, you can script the entire process:

```bash
#!/bin/bash
set -e

echo "Building ThorVG for Android..."

# Build for ARM64
gradle svg:setupCrossBuild -Pabi=1
./build_libthorvg.sh
./copy_libthorvg.sh 1

# Build for x86_64
gradle svg:setupCrossBuild -Pabi=2
./build_libthorvg.sh
./copy_libthorvg.sh 2

echo "Building SVG module..."
gradle :svg:assembleRelease

echo "Building sample app..."
gradle :sample:assembleDebug

echo "Build complete!"
```

## File Sizes

Approximate sizes for reference:

- `libthorvg.a`: ~2-3 MB (per architecture)
- `libsvg-libs.so`: ~3-4 MB (includes ThorVG, per architecture)
- `svg-release.aar`: ~6-8 MB (both architectures)

## Performance Tips

1. **Use Release Builds**: Always use release builds for performance testing
2. **Bitmap Caching**: The module automatically caches bitmaps - no manual optimization needed
3. **Thread Count**: 2 threads is optimal for most devices
4. **SVG Complexity**: Simple SVGs render faster - avoid overly complex paths if possible

## Next Steps

After successful build:

1. Integrate the AAR into your app project
2. Follow the usage examples in `svg/README.md`
3. Check the sample app for implementation patterns
4. Refer to ThorVG documentation for advanced features

## Support

For issues:
- Check ThorVG documentation: https://www.thorvg.org/apis
- Review the sample code in `sample/src/main/java/org/thorvg/sample/SvgActivity.kt`
- Check build logs for error details

---

**Last Updated**: 2026-02-13
**ThorVG Version**: 1.0.0
