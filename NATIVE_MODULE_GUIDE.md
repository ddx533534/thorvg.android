# Native 模块架构说明

## 概述

为了解决 OpenMP 冲突问题并简化 C++ 代码管理，我们创建了一个统一的 `native` 模块，将所有 C++ 代码编译到一个共享库 `libthorvg-native.so` 中。

## ThorVG 依赖说明

### 依赖的 ThorVG 版本

本项目依赖 ThorVG 的特定 commit：

```
Repository: https://github.com/thorvg/thorvg.git
Commit: e15069de7afcc5e853edf1561e69d9b8383e2c6c
Version: ~1.0.0
```

### 为什么依赖特定 commit？

- **API 稳定性**: ThorVG 1.0.0 的 API 与之前版本有较大变化（使用 `std::unique_ptr`）
- **功能支持**: 该版本完整支持 SVG 和 Lottie 加载器
- **已验证**: 该 commit 已经过充分测试，确保兼容性

### 获取 ThorVG 源码

在项目根目录（`thorvg.android/`）下执行：

```bash
# 方法 1: 克隆整个仓库并切换到指定 commit
git clone https://github.com/thorvg/thorvg.git
cd thorvg
git checkout e15069de7afcc5e853edf1561e69d9b8383e2c6c
cd ..

# 方法 2: 作为 submodule 添加（推荐）
git submodule add https://github.com/thorvg/thorvg.git thorvg
cd thorvg
git checkout e15069de7afcc5e853edf1561e69d9b8383e2c6c
cd ..
git add thorvg
git commit -m "Add thorvg submodule at commit e15069de"

# 方法 3: 如果已经是 submodule，初始化并更新
git submodule update --init --recursive
cd thorvg
git checkout e15069de7afcc5e853edf1561e69d9b8383e2c6c
cd ..
```

### 目录结构要求

ThorVG 必须位于项目根目录下：

```
thorvg.android/
├── thorvg/                    # ThorVG 源码目录（必需）
│   ├── src/                   # ThorVG 源代码
│   ├── inc/                   # ThorVG 头文件
│   ├── cross/                 # 交叉编译配置
│   ├── lib/                   # 构建后的静态库（生成）
│   │   ├── arm64-v8a/
│   │   │   └── libthorvg.a
│   │   └── x86_64/
│   │       └── libthorvg.a
│   └── meson.build
├── native/
├── lottie/
├── svg/
└── sample/
```

### ThorVG 的构建配置

ThorVG 需要使用 Meson 构建系统编译，配置要求：

```bash
meson setup build \
  -Dloaders="svg, lottie" \    # 启用 SVG 和 Lottie 加载器
  --cross-file /tmp/android_cross.txt \  # Android 交叉编译配置
  -Ddefault_library=static      # 构建静态库
```

详细构建步骤见下文"构建流程"部分。

## 架构设计

```
┌─────────────────────────────────────────────────────────┐
│  App Module (sample)                                    │
│  - 依赖 lottie 和 svg 模块                               │
└────────────────┬──────────────────┬─────────────────────┘
                 │                  │
                 ▼                  ▼
┌────────────────────────┐  ┌────────────────────────────┐
│  Lottie Module         │  │  SVG Module                │
│  - Kotlin API          │  │  - Kotlin API              │
│  - 依赖 native 模块     │  │  - 依赖 native 模块         │
└────────────┬───────────┘  └────────────┬───────────────┘
             │                           │
             │      ┌────────────────────┘
             │      │
             ▼      ▼
┌─────────────────────────────────────────────────────────┐
│  Native Module                                          │
│  - 编译所有 C++ 代码到 libthorvg-native.so              │
│  - lottie-libs.cpp, LottieData.cpp                     │
│  - svg-libs.cpp, SvgData.cpp                           │
│  - 链接 libthorvg.a                                    │
│  - 静态链接 OpenMP (-static-openmp)                     │
└───────────────────────────────────���─────────────────────┘
```

### ThorVG 依赖层次

Native 模块链接 ThorVG 静态库：

```
Native Module (libthorvg-native.so)
  ↓ 静态链接
ThorVG Library (libthorvg.a)
  ├─ 来源: thorvg/ 目录（项目根目录下）
  ├─ Commit: e15069de7afcc5e853edf1561e69d9b8383e2c6c
  ├─ 构建工具: Meson + Ninja
  ├─ 渲染引擎: SwCanvas (软件渲染)
  └─ 支持格式: SVG, Lottie
```

