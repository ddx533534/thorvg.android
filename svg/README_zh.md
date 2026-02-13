# ThorVG SVG Android 模块

## 概述

本模块提供了一种在 Android 上使用 ThorVG 矢量图形引擎渲染 SVG 文件的简单方法。它提供了类似 ImageView 风格的 API，用于静态 SVG 渲染，支持多种数据源（文件、资源、字符串）。

### 核心特性

- **静态 SVG 渲染**：通过 ThorVG 提供高性能矢量图形渲染
- **简单的 API**：提供 ImageView 兼容的接口，包括 `SvgImageView` 和 `SvgDrawable`
- **多种数据源**：支持从文件路径、raw 资源或字符串内容加载
- **原生分辨率**：使用 SVG 的固有尺寸渲染，支持自动缩放
- **DPI 适配**：通过 ImageView 的 `scaleType` 完全支持 Android DPI 系统
- **轻量级**：ThorVG 二进制体积小（约 150KB）

## 架构设计

模块采用三层架构设计：

```
┌─────────────────────────────────────────────────┐
│  Kotlin 层（公共 API）                           │
│  - SvgImageView.kt                              │
│  - SvgDrawable.kt                               │
└────────────────┬────────────────────────────────┘
                 │ JNI 调用
┌────────────────▼────────────────────────────────┐
│  JNI 桥接层（C++）                               │
│  - svg-libs.cpp                                 │
│  - 将 Java 对象转换为原生类型                    │
└────────────────┬────────────────────────────────┘
                 │ ThorVG API 调用
┌────────────────▼────────────────────────────────┐
│  ThorVG 引擎层（C++）                            │
│  - SvgData.cpp                                  │
│  - Picture、SwCanvas、Initializer               │
└─────────────────────────────────────────────────┘
```

## 完整调用链路

### 1. 从字符串加载 SVG

**用户代码（Kotlin）**
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
        // JNI 调用到原生层
        drawable.nativePtr = nLoadSvgFromString(svgContent, outSize)

        if (drawable.nativePtr == 0L) return null

        drawable.intrinsicWidth = outSize[0].toInt()
        drawable.intrinsicHeight = outSize[1].toInt()
        return drawable
    }

    private external fun nLoadSvgFromString(content: String, outSize: FloatArray): Long
}
```

**↓ svg-libs.cpp（JNI 桥接）**
```cpp
JNIEXPORT jlong JNICALL
Java_org_thorvg_svg_SvgDrawable_nLoadSvgFromString(
    JNIEnv *env, jclass clazz, jstring content, jfloatArray outSize) {

    // 初始化 ThorVG（仅首次）
    static bool initialized = false;
    if (!initialized) {
        tvg::Initializer::init(tvg::CanvasEngine::Sw, 2);
        initialized = true;
    }

    // 将 Java 字符串转换为 C++ 字符串
    const char *contentStr = env->GetStringUTFChars(content, nullptr);
    const size_t contentLen = env->GetStringUTFLength(content);

    // 创建 SvgData 包装器
    auto svgData = std::make_unique<SvgData>();

    // 通过 ThorVG 加载 SVG 数据
    auto result = svgData->load(contentStr, contentLen);
    env->ReleaseStringUTFChars(content, contentStr);

    if (result != tvg::Result::Success) {
        return 0;
    }

    // 获取 SVG 尺寸
    float width, height;
    svgData->size(width, height);

    // 将尺寸返回给 Java
    jfloat sizeArray[2] = {width, height};
    env->SetFloatArrayRegion(outSize, 0, 2, sizeArray);

    // 返回指针作为 long
    return reinterpret_cast<jlong>(svgData.release());
}
```

**↓ SvgData.cpp（ThorVG 包装器）**
```cpp
tvg::Result SvgData::load(const char *data, size_t size) {
    // 创建 ThorVG Picture（SVG 容器）
    picture = tvg::Picture::gen();
    if (!picture) {
        return tvg::Result::InsufficientCondition;
    }

    // 将 SVG 内容加载到 Picture
    // 参数：data、length、mimetype、copy、override
    auto result = picture->load(data, size, "svg", nullptr, true);
    return result;
}

