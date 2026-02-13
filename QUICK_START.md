# ThorVG Android 快速开始指南

## 前置要求

- Android Studio (最新版本)
- Android NDK (通过 Android Studio SDK Manager 安装)
- Meson 构建系统: `brew install meson` (macOS) 或 `pip install meson` (通用)
- Ninja 构建工具: `brew install ninja` (macOS) 或 `pip install ninja` (通用)
- Git

## 快速开始（5 步）

### 1. 克隆项目

```bash
git clone https://github.com/ddx533534/thorvg.android.git
cd thorvg.android
```

### 2. 获取 ThorVG 源码（重要！）

ThorVG 是本项目的核心依赖，必须放在项目根目录下。

```bash
# 克隆 ThorVG 仓库
git clone https://github.com/thorvg/thorvg.git

# 切换到指定的稳定 commit
cd thorvg
git checkout e15069de7afcc5e853edf1561e69d9b8383e2c6c
cd ..
```

**验证**: 确保目录结构如下：

```
thorvg.android/
├── thorvg/           ✓ 必须存在
│   ├── src/
│   ├── inc/
│   ├── cross/
│   └── meson.build
├── native/
├── lottie/
├── svg/
└── sample/
```

### 3. 构建 ThorVG 静态库

为目标架构构建 ThorVG：

```bash
# 为 ARM64 设备构建 (推荐先构建这个)
./gradlew native:setupCrossBuild -Pabi=1
./build_libthorvg.sh
./copy_libthorvg.sh 1

# 为 x86_64 模拟器构建 (可选，如需模拟器测试)
./gradlew native:setupCrossBuild -Pabi=2
./build_libthorvg.sh
./copy_libthorvg.sh 2
```

**验证**: 检查生成的库文件：

```bash
ls -la thorvg/lib/arm64-v8a/libthorvg.a   # 应该存在
ls -la thorvg/lib/x86_64/libthorvg.a      # 如果构建了 x86_64
```

### 4. 构建 Android 项目

```bash
# 清理之前的构建（推荐）
./gradlew clean

# 构建 Debug APK
./gradlew :sample:assembleDebug
```

### 5. 安装并运行

```bash
# 安装到连接的设备
./gradlew :sample:installDebug

# 或者直接从 Android Studio 运行
```

## 常见问题

### Q1: `thorvg/meson.build` 找不到

**原因**: 没有执行步骤 2，ThorVG 源码不存在。

**解决**:
```bash
git clone https://github.com/thorvg/thorvg.git
cd thorvg && git checkout e15069de7afcc5e853edf1561e69d9b8383e2c6c && cd ..
```

### Q2: `libthorvg.a` 找不到

**原因**: 没有执行步骤 3，ThorVG 静态库未构建。

**解决**:
```bash
./gradlew native:setupCrossBuild -Pabi=1
./build_libthorvg.sh
```

### Q3: Meson 命令找不到

**原因**: 系统未安装 Meson。

**解决**:
```bash
# macOS
brew install meson ninja

# Linux
sudo apt install meson ninja-build  # Ubuntu/Debian
sudo dnf install meson ninja-build  # Fedora

# 通用 (Python)
pip install meson ninja
```

### Q4: 构建时报 OpenMP 错误

**原因**: OpenMP 已经通过统一的 native 模块静态链接，不应该再有冲突。

**检查**:
- 确保 lottie 和 svg 模块的 `build.gradle` 中 native 构建已注释
- 确保两者都依赖 `project(':native')`
- 清理后重新构建：`./gradlew clean && ./gradlew :sample:assembleDebug`

### Q5: 颜色显示不正确（红蓝反转）

**原因**: 颜色空间配置问题（已在最新代码中修复）。

**检查**: `svg/src/main/cpp/SvgData.cpp` 中应该使用 `ABGR8888`:
```cpp
canvas->target(buffer, width, stride, height, tvg::SwCanvas::Colorspace::ABGR8888);
```

## 使用示例

### SVG 渲染

```kotlin
// 在 XML 中
<org.thorvg.svg.SvgImageView
    android:id="@+id/svg_view"
    android:layout_width="200dp"
    android:layout_height="200dp"
    android:scaleType="centerInside" />

// 在 Activity 中
val svgView = findViewById<SvgImageView>(R.id.svg_view)

// 从资源加载
svgView.setSvgResource(R.raw.my_icon)

// 从字符串加载
svgView.setSvgString("""
    <svg width="100" height="100">
        <circle cx="50" cy="50" r="40" fill="blue"/>
    </svg>
""")
```

### Lottie 动画

```kotlin
// 在 XML 中
<org.thorvg.lottie.LottieAnimationView
    android:id="@+id/lottie_view"
    android:layout_width="200dp"
    android:layout_height="200dp"
    app:lottieDrawable="@drawable/animation" />

// 在 Activity 中
val lottieView = findViewById<LottieAnimationView>(R.id.lottie_view)
lottieView.playAnimation()
```

## 项目结构

```
thorvg.android/
├── thorvg/              # ThorVG 源码（外部依赖）
│   └── lib/             # 构建产物
├── native/              # 统一的 Native 模块
│   └── src/main/cpp/    # 编译所有 C++ 代码
├── lottie/              # Lottie 模块（Kotlin API）
├── svg/                 # SVG 模块（Kotlin API）
└── sample/              # 示例 App
```

## 下一步

- 查看 [NATIVE_MODULE_GUIDE.md](NATIVE_MODULE_GUIDE.md) 了解详细架构
- 查看 [svg/README.md](svg/README.md) 了解 SVG 模块 API
- 查看 `sample/` 目录的示例代码

## 支持

- ThorVG 官网: https://www.thorvg.org/
- ThorVG GitHub: https://github.com/thorvg/thorvg
- ThorVG 文档: https://www.thorvg.org/apis
