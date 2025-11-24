package com.example.refrotech

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

/**
 * Adapter to show:
 * - Existing uploaded documentation (URLs)
 * - Newly selected photos (Uri)
 */
class DocumentationPreviewAdapter(
    private val newPhotos: MutableList<Uri>,
    private val existingPhotos: MutableList<String> = mutableListOf()
) : RecyclerView.Adapter<DocumentationPreviewAdapter.PhotoViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_documentation_photo, parent, false)
        return PhotoViewHolder(v)
    }

    override fun getItemCount(): Int {
        return existingPhotos.size + newPhotos.size
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {

        if (position < existingPhotos.size) {
            // Load existing Firestore image
            Glide.with(holder.itemView)
                .load(existingPhotos[position])
                .into(holder.img)
        } else {
            // Load newly selected image (Uri)
            val uriIndex = position - existingPhotos.size
            Glide.with(holder.itemView)
                .load(newPhotos[uriIndex])
                .into(holder.img)
        }
    }

    class PhotoViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val img: ImageView = v.findViewById(R.id.imgPhoto)
    }

    /** Add new selected photos */
    fun updateNewPhotos(list: List<Uri>) {
        newPhotos.clear()
        newPhotos.addAll(list)
        notifyDataSetChanged()
    }

    /** Set existing photos from Firestore */
    fun setExistingPhotos(urls: List<String>) {
        existingPhotos.clear()
        existingPhotos.addAll(urls)
        notifyDataSetChanged()
    }
}
