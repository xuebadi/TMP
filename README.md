# 🎓 学霸帝AI - 多模态 Android APK

基于 **Qwen3.5-2B** + **llama.cpp**（MTP 推测解码）的 Android 本地 AI 助手，支持文本对话和图片理解。

## ✨ 功能特性

- 💬 **智能对话** — Qwen3.5-2B Q4_K_M 量化模型，本地离线推理
- 📷 **拍照识别** — 调用相机拍摄，AI 分析图片内容
- 🖼️ **图片上传** — 从相册选择图片，支持多模态理解
- 🚀 **MTP 推测解码** — 自动检测并启用 MTP，推理速度提升 ~2x
- 📥 **自动下载模型** — 首次运行自动从 ModelScope 下载 GGUF 模型
- 🔒 **隐私安全** — 所有推理在设备本地完成，无需联网

## 📦 下载

| 版本 | 大小 | 说明 |
|------|------|------|
| [app-debug.apk](https://github.com/xuebadi/TMP/releases/latest) | ~29MB | 最新调试版（需自行下载模型） |

> **注意**：APK 不含模型文件（~2.1GB），首次启动会自动下载到设备。

## 🏗️ 构建

### 环境要求

- JDK 17+
- Android SDK (API 34)
- Android NDK 26.1.10909125
- Gradle 8.5

### 一键构建

```bash
git clone https://github.com/xuebadi/TMP.git
cd TMP

# 按需修改 local.properties
echo "sdk.dir=$ANDROID_HOME" > local.properties

# 构建 APK
./gradlew assembleDebug
```

输出路径：`app/build/outputs/apk/debug/app-debug.apk`

## 🔧 模型

| 文件 | 用途 | 下载地址 |
|------|------|----------|
| Qwen3.5-2B-Q4_K_M.gguf | 主模型 | [ModelScope](https://modelscope.cn/models/unsloth/Qwen3.5-2B-MTP-GGUF/resolve/master/Qwen3.5-2B-Q4_K_M.gguf) |
| mmproj-F16.gguf | 视觉投影器 | [ModelScope](https://modelscope.cn/models/unsloth/Qwen3.5-2B-MTP-GGUF/resolve/master/mmproj-F16.gguf) |

App 首次启动会自动下载到：`/Android/data/com.xuabadai.ai/files/`

## 📱 使用方法

1. 安装 APK（允许「未知来源」）
2. 首次启动 → 自动下载模型（需联网，~2.1GB）
3. 下载完成后即可开始对话
4. 点击 📷 拍照，或 🖼️ 选择图片进行多模态问答

## 🧠 技术架构

```
学霸帝AI
├── UI Layer (Kotlin)
│   ├── MainActivity.kt      # 主界面 + 相机/相册
│   ├── ChatAdapter.kt       # 对话列表适配器
│   └── ModelDownloader.kt  # 模型下载管理
├── JNI Layer (C++)
│   └── xuabadai_jni.cpp   # llama.cpp 推理桥接
└── Native (llama.cpp)
    ├── Qwen3.5 模型推理
    └── MTP 推测解码加速
```

## 🚀 MTP 推测解码

MTP（Multi-Token Prediction）已合并到 [llama.cpp main 分支](https://github.com/ggerganov/llama.cpp)，Qwen3.5 模型原生支持：

- 接受率：~75%（3 draft tokens）
- 加速比：1.5-2.0x
- 自动启用：检测到 MTP 模型时无需额外配置

## 📋 开发日志

- **2025-05-21** — APK 构建成功，llama.cpp main 分支集成，MTP 加速启用
- 项目基于 llama.cpp b25582 构建

## 📄 License

MIT License

## 🙏 致谢

- [llama.cpp](https://github.com/ggerganov/llama.cpp) — 优秀的本地 LLM 推理引擎
- [Qwen](https://github.com/QwenLM/Qwen) — 强大的多模态基础模型
- [ModelScope](https://modelscope.cn/) — 模型托管平台