bool SvgData::size(float &w, float &h) {
    if (!picture) return false;

    // 获取 SVG 的固有尺寸
    picture->size(&w, &h);
    return true;
}
```

### 2. 渲染 SVG

**Android 框架**
```
View.invalidate() → View.onDraw() → Drawable.draw()
```

**↓ SvgDrawable.kt**
```kotlin
override fun draw(canvas: Canvas) {
    if (nativePtr == 0L) return
    if (intrinsicWidth <= 0 || intrinsicHeight <= 0) return

    // 使用 SVG 的固有尺寸创建位图
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

        // JNI 调用设置尺寸
        nSetSvgSize(nativePtr, newBitmap, intrinsicWidth.toFloat(), intrinsicHeight.toFloat())
        isDirty = true
    }

    // 如需要则渲染
    if (isDirty) {
        bitmap?.let {
            // JNI 调用渲染
            nDrawSvg(nativePtr, it)
            isDirty = false
        }
    }

    // 将位图绘制到画布（ImageView 通过 scaleType 处理缩放）
    bitmap?.let {
        canvas.drawBitmap(it, null, bounds, null)
    }
}
```

**↓ svg-libs.cpp（JNI 桥接）**
```cpp
JNIEXPORT void JNICALL
Java_org_thorvg_svg_SvgDrawable_nSetSvgSize(
    JNIEnv *env, jclass clazz, jlong svgPtr, jobject bitmap, jfloat width, jfloat height) {

    auto svgData = reinterpret_cast<SvgData *>(svgPtr);
    if (!svgData) return;

    // 获取原生位图信息
    AndroidBitmapInfo info;
    AndroidBitmap_getInfo(env, bitmap, &info);

    // 锁定位图像素
    void *pixels;
    AndroidBitmap_lockPixels(env, bitmap, &pixels);

    // 设置 ThorVG 画布目标
    svgData->setCanvasTarget(
        static_cast<uint32_t *>(pixels),
        info.width, info.height, info.stride / 4
    );

    AndroidBitmap_unlockPixels(env, bitmap);

    // 设置 SVG 尺寸
    svgData->setSize(width, height);
}

JNIEXPORT void JNICALL
Java_org_thorvg_svg_SvgDrawable_nDrawSvg(
    JNIEnv *env, jclass clazz, jlong svgPtr, jobject bitmap) {

    auto svgData = reinterpret_cast<SvgData *>(svgPtr);
    if (!svgData) return;

    // 锁定位图像素
    void *pixels;
    AndroidBitmap_lockPixels(env, bitmap, &pixels);

    // 更新画布目标并渲染
    AndroidBitmapInfo info;
    AndroidBitmap_getInfo(env, bitmap, &info);
    svgData->setCanvasTarget(
        static_cast<uint32_t *>(pixels),
        info.width, info.height, info.stride / 4
    );

    // 将 SVG 渲染到位图
    svgData->draw();

    AndroidBitmap_unlockPixels(env, bitmap);
}
```

**↓ SvgData.cpp（ThorVG 渲染）**
```cpp
void SvgData::setCanvasTarget(uint32_t *buffer, uint32_t width, uint32_t height, uint32_t stride) {
    if (!canvas) {
        canvas = tvg::SwCanvas::gen();
    }

    // 设置渲染目标（ARGB8888 位图）
    canvas->target(buffer, width, stride, height, tvg::SwCanvas::Colorspace::ARGB8888);
}

void SvgData::setSize(float width, float height) {
    if (!picture) return;

    // 缩放 picture 到目标尺寸
    picture->size(width, height);
}

