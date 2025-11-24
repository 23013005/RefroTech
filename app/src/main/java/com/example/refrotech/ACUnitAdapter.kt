package com.example.refrotech

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Adapter for showing AC units. The right side shows a unit number (#1, #2, ...).
 * Click on the whole item triggers onItemClick(index) which should open edit/delete dialog.
 *
 * Note: this adapter no longer handles delete inside the list; deletion is handled in dialog.
 */
class ACUnitAdapter(
    private val items: MutableList<ACUnit>,
    private val onItemClick: ((Int) -> Unit)? = null
) : RecyclerView.Adapter<ACUnitAdapter.VH>() {

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvBrand: TextView = itemView.findViewById(R.id.tvMerkAC)
        val tvPk: TextView = itemView.findViewById(R.id.tvJumlahPK)
        val tvWork: TextView = itemView.findViewById(R.id.tvJenisPekerjaan)
        val tvNumber: TextView? = itemView.findViewById(R.id.tvUnitNumber) // optional in layout
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_ac_unit, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val unit = items[position]
        holder.tvBrand.text = "Merk AC: ${unit.brand}"
        holder.tvPk.text = "Jumlah PK: ${unit.pk}"
        holder.tvWork.text = "Jenis Pekerjaan: ${unit.workType}"
        holder.tvNumber?.text = "#${position + 1}"

        holder.itemView.setOnClickListener {
            onItemClick?.invoke(position)
        }
    }
}
