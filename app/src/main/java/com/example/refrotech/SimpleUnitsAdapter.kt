package com.example.refrotech

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SimpleUnitsAdapter(private var items: List<ACUnit>) : RecyclerView.Adapter<SimpleUnitsAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvBrand: TextView = view.findViewById(R.id.tvUnitBrand)
        val tvPk: TextView = view.findViewById(R.id.tvUnitPk)
        val tvWork: TextView = view.findViewById(R.id.tvUnitWork)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_unit_simple, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val u = items[position]
        holder.tvBrand.text = u.brand
        holder.tvPk.text = u.pk
        holder.tvWork.text = u.workType
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<ACUnit>) {
        // not strictly necessary for read-only but helpful
        (items as? MutableList<ACUnit>)?.apply {
            clear()
            addAll(newItems)
        }
        notifyDataSetChanged()
    }
}
