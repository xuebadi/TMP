#!/bin/bash
set -euo pipefail

# ========================================
# 学霸帝-多模态AI 一键构建脚本
# ========================================
# 前置要求: macOS, Homebrew, git
# 用法: chmod +x build_apk.sh && ./build_apk.sh
# ========================================

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log() { echo -e "${GREEN}[✓]${NC} $1"; }
warn() { echo -e "${YELLOW}[!]${NC} $1"; }
err() { echo -e "${RED}[✗]${NC} $1"; exit 1; }

# 1. Check/install JDK 17
echo ""
echo "=== Step 1: JDK 17 ==="
if java -version 2>&1 | grep -q "17"; then
    log "JDK 17 already installed"
else
    warn "Installing JDK 17..."
    brew install --quiet openjdk@17 || err "Failed to install JDK 17"
    JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || echo "")
    if [ -z "$JAVA_HOME" ]; then
        # Try brew path
        JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"
    fi
    export JAVA_HOME="$JAVA_HOME"
    export PATH="$JAVA_HOME/bin:$PATH"
fi
java -version 2>&1 | head -1
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17 2>/dev/null)}"

# 2. Install Android SDK
echo ""
echo "=== Step 2: Android SDK ==="
ANDROID_SDK_ROOT="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export ANDROID_SDK_ROOT="$ANDROID_SDK_ROOT"

if [ -d "$ANDROID_HOME/cmdline-tools" ]; then
    log "Android SDK found at $ANDROID_HOME"
else
    warn "Installing Android commandline-tools..."
    mkdir -p "$ANDROID_HOME/cmdline-tools"
    cd /tmp
    curl -L -o cmdline-tools.zip "https://dl.google.com/android/repository/commandlinetools-mac-11076708_latest.zip" \
        || err "Failed to download Android commandline-tools"
    unzip -q -o cmdline-tools.zip
    mv cmdline-tools "$ANDROID_HOME/cmdline-tools/latest"
    rm cmdline-tools.zip
    cd "$SCRIPT_DIR"
    log "Android commandline-tools installed"
fi

# Install required SDK packages
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
log "Installing SDK packages (platform 34, build-tools 34.0.0, ndk 26.1.10909125)..."
yes | sdkmanager --licenses > /dev/null 2>&1 || true
sdkmanager "platforms;android-34" "build-tools;34.0.0" "ndk;26.1.10909125" 2>&1 | tail -5
log "SDK packages installed"

# 3. Clone llama.cpp (with MTP support from main)
echo ""
echo "=== Step 3: llama.cpp ==="
LLAMA_DIR="$SCRIPT_DIR/app/src/main/cpp/llama.cpp"
if [ -d "$LLAMA_DIR" ]; then
    log "llama.cpp already exists, updating..."
    cd "$LLAMA_DIR" && git pull && cd "$SCRIPT_DIR"
else
    warn "Cloning llama.cpp (main branch with MTP)..."
    git clone --depth=1 --branch master https://github.com/ggml-org/llama.cpp.git "$LLAMA_DIR" \
        || err "Failed to clone llama.cpp"
    log "llama.cpp cloned"
fi

# 4. Set NDK path in local.properties
echo ""
echo "=== Step 4: Configuration ==="
echo "sdk.dir=$ANDROID_HOME" > "$SCRIPT_DIR/local.properties"
echo "ndk.dir=$ANDROID_HOME/ndk/26.1.10909125" >> "$SCRIPT_DIR/local.properties"
log "local.properties written"

# 5. Create gradle wrapper if missing
if [ ! -f "$SCRIPT_DIR/gradlew" ]; then
    warn "Creating Gradle wrapper..."
    curl -L -o /tmp/gradle.zip "https://services.gradle.org/distributions/gradle-8.5-bin.zip" \
        || err "Failed to download Gradle"
    mkdir -p /tmp/gradle-install && cd /tmp/gradle-install && unzip -q /tmp/gradle.zip
    /tmp/gradle-install/gradle-8.5/bin/gradle wrapper --gradle-version 8.5
    cp gradlew gradlew.bat "$SCRIPT_DIR/"
    cp -r gradle "$SCRIPT_DIR/"
    cd "$SCRIPT_DIR"
    rm -rf /tmp/gradle.zip /tmp/gradle-install
    chmod +x gradlew
    log "Gradle wrapper created"
fi

# 6. Build APK
echo ""
echo "=== Step 5: Building APK ==="
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export NDK_HOME="$ANDROID_HOME/ndk/26.1.10909125"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

if [ -z "${JAVA_HOME:-}" ]; then
    JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null)"
    export JAVA_HOME
fi

./gradlew assembleDebug 2>&1 | tail -20

if [ -f "$SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk" ]; then
    echo ""
    log "=========================================="
    log "APK 构建成功！"
    log "路径: $SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk"
    log "大小: $(du -h "$SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk" | cut -f1)"
    log "=========================================="
    echo ""
    # Try to open in Finder
    open -R "$SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk" 2>/dev/null || true
else
    err "APK build failed. Check the output above for errors."
fi