完整依赖链路：

```
App (sample.apk)
  ↓ 依赖
Lottie/SVG Module (.aar)
  ↓ 依赖
Native Module (libthorvg-native.so)
  ↓ 静态链接
ThorVG Library (libthorvg.a)
  ↓ 链接
NDK Runtime (libc++_static.a, OpenMP)
```

## 模块职责

### Native 模块 (`:native`)
- **位置**: `native/`
- **职责**:
  - 编译所有 JNI C++ 代码
  - 链接 ThorVG 静态库
  - 静态链接 OpenMP（避免运行时冲突）
  - 生成统一的 `libthorvg-native.so`
- **输出**: `libthorvg-native.so` (ARM64/x86_64)
- **不包含**: Kotlin/Java 代码（纯 native 构建）

### SVG 模块 (`:svg`)
- **位置**: `svg/`
- **职责**:
  - 提供 SVG 渲染的 Kotlin API (`SvgImageView`, `SvgDrawable`)
  - 定义 JNI 接口
  - 依赖 `:native` 获取 native 库
- **C++ 代码位置**: `svg/src/main/cpp/` (被 native 模块编译)
- **加载库**: `System.loadLibrary("thorvg-native")`

### Lottie 模块 (`:lottie`)
- **位置**: `lottie/`
- **职责**:
  - 提供 Lottie 动画的 Kotlin API (`LottieAnimationView`, `LottieDrawable`)
  - 定义 JNI 接口
  - 依赖 `:native` 获取 native 库
- **C++ 代码位置**: `lottie/src/main/cpp/` (被 native 模块编译)
- **加载库**: `System.loadLibrary("thorvg-native")`

## 依赖关系

```
sample
  ├─ lottie
  │   └─ native
  └─ svg
      └─ native
```

## 构建流程

### 0. 准备 ThorVG 源码（首次构建必需）

**重要**: 必须先获取 ThorVG 源码并切换到指定 commit，否则构建会失败。

```bash
# 在项目根目录（thorvg.android/）执行

# 如果 thorvg 目录不存在，克隆仓库
git clone https://github.com/thorvg/thorvg.git

# 进入 thorvg 目录
cd thorvg

# 切换到指定 commit（必需）
git checkout e15069de7afcc5e853edf1561e69d9b8383e2c6c

# 返回项目根目录
cd ..
```

**验证**: 确保 `thorvg/` 目录存在且包含以下文件：
- `thorvg/meson.build`
- `thorvg/src/`
- `thorvg/inc/`
- `thorvg/cross/`

### 1. 构建 ThorVG 静态库

**前提条件**:
- 已完成步骤 0（获取 ThorVG 源码）
- 已安装 Meson 构建系统：`brew install meson`（macOS）
- Android NDK 已配置

```bash
# 为 arm64-v8a 构建
./gradlew native:setupCrossBuild -Pabi=1
./build_libthorvg.sh

# 为 x86_64 构建
./gradlew native:setupCrossBuild -Pabi=3
./build_libthorvg.sh
```

**说明**:
- `setupCrossBuild` 生成交叉编译配置文件 `/tmp/android_cross.txt`
- `build_libthorvg.sh` 使用 Meson 编译 ThorVG，启用 SVG 和 Lottie 加载器

这会生成：
- `thorvg/lib/arm64-v8a/libthorvg.a`
- `thorvg/lib/x86_64/libthorvg.a`

### 2. 构建 Native 模块

```bash
./gradlew :native:assembleDebug
```

这会：
1. 读取 `lottie/src/main/cpp/` 中的代码
2. 读取 `svg/src/main/cpp/` 中的代码
3. 链接 `libthorvg.a`
4. 静态链接 OpenMP
5. 生成 `libthorvg-native.so`

### 3. 构建 App

```bash
./gradlew :sample:assembleDebug
```

这会：
1. 构建 lottie 模块（Kotlin 代码）
2. 构建 svg 模块（Kotlin 代码）
3. 自动包含 native 模块的 `.so` 文件
4. 打包到 APK

## 文件结构

