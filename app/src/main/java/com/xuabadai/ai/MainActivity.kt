package com.xuabadai.ai

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatAdapter
    private lateinit var etInput: EditText
    private lateinit var btnSend: Button
    private lateinit var btnCamera: ImageButton
    private lateinit var btnGallery: ImageButton
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar

    private val messages = mutableListOf<ChatMessage>()
    private var currentPhotoPath: String? = null
    private var pendingImageBitmap: Bitmap? = null
    private var modelLoaded = false
    private var isGenerating = false
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val REQUEST_CAMERA = 1001
        private const val REQUEST_GALLERY = 1002
        private const val REQUEST_PERMISSIONS = 1003

        private const val MODEL_URL = "https://modelscope.cn/models/unsloth/Qwen3.5-2B-MTP-GGUF/resolve/master/Qwen3.5-2B-Q4_K_M.gguf"
        private const val MMPROJ_URL = "https://modelscope.cn/models/unsloth/Qwen3.5-2B-MTP-GGUF/resolve/master/mmproj-F16.gguf"

        private const val MODEL_FILE = "Qwen3.5-2B-Q4_K_M.gguf"
        private const val MMPROJ_FILE = "mmproj-F16.gguf"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerView)
        etInput = findViewById(R.id.etInput)
        btnSend = findViewById(R.id.btnSend)
        btnCamera = findViewById(R.id.btnCamera)
        btnGallery = findViewById(R.id.btnGallery)
        tvStatus = findViewById(R.id.tvStatus)
        progressBar = findViewById(R.id.progressBar)

        adapter = ChatAdapter(messages)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // 添加欢迎消息
        messages.add(ChatMessage(
            role = "ai",
            content = "🎓 欢迎使用学霸帝AI！\n\n我是基于 Qwen3.5 + llama.cpp (MTP推测解码) 的本地AI助手。\n\n首次使用需要下载模型（约1.5GB），请点击下方「下载模型」开始。\n\n支持功能：\n📷 拍照识别\n🖼️ 图片上传\n💬 智能对话"
        ))
        adapter.notifyItemInserted(0)

        // 发送按钮
        btnSend.setOnClickListener { sendMessage() }
        etInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                btnSend.isEnabled = s.toString().isNotEmpty() || pendingImageBitmap != null
            }
        })

        // 拍照按钮
        btnCamera.setOnClickListener {
            checkPermissionsAndOpenCamera()
        }

        // 相册按钮
        btnGallery.setOnClickListener {
            openGallery()
        }

        // 检查模型状态
        checkModelStatus()
    }

    private fun checkModelStatus() {
        val modelFile = File(filesDir, MODEL_FILE)
        val mmprojFile = File(filesDir, MMPROJ_FILE)

        if (modelFile.exists() && mmprojFile.exists()) {
            tvStatus.text = "✅ 模型已就绪"
            loadModel(modelFile.absolutePath, mmprojFile.absolutePath)
        } else {
            tvStatus.text = "📥 需要下载模型"
            showDownloadDialog()
        }
    }

    private fun showDownloadDialog() {
        AlertDialog.Builder(this)
            .setTitle("下载模型")
            .setMessage("需要下载以下模型文件：\n\n1. Qwen3.5-2B 主模型 (~1.5GB)\n2. mmproj 视觉投影器 (~600MB)\n\n总计约 2.1GB，建议使用 WiFi 下载。")
            .setPositiveButton("开始下载") { _, _ ->
                downloadModels()
            }
            .setNegativeButton("稍后再说", null)
            .show()
    }

    private fun downloadModels() {
        lifecycleScope.launch {
            tvStatus.text = "⏬ 下载主模型中..."
            progressBar.visibility = View.VISIBLE

            val modelFile = File(filesDir, MODEL_FILE)
            val mmprojFile = File(filesDir, MMPROJ_FILE)

            withContext(Dispatchers.IO) {
                try {
                    if (!modelFile.exists()) {
                        downloadFile(MODEL_URL, modelFile) { progress ->
                            handler.post { tvStatus.text = "⏬ 下载主模型: ${progress}%" }
                        }
                    }

                    if (!mmprojFile.exists()) {
                        handler.post { tvStatus.text = "⏬ 下载视觉投影器中..." }
                        downloadFile(MMPROJ_URL, mmprojFile) { progress ->
                            handler.post { tvStatus.text = "⏬ 下载视觉投影器: ${progress}%" }
                        }
                    }
                } catch (e: Exception) {
                    handler.post {
                        tvStatus.text = "❌ 下载失败: ${e.message}"
                        Toast.makeText(this@MainActivity, "下载失败: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                    return@withContext
                }
            }

            progressBar.visibility = View.GONE
            if (modelFile.exists() && mmprojFile.exists()) {
                tvStatus.text = "✅ 模型已就绪"
                loadModel(modelFile.absolutePath, mmprojFile.absolutePath)
            }
        }
    }

    private fun downloadFile(urlString: String, destFile: File, onProgress: (Int) -> Unit) {
        val url = URL(urlString)
        val connection = url.openConnection()
        connection.connect()
        val totalSize = connection.contentLength

        url.openStream().use { input ->
            FileOutputStream(destFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (totalSize > 0) {
                        onProgress(((totalRead * 100) / totalSize).toInt())
                    }
                }
            }
        }
    }

    private fun loadModel(modelPath: String, mmprojPath: String) {
        thread {
            try {
                val success = LlamaBridge.loadModel(modelPath, mmprojPath, 4, true)
                handler.post {
                    if (success) {
                        modelLoaded = true
                        val mtpStatus = if (LlamaBridge.isMTPEnabled()) " (MTP已启用🚀)" else ""
                        tvStatus.text = "🟢 模型加载成功$mtpStatus"
                        Toast.makeText(this, "模型加载成功！", Toast.LENGTH_SHORT).show()
                    } else {
                        tvStatus.text = "❌ 模型加载失败"
                        Toast.makeText(this, "模型加载失败", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                handler.post {
                    tvStatus.text = "❌ 加载异常: ${e.message}"
                }
            }
        }
    }

    private fun checkPermissionsAndOpenCamera() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.CAMERA)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQUEST_PERMISSIONS)
        } else {
            openCamera()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            openCamera()
        }
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val photoFile = File.createTempFile("xuabadai_", ".jpg", cacheDir)
        currentPhotoPath = photoFile.absolutePath
        val photoUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photoFile)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        startActivityForResult(intent, REQUEST_CAMERA)
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        startActivityForResult(intent, REQUEST_GALLERY)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return

        when (requestCode) {
            REQUEST_CAMERA -> {
                currentPhotoPath?.let { path ->
                    pendingImageBitmap = BitmapFactory.decodeFile(path)
                    pendingImageBitmap?.let { showImagePreview(it) }
                }
            }
            REQUEST_GALLERY -> {
                data?.data?.let { uri ->
                    pendingImageBitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                    pendingImageBitmap?.let { showImagePreview(it) }
                }
            }
        }
    }

    private fun showImagePreview(bitmap: Bitmap) {
        // 创建图片预览消息
        val imageView = ImageView(this)
        imageView.setImageBitmap(bitmap)
        imageView.adjustViewBounds = true
        imageView.maxWidth = 200
        imageView.maxHeight = 200

        val message = ChatMessage(role = "user", content = "[图片]", imageBitmap = bitmap)
        messages.add(message)
        adapter.notifyItemInserted(messages.size - 1)
        recyclerView.scrollToPosition(messages.size - 1)

        btnSend.isEnabled = true
        Toast.makeText(this, "图片已选择，输入问题后发送", Toast.LENGTH_SHORT).show()
    }

    private fun sendMessage() {
        val text = etInput.text.toString().trim()
        if (text.isEmpty() && pendingImageBitmap == null) return

        // 添加用户消息
        val userMsg = ChatMessage(
            role = "user",
            content = if (text.isNotEmpty()) text else "[图片提问]",
            imageBitmap = pendingImageBitmap
        )
        messages.add(userMsg)
        etInput.text.clear()
        adapter.notifyItemInserted(messages.size - 1)
        recyclerView.scrollToPosition(messages.size - 1)

        // 清理待发送图片
        val imageToSend = pendingImageBitmap
        pendingImageBitmap = null
        btnSend.isEnabled = false

        if (!modelLoaded) {
            messages.add(ChatMessage(role = "ai", content = "⚠️ 模型尚未加载，请先下载并加载模型。"))
            adapter.notifyItemInserted(messages.size - 1)
            recyclerView.scrollToPosition(messages.size - 1)
            return
        }

        if (isGenerating) return
        isGenerating = true
        progressBar.visibility = View.VISIBLE
        tvStatus.text = "🤔 思考中..."

        thread {
            try {
                // 构建提示词
                var prompt = "<|im_start|>system\n你是一个名为学霸帝AI的智能助手，精通各学科知识，善于用通俗易懂的方式解答问题。<|im_end|>\n"

                // 如果有图片，添加视觉标记
                if (imageToSend != null) {
                    prompt += "<|im_start|>user\n<|vision_start|><|vision_end|>$text<|im_end|>\n"
                } else {
                    prompt += "<|im_start|>user\n$text<|im_end|>\n"
                }
                prompt += "<|im_start|>assistant\n"

                // 生成回复
                val response = LlamaBridge.generate(prompt, maxTokens = 2048, temperature = 0.7f, topP = 0.9f)

                handler.post {
                    // 清理思考中的占位
                    val cleaned = response
                        .replace("<|im_end|>", "")
                        .replace("<|im_start|>assistant\n", "")
                        .trim()

                    messages.add(ChatMessage(role = "ai", content = cleaned))
                    adapter.notifyItemInserted(messages.size - 1)
                    recyclerView.scrollToPosition(messages.size - 1)
                    progressBar.visibility = View.GONE
                    tvStatus.text = "🟢 就绪"
                    isGenerating = false
                }
            } catch (e: Exception) {
                handler.post {
                    messages.add(ChatMessage(role = "ai", content = "❌ 生成错误: ${e.message}"))
                    adapter.notifyItemInserted(messages.size - 1)
                    recyclerView.scrollToPosition(messages.size - 1)
                    progressBar.visibility = View.GONE
                    tvStatus.text = "❌ 错误"
                    isGenerating = false
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LlamaBridge.freeModel()
    }
}