bool SvgData::draw() {
    if (!canvas || !picture) return false;

    // 清空画布
    canvas->clear();

    // 将 picture 添加到画布（临时转移所有权）
    canvas->push(tvg::cast(picture.get()));

    // 渲染场景
    if (canvas->draw() != tvg::Result::Success) {
        return false;
    }

    // 将渲染结果刷新到位图
    if (canvas->sync() != tvg::Result::Success) {
        return false;
    }

    return true;
}
```

### 3. 内存清理

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
    delete svgData;  // 触发 ~SvgData()
}
```

**↓ SvgData.cpp**
```cpp
SvgData::~SvgData() {
    // unique_ptr 自动销毁 picture 和 canvas
    picture.reset();
    canvas.reset();
}
```

## 使用教程

### 1. 添加模块依赖

在你的 app 的 `build.gradle` 中：

```gradle
dependencies {
    implementation project(':svg')
}
```

### 2. 在布局中使用 SvgImageView

```xml
<org.thorvg.svg.SvgImageView
    android:id="@+id/svg_view"
    android:layout_width="200dp"
    android:layout_height="200dp"
    android:scaleType="centerInside"
    android:background="#f0f0f0" />
```

### 3. 在 Activity/Fragment 中加载 SVG

#### 从 Raw 资源加载

```kotlin
val svgView = findViewById<SvgImageView>(R.id.svg_view)
svgView.setSvgResource(R.raw.my_svg)
```

#### 从文件路径加载

```kotlin
svgView.setSvgPath("/sdcard/Download/image.svg")
```

#### 从字符串加载

```kotlin
val svgContent = """
    <svg width="100" height="100" xmlns="http://www.w3.org/2000/svg">
        <circle cx="50" cy="50" r="40" fill="red" />
    </svg>
""".trimIndent()

svgView.setSvgString(svgContent)
```

### 4. 直接使用 SvgDrawable

如需更多控制，可以直接使用 `SvgDrawable`：

```kotlin
val drawable = SvgDrawable.fromString(svgContent)
if (drawable != null) {
    imageView.setImageDrawable(drawable)

    // 使用完记得释放
    // drawable.release()
}
```

### 5. 处理 ScaleType

`SvgImageView` 继承了所有 `ImageView` 的缩放类型：

```kotlin
svgView.scaleType = ImageView.ScaleType.CENTER_INSIDE  // 适应内部，保持宽高比
svgView.scaleType = ImageView.ScaleType.CENTER_CROP     // 填充视图，必要时裁剪
svgView.scaleType = ImageView.ScaleType.FIT_XY          // 拉伸填充
```

## API 参考

### SvgImageView

继承自 `androidx.appcompat.widget.AppCompatImageView`

#### 方法

- `setSvgResource(@RawRes resId: Int)`：从 raw 资源加载 SVG
- `setSvgPath(path: String)`：从文件路径加载 SVG
- `setSvgString(svgContent: String)`：从字符串内容加载 SVG

### SvgDrawable

继承自 `android.graphics.drawable.Drawable`

#### 静态工厂方法

- `fromResource(resources: Resources, @RawRes resId: Int): SvgDrawable?`
- `fromPath(path: String): SvgDrawable?`
- `fromString(svgContent: String): SvgDrawable?`

#### 实例方法

- `release()`：释放原生资源（使用完时调用）

#### 属性

- `intrinsicWidth: Int`：SVG 的原生宽度（像素）
- `intrinsicHeight: Int`：SVG 的原生高度（像素）

## 构建说明

### 前置条件

- Android Studio（推荐最新版本）
- Android NDK（在 Android Studio 中配置）
- Meson 构建系统：`brew install meson`（macOS）或等效命令

### 构建 ThorVG 库

在构建模块之前，必须先为 Android 架构构建 ThorVG 库：

```bash
cd thorvg.android

# 为 arm64-v8a（64 位 ARM）构建
./gradlew svg:setupCrossBuild -Pabi=1
./build_libthorvg.sh

# 为 x86_64（64 位模拟器）构建
./gradlew svg:setupCrossBuild -Pabi=3
./build_libthorvg.sh
```

