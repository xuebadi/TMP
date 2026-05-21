package com.xuabadai.ai

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(private val messages: List<ChatMessage>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_AI = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].role == "user") TYPE_USER else TYPE_AI
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layout = if (viewType == TYPE_USER)
            R.layout.item_message_user
        else
            R.layout.item_message_ai
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return object : RecyclerView.ViewHolder(view) {}
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        val tvContent = holder.itemView.findViewById<TextView>(R.id.tvContent)
        tvContent.text = message.content

        // 显示图片（如果有）
        val ivImage = holder.itemView.findViewById<ImageView?>(R.id.ivImage)
        if (ivImage != null) {
            if (message.imageBitmap != null) {
                ivImage.visibility = View.VISIBLE
                ivImage.setImageBitmap(message.imageBitmap)
            } else {
                ivImage.visibility = View.GONE
            }
        }
    }

    override fun getItemCount(): Int = messages.size
}
