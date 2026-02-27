# ThorVG 是怎么知道要渲染成什么样子的？

## 完整流程概览

```
SVG 文件 → XML 解析器 → 场景图构建 → 图形对象树 → 光栅化渲染 → 像素输出
```

让我们逐步拆解这个过程。

---

## 阶段 1：SVG 文件加载

### 在你的项目中 (`svg-libs.cpp:68`)

```cpp
svgData->picture = tvg::Picture::gen();

// 从文件加载 SVG
svgData->picture->load(pathStr);

// 或从字符串加载
svgData->picture->load(contentStr, contentLen, "svg", true);
```

**这一步发生了什么？**
1. 创建 `Picture` 对象（容器）
2. 调用 `load()` 方法，传入 SVG 数据

---

## 阶段 2：加载器（Loader）系统

### Picture::load() 的内部实现 (`tvgPicture.cpp:112-130`)

```cpp
Result Picture::Impl::load(ImageLoader* loader)
{
    this->loader = loader;  // 保存加载器引用

    if (!loader->read()) return Result::Unknown;  // 关键：读取并解析

    this->w = loader->w;  // 获取 SVG 尺寸
    this->h = loader->h;

    return Result::Success;
}
```

**加载器的作用**：
- `ImageLoader` 是一个抽象基类
- 不同格式有不同的加载器：
  - `SvgLoader` → 处理 SVG 文件
  - `LottieLoader` → 处理 Lottie 动画
  - `PngLoader` → 处理 PNG 图片
  - `JpgLoader` → 处理 JPG 图片

---

## 阶段 3：SVG XML 解析

### SvgLoader::read() 的工作流程

ThorVG 使用内置的 **XML 解析器** 来读取 SVG 文件：

```
SVG 文件内容:
<svg width="100" height="100">
  <rect x="10" y="10" width="80" height="80" fill="red" />
  <circle cx="50" cy="50" r="30" fill="blue" />
</svg>

            ↓ XML 解析

解析树(SvgNode):
SvgDoc
  ├─ SvgRect (x:10, y:10, w:80, h:80, fill:red)
  └─ SvgCircle (cx:50, cy:50, r:30, fill:blue)
```

### 关键数据结构：SvgNode

```cpp
struct SvgNode {
    SvgNodeType type;        // 节点类型（Rect, Circle, Path 等）
    SvgNode* parent;         // 父节点
    SvgNode* child;          // 子节点

    // 样式属性
    struct {
        uint8_t r, g, b, a;  // 填充颜色
        float opacity;        // 透明度
        SvgStroke* stroke;    // 描边属性
        // ...
    } style;

    // 几何属性（根据类型不同）
    union {
        SvgRect* rect;        // 矩形属性
        SvgCircle* circle;    // 圆形属性
        SvgPath* path;        // 路径数据
        // ...
    } node;
};
```

---

## 阶段 4：场景图构建（Scene Graph）

### SvgSceneBuilder 的作用 (`tvgSvgSceneBuilder.cpp`)

解析后的 `SvgNode` 树会被转换成 ThorVG 的 **图形对象树**：

```
SvgNode树                    →      ThorVG 图形对象树

SvgDoc                              Scene (容器)
  ├─ SvgRect                          ├─ Shape (矩形)
  │   (x:10, y:10, w:80, h:80)        │   - appendRect(10, 10, 80, 80)
  │   (fill: red)                     │   - fill(255, 0, 0, 255)
  │                                   │
  └─ SvgCircle                        └─ Shape (圆形)
      (cx:50, cy:50, r:30)                - appendCircle(50, 50, 30, 30)
      (fill: blue)                        - fill(0, 0, 255, 255)
```

### Shape 对象的构建过程

#### 示例：矩形

```cpp
// 从 SvgNode 创建 Shape
auto shape = Shape::gen();

// 1. 添加路径（几何形状）
shape->appendRect(x, y, w, h, rx, ry);

// 2. 设置填充颜色
shape->fill(r, g, b, a);

// 3. 设置描边
if (svgNode->style.stroke) {
    shape->stroke(strokeWidth);
    shape->stroke(sr, sg, sb, sa);
}

// 4. 应用变换矩阵
if (svgNode->transform) {
    shape->transform(matrix);
}
```

