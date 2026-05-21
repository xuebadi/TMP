package com.xuabadai.ai

import android.graphics.Bitmap

data class ChatMessage(
    val role: String,       // "user" or "ai"
    val content: String,
    val imageBitmap: Bitmap? = null
)