此脚本会：
1. 为目标 Android ABI 配置交叉编译
2. 构建启用 SVG 加载器、禁用 OpenMP 的 ThorVG
3. 输出静态库到 `thorvg/lib/{ABI}/libthorvg.a`

### 构建模块

```bash
./gradlew :svg:assembleDebug
```

模块会：
1. 定位预构建的 ThorVG 静态库
2. 编译 C++ JNI 桥接代码
3. 将所有内容链接到 `libsvg-libs.so`
4. 打包为 AAR

## 技术细节

### 内存管理

- **Kotlin 层**：使用标准对象生命周期，Bitmap 回收
- **JNI 层**：返回转换为 `jlong` 的原始指针，调用者必须调用销毁方法
- **C++ 层**：使用 `std::unique_ptr` 自动清理 ThorVG 对象

### 线程模型

- ThorVG 初始化使用 2 个线程进行软件渲染
- OpenMP 已禁用以避免模块间运行时冲突
- 渲染在 UI 线程上进行（Android 标准绘制模型）

### 渲染管线

1. **加载阶段**：SVG 解析为 `tvg::Picture`（矢量表示）
2. **尺寸阶段**：画布目标设置为匹配 Android Bitmap
3. **绘制阶段**：ThorVG 将矢量光栅化为 ARGB8888 位图
4. **显示阶段**：使用硬件加速将位图绘制到 Canvas

### 颜色格式

- ThorVG 渲染为 `ARGB8888` 格式（每通道 8 位，预乘 alpha）
- 匹配 Android 的 `Bitmap.Config.ARGB_8888`
- 完全支持透明度和颜色准确性

## 故障排查

### 问题："加载 SVG 失败"（结果代码 5）

**原因**：ThorVG 构建中未启用 SVG 加载器

**解决方案**：确保 `build_libthorvg.sh` 包含 `-Dloaders="svg"`：
```bash
meson setup build -Dloaders="svg" ...
```

### 问题：启动时崩溃并出现 OpenMP 错误

**原因**：多个模块静态链接 OpenMP

**解决方案**：ThorVG 构建中通过 `-Dextra=""` 禁用了 OpenMP。不要重新启用它。

### 问题：SVG 显示模糊或像素化

**原因**：SVG 以低分辨率渲染后放大

**解决方案**：模块自动使用 SVG 的固有尺寸。检查你的 SVG 是否有正确的 width/height 属性：
```xml
<svg width="200" height="200" ...>
```

### 问题：构建失败，提示"找不到 libthorvg.a"

**原因**：当前 ABI 的 ThorVG 库未构建

**解决方案**：为目标 ABI 运行构建脚本：
```bash
./gradlew svg:setupCrossBuild -Pabi=1  # arm64-v8a
./build_libthorvg.sh
```

### 问题：SVG 在布局预览中不显示

**原因**：Android Studio 布局预览不执行 JNI 代码

**解决方案**：这是预期行为。在设备或模拟器上测试。

## 性能特征

- **初始加载**：典型 SVG（100KB，中等复杂度）约 5-20ms
- **渲染**：每帧约 2-10ms（取决于 SVG 复杂度）
- **内存**：约为 SVG 固有尺寸像素数的 2 倍（ARGB = 4 字节/像素）
- **ThorVG 库**：约 150KB 二进制体积

## 限制

- **仅静态**：不支持动画（使用 ThorVG Lottie 模块进行动画）
- **无交互**：无法拦截 SVG 元素上的触摸事件
- **无动态更新**：加载后无法更改 SVG 属性
- **软件渲染**：使用 CPU 光栅化（ThorVG 的 Sw 后端）

## 未来增强

- [ ] 常用 SVG 的缓存机制
- [ ] 支持占位符的异步加载
- [ ] 超大 SVG 文件的流式处理
- [ ] GPU 渲染后端（OpenGL ES）
- [ ] SVG 属性动画
- [ ] 交互式事件处理

## 许可证

MIT License - 请参阅项目根目录的 LICENSE 文件

Copyright (c) 2025 - 2026 ThorVG project