#### 示例：复杂路径（Path）

SVG 中的 `<path>` 元素最为强大，可以绘制任意形状：

```xml
<path d="M10,10 L50,50 C50,10 90,10 90,50 Z" fill="green" />
```

转换过程：

```cpp
auto shape = Shape::gen();

// 解析 path 的 'd' 属性
// M10,10 → moveTo(10, 10)
shape->moveTo(10, 10);

// L50,50 → lineTo(50, 50)
shape->lineTo(50, 50);

// C50,10 90,10 90,50 → cubicTo(cx1, cy1, cx2, cy2, x, y)
shape->cubicTo(50, 10, 90, 10, 90, 50);

// Z → close()
shape->close();

// 设置填充
shape->fill(0, 255, 0, 255);  // 绿色
```

---

## 阶段 5：Shape 对象的内部表示

### Shape 类的关键方法 (`thorvg.h:855`)

```cpp
class Shape : public Paint {
public:
    // 基础路径命令
    Result moveTo(float x, float y);
    Result lineTo(float x, float y);
    Result cubicTo(float cx1, float cy1, float cx2, float cy2, float x, float y);
    Result close();

    // 便捷几何形状
    Result appendRect(float x, float y, float w, float h, float rx=0, float ry=0);
    Result appendCircle(float cx, float cy, float rx, float ry);
    Result appendArc(float cx, float cy, float radius, float startAngle, float sweep);

    // 颜色和样式
    Result fill(uint8_t r, uint8_t g, uint8_t b, uint8_t a=255);
    Result fill(unique_ptr<Fill> f);  // 渐变填充
    Result stroke(float width);
    Result stroke(uint8_t r, uint8_t g, uint8_t b, uint8_t a=255);

    // 变换
    Result transform(const Matrix& m);
};
```

### 内部数据结构：PathCommand

```cpp
struct PathCommand {
    uint8_t type;     // MoveTo=0, LineTo=1, CubicTo=2, Close=3
    float coords[6];  // 坐标数据
};

// 示例：M10,10 L50,50
PathCommand commands[] = {
    {MoveTo, {10, 10, 0, 0, 0, 0}},
    {LineTo, {50, 50, 0, 0, 0, 0}}
};
```

---

## 阶段 6：渲染管线（Render Pipeline）

### 从 Picture 到像素的完整流程

```
Picture::load()
  ↓
  加载 SVG → 创建 SvgLoader → 解析 XML → 构建 SvgNode 树
  ↓
Picture::Impl::load()
  ↓
  SvgLoader::read() → 构建 Scene/Shape 对象树
  ↓
canvas->push(picture)
  ↓
  将 Picture 中的 Paint 对象添加到 Canvas 的渲染列表
  ↓
canvas->draw()
  ↓
  Canvas::Impl::draw() → 遍历所有 Paint 对象
  ↓
  对每个 Shape:
    1. Shape::update() → 准备渲染数据
    2. Shape::render() → 提交到 Renderer
  ↓
SwRenderer::renderShape(RenderData)
  ↓
  将矢量路径转换为光栅化数据（栅格化）
  ↓
  1. 路径细分 → 生成扫描线
  2. 边缘抗锯齿
  3. 填充算法（Winding/Even-Odd）
  ↓
rasterShape() → 写入像素到 Bitmap
```

---

## 阶段 7：矢量到光栅转换（最核心的部分）

### 什么是光栅化（Rasterization）？

**矢量图形**：用数学公式描述的形状
```
矩形: (x=10, y=10, width=80, height=80)
圆形: (cx=50, cy=50, radius=30)
```

**光栅图形**：由像素组成的位图
```
像素矩阵:
[红][红][红][红]...
[红][蓝][蓝][红]...
[红][蓝][蓝][红]...
[红][红][红][红]...
```

### 光栅化算法（Scanline Rasterization）

#### 步骤 1：路径细分

将贝塞尔曲线分解为小线段：

```cpp
// 三次贝塞尔曲线
cubicTo(cx1, cy1, cx2, cy2, x, y)

// 细分为多个点
for (t = 0; t <= 1.0; t += 0.01) {
    px = (1-t)³*x0 + 3(1-t)²t*cx1 + 3(1-t)t²*cx2 + t³*x;
    py = (1-t)³*y0 + 3(1-t)²t*cy1 + 3(1-t)t²*cy2 + t³*y;
    points.push({px, py});
}
```

