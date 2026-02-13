# ThorVG Android - Quick Start

## Prerequisites

- Android Studio (latest)
- Android NDK (via SDK Manager)
- Meson: `brew install meson ninja` (macOS) or `pip install meson ninja`
- Git

## Build Steps

### 1. Clone Project

```bash
git clone git@github.com:ddx533534/thorvg.android.git
cd thorvg.android
```

### 2. Get ThorVG Source (Required!)

```bash
git clone https://github.com/thorvg/thorvg.git
cd thorvg
git checkout e15069de7afcc5e853edf1561e69d9b8383e2c6c
cd ..
```

**Verify**: Check that `thorvg/meson.build` exists.

### 3. Build ThorVG Library

```bash
# For ARM64 devices
./gradlew native:setupCrossBuild -Pabi=1
./build_libthorvg.sh
 ./copy_libthorvg.sh 1

# For x86_64 emulator (optional)
./gradlew native:setupCrossBuild -Pabi=2
./build_libthorvg.sh
 ./copy_libthorvg.sh 2
```

**Verify**: Check `thorvg/lib/arm64-v8a/libthorvg.a` exists (~500KB).

### 4. Build Project

```bash
./gradlew clean
./gradlew :sample:assembleDebug
```

### 5. Install

```bash
./gradlew :sample:installDebug
# Or run from Android Studio
```

## Project Structure

```
thorvg.android/
├── native/          # C++ (lottie + svg JNI)
│   └── src/main/cpp/
│       ├── lottie/  # Lottie JNI
│       ├── svg/     # SVG JNI
│       └── CMakeLists.txt
├── lottie/          # Lottie Kotlin API
├── svg/             # SVG Kotlin API
├── sample/          # Demo app
└── thorvg/          # ThorVG source (external)
```

## Usage Examples

### SVG

```kotlin
// XML
<org.thorvg.svg.SvgImageView
    android:id="@+id/svg_view"
    android:layout_width="200dp"
    android:layout_height="200dp" />

// Kotlin
svgImageView.setSvgResource(R.raw.icon)
svgImageView.setSvgString("<svg>...</svg>")
```

### Lottie

```kotlin
// XML
<org.thorvg.lottie.LottieAnimationView
    android:id="@+id/lottie_view"
    android:layout_width="200dp"
    android:layout_height="200dp" />

// Kotlin
lottieView.playAnimation()
```

## Common Issues

### Q1: `thorvg/meson.build` not found

**Fix**: Run step 2 to clone ThorVG.

### Q2: `libthorvg.a` not found

**Fix**: Run step 3 to build ThorVG library.

### Q3: Meson not found

**Fix**: Install Meson:
```bash
brew install meson ninja  # macOS
pip install meson ninja   # Any OS
```

### Q4: Colors wrong (red/blue swapped)

**Fix**: Already fixed in code. Use `ABGR8888` colorspace.

### Q5: OpenMP errors

**Fix**: Clean and rebuild:
```bash
./gradlew clean
./gradlew :sample:assembleDebug
```

## Architecture

```
sample (App)
  ├─ lottie (Kotlin API)
  │   └─ native (C++ JNI) → ThorVG (libthorvg.a)
  └─ svg (Kotlin API)
      └─ native (C++ JNI) → ThorVG (libthorvg.a)
```

**Key Points**:
- **native** module: Single .so file with all C++ code
- **OpenMP**: Statically linked in native module (no conflicts)
- **ThorVG**: Commit `e15069de`, built with Meson

## Next Steps

- Check `sample/src/main/java/` for examples
- Read `svg/README.md` for API details
- Join [ThorVG Discord](https://discord.gg/n25xj6J6HM)

## License

MIT License - See [LICENSE](LICENSE)
