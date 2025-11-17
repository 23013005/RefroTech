package com.example.refrotech

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SimpleUnitsAdapter(
    private val unitList: List<ACUnit>
) : RecyclerView.Adapter<SimpleUnitsAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val brand: TextView = view.findViewById(R.id.tvUnitBrand)
        val pk: TextView = view.findViewById(R.id.tvUnitPk)
        val work: TextView = view.findViewById(R.id.tvUnitWork)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_unit_simple, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val u = unitList[position]

        holder.brand.text = "Brand: ${u.brand}"
        holder.pk.text = "PK: ${u.pk}"
        holder.work.text = "Work Type: ${u.workType}"
    }

    override fun getItemCount() = unitList.size
}
