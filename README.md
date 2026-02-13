[![Discord](https://img.shields.io/badge/Community-5865f2?style=flat&logo=discord&logoColor=white)](https://discord.gg/n25xj6J6HM)
[![ThorVGPT](https://img.shields.io/badge/ThorVGPT-76A99C?style=flat&logo=openai&logoColor=white)](https://chat.openai.com/g/g-Ht3dYIwLO-thorvgpt)
[![OpenCollective](https://img.shields.io/badge/OpenCollective-84B5FC?style=flat&logo=opencollective&logoColor=white)](https://opencollective.com/thorvg)
[![License](https://img.shields.io/badge/licence-MIT-green.svg?style=flat)](LICENSE)

# ThorVG for Android
<p align="center">
  <img width="800" height="auto" src="https://github.com/thorvg/thorvg.site/blob/main/readme/logo/512/thorvg-banner.png">
</p>
ThorVG Android enhances Lottie animations on Android by bridging the capabilities of ThorVG's graphics engine with Lottie animations.
It simplifies integration with a script that builds ThorVG for Android system(arm64-v8a, x86_64) to includes its binary(libthorvg.a) in your package.
<br />

## Preparation

Please ensure that you have installed the [Android SDK](https://developer.android.com/studio) and [Meson build system](https://mesonbuild.com/Getting-meson.html).

```bash
# Clone this project
git clone https://github.com/ddx533534/thorvg.android.git
cd thorvg.android

# Clone ThorVG dependency (required)
git clone https://github.com/thorvg/thorvg.git
cd thorvg
git checkout e15069de7afcc5e853edf1561e69d9b8383e2c6c
cd ..
```

**Important**: This project depends on ThorVG at specific commit `e15069de7afcc5e853edf1561e69d9b8383e2c6c`. The `thorvg/` directory must exist in the project root before building.

Please refer to [ThorVG](https://github.com/thorvg/thorvg) for detailed information on setting up the ThorVG build environment.

For detailed architecture and build instructions, see:
- [QUICK_START.md](QUICK_START.md) - Quick start guide for new developers
- [NATIVE_MODULE_GUIDE.md](NATIVE_MODULE_GUIDE.md) - Detailed architecture documentation
<br />

## ThorVG Cross-Build

Follow these steps to cross-build ThorVG Android library.

### Build for ARM64 (arm64-v8a)

```bash
# 1. Generate cross-compilation config (use abi=1 for arm64-v8a)
./gradlew native:setupCrossBuild -Pabi=1

# 2. Build ThorVG static library
./build_libthorvg.sh
./copy_libthorvg.sh 1
```

### Build for x86_64 (Emulator)

```bash
# 1. Generate cross-compilation config (use abi=3 for x86_64)
./gradlew native:setupCrossBuild -Pabi=2

# 2. Build ThorVG static library
./build_libthorvg.sh
./copy_libthorvg.sh 2
```

The generated `libthorvg.a` will be automatically placed in `thorvg/lib/{ABI}/` directory.

## ThorVG-Android Build

Build and package the thorvg-android project.
