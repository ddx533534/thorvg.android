# ThorVG 依赖说明

## 概述

本项目依赖 [ThorVG](https://github.com/thorvg/thorvg) 开源矢量图形库作为底层渲染引擎。ThorVG 提供了高性能的 SVG 和 Lottie 渲染能力。

## 依赖的 ThorVG 版本

| 属性 | 值 |
|------|-----|
| **仓库地址** | https://github.com/thorvg/thorvg.git |
| **Commit ID** | `e15069de7afcc5e853edf1561e69d9b8383e2c6c` |
| **版本** | ~1.0.0 |
| **日期** | 2024-2025 |

## 为什么使用这个特定版本？

### 1. API 兼容性
- ThorVG 1.0.0 引入了重大 API 变更，使用 `std::unique_ptr` 管理对象
- 早期版本使用原始指针，内存管理方式不同
- 本项目的 C++ 代码针对 1.0.0 API 编写

### 2. 功能完整性
- 该版本完整支持 SVG 和 Lottie 加载器
- 包含软件渲染引擎 (SwCanvas)
- 支持 ARGB/ABGR 多种颜色空间

### 3. 稳定性
- 该 commit 经过充分测试
- 已在生产环境验证
- Bug 修复完整

## 获取 ThorVG 源码

### 方式 1: Git Clone（推荐用于开发）

```bash
cd thorvg.android/
git clone https://github.com/thorvg/thorvg.git
cd thorvg
git checkout e15069de7afcc5e853edf1561e69d9b8383e2c6c
cd ..
```

### 方式 2: Git Submodule（推荐用于发布）

```bash
cd thorvg.android/
git submodule add https://github.com/thorvg/thorvg.git thorvg
cd thorvg
git checkout e15069de7afcc5e853edf1561e69d9b8383e2c6c
cd ..
git add .gitmodules thorvg
git commit -m "Add ThorVG as submodule at e15069de"
```

### 方式 3: 从已有项目更新

```bash
cd thorvg.android/

# 如果是 submodule
git submodule update --init --recursive
cd thorvg && git checkout e15069de7afcc5e853edf1561e69d9b8383e2c6c && cd ..

# 如果是普通 clone
cd thorvg && git pull && git checkout e15069de7afcc5e853edf1561e69d9b8383e2c6c && cd ..
```

## 目录结构要求

ThorVG 必须位于项目根目录：

```
thorvg.android/              ← 项目根目录
├── thorvg/                  ← ThorVG 源码（必需）
│   ├── src/                 ← ThorVG C++ 源代码
│   ├── inc/                 ← ThorVG 头文件
│   ├── cross/               ← 交叉编译配置
│   ├── meson.build          ← Meson 构建文件
│   ├── build/               ← 构建目录（生成）
│   └── lib/                 ← 静态库输出（生成）
│       ├── arm64-v8a/
│       │   └── libthorvg.a
│       └── x86_64/
│           └── libthorvg.a
├── native/
├── lottie/
├── svg/
└── sample/
```

## ThorVG 构建配置

### Meson 配置选项

本项目使用以下 Meson 配置编译 ThorVG：

```bash
meson setup build \
  -Dloaders="svg, lottie" \          # 启用 SVG 和 Lottie 加载器
  --cross-file /tmp/android_cross.txt \  # Android 交叉编译配置
  -Ddefault_library=static            # 构建静态库
```

### 启用的加载器

| 加载器 | 说明 | 用途 |
|--------|------|------|
| `svg` | SVG 1.1/2.0 | 渲染 SVG 矢量图 |
| `lottie` | Lottie JSON | 渲染 Lottie 动画 |

### 其他配置

- **库类型**: 静态库 (`.a`)
- **线程支持**: 使用 OpenMP（静态链接）
- **渲染引擎**: SwCanvas (软件渲染)
- **颜色空间**: ABGR8888 (Android 兼容)

## 构建流程

详细构建步骤见 [QUICK_START.md](QUICK_START.md)。

简要流程：

1. **获取源码**: 克隆 ThorVG 到 `thorvg/` 目录
2. **生成配置**: `./gradlew native:setupCrossBuild -Pabi=1`
3. **编译**: `./build_libthorvg.sh`
4. **验证**: 检查 `thorvg/lib/{ABI}/libthorvg.a` 是否生成

## 依赖关系图

```
┌─────────────────────────────────────────┐
│  thorvg.android 项目                     │
│  - Android 上的 ThorVG 封装              │
└──────────────────┬──────────────────────┘
                   │ 依赖
                   ▼
┌─────────────────────────────────────────┐
│  ThorVG Library                         │
│  - Commit: e15069de7afcc5e853edf1561... │
│  - 来源: github.com/thorvg/thorvg       │
└──────────────────┬──────────────────────┘
                   │ 构建生成
                   ▼
┌─────────────────────────────────────────┐
│  libthorvg.a                            │
│  - 位置: thorvg/lib/{ABI}/              │
│  - 类型: 静态库                          │
│  - 大小: ~500KB - 1MB                   │
└──────────────────┬──────────────────────┘
                   │ 链接到
                   ▼
┌─────────────────────────────────────────┐
│  libthorvg-native.so                    │
│  - native 模块生成的共享库               │
│  - 包含 JNI 桥接代码                     │
└─────────────────────────────────────────┘
```

## 更新 ThorVG 版本

### 注意事项

⚠️ **更新 ThorVG 版本可能导致 API 不兼容，需要修改 C++ 代码！**

### 更新步骤

1. **备份当前代码**
   ```bash
   git checkout -b backup-before-thorvg-update
   git push origin backup-before-thorvg-update
   ```

2. **更新 ThorVG**
   ```bash
   cd thorvg
   git fetch origin
   git checkout <new-commit-id>
   cd ..
   ```

3. **测试编译**
   ```bash
   ./gradlew native:setupCrossBuild -Pabi=1
   ./build_libthorvg.sh
   ```

4. **检查 API 变化**
   - 查看 ThorVG 的 CHANGELOG
   - 检查头文件 `thorvg/inc/thorvg.h` 的变化
   - 测试编译 native 模块

5. **修改 C++ 代码**（如需要）
   - `lottie/src/main/cpp/`
   - `svg/src/main/cpp/`

6. **完整测试**
   ```bash
   ./gradlew clean
   ./gradlew :sample:assembleDebug
   ```

7. **更新文档**
   - 修改本文件中的 commit ID
   - 更新 NATIVE_MODULE_GUIDE.md
   - 更新 QUICK_START.md

## ThorVG API 变化历史

### ThorVG 0.x → 1.0.0

| 变化 | 说明 |
|------|------|
| **对象管理** | 从原始指针改为 `std::unique_ptr` |
| **Initializer** | API 签名变化，参数顺序调整 |
| **ColorSpace** | 枚举从全局移到 `SwCanvas` 作用域 |
| **Canvas API** | `add()` 改为 `push()` + `tvg::cast()` |

### 适配代码示例

**0.x 版本**:
```cpp
auto picture = tvg::Picture::gen();
canvas->add(picture);
```

**1.0.0 版本**:
```cpp
auto picture = tvg::Picture::gen();  // 返回 unique_ptr
canvas->push(tvg::cast(picture.get()));
```

## 相关资源

- [ThorVG 官网](https://www.thorvg.org/)
- [ThorVG GitHub](https://github.com/thorvg/thorvg)
- [ThorVG API 文档](https://www.thorvg.org/apis)
- [ThorVG 社区 Discord](https://discord.gg/n25xj6J6HM)
- [Meson 构建系统](https://mesonbuild.com/)

## 许可证

ThorVG 使用 MIT License，与本项目兼容。

详见：
- ThorVG License: https://github.com/thorvg/thorvg/blob/main/LICENSE
- 本项目 License: [LICENSE](LICENSE)
