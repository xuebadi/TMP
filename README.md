# 学霸帝 AI

基于 Qwen3.5-2B + llama.cpp MTP 推测解码的 Android 多模态 AI 应用。

## 功能特性

- 🤖 **本地推理**: 基于 llama.cpp，无需联网即可运行
- 🖼️ **多模态理解**: 支持图片输入（相机/相册）
- ⚡ **MTP 加速**: 推测解码技术提升生成速度
- 📱 **纯原生**: Kotlin + JNI，无 WebView 依赖

## 技术栈

| 组件 | 说明 |
|------|------|
| 模型 | Qwen3.5-2B (Q4_K_M 量化) |
| 推理引擎 | llama.cpp |
| 多模态 | mmproj-F16 |
| 前端 | Kotlin + Android SDK |
| 原生层 | C++17 + CMake |

## 快速开始

### 前置要求

- macOS / Linux
- JDK 17+
- Android SDK (API 34)
- Android NDK r26+

### 一键构建

```bash
chmod +x build_apk.sh
./build_apk.sh
```

脚本会自动：
1. 检测并安装 JDK 17
2. 配置 Android SDK/NDK
3. 编译 llama.cpp 原生库
4. 构建 APK

### 手动构建

```bash
# 1. 编译原生库
cd app/src/main/cpp
mkdir build && cd build
cmake .. -DANDROID_ABI=arm64-v8a
make

# 2. 构建 APK
cd ../../..
./gradlew assembleRelease
```

## 模型下载

首次启动时，App 会自动从 ModelScope 下载模型：

- **语言模型**: `Qwen3.5-2B-Q4_K_M.gguf` (~1.4GB)
- **视觉投影**: `mmproj-F16.gguf` (~150MB)

下载路径: `/sdcard/Android/data/com.xuabadai.ai/files/models/`

## 项目结构

```
├── app/
│   ├── src/main/
│   │   ├── java/com/xuabadai/ai/
│   │   │   ├── MainActivity.kt      # 主界面
│   │   │   ├── ChatAdapter.kt      # 聊天列表适配器
│   │   │   ├── ChatMessage.kt      # 消息数据类
│   │   │   ├── ModelDownloader.kt  # 模型下载器
│   │   │   └── LlamaBridge.kt     # JNI 桥接层
│   │   └── cpp/                    # C++ 原生代码
│   └── build.gradle.kts
├── build_apk.sh                    # 一键构建脚本
└── gradle/
```

## 运行截图

> 待添加

## 已知问题

- 首次模型加载需要较长时间（约 30-60 秒）
- 部分低端设备可能出现内存不足
- 图片理解需要 mmproj 模型支持

## 许可证

[MIT License](LICENSE)

## 致谢

- [Qwen Team](https://github.com/QwenLM) - 语言模型
- [llama.cpp](https://github.com/ggml-org/llama.cpp) - 推理引擎
- [ModelScope](https://modelscope.cn) - 模型托管
