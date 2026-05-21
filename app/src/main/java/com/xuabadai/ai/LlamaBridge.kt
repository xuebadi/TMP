package com.xuabadai.ai

object LlamaBridge {
    init {
        System.loadLibrary("xuabadai_jni")
    }

    @JvmStatic
    external fun loadModelNative(
        modelPath: String,
        mmprojPath: String,
        nThreads: Int,
        enableMtp: Boolean
    ): Boolean

    @JvmStatic
    external fun isMTPEnabledNative(): Boolean

    @JvmStatic
    external fun freeModelNative()

    @JvmStatic
    external fun generateNative(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float
    ): String

    fun loadModel(modelPath: String, mmprojPath: String, nThreads: Int, enableMtp: Boolean): Boolean {
        return try {
            loadModelNative(modelPath, mmprojPath, nThreads, enableMtp)
        } catch (e: Exception) {
            false
        }
    }

    fun isMTPEnabled(): Boolean {
        return try {
            isMTPEnabledNative()
        } catch (e: Exception) {
            false
        }
    }

    fun freeModel() {
        try {
            freeModelNative()
        } catch (_: Exception) {}
    }

    fun generate(prompt: String, maxTokens: Int = 2048, temperature: Float = 0.7f, topP: Float = 0.9f): String {
        return try {
            generateNative(prompt, maxTokens, temperature, topP)
        } catch (e: Exception) {
            "[生成错误] ${e.message}"
        }
    }
}