```
thorvg.android/
├── native/                          # Native 模块（新增）
│   ├── build.gradle                 # Native 模块构建配置
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── cpp/
│           └── CMakeLists.txt       # 编译所有 C++ 代码
│
├── lottie/
│   ├── build.gradle                 # 禁用了 native 构建
│   └── src/main/
│       ├── java/                    # Kotlin API
│       └── cpp/                     # C++ 代码（被 native 模块引用）
│           ├── lottie-libs.cpp
│           └── LottieData.cpp
│
├── svg/
│   ├── build.gradle                 # 禁用了 native 构建
│   └── src/main/
│       ├── java/                    # Kotlin API
│       └── cpp/                     # C++ 代码（被 native 模块引用）
│           ├── svg-libs.cpp
│           └── SvgData.cpp
│
├── sample/
│   ├── build.gradle                 # 依赖 lottie 和 svg
│   └── src/main/java/               # 示例代码
│
├── thorvg/                          # ThorVG 源码
│   └── lib/
│       ├── arm64-v8a/libthorvg.a
│       └── x86_64/libthorvg.a
│
├── settings.gradle                  # 包含 :native 模块
└── build_libthorvg.sh              # 构建 ThorVG 脚本
```

## CMakeLists.txt 详解

`native/src/main/cpp/CMakeLists.txt`:

```cmake
# 定义路径
set(THORVG_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../thorvg)
set(LOTTIE_CPP_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../lottie/src/main/cpp)
set(SVG_CPP_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../svg/src/main/cpp)

# 导入 ThorVG 静态库
add_library(lib_thorvg STATIC IMPORTED)
set_target_properties(lib_thorvg PROPERTIES IMPORTED_LOCATION
        ${THORVG_DIR}/lib/${ANDROID_ABI}/libthorvg.a)

# 创建统一的共享库
add_library(thorvg-native SHARED
        # Lottie 源文件
        ${LOTTIE_CPP_DIR}/lottie-libs.cpp
        ${LOTTIE_CPP_DIR}/LottieData.cpp
        # SVG 源文件
        ${SVG_CPP_DIR}/svg-libs.cpp
        ${SVG_CPP_DIR}/SvgData.cpp)

# 包含头文件目录
target_include_directories(thorvg-native PRIVATE
        ${THORVG_DIR}/inc
        ${LOTTIE_CPP_DIR}
        ${SVG_CPP_DIR})

# 链接库
target_link_libraries(thorvg-native
        android
        lib_thorvg
        -ljnigraphics
        -fopenmp           # 动态链接 OpenMP
        -static-openmp     # 静态链接 OpenMP 实现
        log)
```

## OpenMP 处理

- **问题**: 多个模块各自静态链接 OpenMP 会导致符号冲突和运行时崩溃
- **解决方案**: 在统一的 native 模块中静态链接 OpenMP
- **结果**: 整个 APK 只有一个 OpenMP 实现，避免冲突

## 优势

1. **统一管理**: 所有 C++ 代码在一个地方编译
2. **避免冲突**: OpenMP 只被链接一次
3. **简化构建**: lottie 和 svg 模块不需要配置 CMake
4. **便于扩展**: 未来添加新模块只需在 native 模块添加源文件
5. **减小体积**: 只有一个 .so 文件，没有重复的代码

## 调试技巧

### 检查 .so 文件是否正确打包

```bash
unzip -l sample/build/outputs/apk/debug/sample-debug.apk | grep thorvg-native
```

应该看到：
```
lib/arm64-v8a/libthorvg-native.so
lib/x86_64/libthorvg-native.so
```

### 检查符号

```bash
nm -D native/build/intermediates/cmake/debug/obj/arm64-v8a/libthorvg-native.so | grep Java_org_thorvg
```

应该看到所有 JNI 函数：
```
Java_org_thorvg_lottie_LottieDrawable_nCreateLottie
Java_org_thorvg_svg_SvgDrawable_nLoadSvgFromString
...
```

## 常见问题

### Q: 为什么不在每个模块单独构建？
A: 会导致 OpenMP 冲突，每个 .so 都包含 OpenMP 会引起运行时崩溃。

### Q: 如果添加新功能模块怎么办？
A: 在 native 模块的 CMakeLists.txt 中添加新的源文件即可。

### Q: 能否动态链接 OpenMP？
A: 可以，但需要在 APK 中打包 `libomp.so`，会增加复杂性。静态链接更简单可靠。

### Q: 为什么 lottie 和 svg 还保留 cpp 目录？
A: 为了代码组织清晰，每个模块的 C++ 代码仍在各自目录，只是由 native 模块编译。

## 后续可能的优化

1. 将 C++ 代码也移到 native 模块（完全集中管理）
2. 支持更多架构（armeabi-v7a, x86）
3. 提供调试/发布版本的不同优化级别
4. 添加符号表剥离以减小体积
