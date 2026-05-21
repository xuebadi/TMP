package com.xuabadai.ai

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object ModelDownloader {

    data class ModelInfo(
        val name: String,
        val url: String,
        val fileName: String
    )

    val MAIN_MODEL = ModelInfo(
        "Qwen3.5-2B-Q4_K_M",
        "https://modelscope.cn/models/unsloth/Qwen3.5-2B-MTP-GGUF/resolve/master/Qwen3.5-2B-Q4_K_M.gguf",
        "Qwen3.5-2B-Q4_K_M.gguf"
    )

    val MMPROJ = ModelInfo(
        "mmproj-F16",
        "https://modelscope.cn/models/unsloth/Qwen3.5-2B-MTP-GGUF/resolve/master/mmproj-F16.gguf",
        "mmproj-F16.gguf"
    )

    fun getModelDir(context: Context): File {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun isModelDownloaded(context: Context): Boolean {
        val mainFile = File(getModelDir(context), MAIN_MODEL.fileName)
        val mmprojFile = File(getModelDir(context), MMPROJ.fileName)
        return mainFile.exists() && mainFile.length() > 1_000_000L &&
               mmprojFile.exists() && mmprojFile.length() > 100_000L
    }

    fun getMainModelPath(context: Context): String {
        return File(getModelDir(context), MAIN_MODEL.fileName).absolutePath
    }

    fun getMmprojPath(context: Context): String {
        return File(getModelDir(context), MMPROJ.fileName).absolutePath
    }

    suspend fun downloadModel(
        context: Context,
        model: ModelInfo,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val targetFile = File(getModelDir(context), model.fileName)
            if (targetFile.exists() && targetFile.length() > 1_000_000L) {
                onProgress(1f)
                return@withContext Result.success(targetFile)
            }

            val tmpFile = File(targetFile.absolutePath + ".tmp")
            val connection = URL(model.url).openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            val contentLength = connection.contentLengthLong

            connection.inputStream.buffered().use { input ->
                tmpFile.outputStream().buffered().use { output ->
                    val buffer = ByteArray(8192)
                    var totalRead = 0L
                    var lastProgress = -1f
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        totalRead += read
                        if (contentLength > 0) {
                            val progress = totalRead.toFloat() / contentLength.toFloat()
                            if (progress - lastProgress > 0.01f) {
                                lastProgress = progress
                                onProgress(progress)
                            }
                        }
                    }
                }
            }

            tmpFile.renameTo(targetFile)
            onProgress(1f)
            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
