package com.example.refrotech

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ACUnitAdapter(
    private val items: MutableList<ACUnit>,
    private val onDelete: ((Int) -> Unit)? = null
) : RecyclerView.Adapter<ACUnitAdapter.VH>() {

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvBrand: TextView = itemView.findViewById(R.id.tvMerkAC)
        val tvPk: TextView = itemView.findViewById(R.id.tvJumlahPK)
        val tvWork: TextView = itemView.findViewById(R.id.tvJenisPekerjaan)
        val btnDelete: FrameLayout? = itemView.findViewById(R.id.btnDeleteUnit)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_ac_unit, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val unit = items[position]
        holder.tvBrand.text = unit.brand
        holder.tvPk.text = unit.pk
        holder.tvWork.text = unit.workType

        holder.btnDelete?.setOnClickListener {
            onDelete?.invoke(position)
        }
    }
}
