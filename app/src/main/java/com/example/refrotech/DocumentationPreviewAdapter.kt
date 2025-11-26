package com.example.refrotech

import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

typealias RealDocItem = DocItem

class DocumentationPreviewAdapter(
    private var items: MutableList<RealDocItem>,
    private val onDelete: ((RealDocItem) -> Unit)? = null,
    private val readOnly: Boolean = false
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
        val doc = items[position]

        try {
            when {
                doc.localUri != null -> {
                    try {
                        val stream = holder.itemView.context
                            .contentResolver
                            .openInputStream(doc.localUri!!)
                        val bmp = BitmapFactory.decodeStream(stream)
                        if (bmp != null) holder.img.setImageBitmap(bmp)
                        else holder.img.setImageResource(android.R.color.darker_gray)
                    } catch (e: Exception) {
                        Log.e("DocAdapter", "Failed localUri preview: ${e.message}")
                        holder.img.setImageResource(android.R.color.darker_gray)
                    }
                }

                !doc.base64.isNullOrBlank() -> {
                    try {
                        val trimmed = doc.base64.replace("\n", "")
                        val bytes = Base64.decode(trimmed, Base64.DEFAULT)
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) holder.img.setImageBitmap(bmp)
                        else holder.img.setImageResource(android.R.color.darker_gray)
                    } catch (e: Exception) {
                        Log.e("DocAdapter", "Failed base64 preview: ${e.message}")
                        holder.img.setImageResource(android.R.color.darker_gray)
                    }
                }

                else -> holder.img.setImageResource(android.R.color.darker_gray)
            }
        } catch (e: Exception) {
            Log.e("DocAdapter", "onBind error: ${e.message}")
            holder.img.setImageResource(android.R.color.darker_gray)
        }

        // FULLSCREEN IMAGE CLICK HANDLER (UPDATED FIX)
        holder.img.setOnClickListener {
            val ctx = holder.itemView.context
            val intent = Intent(ctx, FullScreenImageActivity::class.java)

            // ALWAYS prioritize base64 viewer
            if (!doc.base64.isNullOrBlank()) {
                intent.putExtra("base64", doc.base64)
            }

            // Only pass URI if this image originated from local gallery AND no base64 exists yet
            if (doc.localUri != null && doc.base64.isNullOrBlank()) {
                intent.putExtra("uri", doc.localUri.toString())
            }

            if (ctx !is android.app.Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            ctx.startActivity(intent)
        }

        if (readOnly || onDelete == null) {
            holder.btnDelete.visibility = View.GONE
        } else {
            holder.btnDelete.visibility = View.VISIBLE
            holder.btnDelete.setOnClickListener {
                val pos = holder.adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onDelete.invoke(items[pos])
                }
            }
        }
    }

    fun updateItems(newItems: List<RealDocItem>) {
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

    fun getItems(): List<RealDocItem> = items
}
