package com.example.refrotech

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

/**
 * Adapter that displays:
 * - existing uploaded documentation (URLs from Firestore)
 * - newly selected images (Uri)
 */
class DocumentationPreviewAdapter :
    RecyclerView.Adapter<DocumentationPreviewAdapter.PhotoViewHolder>() {

    private val existingPhotos = mutableListOf<String>()
    private val newPhotos = mutableListOf<Uri>()

    fun setExistingPhotos(list: List<String>) {
        existingPhotos.clear()
        existingPhotos.addAll(list)
        notifyDataSetChanged()
    }

    fun setSelectedPhotos(list: List<Uri>) {
        newPhotos.clear()
        newPhotos.addAll(list)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = existingPhotos.size + newPhotos.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_documentation_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        if (position < existingPhotos.size) {
            // Existing image from Firestore URL
            Glide.with(holder.itemView)
                .load(existingPhotos[position])
                .into(holder.img)
        } else {
            // Newly selected image (Uri)
            val uriIndex = position - existingPhotos.size
            Glide.with(holder.itemView)
                .load(newPhotos[uriIndex])
                .into(holder.img)
        }
    }

    class PhotoViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val img: ImageView = v.findViewById(R.id.imgPhoto)
    }
}