#### 步骤 2：生成扫描线（Scanlines）

```
        y=10 ─────────────────────
             │               │
        y=11 │   扫描线 1     │
             │               │
        y=12 │   扫描线 2     │
             │               │
        ...  │               │
             │               │
        y=90 ─────────────────────
            x=10           x=90
```

对每条扫描线：
1. 找出与形状边界的交点
2. 对交点之间的像素进行填充

#### 步骤 3：填充算法

```cpp
// 对于每条扫描线
for (y = yMin; y <= yMax; y++) {
    // 找出所有交点
    intersections = findIntersections(shape, y);

    // 排序交点
    sort(intersections);

    // 填充交点之间的像素（Winding 规则）
    for (i = 0; i < intersections.size(); i += 2) {
        int x1 = intersections[i];
        int x2 = intersections[i+1];

        for (x = x1; x <= x2; x++) {
            setPixel(x, y, color);  // 写入像素
        }
    }
}
```

#### 步骤 4：抗锯齿（Anti-Aliasing）

对边缘像素进行混合：

```cpp
// 边缘像素的覆盖率计算
float coverage = calculateCoverage(x, y, edge);

// 混合颜色（Alpha Blending）
finalColor.r = bgColor.r * (1 - coverage) + fillColor.r * coverage;
finalColor.g = bgColor.g * (1 - coverage) + fillColor.g * coverage;
finalColor.b = bgColor.b * (1 - coverage) + fillColor.b * coverage;
```

---

## 完整示例：一个红色圆形的渲染流程

### SVG 输入

```xml
<svg width="100" height="100">
  <circle cx="50" cy="50" r="30" fill="red" />
</svg>
```

### 流程追踪

#### 1. XML 解析
```cpp
SvgNode {
    type = SvgNodeType::Circle,
    node.circle = { cx: 50, cy: 50, r: 30 },
    style.fill = { r: 255, g: 0, b: 0, a: 255 }
}
```

#### 2. 场景构建
```cpp
auto shape = Shape::gen();
shape->appendCircle(50, 50, 30, 30);  // 转为椭圆（rx=ry=30）
shape->fill(255, 0, 0, 255);          // 红色填充
```

#### 3. 路径生成
```cpp
// Circle 内部转换为路径（使用贝塞尔曲线近似）
// 大约分为 8 段贝塞尔曲线
shape->moveTo(80, 50);                    // 起点（右边）
shape->cubicTo(80, 66.5, 66.5, 80, 50, 80);  // 右下弧
shape->cubicTo(33.5, 80, 20, 66.5, 20, 50);  // 左下弧
shape->cubicTo(20, 33.5, 33.5, 20, 50, 20);  // 左上弧
shape->cubicTo(66.5, 20, 80, 33.5, 80, 50);  // 右上弧
shape->close();
```

#### 4. 光栅化
```cpp
// 对于扫描线 y=50（圆心）
intersections = [20, 80];  // x 范围

// 填充像素
for (x = 20; x <= 80; x++) {
    setPixel(x, 50, {255, 0, 0, 255});  // 红色
}

// 对于扫描线 y=30（边缘附近）
intersections = [35, 65];
for (x = 35; x <= 65; x++) {
    coverage = calculateEdgeCoverage(x, 30);
    color = blend(background, red, coverage);
    setPixel(x, 30, color);
}
```

#### 5. 像素输出
```
Bitmap (100x100):
行 20: [白][白][白]...[红][红][红]...[白][白][白]
行 30: [白][白]...[淡红][红][红]...[淡红]...[白][白]
行 50: [白]...[红][红][红][红][红][红][红]...[白]
行 70: [白][白]...[淡红][红][红]...[淡红]...[白][白]
行 80: [白][白][白]...[红][红][红]...[白][白][白]
```

---

## 关键数据流总结

