package com.example.refrotech

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class DocumentationPreviewAdapter(
    private var items: MutableList<DocItem>,
    private val onDelete: ((DocItem) -> Unit)? = null
) : RecyclerView.Adapter<DocumentationPreviewAdapter.VH>() {

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val img: ImageView = itemView.findViewById(R.id.imgDocPreview)
        val btnDelete: ImageView = itemView.findViewById(R.id.btnDeleteDoc)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_doc_preview, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val it = items[position]

        try {
            when {
                // PREVIEW: Not uploaded yet, show from local Uri
                it.localUri != null -> {
                    try {
                        val stream = holder.itemView.context
                            .contentResolver
                            .openInputStream(it.localUri)
                        val bmp = BitmapFactory.decodeStream(stream)
                        if (bmp != null) holder.img.setImageBitmap(bmp)
                        else holder.img.setImageResource(android.R.color.darker_gray)
                    } catch (e: Exception) {
                        Log.e("DocAdapter", "Failed to load localUri preview: ${e.message}")
                        holder.img.setImageResource(android.R.color.darker_gray)
                    }
                }

                // STORED BASE64: show uploaded image
                !it.base64.isNullOrBlank() -> {
                    try {
                        val trimmed = it.base64.replace("\n", "")
                        val bytes = Base64.decode(trimmed, Base64.DEFAULT)
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) holder.img.setImageBitmap(bmp)
                        else holder.img.setImageResource(android.R.color.darker_gray)
                    } catch (e: Exception) {
                        Log.e("DocAdapter", "Failed to load base64: ${e.message}")
                        holder.img.setImageResource(android.R.color.darker_gray)
                    }
                }

                else -> holder.img.setImageResource(android.R.color.darker_gray)
            }
        } catch (e: Exception) {
            Log.e("DocAdapter", "onBind error: ${e.message}")
            holder.img.setImageResource(android.R.color.darker_gray)
        }

        holder.btnDelete.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onDelete?.invoke(items[pos])
            }
        }
    }

    fun updateItems(newItems: List<DocItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun removeById(docId: String) {
        val idx = items.indexOfFirst { it.id == docId }
        if (idx >= 0) {
            items.removeAt(idx)
            notifyItemRemoved(idx)
        }
    }

    fun getItems(): List<DocItem> = items
}
