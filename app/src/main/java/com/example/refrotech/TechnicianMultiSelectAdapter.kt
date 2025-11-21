package com.example.refrotech

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * TechnicianMultiSelectAdapter
 *
 * Model used here:
 *  data class TechItem(
 *      val id: String,
 *      val name: String,
 *      val status: String,
 *      val disabled: Boolean,   // unavailable = disabled
 *      var checked: Boolean     // selected or not
 *  )
 */
class TechnicianMultiSelectAdapter(
    private val items: MutableList<TechItem>,
    private val onSelectionChanged: (() -> Unit)? = null
) : RecyclerView.Adapter<TechnicianMultiSelectAdapter.VH>() {

    data class TechItem(
        val id: String,
        val name: String,
        val status: String,
        val disabled: Boolean,
        var checked: Boolean
    )

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val root: LinearLayout = view.findViewById(R.id.rootItem)
        val cb: CheckBox = view.findViewById(R.id.checkBoxTech)
        val tvName: TextView = view.findViewById(R.id.tvTechnicianName)
        val tvStatus: TextView = view.findViewById(R.id.tvTechnicianStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_technician_multiselect, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]

        holder.tvName.text = item.name
        holder.tvStatus.text = item.status
        holder.cb.isChecked = item.checked

        // if unavailable → grey text + disabled checkbox
        if (item.disabled) {
            holder.tvStatus.setTextColor(0xFF888888.toInt())
            holder.cb.isEnabled = false
        } else {
            holder.cb.isEnabled = true
        }

        // clicking row toggles checkbox only if NOT disabled
        holder.root.setOnClickListener {
            if (item.disabled) return@setOnClickListener

            item.checked = !item.checked
            holder.cb.isChecked = item.checked
            onSelectionChanged?.invoke()
        }

        // clicking the checkbox also toggles only if NOT disabled
        holder.cb.setOnClickListener {
            if (item.disabled) {
                holder.cb.isChecked = false
                return@setOnClickListener
            }

            item.checked = holder.cb.isChecked
            onSelectionChanged?.invoke()
        }
    }

    fun getSelectedIds(): List<String> =
        items.filter { it.checked }.map { it.id }

    fun getSelectedNames(): List<String> =
        items.filter { it.checked }.map { it.name }
}
