package com.example.refrotech

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * TechnicianManageAdapter
 *
 * items: MutableList<Map<String, Any>> where each item at least contains:
 *   - "id" -> String
 *   - "name" -> String
 *   - "username" -> String
 *
 * Callbacks:
 *  - onEdit(id)
 *  - onDelete(id)
 *  - onSetAvailability(id, name)  // invoked on long-press of row
 */
class TechnicianManageAdapter(
    private var items: MutableList<Map<String, Any>>,
    private val onEdit: ((String) -> Unit)? = null,
    private val onDelete: ((String) -> Unit)? = null,
    private val onSetAvailability: ((String, String) -> Unit)? = null
) : RecyclerView.Adapter<TechnicianManageAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvTechnicianName)
        val tvUsername: TextView = view.findViewById(R.id.tvTechnicianUsername)
        val btnEdit: FrameLayout? = view.findViewById(R.id.btnEditTech)
        val btnDelete: FrameLayout? = view.findViewById(R.id.btnDeleteTech)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_technician_manage, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val id = item["id"] as? String ?: ""
        val name = item["name"] as? String ?: ""
        val username = item["username"] as? String ?: ""

        holder.tvName.text = name
        holder.tvUsername.text = username

        holder.btnEdit?.setOnClickListener {
            onEdit?.invoke(id)
        }

        holder.btnDelete?.setOnClickListener {
            onDelete?.invoke(id)
        }

        // Long-press the row to open Set Availability dialog.
        holder.itemView.setOnLongClickListener {
            onSetAvailability?.invoke(id, name)
            true
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<Map<String, Any>>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