```
┌─────────────┐
│ SVG 文件    │ "circle cx=50 cy=50 r=30 fill=red"
└──────┬──────┘
       │
       ↓ XML 解析
┌─────────────┐
│ SvgNode树   │ Circle { cx:50, cy:50, r:30, fill:{255,0,0} }
└──────┬──────┘
       │
       ↓ 场景构建
┌─────────────┐
│ Shape 对象  │ appendCircle() + fill(255,0,0)
└──────┬──────┘
       │
       ↓ 路径命令
┌─────────────┐
│ PathCommand │ [MoveTo, CubicTo, CubicTo, ..., Close]
└──────┬──────┘
       │
       ↓ 光栅化
┌─────────────┐
│ 扫描线数据  │ y=20: x[35-65], y=30: x[28-72], ...
└──────┬──────┘
       │
       ↓ 像素填充
┌─────────────┐
│ Bitmap像素  │ [255,0,0,255] [255,0,0,255] ...
└─────────────┘
```

---

## ThorVG 的优势：数据驱动

### 声明式 vs 命令式

**传统图形 API（命令式）**：
```cpp
canvas.beginPath();
canvas.arc(50, 50, 30, 0, 2*PI);
canvas.fillStyle = "red";
canvas.fill();  // 立即渲染
```

**ThorVG（声明式）**：
```cpp
auto shape = Shape::gen();
shape->appendCircle(50, 50, 30, 30);
shape->fill(255, 0, 0);
// 仅构建数据结构，不立即渲染

canvas->push(std::move(shape));
canvas->draw();  // 统一渲染所有对象
```

**优势**：
1. **延迟渲染**：可以批量优化
2. **数据可重用**：Shape 对象可以多次渲染
3. **易于修改**：可以在渲染前修改属性
4. **多线程友好**：数据准备和渲染分离

---

## 核心概念总结

### ThorVG 如何"知道"要渲染什么？

1. **SVG XML 告诉它几何形状**：
   - `<rect>` → 矩形的位置和尺寸
   - `<circle>` → 圆的圆心和半径
   - `<path>` → 任意路径的控制点

2. **SVG 属性告诉它外观**：
   - `fill="red"` → 填充红色
   - `stroke-width="2"` → 描边宽度
   - `opacity="0.5"` → 半透明

3. **Scene Graph 组织层次关系**：
   - 父子关系（嵌套）
   - 图层顺序（绘制顺序）
   - 变换继承（坐标系）

4. **Renderer 执行光栅化**：
   - 矢量路径 → 像素位置
   - 颜色计算 → 像素颜色值
   - 抗锯齿 → 边缘平滑

### 最终答案

**ThorVG 是通过以下数据知道要渲染什么：**

| 数据来源 | 内容 | 作用 |
|---------|------|------|
| **SVG XML** | 几何形状、属性、样式 | 定义"画什么" |
| **SvgNode树** | 解析后的结构化数据 | 组织场景层次 |
| **Shape对象** | 路径命令 + 样式属性 | 描述图形 |
| **Renderer** | 光栅化算法 | 决定"怎么画" |
| **Bitmap** | 像素数据 | 最终输出 |

这就是一个完整的从 SVG 文件到屏幕像素的过程！🎨

---

## 相关文件索引

### SVG 解析
- `thorvg/src/loaders/svg/tvgSvgLoader.cpp` - SVG 文件加载和 XML 解析
- `thorvg/src/loaders/svg/tvgXmlParser.h` - XML 解析器
- `thorvg/src/loaders/svg/tvgSvgPath.cpp` - SVG 路径命令解析

### 场景构建
- `thorvg/src/loaders/svg/tvgSvgSceneBuilder.cpp` - SvgNode → Shape 转换
- `thorvg/src/renderer/tvgPicture.cpp` - Picture 容器实现

### 图形对象
- `thorvg/inc/thorvg.h` - 公共 API（Shape, Paint, Scene 等）
- `thorvg/src/renderer/tvgShape.cpp` - Shape 实现
- `thorvg/src/renderer/tvgPaint.cpp` - Paint 基类

### 光栅化渲染
- `thorvg/src/renderer/sw_engine/tvgSwRenderer.cpp` - 软件渲染器
- `thorvg/src/renderer/sw_engine/tvgSwRaster.cpp` - 光栅化算法
- `thorvg/src/renderer/sw_engine/tvgSwCommon.h` - 公共定义

---

*文档生成时间: 2024年*
