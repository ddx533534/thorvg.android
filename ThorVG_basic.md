# ThorVG 跨平台渲染架构原理详解

## 目录
1. [核心设计模式](#核心设计模式)
2. [draw() 的实现路径](#draw-的实现路径)
3. [跨平台的秘密](#跨平台的秘密)
4. [像素缓冲区的建立](#像素缓冲区的建立)
5. [软件光栅化流程](#软件光栅化流程)
6. [Android 系统的参与](#android-系统的参与)
7. [完整渲染链路](#完整渲染链路)
8. [设计优缺点](#设计优缺点)

---

## 核心设计模式

ThorVG 使用了经典的**策略模式 (Strategy Pattern)** 来实现跨平台渲染：

```
Canvas (抽象接口)
   ├── SwCanvas (CPU软件渲染 - Android/iOS/Windows)
   ├── GlCanvas (OpenGL渲染 - 高性能平台)
   └── WgCanvas (WebGPU渲染 - 现代Web)

每个 Canvas 内部持有一个 RenderMethod:
   ├── SwRenderer (软件光栅化)
   ├── GlRenderer (OpenGL实现)
   └── WgRenderer (WebGPU实现)
```

---

## draw() 的实现路径

### 第一层：Canvas 基类 (`tvgCanvas.cpp:64`)

```cpp
Result Canvas::draw() noexcept
{
    TVGLOG("RENDERER", "Draw S. --------------------------------");
    auto ret = pImpl->draw();  // 委托给 Canvas::Impl
    TVGLOG("RENDERER", "Draw E. --------------------------------");
    return ret;
}
```

### 第二层：Canvas::Impl (`tvgCanvas.h:109`)

```cpp
Result Canvas::Impl::draw()
{
    if (status == Status::Damaged) update(nullptr, false);
    if (status == Status::Drawing || paints.empty() || !renderer->preRender())
        return Result::InsufficientCondition;

    bool rendered = false;
    for (auto paint : paints) {
        // 每个 Paint 使用 renderer 进行渲染
        if (paint->pImpl->render(renderer)) rendered = true;
    }

    if (!rendered || !renderer->postRender())
        return Result::InsufficientCondition;

    status = Status::Drawing;
    return Result::Success;
}
```

**关键点**：这里的 `renderer` 是一个 `RenderMethod*` 指针，它是多态的基类指针。

### 第三层：RenderMethod 抽象接口 (`tvgRender.h:391`)

```cpp
class RenderMethod
{
public:
    virtual bool preRender() = 0;
    virtual bool renderShape(RenderData data) = 0;
    virtual bool renderImage(RenderData data) = 0;
    virtual bool postRender() = 0;
    virtual bool sync() = 0;
    virtual bool clear() = 0;
    // ... 其他纯虚函数
};
```

这是关键的**抽象基类**，定义了所有渲染器必须实现的接口。

### 第四层：SwRenderer 具体实现 (`tvgSwRenderer.h:36`)

```cpp
class SwRenderer : public RenderMethod
{
public:
    bool preRender() override;
    bool renderShape(RenderData data) override;
    bool renderImage(RenderData data) override;
    bool postRender() override;
    bool sync() override;
    bool clear() override;
    // ... 实现所有虚函数

    static SwRenderer* gen();  // 工厂方法
};
```

---

## 跨平台的秘密

### 多态 + 工厂模式

#### 在 Android 上 (本项目)

```cpp
// tvgSwCanvas.cpp:49
SwCanvas::SwCanvas() : Canvas(SwRenderer::gen()), pImpl(nullptr)
{
}
```

**工作原理**：
1. 创建 `SwCanvas` 对象
2. 内部创建 `SwRenderer::gen()` → 返回 `SwRenderer*`
3. 将这个 `SwRenderer*` 以 `RenderMethod*` 类型传给 `Canvas` 基类
4. **多态魔法**：虽然 `Canvas::Impl` 中存储的是 `RenderMethod* renderer`，但实际指向的是 `SwRenderer` 对象
5. 当调用 `renderer->draw()` 时，通过虚函数表(vtable)，实际调用的是 `SwRenderer::draw()`

#### 在其他平台上

- **OpenGL平台**：`GlCanvas::gen()` → 创建 `GlRenderer`
- **WebGPU平台**：`WgCanvas::gen()` → 创建 `WgRenderer`

### 为什么这样设计能跨平台？

#### 关键优势：

1. **接口统一**：所有平台的 Canvas 都继承自同一个 `Canvas` 基类，API 完全一致
   ```cpp
   // 用户代码在任何平台都一样
   auto canvas = SwCanvas::gen();  // 或 GlCanvas::gen()
   canvas->draw();
   canvas->sync();
   ```

2. **实现分离**：每个平台的具体渲染逻辑封装在各自的 `Renderer` 中
   - `SwRenderer`：纯CPU光栅化，使用算法绘制像素（Android常用）
   - `GlRenderer`：调用OpenGL API（iOS Metal/Android OpenGL ES）
   - `WgRenderer`：调用WebGPU API（Chrome/Firefox）

3. **编译时选择**：通过编译宏选择
   ```cpp
   #ifdef THORVG_SW_RASTER_SUPPORT
       SwCanvas::SwCanvas() : Canvas(SwRenderer::gen())
   #else
       SwCanvas::SwCanvas() : Canvas(nullptr)  // 不支持该渲染器
   #endif
   ```

### 不同平台的差异

| 平台 | Canvas类型 | Renderer类型 | 渲染方式 |
|------|-----------|-------------|---------|
| **Android** (本项目) | SwCanvas | SwRenderer | CPU软件光栅化到ARGB_8888 buffer |
| **iOS** | GlCanvas | GlRenderer | Metal/OpenGL GPU加速 |
| **Windows** | SwCanvas/GlCanvas | SwRenderer/GlRenderer | DirectX或OpenGL |
| **Web** | WgCanvas | WgRenderer | WebGPU GPU加速 |

---

## 像素缓冲区的建立

### 关键理解：ThorVG 不调用 Android 渲染 API

ThorVG 是**纯软件光栅化引擎**，它直接操作内存中的像素缓冲区，**不依赖任何操作系统的图形 API**（如 Android Canvas、Skia、OpenGL 等）。

### 在本项目中 (`SvgData.cpp:30-44`)

```cpp
void SvgData::setBufferSize(uint32_t* buf, float w, float h) {
    buffer = buf;  // 这是 Android Bitmap 的像素数组指针
    width = static_cast<uint32_t>(w);
    height = static_cast<uint32_t>(h);

    canvas = tvg::SwCanvas::gen();

    // 关键：将 Android Bitmap 的内存地址传递给 ThorVG
    if (canvas->target(buffer, width, width, height, tvg::SwCanvas::Colorspace::ABGR8888)
        != tvg::Result::Success) {
        LOGE("Failed to set canvas target");
        return;
    }
    // ...
}
```

**这一步做了什么？**
- `buf` 是从 Java 层传入的 **Android Bitmap 的像素数组**（通过 JNI 的 `AndroidBitmap_lockPixels()` 获得）
- `canvas->target()` 告诉 ThorVG："请直接在这块内存上绘制像素"

### SwRenderer::target() (`tvgSwRenderer.cpp:345`)

```cpp
bool SwRenderer::target(pixel_t* data, uint32_t stride, uint32_t w, uint32_t h, ColorSpace cs)
{
    if (!data || stride == 0 || w == 0 || h == 0 || w > stride) return false;

    clearCompositors();

    if (!surface) surface = new SwSurface;

    surface->data = data;  // 保存 Android Bitmap 的指针
    surface->stride = stride;
    surface->w = w;
    surface->h = h;
    surface->cs = cs;
    surface->channelSize = CHANNEL_SIZE(cs);
    surface->premultiplied = true;
    // ...
}
```

**关键点**：`surface->data` 现在指向 **Android Bitmap 的内存**。

---

## 软件光栅化流程

### 绘制时的像素写入调用链

```
Canvas::draw()
  ↓
Canvas::Impl::draw()  (tvgCanvas.h:109)
  ↓
renderer->renderShape()  (多态调用)
  ↓
SwRenderer::renderShape()  (tvgSwRenderer.cpp:436)
  ↓
_renderFill(task, surface, opacity)  (tvgSwRenderer.cpp:259)
  ↓
rasterShape(surface, shape, r, g, b, a)  (tvgSwRaster.cpp:1743)
  ↓
_rasterSolidRect(surface, region, r, g, b)  (tvgSwRaster.cpp:449)
  ↓
rasterPixel32(buffer + y * stride, color, x, width)  (tvgSwRaster.cpp:1512)
  ↓
【关键】直接写入像素到 surface->buf32（即 Android Bitmap 的内存）
```

### SwRenderer::renderShape() (`tvgSwRenderer.cpp:436-455`)

```cpp
bool SwRenderer::renderShape(RenderData data)
{
    auto task = static_cast<SwShapeTask*>(data);
    if (!task) return false;

    task->done();

    if (task->opacity == 0) return true;

    // Main raster stage
    if (task->rshape->stroke && task->rshape->stroke->strokeFirst) {
        _renderStroke(task, surface, task->opacity);
        _renderFill(task, surface, task->opacity);
    } else {
        _renderFill(task, surface, task->opacity);
        _renderStroke(task, surface, task->opacity);
    }

    return true;
}
```

### _renderFill() (`tvgSwRenderer.cpp:259-269`)

```cpp
static void _renderFill(SwShapeTask* task, SwSurface* surface, uint8_t opacity)
{
    uint8_t r, g, b, a;
    if (auto fill = task->rshape->fill) {
        rasterGradientShape(surface, &task->shape, fill, opacity);
    } else {
        task->rshape->fillColor(&r, &g, &b, &a);
        a = MULTIPLY(opacity, a);
        if (a > 0) rasterShape(surface, &task->shape, r, g, b, a);
    }
}
```

### rasterShape() (`tvgSwRaster.cpp:1743-1752`)

```cpp
bool rasterShape(SwSurface* surface, SwShape* shape, uint8_t r, uint8_t g, uint8_t b, uint8_t a)
{
    if (a < 255) {
        r = MULTIPLY(r, a);
        g = MULTIPLY(g, a);
        b = MULTIPLY(b, a);
    }
    if (shape->fastTrack) return _rasterRect(surface, shape->bbox, r, g, b, a);
    else return _rasterRle(surface, shape->rle, r, g, b, a);
}
```

### _rasterSolidRect() (`tvgSwRaster.cpp:449-471`)

```cpp
static bool _rasterSolidRect(SwSurface* surface, const SwBBox& region, uint8_t r, uint8_t g, uint8_t b)
{
    auto w = static_cast<uint32_t>(region.max.x - region.min.x);
    auto h = static_cast<uint32_t>(region.max.y - region.min.y);

    // 32bits channels
    if (surface->channelSize == sizeof(uint32_t)) {
        auto color = surface->join(r, g, b, 255);
        auto buffer = surface->buf32 + (region.min.y * surface->stride);
        for (uint32_t y = 0; y < h; ++y) {
            rasterPixel32(buffer + y * surface->stride, color, region.min.x, w);
        }
        return true;
    }
    // 8bits grayscale
    if (surface->channelSize == sizeof(uint8_t)) {
        for (uint32_t y = 0; y < h; ++y) {
            rasterGrayscale8(surface->buf8, 255, (y + region.min.y) * surface->stride + region.min.x, w);
        }
        return true;
    }
    return false;
}
```

### 最底层的像素写入 (`tvgSwRaster.cpp:1512-1521`)

```cpp
void rasterPixel32(uint32_t *dst, uint32_t val, uint32_t offset, int32_t len)
{
#if defined(THORVG_AVX_VECTOR_SUPPORT)
    avxRasterPixel32(dst, val, offset, len);  // 使用 AVX SIMD 指令
#elif defined(THORVG_NEON_VECTOR_SUPPORT)
    neonRasterPixel32(dst, val, offset, len);  // 使用 ARM NEON SIMD 指令
#else
    cRasterPixels(dst, val, offset, len);     // 纯 C 实现
#endif
}
```

**这段代码做了什么？**
- `dst`：指向 **Android Bitmap 的某一行像素**
- `val`：要填充的颜色值（ARGB 格式）
- 直接用 `memcpy` 或 SIMD 指令将颜色值写入 `dst` 指向的内存

**示例**：假设要绘制一个红色矩形
```cpp
// 在 _rasterSolidRect 中
auto color = surface->join(r, g, b, 255);  // 组装成 0xFFFF0000 (红色)
auto buffer = surface->buf32 + (region.min.y * surface->stride);  // 定位到起始行

for (uint32_t y = 0; y < h; ++y) {
    // 对每一行的像素，直接写入红色值
    rasterPixel32(buffer + y * surface->stride, color, region.min.x, w);
}
```

---

## Android 系统的参与

### ThorVG 完成后，像素数据已经在 Bitmap 中了

在 `SvgDrawable.kt:144-194` 中：

```kotlin
override fun draw(canvas: Canvas) {
    if (nativePtr == 0L) {
        Log.w(TAG, "Cannot draw: SVG not loaded")
        return
    }

    // 使用 SVG 的固有尺寸，而非 view bounds
    if (intrinsicWidth <= 0 || intrinsicHeight <= 0) {
        Log.w(TAG, "Invalid SVG size: ${intrinsicWidth}x${intrinsicHeight}")
        return
    }

    // 创建或重建 bitmap
    val currentBitmap = bitmap
    if (currentBitmap == null ||
        currentBitmap.width != intrinsicWidth ||
        currentBitmap.height != intrinsicHeight
    ) {
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

    // 1. ThorVG 已经通过 nDrawSvg() 将像素写入 bitmap
    if (isDirty) {
        bitmap?.let {
            nDrawSvg(nativePtr, it)  // 调用 JNI，ThorVG 操作 Bitmap 的像素
            isDirty = false
        }
    }

    // 2. 使用 Android Canvas 将 bitmap 绘制到屏幕
    bitmap?.let {
        val bounds = bounds
        canvas.drawBitmap(it, null, bounds, paint)  // 这里才调用 Android 渲染系统
    }
}
```

**关键点**：
- `nDrawSvg()` 完成后，**`bitmap` 的像素已经被 ThorVG 填充完毕**
- `canvas.drawBitmap()` 是 **Android 系统调用**，负责：
  - 将 `Bitmap` 的像素数据提交给 **Skia 图形库**
  - Skia 将像素数据传递给 **SurfaceFlinger**（Android 的合成器）
  - SurfaceFlinger 将多个窗口的内容合成后，通过 **GPU** 显示到屏幕

---

## 完整渲染链路

```
┌─────────────────────────────────────────────────────────────┐
│ Java/Kotlin 层                                               │
│                                                              │
│  SvgDrawable.draw(canvas)                                    │
│      ↓                                                       │
│  nDrawSvg(nativePtr, bitmap)  ← JNI 调用                     │
└─────────────────────────────────────────────────────────────┘
                ↓
┌─────────────────────────────────────────────────────────────┐
│ JNI 层 (native/src/main/cpp/svg/svg-libs.cpp)               │
│                                                              │
│  AndroidBitmap_lockPixels(env, bitmap, &buffer)              │
│      ↓                                                       │
│  svgData->draw()                                             │
└─────────────────────────────────────────────────────────────┘
                ↓
┌─────────────────────────────────────────────────────────────┐
│ ThorVG 层 (纯 C++，CPU 软件光栅化)                           │
│                                                              │
│  canvas->draw()                                              │
│      ↓                                                       │
│  SwRenderer::renderShape()                                   │
│      ↓                                                       │
│  rasterPixel32(dst, color, ...)                              │
│      ↓                                                       │
│  【直接写入 uint32_t* dst】                                  │
│  dst[0] = 0xFFFF0000;  // 写入红色像素                       │
│  dst[1] = 0xFFFF0000;                                        │
│  ...                                                         │
│                                                              │
│  ↓ 这时 Android Bitmap 的像素已经被填充                     │
└─────────────────────────────────────────────────────────────┘
                ↓
┌─────────────────────────────────────────────────────────────┐
│ 回到 Kotlin 层                                               │
│                                                              │
│  canvas.drawBitmap(bitmap, null, bounds, paint)              │
│      ↓  ← 这里才调用 Android 系统渲染                         │
└─────────────────────────────────────────────────────────────┘
                ↓
┌─────────────────────────────────────────────────────────────┐
│ Android 图形栈                                               │
│                                                              │
│  Skia (Android 的 2D 图形库)                                 │
│      ↓                                                       │
│  SurfaceFlinger (窗口合成器)                                 │
│      ↓                                                       │
│  GPU/Display Driver                                          │
│      ↓                                                       │
│  屏幕显示 🖥️                                                 │
└─────────────────────────────────────────────────────────────┘
```

### 渲染阶段总结

| 阶段 | 负责方 | 操作 |
|------|--------|------|
| **1. 准备画布** | Android (Kotlin/JNI) | 创建 `Bitmap`，获取其像素数组指针 |
| **2. 软件光栅化** | ThorVG (C++) | **直接写入像素**到 `Bitmap` 的内存，不调用任何系统 API |
| **3. 显示到屏幕** | Android (Canvas/Skia/SurfaceFlinger/GPU) | 将 `Bitmap` 合成到屏幕 |

---

## 设计优缺点

### 优点

1. **跨平台**：ThorVG 只操作内存，不依赖平台 API（可以在 Linux、Windows、嵌入式系统运行）
2. **可控性强**：完全控制每个像素的绘制算法
3. **无驱动依赖**：不需要 GPU 驱动，在软件环境也能运行
4. **一致性**：在所有平台上渲染结果完全一致

### 缺点

1. **性能较低**：纯 CPU 计算，没有 GPU 加速
2. **耗电**：CPU 密集型操作比 GPU 更耗电
3. **大尺寸性能差**：渲染大尺寸 SVG 时，CPU 负担较重

---

## 对比：Android Canvas 直接绘制

如果不用 ThorVG，直接用 Android Canvas API 绘制 SVG：

```kotlin
// 假设不用 ThorVG，直接用 Android Canvas
canvas.drawPath(path, paint)  // 调用 Skia，Skia 调用 GPU
```

**区别**：
- **Android Canvas**：调用 Skia → Skia 调用 GPU 光栅化 → 直接显示
- **ThorVG**：CPU 软件光栅化 → 填充 Bitmap → Canvas.drawBitmap() → Skia/GPU 显示

---

## 核心结论

**ThorVG 直接操作像素内存（CPU 光栅化），Android 系统只负责把最终的 Bitmap 显示到屏幕（GPU 合成）。**

这就是为什么 ThorVG 能跨平台的原因 —— 它不依赖任何操作系统的图形 API，只需要一块内存区域来写入像素数据！

---

## 虚函数的跨平台魔法

```cpp
// 这是多态的核心
RenderMethod* renderer = SwRenderer::gen();  // 或 GlRenderer::gen()

// 调用虚函数时，C++ 运行时通过虚函数表找到实际的实现
renderer->draw();  // 自动路由到 SwRenderer::draw() 或 GlRenderer::draw()
```

这就是为什么 `virtual Result draw() noexcept` 能实现跨平台的原因：
- **接口定义在基类** (`RenderMethod`)
- **实现在子类** (`SwRenderer`, `GlRenderer`, `WgRenderer`)
- **通过多态机制**，在运行时自动选择正确的实现
- **用户代码完全不感知**底层是 CPU 渲染还是 GPU 渲染

这是教科书般的面向对象设计！🎯


## 比较 ThorVG, Skia, WebGPU, Vulkan 和 OpenGL

| 特性                 | **ThorVG**                                             | **Skia**                                              | **WebGPU**                                           | **Vulkan**                                           | **OpenGL**                                          |
|----------------------|--------------------------------------------------------|------------------------------------------------------|-----------------------------------------------------|----------------------------------------------------|----------------------------------------------------|
| **类型**             | 图形库 (2D 渲染引擎)                                    | 图形库 (2D 和 3D 渲染引擎)                             | 图形和计算 API (现代 Web API)                         | 图形和计算 API (低级控制)                            | 图形 API (高层控制，支持 2D 和 3D 渲染)             |
| **功能**             | 提供简化的 2D 图形渲染接口，支持硬件加速               | 提供 2D 和 3D 图形渲染，支持 CPU 和 GPU 渲染           | 提供图形渲染和 GPU 计算的统一支持，针对 Web 应用        | 提供低级别 GPU 控制，适用于高性能图形渲染和计算任务   | 提供图形渲染，支持 2D 和 3D 图形，GPU 加速            |
| **硬件加速支持**     | 支持通过 OpenGL、Vulkan、Metal 等后端实现 GPU 加速    | 支持通过 OpenGL、Vulkan、Metal 等后端实现 GPU 加速     | 支持通过 GPU 后端 (Vulkan、Metal) 实现硬件加速         | 原生支持硬件加速，直接控制 GPU 和资源               | 支持 GPU 加速，使用 OpenGL ES 或 OpenGL 实现硬件加速   |
| **平台支持**         | 跨平台，支持 Android、iOS、Windows、Linux 等平台       | 跨平台，支持 Android、iOS、Windows、Linux 等平台       | 跨平台，支持现代浏览器，主要在 Web 环境中使用           | 跨平台，支持 Windows、Linux、macOS、Android 等平台    | 跨平台，支持 Windows、Linux、macOS 等平台            |
| **编程模型**         | 高层抽象，简化的 2D 图形 API                          | 提供高级抽象，支持 2D 和 3D 图形及文本渲染              | 基于现代图形 API，低级别控制，支持图形和计算任务          | 提供对 GPU 更低级的控制，需要开发者手动管理资源         | 基于高级抽象的图形管线，支持 2D 和 3D 图形              |
| **易用性**           | 简单易用，适合快速图形开发                            | 相对易用，适合 2D 和 3D 图形开发                         | 复杂，需要更多配置，适合高性能 Web 图形和计算应用        | 复杂，需要手动管理内存、着色器和管线，适合高性能应用    | 较为易用，适合 2D 和 3D 图形渲染开发                   |
| **性能优化**         | 通过图形后端选择硬件加速进行优化                      | 支持多种硬件后端，能在 GPU 上进行优化                    | 通过 GPU 加速渲染，适合高性能 Web 应用和计算任务         | 提供更低级别的控制，可优化到接近硬件的性能              | 提供 GPU 加速，但性能不如 Vulkan                       |
| **计算支持**         | 无专门支持，侧重于图形渲染                           | 支持 2D 图形、文本和图像渲染，计算支持较弱               | 支持计算任务和图形渲染的统一处理，适合高性能计算应用      | 支持 GPU 计算，适合进行高效的并行计算任务               | 主要侧重于图形渲染，计算支持较少                       |
| **常见用途**         | 适用于 2D 图形渲染（如 UI、动画、图标）               | 用于 2D 和 3D 图形渲染（如游戏、浏览器、图形工具）       | 用于 Web 图形渲染和计算（如高性能 Web 应用、游戏等）      | 用于图形渲染和并行计算，适用于游戏、虚拟现实、科学计算等 | 适用于 2D 和 3D 图形渲染、WebGL 和 OpenGL ES 应用       |
| **跨平台性**         | 跨平台，支持 Android、iOS、Windows、Linux 等平台       | 跨平台，支持 Android、iOS、Windows、Linux 等平台        | 跨平台，支持所有现代浏览器，目标 Web 环境               | 跨平台，支持 Windows、Linux、macOS、Android 等平台      | 跨平台，支持 Windows、Linux、macOS 等平台              |

---

### 总结：

- **ThorVG** 是一个专注于 2D 图形的渲染库，简化了图形绘制过程，支持通过后端（如 OpenGL、Vulkan、Metal）来实现硬件加速。
- **Skia** 是一个功能强大的 2D 和 3D 图形渲染库，支持跨平台渲染，并通过 GPU 加速提供高效的图形渲染。
- **WebGPU** 是一个现代的图形和计算 API，主要面向 Web 开发，提供更接近底层的 GPU 控制，适用于高性能计算和图形渲染。
- **Vulkan** 是一个低级别的图形和计算 API，允许开发者完全控制 GPU 渲染管线，适用于高性能要求的应用。
- **OpenGL** 是一个成熟的跨平台图形 API，提供较为高级的抽象，适用于 2D 和 3D 图形渲染，支持 GPU 加速。

选择合适的工具取决于你的项目需求：
- **ThorVG** 和 **Skia** 更适合快速图形开发和需要抽象层的项目。
- **WebGPU** 是未来 Web 图形开发的重点，适合高性能 Web 应用。
- **Vulkan** 和 **OpenGL** 提供了更低级和更强大的 GPU 控制，适合需要最大性能和灵活性的应用。

---

## 相关文件索引

### 核心架构文件
- `thorvg/inc/thorvg.h` - 公共 API 定义
- `thorvg/src/renderer/tvgCanvas.h` - Canvas 基类实现
- `thorvg/src/renderer/tvgCanvas.cpp` - Canvas 基类方法
- `thorvg/src/renderer/tvgRender.h` - RenderMethod 抽象接口

### SwRenderer (CPU 软件渲染)
- `thorvg/src/renderer/sw_engine/tvgSwRenderer.h` - SwRenderer 定义
- `thorvg/src/renderer/sw_engine/tvgSwRenderer.cpp` - SwRenderer 实现
- `thorvg/src/renderer/sw_engine/tvgSwCanvas.cpp` - SwCanvas 实现
- `thorvg/src/renderer/sw_engine/tvgSwRaster.cpp` - 像素光栅化核心算法
- `thorvg/src/renderer/sw_engine/tvgSwCommon.h` - 公共定义和工具函数

### 本项目集成代码
- `native/src/main/cpp/svg/SvgData.h` - SVG 数据封装
- `native/src/main/cpp/svg/SvgData.cpp` - ThorVG Canvas 初始化和绘制
- `native/src/main/cpp/svg/svg-libs.cpp` - JNI 接口
- `svg/src/main/java/org/thorvg/svg/SvgDrawable.kt` - Android Drawable 实现

---
